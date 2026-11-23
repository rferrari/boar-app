package team.sopa.boar.eval

import team.sopa.boar.app.safeScope

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.eval.BenchLevel
import team.sopa.boar.engine.eval.BenchPlan
import team.sopa.boar.engine.eval.BenchPreset
import team.sopa.boar.engine.eval.BenchPresets
import team.sopa.boar.engine.eval.BenchTopic
import team.sopa.boar.engine.eval.EvalConfig
import team.sopa.boar.engine.eval.EvalExport
import team.sopa.boar.engine.eval.EvalQuery
import team.sopa.boar.engine.eval.EvalSets
import team.sopa.boar.engine.eval.ModelRef
import team.sopa.boar.engine.eval.RunMeta
import team.sopa.boar.engine.eval.Scorecard
import team.sopa.boar.engine.routing.Classify
import team.sopa.boar.engine.routing.TaskType
import java.io.File

data class EvaluationState(
    val configs: List<EvalConfig.Model>? = null,
    val selected: Set<String> = emptySet(),
    val queries: List<EvalQuery> = emptyList(),
    val setVersion: String = "",
    val running: Boolean = false,
    val stopping: Boolean = false,
    val progress: DeviceEvalRunner.Progress? = null,
    val rows: List<JsonObject> = emptyList(),
    val runId: String? = null,
    val savedPath: String? = null,
    val stopped: Boolean = false,
    val error: String? = null,
    /** The share preview is open (what will be sent is listed before anything leaves the phone). */
    val sharePreview: team.sopa.boar.engine.eval.ShareResults.Device? = null,
    val shareLog: List<team.sopa.boar.share.ResultsSharing.StepInfo> = emptyList(),
    val shareSending: Boolean = false,
    val shareOutcome: team.sopa.boar.engine.eval.ShareResults.Outcome? = null,
    /** The run already shared (its button shows "Shared"). */
    val sharedRunId: String? = null,
    /** The preset's level; null: by hand (the ticked models, the default style), as before. */
    val level: BenchLevel? = null,
    /** The preset's topic ("core": the fixed 17). */
    val topic: String = BenchPresets.CORE,
    val topics: List<BenchTopic> = emptyList(),
    /** What the chosen preset runs, and how long it should take. */
    val plan: BenchPlan? = null,
    val estimate: BenchPresets.Estimate? = null,
    /** The answers expected in the run going now (or the one just made). */
    val total: Int = 0,
    val scorecard: Scorecard.Card? = null,
    /** Every run kept on the phone, newest first. */
    val saved: List<RunMeta> = emptyList(),
    /** The saved run shown, when one was opened from the list. */
    val opened: RunMeta? = null,
)

/**
 * The Evaluation screen (EvaluationScreen.tsx): presets by level and topic or the fixed set against the chosen models,
 * saved, listed and exportable. adam, Nov 14: "i run, closed, and now, cant review? ... i could load it from
 * evaluations (for builders) page": every run is listed from files/eval, opened with its rows and scorecard, deleted.
 */
class EvaluationViewModel(app: Application) : AndroidViewModel(app) {
    private val services = BoarServices.get(app)
    private val runner = DeviceEvalRunner(app, { q -> Classify.classifyTask(q).value }, { t -> Classify.isRetrievalIrrelevant(TaskType.of(t)) })
    private val _state = MutableStateFlow(EvaluationState())
    val state: StateFlow<EvaluationState> = _state
    private var sets: EvalSets? = null
    private var installed: List<ModelRef> = emptyList()
    private var topicsById: Map<String, BenchTopic> = emptyMap()

