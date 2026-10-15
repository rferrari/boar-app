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

    /**
     * With a PIN, the screen is secure: blank in recent apps, screenshots and screen recordings, so chats can't be seen
     * there without the PIN. Without one, screenshots work as usual. Checked on every resume and pause, so a PIN set or
     * removed in Settings applies before the recent-apps picture is taken.
     */
    private fun applySecure() {
        val flag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        if (BoarServices.get(this).pinLock.isSet) window.addFlags(flag) else window.clearFlags(flag)
    }

    override fun onResume() {
        super.onResume()
        applySecure()
    }

    override fun onPause() {
        applySecure()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        team.sopa.boar.app.DebugLog.foreground(false)
        // A rotation or font change stops and starts the activity: that isn't leaving BOAR.
        if (!isChangingConfigurations) BoarServices.get(this).onBackground()
        stoppedAt = android.os.SystemClock.elapsedRealtime() // not the wall clock: changing the time can't skip the lock
        // The equipped boar goes on the launcher now that BOAR is off screen (switching it while open closes the app).
        if (!isChangingConfigurations) BoarServices.get(this).vault.applyIcon()
    }

    override fun onStart() {
        super.onStart()
        team.sopa.boar.app.DebugLog.foreground(true)
        BoarServices.get(this).onForeground()
        if (stoppedAt > 0 && BoarServices.get(this).pinLock.isSet && android.os.SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MS) locked.value = true
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        team.sopa.boar.app.DebugLog.trimMemory(level)
        BoarServices.get(this).onTrimMemory(level)
    }

    /** Settings → Screen rotation: Auto follows the phone's own rotation lock; Portrait and Landscape hold it. */
    private fun applyOrientation(mode: String) {
        requestedOrientation = when (mode) {
            "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val services = BoarServices.get(this)
        // A PIN set or removed in Settings protects the screen now, not only after BOAR next pauses.
        services.pinLock.onChange = { runOnUiThread { applySecure() } }
        team.sopa.boar.labs.LabHooks.init(this)
        // A new process has no chat key yet; a recreated activity (rotation, font size) in the same process keeps it.
        if (services.pinLock.isSet && services.chats.cipher == team.sopa.boar.engine.security.PlainCipher) locked.value = true
        kotlin.concurrent.thread(name = "vault") { services.checkVault() }
        applyOrientation(services.settings.screenOrientation)
        setContent {
            val equipped by services.vault.icon.collectAsState()
            val avatarsOn by services.vault.enabled.collectAsState()
            val truffle = equipped.takeIf { avatarsOn }
            var theme by remember { mutableStateOf(services.settings.themeId) }
            var fontScale by remember { mutableStateOf(services.settings.fontScale) }
            val prefs = object : DisplayPrefs {
                override fun setTheme(id: String) { theme = id }
                override fun setFontScale(scale: String) { fontScale = scale }
                override fun setOrientation(mode: String) { applyOrientation(mode) }
                override fun setLanguage(id: String?) { recreate() }
            }
            androidx.compose.runtime.CompositionLocalProvider(team.sopa.boar.appearance.LocalTruffle provides truffle) {
                // A theme unlocked by Boar Avatars applies only while that experiment is on.
                BoarTheme(theme.takeIf { avatarsOn || it == null || it in team.sopa.boar.ui.theme.Themes.open }, fontScale) {
                    if (locked.value) team.sopa.boar.ui.security.LockScreen(services.pinLock) { locked.value = false }
                    else androidx.compose.foundation.layout.Box {
                        App(services, prefs)
                        team.sopa.boar.ui.appearance.FreshTruffle(services.vault)
                        // The one toast, over every screen (Toasts).
                        team.sopa.boar.ui.ToastHost(Modifier.align(androidx.compose.ui.Alignment.TopCenter))
                    }
                }
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
    // The offline APK: its models are copied out once, before anything else; no network involved.
    val context = androidx.compose.ui.platform.LocalContext.current
    val bundled = remember { team.sopa.boar.app.BundledAssets(services, context.applicationContext) }
    var unpacking by rememberSaveable { mutableStateOf(bundled.pending().isNotEmpty()) }
    if (unpacking) {
        team.sopa.boar.ui.setup.UnpackScreen(bundled) {
            setup.restart()
            screen = if (setup.ready() && !setup.unfinished) "chat" else "setup"
            unpacking = false
        }
        return
    }
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
            LaunchedEffect(modelsState.hfAddedId) { modelsState.hfAddedId?.let { id -> setupModels.toastShown(); setup.pickDiscovered(id); browsing = false } }
            BackHandler { browsing = false }
            BrowseScreen(modelsState, setupModels, rowActions = false) { browsing = false }
            return
        }
        SetupScreen(setupState, setup, team.sopa.boar.ui.setup.currentLanguage(services.settings.languageId),
            onLanguage = { id -> services.settings.languageId = id; prefs.setLanguage(id) },
            // Settings too: after "Reset settings" the chat must show the default style and Deep Research off.
            onSkip = if (wasReady) ({ setup.forget(); vm.refreshSettings(); screen = "chat" }) else null,
            // Cleared first: a model added earlier from Models must not be picked the moment the page opens.
            onBrowse = { setupModels.clearAdded(); browsing = true },
            // The tour's theme card: the same write as Settings → Display & Theme.
            themeId = services.settings.themeId,
            onTheme = { id -> services.settings.themeId = id; prefs.setTheme(id); services.vault.record { f -> f.copy(themeChanged = true) } },
        ) { setup.forget(); vm.refreshSettings(); vm.refreshModel(); screen = afterSetup(services.pinLock.isSet) }
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
            onDone = { vm.refreshSettings(); services.checkVault(); screen = back }, onCancel = { screen = back })
        return
    }
    if (screen == "models") {
        val models: ModelsViewModel = viewModel()
        val modelsState by models.state.collectAsState()
        BackHandler { vm.refreshModel(); services.checkVault(); screen = "chat" }
        ModelsScreen(modelsState, models, onBrowse = { screen = "browse" }) { vm.refreshModel(); services.checkVault(); screen = "chat" }
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
        DocumentsScreen(docsState, docs, packs = { KnowledgePacks(modelsState, models) },
            // "Ask about them": a new chat in Personal Files, the style that answers from the user's files.
            onAskFiles = { vm.setTone("personal"); vm.newChat(); screen = "chat" }) { screen = "chat" }
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
    if (screen == "vault") {
        BackHandler { screen = "settings" }
        team.sopa.boar.ui.appearance.VaultScreen(services.vault) { screen = "settings" }
        return
    }
    if (team.sopa.boar.labs.LabHooks.handles(screen)) {
        // Back in the chat from Labs: a lab may have changed the answer model (Big Boar's "Answer with this model").
        BackHandler { if (screen == "labs") { vm.refreshSettings(); screen = "chat" } else screen = "labs" }
        team.sopa.boar.labs.LabHooks.Screen(screen) { if (it == "chat") vm.refreshSettings(); screen = it }
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
    LaunchedEffect(drawer.isOpen, state.answeringId) {
        if (drawer.isOpen) sessions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Old "<think>" titles (named by a reasoning model before Oct 15) read as the chat's first question.
            services.chats.repairThinkingTitles()
            services.chats.listSessions()
        }
    }
    // Menu → Close BOAR: the answer stops (what it wrote is saved), the model is freed, the app closes. Chats are saved.
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    var confirmClose by remember { mutableStateOf(false) }
    // The chat the menu's 🗑️ is about to delete, while Settings asks to confirm first.
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }
    // Boar Labs: green rain with each closing step for a moment, so closing reads as BOAR putting things away.
    var closeStage by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun closeBoar() {
        if (closing) return
        closing = true
        val rain = team.sopa.boar.labs.LabHooks.closeAnimation(ctx)
        scope.launch {
            suspend fun stage(s: String) { closeStage = s; if (rain) kotlinx.coroutines.delay(STAGE_MS) }
            if (runCatching { vm.closeAndFreeMemory(::stage) }.getOrDefault(true)) { stage("closing"); activity?.finishAndRemoveTask() }
            else { closing = false; closeStage = null }
        }
    }
    if (closing && closeStage != null && team.sopa.boar.labs.LabHooks.closeAnimation(ctx)) team.sopa.boar.labs.LabHooks.ClosingOverlay(closeStage!!)
    if (confirmClose) androidx.compose.material3.AlertDialog({ confirmClose = false },
        text = { Text(t("native.drawer.closeConfirm")) },
        confirmButton = { androidx.compose.material3.TextButton({ confirmClose = false; closeBoar() }) { Text(t("native.drawer.closeButton"), color = Boar.colors.crimson400) } },
        dismissButton = { androidx.compose.material3.TextButton({ confirmClose = false }) { Text(t("common.cancel")) } })
    confirmDelete?.let { id ->
        val c = Boar.colors
        androidx.compose.material3.AlertDialog({ confirmDelete = null },
            title = { Text(t("native.drawer.deleteConfirm")) },
            text = { Text(t("native.drawer.deleteConfirmMessage", "title" to (sessions.firstOrNull { it.id == id }?.title ?: ""))) },
            containerColor = c.bgCard, titleContentColor = c.textHeading, textContentColor = c.textSecondary,
            confirmButton = { androidx.compose.material3.TextButton({ vm.deleteSession(id); sessions = sessions.filter { it.id != id }; confirmDelete = null }) { Text(t("native.drawer.deleteButton"), color = Boar.colors.crimson400) } },
            dismissButton = { androidx.compose.material3.TextButton({ confirmDelete = null }) { Text(t("common.cancel")) } })
    }
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
                        DrawerItem("close", t("native.drawer.close")) { go { if (state.answeringId != null) confirmClose = true else closeBoar() } },
                    ).let { items ->
                        // Evaluation is for builders (SHOW_EVAL_IN_MENU); otherwise it's in Settings → Advanced tweaks.
                        if (!team.sopa.boar.BuildConfig.SHOW_EVAL_IN_MENU) items
                        else items.take(2) + DrawerItem("evaluation", t("evaluation.title")) { go { screen = "evaluation" } } + items.drop(2)
                    }.let { items ->
                        // Boar Labs, then Settings, then Close BOAR last (Oct 18); About opens from the BOAR mark at the top.
                        val labs = team.sopa.boar.labs.LabHooks.drawerItems().map { (route, label) -> DrawerItem(route, t(label)) { go { screen = route } } }
                        val last = setOf("settings", "close")
                        items.filter { it.key !in last } + labs + items.filter { it.key in last }
                    },
                    onAbout = { go { screen = "about" } },
                    lastTokPerSec = state.liveTokPerSec.takeIf { state.answeringId == null },
                    storageUsed = { services.storageUsedBytes() },
                    downloading = { team.sopa.boar.engine.models.AssetStore.downloadingBytes(services.filesDir) },
                    storageBudgetBytes = team.sopa.boar.engine.models.StorageBudget.limit(services.catalog.storageBudgetBytes),
                    showStats = state.technicalDetails,
                    answering = state.answeringId != null,
                    onNewChat = { go { vm.newChat() } },
                    onSelect = { id -> go { vm.openSession(id) } },
                    onDelete = { id -> if (services.settings.confirmChatDelete) confirmDelete = id else { vm.deleteSession(id); sessions = sessions.filter { it.id != id } } },
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
            onRedo = vm::redoWithLimit,
            onRetry = vm::retry,
            onAskModel = vm::askModel,
            onDismissLoadNote = vm::dismissLoadNote,
            onRetryLessMemory = vm::retryWithLessMemory,
            onLoadAnyway = vm::loadAnyway,
            onOpenModels = { vm.openedCatalog(); screen = "models" },
            onReused = { vm.showToast("chatScreen.messageReused") },
            onToastHidden = vm::hideToast,
            voiceEnabled = services.settings.voiceInputEnabled,
            onOpenRoute = { screen = it },
        )
    }
}

/** How long each closing step shows in the Boar Labs animation: about 1.6 s for the four. */
private const val STAGE_MS = 350L
