package team.sopa.boar.labs.canvas

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.answer.SessionEngine
import team.sopa.boar.engine.canvas.AnswerCard
import team.sopa.boar.engine.canvas.Visual
import team.sopa.boar.engine.canvas.VisualContext
import team.sopa.boar.plugins.AnswerRef
import java.io.File

/**
 * Cards' state for each answer (adam's interview, Nov 14; t9 0207 item 3).
 *
 * - The card shown under an answer, and its checklist's ticks, are kept in that answer's own message meta ("card" and
 *   "cardTicks", AnswerCard.withCard / withTicks), as the receipt is: adam, "ticks kept with the chat". So they go with
 *   the chat (deleting it, Forgot PIN, Clear all) with nothing of their own to erase, and Press reads the card from there.
 * - The model's card (the "slower" row, only when the rules find nothing) is made here, as Visual does it: the model's
 *   JSON, checked against the answer, the earlier answers and the sources, what isn't there left out. While it is being
 *   made, or when it failed, the state is in memory only.
 * - Before Cards, model cards were kept in files/canvas/<answer id>.json (and a widget beside it, "~widget"). The first
 *   time such an answer is drawn, its card moves into the meta and the file goes; a widget's file just goes (the widget
 *   is gone, adam Nov 14). eraseAnswers and eraseAll still remove the folder's files.
 */
internal object Canvas {
    sealed interface Status {
        /** The model is reading the answer. */
        data object Making : Status
        /** [detail]: what was refused (a number, a label); [raw]: the model's reply, to see where it went wrong (this run only). */
        data class Failed(val key: String, val detail: String? = null, val raw: String? = null) : Status
    }

    /** The model path's state, by answer id. */
    private val status = mutableStateMapOf<String, Status>()
    /** The card shown under each answer, as read from its meta (absent: not read yet). */
    private val shown = mutableStateMapOf<String, Shown>()
    /** The ticks of each answer's checklist, as read from its meta. */
    private val ticks = mutableStateMapOf<String, Set<Int>>()
    private val jobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A wrapper so "read, no card" (card null) differs from "not read yet" (no entry). [text]: the answer's text when it
     * was read; an answer written again under the same id is read again (its meta was rewritten without the card).
     */
    data class Shown(val card: AnswerCard?, val text: Int = 0)

    fun busy() = status.values.any { it is Status.Making }

    /** Not a tool's fixed answer, nor a health or seed-phrase one: a made-up dose table or wallet card is what BOAR must never draw. */
    fun allowed(a: AnswerRef): Boolean = a.tool == null && a.styleNote == null && a.text.isNotBlank() &&
        !team.sopa.boar.engine.routing.QueryContext.isHealthQuestion(a.question) &&
        !team.sopa.boar.engine.routing.QueryContext.isSeedPhraseQuestion(a.question)

    fun status(id: String): Status? = status[id]

    /** The card shown under [a], read from its meta in the background the first time (null meanwhile, and when none). */
    fun shown(ctx: Context, a: AnswerRef): AnswerCard? {
        val s = shown[a.id]?.takeIf { it.text == a.text.hashCode() }
        if (s == null) load(ctx, a)
        return s?.card
    }

    fun ticksOf(id: String): Set<Int> = ticks[id] ?: emptySet()

    private val loading = mutableSetOf<String>()

    private fun load(ctx: Context, a: AnswerRef) {
        if (!synchronized(loading) { loading.add(a.id) }) return
        scope.launch {
            val meta = runCatching { chats(ctx).messageMeta(a.id) }.getOrNull()
            var card = AnswerCard.fromMeta(meta)
            if (card == null) card = runCatching { fromOldFile(ctx, a) }.getOrNull()
            ticks[a.id] = AnswerCard.ticksFromMeta(meta)
            shown[a.id] = Shown(card, a.text.hashCode())
            synchronized(loading) { loading.remove(a.id) }
        }
    }

