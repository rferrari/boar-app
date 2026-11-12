package team.sopa.boar.labs.designer

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.remember
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import team.sopa.boar.engine.labs.DesignerCanvas
import team.sopa.boar.ui.PackIcon
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.io.File
import java.util.Locale

/** Boar Designer (was Image Gen) as a Boar Labs plugin: the screen stays on while it draws or downloads, and back waits for the drawing. */
object ImageGenLab : team.sopa.boar.labs.LabPlugin {
    override val id = "image"

    override suspend fun selfTest(context: android.content.Context, args: Map<String, String>, say: (String) -> Unit): Boolean {
        DesignerSelfTest.run(context, args, say); return true
    }

    /** Chats erased: their illustrations go. Everything: every picture made here too (the model stays). */
    override fun eraseUserData(context: android.content.Context, scope: team.sopa.boar.plugins.UserData.Scope) {
        Illustrator.eraseAll(context)
        if (scope == team.sopa.boar.plugins.UserData.Scope.EVERYTHING) SdRunner.outDir(context).listFiles().orEmpty().forEach { it.delete() }
    }
    override fun eraseAnswers(context: android.content.Context, answerIds: List<String>) = Illustrator.eraseAnswers(context, answerIds)

    override val emoji = "🎨"
    override val art = team.sopa.boar.R.drawable.truffle_retrowave
    override val status = team.sopa.boar.labs.LabStatus.BETA

    /** The picture is the page (adam's mock-up, Nov 13): the canvas fills the space under the header and never scrolls. */
    override val fullPage = true

    @Composable
    override fun Content() {
        val vm: ImageLabViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshLastChat(); vm.reload() }
        val state by vm.state.collectAsState()
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.DisposableEffect(state.phase, state.downloadPct) {
            view.keepScreenOn = state.phase != null || state.downloadPct != null
            onDispose { view.keepScreenOn = false }
        }
        var managing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        // Back while drawing stops the drawing (or the variations), as before the canvas.
        if (state.phase != null) androidx.activity.compose.BackHandler { vm.cancel() }
        if (managing) {
            androidx.activity.compose.BackHandler { managing = false }
            // The media manager as it was; the full page doesn't scroll, so it brings its own scroll and margin.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).padding(bottom = 24.dp)) {
                MediaManager(state.gallery, vm) { managing = false }
            }
        } else DesignerCanvasScreen(state, vm) { managing = true }
    }

    /** 🎨 in the answer's 🧩 sheet: Illustrator picks what to draw and draws it below the answer. */
    @Composable
    override fun answerActions(a: team.sopa.boar.plugins.AnswerRef): List<team.sopa.boar.labs.LabAction> {
        val ctx = LocalContext.current
        // Nothing to draw for a tool's fixed answer (a carbon-monoxide warning, a fact, a distance, a language reply) or a
        // health or seed-phrase question: a picture there could show the very thing the answer warns against (Oct 18).
        if (!Illustrator.drawable(a)) return emptyList()
        val st = Illustrator.status(ctx, a.id)
        val running = st is Illustrator.Status.Choosing || st is Illustrator.Status.Drawing
        val notReady = t("native.labs.image.illustrateNotReady")
        // No drawing model (or engine) on the phone yet: 🎨 is greyed out and says where to get it, instead of failing on tap.
        val ready = SdRunner.ready(ctx) // a file check, read each time the sheet is drawn so a fresh download counts
        val label = t(when { !ready -> "native.labs.image.illustrateNeedsModel"; running -> "native.labs.image.illustrateRunning"; st is Illustrator.Status.Done -> "native.labs.image.illustrateAgain"; else -> "native.labs.image.illustrate" })
        // The row draws at once with the defaults; ✎ opens the style and size sheet first (adam, Nov 11), in every profile.
        return listOf(team.sopa.boar.labs.LabAction("🎨", label, done = st is Illustrator.Status.Done, enabled = ready && !running, art = art,
            onEdit = { Illustrator.asking = a.id }) { Illustrator.start(ctx, a, notReady) })
    }

    /** The drawing (or its progress) under the answer, with what was drawn and how long it took. */
    @Composable
    override fun AnswerExtra(a: team.sopa.boar.plugins.AnswerRef) {
        val c = Boar.colors
        val ctx = LocalContext.current
        if (Illustrator.asking == a.id) IllustrateSheet(a) { Illustrator.asking = null }
        when (val st = Illustrator.status(ctx, a.id)) {
            null -> {}
            Illustrator.Status.Choosing -> Busy(t("native.labs.image.illustrateChoosing"))
            is Illustrator.Status.Drawing -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                team.sopa.boar.labs.DesignerWait(st.style, Modifier.size(240.dp).clip(RoundedCornerShape(10.dp)))
                Busy(t("native.labs.image.illustrateDrawing", "subject" to st.subject))
            }
            is Illustrator.Status.Failed -> Text(st.message, style = Boar.type.caption, color = c.crimson400)
            // What was drawn and the style, at a glance; ⓘ opens the exact prompt, shape and quality, seed and time (Oct 18).
            is Illustrator.Status.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var details by remember(a.id) { mutableStateOf(false) }
                val style = st.style?.let { k -> ImageLab.Style.entries.firstOrNull { it.key == k } }
                // Fresh from the sketch: the lines fade into the picture (a reopened chat shows it at once).
                var drawn by remember(st.file) { mutableStateOf(!Illustrator.justDrawn(a.id)) }
                androidx.compose.runtime.LaunchedEffect(st.file) { drawn = true }
                androidx.compose.animation.Crossfade(drawn, animationSpec = androidx.compose.animation.core.tween(team.sopa.boar.labs.revealMs()), label = "reveal") { done ->
                    if (done) Picture(st.file, Modifier.size(240.dp).clip(RoundedCornerShape(10.dp)).clickable { details = !details })
                    else team.sopa.boar.labs.DesignerWait(st.style, Modifier.size(240.dp).clip(RoundedCornerShape(10.dp)))
                }
                // Only ⓘ and ✕ under the picture (adam, Nov 11); what was drawn, the style, the prompt, size, seed and time are in ⓘ.
                Row(Modifier.widthIn(max = 240.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text("ⓘ", style = Boar.type.body, color = if (details) c.textAccentCyan else c.textMuted,
                        modifier = Modifier.clip(CircleShape).clickable(onClickLabel = t("native.labs.image.details")) { details = !details }.padding(horizontal = 12.dp, vertical = 8.dp))
                    Text("✕", style = Boar.type.body, color = c.textMuted,
                        modifier = Modifier.clip(CircleShape).clickable(onClickLabel = t("native.a11y.close")) { Illustrator.forget(a.id) }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                if (details) Column(Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("🎨 " + st.subject, style = Boar.type.caption, color = c.textSecondary)
                    if (style != null && style != ImageLab.Style.NONE) Text(t("native.labs.image.style.${style.key}"), style = Boar.type.caption, color = c.textAccentCyan)
                    Text(listOfNotNull(
                        style?.prompt(st.subject) ?: st.subject,
                        st.size?.let { ImageLab.Canvas.of(it) }?.let { shapeQuality(it) },
                        st.seed?.let { t("native.labs.image.seed", "seed" to it) },
                        st.seconds?.let { String.format(Locale.US, "%.1f s", it) },
                    ).joinToString(" · "), style = Boar.type["mono.xs"], color = c.textDim)
                }
            }
        }
    }
}

