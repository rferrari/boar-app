package team.sopa.boar.labs.printer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import team.sopa.boar.engine.labs.PressDoc
import team.sopa.boar.engine.labs.PressFiles
import java.io.File

/**
 * Press's projects (adam's mock-up, Nov 13), kept in files/labs/press/projects.json (PressDoc's JSON), written whole and
 * swapped in (AtomicFile), so a crash can't leave half a file. Each project also has a folder,
 * files/labs/press/<project id>/, for what it owns (phase 2's photos); it goes with the project.
 *
 * The single basket of earlier versions (files/labs/basket.json) becomes the first project, once: only while there is no
 * projects file, and the basket file is deleted only after the projects file is written.
 */
object PressStore {
    private val _library = MutableStateFlow<PressDoc.Library?>(null)
    /** Null until loaded (or while the projects file can't be read). */
    val library: StateFlow<PressDoc.Library?> = _library
    private val _unreadable = MutableStateFlow(false)
    /** The projects file is there but can't be read: the screen says so, and nothing is written over it. */
    val unreadable: StateFlow<Boolean> = _unreadable
    private var file: android.util.AtomicFile? = null
    /** Read successfully (or nothing saved yet). Until then nothing is written, so a file that failed to read is never replaced. */
    private var loaded = false
    private val lock = Any()

    fun dir(context: Context) = File(context.filesDir, "labs/press")
    /** A project's own folder (phase 2: its photos, copied in from the phone). */
    fun projectDir(context: Context, projectId: String) = team.sopa.boar.engine.labs.PressFiles.projectDir(dir(context), projectId)
    private fun projectsFile(context: Context) = android.util.AtomicFile(File(dir(context), "projects.json"))
    private fun basketFile(context: Context) = File(context.filesDir, "labs/basket.json")
    /** The old basket couldn't be read once: it is tried again when Press loads (G-3). */
    private fun pendingMarker(context: Context) = File(dir(context), "basket-pending")

    fun newId(prefix: String) = prefix + "-" + java.util.UUID.randomUUID().toString().take(8)

