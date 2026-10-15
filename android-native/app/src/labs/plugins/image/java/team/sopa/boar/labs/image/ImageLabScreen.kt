package team.sopa.boar.labs.image

import androidx.compose.runtime.remember
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.io.File
import java.util.Locale

/** Boar Designer (was Image Gen) as a Boar Labs plugin: the screen stays on while it draws or downloads, and back waits for the drawing. */
object ImageGenLab : team.sopa.boar.labs.LabPlugin {
    override val id = "image"
    override val bundledAssets = listOf(ImageLab.MODEL)

    override suspend fun selfTest(context: android.content.Context, args: Map<String, String>, say: (String) -> Unit): Boolean {
        DesignerSelfTest.run(context, args, say); return true
    }

    /** Chats erased: their illustrations go. Everything: every picture made here too (the model stays). */
    override fun eraseUserData(context: android.content.Context, scope: team.sopa.boar.plugins.UserData.Scope) {
        Illustrator.eraseAll(context)
        if (scope == team.sopa.boar.plugins.UserData.Scope.EVERYTHING) SdRunner.outDir(context).listFiles().orEmpty().forEach { it.delete() }
    }
    override val emoji = "🎨"
    override val art = team.sopa.boar.R.drawable.truffle_retrowave
    override val status = team.sopa.boar.labs.LabStatus.ALPHA
    // Off at first launch (owner, Oct 20): its drawing model is a download.
    override val onByDefault = false

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
        if (state.phase != null) androidx.activity.compose.BackHandler { vm.cancel() }
        if (managing) {
            androidx.activity.compose.BackHandler { managing = false }
            MediaManager(state.gallery, vm) { managing = false }
        } else ImageLabScreen(state, vm) { managing = true }
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
        return listOf(team.sopa.boar.labs.LabAction("🎨", label, done = st is Illustrator.Status.Done, enabled = ready && !running, art = art) { Illustrator.start(ctx, a, notReady) })
    }

    /** The drawing (or its progress) under the answer, with what was drawn and how long it took. */
    @Composable
    override fun AnswerExtra(a: team.sopa.boar.plugins.AnswerRef) {
        val c = Boar.colors
        val ctx = LocalContext.current
        when (val st = Illustrator.status(ctx, a.id)) {
            null -> {}
            Illustrator.Status.Choosing -> Busy(t("native.labs.image.illustrateChoosing"))
            is Illustrator.Status.Drawing -> Busy(t("native.labs.image.illustrateDrawing", "subject" to st.subject))
            is Illustrator.Status.Failed -> Text(st.message, style = Boar.type.caption, color = c.crimson400)
            // What was drawn and the style, at a glance; ⓘ opens the exact prompt, size, seed and time (Oct 18).
            is Illustrator.Status.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var details by remember(a.id) { mutableStateOf(false) }
                val style = st.style?.let { k -> ImageLab.Style.entries.firstOrNull { it.key == k } }
                Picture(st.file, Modifier.size(240.dp).clip(RoundedCornerShape(10.dp)).clickable { details = !details })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎨 " + st.subject, style = Boar.type["mono.xs"], color = c.textSecondary, modifier = Modifier.weight(1f, fill = false))
                    if (style != null && style != ImageLab.Style.NONE) Text(t("native.labs.image.style.${style.key}"), style = Boar.type["mono.xs"].copy(fontSize = 10.sp), color = c.textAccentCyan,
                        modifier = Modifier.background(c.cyanBgSubtle, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                    Text("ⓘ", style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.clickable { details = !details }.padding(4.dp))
                    Text("✕", style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.clickable { Illustrator.forget(a.id) }.padding(4.dp))
                }
                if (details) Text(listOfNotNull(
                    style?.prompt(st.subject) ?: st.subject,
                    st.size?.let { ImageLab.Canvas.of(it)?.label ?: it },
                    st.seed?.let { t("native.labs.image.seed", "seed" to it) },
                    st.seconds?.let { String.format(Locale.US, "%.1f s", it) },
                ).joinToString(" · "), style = Boar.type["mono.xs"], color = c.textDim)
            }
        }
    }
}

