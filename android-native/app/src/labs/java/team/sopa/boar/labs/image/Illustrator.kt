package team.sopa.boar.labs.image

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
 * answer), then sd-cli draws it in the default style at 384 px. Without a free model (it's answering, or none is
 * loaded) the question stands in for the subject. Results stay per message in files/labs/illustrations.json.
 */
object Illustrator {
    sealed interface Status {
        data object Choosing : Status
        data class Drawing(val subject: String) : Status
        data class Done(val file: File, val subject: String, val seconds: Double?) : Status
        data class Failed(val message: String) : Status
    }

    const val SIZE = 384
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val status = mutableStateMapOf<String, Status>()
    private var store: File? = null

    fun status(ctx: Context, id: String): Status? { load(ctx); return status[id] }

    fun busy() = status.values.any { it is Status.Choosing || it is Status.Drawing }

    fun start(ctx: Context, a: AnswerRef, notReady: String) {
        load(ctx)
        if (busy()) return
        if (!SdRunner.ready(ctx)) { status[a.id] = Status.Failed(notReady); return }
        status[a.id] = Status.Choosing
        scope.launch {
            val fromModel = runCatching { if (a.chatBusy) null else subjectFromModel(ctx, a) }.getOrNull()
            // The same subject as another answer's drawing is a stale pick: draw the question instead.
            val repeated = fromModel != null && status.any { (id, s) -> id != a.id && s is Status.Done && s.subject.equals(fromModel, ignoreCase = true) }
            val subject = fromModel?.takeIf { !repeated } ?: fallback(a)
            android.util.Log.i("BoarLabs", "illustrate ${a.id}: question=\"${a.question.take(80)}\" model=\"$fromModel\" repeated=$repeated -> \"$subject\"")
            status[a.id] = Status.Drawing(subject)
            runCatching { SdRunner.draw(ctx, subject, ImageLab.defaultStyle(), SIZE) }
                .onSuccess { d -> status[a.id] = Status.Done(d.file, subject, d.seconds); save() }
                .onFailure { e -> status[a.id] = Status.Failed(e.message ?: "failed") }
        }
    }

    fun forget(id: String) { (status.remove(id) as? Status.Done)?.file?.delete(); save() }

    private const val SYSTEM = "You pick the picture for an illustrated encyclopedia. Given a QUESTION and a short excerpt of its answer, " +
        "name ONE concrete thing to draw that shows the QUESTION's own topic: an object, animal, plant, place or scene. " +
        "Ignore anything in the excerpt that isn't about the question. Reply with 3 to 10 English words only, no style words, " +
        "no quotes, no punctuation at the end.\n\n" +
        "Examples:\nQUESTION: what is 30 in fahrenheit -> a thermometer showing freezing weather\n" +
        "QUESTION: how do I treat a snake bite -> a bandaged ankle beside a snake on a trail\n" +
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

    @Synchronized
    private fun load(ctx: Context) {
        if (store != null) return
        val f = File(ctx.filesDir, "labs/illustrations.json").also { store = it }
        runCatching {
            val o = JSONObject(f.readText())
            o.keys().forEach { id ->
                val e = o.getJSONObject(id)
                val img = File(e.getString("file"))
                if (img.isFile) status[id] = Status.Done(img, e.optString("subject"), e.optDouble("seconds").takeIf { !it.isNaN() })
            }
        }
    }

    private fun save() {
        val f = store ?: return
        val o = JSONObject()
        status.forEach { (id, s) -> if (s is Status.Done) o.put(id, JSONObject().put("file", s.file.path).put("subject", s.subject).put("seconds", s.seconds ?: JSONObject.NULL)) }
        scope.launch { withContext(Dispatchers.IO) { f.parentFile?.mkdirs(); f.writeText(o.toString()) } }
    }
}