    /** Shows [card] under answer [id] (or removes it, null), saved in the answer's meta. */
    fun show(ctx: Context, id: String, card: AnswerCard?, text: String) {
        shown[id] = Shown(card, text.hashCode())
        if (card == null) { ticks[id] = emptySet(); status.remove(id) }
        scope.launch { writeMeta(ctx, id) { AnswerCard.withCard(it, card) } }
    }

    /** Ticks or unticks step [i] of answer [id]'s checklist, saved with the answer. */
    fun tick(ctx: Context, id: String, i: Int) {
        val now = ticksOf(id).let { if (i in it) it - i else it + i }
        ticks[id] = now
        scope.launch { writeMeta(ctx, id) { AnswerCard.withTicks(it, now) } }
    }

    /** Read, change and write the meta in order, so two quick ticks can't undo each other. */
    private fun writeMeta(ctx: Context, id: String, change: (String?) -> String?) = synchronized(this) {
        runCatching {
            val store = chats(ctx)
            // An answer with no meta row yet is a message all the same: the card or ticks start one.
            change(store.messageMeta(id))?.let { store.setMessageMeta(id, it) }
        }.onFailure { android.util.Log.w("BoarLabs", "cards: meta not saved for $id") }
    }

    private fun chats(ctx: Context) = BoarServices.get(ctx).chats

    /** The model's card for [a], only when the rules found nothing (the "slower" row). */
    fun start(ctx: Context, a: AnswerRef, fallbackTitle: String) {
        if (busy() || a.chatBusy) return
        val id = a.id
        // The chat's model, held as Press's rewrite holds it: a question sent meanwhile waits, saying why, and nothing
        // else starts on it (validator R-E5). Busy now: said, not queued behind it.
        val services = team.sopa.boar.app.BoarServices.get(ctx)
        if (!services.claimModelForLab()) { status[id] = Status.Failed("native.labs.canvas.busy"); return }
        status[id] = Status.Making
        jobs[id] = scope.launch {
          try {
            val none = VisualContext.Earlier(emptyList(), "")
            val earlier = runCatching { earlier(ctx, a) }.getOrDefault(none)
            // A number the answer leaves out may be in its own sources (Brazil's area in "Economy of Brazil").
            val fromSources = VisualContext.fromSources(a.question, a.text, a.passages)
            val context = listOf(earlier.sourceText, fromSources.sourceText).filter { it.isNotBlank() }.joinToString("\n")
            val r = runCatching { generate(ctx, a, earlier, fromSources) }
            val raw = r.getOrNull()
            val parsed = raw?.let { Visual.parse(it, grounding(a, context), a.question) }
            // The reason in the log (never the answer's text); the refused number or label only on screen.
            (parsed as? Visual.Parsed.Rejected)?.let { android.util.Log.i("BoarLabs", "cards $id: refused ${it.reason}") }
            synchronized(this@Canvas) {
                // Removed (✕) or erased while it was being made: nothing is kept.
                if (status[id] !is Status.Making) return@launch
                when (parsed) {
                    is Visual.Parsed.Ok -> { status.remove(id); show(ctx, id, AnswerCard.ofVisual(parsed.visual, parsed.leftOut, fallbackTitle, a.text), a.text) }
                    is Visual.Parsed.Rejected -> status[id] = Status.Failed(rejectedKey(parsed), parsed.detail?.take(60), raw)
                    null -> status[id] = Status.Failed(if (r.exceptionOrNull() != null) "native.labs.canvas.failed" else "native.labs.canvas.noModel")
                }
            }
            android.util.Log.i("BoarLabs", "cards $id: model -> ${status[id]?.javaClass?.simpleName ?: "card"}")
            jobs.remove(id)
          } finally { services.releaseModelForLab() }
        }
    }