/** "Square", "Portrait", "Wide". */
@Composable
private fun shapeName(shape: DesignerCanvas.Shape) = t("native.labs.image.shape.${shape.key}")

/** "Low", "Medium", "High": the Quality tray's pills and the "i" sheet. */
@Composable
private fun qualityName(q: DesignerCanvas.Quality) = t("native.labs.image.quality.${q.key}")

/** "Low quality": the caption before a level is timed, and inside other words. */
@Composable
private fun qualityLong(q: DesignerCanvas.Quality) = t("native.labs.image.qualityLong.${q.key}")

/** "Square, low quality": a size in words, never in pixels (Illustrate, the media manager, the answer card). */
@Composable
private fun shapeQuality(c: ImageLab.Canvas) = t("native.labs.image.shapeQuality", "shape" to shapeName(c.shape), "quality" to qualityLong(c.quality).lowercase(Locale.getDefault()))

@Composable
private fun styleName(key: String?) = t("native.labs.image.style.${key ?: "none"}")

/**
 * The canvas (adam's mock-up, Nov 13): the picture fills the space under the header at the picked shape, with one
 * caption line under it; then the history strip and the chat's own input bar (adam, Nov 12: "use the same prompt input
 * component for designer"): its left round button opens Designer's drawer (Style, Shape, Quality and ★ Default), the
 * field says "What should I draw?" and has the mic, and the right round button draws (Stop while drawing). The plugin's
 * own settings are behind the settings icon at the end of the caption line.
 */
