package team.sopa.boar.setup

import team.sopa.boar.app.safeScope

import android.app.ActivityManager
import android.app.Application
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.EngineParams
import team.sopa.boar.engine.LlamaSession
import team.sopa.boar.engine.models.AnswerTier
import team.sopa.boar.engine.models.AssetKind
import team.sopa.boar.engine.models.AssetStore
import team.sopa.boar.engine.models.CatalogAsset
import team.sopa.boar.engine.models.SetupGuide
import team.sopa.boar.engine.models.SetupModel
import team.sopa.boar.engine.rag.DocumentImport
import team.sopa.boar.engine.rag.SeedCorpus
import java.io.File
import java.io.IOException

/** One file of the install step, as its row shows it. */
data class AssetProgress(
    val asset: CatalogAsset,
    val bytesDone: Long,
    val verifying: Boolean,
    val done: Boolean,
    val error: String? = null,
    /** Not enough space: the step offers a smaller package. */
    val storage: Boolean = false,
) {
    val moving get() = !done && error == null && (bytesDone > 0 || verifying)
    val fraction get() = if (done) 1f else if (asset.sizeBytes > 0) (bytesDone.toFloat() / asset.sizeBytes).coerceIn(0f, 1f) else 0f
}

/** The two knowledge packages of the setup (port of src/ui/flows/packages.ts): each is a catalog tier. */
enum class SetupPackage(val id: String, val tierId: String) { ESSENTIAL("essential", "full"), ENCYCLOPEDIA("encyclopedia", "encyclopedia") }

/** A package as its card shows it: what it adds, what is left to download, and whether it fits the free space. */
data class PackagePlan(val pkg: SetupPackage, val assets: List<CatalogAsset>, val downloadBytes: Long, val installedBytes: Long, val shortfallBytes: Long)

enum class IndexPhase { WAITING, BUILDING, READY, ERROR }

data class SetupState(
    /** 1 welcome, 2 answer model, 3 knowledge, 4 install (download, then index, then the done screen). */
    val step: Int = 1,
    val totalRamBytes: Long = 0,
    val freeDiskBytes: Long = 0,
    /** The answer models offered: the default and the compact tier. */
    val models: List<CatalogAsset> = emptyList(),
    val recommendedId: String? = null,
    val chosenId: String? = null,
    val plans: List<PackagePlan> = emptyList(),
    val recommendedPackage: SetupPackage? = null,
    val packageId: SetupPackage = SetupPackage.ESSENTIAL,
    val downloads: List<AssetProgress> = emptyList(),
    /** Measured download speed, once 5 s of bytes came in; null before. */
    val etaSeconds: Long? = null,
    /** No byte for a minute while downloading: the step offers to restart the downloads. */
    val stalled: Boolean = false,
    val indexPhase: IndexPhase = IndexPhase.WAITING,
    val indexDone: Int = 0,
    val indexTotal: Int = 0,
    val indexTitle: String = "",
    val indexEtaSeconds: Long? = null,
    val indexError: String? = null,
    /** Counts progress updates: the loading line under the bar changes every few of them, without a timer. */
    val tick: Int = 0,
    /** The pages shown while setup downloads (SetupGuide), and the one on screen. */
    val pages: List<SetupGuide.Page> = emptyList(),
    val guidePage: Int = 0,
    /** The download's details over the guide: a tap on its progress strip, or a problem to fix. */
    val details: Boolean = false,
    /** Files picked on the files page, copied into setup-imports/, and the ones the last finished import took. */
    val files: List<String> = emptyList(),
    val filesImported: List<String> = emptyList(),
    val importing: Boolean = false,
    /** Why a picked file didn't go in, in the user's language. */
    val importErrors: List<String> = emptyList(),
    /** Files skipped because the same content was already picked ("Already added: notes.txt"). */
    val importNotes: List<String> = emptyList(),
) {
    val page get() = pages.getOrNull(guidePage) ?: SetupGuide.Page.READY
    val allPresent get() = downloads.isNotEmpty() && downloads.all { it.done }
    /** Files are still coming in and nothing has failed: no Back, and the "keep BOAR open" card. */
    val transferring get() = downloads.isNotEmpty() && !allPresent && failed.isEmpty()
    val failed get() = downloads.filter { it.error != null }
    val totalBytes get() = downloads.sumOf { it.asset.sizeBytes }
    val doneBytes get() = downloads.sumOf { if (it.done) it.asset.sizeBytes else it.bytesDone }
    val finished get() = indexPhase == IndexPhase.READY
}