    /** Stops the model's card for [id] and forgets its state (✕ on a wait or a failure). */
    @Synchronized
    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        status.remove(id)
    }

    /** A deleted chat's answers: their message meta goes with the chat; here the memory and any file from before Cards. */
    @Synchronized
    fun forget(ctx: Context, id: String) {
        cancel(id)
        shown.remove(id); ticks.remove(id)
        file(ctx, id).delete(); file(ctx, "$id$WIDGET_SUFFIX").delete()
    }

    @Synchronized
    fun eraseAll(ctx: Context) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        status.clear(); shown.clear(); ticks.clear()
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
     * The earlier answers' and the sources' sentences a model card of [a] reads and is checked against, read from the
     * chat (never stored).
     */
    fun contextFor(ctx: Context, a: AnswerRef): String {
        val none = VisualContext.Earlier(emptyList(), "")
        val earlier = runCatching { earlier(ctx, a) }.getOrDefault(none)
        val sources = VisualContext.fromSources(a.question, a.text, a.passages)
        return listOf(earlier.sourceText, sources.sourceText).filter { it.isNotBlank() }.joinToString("\n")
    }

    /** What a model card's numbers and labels are checked against: the answer and the earlier facts the model read. */
    fun grounding(a: AnswerRef, context: String): String = if (context.isBlank()) a.text else a.text + "\n" + context

    /** The earlier exchanges of this chat, before this answer (VisualContext keeps the ones on the same topic). */
    private fun earlier(ctx: Context, a: AnswerRef): VisualContext.Earlier {
        val session = a.sessionId ?: return VisualContext.Earlier(emptyList(), "")
        val messages = chats(ctx).messages(session).takeWhile { it.id != a.id }
        val exchanges = ArrayList<VisualContext.Exchange>()
        var question: String? = null
        for (m in messages) {
            if (m.role == "user") question = m.text
            else if (m.role == "assistant" && question != null) {
                exchanges += VisualContext.Exchange(question, team.sopa.boar.engine.answer.AnswerText.stripThinking(m.text))
                question = null
            }
        }
        return VisualContext.earlier(VisualContext.Exchange(a.question, a.text), exchanges)
    }

    /** The model's reply; null when no model is loaded. No session.cancel() of its own: the chat's Stop cancels it. */
    private suspend fun generate(ctx: Context, a: AnswerRef, earlier: VisualContext.Earlier, sources: VisualContext.Earlier): String? {
        val session = BoarServices.get(ctx).activeSession() ?: return null
        return SessionEngine(session).generate(Visual.PROMPT, VisualContext.prompt(a.question, a.text, earlier, sources),
            nPredict = 700, temperature = 0.2f, timeoutMs = 180_000, onToken = null).text
    }

    // ---- Files from before Cards ----

    private const val WIDGET_SUFFIX = "~widget"
    private fun dir(ctx: Context) = File(ctx.filesDir, "canvas")
    private fun file(ctx: Context, id: String) = File(dir(ctx), "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    /**
     * A model card saved before Cards (files/canvas/<id>.json, its reply only): parsed and checked again as before, kept in
     * the answer's meta from now on, and the file removed. A widget's file is removed: nothing draws it any more.
     */
    private fun fromOldFile(ctx: Context, a: AnswerRef): AnswerCard? {
        file(ctx, "${a.id}$WIDGET_SUFFIX").delete()
        val f = file(ctx, a.id).takeIf { it.isFile } ?: return null
        val o = Json.parseToJsonElement(f.readText()).jsonObject
        val kind = (o["kind"] as? JsonPrimitive)?.content
        val raw = (o["raw"] as? JsonPrimitive)?.content
        val card = if (kind != "WIDGET" && raw != null) (Visual.parse(raw, grounding(a, contextFor(ctx, a)), a.question) as? Visual.Parsed.Ok)
            ?.let { AnswerCard.ofVisual(it.visual, it.leftOut, a.sources.firstOrNull()?.first ?: a.question.take(60), a.text) } else null
        if (card != null) writeMeta(ctx, a.id) { AnswerCard.withCard(it, card) }
        f.delete()
        return card
    }
}
