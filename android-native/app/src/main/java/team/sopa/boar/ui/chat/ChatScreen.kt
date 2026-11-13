package team.sopa.boar.ui.chat

import androidx.compose.ui.semantics.clearAndSetSemantics
import team.sopa.boar.engine.chat.ConnectionPill
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.minimumInteractiveComponentSize
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import team.sopa.boar.R
import team.sopa.boar.chat.Activity
import team.sopa.boar.chat.ChatState
import androidx.compose.ui.platform.LocalContext
import team.sopa.boar.ui.I18n
import team.sopa.boar.chat.Receipt
import team.sopa.boar.chat.ReceiptFormat
import team.sopa.boar.chat.Source
import team.sopa.boar.chat.UiMessage
import team.sopa.boar.engine.UserErrors
import team.sopa.boar.engine.answer.AnswerText
import team.sopa.boar.engine.answer.ResearchStage
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import team.sopa.boar.ui.theme.BrandType
import kotlin.math.sqrt
import team.sopa.boar.ui.theme.wash

/**
 * The chat, laid out as src/ui-android/ChatScreen.tsx and ChatHeader.tsx with their style values: the ambient light,
 * header and sub-bar, the loading and Deep Research strips, the empty state, the bubbles and the input bar.
 */
@Composable
fun ChatScreen(
    state: ChatState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onMenu: () -> Unit,
    onSetTone: (String) -> Unit,
    onSetDeepResearch: (Boolean) -> Unit,
    onHideResearchBanner: () -> Unit = {},
    onRate: (String, String) -> Unit,
    /** The cut answer's "Allow more and redo": its id and the higher limit. */
    onRedo: (String, Int) -> Unit = { _, _ -> },
    onRetry: (String) -> Unit = {},
    onAskModel: (String) -> Unit = {},
    /** The memory note (LoadNote): close it, its actions by model id, and the Model Catalog. */
    onDismissLoadNote: () -> Unit = {},
    onRetryLessMemory: (String) -> Unit = {},
    onLoadAnyway: (String) -> Unit = {},
    onOpenModels: () -> Unit = {},
    onReused: () -> Unit = {},
    onToastHidden: () -> Unit = {},
    /** The setup's first question went into the input box. */
    onPrefilled: () -> Unit = {},
    /** Opens an app route from a plugin's header badge ("lab:printer"). */
    onOpenRoute: (String) -> Unit = {},
    voiceEnabled: Boolean = false,
    /** A source's "Read the whole article": the reader page for it. */
    onReadSource: (team.sopa.boar.chat.Source) -> Unit = {},
    /** The queue's ✕ and Clear queue. */
    onRemoveQueued: (Long) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onMergeQueue: () -> Unit = {},
    onSkipTyping: () -> Unit = {},
) {
    val c = Boar.colors
    // Back from the article reader (or any page), the chat is where it was (adam, Nov 12): the same answer in view.
    val returning = remember { ChatScroll.restore(state.sessionId) }
    val list = rememberLazyListState(returning?.first ?: 0, returning?.second ?: 0)
    androidx.compose.runtime.DisposableEffect(state.sessionId) { onDispose { ChatScroll.save(state.sessionId, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.canScrollForward) } }
    var input by remember { mutableStateOf("") }
    LaunchedEffect(state.prefill) { state.prefill?.let { input = it; onPrefilled() } }
    // Android 13+: "BOAR is answering…" (AnswerService) needs the notification permission, which nothing asked for
    // (issue #91). Asked once, with the first question; a refusal stands and is never asked again.
    val notifyCtx = androidx.compose.ui.platform.LocalContext.current
    val notifyAsk = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
    val send: (String) -> Unit = { q -> askNotificationsOnce(notifyCtx) { notifyAsk.launch(android.Manifest.permission.POST_NOTIFICATIONS) }; onSend(q) }
    var toneSheet by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    if (toneSheet) ToneSheet(state, onSetTone, onSetDeepResearch) { toneSheet = false }
    val last = state.messages.lastOrNull()
    // Follow the end of the answer while it streams, unless the user scrolled up to read: a drag that ends short of
    // the bottom stops following, reaching the bottom again resumes it, and a new message always does.
    var follow by remember { mutableStateOf(returning == null) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { moving -> if (!moving) follow = !list.canScrollForward }
    }
    // A new message follows the end; coming back to the same chat doesn't.
    var seenSize by remember { mutableStateOf(if (returning != null) state.messages.size else -1) }
    LaunchedEffect(state.messages.size) { if (state.messages.size != seenSize) { seenSize = state.messages.size; follow = true } }
    LaunchedEffect(state.messages.size, last?.text?.length, list.isScrollInProgress) {
        // Jump to the end of the last message (the offset is clamped to the list's end); not animated, since this
        // runs on every streamed token.
        if (follow && !list.isScrollInProgress && state.messages.isNotEmpty()) list.scrollToItem(state.messages.size - 1, Int.MAX_VALUE / 2)
    }
    Box(Modifier.fillMaxSize().background(c.bgTerminal)) {
        Ambient()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            team.sopa.boar.ui.theme.FixedSizeChrome { Header(state, onMenu, onNewChat, onOpenRoute, onSetDeepResearch) }
            if (state.deepResearch && !state.researchBannerHidden) DeepResearchBanner(onHideResearchBanner)
            state.loadingKey?.let { key -> LoadingBanner(state.loadingModel, state.loadingEngine, state.loadingSince, t(key)) }
            state.loadNote?.let { LoadNoteBanner(it, onDismissLoadNote, onRetryLessMemory, onLoadAnyway, onOpenModels) }
            // Deep Research on: the chat area takes a light tint of its color, so the mode shows at a glance.
            val researchTint = if (state.deepResearch) Boar.colors.frontierText.copy(alpha = 0.06f) else Color.Transparent
            if (state.messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(researchTint)) {
                    EmptyState(state.suggestions, onAsk = { send(it) }, onFill = { input = it })
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    // A readable line length on a wide screen (landscape, tablets), centred; a phone held upright is narrower.
                    Modifier.fillMaxSize().background(researchTint).widthIn(max = MAX_CHAT_WIDTH).align(Alignment.TopCenter), state = list,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.messages, key = { it.id }) { m -> Bubble(m, state, onRate, onReuse = { input = it; onReused() }, onRedo = onRedo, onRetry = onRetry, onAskModel = onAskModel, onReadSource = onReadSource,
                        // "Lunar is off": the Plugins list in Pro (its switch), else the plugin's page.
                        onReadPlugin = { id -> onOpenRoute(if (team.sopa.boar.labs.LabPrefs.proProfile) "labs" else "lab:$id") }, onSkipTyping = onSkipTyping) }
                }
                // ↓ when the chat isn't at its end (adam, Nov 11): back to the latest answer and its buttons in one tap.
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                if (list.canScrollForward) Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp).size(44.dp).clip(CircleShape)
                        .background(c.bgCardElevated).border(1.dp, c.borderElevated, CircleShape)
                        .clickable(onClickLabel = t("native.chat.toBottom")) { scope.launch { list.animateScrollToItem(state.messages.size - 1, Int.MAX_VALUE / 2) } },
                    contentAlignment = Alignment.Center,
                ) { team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_chevron_down, 22.dp, c.textHeading, description = t("native.chat.toBottom")) }
                }
            }
            if (state.queued.isNotEmpty()) QueueStrip(state.queued, onRemoveQueued, onClearQueue, onMergeQueue)
            InputBar(input, { input = it }, state.answeringId != null, onSend = {
                // The answer gets the whole screen: the keyboard closes on send.
                send(input); input = ""; keyboard?.hide(); focus.clearFocus()
            }, onStop, voiceEnabled, state.personalityId, state.toneIcon, state.stopping, deepResearch = state.deepResearch) { toneSheet = true }
        }
    }
}

