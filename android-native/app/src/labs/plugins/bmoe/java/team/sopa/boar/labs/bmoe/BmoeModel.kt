package team.sopa.boar.labs.bmoe

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import team.sopa.boar.engine.ChatMessage
import team.sopa.boar.engine.EngineParams
import team.sopa.boar.engine.FormattedPrompt
import team.sopa.boar.engine.GenerationEvent
import team.sopa.boar.engine.GenerationMetrics
import team.sopa.boar.engine.Sampling
import team.sopa.boar.engine.TextModel
import team.sopa.boar.labs.engines.EngineProcess

/**
 * A GGUF mixture of experts running on BigMoeOnEdge's bmoe-cli in session mode (--chatml --session --moe-stream):
 * one JSON request per line on stdin, BMOE_PROGRESS deltas and a BMOE_DONE line with stats on stdout. The always-used
 * weights stay in RAM and each token's experts are read from storage, so a model bigger than RAM runs. Ported from
 * the experimental-engines branch (src/inference/externalEngines.ts, BmoeEngine).
 *
 * bmoe applies the ChatML template itself and takes one prompt, so [formatChat] flattens the chat into plain text.
 */
class BmoeModel private constructor(private val engine: EngineProcess, private val nCtx: Int) : TextModel {
    private val lock = Mutex()
    private var nextId = 1
    @Volatile private var busy = false
    @Volatile private var think = false
    /** A request whose BMOE_DONE hasn't been read (stopped mid-answer): its leftover output is drained first. */
    private var unfinished: Int? = null

    override val hasChatTemplate = true
    override val isGenerating: Boolean get() = busy
    @Volatile override var onGenerated: ((GenerationMetrics) -> Unit)? = null

    override suspend fun formatChat(messages: List<ChatMessage>, enableThinking: Boolean): FormattedPrompt {
        think = enableThinking
        return FormattedPrompt(flatten(messages), supportsThinking = true, thinkingStart = "<think>", thinkingEnd = listOf("</think>"), stops = emptyList())
    }

    override fun generate(prompt: String, sampling: Sampling): Flow<GenerationEvent> = callbackFlow {
        lock.withLock {
            unfinished?.let { old -> drain(old) }
            val id = nextId++
            val req = JSONObject().put("cmd", "generate").put("id", id).put("prompt", prompt)
                .put("n_predict", team.sopa.boar.engine.models.EngineChoice.bigBoarTokens(sampling.nPredict, nCtx)).put("think", think).put("clear_kv", true)
            busy = true
            val t0 = System.nanoTime()
            var firstMs = -1.0
            var inReasoning = false
            try {
                unfinished = id
                engine.write(req.toString() + "\n")
                while (true) {
                    val out = engine.output.receive()
                    if (out is EngineProcess.Output.Exit) error("BigMoeOnEdge stopped (code ${out.code}): ${engine.tail()}")
                    val line = (out as? EngineProcess.Output.Line)?.text ?: continue
                    val sp = line.indexOf(' ')
                    when (if (sp > 0) line.substring(0, sp) else line) {
                        "BMOE_PROGRESS" -> {
                            val p = JSONObject(line.substring(sp + 1))
                            if (p.optBoolean("reset")) continue // a retroactive re-parse; DONE carries the final text
                            if (firstMs < 0) firstMs = (System.nanoTime() - t0) / 1e6
                            p.optString("delta_reasoning").takeIf { it.isNotEmpty() }?.let {
                                trySend(GenerationEvent.Piece(if (inReasoning) it else "<think>$it")); inReasoning = true
                            }
                            p.optString("delta_text").takeIf { it.isNotEmpty() }?.let {
                                trySend(GenerationEvent.Piece(if (inReasoning) "</think>$it" else it)); inReasoning = false
                            }
                        }
                        "BMOE_DONE" -> {
                            val d = JSONObject(line.substring(sp + 1))
                            if (d.optInt("id") != id) continue
                            unfinished = null
                            if (inReasoning) trySend(GenerationEvent.Piece("</think>"))
                            val m = metrics(d, firstMs, (System.nanoTime() - t0) / 1e6)
                            runCatching { onGenerated?.invoke(m) }
                            send(GenerationEvent.Done(m))
                            break
                        }
                        "BMOE_ERROR" -> {
                            unfinished = null // the request ended: nothing left to drain
                            error("BigMoeOnEdge: ${JSONObject(line.substring(sp + 1)).optString("msg")}")
                        }
                    }
                }
            } finally {
                busy = false
            }
        }
        close()
        awaitClose { }
    }.buffer(kotlinx.coroutines.channels.Channel.UNLIMITED).flowOn(Dispatchers.IO) // never drop a piece of the answer

