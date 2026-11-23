package team.sopa.boar.eval

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import team.sopa.boar.engine.CpuTopology
import team.sopa.boar.engine.EngineParams
import team.sopa.boar.engine.EngineTweaks
import team.sopa.boar.engine.LlamaSession
import team.sopa.boar.engine.answer.AnswerEvent
import team.sopa.boar.engine.answer.AnswerPipeline
import team.sopa.boar.engine.answer.AnswerRequest
import team.sopa.boar.engine.answer.Personalities
import team.sopa.boar.engine.answer.PersonalFiles
import team.sopa.boar.engine.db.NativeSqlite
import team.sopa.boar.engine.eval.BenchModels
import team.sopa.boar.engine.eval.BenchPass
import team.sopa.boar.engine.eval.BenchPlan
import team.sopa.boar.engine.eval.BenchPresets
import team.sopa.boar.engine.eval.BenchTopic
import team.sopa.boar.engine.eval.DeviceEval
import team.sopa.boar.engine.eval.RunMeta
import team.sopa.boar.engine.eval.SavedRunStore
import team.sopa.boar.engine.eval.Scorecard
import team.sopa.boar.engine.eval.EvalConfig
import team.sopa.boar.engine.eval.EvalQuery
import team.sopa.boar.engine.eval.EvalRequest
import team.sopa.boar.engine.eval.EvalRequestException
import team.sopa.boar.engine.eval.EvalSets
import team.sopa.boar.engine.eval.ModelRef
import team.sopa.boar.engine.eval.Resolved
import team.sopa.boar.engine.rag.KnowledgeBase
import team.sopa.boar.engine.rag.OpenPack
import team.sopa.boar.engine.rag.Packs
import team.sopa.boar.engine.rag.Retriever
import team.sopa.boar.engine.rag.WikiPack
import team.sopa.boar.plugins.AnswerTools
import team.sopa.boar.plugins.ToolResult
import java.io.File

/**
 * The native app's side of scripts/eval-device.mjs, byte-compatible with the Expo app's (src/eval/deviceEvalRequest.ts):
 * takes files/eval/requests/pending.json, reports in <requestId>.status.json, writes eval/<runId>.jsonl and
 * .run.json. Runs the legacy pipeline (the fixed-model answer path); adaptive routing and the "answer" pipeline
 * are reported as not supported yet. Request polling is for debug builds only (the caller checks); [runConfigs]
 * also backs the Evaluation screen.
 */