/** The answer's ▲ ▼ rating, hidden until ratings are used for something (adam, Nov 11). */
private const val SHOW_RATING = false

/** The chat column's widest: about 75 characters of body text. */
private val MAX_CHAT_WIDTH = 720.dp

/** components/Ambient.tsx: Campfire's ember glow rising from the bottom, Moonlight's faint moon at the top right. */
@Composable
private fun Ambient() {
    val c = Boar.colors
    if (c.id == "moonlight") {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = 120.dp).size(110.dp).alpha(0.13f).background(c.cyan500, CircleShape))
        }
        return
    }
    val glow = c.emerald500
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The ember box: 60 dp past each side, 440 dp tall, 170 dp below the bottom edge; "ellipse at 50% 100%".
        Box(
            Modifier.align(Alignment.BottomCenter).offset(y = 170.dp).size(maxWidth + 120.dp, 440.dp).drawBehind {
                val rx = size.width / 2 * sqrt(2f)
                val ry = size.height * sqrt(2f)
                val center = Offset(size.width / 2, size.height)
                scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
                    drawCircle(
                        Brush.radialGradient(0f to glow.copy(alpha = 0.25f), 0.45f to glow.copy(alpha = 0.0625f), 0.7f to glow.copy(alpha = 0f), center = center, radius = rx),
                        radius = rx, center = center,
                    )
                }
            },
        )
    }
}