@Composable
fun DesignerCanvasScreen(state: ImageLabState, vm: ImageLabViewModel, onManage: () -> Unit) {
    val c = Boar.colors
    val ctx = LocalContext.current
    var subject by rememberSaveable { mutableStateOf("") }
    // Not saved and restored: the drawer opens only from its button, never by itself (t9 1721).
    var drawer by remember { mutableStateOf(false) }
    var info by rememberSaveable { mutableStateOf(false) }
    var full by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val busy = state.phase != null
    val ready = state.engineFound && state.modelPresent
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    // A drawing started (Draw, or 4 variations from the "i" sheet): the drawer closes, the bar shows Stop.
    androidx.compose.runtime.LaunchedEffect(busy) { if (busy) drawer = false }
    // Shown with what it was drawn from: tapping a drawing in the strip puts its words back in the prompt bar.
    val showFile: (File) -> Unit = { f -> if (!busy) { vm.show(f); ImageLab.recipeOf(f)?.let { subject = it.subject } } }
    val voiceEnabled = remember { team.sopa.boar.app.BoarServices.get(ctx).settings.voiceInputEnabled }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().background(c.bgSubtle).padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CanvasArt(state, vm, ready, busy, onInfo = { info = true }, onFull = { full = true }, onShow = showFile)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(caption(state, ready), style = Boar.type.caption, color = if (state.error != null) c.crimson400 else c.textMuted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(team.sopa.boar.R.drawable.ic_settings_tweaks, t("native.labs.image.settingsTitle"), c.textMuted) { settings = true }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        Column(Modifier.fillMaxWidth().background(c.bgCardElevated)) {
            if (state.gallery.isNotEmpty()) HistoryStrip(state, onShow = showFile, onManage = onManage)
            // Kept above the bar (adam, Nov 12).
            state.lastChat?.takeIf { subject.isBlank() && !busy && ready }?.let { title ->
                Text(t("native.labs.image.fromChat", "title" to title), style = Boar.type.caption, color = c.textAccentCyan, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp).clip(RoundedCornerShape(6.dp)).clickable { subject = title }.padding(horizontal = 6.dp, vertical = 6.dp))
            }
        }
        val drawerLabel = t("native.labs.image.drawer")
        // The chat's bar, shared (team.sopa.boar.ui.chat.PromptBar): Draw never queues, so it is Stop all the while it draws.
        team.sopa.boar.ui.chat.PromptBar(subject, { subject = it }, answering = busy,
            onSend = { vm.generate(subject); keyboard?.hide(); focus.clearFocus() }, onStop = { vm.cancel() }, voiceEnabled = voiceEnabled,
            placeholder = t("native.labs.image.placeholder"), onLeft = { if (ready && !busy) drawer = true }, leftLabel = drawerLabel,
            left = { PackIcon(team.sopa.boar.R.drawable.ic_designer_style, 22.dp, tint = if (ready && !busy) c.textHeading else c.textDim, description = drawerLabel) },
            sendIcon = team.sopa.boar.R.drawable.ic_designer_draw, sendLabel = t("native.labs.image.generate"), queues = false, enabled = ready)
    }
    if (drawer) DesignerDrawer(state, vm) { drawer = false }
    if (info) state.image?.let { f -> InfoSheet(f, state, vm) { info = false } } ?: run { info = false }
    if (full) state.image?.let { f -> FullView(f) { full = false } } ?: run { full = false }
    if (settings) SettingsSheet(state, vm) { settings = false }
}

/** The canvas's one line: "Style · Shape · N s" (this phone's average at that size, else "Low quality"), or what's happening. */
@Composable
private fun caption(state: ImageLabState, ready: Boolean): String {
    state.error?.let { return it }
    if (!state.engineFound) return t("native.labs.image.noEngine")
    if (!state.modelPresent) return t("native.labs.image.noModelCaption")
    val v = state.variations
    if (state.phase != null) return if (v != null) t("native.labs.image.variationOf", "n" to (v.size + 1).coerceAtMost(DesignerCanvas.VARIATIONS), "total" to DesignerCanvas.VARIATIONS)
        else t("native.labs.image.phase.${state.phase}")
    if (v != null) return t("native.labs.image.variationsPick")
    val shown = state.image?.let { ImageLab.canvasOf(it) }
    return if (shown != null && shownFits(state)) DesignerCanvas.caption(styleName(state.recipe?.style ?: state.style.key), shapeName(shown.shape), ImageLab.expectedSeconds(shown), qualityLong(shown.quality))
    else DesignerCanvas.caption(styleName(state.style.key), shapeName(state.size.shape), null, qualityLong(state.size.quality))
}

/** The shown picture has the picked shape and quality. */
private fun shownFits(state: ImageLabState): Boolean =
    state.image?.let { ImageLab.canvasOf(it) }?.let { ImageLab.Canvas.offered(it.key) } == state.size

/** What the canvas shows, for the crossfade from the sketch to the picture. */
private sealed interface Art {
    data object Waiting : Art
    data class Shown(val file: File) : Art
    data object Empty : Art
}

