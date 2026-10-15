package team.sopa.boar

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import team.sopa.boar.engine.settings.AppSettings
import team.sopa.boar.ui.settings.DisplayPrefs
import team.sopa.boar.ui.settings.SettingsScreen
import team.sopa.boar.setup.SetupViewModel
import team.sopa.boar.models.ModelsViewModel
import team.sopa.boar.documents.DocumentsViewModel
import team.sopa.boar.ui.documents.DocumentsScreen
import team.sopa.boar.ui.about.AboutScreen
import team.sopa.boar.ui.drawer.BoarDrawer
import team.sopa.boar.ui.drawer.DrawerItem
import team.sopa.boar.ui.eval.EvaluationScreen
import team.sopa.boar.eval.EvaluationViewModel
import team.sopa.boar.ui.models.BrowseScreen
import team.sopa.boar.ui.models.KnowledgePacks
import team.sopa.boar.ui.models.ModelsScreen
import team.sopa.boar.ui.setup.SetupScreen
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.io.File
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import team.sopa.boar.app.BoarServices
import team.sopa.boar.chat.ChatViewModel
import team.sopa.boar.engine.chat.ChatSession
import team.sopa.boar.ui.chat.ChatScreen
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import team.sopa.boar.ui.theme.BoarTheme

/** The app: first-run setup until the required models are on disk, then the chat with past chats and settings in the drawer. */
class MainActivity : ComponentActivity() {
    /** The app language from settings ("en", "pt"), else the phone's. */
    override fun attachBaseContext(base: Context) {
        val lang = AppSettings(File(base.filesDir, "settings.json")).languageId
        if (lang == null) return super.attachBaseContext(base)
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    /** With a PIN: locked at start (the chats' key isn't in memory yet) and after a minute in the background. */
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onStop() {
        super.onStop()
        stoppedAt = android.os.SystemClock.elapsedRealtime() // not the wall clock: changing the time can't skip the lock
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && BoarServices.get(this).pinLock.isSet && android.os.SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MS) locked.value = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val services = BoarServices.get(this)
        // A new process has no chat key yet; a recreated activity (rotation, font size) in the same process keeps it.
        if (services.pinLock.isSet && services.chats.cipher == team.sopa.boar.engine.security.PlainCipher) locked.value = true
        setContent {
            var theme by remember { mutableStateOf(services.settings.themeId) }
            var fontScale by remember { mutableStateOf(services.settings.fontScale) }
            val prefs = object : DisplayPrefs {
                override fun setTheme(id: String) { theme = id }
                override fun setFontScale(scale: String) { fontScale = scale }
                override fun setLanguage(id: String?) { recreate() }
            }
            BoarTheme(theme, fontScale) {
                if (locked.value) team.sopa.boar.ui.security.LockScreen(services.pinLock) { locked.value = false }
                else App(services, prefs)
            }
        }
    }

    private companion object { const val RELOCK_AFTER_MS = 60_000L }
}

/** Where setup leads: the PIN offer, unless a PIN is already set (setup run again from Settings → Recovery). */
internal fun afterSetup(pinSet: Boolean) = if (pinSet) "chat" else "pinOffer"