/** ChatHeader.tsx */
@Composable
private fun Header(state: ChatState, onMenu: () -> Unit, onNewChat: () -> Unit, onOpenRoute: (String) -> Unit, onSetDeepResearch: (Boolean) -> Unit) {
    val c = Boar.colors
    val type = Boar.type
    Column(Modifier.fillMaxWidth().background(c.bgSurface).padding(horizontal = 12.dp)) {
        // The bar every page header copies (HeaderMetrics): 62 dp, the 42 dp round buttons centred in it, and the title's two
        // lines get the whole height (t9 1950: in a 42 dp row the second line was cut off above Standard text).
        Row(Modifier.height(team.sopa.boar.ui.HeaderMetrics.HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Boar.colors.wash(0.05f), null, onMenu) { team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_menu, 22.dp, description = t("native.a11y.menu")) }
            Row(Modifier.weight(1f).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // No boar here: the menu shows it, once is enough (adam, Nov 10).
                Column(Modifier.weight(1f)) {
                    Text("BOAR", style = team.sopa.boar.ui.HeaderMetrics.titleStyle(type).copy(letterSpacing = 0.5.sp), color = c.textHeading, maxLines = 1)
                    // While adaptive routing has another model write the answer: "→ that model", then back to the pick.
                    val headerModel = team.sopa.boar.engine.chat.HeaderModel.label(state.answeringId != null, state.answeringModel, state.modelLabel)
                    // The connection lives on the model line (adam picked D of mockup-header-pill.html, Nov 12): its dot before the
                    // model's name, and for 2 s the name gives way to ONLINE or OFFLINE, same line, same size. The title never changes.
                    ConnectionLine(state.online, (headerModel ?: t("chatHeader.localLlmCore")).uppercase(), busy = state.answeringId != null) { onOpenRoute("models") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                team.sopa.boar.labs.LabHooks.HeaderBadges(onOpenRoute)
                RoundButton(c.emeraldBgSubtle, c.emeraldBorder, onNewChat) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textAccentEmerald, modifier = Modifier.spokenAs(t("chatHeader.newChatSession"))) }
            }
        }
        // Live speed in the header is a technical detail (Advanced tweaks → Show technical details).
        val tps = if (state.activity == Activity.Generating && state.technicalDetails) state.liveTokPerSec else null
        if (tps != null) Row(Modifier.padding(bottom = team.sopa.boar.ui.HeaderMetrics.PAD), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            tps?.let {
                Row(
                    Modifier.background(Color(0x1F06B6D4), RoundedCornerShape(6.dp)).border(1.dp, c.cyanBorder, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("●", fontSize = 8.sp, color = c.cyan400)
                    Text("%.1f".format(it), style = type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.Bold)
                    Text("tok/s", style = type["mono.xs"], color = c.textMuted)
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
}

@Composable
private fun RoundButton(bg: Color, border: Color?, onClick: () -> Unit, content: @Composable () -> Unit) {
    var m = Modifier.size(42.dp).clip(CircleShape).background(bg)
    if (border != null) m = m.border(1.dp, border, CircleShape)
    Box(m.clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

/** The brand's moon blue (brand book: moonlight means no signal), for the offline ring. */
private val MOON = Color(0xFF9EC7F0)

/**
 * The connection, on the header's second line (adam, Nov 12: option D, "the metamorphic one"): a small dot (amber online,
 * a greyed moon-blue ring offline) before the model's name. When BOAR opens and when the connection really changes
 * (ConnectionPill: once per state, after it has held), the name becomes "ONLINE" or "OFFLINE" for about 2 s, in the dot's
 * color, then turns back into the name. A tap shows the word for about 4 s; a tap while it shows opens what the connection
 * is used for. Nothing changes size: it is one line of the same text style, cross-faded. Reduce motion: instant.
 */
@Composable
private fun ConnectionLine(online: Boolean, model: String, busy: Boolean = false, onCatalog: () -> Unit = {}) {
    val c = Boar.colors
    val type = Boar.type
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val animOff = remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var expanded by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var tapped by remember { mutableStateOf(0) }
    LaunchedEffect(online) {
        // On open at once; a change only once it has held, so a flapping signal doesn't flash the word.
        if (ConnectionPill.lastShown != null) kotlinx.coroutines.delay(ConnectionPill.SETTLE_MS)
        if (!ConnectionPill.shouldShow(online)) return@LaunchedEffect
        ConnectionPill.lastShown = online
        expanded = true
        kotlinx.coroutines.delay(ConnectionPill.SHOW_MS)
        expanded = false
    }
    LaunchedEffect(tapped) {
        if (tapped == 0) return@LaunchedEffect
        expanded = true
        kotlinx.coroutines.delay(ConnectionPill.TAP_SHOW_MS)
        expanded = false
    }
    val tint = if (online) c.amber400 else MOON
    val label = t(if (online) "chatHeader.online" else "chatHeader.offline")
    val spoken = label + ". " + model + ". " + t("native.chat.connectionDetails")
    val style = team.sopa.boar.ui.HeaderMetrics.subtitleStyle(type)
    // First tap: the word; a tap on the word: the dialog. A screen reader gets one action that opens the dialog.
    Row(Modifier.clip(RoundedCornerShape(6.dp))
        .clearAndSetSemantics { contentDescription = spoken; role = androidx.compose.ui.semantics.Role.Button; onClick(label) { info = true; true } }
        .clickable { if (expanded) info = true else tapped++ },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).then(if (online) Modifier.background(c.amber400, CircleShape) else Modifier.border(1.5.dp, MOON.copy(alpha = 0.55f), CircleShape)))
        androidx.compose.animation.Crossfade(expanded, animationSpec = androidx.compose.animation.core.tween(if (animOff) 0 else 250), label = "connection") { word ->
            if (word) Text(label, style = style, color = tint, fontWeight = FontWeight.Bold, maxLines = 1)
            else Text(model, style = style, color = c.textDim, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
    // A tap on the word: what the connection is for, and the answer model, with the way to change it (adam, Nov 13).
    if (info) ConnectionSheet(online, busy, onCatalog = { info = false; onCatalog() }) { info = false }
}

/**
 * The sheet behind the header's second line (adam, Nov 13): the connection is only an indicator (BOAR answers the same
 * offline), and the answer model: the ones on this phone, a tap to answer with another, and the Model Catalog. The catalog's
 * own view model does the switch, so it stops a running answer first and loads the model, exactly as "Select & Use" does.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionSheet(online: Boolean, busy: Boolean, onCatalog: () -> Unit, onClose: () -> Unit) {
    val c = Boar.colors
    val models: team.sopa.boar.models.ModelsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val ms by models.state.collectAsState()
    LaunchedEffect(Unit) { models.refresh() }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bgCardElevated) {
        Column(Modifier.navigationBarsPadding().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).then(if (online) Modifier.background(c.amber400, CircleShape) else Modifier.border(1.5.dp, MOON.copy(alpha = 0.7f), CircleShape)))
                Text(t(if (online) "chatHeader.onlineTitle" else "chatHeader.offlineTitle"), style = Boar.type.titleSm, color = c.textHeading)
            }
            Text(team.sopa.boar.ui.boldMarks(t(if (online) "chatHeader.onlineBody" else "chatHeader.offlineBody")), style = Boar.type.body, color = c.textSecondary)
            Text(t("native.chat.connectionOnly"), style = Boar.type.caption, color = c.textMuted)
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(c.borderDefault))
            Text(t("native.chat.answerModel").uppercase(), style = Boar.type.caption.copy(letterSpacing = 1.sp), color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
            // The answer models on this phone; the active one is checked. Not while an answer is being written.
            val installed = ms.items.filter { it.kind == team.sopa.boar.engine.models.AssetKind.LLM && ms.rows[it.id]?.present == true }
            if (installed.isEmpty()) Text(t("native.chat.noModels"), style = Boar.type.caption, color = c.textMuted)
            installed.forEach { a ->
                val active = a.id == ms.activeLlm
                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !active && !busy && ms.activating == null, role = androidx.compose.ui.semantics.Role.Button) { models.use(a); onClose() }
                    .padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (active) "✓" else " ", style = Boar.type.body, color = c.textAccentEmerald, modifier = Modifier.widthIn(min = 16.dp))
                    Text(a.shortName, style = Boar.type.body, color = if (active) c.textAccentEmerald else c.textPrimary, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f).alpha(if (busy && !active) 0.5f else 1f))
                    Text(team.sopa.boar.engine.models.Units.formatBytes(a.sizeBytes), style = Boar.type.caption, color = c.textMuted)
                }
            }
            if (busy) Text(t("native.chat.modelBusy"), style = Boar.type.caption, color = c.amber400)
            Text(t("native.chat.openCatalog") + " ›", style = Boar.type.body, color = c.textAccentCyan, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onCatalog).padding(horizontal = 8.dp, vertical = 14.dp))
        }
    }
}

@Composable
private fun DeepResearchBanner(onClose: () -> Unit) {
    val c = Boar.colors
    Row(Modifier.fillMaxWidth().background(c.frontierBadgeBg).padding(start = 12.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_deep_research, 12.dp, c.frontierText)
        Text(t("chatScreen.deepResearchBanner"), style = Boar.type["mono.xs"].copy(fontSize = 9.sp, letterSpacing = 0.4.sp), color = c.frontierText,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 5.dp))
        // Closed for good: the input's border and the chat's tint still show Deep Research is on (no header button since Nov 11).
        Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_close, 14.dp, c.frontierText, description = t("native.a11y.close"))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.frontierBadgeBorder))
}

/**
 * The answer model and the phone's memory: it closed the app last time (with "Try again with less memory" and the
 * Model Catalog), or it may not fit (with "Load anyway" and another model). Nothing is blocked for good.
 */
@Composable
private fun LoadNoteBanner(
    note: team.sopa.boar.chat.LoadNote,
    onDismiss: () -> Unit,
    onRetryLessMemory: (String) -> Unit,
    onLoadAnyway: (String) -> Unit,
    onOpenModels: () -> Unit,
) {
    val c = Boar.colors
    val catalog = t("modelCatalogScreen.title")
    val (text, actions) = when (note) {
        is team.sopa.boar.chat.LoadNote.Crashed -> {
            val retry = t("native.chat.retryLessMemory") to { onRetryLessMemory(note.modelId) }
            when {
                note.onlyModel -> t("native.chat.loadCrashedOnly", "model" to note.label) to listOf(catalog to onOpenModels, retry)
                note.fallbackLabel != null -> t("native.chat.loadCrashed", "crashed" to note.label, "fallback" to note.fallbackLabel) to listOf(retry, catalog to onOpenModels)
                else -> t("native.chat.loadCrashedNoFallback", "crashed" to note.label) to listOf(retry, catalog to onOpenModels)
            }
        }
        is team.sopa.boar.chat.LoadNote.Risky -> {
            val load = t("native.chat.loadAnyway") to { onLoadAnyway(note.modelId) }
            // The safer choice first (filled): another model; loading anyway is the user's call.
            if (note.onlyModel) t("native.chat.mayNotFitOnly", "model" to note.label) to listOf(catalog to onOpenModels, load)
            else t("native.chat.mayNotFit", "model" to note.label) to listOf(t("native.chat.pickAnother") to onOpenModels, load)
        }
    }
    Column(Modifier.fillMaxWidth().background(c.amberBgSubtle).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text(text, style = Boar.type.subtext, color = c.amber400, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_close, 14.dp, c.amber400, description = t("native.a11y.close"))
            }
        }
        // The first action is the recommended one: filled. Both share the width and the height of the taller label.
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { i, (label, action) ->
                val primary = i == 0
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                    .background(if (primary) c.amber400 else Color.Transparent)
                    .border(1.dp, if (primary) c.amber400 else c.amberBorder, RoundedCornerShape(8.dp))
                    .clickable(onClick = action).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = Boar.type.subtext, color = if (primary) c.textInverse else c.amber400, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.amberBorder))
}

/** "Loading Qwen3 4B · 3 s" (and "with Big Boar" for an engine plugin): which model, and that it's still going. */
@Composable
private fun LoadingBanner(model: String?, engine: String?, since: Long, fallback: String) {
    val c = Boar.colors
    var seconds by remember(since) { mutableStateOf(0L) }
    LaunchedEffect(since) { while (true) { seconds = (android.os.SystemClock.elapsedRealtime() - since) / 1000; kotlinx.coroutines.delay(1000) } }
    val text = when {
        model == null -> fallback
        engine == null -> t("native.chat.loadingModel", "model" to model, "s" to seconds)
        else -> t("native.chat.loadingModelWith", "model" to model, "engine" to engine, "s" to seconds)
    }
    Row(Modifier.fillMaxWidth().background(c.emeraldBgSubtle).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size(12.dp), color = c.textAccentEmerald, strokeWidth = 1.5.dp)
        Text(text, style = Boar.type["mono.xs"], color = c.textAccentEmerald, fontWeight = FontWeight.SemiBold)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.emeraldBorder))
}

/** components/ChatEmptyState.tsx: the boar, the name and tagline, then the suggested questions (tap asks, long-press fills). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmptyState(suggestions: List<String>, onAsk: (String) -> Unit, onFill: (String) -> Unit) {
    val c = Boar.colors
    val type = Boar.type
    // Scrolls: with a large font size (or a small screen) the last ideas were below the input and out of reach.
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp).padding(top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(team.sopa.boar.appearance.TruffleVault.avatar(team.sopa.boar.appearance.LocalTruffle.current, R.drawable.mascot)), null, Modifier.size(96.dp), contentScale = ContentScale.Fit)
            Text("BOAR", style = type.headline, color = c.textHeading)
            Text(t("chat.empty.tagline"), style = type.subtext.copy(fontFamily = BrandType.familyFor(null, 500).first), color = c.textAccentEmerald, textAlign = TextAlign.Center)
        }
        if (suggestions.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("chat.empty.suggestionsLabel"), style = type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
            for (k in suggestions) {
                val q = t("chat.suggestions.$k")
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.bgCard).combinedClickable(onClick = { onAsk(q) }, onLongClick = { onFill(q) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(t("chat.suggestionTopics.$k"), style = type.caption.copy(fontFamily = BrandType.familyFor(null, 600).first), color = c.textAccentEmerald)
                    // Three research topics, three lines each at most: a whole topic, even in Portuguese at Large text.
                    Text(q, style = type.body, color = c.textPrimary, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun activityLabel(a: Activity?): String = when (a) {
    Activity.Retrieving -> t("processingIndicator.retrieving")
    Activity.Thinking -> t("processingIndicator.thinking")
    Activity.Generating -> t("processingIndicator.generating")
    is Activity.Researching -> when (a.stage) {
        ResearchStage.DECOMPOSING -> t("chatScreen.research.decomposing")
        ResearchStage.RESEARCHING -> t("chatScreen.research.researching", "index" to (a.index ?: 1), "count" to (a.count ?: 1))
        ResearchStage.SYNTHESIZING -> t("chatScreen.research.synthesizing")
    }
    null -> ""
}

/** A message bubble (ChatScreen.tsx styles.bubble / userBubble / assistantBubble). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: UiMessage, state: ChatState, onRate: (String, String) -> Unit, onReuse: (String) -> Unit, onRedo: (String, Int) -> Unit, onRetry: (String) -> Unit, onAskModel: (String) -> Unit = {},
    onReadSource: (team.sopa.boar.chat.Source) -> Unit = {}, onReadPlugin: (String) -> Unit = {}, onSkipTyping: () -> Unit = {}) {
    val c = Boar.colors
    val type = Boar.type
    val user = m.role == "user"
    val streaming = state.answeringId == m.id
    var reasoningShown by remember { mutableStateOf(false) }
    // When this answer started, for the seconds on its activity line (Expo's ReasoningPeek).
    val startedAt = state.answerStartedAt
    val clipboard = LocalClipboardManager.current
    val shape = if (user) RoundedCornerShape(20.dp, 20.dp, 8.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 8.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = maxWidth * 0.92f
        // The shape is drawn, not clipped: a clip on a very tall answer (Staged's quotes open) stopped taps on its lower
        // half, the 🧩, Copy and rating row included (X6, Nov 10). Nothing inside draws past the padding.
        var mod = Modifier.align(if (user) Alignment.CenterEnd else Alignment.CenterStart).widthIn(min = if (user) 0.dp else 200.dp, max = maxW)
            .background(if (user) c.bgCardHover else c.bgSurface, shape)
        // A Deep Research answer keeps its mark in the history: the border and the header in the mode's color.
        val researched = !user && m.receipt?.path == "deep"
        if (!user) mod = mod.border(if (researched) 1.5.dp else 1.dp, if (researched) c.frontierBadgeBorder else c.borderDefault, shape)
        if (user) mod = mod.combinedClickable(onClick = {}, onLongClick = { onReuse(m.text) })
        Column(mod.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // A Labs plugin's instant answer speaks as that plugin (AnswerTools.speaker): its art and name, not BOAR's.
            val speaker = if (user) null else m.receipt?.tool?.substringBefore('+')?.let { team.sopa.boar.plugins.AnswerTools.speaker(it) }
            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!user) {
                    val art = speaker?.art
                    if (speaker != null && art == null) Text(speaker.emoji, fontSize = 15.sp)
                    else Image(painterResource(art ?: team.sopa.boar.appearance.TruffleVault.avatar(team.sopa.boar.appearance.LocalTruffle.current, R.drawable.mascot)), null, Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Fit)
                }
                Text(if (user) t("chatScreen.roleYou") else speaker?.let { t(it.titleKey) } ?: if (researched) t("native.chat.roleDeepResearch") else t("chatScreen.roleAssistant"), style = type["mono.xs"].copy(letterSpacing = 0.5.sp),
                    color = if (user) c.textAccentCyan else c.textAccentEmerald, fontWeight = FontWeight.SemiBold)
            }
            if (user) {
                Text(m.text, style = type.bodyLg, color = c.textPrimary)
                return@Column
            }
            val split = AnswerText.splitThinking(m.text)
            // Sources are attached when the reply finishes, so invented citations are cleaned only then.
            val shown = if (streaming) split.answer else AnswerText.splitInlineBullets(AnswerText.cleanCitations(split.answer, m.sources.size))
            val reasoningStyle = Modifier.drawBehind { drawRect(c.borderDefault, size = size.copy(width = 2.dp.toPx())) }.padding(start = 8.dp, bottom = 8.dp)
            // Layout B+ (Advanced tweaks → Sources layout): the sources open above the answer, never removed when it starts;
            // a tap there or on an [n] in the answer opens the passage in a sheet.
            val question = state.messages.take(state.messages.indexOfFirst { it.id == m.id }.coerceAtLeast(0)).lastOrNull { it.role == "user" }?.text.orEmpty()
            // One sources design for everyone (adam, Nov 12): the block above the answer, or Staged's quote cards when Staged
            // shows them. A tap on a source, a quote or an [n] opens the article at the passage that backs the answer.
            fun read(n: Int) { m.sources.getOrNull(n - 1)?.let(onReadSource) }
            val above = m.sources.isNotEmpty() && m.staged == null
            if (above) SourcesAbove(m.id, question, m.sources) { read(it) }
            // Boar Staged answers: what the sources say comes first, before and above the answer's text (with B+ the block
            // above says it, so Staged's quote cards don't show).
            if (!above) m.staged?.let { StagedCard(it, streaming, m.text.isNotEmpty(), onReadSource, key = m.id) }
            when {
                m.text.isEmpty() && streaming -> ProcessingIndicator(if (state.stopping) t("native.chat.stopping") else activityLabel(state.activity),
                    if (state.stopping) null else activityDetail(state.activity), startedAt)
                split.thinkingInProgress && split.answer.isEmpty() && streaming -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("chatScreen.reasoningElapsed", "seconds" to elapsedSeconds(startedAt)), style = Boar.type.subtext, color = c.textMuted)
                    ThinkingTail(split.thinking.orEmpty(), reasoningStyle)
                }
                // The reasoning used the whole answer budget: the note says "tap 💭", so 💭 shows what it reasoned.
                split.thinkingInProgress && split.answer.isEmpty() ->
                    Text(if (reasoningShown && split.thinking != null) split.thinking!! else "💭 " + t("chatScreen.reasoningUnfinished"),
                        fontSize = 12.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic, color = c.textDim, modifier = reasoningStyle)
                else -> {
                    if (reasoningShown && split.thinking != null) Text(split.thinking!!, fontSize = 12.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic, color = c.textDim, modifier = reasoningStyle)
                    // A tap while an instant answer types shows the rest at once (InstantTyping).
                    if (streaming) Box(Modifier.clickable(onClickLabel = t("native.chat.showAll")) { onSkipTyping() }) { MarkdownMessage(shown, true) }
                    else MarkdownMessage(shown, streaming, onCite = if (m.sources.isNotEmpty() && !streaming) ({ n -> if (n in 1..m.sources.size) read(n) }) else null)
                }
            }
            // The answer failed (no model, an engine or storage error): a plain line, never the exception text.
            // Kept on the message (UiMessage.error), so it stays after the next question; the live error covers a failure
            // before the answer could be saved.
            val errorKey = m.error ?: state.error?.takeIf { state.messages.lastOrNull()?.id == m.id }?.let(UserErrors::chatKey)
            if (!streaming && !m.stopped && errorKey != null) {
                Text(t(errorKey), style = type.bodyLg, color = c.crimson400)
                if ((errorKey == UserErrors.chatKey(UserErrors.EMPTY_ANSWER) || errorKey == UserErrors.INTERRUPTED_KEY) && state.answeringId == null)
                    ActionButton("↻", t("native.chat.tryAgain")) { onRetry(m.id) }
                // Holding a question already copies it back to the input (onReuse); say so where it helps.
                Text(t("native.chat.holdToReuse"), style = Boar.type.subtext, color = c.textPrimary, modifier = Modifier.padding(top = 2.dp))
            }
            // Stopped before writing anything: these are what the search found, not what the answer cites.
            // BOAR answered in another style than the one picked (a health question under Detailed): say so, once, under it.
            if (!streaming) m.receipt?.styleNote?.let { Text(t("native.chat.styleNote.$it"), style = Boar.type.subtext, color = c.textDim, modifier = Modifier.padding(top = 2.dp)) }
            // "Every number … is in its sources" only when there are sources to check against.
            if (!streaming && m.sources.isNotEmpty()) m.staged?.let { StagedCheck(it) }
            // No list under the answer any more: the sources are shown once, above it (adam, Nov 12).
            // The question was a plugin's, but it's off (Nov 12): one line, a tap goes where it's turned on.
            if (!streaming) m.offPlugin?.let { id ->
                Text(t("native.chat.pluginOff", "name" to t("native.labs.$id.title")), style = Boar.type.subtext, color = c.textAccentCyan,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { onReadPlugin(id) }.padding(vertical = 4.dp))
            }
            if (m.stopped) Badge("⏹ " + t("chatScreen.stoppedByUser"))
            if (m.timedOut) Badge("⏱ " + t("chatScreen.stageTimedOut"))
            // The plugin answered without the model: the same question, plugins skipped (never for the CO safety answer).
            // Its own row on the right, bigger, with BOAR's mascot: a way out of the plugin's answer, not one of its controls.
            if (!streaming && speaker?.canBypass == true && state.answeringId == null) Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.CenterEnd) {
                ActionButton(null, t("native.chat.askBoarInstead"), art = R.drawable.mascot) { onAskModel(m.id) }
            }
            // The cut is a note; the way to run it again with more tokens is its own button on the next line, in the
            // accent, so the two never squeeze into one row.
            m.cutAt?.let { limit ->
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge("✂ " + t("native.chat.cutAtLimit", "limit" to limit))
                    if (m.nextLimit != null && state.answeringId == null) ActionButton("↻", t("native.chat.allowMore", "limit" to m.nextLimit)) { onRedo(m.id, m.nextLimit) }
                }
            }
            var receiptOpen by remember { mutableStateOf(false) }
            // Plugin slots (Boar Labs): what this answer hands them, and their content under it.
            val ref = team.sopa.boar.plugins.AnswerRef(m.id, state.sessionId,
                state.messages.take(state.messages.indexOfFirst { it.id == m.id }.coerceAtLeast(0)).lastOrNull { it.role == "user" }?.text.orEmpty(),
                shown, m.sources.map { it.title to it.source }, state.answeringId != null, m.receipt?.model, m.receipt?.tokPerSec, m.receipt?.tool, m.receipt?.styleNote,
                m.sources.map { it.title to it.body })
            if (m.text.isNotEmpty() && !streaming) team.sopa.boar.labs.LabHooks.AnswerExtras(ref)
            if (m.text.isNotEmpty() && !streaming) {
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(0.5.dp).background(c.borderSubtle))
                Row(Modifier.padding(top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Rating hidden (adam, Nov 11): nothing uses ratings yet. The code stays for when something does.
                    if (SHOW_RATING) {
                        FooterButton("▲", null, m.feedback == "up", c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder, t("native.a11y.rateUp")) { onRate(m.id, "up") }
                        FooterButton("▼", null, m.feedback == "down", c.crimson400, c.crimsonBgSubtle, c.crimsonBorder, t("native.a11y.rateDown")) { onRate(m.id, "down") }
                    }
                    // The receipt fills the gap between rating and copy (main's ReceiptToggle), tapped open below.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        // Everyone sees time and speed; the measured details open only with technical details on.
                        m.receipt?.let { r -> if (state.technicalDetails) ReceiptToggle(r, receiptOpen) { receiptOpen = !receiptOpen } else ReceiptShort(r) }
                    }
                    if (split.thinking != null) FooterButton("💭", null, reasoningShown, c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder, t("native.a11y.reasoning")) { reasoningShown = !reasoningShown }
                    team.sopa.boar.labs.LabHooks.AnswerActions(ref)
                    var copied by remember { mutableStateOf(false) }
                    // With 💭 open, the reasoning is copied too: reviewing a thinking model needs both.
                    FooterButton("⧉", null, copied, c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder, t("native.a11y.copyAnswer")) {
                        clipboard.setText(AnnotatedString(if (reasoningShown && split.thinking != null) "💭 ${split.thinking}\n\n---\n\n$shown" else shown)); copied = true
                    }
                }
                if (receiptOpen && state.technicalDetails) m.receipt?.let { r -> ReceiptDetails(r) }
            }
        }
    }
}

/** A tool's instant answer generated nothing: "0.0 s · instant" instead of a speed. */
private fun Receipt.instant() = tool != null && tokens == 0

private fun receiptShort(r: Receipt, rate: String) = listOf(ReceiptFormat.seconds(r.totalMs), rate).joinToString(" · ")

@Composable
private fun receiptRate(r: Receipt) = if (r.instant()) t(if (r.typed) "native.receipt.instantTyped" else "native.receipt.instant") else t("chat.receipt.speed", "rate" to ReceiptFormat.rate(r.tokPerSec))

/** The receipt everyone sees: "18 s · 10.4 tok/s", nothing to open. */
@Composable
private fun ReceiptShort(r: Receipt) =
    Text(receiptShort(r, receiptRate(r)), style = Boar.type["mono.xs"], color = Boar.colors.textMuted,
        // Two lines with Large text rather than losing "tok/s" off the end.
        maxLines = 2, textAlign = TextAlign.End, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp))

/** The answer's receipt (main's ReceiptToggle): time and speed, tapped open for the measured details. */
@Composable
private fun ReceiptToggle(r: Receipt, open: Boolean, onToggle: () -> Unit) {
    val short = receiptShort(r, receiptRate(r))
    Text("$short " + if (open) "▴" else "▾", style = Boar.type["mono.xs"], color = Boar.colors.textMuted, maxLines = 2, textAlign = TextAlign.End,
        modifier = Modifier.clickable(onClickLabel = t("chat.receipt.details"), onClick = onToggle).padding(vertical = 8.dp, horizontal = 4.dp))
}

@Composable
private fun ReceiptDetails(r: Receipt) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        receiptRows(r).forEach { (label, value) -> Text("$label: $value", style = Boar.type["mono.xs"], color = Boar.colors.textSecondary) }
    }
}