/** Pick a style, type the subject, draw. The last images stay on the phone below. */
@Composable
fun ImageLabScreen(state: ImageLabState, vm: ImageLabViewModel, onManage: () -> Unit) {
    val c = Boar.colors
    val ctx = LocalContext.current
    var subject by rememberSaveable { mutableStateOf("") }
    val busy = state.phase != null
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    if (confirmRemove) androidx.compose.material3.AlertDialog({ confirmRemove = false },
        text = { Text(t("native.labs.image.removeConfirm")) },
        confirmButton = { androidx.compose.material3.TextButton({ confirmRemove = false; vm.removeModel() }) { Text(t("native.labs.image.remove"), color = c.crimson400) } },
        dismissButton = { androidx.compose.material3.TextButton({ confirmRemove = false }) { Text(t("common.cancel")) } })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("native.labs.image.intro"), style = Boar.type.caption, color = c.textMuted)
            when {
                !state.engineFound -> Text(t("native.labs.image.noEngine"), style = Boar.type.body, color = c.textSecondary)
                !state.modelPresent -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("native.labs.image.needModel"), style = Boar.type.body, color = c.textSecondary)
                    if (state.downloadPct != null) Busy(t("native.labs.image.downloading", "pct" to state.downloadPct))
                    else Button(t("native.labs.image.download")) { vm.download() }
                }
                else -> {
                    Label(t("native.labs.image.style"))
                    // Tapping the selected style turns it off (NONE): the subject is then the whole prompt.
                    ImageLab.Style.entries.filter { it != ImageLab.Style.NONE }.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { s -> Chip(t("native.labs.image.style.${s.key}"), s == state.style, Modifier.weight(1f)) { vm.setStyle(if (s == state.style) ImageLab.Style.NONE else s) } }
                            repeat(4 - row.size) { Box(Modifier.weight(1f)) } // a short last row keeps the chips' width
                        }
                    }
                    Label(t("native.labs.image.size"))
                    // One row per shape. The phone's own average once it has drawn at that size; until then a square shows the
                    // conservative estimate and the new shapes their size alone.
                    ImageLab.CANVASES.groupBy { it.shape }.forEach { (shape, canvases) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("native.labs.image.shape.$shape"), style = Boar.type.caption, color = c.textMuted, modifier = Modifier.weight(0.8f))
                            canvases.forEach { cv ->
                                val label = ImageLab.expectedSeconds(cv)?.let { "${cv.label}\n~${it}s" }
                                    ?: if (cv.w == cv.h && cv.w in ImageLab.SIZES) t("native.labs.image.size.${cv.w}") else cv.label
                                Chip(label, cv == state.size, Modifier.weight(1f), lines = 2) { vm.setSize(cv) }
                            }
                            repeat(3 - canvases.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                    // One line in one style either way, so picking a style or size never moves the screen.
                    val isDefault = state.style == state.defaultStyle && state.size == state.defaultSize
                    val combo = t("native.labs.image.style.${state.style.key}") + " · ${state.size.label}"
                    Text(if (isDefault) t("native.labs.image.isDefault", "combo" to combo) else t("native.labs.image.setDefault", "combo" to combo),
                        style = Boar.type.caption, color = if (isDefault) c.textDim else c.textAccentCyan, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(enabled = !isDefault) { vm.setDefaults() }.padding(vertical = 4.dp))
                    Label(t("native.labs.image.subject"))
                    BasicTextField(subject, { subject = it }, singleLine = true, enabled = !busy,
                        textStyle = Boar.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(c.textAccentEmerald),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { vm.generate(subject) }),
                        modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(8.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(8.dp)).padding(12.dp),
                        decorationBox = { inner -> if (subject.isEmpty()) Text(t("native.labs.image.placeholder"), style = Boar.type.body, color = c.textDim); inner() })
                    state.lastChat?.takeIf { it != subject && !busy }?.let { title ->
                        Text(t("native.labs.image.fromChat", "title" to title), style = Boar.type.caption, color = c.textAccentCyan,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { subject = title }.padding(vertical = 4.dp))
                    }
                    Text(state.style.prompt(subject.ifBlank { "…" }), style = Boar.type["mono.xs"], color = c.textDim)
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Busy(t("native.labs.image.phase.${state.phase}"))
                        Button(t("common.cancel")) { vm.cancel() }
                    } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(t("native.labs.image.generate"), enabled = subject.isNotBlank()) { vm.generate(subject) }
                        // Seed lock: the next drawing reuses the shown one's seed, so changed words keep its composition.
                        state.seed?.let { sd ->
                            Chip(if (state.seedLocked) t("native.labs.image.seedKeep", "seed" to sd) else t("native.labs.image.seedNew"), state.seedLocked, Modifier.weight(1f)) {
                                vm.setSeedLocked(!state.seedLocked)
                            }
                        }
                    }
                }
            }
            state.error?.let { Text(it, style = Boar.type.caption, color = c.crimson500) }
            // The picture's square is always there: raining code while empty or drawing, so nothing moves when a picture lands.
            val image = state.image.takeIf { !busy }
            if (image == null) team.sopa.boar.labs.MatrixRain(Modifier.fillMaxWidth().aspectRatio(state.size.w.toFloat() / state.size.h).clip(RoundedCornerShape(10.dp)),
                label = if (busy) t("native.labs.image.phase.${state.phase}") else null)
            image?.let { f ->
                // At the drawing's own shape: a portrait or banner isn't cropped to a square.
                Picture(f, Modifier.fillMaxWidth().aspectRatio(aspectOf(f)).clip(RoundedCornerShape(10.dp)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.seconds?.let { Text(String.format(Locale.US, "%.1f s", it), style = Boar.type["mono.xs"], color = c.textAccentCyan, modifier = Modifier.weight(1f)) }
                        ?: Box(Modifier.weight(1f))
                    if (state.subject != null) Button(t("native.labs.image.regenerate")) { vm.regenerate() }
                    Button(t("native.labs.image.delete")) { vm.delete(f) }
                }
            }
            if (state.gallery.isNotEmpty()) {
                Label(t("native.labs.image.gallery"))
                // The latest 25 as a 5×5 grid; the rest, and deleting, in the media manager.
                // Tapping a drawing puts back its subject, style, size and seed (prompt history).
                Thumbs(state.gallery.take(GRID), columns = 5, selected = setOfNotNull(state.image)) { f -> vm.show(f); ImageLab.recipeOf(f)?.let { subject = it.subject } }
                Text(t(if (state.gallery.size > GRID) "native.labs.image.showMore" else "native.labs.image.manage", "count" to state.gallery.size),
                    style = Boar.type.caption, color = c.textAccentCyan, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onManage).padding(vertical = 6.dp))
            }
            // Gives the model's space back; the drawings stay. Drawing again needs the download once more.
            if (!busy && state.downloadPct == null) Text(t("native.labs.image.removeModel"), style = Boar.type.caption, color = c.textMuted,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { confirmRemove = true }.padding(vertical = 6.dp))
        }
}

