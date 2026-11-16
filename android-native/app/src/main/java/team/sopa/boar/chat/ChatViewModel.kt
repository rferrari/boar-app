package team.sopa.boar.chat

import kotlinx.coroutines.cancelAndJoin

import kotlinx.coroutines.plus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.chat.AskModelContext
import team.sopa.boar.engine.GenerationMetrics
import team.sopa.boar.engine.answer.AnswerEvent
import team.sopa.boar.engine.answer.AnswerPipeline
import team.sopa.boar.engine.answer.AnswerRequest
import team.sopa.boar.engine.answer.AnswerStage
import team.sopa.boar.engine.answer.AnswerText
import team.sopa.boar.engine.answer.SourceGroups
import team.sopa.boar.engine.answer.DeepResearch
import team.sopa.boar.engine.answer.HistoryBudget
import team.sopa.boar.engine.answer.PipelineInput
import team.sopa.boar.engine.answer.PersonalFiles
import team.sopa.boar.engine.answer.PlanExecutor
import team.sopa.boar.engine.answer.ResearchStage
import team.sopa.boar.engine.answer.SessionEngine
import team.sopa.boar.engine.answer.Summarize
import team.sopa.boar.engine.answer.StagedAnswer
import team.sopa.boar.engine.answer.StagedEvent
import team.sopa.boar.engine.answer.StagedRequest
import team.sopa.boar.engine.answer.StagedStep
import team.sopa.boar.engine.models.AssetKind
import team.sopa.boar.engine.models.StartLoad
import team.sopa.boar.engine.rag.Chunk
import team.sopa.boar.engine.rag.ConversationHistory
import team.sopa.boar.engine.rag.ConversationTurn
import team.sopa.boar.engine.routing.AdaptivePlan
import team.sopa.boar.engine.routing.Classify
import team.sopa.boar.engine.routing.ModelRole
import team.sopa.boar.engine.routing.Profiles
import team.sopa.boar.engine.routing.RoutingPreset
import team.sopa.boar.engine.routing.TaskType
import team.sopa.boar.engine.settings.AnswerLimit
import team.sopa.boar.plugins.AnswerChecks
import team.sopa.boar.plugins.AnswerTools
import team.sopa.boar.plugins.ToolResult

/** A source as the chat shows it (and stores with the answer): the passage the model read, and its similarity to the question. */
data class Source(val title: String, val source: String?, val relevance: Double?, val similarity: Double? = null, val docId: String? = null, val body: String = "")

data class UiMessage(
    val id: String,
    val role: String,
    val text: String,
    val sources: List<Source> = emptyList(),
    val feedback: String? = null,
    val stopped: Boolean = false,
    val timedOut: Boolean = false,
    val receipt: Receipt? = null,
    /** It ended at this token limit, not by itself (shown as a note with a redo at [nextLimit]). */
    val cutAt: Int? = null,
    val nextLimit: Int? = null,
    /** Why it failed (a native.chat.error* key): stays under the answer after the next question and in the saved chat. */
    val error: String? = null,
    /** Boar Staged answers: the steps and what they found; null for an answer written the usual way. */
    val staged: StagedView? = null,
    /** A plugin that would have answered this question but is off (its id): one line under the answer (adam, Nov 12). */
    val offPlugin: String? = null,
    /**
     * A user message sent by a "Keep going" step (KeepGoing.Step.key): its [text] is the real question, the bubble shows
     * the verb (adam, Nov 14: "A", the verb alone). Null for a typed question.
     */
    val step: String? = null,
)

/**
 * What a staged answer shows over its text (team.sopa.boar.engine.answer.StagedAnswer): the step running now, the
 * sentences from the sources that answer (each with its passage), the passages the research step added, the numbers no
 * source has, and when each step happened. Kept with the answer, so a reopened chat shows it too.
 */
data class StagedView(
    val step: team.sopa.boar.engine.answer.StagedStep? = null,
    val keyFacts: List<KeyFactView> = emptyList(),
    val added: Int = 0,
    val unsupported: List<String>? = null,
    val times: Map<String, Double> = emptyMap(),
    val stopped: Boolean = false,
    /** A health question: each sentence shows with its passage, never alone. */
    val withPassages: Boolean = false,
    /** The other sources (not among [keyFacts], and what the research step added), behind "+N sources" (adam, Nov 10). */
    val more: List<KeyFactView> = emptyList(),
)

/** A key fact as shown: the sentence, the passage it comes from (opened by a tap), and its source for the reader. */
data class KeyFactView(val sentence: String, val passage: String, val source: Source)

/** What the assistant is doing for the message being answered. */
sealed interface Activity {
    data object Retrieving : Activity
    data object Thinking : Activity
    data object Generating : Activity
    /** Deep Research: stage, sub-question index (1-based) and count, and the sub-question being researched. */
    data class Researching(val stage: ResearchStage, val index: Int?, val count: Int?, val subQuestion: String? = null) : Activity
}

/** A note over the chat about the answer model and the phone's memory, with what the user can do about it. */
sealed interface LoadNote {
    val modelId: String
    val label: String

    /**
     * [label]'s last load closed the app (not enough memory) and it isn't loaded on its own. [fallbackLabel] answers
     * instead (null: no model can); [onlyModel]: no other model is installed.
     */
    data class Crashed(override val modelId: String, override val label: String, val fallbackLabel: String?, val onlyModel: Boolean) : LoadNote

    /** [label] is rated RED for this phone's memory and wasn't loaded; "Load anyway" loads it. */
    data class Risky(override val modelId: String, override val label: String, val onlyModel: Boolean) : LoadNote
}

/** [title]: the oldest chat a new one would delete; [more]: how many others go with it; [max]: the user's limit. */
data class PruneNote(val title: String, val more: Int, val max: Int)

data class ChatState(
    val sessionId: String? = null,
    val messages: List<UiMessage> = emptyList(),
    val answeringId: String? = null,
    /** When the answer being written started (elapsedRealtime), for its seconds; kept here, not in the bubble, so scrolling it away and back doesn't restart them. */
    val answerStartedAt: Long = 0L,
    val activity: Activity? = null,
    val liveTokPerSec: Double? = null,
    val modelLabel: String? = null,
    /** The model writing the answer in progress when it isn't [modelLabel] (adaptive routing sent it elsewhere). */
    val answeringModel: String? = null,
    val noModel: Boolean = false,
    val deepResearch: Boolean = false,
    /** Advanced tweaks → Show technical details (AppSettings.showLiveStats). */
    val technicalDetails: Boolean = false,
    /** The live tok/s in the header while BOAR answers (Display & theme; AppSettings.showLiveSpeed). */
    val liveSpeed: Boolean = false,
    /** Advanced tweaks → Sources layout "above" (B+): the sources open above each answer, not listed under it. */
    val sourcesAbove: Boolean = false,
    val error: String? = null,
    /** The answer model's memory note (StartLoad): it closed the app last time, or may not fit; with its actions. */
    val loadNote: LoadNote? = null,
    /** A new chat would delete the oldest under the user's limit: asked first (t9 0110 item 2). */
    val pruneNote: PruneNote? = null,
    /** While the model loads or the corpus indexes: the line the Expo app shows under the header (chatScreen.*). */
    val loadingKey: String? = null,
    /** The model being loaded, by name, and the engine when it isn't BOAR's own (Big Boar…), for the banner. */
    val loadingModel: String? = null,
    val loadingEngine: String? = null,
    val loadingSince: Long = 0,
    val personalityId: String = BoarServices.DEFAULT_PERSONALITY_ID,
    /** A short message over the chat for about 2 s (Toast.tsx): the new tone, a reused question. */
    val toast: String? = null,
    val toneIcon: String = "",
    /** Stop was tapped and the answer hasn't ended yet (a step in progress finishes first). */
    val stopping: Boolean = false,
    /** The "Mixture of agents" line under the header was closed for good. */
    val researchBannerHidden: Boolean = false,
    /** The styles the header sheet offers (id to icon): "custom" only when a custom prompt is written. */
    val tones: List<Pair<String, String>> = emptyList(),
    val online: Boolean = false,
    /** Suggested question keys for the empty chat (chat.suggestions.*). */
    val suggestions: List<String> = emptyList(),
    /** The question picked in setup, for the input box once (not sent). */
    val prefill: String? = null,
    /** A chat just deleted from the menu, still undoable (id to title); really deleted when its time runs out. */
    val pendingDelete: Pair<String, String>? = null,
    /** Questions sent while BOAR was answering, waiting their turn (PromptQueue), first to run first. */
    val queued: List<QueuedPrompt> = emptyList(),
)

data class QueuedPrompt(val id: Long, val text: String)