/** main's receiptDetails: what the answer took, step by step. */
@Composable
private fun receiptRows(r: Receipt): List<Pair<String, String>> {
    val ctx = LocalContext.current
    fun count(tokens: Int) = I18n.t(ctx, "chat.receipt.tokenCount", "tokens" to "$tokens", "words" to "${ReceiptFormat.words(tokens.toDouble())}")
    // Mode first: the answer style, then how it was written (Deep Research, from this chat, adaptive routing and its plan).
    val mode = r.style?.let { style ->
        listOfNotNull(
            t("native.receipt.style.$style").takeUnless { it == "native.receipt.style.$style" } ?: style,  // an unknown style shows its id
            when (r.path) {
                "deep" -> t("native.receipt.path.deep")
                "chat" -> t("native.receipt.path.chat")
                "adaptive" -> listOfNotNull(t("native.receipt.path.adaptive"), r.task, if (r.checked) t("native.receipt.checked") else null).joinToString(" · ")
                "staged" -> t("native.receipt.path.staged")
                else -> null
            },
        ).joinToString(" · ")
    }
    return listOfNotNull(
        mode?.let { t("native.receipt.mode") to it },
        r.retrievalMs?.let { t("chat.receipt.search") to ReceiptFormat.seconds(it) },
        // A staged answer: when each step showed up, from the question.
        r.stages?.let { st -> t("native.receipt.stages") to listOf("sources", "introFirst", "detailsFirst").mapNotNull { k -> st[k]?.let { t("native.receipt.stage.$k") + " " + ReceiptFormat.seconds(it) } }.joinToString(" · ") },
        r.prefillMs?.let { t("chat.receipt.prefill") to ReceiptFormat.seconds(it) },
        r.contextTokens?.let { t("chat.receipt.context") to count(it) },
        t("chat.receipt.start") to ReceiptFormat.seconds(r.ttftMs),
        r.model?.let { t("chat.receipt.model") to it },
        r.tool?.let { t("native.receipt.tool") to it },
        if (r.instant()) null else t("chat.receipt.speedLabel") to t("chat.receipt.speedBoth", "rate" to ReceiptFormat.rate(r.tokPerSec), "words" to "${ReceiptFormat.words(r.tokPerSec)}"),
        if (r.instant()) null else t("chat.receipt.words") to count(r.tokens),
        t("chat.receipt.total") to ReceiptFormat.seconds(r.totalMs),
    )
}

