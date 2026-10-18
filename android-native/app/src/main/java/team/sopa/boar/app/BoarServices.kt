package team.sopa.boar.app

import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import team.sopa.boar.engine.BoarEngine
import team.sopa.boar.engine.CpuTopology
import team.sopa.boar.engine.EngineParams
import team.sopa.boar.engine.LlamaSession
import team.sopa.boar.engine.TextModel
import team.sopa.boar.engine.models.LoadGuard
import team.sopa.boar.engine.answer.ModelHost
import team.sopa.boar.engine.answer.Personalities
import team.sopa.boar.engine.answer.SessionEngine
import team.sopa.boar.engine.answer.StepEngine
import team.sopa.boar.engine.chat.ChatStore
import team.sopa.boar.engine.db.NativeSqlite
import team.sopa.boar.engine.models.AssetKind
import team.sopa.boar.engine.models.Catalog
import team.sopa.boar.engine.models.CatalogAsset
import team.sopa.boar.engine.rag.KnowledgeBase
import team.sopa.boar.engine.rag.OpenPack
import team.sopa.boar.engine.rag.Packs
import team.sopa.boar.engine.rag.Retriever
import team.sopa.boar.engine.rag.WikiPack
import team.sopa.boar.engine.settings.AppSettings
import java.io.File

/**
 * Everything the screens share, opened once per process on the Expo app's own files (files/settings.json,
 * files/SQLite/aoair_knowledge.db, files/models, files/corpus), so an upgrade keeps models, chats and settings.
 * One generation model is loaded at a time ([host]); the embedder loads when retrieval first needs it.
 */
