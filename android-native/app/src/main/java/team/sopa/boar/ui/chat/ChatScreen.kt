package team.sopa.boar.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import team.sopa.boar.engine.answer.AnswerText
import team.sopa.boar.engine.answer.ResearchStage
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import team.sopa.boar.ui.theme.BrandType
import kotlin.math.sqrt

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
    onDismissCrash: () -> Unit = {},
    onReused: () -> Unit = {},
    onToastHidden: () -> Unit = {},
    voiceEnabled: Boolean = false,
) {
    val c = Boar.colors
    val list = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var toneSheet by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    if (toneSheet) ToneSheet(state, onSetTone, onSetDeepResearch) { toneSheet = false }
    val last = state.messages.lastOrNull()
    // Follow the end of the answer while it streams, unless the user scrolled up to read: a drag that ends short of
    // the bottom stops following, reaching the bottom again resumes it, and a new message always does.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { moving -> if (!moving) follow = !list.canScrollForward }
    }
    LaunchedEffect(state.messages.size) { follow = true }
    LaunchedEffect(state.messages.size, last?.text?.length, list.isScrollInProgress) {
        // Jump to the end of the last message (the offset is clamped to the list's end); not animated, since this
        // runs on every streamed token.
        if (follow && !list.isScrollInProgress && state.messages.isNotEmpty()) list.scrollToItem(state.messages.size - 1, Int.MAX_VALUE / 2)
    }
    Box(Modifier.fillMaxSize().background(c.bgTerminal)) {
        Ambient()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Header(state, onMenu, { toneSheet = true }, onNewChat)
            if (state.deepResearch && !state.researchBannerHidden) DeepResearchBanner(onHideResearchBanner)
            state.loadingKey?.let { LoadingBanner(t(it)) }
            state.loadCrash?.let { CrashBanner(it, onDismissCrash) }
            if (state.messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    EmptyState(state.suggestions, onAsk = { onSend(it) }, onFill = { input = it })
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(), state = list,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.messages, key = { it.id }) { m -> Bubble(m, state, onRate, onReuse = { input = it; onReused() }) }
                }
            }
            InputBar(input, { input = it }, state.answeringId != null, onSend = {
                // The answer gets the whole screen: the keyboard closes on send.
                onSend(input); input = ""; keyboard?.hide(); focus.clearFocus()
            }, onStop, voiceEnabled, state.toneIcon, state.stopping) { toneSheet = true }
        }
    }
    state.toast?.let { Toast(it, onToastHidden) }
}

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
private fun Header(state: ChatState, onMenu: () -> Unit, onOpenStyles: () -> Unit, onNewChat: () -> Unit) {
    val c = Boar.colors
    val type = Boar.type
    Column(Modifier.fillMaxWidth().background(c.bgSurface).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Color.White.copy(alpha = 0.05f), null, onMenu) { Text("☰", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textHeading) }
            Row(Modifier.weight(1f).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(painterResource(R.drawable.mascot), null, Modifier.size(42.dp).clip(CircleShape), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("BOAR", style = type.titleLg.copy(letterSpacing = 0.5.sp), color = c.textHeading)
                        StatusPill(state.online)
                    }
                    Text((state.modelLabel ?: t("chatHeader.localLlmCore")).uppercase(), style = type["mono.xs"], color = c.textDim, maxLines = 1, modifier = Modifier.padding(top = 1.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(c.emeraldBgSubtle, c.emeraldBorder, onNewChat) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textAccentEmerald) }
            }
        }
        // Live speed in the header is a technical detail (Advanced tweaks → Show technical details).
        val tps = if (state.activity == Activity.Generating && state.technicalDetails) state.liveTokPerSec else null
        if (state.deepResearch || tps != null) Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.deepResearch) Row(
                Modifier.clip(RoundedCornerShape(6.dp)).background(c.frontierBadgeBg).border(1.dp, c.frontierBadgeBorder, RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenStyles).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("🔬", fontSize = 11.sp)
                Text(t("chatHeader.deepResearchActive"), style = type["mono.xs"], color = c.frontierText, fontWeight = FontWeight.Bold)
            }
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

@Composable
private fun StatusPill(online: Boolean) {
    val c = Boar.colors
    Row(
        Modifier.background(if (online) c.amberBgSubtle else c.emeraldBgSubtle, RoundedCornerShape(4.dp))
            .border(1.dp, if (online) c.amberBorder else c.emeraldBorder, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(5.dp).background(if (online) c.amber400 else c.emerald400, CircleShape))
        Text(t(if (online) "chatHeader.online" else "chatHeader.offline"), fontFamily = BrandType.familyFor("monospace", 800).first, fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold, color = if (online) c.textAccentAmber else c.textAccentEmerald)
    }
}