/**
 * The reasoning while it streams: always 3 lines high, showing its last 3 lines, cut at a word. A window of the last
 * 160 characters wrapped to 2, 3 or 4 lines as tokens came in, and the bubble (and the auto-scroll with it) jumped.
 */
@Composable
private fun ThinkingTail(thinking: String, modifier: Modifier) {
    val c = Boar.colors
    val tail = thinking.takeLast(400).let { t -> if (t.length < thinking.length) t.substringAfter(' ', t) else t }.replace(Regex("\\s+"), " ").trim()
    val box = with(androidx.compose.ui.platform.LocalDensity.current) { (17.sp * 3).toDp() }
    // One 💭 only: the wait line above already says "💭 Reasoning…" (two balloons, X6, Nov 10). Only the text scrolls.
    Row(modifier.fillMaxWidth().height(box)) {
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            // Taller than the box and bottom-aligned: the newest line stays at the bottom, older ones slide out at the top.
            Text(tail, fontSize = 12.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic, color = c.textDim,
                modifier = Modifier.align(Alignment.BottomStart).wrapContentHeight(Alignment.Bottom, unbounded = true))
        }
    }
}

/**
 * ProcessingIndicator.tsx: the stage line while nothing has streamed yet, with its seconds, and a second line under it:
 * the sub-question Deep Research is on, else one of the loading lines, swapped every few seconds (Expo's two-line wait).
 */