/**
 * The chat (port of ChatScreen.tsx's send flow): Deep Research when it's on, adaptive routing when that's on, the
 * default answer path otherwise; the answer streams into its message, and both turns are saved with the answer's
 * sources. Stop cancels the generation in flight and the stages after it.
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val services = BoarServices.get(app)
    /**
     * Background work runs here: an unexpected error ends that job with a toast instead of closing the app, and an
     * answer it cut short is closed with the generic error line (layer 1 of the error handler, [team.sopa.boar.app.Safe]).
     */
    private val safeScope = viewModelScope + team.sopa.boar.app.Safe.handler(app) { _ ->
        _state.update { it.copy(answeringId = null, answeringModel = null, activity = null, stopping = false, error = "generic") }
    }
    private val _state = MutableStateFlow(ChatState(deepResearch = services.settings.deepResearchMode, technicalDetails = services.settings.showLiveStats,
        liveSpeed = services.settings.showLiveSpeed))
    val state: StateFlow<ChatState> = _state
    private var job: Job? = null
    /** A chat title or summary still being written; a new question stops it (one model, one generation). */
    private var background: Job? = null
    @Volatile private var stopRequested = false

    private val suggestionData = team.sopa.boar.engine.chat.Suggestions.parse(app.assets.open("boar-data/suggestions.json").bufferedReader().use { it.readText() })

    /**
     * Every prompt idea this model and language can show; [pickIdeas] draws a few at random. Declared before init:
     * a property declared after it is reset to its initial value once init's refreshModel() has filled it.
     */
    private var ideaPool: List<String> = emptyList()

    private var preloading: Job? = null
    /**
     * A title or summary skipped because the answer ended with BOAR in the background, written when BOAR is back on
     * screen. Run then, it would hold the model past MIUI's freeze about 8 s after the answering service stops, and MIUI
     * force-stops a frozen BOAR still holding the model (F3, Oct 19, 17:46:30-40).
     */
    @Volatile private var deferredAfterAnswer: (() -> Unit)? = null
    private val onForeground: () -> Unit = { resumeAfterAnswer() }
    /** For Free memory when I leave BOAR: the model isn't unloaded while an answer, title, summary or preload runs. */
    private val busy: () -> Boolean = { job?.isActive == true || background?.isActive == true || preloading?.isActive == true }
    /** Leaving the screen during a Labs engine answer stops it, as Stop does (BoarServices.onBackground). Declared before init. */
    /** Ends everything before the model is unloaded, switched or its engine closed: the queue goes too (validator Q-1). */
    private val stopper: () -> Unit = { stopAll() }

    init {
        refreshModel()
        refreshTone()
        watchConnectivity(app)
        preload()
        services.addBusyCheck(busy)
        services.addStopper(stopper)
        services.addForegroundHook(onForeground)
        // Questions that were waiting when the app was closed are gone: say so, once.
        queuePrefs().getInt(QUEUE_WAITING, 0).takeIf { it > 0 }?.let { n ->
            queuePrefs().edit().putInt(QUEUE_WAITING, 0).apply()
            team.sopa.boar.ui.Toasts.show(team.sopa.boar.ui.I18n.t(app, "native.chat.queueDropped", "count" to n))
        }
        // "Forgot PIN" erased the chats: the one open here goes too (it stayed on screen above an empty history, X6).
        safeScope.launch {
            services.pinLock.erased.drop(1).collect {
                queue.clear(); publishQueue()
                // Forgot PIN erased every chat: an undoable delete has nothing left to undo.
                pendingDeleteJob?.cancel(); pendingDeleteJob = null; _state.update { it.copy(pendingDelete = null) }
                job?.cancel()
                _state.update { it.copy(sessionId = null, messages = emptyList(), error = null, suggestions = pickIdeas()) }
            }
        }
    }

    override fun onCleared() {
        services.removeBusyCheck(busy)
        services.removeStopper(stopper)
        services.removeForegroundHook(onForeground)
        super.onCleared()
    }

    /**
     * Menu → Close BOAR: stops an answer (what it wrote so far is saved, as with Stop) and a title or summary, then
     * frees the model. The caller closes the app after.
     */
    suspend fun closeAndFreeMemory(onStage: suspend (String) -> Unit = {}): Boolean {
        // Each step as it really happens, for the closing animation (Boar Labs): stopping, saving, freeing.
        if (_state.value.answeringId != null) onStage("stopping")
        // Closing: nothing waiting may start on the model being freed (validator Q-1).
        stopAll()
        onStage("saving")
        // The stopped answer saves what it wrote as it ends; until it has, closing would lose it.
        if (kotlinx.coroutines.withTimeoutOrNull(CLOSE_WAIT_MS) { job?.join() } == null && job?.isActive == true) {
            // Nothing written yet: a model still loading (a big one takes over 20 s), with nothing to save. Its answer is
            // skipped once it loads, so wait for that instead of leaving BOAR open with the model (X6, LFM2.5 8B, Oct 20).
            val writing = _state.value.answeringId?.let { id -> _state.value.messages.firstOrNull { it.id == id }?.text?.isNotBlank() } == true
            if (writing || kotlinx.coroutines.withTimeoutOrNull(CLOSE_LOAD_WAIT_MS) { job?.join() } == null) {
                showToast("native.drawer.closeStillSaving")
                return false
            }
        }
        stopBackground()
        // Waits for a load in progress: cancelled, it closes the model itself (LlamaSession.load); not waited for, unload
        // found nothing and the model stayed in memory.
        preloading?.cancelAndJoin()
        onStage("unloading")
        services.host.unload()
        onStage("freeing")
        return true
    }

    /**
     * Loads the model when the chat opens, as the Expo app does, so the first answer doesn't wait for it. Not a model
     * whose last load closed the app, nor one that may not fit this phone's memory: the chat asks first (StartLoad).
     */
    private fun preload() {
        if (services.activeModel() == null) return
        val decision = services.startDecision()
        if (decision is StartLoad.Decision.AskRisky || decision is StartLoad.Decision.NoFallback) {
            services.loadGuard.consume()
            _state.update { it.copy(loadNote = noteFor(decision)) }
            return
        }
        // The model that really loads: the active one, or a fallback when its last load crashed the app.
        val model = services.answerModel() ?: return
        val engine = services.engineLabelOf(model)
        _state.update { it.copy(loadingKey = "chatScreen.mountingWeights", loadingModel = model.shortName, loadingEngine = engine,
            loadingSince = android.os.SystemClock.elapsedRealtime()) }
        preloading = safeScope.launch(Dispatchers.Default) {
            runCatching { services.activeSession() }
            // Shown once: the model that closed the app last time, and the one answering instead.
            val crash = services.loadGuard.consume()
            _state.update { it.copy(loadingKey = null, loadNote = crash?.let(::crashNote) ?: it.loadNote) }
            if (crash != null) refreshModel()
        }
    }

    /** The note for a decision that doesn't load the user's model on its own; null when it loads. */
    private fun noteFor(d: StartLoad.Decision): LoadNote? = when (d) {
        is StartLoad.Decision.AskRisky -> LoadNote.Risky(d.modelId, labelOf(d.modelId), d.onlyModel)
        is StartLoad.Decision.NoFallback -> LoadNote.Crashed(d.blockedId, labelOf(d.blockedId), null, d.onlyModel)
        else -> null
    }

    private fun crashNote(c: team.sopa.boar.engine.models.LoadGuard.Crash): LoadNote.Crashed {
        val answering = services.answerModel()?.takeIf { it.id != c.crashedModelId }
        val only = services.installed(AssetKind.LLM).none { it.id != c.crashedModelId }
        val label = services.catalog.byId(c.crashedModelId)?.label?.let(::shortLabel) ?: c.crashedLabel
        return LoadNote.Crashed(c.crashedModelId, label, answering?.label?.let(::shortLabel), only)
    }

    private fun labelOf(modelId: String) = services.catalog.byId(modelId)?.label?.let(::shortLabel) ?: modelId

    /** A question asked while the memory note's model wasn't loaded: sent once the user loads it. */
    @Volatile private var heldQuestion: String? = null

    /** The note a question gets instead of an answer: the user's model may not fit, or closed the app last time. */
    private fun gateNote(): LoadNote? {
        val d = services.startDecision()
        val id = when (d) {
            is StartLoad.Decision.AskRisky -> d.modelId
            is StartLoad.Decision.NoFallback -> d.blockedId
            else -> return null
        }
        // Already loaded in this process (picked in the Model Catalog): it answers.
        val file = services.catalog.byId(id)?.filename
        if (file != null && services.host.residentFilename() == file && services.host.current() != null) return null
        return noteFor(d)
    }

    /** The memory note's "Load anyway": loads the model now, and at start from now on without asking. */
    fun loadAnyway(modelId: String) {
        services.rememberLoadAnyway(modelId)
        userLoad(modelId)
    }

    /** The crash note's "Try again with less memory": loads the model now, keeping the mapped file (LoadGuard). */
    fun retryWithLessMemory(modelId: String) {
        services.loadGuard.retryMapped(modelId)
        // The user's call: a model that may not fit isn't asked about again at start.
        if (services.catalog.byId(modelId)?.let(services::isRisky) == true) services.rememberLoadAnyway(modelId)
        userLoad(modelId)
    }

    /** The first question of a new chat, held while [ChatState.pruneNote] asks; and the user's yes to deleting. */
    @Volatile private var prunedQuestion: Pair<String, Int?>? = null
    @Volatile private var pruneAccepted = false

    /** The prune note's "Keep all chats": nothing is deleted, now or later; the question goes. */
    fun pruneKeepAll() {
        services.settings.maxSavedSessions = team.sopa.boar.engine.settings.AppSettings.KEEP_ALL_CHATS
        resumePruned()
    }

    /** The prune note's "Delete it": the question goes, and the oldest chat with it after the answer, as before. */
    fun pruneDelete() {
        pruneAccepted = true
        resumePruned()
    }

    /** The prune note closed: the question goes, unasked, with what was set for it (as dismissLoadNote). */
    fun pruneCancel() {
        prunedQuestion = null
        dropOnce()
        _state.update { it.copy(pruneNote = null) }
    }

    private fun resumePruned() {
        val held = prunedQuestion.also { prunedQuestion = null }
        _state.update { it.copy(pruneNote = null) }
        if (held != null) send(held.first, held.second) else pruneAccepted = false
    }

    /** The held-back model whose note the user closed: not shown again for it in this run. */
    private var heldBackClosed: String? = null

    /** The note's "Model Catalog" / "Pick another model": the caller opens the catalog. */
    fun openedCatalog() {
        heldQuestion = null
        dropOnce()
        dropQueueWithNote()
        heldBackClosed = (_state.value.loadNote as? LoadNote.Crashed)?.modelId ?: heldBackClosed
        _state.update { it.copy(loadNote = null) }
    }

    private fun userLoad(modelId: String) {
        val note = _state.value.loadNote
        val held = heldQuestion.also { heldQuestion = null }
        _state.update { it.copy(loadNote = null, loadingKey = "chatScreen.mountingWeights") }
        val previous = preloading
        preloading = safeScope.launch(Dispatchers.Default) {
            previous?.join()
            val loaded = runCatching { services.host.load(modelId) }.getOrNull() != null
            withContext(Dispatchers.Main) {
                refreshModel()
                // A load that returned without a model (not a closed app): the note comes back, so the user can still choose.
                _state.update { it.copy(loadingKey = null, loadNote = if (loaded) null else note) }
                // The held question keeps its place: first, before any that waited behind it.
                if (loaded && held != null) send(held, null, fromQueue = true, serviceUp = false)
                // Loaded with nothing held: questions waiting behind the note start now (validator Q-3).
                else if (loaded) runNext(fresh = true)
                // A failed load: the held question went, and what was set for it goes too, or the next typed question
                // would run Detailed or with Deep Research (validator R-A5).
                else { dropOnce(); dropQueueWithNote() }
            }
        }
    }

    private fun refreshTone() {
        val id = services.settings.personalityId ?: services.personalities.defaultId
        val custom = BoarServices.SHOW_CUSTOM_STYLE && services.settings.customSystemPrompt.orEmpty().isNotBlank()
        val tones = services.personalities.personalities.filter { it.id != "custom" || custom }.map { it.id to it.icon }
        _state.update { it.copy(personalityId = id, toneIcon = services.personalities.get(id).icon, tones = tones, researchBannerHidden = services.settings.researchBannerHidden) }
    }

    fun hideResearchBanner() {
        services.settings.researchBannerHidden = true
        _state.update { it.copy(researchBannerHidden = true) }
    }

    /** The header sheet: answer in this style from the next question on. */
    fun setTone(id: String) {
        services.settings.personalityId = id
        refreshTone()
    }

    /** Back from settings: the tone, Deep Research and model may have changed. */
    fun refreshSettings() {
        refreshTone()
        refreshModel()
        _state.update { it.copy(deepResearch = services.settings.deepResearchMode, technicalDetails = services.settings.showLiveStats, liveSpeed = services.settings.showLiveSpeed,
            sourcesAbove = services.settings.sourcesLayout == team.sopa.boar.engine.settings.AppSettings.SOURCES_ABOVE) }
        // Setup's "How to ask" page: its question waits in the input, once.
        services.settings.pendingFirstQuestion?.let { q -> services.settings.pendingFirstQuestion = null; _state.update { it.copy(prefill = q) } }
        // The open chat was erased (Clear all chat history, or a forgotten PIN): start a new one.
        val open = _state.value.sessionId
        if (open != null && services.chats.listSessions().none { it.id == open }) newChat()
    }

    fun showToast(key: String) = team.sopa.boar.ui.Toasts.show(team.sopa.boar.ui.I18n.t(getApplication(), key))
    fun hideToast() = _state.update { it.copy(toast = null) }
    fun prefilled() = _state.update { it.copy(prefill = null) }

    /** A plugin page's Try row, long-pressed: the question waits in the input box to edit (AskFromPlugin). */
    fun prefillWith(q: String) = _state.update { it.copy(prefill = q) }

    /** ONLINE / OFFLINE in the header (connectivity.pure.ts isOnline: connected and the internet not known unreachable). */
    private fun watchConnectivity(app: Application) {
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        // connectivity.pure.ts isOnline: connected and reachable (Android's validated internet), not just a network.
        fun online(caps: android.net.NetworkCapabilities?) = caps != null &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        _state.update { it.copy(online = online(cm.getNetworkCapabilities(cm.activeNetwork))) }
        runCatching {
            // The callback's own network and capabilities: re-reading activeNetwork in onLost can still return the
            // network just lost (airplane mode showed ONLINE).
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) { _state.update { it.copy(online = online(caps)) } }
                override fun onLost(network: android.net.Network) { _state.update { it.copy(online = false) } }
            })
        }
    }

    /** After setup or a model change: the active model's name, or that there's none. */
    fun refreshModel() {
        val m = services.answerModel()
        val lang = getApplication<Application>().resources.configuration.locales[0]?.language
        val installed = services.catalog.corpusCatalog.filter { services.fileOf(it).isFile }.map { it.id }
        // shortModelLabel: the catalog label without its parenthesis ("Qwen3-4B-Instruct-2507").
        val label = m?.label?.let(::shortLabel)
        // Fewer than 3 tested on this model (a Hugging Face model has none; Qwen2.5 1.5B has 1 in Portuguese): fill from
        // the ideas tested on the other models, same language, only for packs on the phone.
        val own = suggestionData.forModel(m?.id, lang, installed)
        val tested = if (own.size >= 3) own else (own + suggestionData.validation.byModel.keys.flatMap { suggestionData.forModel(it, lang, installed) }).distinct()
        // Plus the short questions of BOAR's own 17-question evaluation, so each new chat shows a different few.
        ideaPool = (tested + BENCH_IDEAS).distinct()
        // Ideas whose source is a pack the user downloaded (not the built-in library) come up more often (IdeaPicker).
        val packs = installed.toSet()
        ideaFromPacks = tested.filter { k -> suggestionData.sources.firstOrNull { it.key == k }?.corpus?.any { it in packs && it != "builtin" } == true }.toSet()
        // The pick is held back (its last load closed the app): the note says so for as long as it is, not only once after
        // the crash, so the header naming another model than the Model Catalog's pick is never left unexplained (X6, Nov 4).
        val heldBack = services.activeModel()?.takeIf { services.answeringInstead() && it.id != heldBackClosed }
        _state.update { s ->
            s.copy(modelLabel = label, noModel = m == null, suggestions = pickIdeas(),
                loadNote = s.loadNote ?: heldBack?.let { a -> LoadNote.Crashed(a.id, shortLabel(a.label), label, services.installed(AssetKind.LLM).none { it.id != a.id }) })
        }
        // A note that no longer holds (the model was loaded, unblocked or replaced) goes away.
        val note = _state.value.loadNote
        val holds = when (note) {
            null -> true
            // Picking another model in the Model Catalog closes it (adam, Nov 10): the note is about the pick, and the pick
            // changed. It stays while the crashed model is still blocked and still the pick.
            is LoadNote.Crashed -> services.loadGuard.isBlocked(note.modelId) &&
                team.sopa.boar.engine.models.LoadNotes.stillAbout(note.modelId, services.activeModel()?.id)
            is LoadNote.Risky -> services.startDecision().let { it is StartLoad.Decision.AskRisky && it.modelId == note.modelId }
        }
        if (!holds) _state.update { it.copy(loadNote = null) }
    }

    /** shortModelLabel: the catalog label without its parenthesis ("Qwen3-4B-Instruct-2507"). */
    private fun shortLabel(label: String): String =
        label.replace(Regex("\\s*\\([^)]*\\)"), "").replace(Regex("\\s{2,}"), " ").trim().ifEmpty { label.trim() }

    fun dismissLoadNote() {
        heldQuestion = null
        dropOnce()
        dropQueueWithNote()
        heldBackClosed = (_state.value.loadNote as? LoadNote.Crashed)?.modelId ?: heldBackClosed
        _state.update { it.copy(loadNote = null) }
    }

    fun setDeepResearch(on: Boolean) {
        services.settings.deepResearchMode = on
        _state.update { it.copy(deepResearch = on) }
    }

    private var ideaFromPacks: Set<String> = emptySet()

    /** Three ideas, never the three shown last (kept across launches), packs' ideas weighted up (adam, Nov 12). */
    private fun pickIdeas(): List<String> {
        val prefs = getApplication<Application>().getSharedPreferences("ideas", android.content.Context.MODE_PRIVATE)
        val last = prefs.getString("last", null)?.split('\n')?.toSet().orEmpty()
        val picked = team.sopa.boar.engine.chat.IdeaPicker.pick(ideaPool, ideaFromPacks, last, IDEAS_SHOWN)
        prefs.edit().putString("last", picked.joinToString("\n")).apply()
        return picked
    }

    fun newChat() {
        if (job?.isActive == true) return
        _state.update { it.copy(sessionId = null, messages = emptyList(), error = null, suggestions = pickIdeas()) }
    }

    /**
     * Delete from the menu with Undo (adam, Nov 12): the chat leaves the list at once and is really deleted
     * (deleteSession, plugin files included) after ChatList.UNDO_MS. Undo in that time leaves everything as it was. A
     * second delete first finishes the one before. If BOAR is closed in that time, the chat stays (the safe side).
     */
    fun deleteWithUndo(id: String, title: String) {
        if (job?.isActive == true && _state.value.sessionId == id) return
        finishPendingDelete()
        if (_state.value.sessionId == id) { queue.clear(); publishQueue(); newChat() }
        _state.update { it.copy(pendingDelete = id to title) }
        pendingDeleteJob = safeScope.launch {
            kotlinx.coroutines.delay(team.sopa.boar.engine.chat.ChatList.UNDO_MS)
            finishPendingDelete()
        }
    }

    fun undoDelete() {
        pendingDeleteJob?.cancel(); pendingDeleteJob = null
        _state.update { it.copy(pendingDelete = null) }
    }

    private var pendingDeleteJob: kotlinx.coroutines.Job? = null

    private fun finishPendingDelete() {
        val (id, _) = _state.value.pendingDelete ?: return
        pendingDeleteJob?.cancel(); pendingDeleteJob = null
        _state.update { it.copy(pendingDelete = null) }
        deleteSession(id)
    }

    fun renameSession(id: String, title: String) {
        val t = title.trim().take(80).ifEmpty { return }
        safeScope.launch(Dispatchers.IO) { runCatching { services.chats.setTitle(id, t) }.onFailure { showToast("native.chat.dbFailed") } }
    }

    /** The drawer's 🗑️: the chat and its messages; the open one becomes a new chat. */
    fun deleteSession(id: String) {
        // Not the chat still being answered: its answer would be saved into a deleted session.
        if (job?.isActive == true && _state.value.sessionId == id) return
        // A database error (disk full, a damaged file) is told in a toast; uncaught here it would close the app.
        safeScope.launch(Dispatchers.IO) {
            // What the labs made for this chat's answers goes first, while the answers can still be listed.
            runCatching { team.sopa.boar.plugins.UserData.eraseAnswers(services.chats.messages(id).filter { it.role == "assistant" }.map { it.id }) }
            // Once more if the delete fails (a busy database): the plugins' files are already gone, so the chat must not stay
            // half deleted; a second failure is logged and told (validator M-6).
            runCatching { services.chats.deleteSession(id) }
                .recoverCatching { kotlinx.coroutines.delay(300); services.chats.deleteSession(id) }
                .onFailure { e -> android.util.Log.w("BOAR", "chat delete failed after its plugin files were erased", e); showToast("native.chat.dbFailed") }
        }
        if (_state.value.sessionId == id) { queue.clear(); publishQueue(); newChat() }
    }

    fun openSession(id: String) {
        if (job?.isActive == true) return
        safeScope.launch {
            val msgs = runCatching {
                withContext(Dispatchers.IO) { services.chats.messages(id) }.map { r ->
                    UiMessage(r.id, r.role, r.text, sourcesFromMeta(r.meta), r.feedback, receipt = receiptFromMeta(r.meta), error = errorFromMeta(r.meta),
                        staged = receiptFromMeta(r.meta).let { stagedFromMeta(r.meta, it) }, step = if (r.role == "user") stepFromMeta(r.meta) else null)
                }
            }.getOrElse { showToast("native.chat.dbFailed"); return@launch }
            _state.update { it.copy(sessionId = id, messages = msgs, error = null) }
        }
    }

    fun rate(messageId: String, rating: String) {
        val current = _state.value.messages.firstOrNull { it.id == messageId }?.feedback
        val next = if (current == rating) null else rating
        _state.update { s -> s.copy(messages = s.messages.map { if (it.id == messageId) it.copy(feedback = next) else it }) }
        // Once, on the first rating: ratings stay on the phone and nothing uses them yet.
        if (next != null && !services.settings.ratingNoteShown) { services.settings.ratingNoteShown = true; showToast("native.chat.ratingNote") }
        safeScope.launch(Dispatchers.IO) {
            runCatching { services.chats.setFeedback(messageId, next) }.onFailure {
                _state.update { s -> s.copy(messages = s.messages.map { if (it.id == messageId) it.copy(feedback = current) else it }) }
                showToast("native.chat.dbFailed")
            }
        }
    }

    /**
     * Stop and clear the queue: for the paths that end everything and then unload, switch or close the model (Close
     * BOAR, the Model Catalog's switch or delete, leaving during a Labs engine answer). The Stop button uses [stop],
     * which ends only the answer in progress (validator Q-1).
     */
    fun stopAll() {
        if (!queue.isEmpty()) { queue.clear(); publishQueue() }
        stop()
    }

    fun stop() {
        stopRequested = true
        if (_state.value.answeringId != null) _state.update { it.copy(stopping = true) }
        services.host.current()?.cancel()
    }

    /** Asks the question of [answerId] again (an answer that came back empty). */
    /** Asks the question of [answerId] again with the Labs plugins skipped, so the model answers it ("Ask BOAR instead"). */
    fun askModel(answerId: String) {
        // Not queued: the "skip the plugins" flag belongs to this question only, so not while others wait either (it would
        // be taken by the next one in line; CodeRabbit).
        if (job?.isActive == true || handoff || !queue.isEmpty() || _state.value.loadNote != null) return
        val messages = _state.value.messages
        val i = messages.indexOfFirst { it.id == answerId }
        val q = messages.take(i.coerceAtLeast(0)).lastOrNull { it.role == "user" }?.text ?: return
        skipToolsOnce = true
        // The plugin's answer stays in the chat, and its result goes to the model to explain, numbers unchanged (adam, Nov 12).
        val instant = messages.getOrNull(i)
        bypassed = instant?.receipt?.tool?.let { tool -> ToolResult.Context(tool, AskModelContext.of(instant.text)) }
        send(q)
    }

    /** A tap on an instant answer being typed: the rest at once (InstantTyping). */
    @Volatile private var skipTyping = false
    fun skipTyping() { skipTyping = true }
    private fun rest(text: String, from: Int) = text.substring(from.coerceAtMost(text.length))

    /** What the bypassed plugin answered, for the model's prompt on [askModel]'s question only. */
    @Volatile private var bypassed: ToolResult.Context? = null

    /** Set by [askModel] for the next question only. */
    @Volatile private var skipToolsOnce = false

    /** "Keep going" (t9 0235): for the next question only, the style ("detailed"), Deep Research, and the step it came from. */
    @Volatile private var styleOnce: String? = null
    @Volatile private var deepOnce: Boolean? = null
    @Volatile private var stepOnce: String? = null

    /**
     * The held question went with its note (closed, or the catalog opened instead): what was set for it goes too, or the
     * next typed question would run in Detailed or Deep Research, skip the plugins, and show a step's verb (validator A-2).
     */
    private fun dropOnce() {
        stepOnce = null; styleOnce = null; deepOnce = null; skipToolsOnce = false; bypassed = null
    }

    /**
     * A "Keep going" step under [answerId] (t9 0235): the same question again, for that answer only. Go deeper in Detailed,
     * Research this with Deep Research, Ask BOAR as askModel. The style and the Deep Research switch stay as the user set
     * them, as redoWithLimit leaves the limit. Not while BOAR answers or questions wait (the bubbles hide then).
     */
    fun keepGoing(answerId: String, step: team.sopa.boar.engine.chat.KeepGoing.Step) {
        if (job?.isActive == true || handoff || !queue.isEmpty() || _state.value.loadNote != null) return
        val messages = _state.value.messages
        val i = messages.indexOfFirst { it.id == answerId }
        val q = messages.take(i.coerceAtLeast(0)).lastOrNull { it.role == "user" }?.text ?: return
        // Continue is its own message ("Continue" / "Continuar"), a meta follow-up in the same style: no step mark, so the
        // bubble shows what was sent (adam, 16 Nov).
        if (step == team.sopa.boar.engine.chat.KeepGoing.Step.CONTINUE) { send(team.sopa.boar.ui.I18n.t(getApplication(), "native.chat.kgContinue")); return }
        stepOnce = step.key
        when (step) {
            team.sopa.boar.engine.chat.KeepGoing.Step.DEEPER -> { styleOnce = "detailed"; send(q) }
            team.sopa.boar.engine.chat.KeepGoing.Step.CONTINUE -> Unit
            team.sopa.boar.engine.chat.KeepGoing.Step.RESEARCH -> { deepOnce = true; send(q) }
            team.sopa.boar.engine.chat.KeepGoing.Step.ASK_BOAR -> askModel(answerId)
        }
    }

    fun retry(answerId: String) {
        if (job?.isActive == true) return
        val messages = _state.value.messages
        val i = messages.indexOfFirst { it.id == answerId }
        val q = messages.take(i.coerceAtLeast(0)).lastOrNull { it.role == "user" }?.text ?: return
        send(q)
    }

    /** Asks [answerId]'s question again with a higher token limit, for this answer only (the setting stays as it is). */
    fun redoWithLimit(answerId: String, limit: Int) {
        if (job?.isActive == true) return
        val messages = _state.value.messages
        val i = messages.indexOfFirst { it.id == answerId }
        val q = messages.take(i.coerceAtLeast(0)).lastOrNull { it.role == "user" }?.text ?: return
        send(q, limit)
    }

    /** [limit] overrides the answer's token limit (the "Allow more and redo" note); null uses the setting. */
    fun send(query: String, limit: Int? = null) = send(query, limit, fromQueue = false, serviceUp = false)

    /**
     * While BOAR answers, a question waits its turn (PromptQueue, adam Nov 10) and runs after, as the chat's next turn.
     * [fromQueue]: the queue starting it (it doesn't join the queue again). [serviceUp]: the answer notification is still
     * up from the answer before.
     */
    private fun send(query: String, limit: Int?, fromQueue: Boolean, serviceUp: Boolean) {
        val q = query.trim()
        if (q.isEmpty()) return
        // While an answer runs, or between its end and the next waiting question's start, or while questions wait: the
        // question joins the end of the queue, so nothing jumps it (validator Q-2).
        if (job?.isActive == true || (!fromQueue && (handoff || !queue.isEmpty()))) {
            if (queue.add(q)) publishQueue() else showToast("native.chat.queueFull")
            // Waiting with nothing running (behind a note the user answered): start the oldest one now.
            if (job?.isActive != true && !handoff && _state.value.loadNote == null) runNext(fresh = true)
            return
        }
        // The user's model may not fit, or closed the app last time: the note asks first, and the question waits.
        gateNote()?.let { n ->
            // A second question while the note shows waits behind the first instead of replacing it (validator Q-4,
            // CodeRabbit); the queue starts after the held one once the model loads, or goes with the note.
            if (heldQuestion != null && _state.value.loadNote != null) {
                if (queue.add(q)) publishQueue() else showToast("native.chat.queueFull")
                return
            }
            heldQuestion = q
            // A waiting question that stops here ends the answer notification the queue kept up.
            if (serviceUp) team.sopa.boar.app.AnswerService.stop(getApplication())
            _state.update { it.copy(loadNote = n) }
            return
        }
        // A new chat under a limit the user set: say which chat it deletes before it does (adam, Nov 15; t9 0110 item 2).
        if (_state.value.sessionId == null && !pruneAccepted && _state.value.pruneNote == null) {
            val max = services.settings.maxSavedSessions
            val going = runCatching { services.chats.pushedOutByNew(max) }.getOrDefault(emptyList())
            if (going.isNotEmpty()) {
                prunedQuestion = q to limit
                if (serviceUp) team.sopa.boar.app.AnswerService.stop(getApplication())
                _state.update { it.copy(pruneNote = PruneNote(going.first().title, going.size - 1, max)) }
                return
            }
        }
        pruneAccepted = false
        team.sopa.boar.app.Haptics.sent(getApplication())
        stopRequested = false
        routedModelId = null
        answeredById = null
        // Started while BOAR is on screen (Android allows it only then); keeps BOAR alive if the user leaves mid-answer.
        if (!serviceUp) team.sopa.boar.app.AnswerService.start(getApplication())
        // The app's language is the answer's last word when the question can't tell (AnswerLanguage, t9 0420).
        team.sopa.boar.engine.answer.AnswerLanguage.appLanguage = services.settings.languageId
        val stepNow = stepOnce.also { stepOnce = null }
        val styleNow = styleOnce.also { styleOnce = null }
        val deepNow = deepOnce.also { deepOnce = null }
        job = safeScope.launch(Dispatchers.Default) {
            stopBackground()
            val chats = services.chats
            val isNewSession = _state.value.sessionId == null
            val prior = _state.value.messages.filter { it.text.isNotEmpty() }
            val userId = chats.newId()
            val answerId = chats.newId()
            // A failed write (disk full) shows as a failed answer instead of closing the app.
            val sessionId = try {
                // The question stands in as the title until the model writes one.
                (_state.value.sessionId ?: chats.createSession().also { s -> chats.setTitle(s.id, q.take(60)) }.id)
                    .also { chats.addMessage(it, "user", q, userId); if (stepNow != null) chats.setMessageMeta(userId, stepMeta(stepNow)) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(error = e.message ?: e.toString(), messages = it.messages + UiMessage(userId, "user", q) + UiMessage(answerId, "assistant", "")) }
                return@launch
            }
            _state.update {
                it.copy(sessionId = sessionId, error = null, answeringId = answerId, answerStartedAt = android.os.SystemClock.elapsedRealtime(), activity = Activity.Retrieving, liveTokPerSec = null,
                    messages = it.messages + UiMessage(userId, "user", q, step = stepNow) + UiMessage(answerId, "assistant", ""))
            }
            val settings = services.settings
            // Health and first aid answer in Succinct even when Detailed is picked: a sentence-level audit of 15 Detailed
            // answers (r4to, Oct 17) found 20 % of claims wrong, 5 of them safety errors ("a first-degree burn is painless").
            val chosenStyle = styleNow ?: settings.personalityId ?: services.personalities.defaultId
            // Personal Files: only the user's own files, whole; no Deep Research, router, answer check or Labs tools
            // but the safety cards (PersonalFiles). ShortAnswer already leaves it alone (Detailed only).
            val personal = PersonalFiles.isOn(chosenStyle)
            // Health and seed-phrase questions answer short even under Detailed: a padded detail there can hurt (r4to's audits).
            // A follow-up in such a chat too ("and for how long?" after a burn question).
            val shortReason = team.sopa.boar.engine.routing.ShortAnswer.reason(chosenStyle, q, prior.lastOrNull { it.role == "user" }?.text)
            // Local Files keeps its files-only prompt: kept short by a reminder instead (t9 2200 c).
            val style = if (shortReason != null && !personal) "succinct" else chosenStyle
            // A small answer model (the 1.5B) gets Detailed without D4's "Always do what the user asks" (r4to, Oct 18).
            val smallModel = (services.answerModel()?.sizeBytes ?: Long.MAX_VALUE) < team.sopa.boar.engine.answer.Personalities.SMALL_MODEL_BYTES
            val (systemPrompt, baseReminder) = services.personalities.promptFor(style, settings.customSystemPrompt, smallModel)
            // A Local Files summary: in the model's words, each result with the mark the code gave it (t9 2330 a).
            val ptQ = team.sopa.boar.engine.answer.AnswerLanguage.of(q) == team.sopa.boar.engine.answer.AnswerLanguage.Lang.PT
            val summaryLine = if (personal && PersonalFiles.asksSummary(q)) (if (ptQ) PersonalFiles.SUMMARY_PT else PersonalFiles.SUMMARY_EN) else null
            val styleReminder = listOfNotNull(baseReminder, team.sopa.boar.engine.routing.ShortAnswer.PERSONAL_SHORT.takeIf { personal && shortReason != null }, summaryLine)
                .joinToString(" ").ifBlank { null }
            val history = ConversationHistory(
                summary = runCatching { chats.listSessions().firstOrNull { it.id == sessionId }?.summary }.getOrNull(),
                // Within a token budget: earlier answers cut to their opening sentences (HistoryBudget).
                turns = HistoryBudget.budget(prior.takeLast(VERBATIM_MESSAGE_COUNT).map { ConversationTurn(it.role, if (it.role == "assistant") AnswerText.stripThinking(it.text) else it.text) }),
            )
            val startAt = System.nanoTime()
            var metrics: GenerationMetrics? = null
            var retrievalMs: Double? = null
            val text = StringBuilder()
            var sources: List<Chunk> = emptyList()
            var reusedSources: List<Source>? = null
            // The answer a meta follow-up ("summary", "continue") is written from: the last one with text.
            val lastAnswer = prior.lastOrNull { it.role == "assistant" && it.text.isNotBlank() }
            // Every answered question so far, oldest first: what "summarize our conversation" is written from.
            // How this answer is written, for the receipt's Mode row (set where the path is chosen below).
            var path: String? = null
            val conversation = prior.zipWithNext().filter { (u, a) -> u.role == "user" && a.role == "assistant" && a.text.isNotBlank() }
                .map { (u, a) -> DeepResearch.Companion.Exchange(u.text, AnswerText.stripThinking(a.text)) }
            var timedOut = false
            var firstAt = 0L
            var tokens = 0
            // The mode this question runs in, kept for the Vault even if Deep Research is switched while it answers. It
            // steps aside in Personal Files.
            val deep = (deepNow ?: _state.value.deepResearch) && !personal
            // The limit the answer could hit, set once the model is loaded; the adaptive router picks its own.
            var limited: Int? = null
            // The tool slot (Boar Labs): an instant answer needs no model, a context is read by it. Not for a recap; under Deep
            // Research only a safety answer (Boar Cohazard): the CO question went to the model there (F3, Oct 19).
            var instantAt = 0L
            var typedAt = false
            val skipTools = skipToolsOnce.also { skipToolsOnce = false }
            val bypass = bypassed.also { bypassed = null }
            val tool = if (skipTools) bypass?.takeIf { !(deep || personal) }
            else if (aboutConversation(q, history, conversation)) null
            else AnswerTools.run(q, prior.map { ConversationTurn(it.role, if (it.role == "assistant") AnswerText.stripThinking(it.text) else it.text) })
                ?.takeIf { !(deep || personal) || PersonalFiles.keepsTool(it.tool) }
                // A merged queue goes to the model whole: an instant answer would take one question and drop the rest (validator
                // Q-8). A safety answer isn't lost: the model reads it first.
                ?.let { r -> if (r is ToolResult.Instant && team.sopa.boar.engine.chat.PromptMerge.isMerged(q)) (if (r.tool.startsWith("safety-")) ToolResult.Context(r.tool, team.sopa.boar.engine.chat.PromptMerge.safetyContext(r.text)) else null) else r }
            val instant = tool as? ToolResult.Instant
            val toolPrompt = AnswerTools.systemPrompt(systemPrompt, tool)
            // Saved as it's written, marked interrupted until it ends: if Android closes BOAR mid-answer (Xiaomi did 10-35 s
            // after leaving, Oct 19), the chat reopens with what was written and the note, not an empty bubble.
            var savedAt = 0L
            fun savePartial(force: Boolean = false) {
                val now = System.nanoTime()
                if (!force && now - savedAt < PARTIAL_SAVE_NS) return
                savedAt = now
                runCatching {
                    chats.upsertMessage(sessionId, "assistant", text.toString(), answerId)
                    chats.setMessageMeta(answerId, metaOf(emptyList(), null, team.sopa.boar.engine.UserErrors.INTERRUPTED_KEY))
                }
            }
            savePartial(force = true)
            /** One streamed piece: [edit] changes the text shown (appends the piece, or rebuilds it). */
            fun onText(edit: (StringBuilder) -> Unit) {
                tokens++
                if (firstAt == 0L) firstAt = System.nanoTime()
                edit(text)
                savePartial()
                val elapsed = (System.nanoTime() - firstAt) / 1e9
                _state.update { s ->
                    s.copy(activity = Activity.Generating, liveTokPerSec = if (elapsed > 0.5) tokens / elapsed else s.liveTokPerSec,
                        messages = s.messages.map { if (it.id == answerId) it.copy(text = text.toString()) else it })
                }
            }
            fun onPiece(piece: String) = onText { it.append(piece) }
            /** A write-up thrown away for a new one (Deep Research's fallback, Staged's redo): the screen starts empty again. */
            fun restartText() {
                text.setLength(0)
                _state.update { s -> s.copy(messages = s.messages.map { if (it.id == answerId) it.copy(text = "") else it }) }
            }
            // Boar Staged answers: the default path only (not Personal Files, Deep Research, a recap or small talk). It loads
            // the model itself, after the search, so the sources show while a cold model loads.
            val staged = instant == null && !personal && !deep && team.sopa.boar.plugins.AnswerModes.staged() &&
                !aboutConversation(q, history, conversation) && !team.sopa.boar.engine.routing.ShortAnswer.isSmallTalk(q)
            var stagedView: StagedView? = null
            var stagedTimes: Map<String, Double>? = null
            try {
                // An instant answer doesn't load the model.
                val session = if (instant != null || staged) null else services.activeSession()
                if (session != null) answeredById = services.host.residentId()
                // Read after the load: Auto fits the length to the context the model was opened with.
                val budget = (limit ?: settings.answerTokens(services.host.openedContext)).let {
                    // A thank-you or greeting: Succinct's length in any style. Not the router's 128: a model that thinks first
                    // (LFM2.5) would spend it all on reasoning.
                    if (team.sopa.boar.engine.routing.ShortAnswer.isSmallTalk(q)) minOf(it, team.sopa.boar.engine.settings.AppSettings.AUTO_MAX_TOKENS) else it
                }
                limited = budget
                // Small talk never starts a Deep Research or the router (AnswerPath): "hi" took minutes on a big model.
                val way = team.sopa.boar.engine.routing.AnswerPath.of(deep, aboutConversation(q, history, conversation),
                    !personal && settings.adaptiveRoutingEnabled && services.answerModel().let { it == null || services.runsOnBuiltIn(it) },
                    team.sopa.boar.engine.routing.ShortAnswer.isSmallTalk(q))
                if (stopRequested) {
                    // Stop pressed while the model loaded: the load can't be interrupted, the answer can be skipped.
                } else if (instant != null) {
                    limited = null
                    instantAt = System.nanoTime()
                    // Shown at typing speed (InstantTyping): presentation only, the receipt keeps the real time above.
                    // A tap on the answer (skipTyping) or Stop shows the rest at once; the safety answer never types.
                    val app = getApplication<Application>()
                    val animOff = android.provider.Settings.Global.getFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                    val reader = (app.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager)?.isTouchExplorationEnabled == true
                    skipTyping = false
                    if (!team.sopa.boar.engine.chat.InstantTyping.typed(instant.tool, animOff, reader)) onPiece(instant.text)
                    else {
                        var shownLen = 0
                        for (piece in team.sopa.boar.engine.chat.InstantTyping.pieces(instant.text)) {
                            if (skipTyping || stopRequested) { onPiece(rest(instant.text, shownLen)); shownLen = instant.text.length; break }
                            onPiece(piece); shownLen += piece.length
                            kotlinx.coroutines.delay(team.sopa.boar.engine.chat.InstantTyping.STEP_MS)
                        }
                        if (shownLen < instant.text.length) onPiece(instant.text.substring(shownLen))
                        typedAt = true
                    }
                } else if (staged) {
                    path = "staged"
                    val (introReminder, detailsReminder) = stagedReminders(style, smallModel)
                    var view = StagedView(withPassages = shortReason != null)
                    val stagedIntro = StringBuilder()
                    val stagedDetails = StringBuilder()
                    fun show(v: StagedView, shownSources: List<Source>? = null) {
                        view = v
                        stagedView = v
                        _state.update { s -> s.copy(messages = s.messages.map { if (it.id == answerId) it.copy(staged = v, sources = shownSources ?: it.sources) else it }) }
                    }
                    StagedAnswer(services::search, { Classify.classifyTask(it).value }, { Classify.isRetrievalIrrelevant(TaskType.of(it)) })
                        .answer(StagedRequest(q, toolPrompt, introReminder, detailsReminder, budget, history, settings.engineTweaks.thinkingEnabled),
                            load = {
                                services.activeSession()?.also {
                                    answeredById = services.host.residentId()
                                    // Auto fits the length to the context the model was opened with, known only now.
                                    limited = limit ?: settings.answerTokens(services.host.openedContext)
                                }
                            },
                            shouldStop = { stopRequested })
                        .collect { e ->
                            when (e) {
                                is StagedEvent.Step -> {
                                    show(view.copy(step = e.step))
                                    _state.update { it.copy(activity = if (e.step == StagedStep.SOURCES) Activity.Retrieving else if (text.isEmpty()) Activity.Thinking else it.activity) }
                                }
                                is StagedEvent.Cards -> {
                                    sources = e.sources
                                    retrievalMs = e.retrievalMs
                                    val shownAt = e.keyFacts.map { it.source }.toSet()
                                    show(view.copy(keyFacts = keyFactViews(e.keyFacts, e.sources),
                                        more = e.sources.filterIndexed { i, _ -> i !in shownAt }.map { factView(q, it) }), groupSources(e.sources))
                                }
                                is StagedEvent.Piece -> {
                                    // The intro and the details kept apart and shown joined, as they're saved: one paragraph
                                    // break between them, not one per piece (one token per line, X6, Nov 9).
                                    if (e.step == StagedStep.DETAILS) stagedDetails.append(e.text) else stagedIntro.append(e.text)
                                    onText { it.setLength(0); it.append(team.sopa.boar.engine.answer.StagedAnswer.join(stagedIntro.toString(), stagedDetails.toString())) }
                                }
                                is StagedEvent.Restart -> { stagedIntro.setLength(0); stagedDetails.setLength(0); restartText() }
                                is StagedEvent.MoreSources -> {
                                    sources = sources + e.added
                                    show(view.copy(added = view.added + e.added.size, more = view.more + e.added.map { factView(q, it) }), groupSources(sources))
                                }
                                is StagedEvent.Checked -> show(view.copy(unsupported = e.unsupported))
                                is StagedEvent.Done -> {
                                    sources = e.sources
                                    metrics = e.metrics
                                    stagedTimes = e.times.toMap()
                                    // The saved text: the intro, then the details, thinking left out (as the TS stores it).
                                    text.setLength(0); text.append(e.text)
                                    show(view.copy(step = null, times = e.times.toMap(), stopped = e.stopped))
                                    if (e.noModel) _state.update { it.copy(noModel = true, error = team.sopa.boar.engine.UserErrors.NO_MODEL) }
                                }
                            }
                        }
                } else if (session == null) {
                    _state.update { it.copy(noModel = true, error = team.sopa.boar.engine.UserErrors.NO_MODEL) }
                } else if (way == team.sopa.boar.engine.routing.AnswerPath.RESEARCH || way == team.sopa.boar.engine.routing.AnswerPath.RECAP) {
                    val research = way == team.sopa.boar.engine.routing.AnswerPath.RESEARCH
                    path = if (research) "deep" else "chat"
                    val r = DeepResearch(SessionEngine(session, settings.engineTweaks.thinkingEnabled), services::search, settings.engineTweaks::stageTimeoutMs, settings.engineTweaks::writeUpTimeoutMs).run(
                        q, systemPrompt, history, budget,
                        onProgress = { p -> _state.update { it.copy(activity = if (research) Activity.Researching(p.stage, p.subQuestionIndex?.plus(1), p.subQuestionCount, p.subQuestion) else Activity.Thinking) } },
                        onToken = ::onPiece,
                        shouldStop = { stopRequested },
                        previousAnswer = lastAnswer?.text?.let(AnswerText::stripThinking),
                        conversation = conversation,
                        // The write-up in the question's language (t9 0420): DeepResearch had the slot, nothing filled it.
                        answerLanguage = team.sopa.boar.engine.answer.AnswerLanguage.line(
                            team.sopa.boar.engine.answer.AnswerLanguage.of(q, prior.filter { it.role == "user" }.map { it.text }), withSources = research),
                        onRestart = ::restartText,
                    )
                    sources = r.citations
                    // Written from the last answer: its numbers [n] are that answer's sources.
                    if (r.fromPreviousAnswer) reusedSources = lastAnswer?.sources
                    timedOut = r.timedOut
                    metrics = r.metrics
                } else if (way == team.sopa.boar.engine.routing.AnswerPath.ADAPTIVE) {
                    // Skipped while a Labs engine answers (its own model, or one the user routed to it): the router only
                    // picks among models on BOAR's own engine.
                    // The router answers within the same budget, so a cut answer gets its note and "Allow more".
                    path = "adaptive"
                    val r = adaptive(q, toolPrompt, styleReminder, history, budget, ::onPiece)
                    sources = r.first
                    timedOut = r.second
                    metrics = r.third
                } else {
                    val plain: (String, Int) -> List<Chunk> = if (personal) { s, k -> services.search(s, k, PersonalFiles.retrieval()) } else services::search
                    // A user's file with results and ranges gets each one marked in code, given to the model with the
                    // passage (t9 2330 b: the 1.7B read 231 against "below 190" as fine).
                    val search: (String, Int) -> List<Chunk> = { s, k -> plain(s, k).map { c ->
                        if (c.collectionId == null) c else team.sopa.boar.engine.answer.LabRanges.note(c.body, ptQ)?.let { n -> c.copy(body = c.body + "\n\n" + n) } ?: c
                    } }
                    val pipeline = AnswerPipeline(session, search, { Classify.classifyTask(it).value }, { Classify.isRetrievalIrrelevant(TaskType.of(it)) })
                    val base = AnswerRequest(q, toolPrompt, styleReminder, budget, history, enableThinking = settings.engineTweaks.thinkingEnabled)
                    // Nothing found in the files: the fixed line in the user's language, then where to switch a collection on
                    // when none is on, or how to ask when one is. The prompt lists the switched-on files by name.
                    val ownFiles = if (personal) services.retriever.ownFiles() else emptyList()
                    val hint = if (ownFiles.isEmpty()) "native.personal.notFoundHint" else "native.personal.notFoundAskHint"
                    val request = if (!personal) base else PersonalFiles.request(base,
                        services.host.openedContext ?: team.sopa.boar.engine.EngineTweaks.contextSizeForRam(totalRamBytes()),
                        team.sopa.boar.ui.I18n.t(getApplication(), "native.personal.notFound") + "\n\n" + team.sopa.boar.ui.I18n.t(getApplication(), hint),
                        team.sopa.boar.engine.rag.OwnFiles.note(ownFiles))
                    pipeline.answer(request).collect { e ->
                        when (e) {
                            is AnswerEvent.Stage -> _state.update { it.copy(activity = when (e.stage) { AnswerStage.RETRIEVING -> Activity.Retrieving; AnswerStage.THINKING -> Activity.Thinking; AnswerStage.GENERATING -> Activity.Generating }) }
                            is AnswerEvent.Sources -> sources = e.chunks
                            is AnswerEvent.Piece -> onPiece(e.text)
                            is AnswerEvent.Done -> { metrics = e.metrics; retrievalMs = e.retrievalMs }
                        }
                    }
                }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message ?: e.toString()) }
            }
            // Ended without a word, no error and not stopped: say so (with Try again) instead of an empty bubble.
            if (text.isBlank() && !stopRequested && _state.value.error == null) _state.update { it.copy(error = team.sopa.boar.engine.UserErrors.EMPTY_ANSWER) }
            // One source per article, best first; the answer's numbers follow (a reused list is already grouped).
            val grouped = groupedSources(sources)
            val shown = reusedSources ?: grouped.items
            if (reusedSources == null) SourceGroups.renumber(text.toString(), grouped.renumber).let { if (it != text.toString()) { text.setLength(0); text.append(it) } }
            if (personal) PersonalFiles.dropHedge(text.toString()).let { if (it != text.toString()) { text.setLength(0); text.append(it) } }
            val stopped = stopRequested
            // The check slot (Boar Labs): a model's finished answer on the default or adaptive path may be repaired; not one
            // that was stopped, timed out or failed part way.
            // The failure line goes with this answer, so it's still there after the next question and when the chat opens again.
            val errorKey = _state.value.error?.takeIf { !stopped }?.let(team.sopa.boar.engine.UserErrors::chatKey)
            val repair = if (skipTools || personal || !team.sopa.boar.engine.routing.LabTools.checksAnswer(instant != null, stopped, timedOut, _state.value.error != null, path, text.isBlank())) null
            else AnswerChecks.run(q, text.toString())
            if (repair != null) { text.setLength(0); text.append(repair.text) }
            // An instant answer generated nothing: its receipt has the time and the tool, no speed.
            val measured = if (instant != null) Receipt(totalMs = ((instantAt.takeIf { it > 0 } ?: System.nanoTime()) - startAt) / 1e6, ttftMs = (firstAt - startAt) / 1e6, tokPerSec = 0.0, tokens = 0, typed = typedAt)
            else receiptOf(startAt, firstAt, tokens, metrics, retrievalMs)
            val receipt = measured?.let { r ->
                val (task, checked) = if (path == "adaptive") (lastAdaptive ?: (null to false)) else (null to false)
                r.copy(style = style, path = path, task = task, checked = checked, stages = stagedTimes,
                    // The model's own answer on "Ask BOAR instead": the plugin's result it read isn't its speaker.
                    tool = listOfNotNull(tool?.tool?.takeIf { tool !== bypass }, repair?.tool).joinToString("+").ifEmpty { null },
                    styleNote = shortReason?.takeIf { it != team.sopa.boar.engine.routing.ShortAnswer.SMALL_TALK })
            }
            val cutAt = limited?.takeIf { !stopped && !timedOut && text.isNotBlank() && AnswerLimit.reached(metrics?.generatedTokens ?: tokens, it) }
            val nextLimit = cutAt?.let { AnswerLimit.next(it, services.personalities.maxTokensOptions, services.host.openedContext) }
            try {
                chats.upsertMessage(sessionId, "assistant", text.toString(), answerId)
                // Always written: it replaces the interrupted mark of the partial saves.
                chats.setMessageMeta(answerId, metaOf(shown, receipt, errorKey, stagedView))
                afterAnswer(sessionId, isNewSession, stopped, prior + UiMessage(userId, "user", q) + UiMessage(answerId, "assistant", text.toString()), systemPrompt, sources.isNotEmpty())
                // Older chats over the limit go, and what the plugins made for their answers goes with them (validator P-1).
                chats.prune(settings.maxSavedSessions) { id ->
                    team.sopa.boar.plugins.UserData.eraseAnswers(chats.messages(id).filter { it.role == "assistant" }.map { it.id })
                }
                // Boar Polyglot's "doesn't speak it yet" reply isn't an answer.
                if (text.isNotBlank() && tool?.tool != team.sopa.boar.engine.routing.LanguageGate.TOOL) services.vault.answered(
                    sourced = shown.isNotEmpty(), deep = deep, portuguese = team.sopa.boar.engine.rag.PtLexicon.looksPortuguese(q),
                    tokPerSec = receipt?.tokPerSec?.takeIf { instant == null }, newChat = isNewSession,
                )
            } catch (e: Exception) {
                // Not saved (disk full): the answer stays on screen with the error under it; the app keeps running.
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(error = e.message ?: e.toString()) }
            }
            if (!stopped && text.isNotEmpty()) team.sopa.boar.app.Haptics.finished(getApplication())
            _state.update { s ->
                s.copy(answeringId = null, answeringModel = null, activity = null, stopping = false,
                    messages = s.messages.map { if (it.id == answerId) it.copy(text = text.toString(), sources = shown, stopped = stopped, timedOut = timedOut, receipt = receipt, cutAt = cutAt, nextLimit = nextLimit, error = errorKey, staged = stagedView,
                        offPlugin = if (instant == null && !stopped) runCatching { AnswerTools.offPlugin(q) }.getOrNull() else null) else it })
            }
        }
        // However the answer ends (done, stopped, failed, or cancelled before it started), the notification goes with it.
        // The next waiting question then starts; Stop ends only the answer in progress, not the queue.
        job?.invokeOnCompletion {
            // From here until the next waiting question has started, a new question joins the queue (validator Q-2).
            handoff = true
            safeScope.launch(Dispatchers.Main) {
                handoff = false
                runNext(fresh = false)
            }
        }
    }

    /** True between an answer's end and the next waiting question's start. */
    @Volatile private var handoff = false

    /**
     * Starts the oldest waiting question, or ends the answer notification when none waits. [fresh]: nothing kept the
     * notification up (a start after the load note), so the question starts it as a typed one does.
     */
    private fun runNext(fresh: Boolean) {
        val next = queue.next()
        if (next == null) { if (!fresh) team.sopa.boar.app.AnswerService.stop(getApplication()); return }
        publishQueue()
        // Defensive: something started meanwhile; the question goes back to the front, not the end.
        if (job?.isActive == true) { queue.pushFront(next); publishQueue(); return }
        send(next.text, null, fromQueue = true, serviceUp = !fresh)
    }

    /** The load note closed, or the model didn't load: the waiting questions need that model too, so they go, said once. */
    private fun dropQueueWithNote() {
        if (queue.isEmpty()) return
        val n = queue.size
        queue.clear(); publishQueue()
        team.sopa.boar.ui.Toasts.show(team.sopa.boar.ui.I18n.t(getApplication(), "native.chat.queueDroppedNote", "count" to n))
    }

    private val queue = team.sopa.boar.engine.chat.PromptQueue()

    private fun queuePrefs() = getApplication<Application>().getSharedPreferences("chat_queue", android.content.Context.MODE_PRIVATE)

    /** The queue on screen, and its size kept so a closed app can say what it dropped. */
    private fun publishQueue() {
        _state.update { it.copy(queued = queue.waiting.map { q -> QueuedPrompt(q.id, q.text) }) }
        queuePrefs().edit().putInt(QUEUE_WAITING, queue.size).apply()
    }

    fun removeQueued(id: Long) { queue.remove(id); publishQueue() }

    fun clearQueue() { queue.clear(); publishQueue() }

    /** The waiting questions become one, answered together (adam, Nov 12). */
    /** A safety question (the CO card) stays its own turn, ahead of the merged one, whether Cohazard is on or not. */
    fun mergeQueue() { if (queue.merge { team.sopa.boar.engine.routing.LabTools.coHazard(it) != null }) publishQueue() }

    /**
     * Stops a title or summary still being written, as ChatScreen.tsx does (cancelBackgroundTask), instead of making the
     * question wait for it: a summary reads every older turn first, which on a slow phone held the next question for tens
     * of seconds. Cancelling the job keeps one that hasn't started from starting, and makes one that has throw instead of
     * returning, so nothing half-written is saved; the native cancel stops the prompt reading or writing in progress. A
     * stopped title keeps the question as the chat's title; a stopped summary is written after a later answer.
     */
    private suspend fun stopBackground() {
        val running = background?.takeIf { it.isActive } ?: return
        running.cancel()
        services.host.current()?.cancel()
        running.join()
    }

    /**
     * As ChatScreen.tsx after an answer: a new chat gets a model-written title (autoGenerateTitles); a long one gets its
     * older turns summarized (autoSummarize, past historyTurnThreshold), in the background.
     */
    private fun afterAnswer(sessionId: String, isNewSession: Boolean, stopped: Boolean, all: List<UiMessage>, systemPrompt: String?, hadSources: Boolean) {
        if (stopped) return
        // In the background the model is freed now; the question stays the title until BOAR is back on screen.
        if (!services.isInForeground) {
            deferredAfterAnswer = { afterAnswer(sessionId, isNewSession, false, all, systemPrompt, hadSources) }
            return
        }
        val s = services.settings
        val session = services.host.current() ?: return
        // Reads the model's info: throws once the model was unloaded (switched in Models or Settings) after the answer.
        val gen = runCatching { SessionEngine(session) }.getOrNull() ?: return
        if (isNewSession && s.autoGenerateTitles) {
            background = safeScope.launch(Dispatchers.Default) {
                runCatching { services.chats.setTitle(sessionId, Summarize.title(gen, all.first { it.role == "user" }.text)) }
                runCatching { warmPrefix(session, systemPrompt, hadSources) }
            }
        } else if (s.autoSummarize && all.size / 2 > s.historyTurnThreshold) {
            val older = all.dropLast(VERBATIM_MESSAGE_COUNT)
            if (older.isEmpty()) return
            val previous = services.chats.listSessions().firstOrNull { it.id == sessionId }?.summary
            background = safeScope.launch(Dispatchers.Default) {
                runCatching {
                    // Bounded like the answer's history: a whole detailed chat (thousands of tokens) took tens of seconds to read.
                    val turns = HistoryBudget.budget(older.map { ConversationTurn(it.role, if (it.role == "assistant") AnswerText.stripThinking(it.text) else it.text) })
                    services.chats.setSummary(sessionId, Summarize.summary(gen, turns, previous))
                }
                runCatching { warmPrefix(session, systemPrompt, hadSources) }
            }
        }
    }

    /**
     * Back on screen: the title or summary skipped in the background, once the model is loaded again (as the chat's
     * preload, only when it may load on its own). A question asked meanwhile stops it, and the question stays the title.
     */
    private fun resumeAfterAnswer() {
        val pending = deferredAfterAnswer ?: return
        deferredAfterAnswer = null
        if (job?.isActive == true || services.startDecision() !is StartLoad.Decision.Load) return
        background = safeScope.launch(Dispatchers.Default) {
            if (runCatching { services.activeSession() }.getOrNull() != null) pending()
        }
    }

    /**
     * A title or summary left its own prompt in the KV cache, so the next question would read the whole system prompt
     * again (about 230 tokens, 8 s on a 4B). Prefill it back: the next answer reuses it (#72's warm-up).
     */
    private suspend fun warmPrefix(model: team.sopa.boar.engine.TextModel, systemPrompt: String?, withSources: Boolean) {
        // Only BOAR's own engine keeps a prompt cache to refill; a Labs engine process manages its own.
        val session = model as? team.sopa.boar.engine.LlamaSession ?: return
        // The variant the last answer used (with or without sources): a follow-up in the same chat most often matches it.
        // Personal Files answers from the files-only prompt, with its sources.
        val filesOnly = PersonalFiles.isOn(services.settings.personalityId)
        val (head, plain) = team.sopa.boar.engine.rag.RagPure.answerPromptPrefix(systemPrompt, withSources || filesOnly, filesOnly)
        val prompt = if (session.info.optBoolean("hasChatTemplate", false))
            session.formatChat(listOf(team.sopa.boar.engine.ChatMessage("system", head)), services.settings.engineTweaks.thinkingEnabled).prompt
        else plain
        session.generate(prompt, team.sopa.boar.engine.Sampling(nPredict = 0)).collect {}
    }

    /** runAdaptiveChat: plan with the installed models and the user's routing settings, then execute. */
    /** The last adaptive plan's task type and whether it had a verify step, for the receipt. */
    @Volatile private var lastAdaptive: Pair<String, Boolean>? = null
    /** The model the adaptive plan's generate step runs, for the header and the receipt; null on the other paths. */
    @Volatile private var routedModelId: String? = null
    /** The model loaded for this answer: the receipt names it even if another is picked meanwhile (X6, Oct 24). */
    @Volatile private var answeredById: String? = null

    private suspend fun adaptive(q: String, systemPrompt: String, styleReminder: String?, history: ConversationHistory, budget: Int, onPiece: (String) -> Unit): Triple<List<Chunk>, Boolean, GenerationMetrics?> {
        lastAdaptive = null  // a plan that fails mustn't leave the previous answer's task on this receipt
        val s = services.settings
        val statuses = services.catalog.models.filter { it.kind == AssetKind.LLM }.map { Profiles.AvailableModel(it, services.runsOnBuiltIn(it) && services.fileOf(it).isFile) }
        val preset = s.routingPreset?.let { runCatching { RoutingPreset.of(it) }.getOrNull() } ?: RoutingPreset.DEFAULT
        val overrides = s.modelRoleAssignments().mapNotNull { (k, v) -> runCatching { ModelRole.of(k) to v }.getOrNull() }.toMap()
        val plan = AdaptivePlan.plan(q, statuses, preset, overrides, services.answerModel()?.id ?: s.activeModelId("llm"), totalRamBytes(), budget)
        lastAdaptive = plan.taskType.value to plan.plan.steps.any { it.type == team.sopa.boar.engine.routing.RoutingStepType.VERIFY }
        val r = PlanExecutor(services.host, services::search, { Classify.classifyTask(it).value }, timeoutFor = s.engineTweaks::stageTimeoutMs)
            .execute(plan.plan, PipelineInput(q, systemPrompt, styleReminder, history), onToken = onPiece, onStepStart = { step ->
                if (step.type == team.sopa.boar.engine.routing.RoutingStepType.GENERATE && step.modelId != null) {
                    routedModelId = step.modelId
                    val other = step.modelId.takeIf { it != services.answerModel()?.id }?.let(services.catalog::byId)?.let { shortLabel(it.label) }
                    _state.update { it.copy(answeringModel = other) }
                }
                _state.update { it.copy(activity = if (step.type.value == "retrieve") Activity.Retrieving else Activity.Thinking) }
            }, shouldStop = { stopRequested })
        if (r.answer.isBlank() && !stopRequested) error("adaptive routing produced no answer: ${r.warnings.joinToString()}")
        return Triple(r.citations, r.timedOut, r.metrics)
    }

    /** The phone's RAM (ram-monitor's getDeviceTotalRamBytes); 0 when unreadable. */
    private fun totalRamBytes(): Long = runCatching {
        val am = getApplication<Application>().getSystemService(android.app.ActivityManager::class.java)
        android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
    }.getOrDefault(0L)

    /**
     * The answer's receipt, from the engine's own numbers (for Deep Research and adaptive routing, those of the step that
     * wrote the answer), else from the tokens as they streamed. None when nothing was generated.
     */
    private fun receiptOf(startAt: Long, firstAt: Long, streamed: Int, m: GenerationMetrics?, retrievalMs: Double?): Receipt? {
        if (firstAt == 0L || streamed == 0) return null
        val end = System.nanoTime()
        val wallRate = streamed / ((end - firstAt) / 1e9).coerceAtLeast(1e-3)
        return Receipt(
            totalMs = (end - startAt) / 1e6, ttftMs = (firstAt - startAt) / 1e6,
            tokPerSec = m?.decodeTokPerSec?.takeIf { it > 0 } ?: wallRate, tokens = m?.generatedTokens?.takeIf { it > 0 } ?: streamed,
            // The model that wrote it: the one adaptive routing picked, else the active one.
            model = ((routedModelId ?: answeredById)?.let(services.catalog::byId) ?: services.activeModel())?.let(::receiptModel), retrievalMs = retrievalMs, prefillMs = m?.prefillMs, contextTokens = m?.promptTokens,
        )
    }

    /** The receipt's model line: its name, plus the Labs engine that ran it when the name doesn't say ("· Big Boar"). */
    private fun receiptModel(a: team.sopa.boar.engine.models.CatalogAsset): String {
        val engine = services.engineLabelOf(a)?.takeIf { it !in a.shortName } ?: return a.shortName
        return "${a.shortName} · $engine"
    }

    private fun receiptFromMeta(meta: String?): Receipt? = runCatching {
        Json.parseToJsonElement(meta ?: return null).jsonObject["receipt"]?.jsonObject?.let(Receipt::fromJson)
    }.getOrNull()

    /** A Keep going step on its user message (the text stays the real question). */
    private fun stepMeta(step: String) = JsonObject(mapOf("step" to JsonPrimitive(step))).toString()
    private fun stepFromMeta(meta: String?): String? = runCatching { Json.parseToJsonElement(meta ?: return null).jsonObject["step"]?.jsonPrimitive?.content }.getOrNull()

    private fun metaOf(sources: List<Source>, receipt: Receipt?, errorKey: String? = null, staged: StagedView? = null): String = JsonObject(listOfNotNull("sources" to JsonArray(sources.map { s ->
        JsonObject(listOfNotNull(
            "title" to JsonPrimitive(s.title), s.source?.let { "source" to JsonPrimitive(it) }, s.relevance?.let { "relevance" to JsonPrimitive(it) },
            s.similarity?.let { "similarity" to JsonPrimitive(it) }, s.docId?.let { "docId" to JsonPrimitive(it) }, "body" to JsonPrimitive(s.body),
        ).toMap())
    }), receipt?.let { "receipt" to it.toJson() }, errorKey?.let { "error" to JsonPrimitive(it) }, staged?.let { "staged" to stagedJson(it) }).toMap()).toString()

    /** A staged answer's key facts and check, kept with it (the times are in the receipt). */
    private fun factsJson(l: List<KeyFactView>) = JsonArray(l.map { k -> JsonObject(mapOf("sentence" to JsonPrimitive(k.sentence), "passage" to JsonPrimitive(k.passage),
        "title" to JsonPrimitive(k.source.title), "docId" to JsonPrimitive(k.source.docId ?: ""), "source" to JsonPrimitive(k.source.source ?: ""))) })

    // "+N sources" and the health passages are kept too, so a reopened chat shows the same card (validator S-1).
    private fun stagedJson(v: StagedView) = JsonObject(listOfNotNull(
        "keyFacts" to factsJson(v.keyFacts),
        if (v.more.isNotEmpty()) "more" to factsJson(v.more) else null,
        if (v.withPassages) "withPassages" to JsonPrimitive(true) else null,
        "added" to JsonPrimitive(v.added), v.unsupported?.let { u -> "unsupported" to JsonArray(u.map(::JsonPrimitive)) },
        if (v.stopped) "stopped" to JsonPrimitive(true) else null,
    ).toMap())

    private fun stagedFromMeta(meta: String?, receipt: Receipt?): StagedView? = runCatching {
        val o = Json.parseToJsonElement(meta ?: return null).jsonObject["staged"]?.jsonObject ?: return null
        fun facts(key: String) = o[key]?.jsonArray.orEmpty().map { e ->
            val k = e.jsonObject
            fun s(key: String) = k[key]?.jsonPrimitive?.content.orEmpty()
            KeyFactView(s("sentence"), s("passage"), Source(s("title"), s("source").ifEmpty { null }, null, null, s("docId").ifEmpty { null }, s("passage")))
        }
        StagedView(
            keyFacts = facts("keyFacts"),
            more = facts("more"),
            withPassages = o["withPassages"]?.jsonPrimitive?.content == "true",
            added = o["added"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            unsupported = o["unsupported"]?.jsonArray?.map { it.jsonPrimitive.content },
            times = receipt?.stages.orEmpty(),
            stopped = o["stopped"]?.jsonPrimitive?.content == "true",
        )
    }.getOrNull()

    private fun groupedSources(chunks: List<Chunk>) =
        SourceGroups.group(chunks.map { Source(it.title, it.source, it.relevance, it.similarity, it.docId, it.body) },
            { SourceGroups.articleKey(it.source, it.docId, it.title) }, { it.similarity ?: it.relevance }) { g ->
            g.first().copy(similarity = g.mapNotNull { it.similarity }.maxOrNull(), relevance = g.mapNotNull { it.relevance }.maxOrNull(),
                body = g.joinToString("\n…\n") { it.body })
        }

    /** The sources as the chat lists them while a staged answer is written: one per article, best first. */
    private fun groupSources(chunks: List<Chunk>): List<Source> = groupedSources(chunks).items

    /** A source as a line of "+N sources": its best sentence for [q] (or its first), its passage and the source to read. */
    private fun factView(q: String, c: Chunk) = KeyFactView(team.sopa.boar.engine.answer.StagedAnswer.factOf(q, c), c.body,
        Source(c.title, c.source, c.relevance, c.similarity, c.docId, c.body))

    private fun keyFactViews(facts: List<team.sopa.boar.engine.answer.KeyFact>, chunks: List<Chunk>) = facts.mapNotNull { f ->
        chunks.getOrNull(f.source)?.let { c -> KeyFactView(f.sentence, c.body, Source(c.title, c.source, c.relevance, c.similarity, c.docId, c.body)) }
    }

    /** The intro's style line (Succinct's) and the details' (the style picked; none when the answer is kept short). */
    private fun stagedReminders(style: String, smallModel: Boolean): Pair<String?, String?> {
        val succinct = services.personalities.promptFor("succinct", null, smallModel).second
        val picked = services.personalities.promptFor(style, services.settings.customSystemPrompt, smallModel).second
        return succinct to picked.takeIf { style != "succinct" }
    }

    private fun errorFromMeta(meta: String?): String? = runCatching {
        Json.parseToJsonElement(meta ?: return null).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun sourcesFromMeta(meta: String?): List<Source> = runCatching {
        Json.parseToJsonElement(meta ?: return emptyList()).jsonObject["sources"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            Source(
                o["title"]!!.jsonPrimitive.content, o["source"]?.jsonPrimitive?.content, o["relevance"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                o["similarity"]?.jsonPrimitive?.content?.toDoubleOrNull(), o["docId"]?.jsonPrimitive?.content, o["body"]?.jsonPrimitive?.content ?: "",
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        /** Messages sent verbatim as history (ChatScreen.tsx VERBATIM_MESSAGE_COUNT). */
        const val VERBATIM_MESSAGE_COUNT = 6
        /** How often an answer being written is saved (savePartial). */
        const val PARTIAL_SAVE_NS = 3_000_000_000L
        /** Close BOAR waits this long for a stopped answer to save what it wrote. */
        const val CLOSE_WAIT_MS = 15_000L
        /** More time for a model still loading when Close BOAR came (nothing written yet). */
        const val CLOSE_LOAD_WAIT_MS = 60_000L

        /**
         * A message about the conversation itself, answered from it without a search: "summarize our conversation", or
         * "TL;DR" / "a summary please" after an answer, as Deep Research already did. "Summarize our conversation" covered
         * only the last three exchanges (X6, Oct 16). "continue" keeps the search: it asks for more than the chat holds.
         */
        fun aboutConversation(query: String, history: ConversationHistory, conversation: List<DeepResearch.Companion.Exchange>): Boolean =
            (DeepResearch.isConversationRecap(query) && conversation.any { !DeepResearch.isConversationRecap(it.question) && !DeepResearch.isSummaryFollowUp(it.question) }) ||
                (DeepResearch.isSummaryFollowUp(query) && DeepResearch.lastAnswered(query, history) != null)

        /** Prompt ideas shown in an empty chat, drawn again for each new chat. */
        const val IDEAS_SHOWN = 3
        /** How many questions were waiting (PromptQueue), so a closed app can say it dropped them. */
        private const val QUEUE_WAITING = "waiting"

        /**
         * Short questions (two lines at most) from the evaluations: b1…b9 from BOAR's 17-question set (eval-set.json),
         * b10…b21 from the public BOAR Bench v0.1 items, without its trap, emergency, crypto and restaurant questions.
         * Their text is chat.suggestions.b* in the app's languages.
         */
        val BENCH_IDEAS = (1..21).map { "b$it" }
    }
}