@Composable
private fun DeepResearchBanner(onClose: () -> Unit) {
    val c = Boar.colors
    Row(Modifier.fillMaxWidth().background(c.frontierBadgeBg).padding(start = 12.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🔬", fontSize = 10.sp)
        Text(t("chatScreen.deepResearchBanner"), style = Boar.type["mono.xs"].copy(fontSize = 9.sp, letterSpacing = 0.4.sp), color = c.frontierText,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 5.dp))
        // Closed for good: the header chip still shows Deep Research is on.
        Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Text("✕", style = Boar.type["mono.sm"], color = c.frontierText)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.frontierBadgeBorder))
}

@Composable
private fun CrashBanner(crash: team.sopa.boar.engine.models.LoadGuard.Crash, onDismiss: () -> Unit) {
    val c = Boar.colors
    val text = if (crash.fallbackLabel.isNotEmpty()) t("native.chat.loadCrashed", "crashed" to crash.crashedLabel, "fallback" to crash.fallbackLabel)
    else t("native.chat.loadCrashedNoFallback", "crashed" to crash.crashedLabel)
    Row(Modifier.fillMaxWidth().background(c.amberBgSubtle).clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Boar.type.subtext, color = c.amber400, modifier = Modifier.weight(1f))
        Text("✕", style = Boar.type["mono.sm"], color = c.amber400)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.amberBorder))
}

