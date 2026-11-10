package team.sopa.boar.labs.canvas

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.answer.SessionEngine
import team.sopa.boar.engine.canvas.CardSpec
import team.sopa.boar.engine.canvas.WidgetHtml
import team.sopa.boar.plugins.AnswerRef
import java.io.File

/**
 * What Boar Canvas made for each answer, and making it: the chat model, given the question and the finished answer,
 * writes a card spec (CardSpec) or a widget page (WidgetHtml). Only the model's reply is kept, in files/canvas/<answer
 * id>.json, so a reopened chat shows it again; erased with the chats.
 */
internal object Canvas {
    enum class Kind { CARD, WIDGET }

    sealed interface Status {
        data class Making(val kind: Kind) : Status
        /** [raw]: the model's reply, parsed again when drawn (cheap, and a parser fix applies to old ones). */
        data class Done(val kind: Kind, val raw: String, val seconds: Double) : Status
        data class Failed(val key: String) : Status
    }

    private val status = mutableStateMapOf<String, Status>()
    private val jobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun busy() = status.values.any { it is Status.Making }

    /** Not a tool's fixed answer, nor a health or seed-phrase one: a made-up dose calculator or wallet tool is what BOAR must never draw. */
    fun allowed(a: AnswerRef): Boolean = a.tool == null && a.styleNote == null && a.text.isNotBlank() &&
        !team.sopa.boar.engine.routing.QueryContext.isHealthQuestion(a.question) &&
        !team.sopa.boar.engine.routing.QueryContext.isSeedPhraseQuestion(a.question)

    fun status(ctx: Context, id: String): Status? = status[id] ?: load(ctx, id)?.also { status[id] = it }

    fun start(ctx: Context, a: AnswerRef, kind: Kind) {
        if (busy() || a.chatBusy) return
        status[a.id] = Status.Making(kind)
        jobs[a.id] = scope.launch {
            val started = System.nanoTime()
            val r = runCatching { generate(ctx, a, kind) }
            // Forgotten (✕) while it was being made.
            if (status[a.id] !is Status.Making) return@launch
            val raw = r.getOrNull()
            val rejected = when {
                raw == null -> if (r.exceptionOrNull() != null) "native.labs.canvas.failed" else "native.labs.canvas.noModel"
                kind == Kind.CARD -> (CardSpec.parse(raw, a.text) as? CardSpec.Parsed.Rejected)?.let(::rejectedKey)
                else -> "native.labs.canvas.noWidget".takeIf { WidgetHtml.extract(raw) == null }
            }
            status[a.id] = if (rejected != null || raw == null) Status.Failed(rejected ?: "native.labs.canvas.failed")
            else Status.Done(kind, raw, (System.nanoTime() - started) / 1e9).also { save(ctx, a.id, it) }
            // No question or answer text in the log, as the other labs.
            android.util.Log.i("BoarLabs", "canvas ${a.id}: $kind -> ${status[a.id]?.javaClass?.simpleName}")
            jobs.remove(a.id)
        }
    }

    fun forget(ctx: Context, id: String) {
        jobs.remove(id)?.cancel()
        status.remove(id)
        file(ctx, id).delete()
    }

    fun eraseAll(ctx: Context) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        status.clear()
        dir(ctx).deleteRecursively()
    }

    private fun rejectedKey(r: CardSpec.Parsed.Rejected) = when (r.reason) {
        CardSpec.Reason.NOTHING -> "native.labs.canvas.nothing"
        CardSpec.Reason.INVENTED_NUMBERS -> "native.labs.canvas.invented"
        else -> "native.labs.canvas.noCard"
    }

    /** The model's reply; null when no model is loaded. No session.cancel() of its own: the chat's Stop cancels it. */
    private suspend fun generate(ctx: Context, a: AnswerRef, kind: Kind): String? {
        val session = BoarServices.get(ctx).activeSession() ?: return null
        val user = "QUESTION: ${a.question.take(400)}\n\nANSWER:\n${a.text.take(3000)}"
        val r = when (kind) {
            Kind.CARD -> SessionEngine(session).generate(CARD_SYSTEM, user, nPredict = 700, temperature = 0.2f, timeoutMs = 180_000, onToken = null)
            Kind.WIDGET -> SessionEngine(session).generate(WIDGET_SYSTEM, user, nPredict = 2000, temperature = 0.3f, timeoutMs = 360_000, onToken = null)
        }
        return r.text
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "canvas")
    private fun file(ctx: Context, id: String) = File(dir(ctx), "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    private fun save(ctx: Context, id: String, d: Status.Done) = runCatching {
        dir(ctx).mkdirs()
        file(ctx, id).writeText(JsonObject(mapOf("kind" to JsonPrimitive(d.kind.name), "raw" to JsonPrimitive(d.raw), "seconds" to JsonPrimitive(d.seconds))).toString())
    }

    private fun load(ctx: Context, id: String): Status.Done? = runCatching {
        val o = Json.parseToJsonElement(file(ctx, id).takeIf { it.isFile }?.readText() ?: return null).jsonObject
        Status.Done(Kind.valueOf((o["kind"] as JsonPrimitive).content), (o["raw"] as JsonPrimitive).content, (o["seconds"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0)
    }.getOrNull()

    // Grounded like BOAR's answers: only what the answer says. "none" is a fine reply, better than a forced card.
    private const val CARD_SYSTEM = "You turn an encyclopedia answer into ONE visual card for a phone screen. Reply with ONE JSON object and nothing else.\n" +
        "Pick the kind that fits the ANSWER best:\n" +
        "- several things with the same properties: {\"type\":\"table\",\"title\":\"...\",\"columns\":[\"...\",\"...\"],\"rows\":[[\"...\",\"...\"]]}\n" +
        "- several numbers of the same kind: {\"type\":\"bars\",\"title\":\"...\",\"unit\":\"...\",\"items\":[{\"label\":\"...\",\"value\":123}]}\n" +
        "- a process or how-to: {\"type\":\"steps\",\"title\":\"...\",\"steps\":[\"...\",\"...\"]}\n" +
        "- two to four options side by side: {\"type\":\"compare\",\"title\":\"...\",\"items\":[{\"name\":\"...\",\"points\":[\"...\",\"...\"]}]}\n" +
        "- key facts about one thing: {\"type\":\"facts\",\"title\":\"...\",\"facts\":[{\"label\":\"...\",\"value\":\"...\"}]}\n" +
        "Use ONLY facts and numbers written in the ANSWER; never add or guess any. Keep every text short (under 12 words). " +
        "If no card would help, reply {\"type\":\"none\"}."

    // The page runs with no network: everything inline, and the app's colors as CSS variables.
    private const val WIDGET_SYSTEM = "You build ONE small interactive web widget that helps a reader explore an encyclopedia answer on a phone: " +
        "a calculator, a converter, a quiz, a timeline, a slider that shows how something changes, or a simple diagram.\n" +
        "Reply with ONE complete HTML page in a ```html block and nothing else.\n" +
        "Rules: everything inline in one file (<style> and <script>); no external files, links, images, fonts or network requests; " +
        "plain JavaScript, no libraries. Use the CSS variables var(--bg), var(--fg), var(--muted), var(--accent), var(--card) for colors. " +
        "Fit a 360 px wide screen. Keep it under 150 lines. Use ONLY facts and numbers from the ANSWER; label any formula with where it comes from."
}