    /** Reads and drops what a stopped request still prints, up to its BMOE_DONE (or 60 s). */
    private suspend fun drain(id: Int) {
        withTimeoutOrNull(60_000) {
            while (true) {
                val out = engine.output.receive()
                if (out is EngineProcess.Output.Exit) return@withTimeoutOrNull
                val line = (out as? EngineProcess.Output.Line)?.text ?: continue
                if (line.startsWith("BMOE_DONE") && runCatching { JSONObject(line.substringAfter(' ')).optInt("id") }.getOrNull() == id) return@withTimeoutOrNull
                if (line.startsWith("BMOE_ERROR")) return@withTimeoutOrNull
            }
        }
        unfinished = null
    }

    override fun cancel() {
        if (busy) runCatching { engine.write("{\"cmd\":\"cancel\"}\n") }
    }

    override fun close() = engine.stop()

    companion object {
        const val BINARY = "libbmoe_cli.so"

        /**
         * Starts bmoe-cli on [modelPath] and waits for BMOE_READY (loading a 12 GB model takes a while); null with the
         * engine's last lines logged when it exits first. Upstream's settings: a micro-batch no wider than 512 tokens
         * (without it the compute buffer is sized for the whole context) and an expert cache sized to free RAM.
         */
        suspend fun start(context: Context, modelPath: String, params: EngineParams): BmoeModel? = withContext(Dispatchers.IO) {
            val engine = EngineProcess.start(context, BINARY, listOf(
                "-m", modelPath, "-t", "${params.threads}", "-c", "${params.nCtx}", "--ubatch", "${minOf(512, params.nCtx)}",
                "--chatml", "--session", "--moe-stream", "--cache-mb", "auto",
            ))
            // Cancelled while it loads (Close BOAR, a model switch), the process is stopped too, not left running.
            val ready = try {
                withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                    while (true) {
                        when (val out = engine.output.receive()) {
                            is EngineProcess.Output.Line -> if (out.text.startsWith("BMOE_READY")) return@withTimeoutOrNull true
                            is EngineProcess.Output.Exit -> return@withTimeoutOrNull false
                            else -> {}
                        }
                    }
                    @Suppress("UNREACHABLE_CODE") false
                } ?: false
            } catch (e: Throwable) {
                engine.stop()
                throw e
            }
            if (!ready) {
                android.util.Log.w(EngineProcess.TAG, "bmoe didn't start: ${engine.tail()}")
                engine.stop()
                null
            } else BmoeModel(engine, params.nCtx)
        }

        private const val LOAD_TIMEOUT_MS = 5 * 60_000L

        /** The chat as one plain prompt: the system text, earlier turns, then the question (bmoe adds ChatML itself). */
        fun flatten(messages: List<ChatMessage>): String {
            val system = messages.filter { it.role == "system" }.map { it.content.trim() }
            val turns = messages.filter { it.role != "system" }
            val last = turns.lastOrNull()
            val history = turns.dropLast(1).map { "${if (it.role == "assistant") "Assistant" else "User"}: ${it.content.trim()}" }
            return (system + (if (history.isEmpty()) emptyList() else listOf("Conversation so far:\n" + history.joinToString("\n"))) + listOfNotNull(last?.content?.trim()))
                .filter { it.isNotEmpty() }.joinToString("\n\n")
        }

        /** What bmoe reports in BMOE_DONE, as the app's metrics (prompt tokens when it says, else 0). */
        private fun metrics(d: JSONObject, firstMs: Double, totalMs: Double): GenerationMetrics {
            val tokens = d.optInt("tokens")
            val tps = d.optDouble("tok_s", 0.0)
            val prefillMs = d.optDouble("prefill_s", 0.0) * 1000
            val promptTokens = d.optInt("prompt_tokens", d.optInt("n_prompt", 0))
            return GenerationMetrics(
                promptTokens = promptTokens, promptTokensDecoded = promptTokens, generatedTokens = tokens,
                ttftMs = if (firstMs >= 0) firstMs else prefillMs, prefillMs = prefillMs,
                decodeMs = if (tps > 0) tokens / tps * 1000 else 0.0,
                prefillTokPerSec = if (prefillMs > 0 && promptTokens > 0) promptTokens / (prefillMs / 1000) else 0.0,
                decodeTokPerSec = tps, totalMs = totalMs, stopReason = d.optString("stop", "done"),
                error = d.optString("error").takeIf { it.isNotEmpty() },
            )
        }
    }
}