    init {
        safeScope.launch(Dispatchers.IO) {
            installed = runner.installedModels().map { it.first }
            val configs = installed.map { EvalConfig.Model(it.id, it.label) }
            val s = runner.evalSets().also { sets = it }
            topicsById = runCatching { runner.topics() }.getOrDefault(emptyMap())
            val ordered = topicsById.values.sortedWith(compareBy({ if (it.id == BenchPresets.CORE) 0 else 1 }, { TOPIC_ORDER.indexOf(it.id).let { i -> if (i < 0) 99 else i } }))
            _state.update { it.copy(configs = configs, selected = configs.map { c -> c.id }.toSet(), queries = s.evalSet,
                setVersion = s.evalSet.firstOrNull()?.setVersion ?: "", topics = ordered) }
            refreshSaved()
        }
    }

    fun toggle(id: String) = _state.update { s ->
        if (s.running) s else s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id)
    }

    fun setLevel(level: BenchLevel?) { if (!_state.value.running) { _state.update { it.copy(level = level) }; replan() } }
    fun setTopic(topic: String) { if (!_state.value.running) { _state.update { it.copy(topic = topic) }; replan() } }

    /** The chosen preset's plan and expected time: the last run of the same level on this phone, else the F3's medians. */
    private fun replan() {
        safeScope.launch(Dispatchers.IO) {
            val s = _state.value
            val level = s.level
            val sets = sets ?: return@launch
            if (level == null) {
                val q = topicsById[s.topic]?.let { t -> runCatching { t.queries(sets) }.getOrNull() } ?: sets.evalSet
                _state.update { it.copy(plan = null, estimate = null, queries = q) }
                return@launch
            }
            val planned = BenchPresets.plan(BenchPreset(level, listOf(s.topic)), installed, topicsById, sets)
            val plan = (planned as? BenchPresets.Planned.Ok)?.plan
            val last = runner.store.lastOfLevel(level.id)?.let { runner.store.rows(it.runId) }
            _state.update { it.copy(plan = plan, estimate = plan?.let { p -> BenchPresets.estimate(p, last) }, queries = plan?.queries ?: it.queries,
                error = (planned as? BenchPresets.Planned.Err)?.error) }
        }
    }

    fun refreshSaved() {
        safeScope.launch(Dispatchers.IO) {
            val list = runCatching { runner.store.list(active = runner.activeRunId) }.getOrDefault(emptyList())
            _state.update { it.copy(saved = list) }
        }
    }

    fun run() {
        val s = _state.value
        val plan = if (s.level != null) s.plan else null
        if (s.level != null && plan == null) return
        val chosen: List<EvalConfig> = plan?.models?.map { EvalConfig.Model(it.id, it.label) } ?: s.configs.orEmpty().filter { it.id in s.selected }
        if (s.running || chosen.isEmpty()) return
        val total = plan?.answers ?: (s.queries.size * chosen.size)
        _state.update { it.copy(running = true, stopping = false, rows = emptyList(), runId = null, savedPath = null, stopped = false, error = null,
            opened = null, scorecard = null, total = total) }
        safeScope.launch(Dispatchers.IO) {
            try {
                // One model in memory at a time: the chat's is reloaded on its next message.
                // The chat's answer and its queue stop first (validator Q-5).
                // Still answering after the wait: the run doesn't start, and says why (validator).
                check(services.stopAnswersAndWait()) { "an answer is still being written" }
                services.host.unload()
                // A topic's labs answer for this run (the plugins topic); by hand, the app's.
                runner.setLabs(plan?.labs)
                val result = runner.runConfigs(chosen, plan?.queries ?: s.queries, object : DeviceEvalRunner.Listener {
                    override fun onStart(runId: String) { refreshSaved() }
                    override fun onProgress(p: DeviceEvalRunner.Progress) = _state.update { it.copy(progress = p) }
                    override fun onRow(row: JsonObject) = _state.update { it.copy(rows = it.rows + row) }
                }, info = DeviceEvalRunner.RunInfo(plan = plan, topics = topicsById))
                val card = result.scorecard ?: Scorecard.of(result.rows, topicsById)
                _state.update { it.copy(runId = result.runId, savedPath = result.file.path, stopped = result.stopped, scorecard = card) }
                if (!result.stopped) services.vault.record { f -> f.copy(evalRuns = f.evalRuns + 1) }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                runner.setLabs(null)
            }
            _state.update { it.copy(running = false, stopping = false, progress = null) }
            refreshSaved()
            replan()
        }
    }

    /** Shows a saved run as right after it ran: its rows and its scorecard against the run before it. */
    fun open(runId: String) {
        if (_state.value.running) return
        safeScope.launch(Dispatchers.IO) {
            val all = runner.store.list(active = runner.activeRunId)
            val meta = all.firstOrNull { it.runId == runId } ?: return@launch
            val rows = runner.store.rows(runId)
            val previous = runner.store.previous(meta, all)?.let { runner.store.rows(it.runId) }
            val card = Scorecard.of(rows, topicsById, previous)
            _state.update { it.copy(opened = meta, rows = rows, runId = runId, savedPath = runner.store.rowsFile(runId).path, stopped = meta.state == RunMeta.STATE_STOPPED,
                scorecard = card, total = rows.size, error = null, saved = all) }
        }
    }

    fun closeOpened() = _state.update { it.copy(opened = null, rows = emptyList(), runId = null, savedPath = null, scorecard = null, stopped = false) }

    fun delete(runId: String) {
        if (runner.activeRunId == runId) return
        safeScope.launch(Dispatchers.IO) {
            runner.store.delete(runId)
            if (_state.value.runId == runId) closeOpened()
            refreshSaved()
        }
    }

    val sharing = team.sopa.boar.share.ResultsSharing(app, services.settings)

    /** Opens the preview: nothing is sent until the user presses Share there. */
    fun previewShare() {
        if (_state.value.rows.isEmpty() || _state.value.running) return
        safeScope.launch(Dispatchers.IO) { val d = sharing.device(); _state.update { it.copy(sharePreview = d, shareLog = emptyList(), shareOutcome = null) } }
    }

    fun closeShare() = _state.update { it.copy(sharePreview = null, shareLog = emptyList(), shareOutcome = null) }

    fun confirmShare() {
        val s = _state.value
        if (s.shareSending || s.rows.isEmpty()) return
        _state.update { it.copy(shareSending = true, shareLog = emptyList(), shareOutcome = null) }
        safeScope.launch {
            val started = System.currentTimeMillis()
            val outcome = sharing.share(s.rows) { info ->
                _state.update { it.copy(shareLog = it.shareLog + info) }
                Thread.sleep(STEP_MIN_MS) // each step stays readable on a fast network (STEP_MIN_MS in the TS)
            }
            kotlinx.coroutines.delay((SHARE_MIN_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            _state.update { it.copy(shareSending = false, shareOutcome = outcome, sharedRunId = if (outcome.result.succeeded) s.runId else it.sharedRunId) }
            if (outcome.result.succeeded) services.vault.record { f -> f.copy(evalShares = f.evalShares + 1) }
        }
    }

    private companion object {
        const val STEP_MIN_MS = 350L
        const val SHARE_MIN_MS = 1500L
        /** The topics in t9's order (0521 item 3), after the fixed 17. */
        val TOPIC_ORDER = listOf("plugins", "problems", "howto", "research", "quick", "cards", "portuguese", "vitalik")
    }

    fun stop() {
        _state.update { it.copy(stopping = true) }
        runner.stop()
    }

    /** Hands the results to the share sheet as JSONL or CSV (exportEvalResults): a run just made or one opened. */
    suspend fun exportIntent(format: String): Intent? = withContext(Dispatchers.IO) {
        val s = _state.value
        val runId = s.runId ?: return@withContext null
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "$runId.$format")
        file.writeText(if (format == "jsonl") EvalExport.jsonl(s.rows) else EvalExport.csv(s.rows))
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = if (format == "jsonl") "application/x-ndjson" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Export evaluation results")
    }

    override fun onCleared() {
        runner.stop()
    }
}