@Composable
private fun ProcessingIndicator(label: String, detail: String?, startedAt: Long) {
    val c = Boar.colors
    val seconds = elapsedSeconds(startedAt)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(12.dp), color = c.textAccentEmerald, strokeWidth = 1.5.dp)
            Text(if (seconds >= 2) "$label · ${seconds}s" else label, style = Boar.type.body, color = c.textMuted)
        }
        val line = detail ?: t("flows.onboarding.loadingLines.l${(seconds / LOADING_LINE_SECONDS + startedAt / 1000).mod(LOADING_LINES) + 1}")
        Text(line, style = Boar.type.subtext, fontStyle = FontStyle.Italic, color = c.textDim, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 20.dp))
    }
}

/** Whole seconds since [startedAt] (elapsedRealtime), ticking once a second while shown. */
@Composable
private fun elapsedSeconds(startedAt: Long): Int {
    var now by remember { mutableStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt) { while (true) { kotlinx.coroutines.delay(1000); now = android.os.SystemClock.elapsedRealtime() } }
    return ((now - startedAt) / 1000).toInt().coerceAtLeast(0)
}

/** The second line's real detail when there is one: the sub-question Deep Research is reading for. */
private fun activityDetail(a: Activity?): String? =
    (a as? Activity.Researching)?.takeIf { it.stage == ResearchStage.RESEARCHING }?.subQuestion?.trim()?.takeIf { it.isNotEmpty() }?.let { "“$it”" }