class DeviceEvalRunner(
    private val context: Context,
    /** classifyTask and isRetrievalIrrelevant, as task type names. */
    private val classify: (String) -> String,
    private val retrievalIrrelevant: (String) -> Boolean,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {
    private val files = context.filesDir
    private val evalDir = File(files, "eval")
    private val requestsDir = File(evalDir, "requests")
    private val json = Json { prettyPrint = false }
    /** The app version on every row, so a saved or exported run says which build answered. */
    /** Tools and checks of the running request's labs; null: the app's (AnswerTools, AnswerChecks). */
    @Volatile private var runLabs: Pair<List<team.sopa.boar.plugins.AnswerTool>, List<team.sopa.boar.plugins.AnswerCheck>>? = null
    /** The ids of the labs a run switched on (rows say them: a question that needs one is skipped when it's off). */
    @Volatile private var runLabIds: List<String>? = null
    /** The request's labs ask for staged answers (Boar Staged Answers); null: the app's (AnswerModes). */
    @Volatile private var runStagedRequested: Boolean? = null
    @Volatile private var runStaged: Boolean? = null
    private val appVersion: String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" 

    private fun asset(name: String) = context.assets.open("boar-data/$name").bufferedReader().readText()

    /** Every run kept on the phone, listed on the Evaluations page (adam, Nov 14: "i run, closed, and now, cant review?"). */
    val store = SavedRunStore(evalDir)

    /**
     * Shared storage the adb runs copy to (Android/data/<pkg>/files/eval-out): the status after every row, not only at
     * the end (t9 0521 item 6: over adb a run showed no progress). Null: nothing copied.
     */
    @Volatile var mirrorDir: File? = null

    private fun mirror(f: File) {
        val out = mirrorDir ?: return
        runCatching { if (f.isFile) f.copyTo(File(out, f.relativeTo(evalDir).path), overwrite = true) }
    }

    private fun writeStatus(requestId: String, vararg fields: Pair<String, Any?>) {
        requestsDir.mkdirs()
        val all = listOf<Pair<String, Any?>>("requestId" to requestId) + fields.toList() + listOf("updatedAt" to System.currentTimeMillis())
        val o = json(*all.toTypedArray())
        val f = File(requestsDir, "$requestId.status.json")
        atomicWrite(f, o.toString())
        mirror(f)
    }

    /**
     * The labs that answer and check in the next run started from the Evaluations page (a topic's, like the plugins
     * topic's); null: the labs switched on in the app. Labs this build leaves out are skipped.
     */
    fun setLabs(ids: List<String>?) {
        if (ids != null) {
            team.sopa.boar.labs.LabPrefs.init(context)
            if (team.sopa.boar.labs.LabContext.app == null) team.sopa.boar.labs.LabContext.app = context.applicationContext
        }
        val labs = ids?.filter { it != "staged" }?.mapNotNull { team.sopa.boar.labs.LabRegistry.byId(it) }
        runLabs = labs?.let { l -> l.sortedBy { team.sopa.boar.engine.routing.LabTools.slotRank(it.id) }.mapNotNull { it.answerTool } to l.mapNotNull { it.answerCheck } }
        runLabIds = labs?.map { it.id }
        runStaged = ids?.contains("staged")
    }

    /** The topic files (assets/boar-data/topics), the newest version of each, by id. */
    fun topics(): Map<String, BenchTopic> {
        val names = context.assets.list("boar-data/topics").orEmpty().toList()
        return BenchTopic.newest(names).mapNotNull { n -> runCatching { BenchTopic.parse(asset("topics/$n")) }.getOrNull() }.associateBy { it.id }
    }

    /** Waits up to [waitMs] for a request and runs it. Returns true when one ran. */
    suspend fun pollOnce(waitMs: Long): Boolean = withContext(Dispatchers.IO) {
        val pending = File(requestsDir, "pending.json")
        val until = System.currentTimeMillis() + waitMs
        while (!pending.exists()) {
            if (System.currentTimeMillis() > until) return@withContext false
            delay(1000)
        }
        val text = pending.readText()
        pending.delete()
        val request = try {
            DeviceEval.parseRequest(text)
        } catch (e: EvalRequestException) {
            writeStatus(DeviceEval.requestIdOf(text), "state" to "failed", "error" to "invalid request: ${e.message}")
            return@withContext true
        }
        run(request)
        true
    }

    /**
     * Installed language models from the catalog, in catalog order: complete files only, the app's own rule
     * (StartLoad.complete, the size the catalog names). A partial download (Qwen3-4B, X6, Nov 10) was listed and failed
     * to load as "corrupted or incomplete".
     */
    fun installedModels(): List<Pair<ModelRef, File>> {
        val catalog = team.sopa.boar.engine.models.Catalog.parse(asset("catalog.json"))
        // Then the bench-only models (bench-models.json, t9 0443): read here and nowhere else, so no user list has them.
        return BenchModels.llms(catalog, benchOnlyModels()).mapNotNull { m ->
            val f = if (m.testOnly) benchFileOf(m) else File(files, m.filename)
            if (team.sopa.boar.engine.models.StartLoad.complete(f.isFile, f.length(), m.sizeBytes)) ModelRef(m.id, m.label) to f else null
        } + discoveredModels() + labEngineModels()
    }

    private fun benchOnlyModels() = runCatching { BenchModels.parse(asset(BenchModels.ASSET)) }
        .onFailure { log("[EVAL] ${BenchModels.ASSET}: $it") }.getOrDefault(emptyList())

    /**
     * A bench-only model's file, copied over USB: files/<filename> (run-as, a debuggable build), else the app's shared
     * folder Android/data/<pkg>/files/<filename>, which adb can write on a release build too.
     */
    private fun benchFileOf(m: team.sopa.boar.engine.models.CatalogAsset): File {
        val own = File(files, m.filename)
        if (own.isFile) return own
        return context.getExternalFilesDir(null)?.let { File(it, m.filename) } ?: own
    }

    /** Models the user added from Hugging Face (Discover GGUF models) and downloaded: evaluated like the catalog's. */
    private fun discoveredModels(): List<Pair<ModelRef, File>> = runCatching {
        val services = team.sopa.boar.app.BoarServices.get(context)
        services.catalog.models.filter { it.kind == team.sopa.boar.engine.models.AssetKind.LLM && it.id.startsWith("hf-") }.mapNotNull { a ->
            val f = services.fileOf(a)
            if (team.sopa.boar.engine.models.StartLoad.complete(f.isFile, f.length(), a.sizeBytes)) ModelRef(a.id, a.shortName) to f else null
        }
    }.getOrDefault(emptyList())

    /** Models a Labs engine brings (Big Boar's 35B), when BOAR has its own copy or one copied over USB (externalPath). */
    private fun labEngineModels(): List<Pair<ModelRef, File>> {
        // Started from BenchActivity, Boar Labs wasn't set up: without the app context Big Boar can't see its USB copy.
        if (team.sopa.boar.labs.LabContext.app == null) team.sopa.boar.labs.LabContext.app = context.applicationContext
        return team.sopa.boar.labs.LabRegistry.ALL.flatMap { it.engine?.models().orEmpty() }.mapNotNull { a ->
            val f = a.externalPath?.let(::File) ?: File(files, a.filename)
            if (team.sopa.boar.engine.models.StartLoad.complete(f.isFile, f.length(), a.sizeBytes)) ModelRef(a.id, a.label) to f else null
        }
    }

    fun evalSets(): EvalSets = EvalSets.parse(asset("eval-set.json"))

    private suspend fun run(request: EvalRequest) {
        // The language arm for this run only (t9 0420), back to what users get when it ends.
        team.sopa.boar.engine.answer.AnswerLanguage.arm = request.languageArm ?: 1
        try { runRequest(request) } finally { team.sopa.boar.engine.answer.AnswerLanguage.arm = 1 }
    }

    private suspend fun runRequest(request: EvalRequest) {
        val installed = installedModels()
        val installedIds = installed.map { it.first.id }
        if (request.pipeline == "answer") {
            writeStatus(request.requestId, "state" to "failed", "error" to "this native build runs the legacy pipeline only", "installedModels" to installedIds)
            return
        }
        val sets = evalSets()
        val resolved = DeviceEval.resolve(request, installed.map { it.first }, sets.evalSet, "Adaptive routing", sets.extraSets)
        if (resolved is Resolved.Err) {
            writeStatus(request.requestId, "state" to "failed", "error" to resolved.error, "installedModels" to installedIds)
            return
        }
        resolved as Resolved.Ok
        // A preset (adam, Nov 14): its level's models (or the request's), styles and passes, its topics' questions and labs.
        val topics = if (request.preset != null) topics() else emptyMap()
        val plan = request.preset?.let { preset ->
            val own = request.models?.takeIf { it.isNotEmpty() }?.let { resolved.configs.filterIsInstance<EvalConfig.Model>().map { c -> ModelRef(c.modelId, c.label) } }
            when (val p = BenchPresets.plan(preset, installed.map { it.first }, topics, sets, own, request.labs)) {
                is BenchPresets.Planned.Err -> { writeStatus(request.requestId, "state" to "failed", "error" to p.error, "installedModels" to installedIds); return }
                is BenchPresets.Planned.Ok -> p.plan
            }
        }
        val runConfigsList: List<EvalConfig> = plan?.models?.map { m ->
            resolved.configs.filterIsInstance<EvalConfig.Model>().firstOrNull { it.modelId == m.id } ?: EvalConfig.Model(m.id, m.label, request.engine ?: EvalConfig.BUILTIN)
        } ?: resolved.configs
        val runQueries = plan?.queries ?: resolved.queries
        val requestLabs = plan?.labs ?: request.labs
        // The request's labs: their tools and checks for this run only, whatever is switched on in the app.
        if (requestLabs != null) {
            team.sopa.boar.labs.LabPrefs.init(context)
            if (team.sopa.boar.labs.LabContext.app == null) team.sopa.boar.labs.LabContext.app = context.applicationContext
        }
        // "staged" in a request's labs still asks for Staged answers (core since Nov 12, no longer a lab).
        runStagedRequested = requestLabs?.contains("staged")
        val labs = requestLabs?.filter { it != "staged" }?.mapNotNull { id -> team.sopa.boar.labs.LabRegistry.byId(id) ?: run {
            // A topic's lab this build leaves out is skipped (its questions then fail their rule); a request's own fails the run.
            if (request.labs == null) { log("[EVAL] lab \"$id\" is not in this build: skipped"); return@mapNotNull null }
            writeStatus(request.requestId, "state" to "failed", "error" to "unknown lab \"$id\"", "installedModels" to installedIds); return
        } }
        runLabs = labs?.let { l -> l.sortedBy { team.sopa.boar.engine.routing.LabTools.slotRank(it.id) }.mapNotNull { it.answerTool } to l.mapNotNull { it.answerCheck } }
        runLabIds = labs?.map { it.id }
        runStaged = runStagedRequested
        // How search ranks: the request's "retrieval", else BALANCED (today's search). Per run, never kept on the runner.
        val retrievalMode = request.retrieval?.let { m -> team.sopa.boar.engine.rag.RetrievalMode.entries.first { it.name.lowercase() == m } }
            ?: team.sopa.boar.engine.rag.RetrievalMode.BALANCED
        // The request's answer style ("personal" for Personal Files), else the default; an unknown id fails the run.
        if (request.style != null && Personalities.parse(asset("personalities.json")).personalities.none { it.id == request.style }) {
            writeStatus(request.requestId, "state" to "failed", "error" to "unknown style \"${request.style}\"", "installedModels" to installedIds)
            return
        }
        val configs = runConfigsList.map { it.id }
        val total = plan?.answers ?: (runConfigsList.size * runQueries.size)
        writeStatus(request.requestId, "state" to "accepted", "configs" to configs, "total" to total, "completed" to 0, "installedModels" to installedIds,
            "preset" to plan?.preset?.name)
        log("[EVAL] device request ${request.requestId}: ${configs.joinToString(", ")} x ${runQueries.size} queries" + (plan?.let { " (preset ${it.preset.name})" } ?: ""))
        var completed = 0
        var runId: String? = null
        try {
            val info = RunInfo(source = "adb", requestId = request.requestId, plan = plan, topics = topics)
            val result = runConfigs(runConfigsList, runQueries, object : Listener {
                override fun onStart(id: String) { runId = id }
                override fun onProgress(p: Progress) = writeStatus(request.requestId, "state" to "running", "configs" to configs, "total" to total,
                    "completed" to completed, "current" to "${p.config.id} / ${p.query.id}", "installedModels" to installedIds, "runId" to runId)
                override fun onRow(row: JsonObject) {
                    completed++
                    writeStatus(request.requestId, "state" to "running", "configs" to configs, "total" to total, "completed" to completed,
                        "installedModels" to installedIds, "runId" to runId)
                }
            }, retrievalMode, request.style, info, searchOnly = request.searchOnly == true)
            writeStatus(request.requestId, "state" to "done", "configs" to configs, "total" to total, "completed" to result.rows.size, "runId" to result.runId,
                "resultPath" to "files/eval/${result.runId}.jsonl", "stopped" to result.stopped, "installedModels" to installedIds,
                "scorecardPath" to "files/eval/${result.runId}.scorecard.txt", "preset" to plan?.preset?.name)
        } catch (e: Throwable) {
            writeStatus(request.requestId, "state" to "failed", "configs" to configs, "total" to total, "completed" to completed, "error" to (e.message ?: e.toString()), "installedModels" to installedIds)
            throw e
        }
    }

    class Progress(val configIndex: Int, val configCount: Int, val queryIndex: Int, val queryCount: Int, val config: EvalConfig, val query: EvalQuery)
    class RunResult(val runId: String, val rows: List<JsonObject>, val file: File, val stopped: Boolean, val scorecard: Scorecard.Card? = null)
    interface Listener {
        /** The run's id, before its first answer. */
        fun onStart(runId: String) {}
        fun onProgress(p: Progress) {}
        fun onRow(row: JsonObject) {}
    }

    /**
     * Who started a run and, for a preset, its plan: kept in <runId>.meta.json for the saved list, and on every row
     * (level, topic) so the scorecard can tell topics apart.
     */
    class RunInfo(
        val source: String = "app",
        val requestId: String? = null,
        val plan: BenchPlan? = null,
        /** The topic files, for the scorecard; empty: read them here. */
        val topics: Map<String, BenchTopic> = emptyMap(),
    )

    @Volatile private var stopRequested = false
    @Volatile private var running: team.sopa.boar.engine.TextModel? = null

    /** The run going now, so the saved list doesn't call it interrupted. */
    @Volatile var activeRunId: String? = null
        private set

    /** Stops after the current answer, which is cut short and recorded as cancelled; the rows so far stay saved. */
    fun stop() {
        stopRequested = true
        running?.cancel()
    }

    /**
     * Answers every query with every configuration, one model loaded at a time, saving eval/<runId>.jsonl after each
     * row (evalHarness.runEvaluation). Used by device requests and the Evaluation screen. A preset ([RunInfo.plan]) runs
     * each of its passes (Succinct, Detailed, Deep Research) per model; otherwise one pass in [style].
     */
    suspend fun runConfigs(
        configs: List<EvalConfig>, queries: List<EvalQuery>, listener: Listener,
        retrievalMode: team.sopa.boar.engine.rag.RetrievalMode = team.sopa.boar.engine.rag.RetrievalMode.BALANCED,
        /** The answer style for every row; null is the default style, as before. Ignored by a preset. */
        style: String? = null,
        info: RunInfo = RunInfo(),
        /** Only each question's search, its sources with their scores; no answer (a device request's "searchOnly"). */
        searchOnly: Boolean = false,
    ): RunResult = withContext(Dispatchers.IO) {
        stopRequested = false
        val runId = DeviceEval.newRunId()
        activeRunId = runId
        evalDir.mkdirs()
        val rowsFile = File(evalDir, "$runId.jsonl")
        val rows = ArrayList<JsonObject>()
        val installed = installedModels()
        val personalities = Personalities.parse(asset("personalities.json"))
        // As the chat answers by default: the default style's prompt and reminder (the small-model variant below 2 GB),
        // at that style's Auto length (Detailed 1,024); or the run's style, at its own (Personal Files 768).
        val plan = info.plan
        val passes = plan?.passes ?: listOf(BenchPass(style ?: personalities.defaultId))
        val idsOf: (BenchPass) -> Set<String>? = { p -> plan?.passQueries?.get(p.key)?.toSet() }
        val perConfig = passes.sumOf { p -> idsOf(p)?.size ?: queries.size }
        val topology = CpuTopology.read()
        // The same Auto context as the chat (contextSizeForRam).
        val totalRam = runCatching {
            android.app.ActivityManager.MemoryInfo().also { context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) }.totalMem
        }.getOrDefault(0L)
        val retrieval = openRetrieval(topology)
        // The context every model is opened with here (Auto), which Personal Files' whole parts must fit.
        val contextSize = EngineTweaks().resolve(topology, totalRam).params.nCtx
        val topics = plan?.let { _ -> info.topics.ifEmpty { this@DeviceEvalRunner.topics() } }
        var meta = RunMeta(runId, System.currentTimeMillis(), appVersion, configs.mapNotNull { (it as? EvalConfig.Model)?.modelId ?: it.id },
            plan?.preset?.level?.id, plan?.preset?.topics.orEmpty(), plan?.preset?.topics.orEmpty().mapNotNull { t -> topics?.get(t)?.let { t to it.version } }.toMap(),
            passes.map { it.key }, info.source, info.requestId)
        fun saveMeta(m: RunMeta) { meta = m; store.writeMeta(m); mirror(store.metaFile(runId)) }
        saveMeta(meta)
        listener.onStart(runId)
        try {
            run@ for ((ci, config) in configs.withIndex()) {
                var session: team.sopa.boar.engine.TextModel? = null
                var loadMs = 0.0
                var residency = "cold"
                var qi = 0
                try {
                    for (pass in passes) for (q in queries) {
                        if (idsOf(pass)?.contains(q.id) == false) continue
                        if (stopRequested) break@run
                        val runStyle = pass.style
                        val personal = PersonalFiles.isOn(runStyle)
                        val maxTokens = team.sopa.boar.engine.settings.AppSettings.autoMaxTokens(runStyle)
                        val current = "${config.id} / ${pass.key} / ${q.id}"
                        listener.onProgress(Progress(ci, configs.size, qi++, perConfig, config, q))
                        atomicWrite(File(evalDir, "$runId.run.json"), json(
                            "runId" to runId, "configs" to configs.map { it.id }, "queries" to queries.map { it.id }, "current" to current,
                        ).toString())
                        val row = when (config) {
                            is EvalConfig.Adaptive -> failureRow(runId, config, q, runStyle, maxTokens, "adaptive routing is not in the native build yet")
                            is EvalConfig.Model -> {
                                if (session == null) {
                                    val file = installed.firstOrNull { it.first.id == config.modelId }?.second
                                    val t0 = System.nanoTime()
                                    session = file?.let { load(config, it, topology, totalRam) }
                                    loadMs = (System.nanoTime() - t0) / 1e6
                                } else {
                                    residency = "resident"
                                    loadMs = 0.0
                                }
                                val s = session
                                if (s == null) failureRow(runId, config, q, runStyle, maxTokens, "model failed to load")
                                else if (searchOnly) searchRow(retrieval, runId, config, q, runStyle, maxTokens, residency, loadMs, retrievalMode)
                                else {
                                    running = s
                                    // Said before the answer, so the bench screen shows what it's doing during a long one (adam, Nov 12).
                                    log("[EVAL] ${q.id}: answering on ${config.modelId} (${pass.key})…")
                                    val small = (installed.firstOrNull { it.first.id == config.modelId }?.second?.length() ?: Long.MAX_VALUE) < team.sopa.boar.engine.answer.Personalities.SMALL_MODEL_BYTES
                                    // The chat's short-answer rules too: small talk, health and seed-phrase questions answer in
                                    // Succinct, and small talk at Succinct's length (ShortAnswer, ChatViewModel).
                                    val style = if (team.sopa.boar.engine.routing.ShortAnswer.reason(runStyle, q.query, null) != null) "succinct" else runStyle
                                    val (systemPrompt, styleReminder) = personalities.promptFor(style, null, small)
                                    val limit = if (team.sopa.boar.engine.routing.ShortAnswer.isSmallTalk(q.query)) minOf(maxTokens, team.sopa.boar.engine.settings.AppSettings.AUTO_MAX_TOKENS) else maxTokens
                                    val r = if (pass.deep) deepRow(s, retrieval, runId, config, q, style, systemPrompt, limit, residency, loadMs, retrievalMode)
                                    else answerRow(s, retrieval, runId, config, q, style, systemPrompt, styleReminder, limit, residency, loadMs, retrievalMode,
                                        if (personal) contextSize else null)
                                    running = null
                                    if (stopRequested) JsonObject(r + ("outcome" to JsonPrimitive("cancelled"))) else r
                                }
                            }
                        }
                        // Every row says the pass's style, whatever the question answered in (personalityId); a preset's
                        // rows also say its level and the question's topic.
                        val extra = listOfNotNull(
                            "style" to JsonPrimitive(runStyle),
                            plan?.let { "level" to JsonPrimitive(it.preset.level.id) },
                            plan?.topicOf?.get(q.id)?.let { "topic" to JsonPrimitive(it) },
                            plan?.topicOf?.get(q.id)?.let { t -> topics?.get(t)?.let { "topicVersion" to JsonPrimitive(it.version) } },
                            runLabIds?.let { ids -> "labsOn" to kotlinx.serialization.json.JsonArray(ids.map { JsonPrimitive(it) }) },
                        )
                        val styled = JsonObject(row + extra)
                        rows += styled
                        atomicWrite(rowsFile, rows.joinToString("") { it.toString() + "\n" })
                        mirror(rowsFile)
                        saveMeta(meta.copy(rows = rows.size))
                        listener.onRow(styled)
                        log("[EVAL] ${q.id}: ${styled["outcome"]} ${styled["tokPerSec"] ?: ""}")
                    }
                } finally {
                    running = null
                    session?.close()
                }
            }
            File(evalDir, "$runId.run.json").delete()
            // The scorecard (t9 0521 item 4), against the last run of the same level and topics; kept as text for adb.
            val all = store.list(active = runId)
            val previous = store.previous(meta, all)?.let { store.rows(it.runId) }
            val card = runCatching { Scorecard.of(rows, topics ?: info.topics.ifEmpty { this@DeviceEvalRunner.topics() }, previous) }.getOrNull()
            card?.let { c ->
                val f = File(evalDir, "$runId.scorecard.txt")
                atomicWrite(f, Scorecard.text(c))
                mirror(f)
                Scorecard.text(c).lines().filter { it.isNotBlank() }.forEach { log("[EVAL] score $it") }
            }
            saveMeta(meta.copy(state = if (stopRequested) RunMeta.STATE_STOPPED else RunMeta.STATE_DONE, rows = rows.size, finishedAt = System.currentTimeMillis()))
            RunResult(runId, rows, rowsFile, stopRequested, card)
        } catch (e: Throwable) {
            runCatching { saveMeta(meta.copy(state = RunMeta.STATE_FAILED, rows = rows.size, finishedAt = System.currentTimeMillis(), error = e.message ?: e.toString())) }
            throw e
        } finally {
            activeRunId = null
            retrieval?.close()
        }
    }

    /**
     * A Deep Research row (the complete level's research topic; adam, Nov 14): DeepResearch as the chat runs it, the
     * write-up in the question's language. Same fields as [answerRow], path "deep"; ttftMs is the write-up's first word.
     */
    private suspend fun deepRow(
        s: team.sopa.boar.engine.TextModel, retrieval: Retrieval?, runId: String, config: EvalConfig.Model, q: EvalQuery, personalityId: String,
        systemPrompt: String, maxTokens: Int, residency: String, loadMs: Double, mode: team.sopa.boar.engine.rag.RetrievalMode,
    ): JsonObject {
        val params = mode.params()
        val tweaks = EngineTweaks()
        val peak = PeakRss().apply { start() }
        val start = System.nanoTime()
        var firstToken = 0L
        var tokens = 0
        var result: team.sopa.boar.engine.answer.ResearchResult? = null
        var error: String? = null
        try {
            result = team.sopa.boar.engine.answer.DeepResearch(team.sopa.boar.engine.answer.SessionEngine(s),
                { query, k -> retrieval?.retriever?.retrieve(query, k, params = params) ?: emptyList() }, tweaks::stageTimeoutMs, tweaks::writeUpTimeoutMs)
                .run(q.query, systemPrompt, null, maxTokens,
                    onToken = { tokens++; if (firstToken == 0L) firstToken = System.nanoTime() },
                    shouldStop = { stopRequested },
                    answerLanguage = team.sopa.boar.engine.answer.AnswerLanguage.line(team.sopa.boar.engine.answer.AnswerLanguage.of(q.query), withSources = true))
        } catch (e: Throwable) {
            error = e.message ?: e.toString()
        }
        val end = System.nanoTime()
        val peakRss = peak.stop()
        val r = result
        val answer = r?.answer.orEmpty()
        val outcome = if (error == null && answer.isNotBlank()) "success" else "failure"
        val ttftMs = if (firstToken > 0) (firstToken - start) / 1e6 else null
        val genLatency = if (firstToken > 0) (end - firstToken) / 1e6 else null
        val titles = r?.citations?.map { it.title } ?: emptyList()
        val taskType = classify(q.query)
        val m = r?.metrics
        return json(
            "modelId" to config.modelId, "taskType" to taskType, "adaptiveRoutingUsed" to false,
            "reasonCodes" to DeviceEval.fixedPlanReasonCodes(!retrievalIrrelevant(taskType), config.modelId),
            "retrievalUsed" to titles.isNotEmpty(), "modelSwitches" to 0, "crossMessageModelSwitch" to false,
            "modelResidency" to residency, "modelLoadMs" to loadMs, "ttftMs" to ttftMs, "generationLatencyMs" to genLatency,
            "totalLatencyMs" to (end - start) / 1e6, "tokensGenerated" to tokens, "tokPerSec" to DeviceEval.tokensPerSecond(tokens, genLatency),
            "peakRssBytes" to peakRss, "outcome" to outcome, "errorMessage" to (error ?: if (outcome == "failure") "empty answer" else null),
            "runId" to runId, "evalSetVersion" to q.setVersion,
            "promptTokens" to m?.promptTokensDecoded, "promptMs" to m?.prefillMs, "predictedTokens" to m?.generatedTokens, "predictedMs" to m?.decodeMs,
            "configId" to config.id, "configLabel" to config.label, "personalityId" to personalityId, "maxTokens" to maxTokens,
            "queryId" to q.id, "category" to q.category, "query" to q.query, "answer" to answer, "promptFormat" to if (s.hasChatTemplate) "chat-template" else "plain",
            "retrievedTitles" to titles, "expectedKbTitles" to q.expectedKbTitles, "expectedKbHit" to DeviceEval.expectedKbHit(q.expectedKbTitles, titles),
            "fileSources" to r?.citations.orEmpty().filter { it.collectionId != null }.map { it.title },
            "timedOut" to (r?.timedOut == true), "createdAt" to System.currentTimeMillis(), "engine" to "native", "appVersion" to appVersion,
            "inferenceEngine" to config.engine, "retrievalMode" to mode.name.lowercase(),
            "path" to BenchPass.PATH_DEEP, "subQuestions" to r?.subQuestions,
        )
    }

    /** The knowledge base, embedder and packs at the Expo app's paths; null when the knowledge base is missing. */
    private class Retrieval(val retriever: Retriever, val embedder: LlamaSession, val closeables: List<AutoCloseable>) : AutoCloseable {
        override fun close() { closeables.forEach { runCatching { it.close() } }; embedder.close() }
    }

    private suspend fun openRetrieval(topology: CpuTopology): Retrieval? {
        val dbFile = File(files, "SQLite/aoair_knowledge.db")
        val embFile = File(files, "models/embedding.gguf")
        if (!dbFile.isFile || !embFile.isFile) { log("[EVAL] no knowledge base or embedding model: answers without retrieval"); return null }
        val embedder = LlamaSession.load(embFile.absolutePath, EngineParams(nCtx = 512, threads = 2, embeddings = true)) ?: return null
        val kbDb = NativeSqlite.open(dbFile.absolutePath)
        val closeables = mutableListOf<AutoCloseable>(kbDb)
        val sha = Json.parseToJsonElement(asset("catalog.json")).jsonObject["models"]!!.jsonArray.map { it.jsonObject }
            .first { it["kind"]!!.jsonPrimitive.content == "embedding" && it["required"]?.jsonPrimitive?.content == "true" }["sha256"]!!.jsonPrimitive.content
        // Local pack files in name order (knowledgePacks lists catalog packs first; same files, by name).
        val packs = File(files, "corpus").listFiles { f -> f.name.endsWith(".sqlite") }.orEmpty().sortedBy { it.name }.mapNotNull { f ->
            runCatching {
                val db = NativeSqlite.open(f.absolutePath, readOnly = true)
                val meta = db.query("SELECT key, value FROM meta") { it.string(0)!! to (it.string(1) ?: "") }.toMap()
                val id = "local-${f.name.removeSuffix(".sqlite")}"
                when (Packs.packFormat(meta, sha)) {
                    1 -> OpenPack.Format1(id, db).also { closeables += db }
                    2 -> OpenPack.Format2(id, WikiPack.open(db, NativeSqlite.zstd)).also { closeables += db }
                    else -> { db.close(); null }
                }
            }.getOrNull()
        }
        // The Portuguese article names ship in the APK (assets/lexicon/pt-en.json), as the chat reads them.
        val lexicon: Map<String, String> = runCatching {
            context.assets.open("lexicon/pt-en.json").bufferedReader().use { r -> Json.parseToJsonElement(r.readText()).jsonObject.mapValues { it.value.jsonPrimitive.content } }
        }.getOrDefault(emptyMap())
        val retriever = Retriever(KnowledgeBase(kbDb), { packs }, { text -> runBlocking { embedder.embed(text) } }, { lexicon })
        log("[EVAL] retrieval: knowledge base + ${packs.size} packs, lexicon ${lexicon.size} names")
        return Retrieval(retriever, embedder, closeables)
    }

    /**
     * [config]'s model on the engine it asks for: BOAR's own with Auto settings (the default, as before), BOAR's own with
     * the mapped file instead of repacked weights, or Boar Labs' Big Boar (its own process) at the same settings.
     */
    private suspend fun load(config: EvalConfig.Model, file: File, topology: CpuTopology, totalRam: Long): team.sopa.boar.engine.TextModel? {
        val auto = EngineTweaks().resolve(topology, totalRam).params
        return when (config.engine) {
            EvalConfig.MAPPED -> LlamaSession.load(file.absolutePath, EngineTweaks(weightRepack = false).resolve(topology, totalRam).params)
            EvalConfig.BIG_BOAR -> {
                // Started from BenchActivity, Boar Labs wasn't set up: Big Boar needs the app context and the Labs prefs.
                team.sopa.boar.labs.LabPrefs.init(context)
                if (team.sopa.boar.labs.LabContext.app == null) team.sopa.boar.labs.LabContext.app = context.applicationContext
                val engine = team.sopa.boar.labs.LabRegistry.ALL.firstNotNullOfOrNull { lab -> lab.engine?.takeIf { it.id == BIG_BOAR_ENGINE } }
                // From the engine itself: the catalog lists a lab's models only once LabHooks set up the switched-on labs.
                val asset = engine?.models()?.firstOrNull { it.id == config.modelId }
                if (engine == null || asset == null) { log("[EVAL] Big Boar isn't in this build or ${config.modelId} isn't in the catalog"); null }
                else runCatching { engine.load(asset, auto) }.onFailure { log("[EVAL] Big Boar load failed: $it") }.getOrNull()
            }
            else -> LlamaSession.load(file.absolutePath, auto)
        }
    }

    private suspend fun answerRow(
        s: team.sopa.boar.engine.TextModel, retrieval: Retrieval?, runId: String, config: EvalConfig.Model, q: EvalQuery, personalityId: String,
        systemPrompt: String, styleReminder: String?, maxTokens: Int, residency: String, loadMs: Double,
        mode: team.sopa.boar.engine.rag.RetrievalMode,
        /** Set in the Personal Files style: the context its whole parts must fit; the chat's decisions follow (PersonalFiles). */
        filesContext: Int? = null,
    ): JsonObject {
        val personal = filesContext != null
        // The tool slot (Boar Labs), as in the chat: a tool's instant answer is the row, no generation; a context is read by the model.
        // In Personal Files only a safety card answers.
        val toolStart = System.nanoTime()
        val labs = runLabs
        val tool = (if (labs != null) team.sopa.boar.engine.routing.LabTools.first(labs.first) { it.run(q.query, emptyList()) } else AnswerTools.run(q.query, emptyList()))
            ?.takeIf { !personal || PersonalFiles.keepsTool(it.tool) }
        if (tool is ToolResult.Instant) return toolRow(runId, config, q, personalityId, maxTokens, residency, loadMs, tool, (System.nanoTime() - toolStart) / 1e6)
        val params = if (personal) PersonalFiles.retrieval(mode.params()) else mode.params()
        if (!personal && (runStaged ?: team.sopa.boar.plugins.AnswerModes.staged()))
            return stagedRow(s, { query, k -> retrieval?.retriever?.retrieve(query, k, params = params) ?: emptyList() }, runId, config, q, personalityId,
                AnswerTools.systemPrompt(systemPrompt, tool), styleReminder, maxTokens, residency, loadMs, mode, tool)
        val pipeline = AnswerPipeline(s, { query, k -> retrieval?.retriever?.retrieve(query, k, params = params) ?: emptyList() }, classify, retrievalIrrelevant)
        val peak = PeakRss().apply { start() }
        val start = System.nanoTime()
        var genStart = 0L
        var firstToken = 0L
        var tokens = 0
        var done: AnswerEvent.Done? = null
        var error: String? = null
        try {
            val base = AnswerRequest(q.query, AnswerTools.systemPrompt(systemPrompt, tool), styleReminder, maxTokens)
            // The files' list as the chat sends it (OwnFiles.note), so a run answers "which files do I have?" as the chat does.
            val filesNote = if (filesContext != null) retrieval?.retriever?.ownFiles()?.let(team.sopa.boar.engine.rag.OwnFiles::note) else null
            pipeline.answer(if (filesContext != null) PersonalFiles.request(base, filesContext, filesNote = filesNote) else base).collect { e ->
                when (e) {
                    is AnswerEvent.Stage -> if (e.stage == team.sopa.boar.engine.answer.AnswerStage.THINKING) genStart = System.nanoTime()
                    is AnswerEvent.Piece -> { tokens++; if (firstToken == 0L) firstToken = System.nanoTime() }
                    is AnswerEvent.Done -> done = e
                    else -> {}
                }
            }
        } catch (e: Throwable) {
            error = e.message ?: e.toString()
        }
        val end = System.nanoTime()
        val peakRss = peak.stop()
        val d = done
        // The check slot, as in the chat: a finished answer may be repaired (Boar Guard). Not in Personal Files.
        val repair = d?.text?.takeIf { error == null && it.isNotBlank() && !personal }?.let { text ->
            if (labs != null) team.sopa.boar.engine.routing.LabTools.first(labs.second) { it.check(q.query, text) } else team.sopa.boar.plugins.AnswerChecks.run(q.query, text)
        }
        val answer = (repair?.text ?: d?.text ?: "").let { if (personal) PersonalFiles.dropHedge(it) else it }
        val ttftMs = if (genStart > 0) ((if (firstToken > 0) firstToken else end) - genStart) / 1e6 else null
        val genLatency = if (genStart > 0 && ttftMs != null) (end - genStart) / 1e6 - ttftMs else null
        val outcome = if (error != null) "failure" else if (answer.isNotBlank()) "success" else "failure"
        val m = d?.metrics
        val titles = d?.sources?.map { it.title } ?: emptyList()
        val taskType = d?.taskType ?: classify(q.query)
        return json(
            "modelId" to config.modelId, "taskType" to taskType, "adaptiveRoutingUsed" to false,
            "reasonCodes" to DeviceEval.fixedPlanReasonCodes(!retrievalIrrelevant(taskType), config.modelId),
            "retrievalUsed" to (titles.isNotEmpty()), "modelSwitches" to 0, "crossMessageModelSwitch" to false,
            "modelResidency" to residency, "modelLoadMs" to loadMs, "ttftMs" to ttftMs, "generationLatencyMs" to genLatency,
            "totalLatencyMs" to (end - start) / 1e6, "tokensGenerated" to tokens, "tokPerSec" to DeviceEval.tokensPerSecond(tokens, genLatency),
            "peakRssBytes" to peakRss, "outcome" to outcome, "errorMessage" to (error ?: if (outcome == "failure") "empty answer" else null),
            "runId" to runId, "evalSetVersion" to q.setVersion, "retrievalMs" to d?.retrievalMs,
            "contextTokensBefore" to d?.tokensBefore, "contextTokensAfter" to d?.tokensAfter,
            "promptTokens" to m?.promptTokensDecoded, "promptMs" to m?.prefillMs, "cachedTokens" to m?.let { it.promptTokens - it.promptTokensDecoded },
            "predictedTokens" to m?.generatedTokens, "predictedMs" to m?.decodeMs,
            "configId" to config.id, "configLabel" to config.label, "personalityId" to personalityId, "maxTokens" to maxTokens,
            "queryId" to q.id, "category" to q.category, "query" to q.query, "answer" to answer, "promptFormat" to d?.promptFormat,
            "retrievedTitles" to titles, "expectedKbTitles" to q.expectedKbTitles, "expectedKbHit" to DeviceEval.expectedKbHit(q.expectedKbTitles, titles),
            "fileSources" to d?.sources.orEmpty().filter { it.collectionId != null }.map { it.title },
            "timedOut" to false, "createdAt" to System.currentTimeMillis(), "engine" to "native", "appVersion" to appVersion,
            "tool" to listOfNotNull(tool?.tool, repair?.tool).joinToString("+").ifEmpty { null },
            // Which engine wrote it (builtin, mapped or bigboar); Big Boar runs in its own process, so peakRssBytes is the app's only.
            "inferenceEngine" to config.engine,
            // How search ranked the passages (balanced, keyword or meaning), so runs in different modes can be compared.
            "retrievalMode" to mode.name.lowercase(),
            // Personal Files: the user's files held nothing for this question (the fixed line, no model).
            "notFound" to d?.notFound,
        )
    }

    /** A row a tool answered: its text, reason code answer:<tool> (as answer.ts's receipts), nothing generated or retrieved. */
    private fun toolRow(
        runId: String, config: EvalConfig.Model, q: EvalQuery, personalityId: String, maxTokens: Int, residency: String, loadMs: Double, tool: ToolResult.Instant, ms: Double,
    ) = json(
        "modelId" to config.modelId, "taskType" to classify(q.query), "adaptiveRoutingUsed" to false, "reasonCodes" to listOf("answer:${tool.tool}"),
        "retrievalUsed" to false, "modelSwitches" to 0, "crossMessageModelSwitch" to false, "modelResidency" to residency, "modelLoadMs" to loadMs,
        "ttftMs" to ms, "totalLatencyMs" to ms, "tokensGenerated" to 0, "outcome" to "success", "runId" to runId, "evalSetVersion" to q.setVersion,
        "configId" to config.id, "configLabel" to config.label, "personalityId" to personalityId, "maxTokens" to maxTokens,
        "queryId" to q.id, "category" to q.category, "query" to q.query, "answer" to tool.text, "retrievedTitles" to emptyList<String>(),
        "expectedKbTitles" to q.expectedKbTitles, "expectedKbHit" to DeviceEval.expectedKbHit(q.expectedKbTitles, emptyList()),
        "timedOut" to false, "createdAt" to System.currentTimeMillis(), "engine" to "native", "appVersion" to appVersion, "tool" to tool.tool,
        "inferenceEngine" to config.engine,
    )

    /**
     * A search-only row (t9 0321): the sources the answer would get, in order, each with its similarity, fused score,
     * match type and kind ("pack", "file" for the user's own files, "kb" for the built-in corpus). No answer is written.
     */
    private fun searchRow(
        retrieval: Retrieval?, runId: String, config: EvalConfig.Model, q: EvalQuery, personalityId: String, maxTokens: Int,
        residency: String, loadMs: Double, mode: team.sopa.boar.engine.rag.RetrievalMode,
    ): JsonObject {
        val start = System.nanoTime()
        var error: String? = null
        val found = try {
            retrieval?.retriever?.retrieve(q.query, team.sopa.boar.engine.rag.RagPure.ANSWER_CONTEXT_CHUNKS, params = mode.params()) ?: run { error = "no retrieval"; emptyList() }
        } catch (e: Throwable) { error = e.message ?: e.toString(); emptyList() }
        val ms = (System.nanoTime() - start) / 1e6
        val sources = JsonArray(found.map { c ->
            JsonObject(mapOf(
                "title" to JsonPrimitive(c.title), "similarity" to (c.similarity?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull), "score" to JsonPrimitive(c.score),
                "match" to JsonPrimitive(c.matchType.name.lowercase()),
                "kind" to JsonPrimitive(if (c.docId.startsWith("pack:")) "pack" else if (c.collectionId != null) "file" else "kb"),
            ))
        })
        return json(
            "modelId" to config.modelId, "taskType" to classify(q.query), "adaptiveRoutingUsed" to false, "retrievalUsed" to found.isNotEmpty(),
            "modelResidency" to residency, "modelLoadMs" to loadMs, "totalLatencyMs" to ms, "retrievalMs" to ms, "tokensGenerated" to 0,
            "outcome" to if (error == null) "success" else "failure", "errorMessage" to error, "runId" to runId, "evalSetVersion" to q.setVersion,
            "configId" to config.id, "configLabel" to config.label, "personalityId" to personalityId, "maxTokens" to maxTokens,
            "queryId" to q.id, "category" to q.category, "query" to q.query, "answer" to "", "retrievedTitles" to found.map { it.title },
            "retrievedSources" to sources, "expectedKbTitles" to q.expectedKbTitles, "expectedKbHit" to DeviceEval.expectedKbHit(q.expectedKbTitles, found.map { it.title }),
            "timedOut" to false, "createdAt" to System.currentTimeMillis(), "engine" to "native", "appVersion" to appVersion,
            "inferenceEngine" to config.engine, "retrievalMode" to mode.name.lowercase(), "path" to "search-only",
        )
    }

    private fun failureRow(runId: String, config: EvalConfig, q: EvalQuery, personalityId: String, maxTokens: Int, error: String) = json(
        "modelId" to (config as? EvalConfig.Model)?.modelId, "taskType" to classify(q.query), "adaptiveRoutingUsed" to (config is EvalConfig.Adaptive),
        "outcome" to "failure", "errorMessage" to error, "tokensGenerated" to 0, "runId" to runId, "evalSetVersion" to q.setVersion,
        "configId" to config.id, "configLabel" to config.label, "personalityId" to personalityId, "maxTokens" to maxTokens,
        "queryId" to q.id, "category" to q.category, "query" to q.query, "answer" to "", "retrievedTitles" to emptyList<String>(),
        "expectedKbTitles" to q.expectedKbTitles, "expectedKbHit" to DeviceEval.expectedKbHit(q.expectedKbTitles, emptyList()),
        "timedOut" to false, "createdAt" to System.currentTimeMillis(), "engine" to "native", "appVersion" to appVersion,
    )

    /**
     * A Boar Staged Answers row: the same fields as [answerRow], plus when each step showed up (ms from the start, the
     * model already loaded: stagedSourcesMs, stagedIntroFirstMs, stagedIntroDoneMs, stagedResearchMs, stagedDetailsFirstMs),
     * the passages the research step added, and the numbers no source has. ttftMs is the intro's first word.
     */
    private suspend fun stagedRow(
        s: team.sopa.boar.engine.TextModel, retrieve: (String, Int) -> List<team.sopa.boar.engine.rag.Chunk>, runId: String, config: EvalConfig.Model, q: EvalQuery,
        personalityId: String, systemPrompt: String, styleReminder: String?, maxTokens: Int, residency: String, loadMs: Double,
        mode: team.sopa.boar.engine.rag.RetrievalMode, tool: ToolResult?,
    ): JsonObject {
        val introReminder = Personalities.parse(asset("personalities.json")).promptFor("succinct", null).second
        val peak = PeakRss().apply { start() }
        val start = System.nanoTime()
        var tokens = 0
        var done: team.sopa.boar.engine.answer.StagedEvent.Done? = null
        var error: String? = null
        try {
            team.sopa.boar.engine.answer.StagedAnswer(retrieve, classify, retrievalIrrelevant)
                .answer(team.sopa.boar.engine.answer.StagedRequest(q.query, systemPrompt, introReminder, styleReminder.takeIf { personalityId != "succinct" }, maxTokens),
                    load = { s }, shouldStop = { stopRequested })
                .collect { e ->
                    when (e) {
                        is team.sopa.boar.engine.answer.StagedEvent.Piece -> tokens++
                        is team.sopa.boar.engine.answer.StagedEvent.Done -> done = e
                        else -> {}
                    }
                }
        } catch (e: Throwable) {
            error = e.message ?: e.toString()
        }
        val end = System.nanoTime()
        val peakRss = peak.stop()
        val d = done
        val answer = d?.text.orEmpty()
        val t = d?.times
        val outcome = if (error == null && answer.isNotBlank()) "success" else "failure"
        val genLatency = t?.let { tt -> tt.introFirstMs?.let { (end - start) / 1e6 - it } }
        val titles = d?.sources?.map { it.title } ?: emptyList()
        val taskType = classify(q.query)
        val m = d?.metrics
        return json(
            "modelId" to config.modelId, "taskType" to taskType, "adaptiveRoutingUsed" to false,
            "reasonCodes" to DeviceEval.fixedPlanReasonCodes(!retrievalIrrelevant(taskType), config.modelId),
            "retrievalUsed" to titles.isNotEmpty(), "modelSwitches" to 0, "crossMessageModelSwitch" to false,
            "modelResidency" to residency, "modelLoadMs" to loadMs, "ttftMs" to t?.introFirstMs, "generationLatencyMs" to genLatency,
            "totalLatencyMs" to (end - start) / 1e6, "tokensGenerated" to tokens, "tokPerSec" to DeviceEval.tokensPerSecond(tokens, genLatency),
            "peakRssBytes" to peakRss, "outcome" to outcome, "errorMessage" to (error ?: if (outcome == "failure") "empty answer" else null),
            "runId" to runId, "evalSetVersion" to q.setVersion, "retrievalMs" to d?.retrievalMs,
            "promptTokens" to m?.promptTokensDecoded, "promptMs" to m?.prefillMs, "cachedTokens" to m?.let { it.promptTokens - it.promptTokensDecoded },
            "predictedTokens" to m?.generatedTokens, "predictedMs" to m?.decodeMs,
            "configId" to config.id, "configLabel" to config.label, "personalityId" to personalityId, "maxTokens" to maxTokens,
            "queryId" to q.id, "category" to q.category, "query" to q.query, "answer" to answer, "promptFormat" to if (s.hasChatTemplate) "chat-template" else "plain",
            "retrievedTitles" to titles, "expectedKbTitles" to q.expectedKbTitles, "expectedKbHit" to DeviceEval.expectedKbHit(q.expectedKbTitles, titles),
            "timedOut" to false, "createdAt" to System.currentTimeMillis(), "engine" to "native", "appVersion" to appVersion,
            "tool" to tool?.tool, "inferenceEngine" to config.engine, "retrievalMode" to mode.name.lowercase(),
            "path" to "staged", "stagedSourcesMs" to t?.sourcesMs, "stagedIntroFirstMs" to t?.introFirstMs, "stagedIntroDoneMs" to t?.introDoneMs,
            "stagedResearchMs" to t?.researchMs, "stagedDetailsFirstMs" to t?.detailsFirstMs,
            "stagedKeyFacts" to d?.keyFacts?.size, "stagedSources" to d?.sources?.size, "stagedIntro" to d?.intro,
            "unsupportedNumbers" to d?.unsupported,
            // The user's own files among the sources (a custom collection), for the scorecard's noFileSources rule.
            "fileSources" to d?.sources.orEmpty().filter { it.collectionId != null }.map { it.title },
        )
    }

    companion object {
        const val TAG = "BOAR_EVAL"
        /** Big Boar's engine id (BmoeLab). */
        private const val BIG_BOAR_ENGINE = "bmoe"

        /** evalRowsToCsv (EvalExport in the engine, so a saved run's export is tested). */
        fun toCsv(rows: List<JsonObject>): String = team.sopa.boar.engine.eval.EvalExport.csv(rows)
    }
}