private const val GRID = 25
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
                modifier = Modifier.clickable { picked = if (picked.size == files.size) emptySet() else files.toSet() }.padding(4.dp))
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
                Text("${r.subject} · ${t("native.labs.image.style.${r.style}")} · ${ImageLab.Canvas.of(r.size)?.label ?: r.size} · seed ${r.seed}",
                    style = Boar.type["mono.xs"], color = c.textDim)
            }
        }
    }
}

@Composable
private fun Picture(f: File, modifier: Modifier, thumb: Boolean = false) {
    val bmp by produceState<ImageBitmap?>(null, f.path, f.lastModified(), thumb) {
        // A grid tile decodes at a quarter of the size (about 1/16 of the memory); the full view decodes it whole.
        val opts = BitmapFactory.Options().apply { if (thumb) inSampleSize = 4 }
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap() }.getOrNull() }
    }
    Box(modifier.background(Boar.colors.bgCard)) { bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
}

/** Width over height of a saved drawing, from its file name ("…-384x512.png"); 1 for a square or an older file. */
private fun aspectOf(f: File): Float =
    Regex("-(\\d+)x(\\d+)\\.png$").find(f.name)?.destructured?.let { (w, h) -> w.toFloat() / h.toFloat() } ?: 1f

@Composable
private fun Label(text: String) = Text(text, style = Boar.type["mono.xs"], color = Boar.colors.textDim, fontWeight = FontWeight.Bold)

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier, lines: Int = 1, onClick: () -> Unit) {
    val c = Boar.colors
    Text(label, style = Boar.type.caption.copy(fontSize = 12.sp), color = if (selected) c.textAccentEmerald else c.textSecondary, maxLines = lines, minLines = lines,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) c.emeraldBgSubtle else c.bgCard)
            .border(1.dp, if (selected) c.emeraldBorder else c.borderSubtle, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

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
