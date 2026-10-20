package team.sopa.boar.labs.colibri

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
import team.sopa.boar.engine.ChatMessage
import team.sopa.boar.engine.EngineParams
import team.sopa.boar.engine.FormattedPrompt
import team.sopa.boar.engine.GenerationEvent
import team.sopa.boar.engine.GenerationMetrics
import team.sopa.boar.engine.Sampling
import team.sopa.boar.engine.TextModel
import team.sopa.boar.labs.engines.EngineProcess

/**
 * OLMoE-1B-7B on colibri's pure-C engine in serve mode (SERVE=1): "SUBMIT <id> 0 <bytes> <n> <temp> <top_p>" then the
 * prompt on stdin; "DATA <id> <size>" frames and "DONE <id> STAT <tokens> <tok/s> <cache hit %>" on stdout. It keeps
 * [CACHE_PER_LAYER] of each layer's 64 experts in RAM and streams the rest from storage. Ported from the
 * experimental-engines branch (src/inference/externalEngines.ts, ColibriEngine).
 */
class ColibriModel private constructor(private val engine: EngineProcess) : TextModel {
    private val lock = Mutex()
    private var nextId = 1
    @Volatile private var current: String? = null

    override val hasChatTemplate = true
    override val isGenerating: Boolean get() = current != null
    @Volatile override var onGenerated: ((GenerationMetrics) -> Unit)? = null

    override suspend fun formatChat(messages: List<ChatMessage>, enableThinking: Boolean) =
        FormattedPrompt(renderOlmoe(messages), supportsThinking = false, thinkingStart = "", thinkingEnd = emptyList(), stops = emptyList())

    override fun generate(prompt: String, sampling: Sampling): Flow<GenerationEvent> = callbackFlow {
        lock.withLock {
            val id = "r${nextId++}"
            current = id
            val t0 = System.nanoTime()
            var firstMs = -1.0
            try {
                engine.write("SUBMIT $id 0 ${prompt.toByteArray(Charsets.UTF_8).size} ${sampling.nPredict} ${sampling.temperature} ${sampling.topP}\n$prompt\n")
                while (true) {
                    when (val out = engine.output.receive()) {
                        is EngineProcess.Output.Exit -> error("colibri stopped (code ${out.code}): ${engine.tail()}")
                        is EngineProcess.Output.Data -> if (out.id == id) {
                            if (firstMs < 0) firstMs = (System.nanoTime() - t0) / 1e6
                            trySend(GenerationEvent.Piece(out.text))
                        }
                        is EngineProcess.Output.Line -> when {
                            out.text.startsWith("DONE $id STAT") -> {
                                val (tok, tps) = out.text.split(' ').drop(3).map { it.toDoubleOrNull() ?: 0.0 }.let { it.getOrElse(0) { 0.0 } to it.getOrElse(1) { 0.0 } }
                                val total = (System.nanoTime() - t0) / 1e6
                                val m = GenerationMetrics(
                                    promptTokens = 0, promptTokensDecoded = 0, generatedTokens = tok.toInt(), ttftMs = firstMs, prefillMs = maxOf(0.0, firstMs),
                                    decodeMs = if (tps > 0) tok / tps * 1000 else 0.0, prefillTokPerSec = 0.0, decodeTokPerSec = tps, totalMs = total,
                                    stopReason = "done", error = null,
                                )
                                runCatching { onGenerated?.invoke(m) }
                                send(GenerationEvent.Done(m))
                                break
                            }
                            out.text.startsWith("ERR $id") -> error("colibri: ${out.text}")
                        }
                    }
                }
            } finally {
                current = null
            }
        }
        close()
        awaitClose { }
    }.buffer(kotlinx.coroutines.channels.Channel.UNLIMITED).flowOn(Dispatchers.IO) // never drop a piece of the answer

    override fun cancel() {
        current?.let { runCatching { engine.write("CANCEL $it\n") } }
    }

    override fun close() = engine.stop()

    companion object {
        const val BINARY = "libcolibri_olmoe.so"
        /** Experts kept in RAM per layer (of 64): 16 measured fastest on the X6 (about 3 GB peak), Sep 26. */
        const val CACHE_PER_LAYER = 16

        /** Starts colibri on the converted model folder [snap] and waits for READY; null when it exits first. */
        suspend fun start(context: Context, snap: String, params: EngineParams): ColibriModel? = withContext(Dispatchers.IO) {
            val engine = EngineProcess.start(context, BINARY, listOf("$CACHE_PER_LAYER", "8"),
                mapOf("SERVE" to "1", "SNAP" to snap, "CTX" to "${params.nCtx}", "OMP_NUM_THREADS" to "${params.threads}"))
            // Cancelled while it loads, the process is stopped too, not left running.
            val ready = try {
                withTimeoutOrNull(3 * 60_000L) {
                    var ok = false
                    while (true) {
                        val out = engine.output.receive()
                        if (out is EngineProcess.Output.Line && out.text.contains("READY")) { ok = true; break }
                        if (out is EngineProcess.Output.Exit) break
                    }
                    ok
                } ?: false
            } catch (e: Throwable) {
                engine.stop()
                throw e
            }
            if (!ready) {
                android.util.Log.w(EngineProcess.TAG, "colibri didn't start: ${engine.tail()}")
                engine.stop()
                null
            } else ColibriModel(engine)
        }

        /** OLMoE-Instruct's chat template, as colibri's own chat mode renders it (bos = eos = "|||IP_ADDRESS|||"). */
        fun renderOlmoe(messages: List<ChatMessage>): String {
            val eos = "|||IP_ADDRESS|||"
            val sb = StringBuilder(eos)
            for (m in messages) when (m.role) {
                "system" -> sb.append("<|system|>\n${m.content}\n")
                "assistant" -> sb.append("<|assistant|>\n${m.content}$eos\n")
                else -> sb.append("<|user|>\n${m.content}\n")
            }
            return sb.append("<|assistant|>\n").toString()
        }
    }
}