/** flows.onboarding.loadingLines.l1 … l12, each shown this long. */
private const val LOADING_LINES = 12
private const val LOADING_LINE_SECONDS = 4

/**
 * Something to do about an answer (Try again, Allow more, Ask BOAR instead): on its own line, full size, in the accent,
 * never squeezed next to a note. The footer's small chips ([FooterButton]) are for its icons only.
 */
@Composable
internal fun ActionButton(glyph: String?, label: String, art: Int? = null, onClick: () -> Unit) {
    val c = Boar.colors
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(c.emeraldBgSubtle).border(1.dp, c.emeraldBorder, RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        art?.let { Image(painterResource(it), null, Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))) }
        glyph?.let { Text(it, style = Boar.type.titleSm, color = c.textAccentEmerald) }
        Text(label, style = Boar.type.titleSm, color = c.textAccentEmerald)
    }
}

@Composable
internal fun FooterButton(glyph: String, label: String?, on: Boolean, onColor: Color, onBg: Color, onBorder: Color, description: String? = null, onClick: () -> Unit) {
    val c = Boar.colors
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).background(if (on) onBg else c.bgSubtle).border(1.dp, if (on) onBorder else c.borderSubtle, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 3.dp)
            // What TalkBack says for a symbol-only button (UX review B1): "Copy the answer", not "white square".
            .then(if (description != null) Modifier.spokenAs(description) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = if (on) onColor else c.textDim)
        label?.let { Text(it, style = Boar.type.micro.copy(fontSize = 10.sp, letterSpacing = 0.2.sp), fontWeight = FontWeight.SemiBold, color = onColor) }
    }
}

