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
import team.sopa.boar.engine.canvas.Visual
import team.sopa.boar.engine.canvas.VisualContext
import team.sopa.boar.engine.canvas.WidgetHtml
import team.sopa.boar.plugins.AnswerRef
import java.io.File

/**
 * What Boar Canvas made for each answer, and making it: the chat model, given the question and the finished answer,
 * writes a visual (Visual: the model picks the type and the data, the app checks and draws it) or a widget page (WidgetHtml). Only the model's reply is kept, in files/canvas/<answer
 * id>.json, so a reopened chat shows it again; erased with the chats.
 */
internal object Canvas {
    /** VISUAL: the merged visual (Visual). CARD: one made before the merge, drawn by the same code. WIDGET: model-written HTML. */
    enum class Kind { CARD, WIDGET, VISUAL }

    sealed interface Status {
        data class Making(val kind: Kind) : Status
        /**
         * [raw]: the model's reply, parsed again when drawn (cheap, and a parser fix applies to old ones). [context]: the
         * earlier answers' sentences the model read (VisualContext), kept so the grounding check reads the same text later.
         */
        data class Done(val kind: Kind, val raw: String, val seconds: Double, val context: String = "") : Status
        /** [detail]: what was refused (a number, a label), shown after the message so the user sees what the model got wrong. */
        data class Failed(val key: String, val detail: String? = null, val raw: String? = null) : Status
    }

    private val status = mutableStateMapOf<String, Status>()
    private val jobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun busy() = status.values.any { it is Status.Making }

    /** Not a tool's fixed answer, nor a health or seed-phrase one: a made-up dose calculator or wallet tool is what BOAR must never draw. */
    fun allowed(a: AnswerRef): Boolean = a.tool == null && a.styleNote == null && a.text.isNotBlank() &&
        !team.sopa.boar.engine.routing.QueryContext.isHealthQuestion(a.question) &&
        !team.sopa.boar.engine.routing.QueryContext.isSeedPhraseQuestion(a.question)

    fun status(ctx: Context, id: String): Status? = status[id] ?: load(ctx, id)?.let { done ->
        // A widget saved before the merge sits under the answer's own id: moved to the widget's place the first time
        // it's read, so "Create a card" makes a card beside it instead of writing over it (validator C-1).
        if (done.kind == Kind.WIDGET && !id.endsWith(WIDGET_SUFFIX)) {
            val widgetKey = keyOf(id, Kind.WIDGET)
            if (load(ctx, widgetKey) == null) save(ctx, widgetKey, done)
            file(ctx, id).delete()
            status[widgetKey] = done
            null
        } else done.also { status[id] = it }
    }

    /**
     * Where a kind is kept for an answer: the card (the model's pick: chart, table, steps…) under the answer's id, the
     * interactive widget beside it (one "Create a card" since Nov 10, the widget built only when asked). A widget saved
     * before the merge sits under the answer's id and is still drawn.
     */
    fun keyOf(id: String, kind: Kind): String = if (kind == Kind.WIDGET) "$id$WIDGET_SUFFIX" else id
    private const val WIDGET_SUFFIX = "~widget"

    fun start(ctx: Context, a: AnswerRef, kind: Kind) {
        if (busy() || a.chatBusy) return
        val key = keyOf(a.id, kind)
        status[key] = Status.Making(kind)
        jobs[key] = scope.launch {
            val started = System.nanoTime()
            val none = VisualContext.Earlier(emptyList(), "")
            val earlier = if (kind == Kind.WIDGET) none else runCatching { earlier(ctx, a) }.getOrDefault(none)
            // A number the answer leaves out may be in its own sources (Brazil's area in "Economy of Brazil").
            val fromSources = if (kind == Kind.WIDGET) none else VisualContext.fromSources(a.question, a.text, a.passages)
            val context = listOf(earlier.sourceText, fromSources.sourceText).filter { it.isNotBlank() }.joinToString("\n")
            val r = runCatching { generate(ctx, a, kind, earlier, fromSources) }
            // The parse and checks run first, outside the lock: a long table check must not make a ✕ or Clear all wait
            // (validator M-3). Only the "still wanted?" check and the save are under it.
            val raw = r.getOrNull()
            val refusal = if (raw != null && kind != Kind.WIDGET) Visual.parse(raw, grounding(a, context), a.question) as? Visual.Parsed.Rejected else null
            val rejected = when {
                raw == null -> if (r.exceptionOrNull() != null) "native.labs.canvas.failed" else "native.labs.canvas.noModel"
                kind != Kind.WIDGET -> refusal?.let(::rejectedKey)
                else -> "native.labs.canvas.noWidget".takeIf { WidgetHtml.extract(raw) == null }
            }
            // The reason in the log (never the answer's text); the refused number or label only on screen.
            refusal?.let { android.util.Log.i("BoarLabs", "canvas ${a.id}: refused ${it.reason}") }
            // Forgotten (✕) or erased while it was being made: checked and saved under the lock that forget and eraseAll
            // take, so an erase can't finish between the check and the save and the file come back (CodeRabbit).
            synchronized(this@Canvas) {
                if (status[key] !is Status.Making) return@launch
                status[key] = if (rejected != null || raw == null) Status.Failed(rejected ?: "native.labs.canvas.failed", refusal?.detail?.take(60), raw)
                else Status.Done(kind, raw, (System.nanoTime() - started) / 1e9, context).also { save(ctx, key, it) }
            }
            // No question or answer text in the log, as the other labs.
            android.util.Log.i("BoarLabs", "canvas ${a.id}: $kind -> ${status[key]?.javaClass?.simpleName}")
            jobs.remove(key)
        }
    }