@Composable
private fun CanvasArt(state: ImageLabState, vm: ImageLabViewModel, ready: Boolean, busy: Boolean, onInfo: () -> Unit, onFull: () -> Unit, onShow: (File) -> Unit) {
    val c = Boar.colors
    val frame = Modifier.aspectRatio(state.size.w.toFloat() / state.size.h)
    when {
        !state.engineFound -> EmptyFrame(frame) { Text(t("native.labs.image.noEngine"), style = Boar.type.body, color = c.textSecondary, textAlign = TextAlign.Center) }
        // No model yet: the frame holds the download (one tap, its percentage), with the model's licence note.
        !state.modelPresent -> EmptyFrame(frame) {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("native.labs.image.noModelTitle"), style = Boar.type.titleSm, color = c.textHeading, textAlign = TextAlign.Center)
                Text(t("native.labs.image.needModel"), style = Boar.type.caption, color = c.textMuted, textAlign = TextAlign.Center)
                if (state.downloadPct != null) Busy(t("native.labs.image.downloading", "pct" to state.downloadPct))
                else Pill(t("native.labs.image.download"), primary = true) { vm.download() }
            }
        }
        // 4 variations: a 2×2 that fills one by one; a finished one opens on the canvas when tapped.
        state.variations != null -> {
            val v = state.variations
            Column(frame, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until 2) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until 2) {
                        val i = row * 2 + col
                        val cell = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(6.dp))
                        when {
                            i < v.size -> Picture(v[i], cell.clickable(enabled = !busy, onClickLabel = t("native.labs.image.variationOpen", "n" to i + 1)) { onShow(v[i]) })
                            busy -> team.sopa.boar.labs.DesignerWait(state.style.key, cell)
                            else -> Box(cell.background(c.bgCard))
                        }
                    }
                }
            }
        }
        else -> {
            val art: Art = when {
                busy -> Art.Waiting
                state.image != null && shownFits(state) -> Art.Shown(state.image)
                else -> Art.Empty
            }
            // The sketch while it draws, then the picture takes its place (a crossfade; instant with animations off).
            androidx.compose.animation.Crossfade(art, animationSpec = androidx.compose.animation.core.tween(team.sopa.boar.labs.revealMs()), label = "reveal") { a ->
                when (a) {
                    // The phase is in the caption line under it, so the sketch carries no label of its own.
                    Art.Waiting -> team.sopa.boar.labs.DesignerWait(state.style.key, frame.clip(RoundedCornerShape(6.dp)))
                    is Art.Shown -> Box(Modifier.aspectRatio(aspectOf(a.file)).clip(RoundedCornerShape(6.dp))) {
                        Picture(a.file, Modifier.fillMaxSize())
                        Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoundOverlay(team.sopa.boar.R.drawable.ic_info, t("native.labs.image.infoButton"), onInfo)
                            RoundOverlay(team.sopa.boar.R.drawable.ic_designer_full, t("native.labs.image.fullButton"), onFull)
                        }
                        // A style picked since: what the next drawing will be (the picture isn't redrawn).
                        val drawnStyle = state.recipe?.style
                        if (drawnStyle != null && drawnStyle != state.style.key) Text(t("native.labs.image.next", "style" to styleName(state.style.key)),
                            style = Boar.type.caption, color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                .background(Color(0xB3080A18), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp))
                    }
                    Art.Empty -> EmptyFrame(frame) {
                        Text(shapeName(state.size.shape) + " · " + qualityLong(state.size.quality),
                            style = Boar.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
                        Text(t("native.labs.image.emptyCanvas"), style = Boar.type.caption, color = c.textMuted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** An empty artboard: a dashed outline at the picked shape, its words in the middle. */
@Composable
private fun EmptyFrame(modifier: Modifier, content: @Composable () -> Unit) {
    val line = Boar.colors.borderDefault
    Column(modifier.drawBehind {
        drawRoundRect(line, cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
    }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

/** The round "i" and full-screen buttons on the picture: light on a dark disc, so they read on any drawing. */
@Composable
private fun RoundOverlay(icon: Int, label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99080A18)).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        .clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        PackIcon(icon, 20.dp, tint = Color.White, description = label)
    }
}

@Composable
private fun IconButton(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        PackIcon(icon, 20.dp, tint = tint, description = label)
    }
}

/** The latest 12 drawings; "All N ›" opens the media manager. */
@Composable
private fun HistoryStrip(state: ImageLabState, onShow: (File) -> Unit, onManage: () -> Unit) {
    val c = Boar.colors
    LazyRow(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 10.dp)) {
        items(state.gallery.take(STRIP), key = { it.path }) { f ->
            val sel = f == state.image
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                .border(if (sel) 2.dp else 1.dp, if (sel) c.emerald400 else c.borderSubtle, RoundedCornerShape(6.dp))
                .clickable(onClickLabel = ImageLab.recipeOf(f)?.subject) { onShow(f) }) {
                Picture(f, Modifier.fillMaxSize(), thumb = true)
            }
        }
        item {
            Box(Modifier.height(48.dp).clip(RoundedCornerShape(6.dp)).background(c.bgCard).border(1.dp, c.borderSubtle, RoundedCornerShape(6.dp))
                .clickable(onClick = onManage).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Text(t("native.labs.image.all", "count" to state.gallery.size), style = Boar.type.caption, color = c.textPrimary, maxLines = 1)
            }
        }
    }
}

/**
 * Designer's drawer (adam, Nov 12: "the drawer instead of the tab buttons"), opened by the bar's left round button, as
 * the chat's answer-style sheet is: the sections in DesignerCanvas.Section, Style, Shape and Quality, and "★ Default"
 * (style, shape and quality together). Every pick applies at once; the canvas reshapes behind it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DesignerDrawer(state: ImageLabState, vm: ImageLabViewModel, onClose: () -> Unit) {
    val c = Boar.colors
    val isDefault = state.style == state.defaultStyle && state.size == state.defaultSize
    // Fully open or closed, never half: half open put the Quality chips over the input bar, so a tap meant for the field
    // could pick a quality (the F3, Nov 12: a drawing came out with settings nobody chose). While it closes, its chips
    // take no taps, so a tap on a closing sheet never reaches a chip.
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val live = sheet.targetValue != androidx.compose.material3.SheetValue.Hidden
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = c.bgCardElevated) {
        Box {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("native.labs.image.drawer"), style = Boar.type.titleSm, color = c.textHeading, modifier = Modifier.weight(1f))
                Pill(t("native.labs.image.default"), selected = isDefault) { vm.setDefaults() }
            }
            for (section in DesignerCanvas.Section.entries) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("native.labs.image.section.${section.key}"), style = Boar.type.caption.copy(letterSpacing = 1.sp), color = c.textMuted)
                when (section) {
                    DesignerCanvas.Section.STYLE -> StyleSection(state, vm)
                    DesignerCanvas.Section.SHAPE -> ShapeSection(state, vm)
                    DesignerCanvas.Section.QUALITY -> QualitySection(state, vm)
                }
            }
        }
        if (!live) Box(Modifier.matchParentSize().pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        })
        }
    }
}

/** Style: the real styles as a carousel of small tiles; tapping the selected one again leaves no style. */
@Composable
private fun StyleSection(state: ImageLabState, vm: ImageLabViewModel) {
    val c = Boar.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ImageLab.Style.entries.filter { it != ImageLab.Style.NONE }) { s ->
            val on = s == state.style
            val name = styleName(s.key)
            Column(Modifier.width(72.dp).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = name) { vm.setStyle(if (on) ImageLab.Style.NONE else s) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                StyleSwatch(s.key, Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).border(2.dp, if (on) c.emerald400 else Color.Transparent, RoundedCornerShape(8.dp)))
                Text(name, style = Boar.type.caption, color = if (on) c.textAccentEmerald else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Text(t(if (state.style == ImageLab.Style.NONE) "native.labs.image.styleHintNone" else "native.labs.image.styleHint"), style = Boar.type.caption, color = c.textMuted)
}

/** A style's tile, for now its paper and ink (Sketch.palette) with a sun and two hills: no picture assets yet. */
@Composable
private fun StyleSwatch(key: String, modifier: Modifier) {
    val p = remember(key) { team.sopa.boar.engine.labs.Sketch.palette(key) }
    androidx.compose.foundation.Canvas(modifier.background(Color(p.paper))) {
        val ink = Color(p.ink)
        val w = size.width; val h = size.height; val sw = w / 30f
        drawCircle(ink, radius = w * 0.1f, center = Offset(w * 0.7f, h * 0.3f), style = Stroke(sw))
        val far = androidx.compose.ui.graphics.Path().apply { moveTo(0f, h * 0.62f); quadraticTo(w * 0.3f, h * 0.42f, w * 0.55f, h * 0.6f); quadraticTo(w * 0.8f, h * 0.72f, w, h * 0.55f) }
        val near = androidx.compose.ui.graphics.Path().apply { moveTo(0f, h * 0.82f); quadraticTo(w * 0.4f, h * 0.68f, w * 0.65f, h * 0.8f); quadraticTo(w * 0.85f, h * 0.88f, w, h * 0.78f) }
        drawPath(far, ink.copy(alpha = 0.6f), style = Stroke(sw, cap = StrokeCap.Round))
        drawPath(near, ink, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** Shape: Square, Portrait and Wide as frames, nothing else (quality is its own section); the canvas reshapes at once. */
@Composable
private fun ShapeSection(state: ImageLabState, vm: ImageLabViewModel) {
    val c = Boar.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.Bottom) {
        for (shape in DesignerCanvas.Shape.entries) {
            val on = shape == state.size.shape
            val (fw, fh) = when (shape) { DesignerCanvas.Shape.SQUARE -> 34 to 34; DesignerCanvas.Shape.PORTRAIT -> 28 to 38; DesignerCanvas.Shape.WIDE -> 52 to 24 }
            val name = shapeName(shape)
            Column(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = name) { vm.setShape(shape) }.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(fw.dp, fh.dp).background(if (on) c.emeraldBgSubtle else Color.Transparent, RoundedCornerShape(3.dp))
                    .border(2.dp, if (on) c.emerald400 else c.textMuted, RoundedCornerShape(3.dp)))
                Text(name, style = Boar.type.caption, color = if (on) c.textAccentEmerald else c.textMuted)
            }
        }
    }
}

/**
 * Quality: Low, Medium and High, the three sizes of the picked shape, each with this phone's own seconds once measured.
 * The hint speaks of the picked level only: "not timed yet" never sits beside a level that shows its seconds (F3, Nov 13).
 * No pixel numbers.
 */
@Composable
private fun QualitySection(state: ImageLabState, vm: ImageLabViewModel) {
    val c = Boar.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        for (q in DesignerCanvas.Quality.entries) {
            val size = ImageLab.Canvas.of(state.size.shape, q)
            Pill(qualityLabel(size), selected = state.size == size) { vm.setQuality(q) }
        }
    }
    val hint = if (ImageLab.expectedSeconds(state.size) != null) t("native.labs.image.qualityHint")
        else t("native.labs.image.qualityHintUntimed", "level" to qualityLong(state.size.quality))
    Text(hint, style = Boar.type.caption, color = c.textMuted)
}

/** "Medium", or "High · ~14 s" once this phone has drawn at that size. */
@Composable
private fun qualityLabel(size: ImageLab.Canvas): String =
    ImageLab.expectedSeconds(size)?.let { t("native.labs.image.qualitySeconds", "level" to qualityName(size.quality), "s" to it) } ?: qualityName(size.quality)

@Composable
private fun Pill(label: String, selected: Boolean = false, primary: Boolean = false, onClick: () -> Unit) {
    val c = Boar.colors
    Text(label, style = Boar.type.caption, maxLines = 1, fontWeight = if (primary) FontWeight.Bold else null,
        color = when { primary -> c.textInverse; selected -> c.textAccentEmerald; else -> c.textPrimary },
        modifier = Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50)).background(if (primary) c.amber400 else c.bgCard)
            .border(1.dp, when { primary -> c.amber400; selected -> c.emerald400; else -> c.borderDefault }, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}

/**
 * The "i" sheet: what the picture was drawn with (words, style, shape and speed, time), then Draw again (replaces it),
 * 4 variations and Delete. Nothing leaves the plugin: no Share or Save to Photos (adam, Nov 13).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(f: File, state: ImageLabState, vm: ImageLabViewModel, onClose: () -> Unit) {
    val c = Boar.colors
    val r = state.recipe
    val drawnAt = ImageLab.canvasOf(f)
    val average = drawnAt?.let { ImageLab.expectedSeconds(it) }
    // This drawing's own time when it was just made, else this phone's average at its size.
    val seconds = state.seconds?.let { kotlin.math.ceil(it).toInt() } ?: average
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, containerColor = c.bgCardElevated) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("native.labs.image.infoTitle"), style = Boar.type.titleSm, color = c.textHeading, modifier = Modifier.padding(bottom = 6.dp))
            (r?.subject ?: state.subject)?.let { Fact(t("native.labs.image.words"), it) }
            Fact(t("native.labs.image.styleLabel"), styleName(r?.style ?: state.style.key))
            drawnAt?.let { Fact(t("native.labs.image.shapeLabel"), shapeName(it.shape)); Fact(t("native.labs.image.qualityLabel"), qualityName(it.quality)) }
            seconds?.let { Fact(t("native.labs.image.time"), "$it s") }
            Spacer(Modifier.height(6.dp))
            if (state.subject != null) {
                Action(t("native.labs.image.drawAgain"), t("native.labs.image.drawAgainNote")) { onClose(); vm.regenerate() }
                Action(t("native.labs.image.variations"),
                    DesignerCanvas.variationsSeconds(average)?.let { t("native.labs.image.variationsTime", "s" to it) } ?: t("native.labs.image.variationsNote")) { onClose(); vm.variations() }
            }
            Action(t("native.labs.image.delete"), null, danger = true) { onClose(); vm.delete(f) }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) = Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(label, style = Boar.type.body, color = Boar.colors.textMuted, modifier = Modifier.widthIn(min = 64.dp))
    Text(value, style = Boar.type.body, color = Boar.colors.textPrimary, modifier = Modifier.weight(1f))
}

@Composable
private fun Action(label: String, note: String?, danger: Boolean = false, onClick: () -> Unit) {
    val c = Boar.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderSubtle))
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Boar.type.body, color = if (danger) c.crimson400 else c.textPrimary, modifier = Modifier.weight(1f))
        note?.let { Text(it, style = Boar.type.caption, color = c.textMuted) }
    }
}

/** The picture alone on black, at its own shape; a tap anywhere closes it. */
@Composable
private fun FullView(f: File, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClickLabel = t("native.a11y.close"), onClick = onClose), contentAlignment = Alignment.Center) {
            Picture(f, Modifier.fillMaxWidth().aspectRatio(aspectOf(f)), fit = true)
        }
    }
}