@Composable
private fun Badge(text: String) {
    val c = Boar.colors
    Text(text, style = Boar.type["mono.xs"], color = c.textAccentAmber, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp).background(c.amberBgSubtle, RoundedCornerShape(4.dp)).border(1.dp, c.amberBorder, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
}

/**
 * The chat's input bar: the shared [PromptBar] (Designer uses the same one) with the answer style on the left (opens the
 * style sheet), the chat's placeholder, and Send that queues while BOAR answers.
 */
@Composable
private fun InputBar(text: String, onText: (String) -> Unit, answering: Boolean, onSend: () -> Unit, onStop: () -> Unit, voiceEnabled: Boolean, toneId: String, toneIcon: String, stopping: Boolean, deepResearch: Boolean = false, onOpenStyles: () -> Unit) {
    val c = Boar.colors
    // Deep Research on: the field's border in the mode's color and its own placeholder, right where you type.
    PromptBar(text, onText, answering, onSend, onStop, voiceEnabled,
        placeholder = t(if (deepResearch) "native.chat.deepResearchPlaceholder" else "chatScreen.inputPlaceholder"), onLeft = onOpenStyles,
        left = { team.sopa.boar.ui.StyleIcon(toneId, toneIcon, 22.dp, description = t("native.a11y.style")) },
        stopping = stopping, highlight = deepResearch, placeholderColor = if (deepResearch) c.frontierText else null)
}

/** The questions waiting their turn, above the input: "Waiting 1 · …" with ✕ each, and Clear queue. */
@Composable
private fun QueueStrip(queued: List<team.sopa.boar.chat.QueuedPrompt>, onRemove: (Long) -> Unit, onClear: () -> Unit, onMerge: () -> Unit) {
    val c = Boar.colors
    Column(Modifier.fillMaxWidth().background(c.bgCardElevated).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("native.chat.queueNote"), style = Boar.type.subtext, color = c.textMuted, modifier = Modifier.weight(1f))
            // Icons (adam, Nov 12): merge the waiting questions into one (two or more), and clear the queue.
            if (queued.size >= 2) Text("⇉", style = Boar.type.title, color = c.textAccentCyan,
                modifier = Modifier.minimumInteractiveComponentSize().clip(CircleShape).clickable(onClickLabel = t("native.chat.mergeQueue"), onClick = onMerge).padding(6.dp))
            team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_delete, 20.dp, c.textMuted,
                Modifier.minimumInteractiveComponentSize().clip(CircleShape).clickable(onClickLabel = t("native.chat.clearQueue"), onClick = onClear).padding(6.dp), t("native.chat.clearQueue"))
        }
        queued.forEachIndexed { i, q ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Just the hourglass and the place in line (adam, Nov 12): "⏳ 1", not "Waiting 1" on every row.
                Text("⏳ ${i + 1}", style = Boar.type["mono.xs"], color = c.textAccentEmerald, fontWeight = FontWeight.Bold)
                Text(q.text, style = Boar.type.caption, color = c.textSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("✕", style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.minimumInteractiveComponentSize().clip(CircleShape).clickable { onRemove(q.id) }.padding(6.dp))
            }
        }
    }
}

/** Runs [ask] once per install on Android 13+, when BOAR may not post notifications yet (issue #91). */
private fun askNotificationsOnce(ctx: android.content.Context, ask: () -> Unit) = team.sopa.boar.app.NotificationAsk.once(ctx, ask)

/** One spoken label for the whole button, in place of its symbol. */
private fun Modifier.spokenAs(label: String): Modifier =
    this.then(Modifier.clearAndSetSemantics { contentDescription = label; role = androidx.compose.ui.semantics.Role.Button })

/**
 * The chat's scroll for the open chat, kept while BOAR runs: back from the reader (or another page), the same answer is
 * in view instead of the end of the chat (adam, Nov 12). At the end already: nothing kept, it follows as before.
 */
internal object ChatScroll {
    private var session: String? = null
    private var at: Pair<Int, Int>? = null

    fun save(sessionId: String?, index: Int, offset: Int, notAtEnd: Boolean) {
        session = sessionId
        at = if (notAtEnd) index to offset else null
    }

    fun restore(sessionId: String?): Pair<Int, Int>? = at.takeIf { sessionId != null && sessionId == session }.also { at = null }
}
