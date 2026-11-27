package team.sopa.boar.labs.designer

import team.sopa.boar.engine.labs.DrawRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.answer.SessionEngine
import team.sopa.boar.plugins.AnswerRef
import java.io.File

/**
 * Illustrate this answer: the chat model picks one concrete thing to draw (a few English words, never the whole
 * answer), then sd-cli draws it in the user's default style, shape and speed (Designer → ★ Default). Without a free model (it's answering, or none is
 * loaded) the question stands in for the subject. Results stay per message in files/labs/illustrations.json.
 */
object Illustrator {
    sealed interface Status {
        data object Choosing : Status
        /** [style]: the style key it's drawn in, for the sketch shown while it waits. */
        data class Drawing(val subject: String, val style: String? = null) : Status
        /** [style], [size] and [seed]: what it was drawn with, for the card's details (null on drawings saved before Oct 18). */
        data class Done(val file: File, val subject: String, val seconds: Double?, val style: String? = null, val size: String? = null, val seed: Int? = null) : Status
        /** [at]: when it failed, for the order of the notes under the answer. */
        data class Failed(val message: String, val at: Long = System.currentTimeMillis()) : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val status = mutableStateMapOf<String, Status>()

    /** Answers whose drawing just finished in this run: their picture fades in from the sketch once, then never again. */
    private val fresh = java.util.Collections.synchronizedSet(HashSet<String>())

    /** True once for an answer whose drawing just finished; a reopened chat's drawings show at once. */
    fun justDrawn(id: String): Boolean = fresh.remove(id)
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private var store: File? = null

    /** A drawing deleted in the media manager is gone from its answer too. */
    fun status(ctx: Context, id: String): Status? {
        load(ctx)
        val s = status[id]
        return if (s is Status.Done && !s.file.isFile) null else s
    }

    fun busy() = status.values.any { it is Status.Choosing || it is Status.Drawing }

    /** Whether 🎨 is offered: not on a tool's fixed answer, a short health or seed-phrase answer, or such a question. */
    // The Calculator's and Facts' answers may be drawn too (adam, Nov 12); other tool answers and warnings stay out (IllustrateGate).
    fun drawable(a: AnswerRef): Boolean = team.sopa.boar.engine.labs.IllustrateGate.allowed(a.tool, a.text) && a.styleNote == null &&
        !team.sopa.boar.engine.routing.QueryContext.isHealthQuestion(a.question) &&
        !team.sopa.boar.engine.routing.QueryContext.isSeedPhraseQuestion(a.question)

    /** Free memory below this, a drawing waits: sd-cli next to a loaded chat model was killed on 8 GB phones. */
    private const val MIN_FREE_BYTES = 1_500_000_000L