/**
 * The plugin's own settings, which sat on the page before the canvas (Nov 13): the waiting animation, removing the
 * model, How it works, and About this plugin.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: ImageLabState, vm: ImageLabViewModel, onClose: () -> Unit) {
    val c = Boar.colors
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    if (confirmRemove) androidx.compose.material3.AlertDialog({ confirmRemove = false },
        text = { Text(t("native.labs.image.removeConfirm")) },
        confirmButton = { androidx.compose.material3.TextButton({ confirmRemove = false; vm.removeModel() }) { Text(t("native.labs.image.remove"), color = c.crimson400) } },
        dismissButton = { androidx.compose.material3.TextButton({ confirmRemove = false }) { Text(t("common.cancel")) } })
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, containerColor = c.bgCardElevated) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("native.labs.image.settingsTitle"), style = Boar.type.titleSm, color = c.textHeading)
            // The same choice as Settings › Boar Labs: the sketch (Designer) and trail (elsewhere), or the Matrix rain everywhere.
            team.sopa.boar.ui.models.ChipRow(t("native.labs.wait.setting"),
                listOf(team.sopa.boar.labs.LabPrefs.WAIT_TRAIL to t("native.labs.image.waitSketch"), team.sopa.boar.labs.LabPrefs.WAIT_MATRIX to t("native.labs.wait.matrix")),
                team.sopa.boar.labs.LabPrefs.waitAnimation) { team.sopa.boar.labs.LabPrefs.waitAnimation = it }
            // Gives the model's space back; the drawings stay. Drawing again needs the download once more.
            if (state.modelPresent && state.phase == null && state.downloadPct == null) Action(t("native.labs.image.removeModel"), null) { confirmRemove = true }
            team.sopa.boar.labs.PluginPage(null, null, null, emptyList(), t("native.labs.image.how"))
            team.sopa.boar.labs.AboutPluginFold(ImageGenLab)
        }
    }
}

/** Strip length: the latest drawings; the rest in the media manager. */
private const val STRIP = 12
/** Drawings per page in the media manager. */
private const val PAGE = 40