@Composable
private fun App(services: BoarServices, prefs: DisplayPrefs, vm: ChatViewModel = viewModel(), setup: SetupViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var screen by rememberSaveable { mutableStateOf(if (setup.ready() && !setup.unfinished) "chat" else "setup") }
    if (screen == "setup") {
        val setupState by setup.state.collectAsState()
        // Downloads pause in the background on MIUI: the screen stays on while setup runs.
        val view = LocalView.current
        DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
        // Reopened from Settings (the app already works): step 1 offers the way back to the chat.
        val wasReady = remember { setup.ready() }
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_START) setup.onForeground() }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        // "Discover GGUF models" from the model step: the Hugging Face page on top of setup; a model added there is
        // listed in setup and chosen, and setup comes back.
        var browsing by rememberSaveable { mutableStateOf(false) }
        val setupModels: ModelsViewModel = viewModel()
        if (browsing) {
            val modelsState by setupModels.state.collectAsState()
            LaunchedEffect(modelsState.hfAddedId) { modelsState.hfAddedId?.let { id -> setup.pickDiscovered(id); browsing = false } }
            BackHandler { browsing = false }
            BrowseScreen(modelsState, setupModels) { browsing = false }
            return
        }
        SetupScreen(setupState, setup, team.sopa.boar.ui.setup.currentLanguage(services.settings.languageId),
            onLanguage = { id -> services.settings.languageId = id; prefs.setLanguage(id) },
            onSkip = if (wasReady) ({ setup.forget(); screen = "chat" }) else null,
            // Cleared first: a model added earlier from Models must not be picked the moment the page opens.
            onBrowse = { setupModels.clearAdded(); browsing = true },
        ) { setup.forget(); vm.refreshModel(); screen = afterSetup(services.pinLock.isSet) }
        return
    }
    if (screen == "pinOffer") {
        team.sopa.boar.ui.security.PinOffer(onSet = { screen = "pin:NEW:chat" }, onSkip = { screen = "chat" })
        return
    }
    if (screen.startsWith("pin:")) {
        // pin:<flow>:<screen to return to>
        val (_, flow, back) = screen.split(":")
        BackHandler { screen = back }
        team.sopa.boar.ui.security.PinSetup(services.pinLock, team.sopa.boar.ui.security.PinFlow.valueOf(flow),
            onDone = { vm.refreshSettings(); screen = back }, onCancel = { screen = back })
        return
    }
    if (screen == "models") {
        val models: ModelsViewModel = viewModel()
        val modelsState by models.state.collectAsState()
        BackHandler { vm.refreshModel(); screen = "chat" }
        ModelsScreen(modelsState, models, onBrowse = { screen = "browse" }) { vm.refreshModel(); screen = "chat" }
        return
    }
    if (screen == "browse") {
        val models: ModelsViewModel = viewModel()
        val modelsState by models.state.collectAsState()
        BackHandler { screen = "models" }
        BrowseScreen(modelsState, models) { screen = "models" }
        return
    }
    if (screen == "documents") {
        val docs: DocumentsViewModel = viewModel()
        val docsState by docs.state.collectAsState()
        val models: ModelsViewModel = viewModel()
        val modelsState by models.state.collectAsState()
        BackHandler { screen = "chat" }
        DocumentsScreen(docsState, docs, packs = { KnowledgePacks(modelsState, models) }) { screen = "chat" }
        return
    }
    if (screen == "evaluation") {
        val eval: EvaluationViewModel = viewModel()
        val evalState by eval.state.collectAsState()
        // A run takes many minutes: the screen stays on, and back does nothing until it ends (as Done hides).
        val view = LocalView.current
        DisposableEffect(evalState.running) { view.keepScreenOn = evalState.running; onDispose { view.keepScreenOn = false } }
        BackHandler { if (!evalState.running) { vm.refreshModel(); screen = "chat" } }
        EvaluationScreen(evalState, eval, chatBusy = state.answeringId != null) { vm.refreshModel(); screen = "chat" }
        return
    }
    if (screen == "about") {
        BackHandler { screen = "chat" }
        AboutScreen { screen = "chat" }
        return
    }
    if (screen == "settings") {
        BackHandler { vm.refreshSettings(); screen = "chat" }
        SettingsScreen(services, prefs, onPin = { flow -> screen = "pin:$flow:settings" }, onSetup = { setup.restart(); screen = "setup" }, onOpen = { screen = it }) { vm.refreshSettings(); screen = "chat" }
        return
    }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    LaunchedEffect(drawer.isOpen, state.answeringId) { if (drawer.isOpen) sessions = services.chats.listSessions() }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Boar.colors.bgCardElevated, modifier = Modifier.width(310.dp)) {
                fun go(target: () -> Unit) { target(); scope.launch { drawer.close() } }
                BoarDrawer(
                    sessions = sessions,
                    activeSessionId = state.sessionId,
                    // ChatScreen.tsx's items; Evaluation stands in for Execution Telemetry (not in the native app yet),
                    // and the model catalog has its own entry (the Expo app reaches it through Settings).
                    items = listOf(
                        DrawerItem("knowledge", t("nav.knowledge")) { go { screen = "documents" } },
                        DrawerItem("models", t("modelCatalogScreen.title")) { go { screen = "models" } },
                        DrawerItem("settings", t("chatScreen.drawerItems.settings")) { go { screen = "settings" } },
                        DrawerItem("evaluation", t("evaluation.title")) { go { screen = "evaluation" } },
                        DrawerItem("about", t("chatScreen.drawerItems.about")) { go { screen = "about" } },
                    ),
                    lastTokPerSec = state.liveTokPerSec.takeIf { state.answeringId == null },
                    storageUsed = { services.storageUsedBytes() },
                    ramBudgetBytes = services.catalog.ramBudgetBytes,
                    storageBudgetBytes = services.catalog.storageBudgetBytes,
                    showStats = state.technicalDetails,
                    onNewChat = { go { vm.newChat() } },
                    onSelect = { id -> go { vm.openSession(id) } },
                    onDelete = { id -> vm.deleteSession(id); sessions = sessions.filter { it.id != id } },
                )
            }
        },
    ) {
        ChatScreen(
            state,
            onSend = vm::send,
            onStop = vm::stop,
            onNewChat = vm::newChat,
            onMenu = { scope.launch { drawer.open() } },
            onSetTone = vm::setTone,
            onSetDeepResearch = vm::setDeepResearch,
            onHideResearchBanner = vm::hideResearchBanner,
            onRate = vm::rate,
            onDismissCrash = vm::dismissLoadCrash,
            onReused = { vm.showToast("chatScreen.messageReused") },
            onToastHidden = vm::hideToast,
            voiceEnabled = services.settings.voiceInputEnabled,
        )
    }
}
