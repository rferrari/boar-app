package team.sopa.boar.labs.printer

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.ui.I18n
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.io.File

/**
 * Boar Press (was Field Printer): the research basket as a document. Answers come in from any chat ("＋ Basket"); here they're
 * reordered, edited or dropped, each gets at most one drawing (its own illustration, one from Image Gen, or none), and
 * Compile writes an A4 PDF to share or save.
 */
object PrinterLab : LabPlugin {
    override val id = "printer"

    /** The basket holds answers' text: it goes with the chats. */
    override fun eraseUserData(context: android.content.Context, scope: team.sopa.boar.plugins.UserData.Scope) {
        Basket.eraseAll(context)
    }
    override val emoji = "🖨️"
    override val art = team.sopa.boar.R.drawable.truffle_steampunk
    override val status = LabStatus.ALPHA
    // Off at first launch (owner, Oct 20): only the instant-answer labs start on.
    override val onByDefault = false

    /** Each answer's own illustration (🎨 under it in the chat), from Image Gen's record: answer id to picture. */
    private fun illustrations(ctx: android.content.Context): Map<String, File> = runCatching {
        val o = org.json.JSONObject(File(ctx.filesDir, "labs/illustrations.json").readText())
        o.keys().asSequence().mapNotNull { id -> File(o.getJSONObject(id).getString("file")).takeIf { it.isFile }?.let { id to it } }.toMap()
    }.getOrDefault(emptyMap())

    /** The drawing that prints with [item]: none if switched off, else the one picked, else its own illustration. */
    private fun drawingOf(item: Basket.Item, own: Map<String, File>): File? =
        if (item.imageOff) null else item.image?.let(::File)?.takeIf { it.isFile } ?: own[item.id]

    /** 📎 in the answer's 🧩 sheet: into the basket with its question, sources and chat title. */
    @Composable
    override fun answerActions(a: team.sopa.boar.plugins.AnswerRef): List<team.sopa.boar.labs.LabAction> {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        remember { Basket.load(ctx) }
        val added = Basket.items.collectAsState().value.any { it.id == a.id }
        return listOf(team.sopa.boar.labs.LabAction("📎", t(if (added) "native.labs.printer.inBasket" else "native.labs.printer.addToBasket"), done = added, art = art) {
            if (added) Basket.remove(a.id)
            else scope.launch(Dispatchers.IO) {
                val title = runCatching { team.sopa.boar.app.BoarServices.get(ctx).chats.listSessions().firstOrNull { it.id == a.sessionId }?.title }.getOrNull().orEmpty()
                Basket.add(ctx, Basket.Item(a.id, title, a.question, a.text, a.sources.map { Basket.Source(it.first, it.second) }, System.currentTimeMillis(), a.model, a.tokPerSec))
            }
        })
    }