/** Square thumbnails, [columns] per row; [selected] get a green border. */
@Composable
private fun Thumbs(files: List<File>, columns: Int, selected: Set<File>, onTap: (File) -> Unit) {
    val c = Boar.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        files.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { f ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                        .then(if (f in selected) Modifier.border(2.dp, c.emerald400, RoundedCornerShape(6.dp)) else Modifier)
                        .clickable { onTap(f) }) {
                        Picture(f, Modifier.fillMaxSize(), thumb = true)
                        if (f in selected && columns < 5) Text("✓", color = c.textHeading, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(c.emerald600, RoundedCornerShape(10.dp)).padding(horizontal = 6.dp))
                    }
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Every image on this phone: select and delete. Nothing leaves the lab, so no share or export. */
@Composable
private fun MediaManager(files: List<File>, vm: ImageLabViewModel, onBack: () -> Unit) {
    val c = Boar.colors
    var picked by remember { mutableStateOf(setOf<File>()) }
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("‹ " + t("common.back"), style = Boar.type.body, color = c.textAccentEmerald, modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            Text(t("native.labs.image.managerCount", "count" to files.size, "mb" to String.format(Locale.US, "%.1f", files.sumOf { it.length() } / 1048576.0)),
                style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.weight(1f))
            Text(t(if (picked.size == files.size && files.isNotEmpty()) "native.labs.image.selectNone" else "native.labs.image.selectAll"), style = Boar.type.caption, color = c.textAccentCyan,
                modifier = Modifier.minimumInteractiveComponentSize().clickable { picked = if (picked.size == files.size) emptySet() else files.toSet() }.padding(4.dp))
        }
        Text(t("native.labs.image.managerNote"), style = Boar.type.caption, color = c.textMuted)
        if (files.isEmpty()) Text(t("native.labs.image.managerEmpty"), style = Boar.type.body, color = c.textSecondary)
        var viewing by remember { mutableStateOf<File?>(null) }
        // Pages of 40: a phone with hundreds of drawings lays out and decodes only what was asked for.
        var shown by remember { mutableStateOf(PAGE) }
        Thumbs(files.take(shown), columns = 4, selected = picked) { f -> picked = if (f in picked) picked - f else picked + f; viewing = if (f in picked) f else picked.lastOrNull() }
        if (files.size > shown) Text(t("native.labs.image.showPage", "n" to minOf(PAGE, files.size - shown)), style = Boar.type.caption, color = c.textAccentCyan,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { shown += PAGE }.padding(vertical = 6.dp))
        if (picked.isNotEmpty()) {
            if (confirm) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("native.labs.image.deleteConfirm", "count" to picked.size), style = Boar.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
                Button(t("common.cancel")) { confirm = false }
                Text(t("native.labs.image.delete"), style = Boar.type.titleSm.copy(fontSize = 13.sp), color = c.crimson400,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, c.crimson400, RoundedCornerShape(8.dp))
                        .clickable { vm.deleteAll(picked); picked = emptySet(); confirm = false }.padding(horizontal = 16.dp, vertical = 10.dp))
            } else Text(t("native.labs.image.deleteSelected", "count" to picked.size), style = Boar.type.titleSm.copy(fontSize = 13.sp), color = c.crimson400,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, c.crimson400, RoundedCornerShape(8.dp)).clickable { confirm = true }.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        // The selected drawing in full, at its own shape, with what it was drawn from.
        viewing?.takeIf { it in picked && it.isFile }?.let { f ->
            Picture(f, Modifier.fillMaxWidth().aspectRatio(aspectOf(f)).clip(RoundedCornerShape(10.dp)))
            ImageLab.recipeOf(f)?.let { r ->
                // Shape and speed in words: no pixel numbers on screen (Nov 13).
                Text("${r.subject} · ${t("native.labs.image.style.${r.style}")} · ${ImageLab.Canvas.of(r.size)?.let { shapeQuality(it) } ?: ""} · ${t("native.labs.image.seed", "seed" to r.seed)}",
                    style = Boar.type["mono.xs"], color = c.textDim)
            }
        }
    }
}