class BoarServices private constructor(private val context: Context) {
    private val files = context.filesDir
    /** The app's files dir, where models, packs and the chat database live. */
    val filesDir: java.io.File get() = files
    val settings = AppSettings(File(files, "settings.json"))
    private val baseCatalog: Catalog = Catalog.parse(asset("boar-data/catalog.json"))
    private val discoveredFile = File(files, "discovered_models.json")
    private val discoveredJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Models added from the Hugging Face search (discoveredModels.ts, same file as the Expo app): not vetted, so kept
     * apart from the built-in catalog and listed after it.
     */
    fun discovered(): List<CatalogAsset> = runCatching {
        discoveredJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(CatalogAsset.serializer()), discoveredFile.readText())
    }.getOrDefault(emptyList())

    /** Written to a temporary file, then moved over the old one: a failed write never leaves a truncated list. */
    private fun writeDiscovered(list: List<CatalogAsset>) {
        val tmp = File(files, "discovered_models.json.tmp")
        tmp.writeText(discoveredJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(CatalogAsset.serializer()), list))
        java.nio.file.Files.move(tmp.toPath(), discoveredFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }

    /** The built-in catalog plus the discovered models (a search result already in the catalog isn't listed twice). */
    @Volatile private var ownCatalog: Catalog = withDiscovered()

    /** [ownCatalog] plus the models of the Boar Labs engine plugins switched on now (team.sopa.boar.plugins.EnginePlugins). */
    val catalog: Catalog get() {
        val extra = team.sopa.boar.plugins.EnginePlugins.models()
        return if (extra.isEmpty()) ownCatalog else ownCatalog.copy(models = ownCatalog.models + extra.filter { e -> ownCatalog.models.none { it.id == e.id } })
    }

    private fun withDiscovered(): Catalog {
        val extra = discovered().filter { d -> baseCatalog.models.none { it.filename == d.filename } }
        return baseCatalog.copy(models = baseCatalog.models + extra)
    }

    fun addDiscovered(asset: CatalogAsset) = synchronized(this) {
        val all = discovered()
        if (all.none { it.id == asset.id }) { writeDiscovered(all + asset); settings.stampModel(AppSettings.MODEL_ADDED, asset.id) }
        ownCatalog = withDiscovered()
    }

    fun removeDiscovered(id: String) = synchronized(this) {
        writeDiscovered(discovered().filter { it.id != id })
        ownCatalog = withDiscovered()
    }
    /**
     * The answer styles. The native app starts on Detailed (the Expo app on Succinct); a style the user picked is kept.
     * The evaluation runner keeps the shared default, so its runs compare with the Expo app's.
     */
    val personalities: Personalities = Personalities.parse(asset("boar-data/personalities.json")).copy(defaultId = DEFAULT_PERSONALITY_ID)
    val topology: CpuTopology = CpuTopology.read()
    /** Total RAM, for the Auto context size; 0 when Android doesn't say. */
    val totalRamBytes: Long = runCatching {
        android.app.ActivityManager.MemoryInfo().also { context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) }.totalMem
    }.getOrDefault(0L)

    private val kbDb = NativeSqlite.open(File(files, "SQLite").apply { mkdirs() }.resolve("aoair_knowledge.db").path)
    val knowledgeBase = KnowledgeBase(kbDb)
    /** A consistent copy of the knowledge base at [to] (VACUUM INTO), for the retrieval parity check. */
    fun exportKnowledgeBase(to: File) = kbDb.exec("VACUUM INTO '${to.path.replace("'", "''")}'")
    val chats = ChatStore(kbDb)
    /** The PIN that opens the app and seals the chats (none until the user sets one). */
    val pinLock = team.sopa.boar.security.PinLock(settings, chats)
    /** The Truffle Vault: boar icons unearthed by using the app, counted on the phone. */
    val vault = team.sopa.boar.appearance.TruffleVault(context, settings)

    /** The vault's facts that come from the phone and the app's files: on start and after leaving a screen that changes them. */
    fun checkVault() = runCatching {
        val packs = catalog.corpusCatalog.count { it.format == "sqlite-pack" && File(files, it.filename).isFile } +
            File(files, "corpus").listFiles { f -> f.name.endsWith(".sqlite") }.orEmpty().count { f -> catalog.corpusCatalog.none { File(files, it.filename) == f } }
        vault.onOpen(pinOn = pinLock.isSet, packs = packs, hfModels = discovered().size)
    }

    private fun asset(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }

    /** Portuguese article names for PT questions (assets/lexicon/pt-en.json), loaded on first use. */
    val lexicon: Map<String, String> by lazy {
        runCatching { Json.parseToJsonElement(asset("lexicon/pt-en.json")).jsonObject.mapValues { it.value.jsonPrimitive.content } }.getOrDefault(emptyMap())
    }

    /** Where [asset] is on the phone: under the app's files, or its [CatalogAsset.externalPath] (placed over USB). */
    fun fileOf(asset: CatalogAsset) = asset.externalPath?.let(::File) ?: File(files, asset.filename)

    /** Installed: a file, or an engine plugin's model folder. */
    fun isInstalled(asset: CatalogAsset) = fileOf(asset).let { it.isFile || (asset.externalPath != null && it.isDirectory) }
    /** Everything BOAR keeps on the phone: models, packs, the knowledge base with imported documents, chats, settings. */
    fun storageUsedBytes(): Long = files.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun installed(kind: AssetKind) = catalog.models.filter { it.kind == kind && isInstalled(it) }

    /** The user's model, else the first installed one (the catalog's order puts the default first). */
    fun activeModel(): CatalogAsset? =
        settings.activeModelId("llm")?.let(catalog::byId)?.takeIf(::isInstalled) ?: installed(AssetKind.LLM).firstOrNull()

    /** CR-2: the marker next to the models and the bookkeeping in settings.json. */
    val loadGuard = LoadGuard(object : LoadGuard.MarkerStore {
        private val f = File(files, "model-load.marker.json")
        override fun read() = f.takeIf { it.isFile }?.readText()
        override fun write(json: String) = f.writeText(json)
        override fun clear() { f.delete() }
    }, settings.loadLedger)

    /**
     * The model the chat answers with: the active one, unless its last load killed the app (CR-2). Then the model loaded
     * before it, else the smallest installed one that isn't blocked, so a phone doesn't crash on every start.
     */
    fun answerModel(): CatalogAsset? {
        val m = activeModel() ?: return null
        if (!loadGuard.isBlocked(m.id)) return m
        val llms = installed(AssetKind.LLM)
        val pick = team.sopa.boar.engine.models.StartLoad.fallback(llms.map(::startModel), loadGuard::isBlocked, settings.lastLoadedModelId)
        return pick?.let { p -> llms.firstOrNull { it.id == p.id } }
    }

    /** A model a Labs engine streams from storage (Big Boar) isn't held to the RAM check: it never loads whole. */
    private fun startModel(a: CatalogAsset) = team.sopa.boar.engine.models.StartLoad.Model(a.id, a.sizeBytes, streamed = !runsOnBuiltIn(a))

    /** What the chat may load on its own now (StartLoad): a blocked model never, a RED one only after "Load anyway". */
    fun startDecision(): team.sopa.boar.engine.models.StartLoad.Decision =
        team.sopa.boar.engine.models.StartLoad.decide(activeModel()?.let(::startModel), installed(AssetKind.LLM).map(::startModel),
            loadGuard::isBlocked, settings.lastLoadedModelId, totalRamBytes, settings.loadAnywayIds.toSet())

    /** Rated RED for this phone's memory (Compatibility, as the Model Catalog shows it). */
    fun isRisky(a: CatalogAsset): Boolean = team.sopa.boar.engine.models.StartLoad.risky(startModel(a), totalRamBytes)

    /** "Load anyway": [modelId] loads at start from now on without asking. */
    fun rememberLoadAnyway(modelId: String) {
        val ids = settings.loadAnywayIds
        if (modelId !in ids) settings.loadAnywayIds = ids + modelId
    }

    // --- Packs and retrieval -----------------------------------------------------------------------------------

    private val embeddingSha = catalog.models.first { it.kind == AssetKind.EMBEDDING && it.required }.sha256

    @Volatile private var openPacks: List<OpenPack>? = null

    /** Installed knowledge packs in knowledgePacks()'s order: catalog packs first, then other files by name. */
    val packs: List<OpenPack> get() = openPacks ?: synchronized(this) { openPacks ?: scanPacks().also { openPacks = it } }

    /** After a pack is downloaded or removed: the next search opens the current set. */
    fun reloadPacks() = synchronized(this) {
        openPacks?.forEach { p -> runCatching { when (p) { is OpenPack.Format1 -> p.db.close(); is OpenPack.Format2 -> p.pack.close() } } }
        openPacks = null
    }

    private fun scanPacks(): List<OpenPack> {
        val dir = File(files, "corpus")
        val catalogFiles = catalog.corpusCatalog.filter { it.format == "sqlite-pack" }.map { it.id to File(files, it.filename) }
        val local = dir.listFiles { f -> f.name.endsWith(".sqlite") }.orEmpty().sortedBy { it.name }
            .filter { f -> catalogFiles.none { it.second == f } }.map { "local-${it.name.removeSuffix(".sqlite")}" to it }
        return (catalogFiles + local).filter { it.second.isFile }.mapNotNull { (id, f) ->
            runCatching {
                val db = NativeSqlite.open(f.path, readOnly = true)
                val meta = db.query("SELECT key, value FROM meta") { it.string(0)!! to (it.string(1) ?: "") }.toMap()
                when (Packs.packFormat(meta, embeddingSha)) {
                    1 -> OpenPack.Format1(id, db)
                    2 -> OpenPack.Format2(id, WikiPack.open(db, NativeSqlite.zstd))
                    else -> { db.close(); null }
                }
            }.getOrNull()
        }
    }

    private val embedLock = Mutex()
    private var embedder: LlamaSession? = null

    /** The embedding model, loaded on first use; an empty vector when none is installed. */
    suspend fun embed(text: String): FloatArray = embedLock.withLock {
        val e = embedder ?: run {
            val f = installed(AssetKind.EMBEDDING).firstOrNull()?.let(::fileOf) ?: return FloatArray(0)
            LlamaSession.load(f.path, EngineParams(nCtx = 512, threads = 2, embeddings = true)).also { embedder = it }
        } ?: return FloatArray(0)
        e.embed(text)
    }

    /**
     * Search for an answer: the built-in retrieval, then the Labs search stages (team.sopa.boar.plugins.PipelineStages),
     * which get twice [topK] passages to choose from. Every answer path calls this, not the retriever directly.
     */
    fun search(query: String, topK: Int): List<team.sopa.boar.engine.rag.Chunk> {
        val stages = runCatching { team.sopa.boar.plugins.PipelineStages.search() }.getOrDefault(emptyList())
        if (stages.isEmpty()) return retriever.retrieve(query, topK)
        var chunks = retriever.retrieve(query, topK * 2)
        // A stage that fails is skipped, never fatal: the answer keeps what search found.
        for (st in stages) chunks = runCatching { st.process(query, chunks, topK) }.getOrDefault(chunks)
        return chunks.take(topK)
    }

    // --- Memory pressure -----------------------------------------------------------------------------------------

    private val lifeScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    private var backgroundRelease: kotlinx.coroutines.Job? = null

    /**
     * Android asks for memory back: a Labs engine (its own process, e.g. Big Boar's 5 GB) goes on critical pressure.
     * BOAR's own model and its prompt cache are never released here; a plain app switch (UI_HIDDEN, BACKGROUND)
     * releases nothing either: reloading a 35B takes a minute or more.
     */
    fun onTrimMemory(level: Int) {
        val critical = level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE
        if (critical && host.residentIsExternal()) lifeScope.launch { host.unloadIfIdle("memory pressure $level") }
    }

    val retriever: Retriever by lazy {
        Retriever(knowledgeBase, { packs }, { text -> runBlocking { embed(text) } }, { lexicon })
    }

    // --- The generation model ----------------------------------------------------------------------------------

    /** One generation model at a time, loaded with the user's Advanced tweaks. */
    val host = SessionHost()

    /**
     * BOAR's own engine (llama.cpp through JNI, in this process) as an engine provider like the Labs ones: every model
     * resolves to a provider ([providerFor]) and the host loads it the same way. Its own extras stay here: the load
     * guard around each load (a load that kills the app is detected on the next start) and the repacked → mapped
     * fallback after a stall (#73).
     */
    inner class BuiltInEngine : team.sopa.boar.plugins.EngineProvider {
        override val id = BUILT_IN_ENGINE
        override val engineLabel = "BOAR"
        override val engineEmoji = "🐗"
        override fun models() = ownCatalog.models.filter { it.kind == AssetKind.LLM }

        /** Models that stalled with repacked weights in this process: mapped from storage until the app restarts (#73). */
        val keepMapped: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())
        @Volatile var residentRepacked = false
            private set

        /** A stall while repacked: reload as the mapped file before the next generation. */
        fun needsReload(asset: CatalogAsset) = residentRepacked && asset.id in keepMapped && settings.engineTweaks.weightRepack != true

        override suspend fun load(asset: CatalogAsset, params: EngineParams): TextModel? {
            val tweaks = settings.engineTweaks
            val previous = (host.residentId() ?: settings.lastLoadedModelId)?.let(catalog::byId)?.let { LoadGuard.Meta(it.filename, it.id, it.label) }
            val meta = LoadGuard.Meta(asset.filename, asset.id, asset.label)
            val repack = tweaks.weightRepack ?: !(loadGuard.mappedNext(asset.id) || asset.id in keepMapped)
            loadGuard.begin(meta, previous, repack)
            val s = LlamaSession.load(fileOf(asset).path, params.copy(repack = repack))
            loadGuard.end(meta, s != null)
            if (s == null) return null
            residentRepacked = repack
            if (repack) s.onGenerated = { m ->
                if (LoadGuard.isDecodeStall(m.generatedTokens, m.decodeMs) && keepMapped.add(asset.id))
                    android.util.Log.w("BOAR", "${asset.id} stalled with repacked weights (${m.generatedTokens} tokens in ${m.decodeMs.toInt()} ms): mapped from the next answer")
            }
            return s
        }
    }

    val builtInEngine = BuiltInEngine()

    /**
     * The engine that runs [asset]: one Boar Labs routes it to (the user's engine choice, EnginePlugins.route), else its
     * own Labs engine (CatalogAsset.engine), else BOAR's. With Labs off nothing is routed: CatalogAsset.engine decides.
     */
    fun providerFor(asset: CatalogAsset): team.sopa.boar.plugins.EngineProvider? =
        team.sopa.boar.plugins.EnginePlugins.routed(asset)
            ?: asset.engine?.let { team.sopa.boar.plugins.EnginePlugins.forEngine(it) } ?: builtInEngine.takeIf { asset.engine == null }

    /** [asset] answers through BOAR's own engine (not a Labs engine, by its catalog entry or the user's choice). */
    fun runsOnBuiltIn(asset: CatalogAsset): Boolean = providerFor(asset) === builtInEngine

    /** The Labs engine that runs [asset] ("Big Boar"); null for BOAR's own. */
    fun engineLabelOf(asset: CatalogAsset): String? = providerFor(asset)?.takeIf { it !== builtInEngine }?.engineLabel

    inner class SessionHost : ModelHost {
        private val lock = Mutex()
        @Volatile private var session: TextModel? = null
        private var filename: String? = null
        private var residentId: String? = null

        override fun residentFilename(): String? = filename
        fun residentId(): String? = residentId
        override fun filenameOf(modelId: String): String? = catalog.byId(modelId)?.takeIf(::isInstalled)?.filename

        /** One path for every engine: the model's provider loads it; the host keeps one model resident at a time. */
        override suspend fun load(modelId: String): StepEngine? = lock.withLock {
            val asset = catalog.byId(modelId)?.takeIf(::isInstalled) ?: return null
            val provider = providerFor(asset) ?: return null
            val tweaks = settings.engineTweaks
            val reload = provider === builtInEngine && filename == asset.filename && builtInEngine.needsReload(asset)
            if (filename != asset.filename || session == null || reload) {
                if (session != null) { session?.close(); DebugLog.modelUnload(residentId) }
                session = null
                filename = null
                residentId = null
                val params = tweaks.resolve(topology, totalRamBytes).params
                val t0 = android.os.SystemClock.elapsedRealtime()
                val m = runCatching { provider.load(asset, params) }.onFailure { android.util.Log.w("BOAR", "${provider.id} load failed", it) }.getOrNull()
                DebugLog.modelLoad(asset.id, android.os.SystemClock.elapsedRealtime() - t0, m != null, params.threads, params.nCtx,
                    provider === builtInEngine && builtInEngine.residentRepacked)
                if (m == null) return null
                val engineOwn = m.onGenerated
                m.onGenerated = { g -> DebugLog.generation(asset.id, g, params.nCtx); engineOwn?.invoke(g) }
                session = m
                filename = asset.filename
                residentId = asset.id
                openedContext = params.nCtx
                settings.lastLoadedModelId = asset.id
                settings.stampModel(AppSettings.MODEL_LAST_USED, asset.id)
            }
            SessionEngine(session!!, tweaks.thinkingEnabled)
        }

        /** Frees the model unless it's answering (an answer in the background must finish); the next answer reloads it. */
        suspend fun unloadIfIdle(why: String) = lock.withLock {
            val s = session ?: return@withLock
            if (s.isGenerating) return@withLock
            val id = residentId
            s.close()
            session = null
            filename = null
            residentId = null
            DebugLog.event("model.release", "id" to id, "why" to why)
            if (id != null) DebugLog.modelUnload(id)
        }

        /** The resident model is a Labs engine's (its own process), for releasing it under memory pressure. */
        fun residentIsExternal(): Boolean = residentId?.let(catalog::byId)?.let { !runsOnBuiltIn(it) } == true

        /** The context window the resident model was opened with (sizes Auto answer length); null before a load. */
        @Volatile var openedContext: Int? = null
            private set

        /** The loaded model, for the default path (AnswerPipeline). */
        fun current(): team.sopa.boar.engine.TextModel? = session

        /** Frees the model; the next answer loads it again (with changed Advanced tweaks, or another model). */
        suspend fun unload() = lock.withLock {
            val id = residentId
            session?.close()
            session = null
            filename = null
            residentId = null
            if (id != null) DebugLog.modelUnload(id)
        }
    }

    // --- Free memory when I leave BOAR -------------------------------------------------------------------------

    /** A Labs engine's release after [BACKGROUND_RELEASE_MS] in the background, apart from BOAR's own free-memory timer. */
    private var labsRelease: kotlinx.coroutines.Job? = null
    @Volatile private var inForeground = true
    /** What uses the model besides a single generation: the chat's answer, its title or summary job, its preload. */
    private val busyChecks = java.util.concurrent.CopyOnWriteArrayList<() -> Boolean>()

    fun addBusyCheck(check: () -> Boolean) { busyChecks += check }
    fun removeBusyCheck(check: () -> Boolean) { busyChecks -= check }
    private fun modelBusy(): Boolean = busyChecks.any { runCatching { it() }.getOrDefault(false) }

    /**
     * BOAR left the screen: with Settings → Free memory when I leave BOAR on, the model is unloaded after the chosen
     * delay (Auto 5 min). An answer, title or summary still running is waited for; coming back first cancels it. The
     * chat stays as it is and the next question loads the model again.
     */
    @Synchronized fun onBackground() {
        inForeground = false
        backgroundRelease?.cancel()
        backgroundRelease = null
        labsRelease?.cancel()
        // A Labs engine (its own process) still idle after BACKGROUND_RELEASE_MS gives its memory back, setting or not.
        labsRelease = lifeScope.launch {
            kotlinx.coroutines.delay(BACKGROUND_RELEASE_MS)
            if (host.residentIsExternal()) host.unloadIfIdle("background ${BACKGROUND_RELEASE_MS / 60_000} min")
        }
        if (!settings.freeMemoryInBackground) return
        val wait = team.sopa.boar.engine.settings.FreeMemory.delayMs(settings.freeMemoryAfterMinutes)
        backgroundRelease = lifeScope.launch {
            kotlinx.coroutines.delay(wait)
            while (true) {
                val d = team.sopa.boar.engine.settings.FreeMemory.decide(settings.freeMemoryInBackground, inForeground, host.current() != null, modelBusy())
                when (d) {
                    team.sopa.boar.engine.settings.FreeMemory.Decision.SKIP -> return@launch
                    team.sopa.boar.engine.settings.FreeMemory.Decision.WAIT -> kotlinx.coroutines.delay(team.sopa.boar.engine.settings.FreeMemory.BUSY_POLL_MS)
                    team.sopa.boar.engine.settings.FreeMemory.Decision.UNLOAD -> {
                        host.unload()
                        android.util.Log.i("BOAR", "model unloaded after ${wait / 1000} s in the background")
                        return@launch
                    }
                }
            }
        }
    }

    @Synchronized fun onForeground() {
        inForeground = true
        backgroundRelease?.cancel()
        backgroundRelease = null
        labsRelease?.cancel()
        labsRelease = null
    }

    /** Loads the active model if it isn't resident; null when no model is installed or it fails to load. */
    suspend fun activeSession(): team.sopa.boar.engine.TextModel? {
        val m = answerModel() ?: return null
        host.load(m.id) ?: return null
        return host.current()
    }

    companion object {
        /**
         * The Custom answer style (a prompt the user writes) is hidden from Settings and the style sheet: it could be used to
         * make BOAR say anything and post it as BOAR's own words. The setting and prompt still work if already chosen.
         */
        const val SHOW_CUSTOM_STYLE = false
        const val DEFAULT_PERSONALITY_ID = "detailed"
        /** BOAR's own engine's provider id (models with no CatalogAsset.engine). */
        const val BUILT_IN_ENGINE = "llama"
        /** How long a Labs engine may sit idle with BOAR in the background before its memory goes back. */
        const val BACKGROUND_RELEASE_MS = 5 * 60_000L

        @Volatile private var instance: BoarServices? = null

        fun get(context: Context): BoarServices = instance ?: synchronized(this) {
            instance ?: run {
                // The CPU build is chosen once per process, from the user's tweak (Auto by default).
                val app = context.applicationContext
                BoarEngine.init(app.applicationInfo.nativeLibraryDir, AppSettings(File(app.filesDir, "settings.json")).engineTweaks.cpuBuild)
                BoarServices(app).also { instance = it; DebugLog.init(app, it.settings) }
            }
        }
    }
}