    /** 📎 n in the chat header while the basket has something: opens the Printer. */
    @Composable
    override fun HeaderBadge(go: (String) -> Unit) {
        val c = Boar.colors
        val ctx = LocalContext.current
        remember { Basket.load(ctx) }
        val n = Basket.items.collectAsState().value.size
        if (n == 0) return
        // Never wider than "9+" and never on two lines: the header is tight with Large text.
        Text("📎 " + if (n > 9) "9+" else "$n", style = Boar.type["mono.xs"], color = c.textAccentEmerald, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.emeraldBgSubtle).border(1.dp, c.emeraldBorder, RoundedCornerShape(12.dp))
                .clickable { go("lab:$id") }.padding(horizontal = 8.dp, vertical = 5.dp))
    }

    @Composable
    override fun Content() {
        val c = Boar.colors
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Basket.load(ctx) } }
        val items by Basket.items.collectAsState()
        // The document title stays between visits; saved a moment after typing stops (each save is a debug-log line).
        var title by rememberSaveable { mutableStateOf(team.sopa.boar.labs.LabPrefs.string("printer.title").orEmpty()) }
        LaunchedEffect(title) {
            kotlinx.coroutines.delay(800)
            if (title != team.sopa.boar.labs.LabPrefs.string("printer.title").orEmpty()) team.sopa.boar.labs.LabPrefs.putString("printer.title", title)
        }
        // Drag to arrange: the card held by its ⠿ follows the finger and swaps with a neighbour once it passes half of it.
        var dragId by remember { mutableStateOf<String?>(null) }
        var dragDy by remember { mutableStateOf(0f) }
        val heights = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
        val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
        var editing by remember { mutableStateOf<String?>(null) }
        // The answer whose drawing is being chosen (the picker is open).
        var choosing by remember { mutableStateOf<String?>(null) }
        var compiling by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var saved by remember { mutableStateOf<String?>(null) }
        var draft by remember { mutableStateOf<File?>(null) }
        // The user picks where the PDF goes (Android's Save as); nothing is shared or sent anywhere.
        val saveAs = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            val src = draft ?: return@rememberLauncherForActivityResult
            if (uri == null) { src.delete(); return@rememberLauncherForActivityResult }
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: error("can't write there") } }
                src.delete()
                ok.onSuccess { saved = uri.lastPathSegment?.substringAfterLast('/') ?: "PDF" }.onFailure { error = it.message }
            }
        }
        // The same basket as Markdown: written straight to the file the user picks.
        var markdown by remember { mutableStateOf<String?>(null) }
        val saveMd = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
            val text = markdown ?: return@rememberLauncherForActivityResult
            markdown = null
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("can't write there") } }
                ok.onSuccess { saved = uri.lastPathSegment?.substringAfterLast('/') ?: "Markdown" }.onFailure { error = it.message }
            }
        }
        val mdLabels = team.sopa.boar.engine.chat.ChatExport.Labels(t("native.labs.printer.mdReasoning"), t("sourceFootnotes.title"))
        val own = remember { illustrations(ctx) }
        val gallery = remember { File(ctx.filesDir, "labs/images").listFiles { f -> f.extension == "png" }.orEmpty().sortedByDescending { it.lastModified() } }
        val defaultTitle = items.firstOrNull()?.chatTitle?.takeIf { it.isNotBlank() } ?: t("native.labs.printer.untitled")
        var preview by rememberSaveable { mutableStateOf(false) }
        // What prints besides the text, kept between visits.
        var meta by remember { mutableStateOf(PdfCompiler.Meta(
            date = team.sopa.boar.labs.LabPrefs.string("printer.date") != "off",
            model = team.sopa.boar.labs.LabPrefs.string("printer.model") == "on",
            sources = team.sopa.boar.labs.LabPrefs.string("printer.sources") != "off",
            images = PdfCompiler.Placement.of(team.sopa.boar.labs.LabPrefs.string("printer.images")),
            drawings = team.sopa.boar.labs.LabPrefs.string("printer.drawings") != "off",
            drawingFirst = team.sopa.boar.labs.LabPrefs.string("printer.drawingPos") == "before")) }
        fun setMeta(m: PdfCompiler.Meta) {
            meta = m
            team.sopa.boar.labs.LabPrefs.putString("printer.date", if (m.date) "on" else "off")
            team.sopa.boar.labs.LabPrefs.putString("printer.model", if (m.model) "on" else "off")
            team.sopa.boar.labs.LabPrefs.putString("printer.sources", if (m.sources) "on" else "off")
            team.sopa.boar.labs.LabPrefs.putString("printer.images", m.images.name.lowercase())
            team.sopa.boar.labs.LabPrefs.putString("printer.drawings", if (m.drawings) "on" else "off")
            team.sopa.boar.labs.LabPrefs.putString("printer.drawingPos", if (m.drawingFirst) "before" else "after")
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (items.isEmpty()) {
                Text(t("native.labs.printer.empty"), style = Boar.type.body, color = c.textSecondary)
                Text(t("native.labs.printer.howTo"), style = Boar.type.caption, color = c.textMuted)
                return@Column
            }
            Label(t("native.labs.printer.docTitle"))
            BasicTextField(title, { title = it }, singleLine = true, textStyle = Boar.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(c.textAccentEmerald),
                modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(8.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(8.dp)).padding(12.dp),
                decorationBox = { inner -> if (title.isEmpty()) Text(defaultTitle, style = Boar.type.body, color = c.textDim); inner() })

            Label(t("native.labs.printer.include"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Toggle("📅 " + t("native.labs.printer.metaDate"), meta.date, Modifier.weight(1f)) { setMeta(meta.copy(date = it)) }
                Toggle("🧠 " + t("native.labs.printer.metaModel"), meta.model, Modifier.weight(1f)) { setMeta(meta.copy(model = it)) }
                Toggle("📚 " + t("native.labs.printer.metaSources"), meta.sources, Modifier.weight(1f)) { setMeta(meta.copy(sources = it)) }
            }
            // Drawings off skips them all for this print, without losing what each answer has picked.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Toggle("🖼 " + t("native.labs.printer.metaDrawings"), meta.drawings, Modifier.weight(1f)) { setMeta(meta.copy(drawings = it)) }
            }
            // Where the drawings go; right and left wrap the answer's text beside its drawing.
            if (meta.drawings && items.any { drawingOf(it, own) != null }) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((p, key) in listOf(PdfCompiler.Placement.CENTER to "center", PdfCompiler.Placement.RIGHT to "right", PdfCompiler.Placement.LEFT to "left", PdfCompiler.Placement.ALTERNATE to "alternate"))
                    Toggle(t("native.labs.printer.place.$key"), meta.images == p, Modifier.weight(1f)) { setMeta(meta.copy(images = p)) }
            }
            // The drawing above its heading, or the heading first with the text starting beside the drawing.
            if (meta.drawings && items.any { drawingOf(it, own) != null }) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Toggle(t("native.labs.printer.pos.after"), !meta.drawingFirst, Modifier.weight(1f)) { setMeta(meta.copy(drawingFirst = false)) }
                Toggle(t("native.labs.printer.pos.before"), meta.drawingFirst, Modifier.weight(1f)) { setMeta(meta.copy(drawingFirst = true)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Toggle(t("native.labs.printer.arrange"), !preview, Modifier.weight(1f)) { preview = false }
                Toggle(t("native.labs.printer.preview"), preview, Modifier.weight(1f)) { preview = true }
            }
            if (preview) Preview(title.ifBlank { defaultTitle }, items, { drawingOf(it, own) }, meta)
            else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(t("native.labs.printer.items", "count" to items.size), Modifier.weight(1f))
                Text(t("native.labs.printer.clear"), style = Boar.type["mono.xs"], color = c.crimson400, modifier = Modifier.clickable { Basket.clear() }.padding(4.dp))
            }
            if (items.size > 1) Text(t("native.labs.printer.dragHint"), style = Boar.type.caption, color = c.textMuted)
            items.forEachIndexed { i, item -> androidx.compose.runtime.key(item.id) {
                val open = editing == item.id
                val dragging = dragId == item.id
                val upLabel = t("native.labs.printer.moveUp")
                val downLabel = t("native.labs.printer.moveDown")
                Column(Modifier.fillMaxWidth()
                    .onGloballyPositioned { heights[item.id] = it.size.height }
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { if (dragging) { translationY = dragDy; shadowElevation = 12f; scaleX = 1.02f; scaleY = 1.02f } }
                    .background(c.bgCard, RoundedCornerShape(10.dp)).border(1.dp, if (open || dragging) c.emeraldBorder else c.borderSubtle, RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⠿", fontSize = 18.sp, color = if (dragging) c.textAccentEmerald else c.textDim,
                            modifier = Modifier
                                .semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction(upLabel) { Basket.move(item.id, -1); true },
                                        CustomAccessibilityAction(downLabel) { Basket.move(item.id, 1); true },
                                    )
                                }
                                .pointerInput(item.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { dragId = item.id; dragDy = 0f },
                                        onDragEnd = { dragId = null; dragDy = 0f },
                                        onDragCancel = { dragId = null; dragDy = 0f },
                                    ) { change, amount ->
                                        change.consume()
                                        dragDy += amount.y
                                        val list = Basket.items.value
                                        val at = list.indexOfFirst { it.id == item.id }
                                        if (dragDy > 0 && at in 0 until list.size - 1) {
                                            val step = (heights[list[at + 1].id] ?: 0) + gapPx
                                            if (dragDy > step / 2) { Basket.move(item.id, 1); dragDy -= step }
                                        } else if (dragDy < 0 && at > 0) {
                                            val step = (heights[list[at - 1].id] ?: 0) + gapPx
                                            if (-dragDy > step / 2) { Basket.move(item.id, -1); dragDy += step }
                                        }
                                    }
                                }
                                .padding(horizontal = 4.dp))
                        Text("${i + 1}", style = Boar.type["mono.xs"], color = c.textAccentEmerald, fontWeight = FontWeight.ExtraBold)
                        Text(item.question.ifBlank { item.chatTitle }, style = Boar.type.titleSm.copy(fontSize = 13.sp), color = c.textHeading, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(if (open) "✓" else "✎", true) { editing = if (open) null else item.id }
                        Icon("✕", true) { Basket.remove(item.id) }
                    }
                    if (open) {
                        // The heading printed above this answer, then its text; both saved as you type.
                        var heading by remember(item.id) { mutableStateOf(item.question.ifBlank { item.chatTitle }) }
                        Label(t("native.labs.printer.heading"))
                        BasicTextField(heading, { heading = it; Basket.editHeading(item.id, it) }, singleLine = true, textStyle = Boar.type.titleSm.copy(fontSize = 13.sp, color = c.textHeading), cursorBrush = SolidColor(c.textAccentEmerald),
                            modifier = Modifier.fillMaxWidth().background(c.bgSurface, RoundedCornerShape(6.dp)).padding(8.dp))
                        var draft by remember(item.id) { mutableStateOf(item.text) }
                        BasicTextField(draft, { draft = it; Basket.edit(item.id, it) }, textStyle = Boar.type.caption.copy(color = c.textPrimary), cursorBrush = SolidColor(c.textAccentEmerald),
                            modifier = Modifier.fillMaxWidth().background(c.bgSurface, RoundedCornerShape(6.dp)).padding(8.dp))
                    } else Text(PdfCompiler.plain(item.text), style = Boar.type.caption, color = c.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.sources.isNotEmpty()) Text(t("native.labs.printer.sources", "count" to item.sources.size), style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.weight(1f))
                        else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        // At most one drawing per answer: tap to choose, ✕ for none.
                        val drawing = drawingOf(item, own)
                        if (drawing != null) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, c.emeraldBorder, RoundedCornerShape(6.dp)).clickable { choosing = item.id }) { Thumb(drawing) }
                            Icon("✕", true) { Basket.setImage(item.id, null) }
                        } else Text("＋ 🖼", style = Boar.type["mono.xs"], color = c.textAccentEmerald, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(6.dp)).clickable { choosing = item.id }.padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }
            } }

            }

            choosing?.let { id ->
                val item = items.firstOrNull { it.id == id }
                if (item == null) { choosing = null; return@let }
                DrawingPicker(own[id], gallery, drawingOf(item, own), onPick = { f -> Basket.setImage(id, f?.path); choosing = null }) { choosing = null }
            }
            error?.let { Text(it, style = Boar.type.caption, color = c.crimson500) }
            saved?.let { Text(t("native.labs.printer.saved", "name" to it), style = Boar.type.caption, color = c.textAccentEmerald) }
            // Printing: a modal in the middle of the screen, over everything, until the Save-as opens.
            if (compiling) androidx.compose.ui.window.Dialog(
                onDismissRequest = {},
                properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            ) {
                Box(Modifier.background(Boar.colors.bgCard, RoundedCornerShape(16.dp)).border(1.dp, Boar.colors.borderDefault, RoundedCornerShape(16.dp)).padding(24.dp)) {
                    PrintingAnimation()
                }
            }
            else Text(t("native.labs.printer.compile"), style = Boar.type.titleSm.copy(fontSize = 14.sp), color = c.textAccentEmerald,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.emeraldBgSubtle).border(1.dp, c.emeraldBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        compiling = true; error = null; saved = null
                        val docTitle = title.ifBlank { defaultTitle }
                        val footer = I18n.t(ctx, "native.labs.printer.footer")
                        scope.launch {
                            val out = File(File(ctx.cacheDir, "print").apply { mkdirs() }, "draft.pdf")
                            // The print run takes at least as long as the animation: the sheet comes out whole.
                            val started = System.currentTimeMillis()
                            val ok = withContext(Dispatchers.IO) { runCatching { PdfCompiler.compile(out, docTitle, items, { drawingOf(it, own) }, footer, meta) } }
                            kotlinx.coroutines.delay((PRINT_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                            compiling = false
                            ok.onSuccess { draft = out; saveAs.launch(slug(docTitle) + ".pdf") }.onFailure { error = it.message }
                        }
                    }.padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (!compiling) Text(t("native.labs.printer.markdown"), style = Boar.type.titleSm.copy(fontSize = 14.sp), color = c.textSecondary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.bgCard).border(1.dp, c.borderSubtle, RoundedCornerShape(10.dp))
                    .clickable {
                        error = null; saved = null
                        val docTitle = title.ifBlank { defaultTitle }
                        markdown = MarkdownCompiler.compile(docTitle, items, I18n.t(ctx, "native.labs.printer.footer"), meta, mdLabels)
                        saveMd.launch(slug(docTitle) + ".md")
                    }.padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    private const val PRINT_MS = 2400L

    @Composable
    private fun Toggle(label: String, on: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
        val c = Boar.colors
        Text(label, style = Boar.type.caption.copy(fontSize = 12.sp), color = if (on) c.textAccentEmerald else c.textMuted, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = modifier.clip(RoundedCornerShape(8.dp)).background(if (on) c.emeraldBgSubtle else c.bgCard)
                .border(1.dp, if (on) c.emeraldBorder else c.borderSubtle, RoundedCornerShape(8.dp)).clickable { onChange(!on) }.padding(horizontal = 6.dp, vertical = 9.dp))
    }

    /** The PDF as it will print, on paper colors: same order, same switches, the drawings where they'll land. */
    @Composable
    private fun Preview(title: String, items: List<Basket.Item>, drawingOf: (Basket.Item) -> File?, meta: PdfCompiler.Meta) {
        val ink = Color(0xFF222222)
        val dim = Color(0xFF777777)
        val green = Color(0xFF1B4D3E)
        val serif = androidx.compose.ui.text.font.FontFamily.Serif
        val headingStyle = androidx.compose.ui.text.TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = green)
        val bodyStyle = androidx.compose.ui.text.TextStyle(fontFamily = serif, fontSize = 12.sp, lineHeight = 17.sp, color = ink)
        Column(Modifier.fillMaxWidth().background(Color(0xFFF7F4EC), RoundedCornerShape(4.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ink)
            Text((if (meta.date) java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date()) + " · " else "") + t("native.labs.printer.footer"),
                fontFamily = serif, fontSize = 10.sp, color = dim)
            Box(Modifier.fillMaxWidth().size(width = 0.dp, height = 1.dp).background(Color(0xFFCCCCCC)))
            var drawn = 0
            items.forEach { item ->
                val heading = PdfCompiler.plain(item.question.ifBlank { item.chatTitle })
                val body = PdfCompiler.plain(item.text)
                val drawing = if (meta.drawings) drawingOf(item) else null
                val side = drawing?.let { meta.images.sideOf(drawn++) }
                if (drawing == null || side == PdfCompiler.Placement.CENTER) {
                    if (drawing != null && meta.drawingFirst) PreviewImage(drawing)
                    Text(heading, style = headingStyle, modifier = Modifier.padding(top = 6.dp))
                    if (drawing != null && !meta.drawingFirst) PreviewImage(drawing)
                    Text(body, style = bodyStyle)
                } else {
                    if (!meta.drawingFirst) Text(heading, style = headingStyle, modifier = Modifier.padding(top = 6.dp))
                    Beside(drawing, side == PdfCompiler.Placement.LEFT, if (meta.drawingFirst) heading else null, headingStyle, body, bodyStyle)
                }
                if (meta.model) PdfCompiler.modelLine(item)?.let { Text(it, fontFamily = serif, fontSize = 9.sp, color = green) }
                if (meta.sources && item.sources.isNotEmpty()) Text(PdfCompiler.sourceLines(item), fontFamily = serif, fontSize = 9.sp, lineHeight = 12.sp, color = Color(0xFF666666))
            }
        }
    }

    @Composable
    private fun PreviewImage(f: File) = Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.size(180.dp)) { Thumb(f) } }

    /**
     * A drawing on the right or left with the text beside it, as in the PDF (200 of the 483 pt column, 14 pt gutter): the
     * lines that fit beside the drawing go there, the rest continues below at the full width. [heading], when given, sits
     * beside the drawing above the text (drawing before the heading).
     */
    @Composable
    private fun Beside(f: File, left: Boolean, heading: String?, headingStyle: androidx.compose.ui.text.TextStyle, body: String, bodyStyle: androidx.compose.ui.text.TextStyle) =
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val density = androidx.compose.ui.platform.LocalDensity.current
            val imageW = maxWidth * (200f / 483f)
            val gutter = maxWidth * (14f / 483f)
            val textW = with(density) { (maxWidth - imageW - gutter).roundToPx() }
            val imageH = with(density) { imageW.roundToPx() }
            val headingH = heading?.let { measurer.measure(it, headingStyle, constraints = androidx.compose.ui.unit.Constraints(maxWidth = textW)).size.height + with(density) { 6.dp.roundToPx() } } ?: 0
            val layout = measurer.measure(body, bodyStyle, constraints = androidx.compose.ui.unit.Constraints(maxWidth = textW))
            // The lines whose bottom is still within the drawing's height go beside it.
            val fit = (0 until layout.lineCount).lastOrNull { layout.getLineBottom(it) <= imageH - headingH }
            val cut = fit?.let { layout.getLineEnd(it, visibleEnd = true) } ?: 0
            val besideText = body.substring(0, cut).trimEnd()
            val rest = body.substring(cut).trimStart()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gutter)) {
                    val image = @Composable { Box(Modifier.size(imageW)) { Thumb(f) } }
                    if (left) image()
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        heading?.let { Text(it, style = headingStyle) }
                        if (besideText.isNotEmpty()) Text(besideText, style = bodyStyle)
                    }
                    if (!left) image()
                }
                if (rest.isNotEmpty()) Text(rest, style = bodyStyle)
            }
        }

    /** A sheet feeding out of the printer, its lines printing top to bottom, for the length of a print run. */
    @Composable
    private fun PrintingAnimation() {
        val c = Boar.colors
        val feed = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) { feed.animateTo(1f, androidx.compose.animation.core.tween(PRINT_MS.toInt(), easing = androidx.compose.animation.core.LinearEasing)) }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // The printer body, with its paper slot.
            Box(Modifier.size(160.dp, 44.dp).background(c.bgCardElevated, RoundedCornerShape(10.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.size(8.dp).align(Alignment.TopEnd).padding(1.dp).background(if ((feed.value * 12).toInt() % 2 == 0) c.emerald400 else c.emeraldBgSubtle, RoundedCornerShape(4.dp)))
                Box(Modifier.size(120.dp, 4.dp).background(Color.Black, RoundedCornerShape(2.dp)))
            }
            // The sheet: grows out of the slot; lines appear as it passes the print head.
            val sheetH = 150.dp * feed.value
            Box(Modifier.size(112.dp, sheetH).background(Color(0xFFF7F4EC)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val lines = (feed.value * 14).toInt()
                    repeat(lines) { i ->
                        val w = when { i == 0 -> 0.7f; i % 5 == 4 -> 0.45f; else -> 0.92f - (i % 3) * 0.08f }
                        Box(Modifier.fillMaxWidth(w).size(width = 0.dp, height = if (i == 0) 5.dp else 3.dp).background(if (i == 0) Color(0xFF1B4D3E) else Color(0xFF888888), RoundedCornerShape(1.dp)))
                    }
                }
            }
            Text(t("native.labs.printer.compiling"), style = Boar.type.caption, color = c.textAccentEmerald, modifier = Modifier.padding(top = 10.dp))
        }
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifEmpty { "boar-document" }

    @Composable
    private fun Label(text: String, modifier: Modifier = Modifier) = Text(text, style = Boar.type["mono.xs"], color = Boar.colors.textDim, fontWeight = FontWeight.Bold, modifier = modifier)

    @Composable
    private fun Icon(glyph: String, enabled: Boolean, onClick: () -> Unit) = Text(glyph, fontSize = 15.sp, color = Boar.colors.textAccentEmerald,
        modifier = Modifier.alpha(if (enabled) 1f else 0.25f).clip(RoundedCornerShape(4.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp))

    /** One answer's drawing: its own illustration first, then Image Gen's pictures, or none. */
    @Composable
    private fun DrawingPicker(own: File?, gallery: List<File>, current: File?, onPick: (File?) -> Unit, onClose: () -> Unit) {
        val c = Boar.colors
        val choices = listOfNotNull(own) + gallery.filter { it != own }
        androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
            Column(Modifier.background(c.bgCard, RoundedCornerShape(16.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("native.labs.printer.pickTitle"), style = Boar.type.titleSm, color = c.textHeading)
                if (choices.isEmpty()) Text(t("native.labs.printer.noImages"), style = Boar.type.caption, color = c.textMuted)
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(choices, key = { it.path }) { f ->
                        Box(Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)).border(2.dp, if (f == current) c.emerald400 else c.borderSubtle, RoundedCornerShape(8.dp)).clickable { onPick(f) }) {
                            Thumb(f)
                            if (f == own) Text("🎨", fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                        }
                    }
                }
                Text(t("native.labs.printer.noDrawing"), style = Boar.type.caption, color = c.crimson400, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onPick(null) }.padding(8.dp))
            }
        }
    }

    @Composable
    private fun Thumb(f: File) {
        val bmp by produceState<ImageBitmap?>(null, f.path) { value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() } }
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