    @Synchronized
    fun forget(ctx: Context, id: String) {
        jobs.remove(id)?.cancel()
        status.remove(id)
        file(ctx, id).delete()
    }

    @Synchronized
    fun eraseAll(ctx: Context) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        status.clear()
        dir(ctx).deleteRecursively()
    }

    private fun rejectedKey(r: Visual.Parsed.Rejected) = when (r.reason) {
        Visual.Reason.NOTHING -> "native.labs.canvas.nothing"
        Visual.Reason.INVENTED_NUMBERS -> "native.labs.canvas.invented"
        Visual.Reason.UNGROUNDED_LABELS -> "native.labs.canvas.ungrounded"
        Visual.Reason.UNPAIRED -> "native.labs.canvas.unpaired"
        else -> "native.labs.canvas.noCard"
    }

    /**
     * The earlier answers' and the sources' sentences a visual of [a] reads and is checked against, read from the chat
     * (never stored): the same text the model had, as long as the chat exists.
     */
    fun contextFor(ctx: Context, a: AnswerRef): String {
        val none = VisualContext.Earlier(emptyList(), "")
        val earlier = runCatching { earlier(ctx, a) }.getOrDefault(none)
        val sources = VisualContext.fromSources(a.question, a.text, a.passages)
        return listOf(earlier.sourceText, sources.sourceText).filter { it.isNotBlank() }.joinToString("\n")
    }

    /** What a visual's numbers and labels are checked against: the answer and the earlier facts the model read. */
    fun grounding(a: AnswerRef, context: String): String = if (context.isBlank()) a.text else a.text + "\n" + context

    /**
     * Step one of a visual, no model: the earlier exchanges of this chat, before this answer (VisualContext keeps the
     * ones on the same topic and their sentences with numbers).
     */
    private fun earlier(ctx: Context, a: AnswerRef): VisualContext.Earlier {
        val session = a.sessionId ?: return VisualContext.Earlier(emptyList(), "")
        val messages = BoarServices.get(ctx).chats.messages(session).takeWhile { it.id != a.id }
        val exchanges = ArrayList<VisualContext.Exchange>()
        var question: String? = null
        for (m in messages) {
            if (m.role == "user") question = m.text
            else if (m.role == "assistant" && question != null) {
                exchanges += VisualContext.Exchange(question, team.sopa.boar.engine.answer.AnswerText.stripThinking(m.text))
                question = null
            }
        }
        // The last one is this answer's own question, already in the prompt.
        return VisualContext.earlier(VisualContext.Exchange(a.question, a.text), exchanges)
    }

    /** The model's reply; null when no model is loaded. No session.cancel() of its own: the chat's Stop cancels it. */
    private suspend fun generate(ctx: Context, a: AnswerRef, kind: Kind, earlier: VisualContext.Earlier, sources: VisualContext.Earlier): String? {
        val session = BoarServices.get(ctx).activeSession() ?: return null
        val user = if (kind == Kind.WIDGET) "QUESTION: ${a.question.take(400)}\n\nANSWER:\n${a.text.take(3000)}"
        else VisualContext.prompt(a.question, a.text, earlier, sources)
        val r = when (kind) {
            // The merged visual's prompt for a card too: CARD is only for ones saved before the merge.
            Kind.VISUAL, Kind.CARD -> SessionEngine(session).generate(Visual.PROMPT, user, nPredict = 700, temperature = 0.2f, timeoutMs = 180_000, onToken = null)
            Kind.WIDGET -> SessionEngine(session).generate(WIDGET_SYSTEM, user, nPredict = 2000, temperature = 0.3f, timeoutMs = 360_000, onToken = null)
        }
        return r.text
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "canvas")
    private fun file(ctx: Context, id: String) = File(dir(ctx), "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    private fun save(ctx: Context, id: String, d: Status.Done) = runCatching {
        dir(ctx).mkdirs()
        // Only the model's reply: the earlier answers' and sources' sentences aren't copied to disk (validator P-1); they're
        // read again from the chat when the visual is drawn (contextFor).
        file(ctx, id).writeText(JsonObject(mapOf("kind" to JsonPrimitive(d.kind.name), "raw" to JsonPrimitive(d.raw), "seconds" to JsonPrimitive(d.seconds))).toString())
    }

    private fun load(ctx: Context, id: String): Status.Done? = runCatching {
        val o = Json.parseToJsonElement(file(ctx, id).takeIf { it.isFile }?.readText() ?: return null).jsonObject
        val done = Status.Done(Kind.valueOf((o["kind"] as JsonPrimitive).content), (o["raw"] as JsonPrimitive).content, (o["seconds"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0)
        // A file from the x6-visual build (fcb6c1ae) still holds the earlier answers' text: written again without it
        // the first time it's read (validator P-1).
        if ("context" in o) save(ctx, id, done)
        done
    }.getOrNull()

    // The page runs with no network: everything inline, and the app's colors as CSS variables.
    private const val WIDGET_SYSTEM = "You build ONE small interactive web widget that helps a reader explore an encyclopedia answer on a phone: " +
        "a calculator, a converter, a quiz, a timeline, a slider that shows how something changes, or a simple diagram.\n" +
        "Reply with ONE complete HTML page in a ```html block and nothing else.\n" +
        "Rules: everything inline in one file (<style> and <script>); no external files, links, images, fonts or network requests; " +
        "plain JavaScript, no libraries. Use the CSS variables var(--bg), var(--fg), var(--muted), var(--accent), var(--card) for colors. " +
        "Fit a 360 px wide screen. Keep it under 150 lines. Use ONLY facts and numbers from the ANSWER; label any formula with where it comes from."
}