/** Peak resident memory while a question runs, sampled from /proc (VmRSS) every 250 ms. */
private class PeakRss {
    @Volatile private var peak = 0L
    @Volatile private var running = false
    private fun rss(): Long = runCatching {
        File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }.split(Regex("\\s+"))[1].toLong() * 1024
    }.getOrDefault(0L)
    fun start() {
        running = true
        peak = rss()
        Thread { while (running) { peak = maxOf(peak, rss()); Thread.sleep(250) } }.apply { isDaemon = true }.start()
    }
    fun stop(): Long { running = false; return maxOf(peak, rss()) }
}

private fun atomicWrite(f: File, text: String) {
    val tmp = File(f.parentFile, f.name + ".tmp")
    tmp.writeText(text)
    tmp.renameTo(f)
}

/** A JSON object from pairs; null values are left out, as JSON.stringify drops undefined. */
internal fun json(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.mapNotNull { (k, v) -> v?.let { k to toJsonElement(it) } }.toMap())

private fun toJsonElement(v: Any): kotlinx.serialization.json.JsonElement = when (v) {
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Int -> JsonPrimitive(v)
    is Long -> JsonPrimitive(v)
    is Double -> JsonPrimitive(v)
    is List<*> -> JsonArray(v.filterNotNull().map(::toJsonElement))
    else -> JsonPrimitive(v.toString())
}