@Composable
private fun Picture(f: File, modifier: Modifier, thumb: Boolean = false, fit: Boolean = false) {
    val bmp by produceState<ImageBitmap?>(null, f.path, f.lastModified(), thumb) {
        // A grid tile decodes at a quarter of the size (about 1/16 of the memory); the full view decodes it whole.
        val opts = BitmapFactory.Options().apply { if (thumb) inSampleSize = 4 }
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap() }.getOrNull() }
    }
    Box(modifier.background(Boar.colors.bgCard)) { bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = if (fit) ContentScale.Fit else ContentScale.Crop) } }
}

/** Width over height of a saved drawing, from its file name ("…-384x512.png"); 1 for a square or an older file. */
private fun aspectOf(f: File): Float =
    Regex("-(\\d+)x(\\d+)\\.png$").find(f.name)?.destructured?.let { (w, h) -> w.toFloat() / h.toFloat() } ?: 1f

@Composable
private fun Button(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Boar.colors
    Text(label, style = Boar.type.titleSm.copy(fontSize = 13.sp), color = if (enabled) c.textAccentEmerald else c.textDim,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.emeraldBgSubtle).border(1.dp, c.emeraldBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
private fun Busy(label: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    CircularProgressIndicator(Modifier.size(16.dp), color = Boar.colors.emerald400, strokeWidth = 1.5.dp)
    Text(label, style = Boar.type.caption, color = Boar.colors.textAccentEmerald)
}

/**
 * Illustrate's sheet, opened by ✎ (adam, Nov 12): a style, a shape and a quality to pick (the canvas's words, Nov 13: no
 * pixel numbers), one Draw button that uses them, and "Remember" to keep them as the next default. Reset goes back to
 * Engraving, Square, Medium.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun IllustrateSheet(a: team.sopa.boar.plugins.AnswerRef, onClose: () -> Unit) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val notReady = t("native.labs.image.illustrateNotReady")
    var style by remember { mutableStateOf(ImageLab.defaultStyle()) }
    var shape by remember { mutableStateOf(ImageLab.defaultCanvas().shape) }
    var quality by remember { mutableStateOf(ImageLab.defaultCanvas().quality) }
    var keep by remember { mutableStateOf(false) }
    val size = ImageLab.Canvas.of(shape, quality)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, containerColor = c.bgCardElevated) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("native.labs.image.sheetTitle"), style = Boar.type["mono.xs"], color = c.textDim, fontWeight = FontWeight.Bold)
            // One Draw button, with what's picked here (adam, Nov 12): the defaults are the one-tap row in the plugins sheet.
            team.sopa.boar.ui.models.ChipRow(t("native.labs.image.styleLabel"), ImageLab.Style.entries.filter { it != ImageLab.Style.NONE }.map { it to styleName(it.key) }, style, sideways = true) { style = it }
            team.sopa.boar.ui.models.ChipRow(t("native.labs.image.shapeLabel"), DesignerCanvas.Shape.entries.map { it to shapeName(it) }, shape, sideways = true) { shape = it }
            team.sopa.boar.ui.models.ChipRow(t("native.labs.image.qualityLabel"), DesignerCanvas.Quality.entries.map { it to qualityLabel(ImageLab.Canvas.of(shape, it)) }, quality, sideways = true) { quality = it }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Checkbox(keep, { keep = it })
                Text(t("native.labs.image.remember"), style = Boar.type.body, color = c.textPrimary, modifier = Modifier.weight(1f).clickable { keep = !keep })
                Text(t("native.labs.image.reset"), style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { ImageLab.resetDefaults(); style = ImageLab.defaultStyle(); shape = ImageLab.defaultCanvas().shape; quality = ImageLab.defaultCanvas().quality }.padding(6.dp))
            }
            SheetButton(t("native.labs.image.drawPicked", "style" to styleName(style.key), "size" to shapeQuality(size)), primary = true) {
                if (keep) ImageLab.setDefaults(style, size)
                onClose(); Illustrator.start(ctx, a, notReady, style, size)
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val c = Boar.colors
    Text(label, style = Boar.type.body, color = if (primary) c.textInverse else c.textAccentEmerald, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (primary) c.emerald500 else c.emeraldBgSubtle)
            .border(1.dp, c.emeraldBorder, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp))
}