/** What survives a killed process, so setup resumes where it was (src/models/settings.ts setupProgress). */
private data class SavedProgress(val step: Int, val packageId: String, val chosenId: String?, val packageChosen: Boolean, val answerChosen: Boolean,
    /** The guide page by name, and whether the pages had the PIN page (SetupGuide.resume). */
    val guidePage: String?, val pinPage: Boolean?)

/**
 * The first-run setup (port of src/ui/SetupWizardScreen.tsx): welcome with the phone's numbers and the language,
 * the answer model (recommended by RAM), the knowledge package (recommended by free space), then the downloads
 * (resumable, verified, retried) and the search index, ending on a summary. Everything after it works offline.
 */
class SetupViewModel(app: Application) : AndroidViewModel(app) {
    private val services = BoarServices.get(app)
    private val store = AssetStore(app.filesDir, services.catalog)
    private val progressFile = File(app.filesDir, "setup_progress.json")
    /** Files picked during setup, kept until they're imported and setup ends; [importRecord] is the last import. */
    private val importDir = File(app.filesDir, "setup-imports")
    private val importRecord = File(importDir, ".import.json")
    private val reader = team.sopa.boar.documents.DocumentReader(app)
    private var importJob: Job? = null
    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state
    private var job: Job? = null
    private var packageChosen = false
    private var answerChosen = false
    /** Bytes and time when downloads were first seen moving, for the measured ETA. */
    private var rateStart: Pair<Long, Long>? = null
    private var lastMove = Pair(0L, System.currentTimeMillis())
    private var indexStart: Pair<Int, Long>? = null
    /** Setup was in its install step when the process ended: the app opens on it, not on the chat. */
    var unfinished = false
        private set