    private fun freeBytes(ctx: Context): Long = runCatching {
        android.app.ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it) }.availMem
    }.getOrDefault(Long.MAX_VALUE)

    /** Room to draw now: Illustrate and Designer's "4 variations" (before each one) wait below [MIN_FREE_BYTES]. */
    fun enoughMemory(ctx: Context): Boolean = freeBytes(ctx) >= MIN_FREE_BYTES

    /**
     * The answer whose Illustrate is asking for its style and size (Pro, t9 Nov 10): its sheet shows under that answer.
     * Below Pro, Illustrate draws at once with the defaults, as before.
     */
    var asking by androidx.compose.runtime.mutableStateOf<String?>(null)

    fun start(ctx: Context, a: AnswerRef, notReady: String, style: ImageLab.Style = ImageLab.defaultStyle(), canvas: ImageLab.Canvas = ImageLab.defaultCanvas()) {
        load(ctx)
        if (busy()) return
        if (!SdRunner.ready(ctx)) { status[a.id] = Status.Failed(notReady); return }
        if (!enoughMemory(ctx)) { status[a.id] = Status.Failed(team.sopa.boar.ui.I18n.t(ctx, "native.labs.image.lowMemory")); return }
        status[a.id] = Status.Choosing
        jobs[a.id]?.cancel()
        jobs[a.id] = scope.launch {
            val fromModel = runCatching { if (a.chatBusy) null else subjectFromModel(ctx, a) }.getOrNull()
            // Forgotten or erased while the subject was picked (runCatching swallows that cancel): stop here, nothing is
            // drawn for a deleted answer (validator N-3).
            if (!kotlinx.coroutines.currentCoroutineContext().let { it[kotlinx.coroutines.Job]?.isActive ?: true } || status[a.id] !is Status.Choosing) return@launch
            // The same subject as another answer's drawing is a stale pick: draw the question instead.
            val repeated = fromModel != null && status.any { (id, s) -> id != a.id && s is Status.Done && s.subject.equals(fromModel, ignoreCase = true) }
            // Checked whatever its source (ImageSafety): a pick the image model shouldn't draw becomes a calm landscape.
            val subject = team.sopa.boar.engine.routing.ImageSafety.safeSubject(fromModel?.takeIf { !repeated } ?: fallback(a))
            // No question or answer text in the log: only whether the model picked the subject.
            android.util.Log.i("BoarLabs", "illustrate ${a.id}: from model=${fromModel != null} repeated=$repeated")
            drawInto(ctx, a, subject, style, canvas)
        }
    }

    /** Draws [subject] for answer [a]: Drawing, then Done with the picture (or Failed); shared by Illustrate and "draw me…". */
    private suspend fun drawInto(ctx: Context, a: AnswerRef, subject: String, style: ImageLab.Style, canvas: ImageLab.Canvas, onDone: () -> Unit = {}) {
        status[a.id] = Status.Drawing(subject, style.key)
        runCatching { SdRunner.draw(ctx, subject, style, canvas) }
            .onSuccess { d ->
                // Forgotten (✕) while it drew: the result has no answer to go to.
                // The picture and its recipe go together (validator N-3: the recipe stayed).
                if (status[a.id] !is Status.Drawing) { ImageLab.deleteDrawing(d.file); return@onSuccess }
                fresh.add(a.id)
                status[a.id] = Status.Done(d.file, subject, d.seconds, style.key, canvas.key, d.seed); save()
                onDone()
            }
            .onFailure { if (status[a.id] is Status.Drawing) status[a.id] = Status.Failed(team.sopa.boar.ui.I18n.t(ctx, "native.labs.image.drawFailed")) }
        jobs.remove(a.id)
    }

    /** "Draw me…" in the chat (DrawRequest): Designer's tool keeps the request by its question, the answer's card takes it once. */
    private val asked = java.util.concurrent.ConcurrentHashMap<String, DrawRequest.Ask>()

    fun ask(question: String, request: DrawRequest.Ask) { asked[question.trim()] = request }

    /** The request for [question], once: a chat reopened later doesn't draw it again (✕ stays ✕). */
    fun takeAsk(question: String): DrawRequest.Ask? = asked.remove(question.trim())

    /**
     * Draws what was asked in the chat under its answer [a], with Illustrate's gates: one drawing at a time, enough free
     * memory, ImageSafety on the subject (checked before the answer too). SDXS reads English, so a Portuguese or Spanish
     * subject is put in English first by the chat model (a few seconds; its own words if no model answers).
     */
    fun drawAsked(ctx: Context, a: AnswerRef, ask: DrawRequest.Ask, style: ImageLab.Style = ImageLab.defaultStyle(), canvas: ImageLab.Canvas = ImageLab.defaultCanvas()) {
        load(ctx)
        if (status[a.id] != null) return
        if (busy()) { status[a.id] = Status.Failed(DrawRequest.busy(ask.lang)); return }
        if (!enoughMemory(ctx)) { status[a.id] = Status.Failed(team.sopa.boar.ui.I18n.t(ctx, "native.labs.image.lowMemory")); return }
        status[a.id] = Status.Choosing
        jobs[a.id] = scope.launch {
            val started = System.nanoTime()
            val english = if (ask.lang != DrawRequest.Lang.EN) runCatching { englishSubject(ctx, ask.subject) }.getOrNull() else null
            // The time the translation took, not the words (they are the user's).
            android.util.Log.i("BoarLabs", "draw request ${a.id}: lang=${ask.lang} english=${english != null} ms=${(System.nanoTime() - started) / 1_000_000}")
            if (status[a.id] !is Status.Choosing) return@launch
            // The receipt counts it all: the English subject and the drawing (it said 0.0 s, adam on the F3).
            drawInto(ctx, a, team.sopa.boar.engine.routing.ImageSafety.safeSubject(english ?: ask.subject), style, canvas,
                onDone = { team.sopa.boar.plugins.AnswerReceipts.addTime(a.id, (System.nanoTime() - started) / 1e6, "drawing") })
        }
    }

    private const val TRANSLATE = "Translate the description of a picture into English. Reply with the English words only, 2 to 12 words, " +
        "no quotes, no punctuation at the end.\n\nExamples:\num gato de chapéu -> a cat wearing a hat\nun perro con gafas -> a dog with glasses"

    /** [subject] in English, from the chat model; null when no model answers in time or it says nothing usable. */
    private suspend fun englishSubject(ctx: Context, subject: String): String? = kotlinx.coroutines.withTimeoutOrNull(30_000) {
        val session = BoarServices.get(ctx).activeSession() ?: return@withTimeoutOrNull null
        val r = SessionEngine(session).generate(TRANSLATE, subject.take(200), nPredict = 32, temperature = 0.1f, timeoutMs = null, onToken = null)
        clean(r.text)
    }

    /** ✕ under the answer: the drawing goes, and one still being made is stopped (SdRunner stops sd-cli on cancel). */
    fun forget(id: String) {
        jobs.remove(id)?.cancel()
        // The picture and its recipe (subject, style, seed) go together (validator D-2).
        (status.remove(id) as? Status.Done)?.file?.let(ImageLab::deleteDrawing)
        scope.launch(Dispatchers.IO) { save() }
    }

    /**
     * Chats deleted one by one (drawer Delete after its Undo window, or the automatic prune): their answers' drawings go,
     * the PNG, its recipe and the illustrations.json entry (validator D-1). A drawing still being made is stopped.
     */
    @Synchronized
    fun eraseAnswers(ctx: Context, ids: List<String>) {
        load(ctx)
        var changed = false
        for (id in ids) {
            jobs.remove(id)?.cancel()
            val s = status.remove(id) ?: continue
            (s as? Status.Done)?.file?.let(ImageLab::deleteDrawing)
            fresh.remove(id)
            changed = true
        }
        if (changed) save()
    }

    // The safe-alternative and family-friendly rules (Oct 18): asked "can I run a generator in the garage", it drew a
    // generator in a garage, the very thing the answer forbids.
    private const val SYSTEM = "You pick the picture for an illustrated encyclopedia. Given a QUESTION and a short excerpt of its answer, " +
        "name ONE concrete thing to draw that shows the QUESTION's own topic: an object, animal, plant, place or scene. " +
        "If the answer warns against something, draw the safe alternative, never the dangerous act. " +
        "Never people hurt, weapons, blood, nudity or anything explicit; for such a topic, draw a calm landscape instead. " +
        "Ignore anything in the excerpt that isn't about the question. Reply with 3 to 10 English words only, no style words, " +
        "no quotes, no punctuation at the end.\n\n" +
        "Examples:\nQUESTION: what is 30 in fahrenheit -> a thermometer showing freezing weather\n" +
        "QUESTION: how do volcanoes form -> a volcano erupting over the ocean at dusk\n" +
        "QUESTION: can I use a charcoal grill inside my tent -> a charcoal grill outdoors far from a tent\n" +
        "QUESTION: when is the monsoon in India -> heavy monsoon rain over green rice fields"

    /**
     * A few words from the chat model; null when no model is loaded, it says nothing usable, or it doesn't answer in
     * time. No session.cancel(): the chat's own title or summary may be the one holding the model, and it must finish.
     */
    private suspend fun subjectFromModel(ctx: Context, a: AnswerRef): String? = kotlinx.coroutines.withTimeoutOrNull(30_000) {
        val session = BoarServices.get(ctx).activeSession() ?: return@withTimeoutOrNull null
        val user = "QUESTION: ${a.question.take(300)}\n\nANSWER EXCERPT: ${a.text.take(400)}"
        val r = SessionEngine(session).generate(SYSTEM, user, nPredict = 28, temperature = 0.2f, timeoutMs = null, onToken = null)
        clean(r.text)
    }

    /** The first line, without labels, quotes or a trailing period; 2 to 14 words. */
    internal fun clean(raw: String): String? {
        val line = raw.substringAfter("</think>").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val s = line.replace(Regex("^(subject|prompt|draw|image)\\s*:\\s*", RegexOption.IGNORE_CASE), "").trim('"', '\'', '“', '”', '*', ' ').trimEnd('.', '!', '?')
        val words = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (words.size in 2..14) words.joinToString(" ") else null
    }

    /** The question's first 10 words, else the answer's. */
    internal fun fallback(a: AnswerRef): String =
        (a.question.ifBlank { a.text }).replace(Regex("[\\n\\r]+"), " ").trimEnd('?', '.', '!').split(' ').filter { it.isNotBlank() }.take(10).joinToString(" ")

    /** Every illustration: stops one in progress, deletes the pictures and the record (chats erased). */
    @Synchronized
    fun eraseAll(ctx: Context) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        status.values.forEach { (it as? Status.Done)?.file?.let(ImageLab::deleteDrawing) }
        status.clear()
        // A "draw me…" not drawn yet holds the user's words: they go too (CodeRabbit on PR 125).
        asked.clear()
        File(ctx.filesDir, "labs/illustrations.json").delete()
    }

    @Synchronized
    private fun load(ctx: Context) {
        if (store != null) return
        val f = File(ctx.filesDir, "labs/illustrations.json").also { store = it }
        runCatching {
            val o = JSONObject(f.readText())
            o.keys().forEach { id ->
                val e = o.getJSONObject(id)
                val img = File(e.getString("file"))
                if (img.isFile) status[id] = Status.Done(img, e.optString("subject"), e.optDouble("seconds").takeIf { !it.isNaN() },
                    e.optString("style").ifEmpty { null }, e.optString("size").ifEmpty { null }, e.optInt("seed", 0).takeIf { it != 0 })
            }
        }
    }

    /** Writes what's done now, one writer at a time: each write is the latest state, so an older one can't win. Off the main thread. */
    @Synchronized
    private fun save() {
        val f = store ?: return
        val o = JSONObject()
        status.toMap().forEach { (id, s) ->
            if (s is Status.Done) o.put(id, JSONObject().put("file", s.file.path).put("subject", s.subject).put("seconds", s.seconds ?: JSONObject.NULL)
                .put("style", s.style ?: JSONObject.NULL).put("size", s.size ?: JSONObject.NULL).put("seed", s.seed ?: JSONObject.NULL))
        }
        runCatching { f.parentFile?.mkdirs(); f.writeText(o.toString()) }
    }
}