/** Dictation: tap to listen (asks for the microphone the first time), tap again to stop; the words go into the input. */
@Composable
private fun MicButton(text: String, onText: (String) -> Unit, enabled: Boolean, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val c = Boar.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (!team.sopa.boar.voice.VoiceInput.available(ctx)) return
    val voice = remember { team.sopa.boar.voice.VoiceInput(ctx) }
    var listening by remember { mutableStateOf(false) }
    val before = remember { mutableStateOf("") }
    val disposed = remember { booleanArrayOf(false) }
    // The permission answer comes later: by then an answer may have started and disabled the mic.
    val enabledNow = androidx.compose.runtime.rememberUpdatedState(enabled)
    DisposableEffect(Unit) { onDispose { disposed[0] = true; voice.stop() } }
    fun listen() {
        before.value = text.trimEnd()
        listening = true
        val join = { said: String -> listOf(before.value, said).filter { it.isNotBlank() }.joinToString(" ") }
        voice.start(onPartial = { onText(join(it)) }, onResult = { onText(join(it)) }, onEnd = { listening = false })
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> if (granted && !disposed[0] && enabledNow.value) listen() }
    Box(Modifier.size(size).alpha(if (enabled) 1f else 0.35f).clip(CircleShape).background(if (listening) c.crimson600 else c.bgInput)
        .border(1.dp, if (listening) c.crimsonBorder else c.borderDefault, CircleShape)
        .clickable(enabled = enabled) {
            when {
                listening -> { voice.stop(); listening = false }
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED -> listen()
                else -> permission.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }, contentAlignment = Alignment.Center) {
        Text(if (listening) "■" else "🎤", fontSize = if (listening) 12.sp else 16.sp, color = Color.White)
    }
}

/** Toast.tsx: below the header, fades in, gone after 1.8 s. */
@Composable
private fun Toast(message: String, onHide: () -> Unit) {
    val alpha = remember(message) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(message) {
        alpha.animateTo(1f, androidx.compose.animation.core.tween(150))
        kotlinx.coroutines.delay(1800)
        alpha.animateTo(0f, androidx.compose.animation.core.tween(200))
        onHide()
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 96.dp), contentAlignment = Alignment.TopCenter) {
        Text(message, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color(0xFFEEEEEE),
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value }.clip(RoundedCornerShape(20.dp))
                .background(androidx.compose.ui.graphics.Color(0xF214141E)).border(1.dp, androidx.compose.ui.graphics.Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

@Composable
private fun LoadingBanner(text: String) {
    val c = Boar.colors
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
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(R.drawable.mascot), null, Modifier.size(96.dp), contentScale = ContentScale.Fit)
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
                    Text(q, style = type.body, color = c.textPrimary)
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
private fun Bubble(m: UiMessage, state: ChatState, onRate: (String, String) -> Unit, onReuse: (String) -> Unit) {
    val c = Boar.colors
    val type = Boar.type
    val user = m.role == "user"
    val streaming = state.answeringId == m.id
    var reasoningShown by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val shape = if (user) RoundedCornerShape(20.dp, 20.dp, 8.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 8.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = maxWidth * 0.92f
        var mod = Modifier.align(if (user) Alignment.CenterEnd else Alignment.CenterStart).widthIn(min = if (user) 0.dp else 200.dp, max = maxW).clip(shape)
            .background(if (user) c.bgCardHover else c.bgSurface)
        if (!user) mod = mod.border(1.dp, c.borderDefault, shape)
        if (user) mod = mod.combinedClickable(onClick = {}, onLongClick = { onReuse(m.text) })
        Column(mod.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!user) Image(painterResource(R.drawable.mascot), null, Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Fit)
                Text(if (user) t("chatScreen.roleYou") else t("chatScreen.roleAssistant"), style = type["mono.xs"].copy(letterSpacing = 0.5.sp),
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
            when {
                m.text.isEmpty() && streaming -> ProcessingIndicator(if (state.stopping) t("native.chat.stopping") else activityLabel(state.activity))
                split.thinkingInProgress && split.answer.isEmpty() && streaming ->
                    Text("💭 " + (split.thinking?.takeLast(160) ?: ""), fontSize = 12.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic, color = c.textDim, modifier = reasoningStyle)
                split.thinkingInProgress && split.answer.isEmpty() ->
                    Text("💭 " + t("chatScreen.reasoningUnfinished"), fontSize = 12.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic, color = c.textDim, modifier = reasoningStyle)
                else -> {
                    if (reasoningShown && split.thinking != null) Text(split.thinking!!, fontSize = 12.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic, color = c.textDim, modifier = reasoningStyle)
                    MarkdownMessage(shown, streaming)
                }
            }
            if (m.sources.isNotEmpty()) SourceFootnotes(m.sources)
            if (m.stopped) Badge("⏹ " + t("chatScreen.stoppedByUser"))
            if (m.timedOut) Badge("⏱ " + t("chatScreen.stageTimedOut"))
            var receiptOpen by remember { mutableStateOf(false) }
            if (m.text.isNotEmpty() && !streaming) {
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(0.5.dp).background(c.borderSubtle))
                Row(Modifier.padding(top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FooterButton("▲", if (m.feedback == "up") t("chatScreen.rateHelpful") else null, m.feedback == "up", c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder) { onRate(m.id, "up") }
                    FooterButton("▼", if (m.feedback == "down") t("chatScreen.rateUnhelpful") else null, m.feedback == "down", c.crimson400, c.crimsonBgSubtle, c.crimsonBorder) { onRate(m.id, "down") }
                    // The receipt fills the gap between rating and copy (main's ReceiptToggle), tapped open below.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        // Everyone sees time and speed; the measured details open only with technical details on.
                        m.receipt?.let { r -> if (state.technicalDetails) ReceiptToggle(r, receiptOpen) { receiptOpen = !receiptOpen } else ReceiptShort(r) }
                    }
                    if (split.thinking != null) FooterButton("💭", null, reasoningShown, c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder) { reasoningShown = !reasoningShown }
                    var copied by remember { mutableStateOf(false) }
                    FooterButton("⧉", null, copied, c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder) { clipboard.setText(AnnotatedString(shown)); copied = true }
                }
                if (receiptOpen && state.technicalDetails) m.receipt?.let { r -> ReceiptDetails(r) }
            }
        }
    }
}

private fun receiptShort(r: Receipt, rate: String) = listOf(ReceiptFormat.seconds(r.totalMs), rate).joinToString(" · ")

/** The receipt everyone sees: "18 s · 10.4 tok/s", nothing to open. */
@Composable
private fun ReceiptShort(r: Receipt) =
    Text(receiptShort(r, t("chat.receipt.speed", "rate" to ReceiptFormat.rate(r.tokPerSec))), style = Boar.type["mono.xs"], color = Boar.colors.textMuted,
        maxLines = 1, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp))

/** The answer's receipt (main's ReceiptToggle): time and speed, tapped open for the measured details. */
@Composable
private fun ReceiptToggle(r: Receipt, open: Boolean, onToggle: () -> Unit) {
    val short = receiptShort(r, t("chat.receipt.speed", "rate" to ReceiptFormat.rate(r.tokPerSec)))
    Text("$short " + if (open) "▴" else "▾", style = Boar.type["mono.xs"], color = Boar.colors.textMuted, maxLines = 1,
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
    return listOfNotNull(
        r.retrievalMs?.let { t("chat.receipt.search") to ReceiptFormat.seconds(it) },
        r.prefillMs?.let { t("chat.receipt.prefill") to ReceiptFormat.seconds(it) },
        r.contextTokens?.let { t("chat.receipt.context") to count(it) },
        t("chat.receipt.start") to ReceiptFormat.seconds(r.ttftMs),
        r.model?.let { t("chat.receipt.model") to it },
        t("chat.receipt.speedLabel") to t("chat.receipt.speedBoth", "rate" to ReceiptFormat.rate(r.tokPerSec), "words" to "${ReceiptFormat.words(r.tokPerSec)}"),
        t("chat.receipt.words") to count(r.tokens),
        t("chat.receipt.total") to ReceiptFormat.seconds(r.totalMs),
    )
}

/** ProcessingIndicator.tsx: the stage line while nothing has streamed yet. */
@Composable
private fun ProcessingIndicator(label: String) {
    val c = Boar.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size(12.dp), color = c.textAccentEmerald, strokeWidth = 1.5.dp)
        Text(label, style = Boar.type.body, color = c.textMuted)
    }
}