    init {
        val ram = runCatching { ActivityManager.MemoryInfo().also { app.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem }.getOrDefault(0L)
        // Only the built-in suggested models: models added from Hugging Face come in through "Discover GGUF models"
        // (pickDiscovered), one at a time, as the user picks them; a Labs engine's model (Big Boar, Hummingbird) is
        // copied over USB and never set up here.
        val added = services.discovered().map { it.id }.toSet()
        val answer = services.catalog.setupModels.filter { it.engine == null && it.id !in added }
        val rec = SetupModel.recommended(services.catalog.answerModels, ram)
        _state.update { it.copy(totalRamBytes = ram, models = answer, recommendedId = rec?.id, chosenId = rec?.id, pages = guidePages()) }
        restore()
        refreshPlans()
        // Process killed in step 4 (downloading or indexing): pick up there. Files already in are skipped and the
        // index skips the articles it has, so this ends on the summary.
        unfinished = _state.value.step == 4
        _state.update { it.copy(files = pickedNames(), filesImported = imported().second) }
        if (unfinished) install()
        watchStall()
    }

    /** Free space on the phone; -1 when Android doesn't say (a real 0 means the disk is full). */
    private fun freeBytes() = runCatching { StatFs(getApplication<Application>().filesDir.path).availableBytes }.getOrDefault(-1L)

    private fun restore() {
        val saved = runCatching {
            val o = org.json.JSONObject(progressFile.readText())
            SavedProgress(o.getInt("step"), o.getString("packageId"), o.optString("chosenId").ifEmpty { null }, o.optBoolean("packageChosen"), o.optBoolean("answerChosen"),
                o.opt("guidePage") as? String, if (o.has("pinPage")) o.getBoolean("pinPage") else null)
        }.getOrNull() ?: return
        packageChosen = saved.packageChosen
        answerChosen = saved.answerChosen
        // A model picked from Hugging Face before the process ended is listed again, so the choice survives.
        saved.chosenId?.let { id -> services.catalog.byId(id) }?.let { m ->
            _state.update { s -> if (s.models.any { it.id == m.id }) s else s.copy(models = s.models + m) }
        }
        val resumed = SetupGuide.resume(team.sopa.boar.BuildConfig.EXPERIMENTAL_FEATURES, saved.pinPage, services.pinLock.isSet, saved.guidePage)
        _state.update { s ->
            s.copy(
                step = saved.step.coerceIn(1, 4),
                packageId = SetupPackage.entries.firstOrNull { it.id == saved.packageId } ?: s.packageId,
                chosenId = saved.chosenId?.takeIf { id -> s.models.any { it.id == id } && saved.answerChosen } ?: s.chosenId,
                pages = resumed.first,
                guidePage = resumed.second,
            )
        }
    }

    private fun save() {
        val s = _state.value
        runCatching {
            val tmp = File(progressFile.path + ".tmp")
            tmp.writeText(org.json.JSONObject().put("step", s.step).put("packageId", s.packageId.id).put("chosenId", s.chosenId ?: "")
                .put("packageChosen", packageChosen).put("answerChosen", answerChosen).put("guidePage", s.page.name).put("pinPage", SetupGuide.Page.PIN in s.pages).toString())
            tmp.renameTo(progressFile)
        }
    }

    /** Setup is over (finished, or left from Settings): the next start doesn't resume it. */
    fun forget() {
        progressFile.delete()
        running = false
        // Picked files still waiting go in now; the folder goes once nothing is left to import.
        if (importJob?.isActive != true && SetupGuide.pending(pickedNames(), imported().second).isEmpty()) importDir.deleteRecursively() else importQueued()
    }

    /** The guide's pages for this setup: no PIN page when a PIN was set before it started. */
    private fun guidePages() = SetupGuide.pages(team.sopa.boar.BuildConfig.EXPERIMENTAL_FEATURES, services.pinLock.isSet)

    fun guideNext() = setGuide(_state.value.guidePage + 1)
    fun guideBack() = setGuide(_state.value.guidePage - 1)
    /** To a page by name (the Answers page's "Add files"), not by count, so it holds if the pages are reordered. */
    fun guideTo(page: SetupGuide.Page) { _state.value.pages.indexOf(page).takeIf { it >= 0 }?.let(::setGuide) }

    private fun setGuide(page: Int) {
        _state.update { it.copy(guidePage = page.coerceIn(0, maxOf(it.pages.lastIndex, 0)), details = false) }
        save()
    }

    fun showDetails(on: Boolean) = _state.update { it.copy(details = on) }

    // --- Files picked during setup ------------------------------------------------------------------------------------

    private fun pickedNames() = importDir.listFiles { f -> f.isFile && !f.name.startsWith(".") }.orEmpty().map { it.name }.sorted()

    /** The last import: its collection (replaced by the next import) and the files it took. */
    private fun imported(): Pair<String?, List<String>> = runCatching {
        val o = org.json.JSONObject(importRecord.readText())
        val names = o.optJSONArray("files")?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty()
        o.optString("collectionId").ifEmpty { null } to names
    }.getOrDefault(null to emptyList())

    private fun recordImport(collectionId: String?, files: List<String>) {
        val tmp = File(importRecord.path + ".tmp")
        tmp.writeText(org.json.JSONObject().put("collectionId", collectionId ?: "").put("files", org.json.JSONArray(files)).toString())
        tmp.renameTo(importRecord)
    }

    /**
     * The files page's picker: each file is copied into setup-imports/ at once (the picker's permission doesn't last),
     * then imported when it can be (SetupGuide.canImport). A kind BOAR can't read is refused here, with the same line
     * as Knowledge → Personal Files.
     */
    fun addFiles(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        safeScope.launch(Dispatchers.IO) {
            importDir.mkdirs()
            val errors = mutableListOf<String>()
            val notes = mutableListOf<String>()
            // The same file picked twice (same bytes, whatever its name) goes in once.
            val have = pickedNames().mapNotNull { n -> sha256(File(importDir, n))?.let { it to n } }.toMap().toMutableMap()
            for (uri in uris) {
                // The name comes from the app that provides the file: only its last segment, so "../x.txt" stays in
                // setup-imports/, and no leading dot, which would hide it with the folder's own .import.json and .part files.
                val (picked, size) = reader.nameAndSize(uri)
                val name = File(picked).name.trimStart('.')
                if (name.isEmpty() || !team.sopa.boar.documents.DocumentReader.readable(name)) { errors += text("native.documents.unsupported", name); continue }
                // Too large is turned away before the copy, which would take the space the downloads need. A size of 0 is
                // unknown: the import still checks the text it reads.
                if (DocumentImport.tooLargeToRead(size)) { errors += reader.messageFor(DocumentImport.TooLarge(name, "too large")); continue }
                val target = File(importDir, SetupGuide.freeName(name, pickedNames().toSet()))
                // Copied under a hidden name, then renamed: an import on another thread (a download just finished) only
                // sees whole files, and never deletes one still being written.
                val part = File(importDir, ".${target.name}.part")
                val ok = runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input -> part.outputStream().use { input.copyTo(it) } }
                }.isSuccess
                val hash = if (ok) sha256(part) else null
                val same = hash?.let(have::get)
                when {
                    !ok -> { part.delete(); errors += text("native.documents.unreadable", name) }
                    same != null -> { part.delete(); notes += text("native.setupGuide.files.duplicate", same) }
                    !part.renameTo(target) -> { part.delete(); errors += text("native.documents.unreadable", name) }
                    else -> hash?.let { have[it] = target.name }
                }
            }
            _state.update { it.copy(files = pickedNames(), importErrors = errors, importNotes = notes) }
            importQueued()
        }
    }

    private fun sha256(f: File): String? = runCatching {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /**
     * The files page's ✕: the picked file goes. Once an import took it, the collection is rebuilt without it (the next
     * import replaces the last one's collection); with no file left, the collection goes too. After a running import.
     */
    fun removeFile(name: String) {
        safeScope.launch(Dispatchers.IO) {
            importJob?.join()
            File(importDir, name).delete()
            val (lastId, done) = imported()
            val left = pickedNames()
            if (name in done) {
                if (left.isEmpty()) {
                    lastId?.let { services.knowledgeBase.deleteCollection(it) }
                    importRecord.delete()
                } else recordImport(lastId, emptyList()) // all pending again: re-imported without it
            }
            _state.update { it.copy(files = pickedNames(), filesImported = imported().second, importNotes = emptyList(), importErrors = emptyList()) }
            importQueued()
        }
    }

    /**
     * Imports the picked files into one Personal Files collection when it can: called when files are picked, after each
     * download and when the index is done. Each import takes every picked file and replaces the last one's collection,
     * so a second batch, or an import the process didn't finish, never leaves a duplicate or a half collection.
     */
    private fun importQueued(): Unit = synchronized(this) {
        val names = pickedNames()
        val (lastId, done) = imported()
        if (SetupGuide.pending(names, done).isEmpty()) return@synchronized
        val search = services.installed(AssetKind.EMBEDDING).isNotEmpty()
        if (!SetupGuide.canImport(search, _state.value.indexPhase == IndexPhase.BUILDING, importJob?.isActive == true)) return@synchronized
        importJob = safeScope.launch(Dispatchers.IO) {
            _state.update { it.copy(importing = true) }
            val errors = mutableListOf<String>()
            try {
                lastId?.let { services.knowledgeBase.deleteCollection(it) }
                val id = DocumentImport.collectionId(text("native.setupGuide.files.collection"))
                // Recorded before the first chunk: a process killed mid-import leaves a collection the next import removes.
                recordImport(id, emptyList())
                val files = names.mapNotNull { n ->
                    val f = File(importDir, n)
                    try { reader.read(android.net.Uri.fromFile(f), n) } catch (e: Exception) { errors += reader.messageFor(e); f.delete(); null }
                }
                if (files.isNotEmpty()) {
                    DocumentImport.import(services.knowledgeBase, services::embed, files, text("native.setupGuide.files.collection"), id)
                    services.vault.record { v -> v.copy(documents = v.documents + files.count { it.name !in done }) }
                }
                recordImport(id.takeIf { files.isNotEmpty() }, files.map { it.name })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += reader.messageFor(e)
            } finally {
                _state.update { it.copy(importing = false, files = pickedNames(), filesImported = imported().second, importErrors = it.importErrors + errors) }
            }
            // Setup ended meanwhile: the copies aren't needed once everything is in.
            if (!progressFile.exists() && SetupGuide.pending(pickedNames(), imported().second).isEmpty()) importDir.deleteRecursively()
            // Files picked during this import: once this job is over (from inside it, canImport sees it still running).
            else if (SetupGuide.pending(pickedNames(), imported().second).isNotEmpty() && errors.isEmpty())
                coroutineContext[Job]?.invokeOnCompletion { importQueued() }
        }
    }

    private fun text(key: String, name: String? = null) = team.sopa.boar.ui.I18n.t(getApplication(), key, "name" to name)

    private fun setStep(step: Int) {
        _state.update { it.copy(step = step) }
        save()
    }

    /** Settings → Recovery: the wizard from step 1 again. */
    fun restart() {
        job?.cancel()
        job = null
        _state.update { it.copy(step = 1, downloads = emptyList(), indexPhase = IndexPhase.WAITING, indexError = null, stalled = false,
            pages = guidePages(), guidePage = 0, details = false) }
        refreshPlans()
        save()
    }

    fun next() = setStep(minOf(_state.value.step + 1, 3))
    fun back() = setStep(maxOf(_state.value.step - 1, 1))

    /** A model added from Hugging Face in setup's Discover page: listed with the suggested ones and chosen. */
    fun pickDiscovered(id: String) {
        val m = services.catalog.byId(id) ?: return
        _state.update { s -> if (s.models.any { it.id == id }) s else s.copy(models = s.models + m) }
        choose(id)
    }

    fun choose(id: String) {
        answerChosen = true
        _state.update { it.copy(chosenId = id) }
        refreshPlans()
        save()
    }

    fun choosePackage(p: SetupPackage) {
        packageChosen = true
        _state.update { it.copy(packageId = p) }
        save()
    }

    fun isHeavy(m: CatalogAsset) = SetupModel.heavyForPhone(m, _state.value.totalRamBytes)
    fun isPicked(m: CatalogAsset) = SetupModel.picked(m, _state.value.totalRamBytes)

    /** The package's files: the required ones (search model), the chosen answer model and the tier's collections. */
    private fun assetsOf(p: SetupPackage): List<CatalogAsset> {
        val catalog = services.catalog
        val chosen = _state.value.chosenId?.let(catalog::byId)
        val packs = catalog.tier(p.tierId)?.corpusPackIds.orEmpty().mapNotNull(catalog::byId)
        return (catalog.models.filter { it.required && it.kind != AssetKind.LLM } + listOfNotNull(chosen) + packs).distinctBy { it.id }
    }

    /** Sizes, free space and the recommendation (the biggest package that fits), recomputed when the choice changes. */
    fun refreshPlans() {
        val free = freeBytes()
        val plans = SetupPackage.entries.map { p ->
            val assets = assetsOf(p)
            val pending = assets.filter { !store.status(it).present }
            val download = pending.sumOf { it.sizeBytes }
            PackagePlan(p, assets, download, assets.sumOf { it.sizeBytes }, if (free >= 0) maxOf(0, download - free) else 0)
        }
        val rec = (plans.lastOrNull { it.shortfallBytes == 0L } ?: plans.first()).pkg
        _state.update { s -> s.copy(freeDiskBytes = free, plans = plans, recommendedPackage = rec, packageId = if (packageChosen) s.packageId else rec) }
    }

    /** Step 3 → 4: downloads what is missing, then builds the index. */
    fun install() {
        if (job?.isActive == true) return
        val assets = assetsOf(_state.value.packageId)
        _state.value.chosenId?.let { services.settings.setActiveModelId("llm", it) }
        _state.update { s ->
            s.copy(step = 4, stalled = false, downloads = assets.map { a ->
                s.downloads.firstOrNull { it.asset.id == a.id }?.copy(error = null, storage = false) ?: AssetProgress(a, 0, false, store.status(a).present)
            })
        }
        save()
        running = true
        rateStart = null
        lastMove = Pair(_state.value.doneBytes, System.currentTimeMillis())
        job = safeScope.launch(Dispatchers.IO) {
            for (a in assets) {
                if (store.status(a).present) update(a) { it.copy(done = true, bytesDone = a.sizeBytes) }
                else if (!fetch(a)) return@launch
                // The search model may have just arrived: files picked meanwhile can go in.
                importQueued()
            }
            index()
        }
    }

    /** One file, with up to three quiet retries when the connection stalls (the bytes already in are kept). */
    private suspend fun fetch(a: CatalogAsset): Boolean {
        var attempt = 0
        while (true) {
            try {
                var shownAt = 0L
                team.sopa.boar.app.DebugLog.download(a.id, a.sizeBytes) { store.download(a) { p ->
                    // At most 4 screen updates a second: one per downloaded chunk swamped the UI thread.
                    val now = System.currentTimeMillis()
                    val verifying = p.phase == AssetStore.Progress.Phase.VERIFYING
                    if (now - shownAt >= 250 || verifying || p.bytesDone >= p.bytesTotal) {
                        shownAt = now
                        update(a) { it.copy(bytesDone = p.bytesDone, verifying = verifying) }
                        moved()
                    }
                } }
                update(a) { it.copy(done = true, bytesDone = a.sizeBytes, verifying = false) }
                moved()
                refreshPlans()
                return true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val transient = e is AssetStore.DownloadException.Paused || (e is IOException && e !is AssetStore.DownloadException)
                if (transient && attempt++ < 3) { delay(5_000L * attempt); continue }
                update(a) { it.copy(error = team.sopa.boar.ui.I18n.t(getApplication(), team.sopa.boar.engine.UserErrors.downloadKey(e), *team.sopa.boar.engine.UserErrors.downloadArgs(e)), verifying = false, storage = e is AssetStore.DownloadException.Storage) }
                return false
            }
        }
    }

    private fun moved() {
        val s = _state.value
        val now = System.currentTimeMillis()
        val bytes = s.doneBytes
        if (bytes != lastMove.first) lastMove = Pair(bytes, now)
        val start = rateStart ?: Pair(bytes, now).also { rateStart = it }
        val elapsed = (now - start.second) / 1000.0
        val bps = (bytes - start.first) / maxOf(elapsed, 1.0)
        val eta = if (elapsed >= 5 && bps > 0) ((s.totalBytes - bytes) / bps).toLong() else null
        _state.update { it.copy(etaSeconds = eta, stalled = false, tick = it.tick + 1) }
    }

    /** No byte for a minute while a download runs: offer to restart (the Expo wizard's STALL_MS). */
    private fun watchStall() = safeScope.launch(Dispatchers.Default) {
        while (isActive) {
            delay(5_000)
            val s = _state.value
            val running = job?.isActive == true && s.step == 4 && !s.allPresent
            val stalled = running && System.currentTimeMillis() - lastMove.second > STALL_MS
            if (stalled != s.stalled) _state.update { it.copy(stalled = stalled) }
        }
    }

    /** Back in the app: retry what retrying can fix (stalls, network), as the Expo wizard does on AppState active. */
    fun onForeground() {
        val s = _state.value
        if (s.step == 4 && job?.isActive != true && s.failed.isNotEmpty() && s.failed.none { it.storage }) install()
    }

    /** "Restart downloads": drops the running transfer and starts again from the bytes on disk. */
    fun restartDownloads() {
        job?.cancel()
        job = null
        install()
    }

    fun retry() = install()

    private fun update(a: CatalogAsset, change: (AssetProgress) -> AssetProgress) =
        _state.update { s -> s.copy(downloads = s.downloads.map { if (it.asset.id == a.id) change(it) else it }) }

    /** Runs the index again after it failed (the embedder didn't load, or seeding stopped). */
    fun retryIndex() {
        if (job?.isActive == true) return
        job = safeScope.launch(Dispatchers.IO) { index() }
    }

    private suspend fun index() {
        // BUILDING first, so no import starts; one already running finishes before the index embeds.
        val importing = synchronized(this) { _state.update { it.copy(indexPhase = IndexPhase.BUILDING, indexError = null) }; importJob }
        importing?.join()
        indexStart = null
        val app = getApplication<Application>()
        val emb = services.installed(AssetKind.EMBEDDING).firstOrNull() ?: run { indexFailed(team.sopa.boar.ui.I18n.t(getApplication(), "native.setup.indexNoEmbedder")); return }
        val embedder = LlamaSession.load(services.fileOf(emb).path, EngineParams(nCtx = 512, threads = 2, embeddings = true))
            ?: run { indexFailed(team.sopa.boar.ui.I18n.t(getApplication(), "native.setup.indexEmbedderFailed")); return }
        try {
            val builtin = app.assets.open("corpus/corpus.json").bufferedReader().use { it.readText() }
            val collections = listOf(SeedCorpus.Collection(SeedCorpus.BUILTIN, SeedCorpus.docs(SeedCorpus.BUILTIN, builtin))) +
                services.catalog.corpusCatalog.filter { it.format != "sqlite-pack" && services.fileOf(it).isFile }.map { p ->
                    runCatching { SeedCorpus.Collection(p.id, SeedCorpus.docs(p.id, services.fileOf(p).readText())) }
                        .getOrElse { e -> SeedCorpus.Collection(p.id, emptyList(), e.message) }
                }
            var shownAt = 0L
            SeedCorpus(services.knowledgeBase) { text -> embedder.embed(text) }.seed(collections, onProgress = { p ->
                val now = System.currentTimeMillis()
                if (now - shownAt >= 250 || p.done >= p.total) {
                    shownAt = now
                    val start = indexStart ?: Pair(p.done, now).also { indexStart = it }
                    val elapsed = (now - start.second) / 1000.0
                    val indexed = p.done - start.first
                    val eta = if (elapsed >= 3 && indexed >= 5) ((p.total - p.done) * elapsed / indexed).toLong() else null
                    _state.update { it.copy(indexDone = p.done, indexTotal = p.total, indexTitle = p.title, indexEtaSeconds = eta, tick = it.tick + 1) }
                }
            })
            services.reloadPacks()
            _state.update { it.copy(indexPhase = IndexPhase.READY) }
            importQueued()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            indexFailed(team.sopa.boar.ui.I18n.t(getApplication(), "native.setup.indexFailed"))
        } finally {
            // Also after a cancel (Restart, Recovery): the native session must not leak.
            withContext(NonCancellable + Dispatchers.IO) { embedder.close() }
        }
    }

    private fun indexFailed(message: String) = _state.update { it.copy(indexPhase = IndexPhase.ERROR, indexError = message) }

    /** Whether the app can answer: everything required and an answer model on disk (requiredModelsPresent). */
    fun ready(): Boolean = store.requiredPresent()

    /** The answer model's tier, for its friendly name ("More accurate" / "Fast"). */
    fun tierOf(m: CatalogAsset) = if (m.answerTier == AnswerTier.COMPACT) "compact" else "default"

    companion object {
        private const val STALL_MS = 60_000L

        /**
         * Setup's install step is running in this process (Install pressed, not yet finished or left). MainActivity
         * keeps the setup screen up while it is, and doesn't lock BOAR before there's a chat to protect.
         */
        @Volatile var running = false
            private set
    }
}