    fun load(context: Context) = synchronized(lock) {
        if (loaded) return@synchronized
        val f = projectsFile(context).also { file = it }
        val projects: String? = if (f.baseFile.exists()) runCatching { String(f.readFully(), Charsets.UTF_8) }.getOrElse { "" } else null
        val basket = basketFile(context).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrElse { "" } }
        // The basket's document title, if the user gave one, names the first project; else "Field notes".
        val name = team.sopa.boar.labs.LabPrefs.string("printer.title")?.trim()?.takeIf { it.isNotEmpty() }
            ?: team.sopa.boar.ui.I18n.t(context, "native.labs.printer.untitled")
        val now = System.currentTimeMillis()
        when (val r = PressDoc.load(projects, basket, newId("p"), name, now, PressPictures.illustrations(context))) {
            is PressDoc.Loaded.Read -> {
                var lib = r.library
                // The old basket couldn't be read the first time (G-3): try again, once a launch, until it can.
                val pending = pendingMarker(context)
                if (pending.isFile && basket != null) runCatching { PressDoc.mergeBasket(lib, basket, newId("p"), name, now, PressPictures.illustrations(context)) }
                    .onSuccess { merged -> if (merged == lib || write(merged)) { lib = merged; basketFile(context).delete(); pending.delete() } }
                    .onFailure { android.util.Log.w("BoarLabs", "press: the old basket still can't be read; will retry") }
                else if (pending.isFile) pending.delete()
                _library.value = lib; loaded = true; _unreadable.value = false
                // Copies no project uses any more (a photo removed past its Undo, a cut-short copy): only after a good read.
                runCatching { PressFiles.orphans(dir(context), lib).forEach { it.deleteRecursively() } }
            }
            is PressDoc.Loaded.Migrated -> {
                // Written first; only then the basket goes. A failed write leaves the basket for the next try.
                if (write(r.library)) {
                    _library.value = r.library; loaded = true
                    basketFile(context).delete()
                }
            }
            is PressDoc.Loaded.Fresh -> {
                // Not written until the first change; a basket that can't be read stays where it is.
                if (r.basketUnreadable) {
                    android.util.Log.w("BoarLabs", "press: the old basket can't be read; kept as is, retried on the next launch")
                    runCatching { dir(context).mkdirs(); pendingMarker(context).writeText("1") }
                }
                _library.value = r.library; loaded = true
            }
            is PressDoc.Loaded.Unreadable -> {
                loaded = false
                _unreadable.value = true
                android.util.Log.w("BoarLabs", "press: projects file unreadable, kept as is; will retry", r.error)
            }
        }
    }

    /** One change at a time, state and file together. Nothing happens before a successful load. */
    fun change(f: (PressDoc.Library) -> PressDoc.Library) = synchronized(lock) {
        val cur = _library.value
        if (!loaded) return@synchronized
        if (cur == null) return@synchronized
        val next = f(cur)
        if (next == cur) return@synchronized
        if (write(next)) _library.value = next
    }

    /** An answer (and the drawing it already has) at the end of the open project. */
    /** Every answer of a chat, into the open project at the end ("Add to Press" in the menu, and Press's own strip). Off the main thread. */
    fun addChat(context: Context, sessionId: String, title: String) {
        Thread {
                val msgs = runCatching { team.sopa.boar.app.BoarServices.get(context).chats.messages(sessionId) }.getOrDefault(emptyList())
                var question = ""
                for (m in msgs) {
                    if (m.role == "user") { question = m.text; continue }
                    if (m.role != "assistant" || m.text.isBlank()) continue
                    val sources = runCatching {
                        val arr = org.json.JSONObject(m.meta ?: "{}").optJSONArray("sources") ?: org.json.JSONArray()
                        (0 until arr.length()).map { i -> arr.getJSONObject(i).let { o -> o.optString("title") to o.optString("source").takeIf { it.isNotEmpty() } } }
                    }.getOrDefault(emptyList())
                    PressStore.addAnswer(context, PrinterLab.answerBlock(m.id, title, question, m.text, sources, null, null,
                        team.sopa.boar.engine.canvas.AnswerCard.fromMeta(m.meta)), listOfNotNull(PressPictures.ofAnswer(context, m.id)))
                }
            }.start()
    }

    fun addAnswer(context: Context, a: PressDoc.Block.Answer, after: List<PressDoc.Block> = emptyList()) {
        load(context)
        change { it.addAnswer(a, after) }
    }

    /** The answers of a deleted chat leave every project (adam chose (a), Nov 12); the header's count follows. */
    fun eraseAnswers(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        load(context)
        change { it.eraseAnswers(ids) }
    }

    /** How many of [ids] are in any project. */
    fun countOf(context: Context, ids: Collection<String>): Int {
        load(context)
        return _library.value?.countOf(ids) ?: 0
    }

    /**
     * A photo block was removed: its copy goes when the Undo is over, if no block uses it again (validator G-1: it stayed
     * until the next cold start). The cleanup at load stays as the safety net.
     */
    fun dropPhotoLater(context: Context, projectId: String, name: String, afterMs: Long) {
        val app = context.applicationContext
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            synchronized(lock) {
                val lib = _library.value ?: return@synchronized
                if (loaded && PressFiles.unused(lib, projectId, name)) runCatching { PressFiles.photoFile(dir(app), projectId, name).delete() }
            }
        }, afterMs)
    }

    /** Deletes a project and its folder (phase 2: its photos with it). */
    fun deleteProject(context: Context, id: String): Boolean {
        var gone = false
        // The last project can go too: an empty "Untitled" one takes its place (adam, Nov 14).
        val now = System.currentTimeMillis()
        val blank = PressDoc.Project(newId("p"), team.sopa.boar.ui.I18n.t(context, "native.labs.printer.untitled"), now, seenAt = now)
        change { lib -> lib.deleteProject(id, blank).also { gone = it !== lib } }
        if (gone) PressFiles.deleteProject(dir(context), id)
        return gone
    }

    /** Erased with the chats (Forgot PIN, Clear all): every project and its folder, and the old basket, even if unreadable. */
    fun eraseAll(context: Context) = synchronized(lock) {
        PressFiles.eraseAll(dir(context))
        basketFile(context).delete()
        file = projectsFile(context)
        val now = System.currentTimeMillis()
        _library.value = PressDoc.emptyLibrary(newId("p"), team.sopa.boar.ui.I18n.t(context, "native.labs.printer.untitled"), now)
        _unreadable.value = false
        loaded = true
    }

    private fun write(lib: PressDoc.Library): Boolean {
        val out = file ?: return false
        out.baseFile.parentFile?.mkdirs()
        val stream = runCatching { out.startWrite() }.getOrElse { android.util.Log.w("BoarLabs", "press not saved", it); return false }
        return try {
            stream.write(PressDoc.toJson(lib).toByteArray(Charsets.UTF_8))
            out.finishWrite(stream)
            true
        } catch (e: java.io.IOException) {
            out.failWrite(stream)
            android.util.Log.w("BoarLabs", "press not saved", e)
            false
        }
    }
}