@Composable
private fun FooterButton(glyph: String, label: String?, on: Boolean, onColor: Color, onBg: Color, onBorder: Color, onClick: () -> Unit) {
    val c = Boar.colors
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).background(if (on) onBg else c.bgSubtle).border(1.dp, if (on) onBorder else c.borderSubtle, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 3.dp),
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
 * The input bar (styles.inputContainer / input / sendBtn / stopBtn): the answer style on the left (opens the style
 * sheet), the mic inside the field on the right (VoiceInputButton.tsx), send or stop.
 */
@Composable
private fun InputBar(text: String, onText: (String) -> Unit, answering: Boolean, onSend: () -> Unit, onStop: () -> Unit, voiceEnabled: Boolean, toneIcon: String, stopping: Boolean, onOpenStyles: () -> Unit) {
    val c = Boar.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
    // Tight gaps, full-size buttons: the field gets the room, and the round buttons keep a 48 dp tap target.
    Row(Modifier.fillMaxWidth().background(c.bgCardElevated).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.bgInput).border(1.dp, c.borderDefault, CircleShape).clickable(onClick = onOpenStyles), contentAlignment = Alignment.Center) {
            Text(toneIcon, fontSize = 18.sp)
        }
        // A pill on one line; a rounded box once the question wraps (a pill's ends cut into the text).
        var lines by remember { mutableStateOf(1) }
        val fieldShape = if (lines > 1) RoundedCornerShape(16.dp) else RoundedCornerShape(50)
        Row(Modifier.weight(1f).clip(fieldShape).background(c.bgInput).border(1.dp, c.borderDefault, fieldShape)
            .padding(start = 18.dp, end = if (voiceEnabled) 6.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                val style = Boar.type.body.copy(fontSize = 14.sp, color = c.textPrimary)
                if (text.isEmpty()) Text(t("chatScreen.inputPlaceholder"), style = style, color = c.textDim)
                BasicTextField(text, onText, textStyle = style, cursorBrush = SolidColor(c.emerald500), modifier = Modifier.fillMaxWidth(), maxLines = 5,
                    onTextLayout = { lines = it.lineCount })
            }
            if (voiceEnabled) MicButton(text, onText, enabled = !answering, size = 34.dp)
        }
        val canSend = text.isNotBlank() && !answering
        val bg = if (answering) c.crimson600 else if (canSend) c.emerald600 else c.borderElevated
        // After Stop, the button pulses until the answer really ends: a library search or a chunk of prompt finishes first.
        val transition = rememberInfiniteTransition(label = "stopping")
        val blink by transition.animateFloat(1f, 0.35f, infiniteRepeatable(tween<Float>(500), RepeatMode.Reverse), label = "pulse")
        val pulse = if (stopping) blink else 1f
        Box(
            Modifier.size(48.dp).alpha(if (answering || canSend) pulse else 0.35f).clip(CircleShape).background(bg).clickable(enabled = answering || canSend) { if (answering) onStop() else onSend() },
            contentAlignment = Alignment.Center,
        ) { Text(if (answering) "■" else "➤", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = if (answering) 14.sp else 16.sp) }
    }
}

