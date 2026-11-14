package team.sopa.boar.labs.printer

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.sopa.boar.R
import team.sopa.boar.engine.labs.PressDoc
import team.sopa.boar.engine.labs.PressDoc.Block
import team.sopa.boar.engine.labs.PressLayout
import team.sopa.boar.engine.labs.PressReceipt
import team.sopa.boar.engine.labs.PressRewrite
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.ui.I18n
import team.sopa.boar.ui.Toasts
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.io.File

/**
 * Press's page (adam's mock-up, Nov 13): the project button on top, the paper (what you see is what prints), and one
 * bottom bar, Add and Save PDF. Tapping a block selects it; an icon bar above the bottom bar moves, edits (on the page,
 * text blocks), removes (with Undo) or lets go of it.
 */
private enum class Sheet { PROJECTS, ALL_PROJECTS, ADD, DRAWINGS, EDIT, SETTINGS }

private const val PRINT_MS = 1800L
/** The selection's gold, from the mock-up. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PressScreen(lab: LabPlugin) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { PressStore.load(ctx) } }
    val lib by PressStore.library.collectAsState()
    val unreadable by PressStore.unreadable.collectAsState()
    val library = lib ?: run {
        if (unreadable) Text(t("native.labs.printer.unreadable"), style = Boar.type.body, color = c.amber400, modifier = Modifier.padding(16.dp))
        return
    }
    val project = library.current
    var selected by remember(project.id) { mutableStateOf<String?>(null) }
    var editing by remember(project.id) { mutableStateOf<String?>(null) }
    /** A block just added: its field takes the focus with its text selected, so typing replaces it. */
    var fresh by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var compiling by remember { mutableStateOf(false) }
    val scroll = remember(project.id) { ScrollState(0) }
    val titleFocus = remember { FocusRequester() }
    var focusTitle by remember { mutableStateOf(false) }

    // The receipt reads Designer's recipes and the pictures' sizes off the main thread; until then it is built from the blocks.
    val labels = remember { PressPage.labels(ctx) }
    val sourcesLabel = t("native.labs.printer.sourcesRefs")
    val date = remember { PressPage.dateText() }
    // Press settings (adam, Nov 14): what prints besides the blocks.
    var options by remember { mutableStateOf(PressPage.options()) }
    val quick = remember(project, options) { PressPage(project, date, team.sopa.boar.BuildConfig.VERSION_NAME, PressReceipt.build(project), labels, sourcesLabel, options) }
    val full by produceState<PressPage?>(null, project, options) { value = withContext(Dispatchers.IO) { runCatching { PressPage.of(ctx, project, date, options) }.getOrNull() } }
    val page = full?.takeIf { it.project == project && it.options == options } ?: quick

    // Leaving the page (or another project) marks it seen: the menu's recent projects are ordered by it.
    DisposableEffect(project.id) {
        val id = project.id
        onDispose { PressStore.change { it.markSeen(System.currentTimeMillis(), id) } }
    }
    BackHandler(enabled = selected != null) { selected = null; editing = null }

    // The user picks where the PDF goes (Android's Save as); nothing is shared or sent anywhere.
    var draft by remember { mutableStateOf<File?>(null) }
    val saveAs = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val src = draft ?: return@rememberLauncherForActivityResult
        draft = null
        if (uri == null) { src.delete(); return@rememberLauncherForActivityResult }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: error("can't write there") } }
            src.delete()
            ok.onSuccess { Toasts.show(I18n.t(ctx, "native.labs.printer.saved", "name" to (uri.lastPathSegment?.substringAfterLast('/') ?: "PDF"))) }
                .onFailure { Toasts.show(it.message ?: "PDF") }
        }
    }
    var markdown by remember { mutableStateOf<String?>(null) }
    val saveMd = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val text = markdown ?: return@rememberLauncherForActivityResult
        markdown = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("can't write there") } }
            ok.onSuccess { Toasts.show(I18n.t(ctx, "native.labs.printer.saved", "name" to (uri.lastPathSegment?.substringAfterLast('/') ?: "Markdown"))) }
                .onFailure { Toasts.show(it.message ?: "Markdown") }
        }
    }

    /** Save PDF: one tap, the printing animation, then Android's Save as. */
    fun savePdf() {
        if (compiling || project.blocks.isEmpty()) return
        compiling = true
        scope.launch {
            val out = File(File(ctx.cacheDir, "print").apply { mkdirs() }, "draft.pdf")
            val started = System.currentTimeMillis()
            val ok = withContext(Dispatchers.IO) {
                runCatching { PdfCompiler.compile(out, PressPage.of(ctx, project, date)) { PressPictures.fileOf(ctx, project.id, it) } }
            }
            delay((PRINT_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            compiling = false
            ok.onSuccess { draft = out; saveAs.launch(slug(project.name) + ".pdf") }.onFailure { Toasts.show(it.message ?: "PDF") }
        }
    }

    fun saveMarkdown() {
        val drawingLabel = I18n.t(ctx, "native.labs.printer.mdDrawing")
        val photoLabel = I18n.t(ctx, "native.labs.printer.mdPhoto")
        scope.launch {
            markdown = withContext(Dispatchers.IO) { PressPage.of(ctx, project, date).markdown(drawingLabel, photoLabel) }
            saveMd.launch(slug(project.name) + ".md")
        }
    }

    /** A new block goes after the selected one, or at the end; it is selected and open for typing. */
    fun addBlock(b: Block) {
        val at = project.blocks.indexOfFirst { it.id == selected }.let { if (it < 0) project.blocks.size else it + 1 }
        PressStore.change { it.insert(b, at) }
        selected = b.id
        // A picture opens its caption, as the mock-up does; text blocks open for typing.
        editing = b.id
        fresh = b.id
        sheet = null
    }

    // + Add, Photo from this phone: Android's photo picker (no permission); the photo is copied into the project's folder.
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val pid = project.id
        scope.launch {
            // The reason goes to the log (never the photo itself), so a failure on a phone can be traced.
            val b = withContext(Dispatchers.IO) {
                runCatching { PressPictures.copyPhoto(ctx, pid, uri) }
                    .onFailure { e -> android.util.Log.w("BoarLabs", "press: photo copy failed", e) }.getOrNull()
            }
            if (b == null) Toasts.show(I18n.t(ctx, "native.labs.printer.photoFailed"))
            else if (PressStore.library.value?.current?.id == pid) addBlock(b)
        }
    }

    var showBreaks by remember { mutableStateOf(team.sopa.boar.labs.LabPrefs.string(BREAKS_PREF) != "off") }
    // The PDF's own page breaks (PdfCompiler.pageStarts, a layout without drawing), redone a moment after the document
    // changes: the lines on the paper are where the PDF really breaks, not an estimate (adam, Nov 12).
    var pageStarts by remember { mutableStateOf<List<PdfCompiler.PageStart>>(emptyList()) }
    LaunchedEffect(page) {
        delay(300)
        pageStarts = withContext(Dispatchers.Default) {
            runCatching { PdfCompiler.pageStarts(page) { b -> PressPictures.fileOf(ctx, page.project.id, b) } }.getOrDefault(emptyList())
        }
    }
    // Designer's layout (adam, Nov 12: "press has same layout of designer"): the page, then a caption line (project, blocks,
    // pages, Save PDF, the project menu), a strip of things to add, and the chat's own input bar, which writes a note.
    var note by remember { mutableStateOf("") }
    // What was typed in the bar for the selected text: the edit sheet starts BOAR's rewrite with it.
    var askOwn by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        // No "N new answers added at the end · Show" banner (adam, Nov 13: Show found nothing new; his own answers).
        Box(Modifier.fillMaxWidth().weight(1f).background(c.bgBlack)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(14.dp)) {
                PaperView(page, if (showBreaks) pageStarts else emptyList(), selected, editing, fresh, titleFocus,
                    onSelect = { id -> if (selected != id) { selected = id; editing = null } },
                    onTitleFocus = { if (it) { selected = null; editing = null } },
                    onFreshUsed = { fresh = null })
            }
        }
        val i = project.blocks.indexOfFirst { it.id == selected }
        if (i >= 0) ActionBar(project.blocks[i], i, project.blocks.size, editing == selected,
            onMove = { by -> PressStore.change { it.move(project.blocks[i].id, by) }; editing = null },
            onEdit = { if (editing == selected) editing = null else sheet = Sheet.EDIT },
            onCaption = { editing = if (editing == selected) null else selected },
            onPlace = { place -> PressStore.change { it.place(project.blocks[i].id, place) } },
            onRemove = {
                val b = project.blocks[i]
                val pid = project.id
                PressStore.change { it.remove(b.id) }
                selected = null; editing = null
                val toast = Toasts.Toast(I18n.t(ctx, "native.labs.printer.removed"), action = I18n.t(ctx, "native.labs.printer.undo"), onAction = {
                    PressStore.change { it.insert(b, i, pid) }
                })
                Toasts.show(toast)
                // A photo's copy goes once the Undo is over (G-1), with room for a toast still waiting its turn.
                if (b is Block.Picture && b.kind == PressDoc.Kind.PHOTO) PressStore.dropPhotoLater(ctx, pid, java.io.File(b.file).name, toast.millis + UNDO_GRACE_MS)
            },
            onClose = { selected = null; editing = null })
        // Nothing selected: the same row holds what can be added, as icons (adam, Nov 12: "keep the add to this page as
        // icons… as toolbar"; Nov 13: paragraph, list and divider too). A note is typed in the bar below.
        else AddBar(
            onParagraph = { addBlock(Block.Paragraph(PressStore.newId("t"), "")) },
            onList = { addBlock(Block.Bullets(PressStore.newId("l"), "")) },
            onDivider = { addBlock(Block.Divider(PressStore.newId("d"))); editing = null },
            onPhoto = { pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onHeading = { addBlock(Block.Heading(PressStore.newId("h"), I18n.t(ctx, "native.labs.printer.newHeading"))) },
            onDrawing = { sheet = Sheet.DRAWINGS },
            onChat = { sheet = Sheet.ADD })
        // The whole bottom is the chat's input bar (adam, Nov 12: a minimalist toolbar). Its left button opens one menu with
        // everything, grouped (add, save as, projects, page). With a text block selected, what is typed tells BOAR how to
        // change that text (NOW and REWRITTEN, nothing changes before Use); with nothing selected it is a note.
        val voice = remember { team.sopa.boar.app.BoarServices.get(ctx).settings.voiceInputEnabled }
        val target = project.blocks.firstOrNull { it.id == selected && textOf(it) != null }
        team.sopa.boar.ui.chat.PromptBar(note, { note = it }, answering = false,
            onSend = {
                val typed = note.trim()
                if (typed.isNotEmpty()) {
                    if (target != null) { askOwn = typed; editing = null; sheet = Sheet.EDIT }
                    else addBlock(Block.Note(PressStore.newId("n"), typed))
                    note = ""
                }
            }, onStop = {}, voiceEnabled = voice,
            placeholder = t(if (target != null) "native.labs.printer.askPlaceholder" else "native.labs.printer.notePlaceholder"),
            onLeft = { sheet = Sheet.PROJECTS }, leftLabel = t("native.labs.printer.menu"),
            // ⋯, the plugin's menu, as on every plugin page (adam, Nov 14).
            left = { team.sopa.boar.labs.PluginMenuIcon(t("native.labs.printer.menu")) },
            sendIcon = if (target != null) team.sopa.boar.R.drawable.ic_send else R.drawable.lab_printer_ic_add,
            sendLabel = t(if (target != null) "native.labs.printer.rwAsk" else "native.labs.printer.addNote"), queues = false)
    }

    // The header's gear (PluginSettings, adam Nov 14) opens Press settings.
    if (team.sopa.boar.labs.PluginSettings.open == lab.id) LaunchedEffect(Unit) { team.sopa.boar.labs.PluginSettings.open = null; sheet = Sheet.SETTINGS }
    if (focusTitle) LaunchedEffect(Unit) { delay(150); runCatching { titleFocus.requestFocus() }; focusTitle = false }

    sheet?.let { which ->
        // Closed any way (swipe down, a tap outside, Back): the bar's instruction goes with it, so the pencil never reopens
        // the sheet on a leftover and rewrites at once (adam on the F3, Nov 14; t9 0136).
        ModalBottomSheet({ sheet = null; askOwn = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bgCardElevated) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                when (which) {
                    Sheet.PROJECTS -> ProjectsSheet(library,
                        onSwitch = { id -> PressStore.change { it.switchTo(id) }; sheet = null },
                        onNew = { name ->
                            PressStore.change { it.newProject(PressStore.newId("p"), name.ifBlank { I18n.t(ctx, "native.labs.printer.untitled") }, System.currentTimeMillis()) }
                            sheet = null
                        },
                        onRename = { sheet = null; scope.launch { scroll.animateScrollTo(0) }; focusTitle = true },
                        onDelete = {
                            val name = project.name
                            if (PressStore.deleteProject(ctx, project.id)) Toasts.show(I18n.t(ctx, "native.labs.printer.deleted", "name" to name))
                            sheet = null
                        },
                        onMarkdown = { sheet = null; saveMarkdown() },
                        onPdf = { sheet = null; savePdf() }, canSave = project.blocks.isNotEmpty() && !compiling,
                        onAll = { sheet = Sheet.ALL_PROJECTS })
                    Sheet.ALL_PROJECTS -> AllProjectsSheet(library) { id -> PressStore.change { it.switchTo(id) }; sheet = null }
                    Sheet.ADD -> ChatsSheet { id, title -> sheet = null; PressStore.addChat(ctx, id, title) }
                    Sheet.EDIT -> {
                        val target = project.blocks.firstOrNull { it.id == selected && textOf(it) != null }
                        if (target == null) LaunchedEffect(Unit) { sheet = null }
                        else EditSheet(target, typed = askOwn, onTypedUsed = { askOwn = null },
                            // The rest of the document, for an instruction in the user's own words (PressRewrite.documentContext).
                            document = PressRewrite.documentContext(project.name, project.blocks.filter { it.id != target.id }.mapNotNull { b ->
                                when (b) { is Block.Answer -> b.shownHeading + "\n" + PressReceipt.plain(b.text); else -> textOf(b) }
                            }), onSelf = { askOwn = null; sheet = null; editing = target.id }, onClose = { askOwn = null; sheet = null })
                    }
                    Sheet.SETTINGS -> SettingsSheet(lab, showBreaks, options,
                        onBreaks = { on -> showBreaks = on; team.sopa.boar.labs.LabPrefs.putString(BREAKS_PREF, if (on) "on" else "off") },
                        onOptions = { o -> options = o; PressPage.saveOptions(o) })
                    Sheet.DRAWINGS -> DrawingsSheet { f ->
                        sheet = null
                        scope.launch {
                            val b = withContext(Dispatchers.IO) { PressPictures.drawingBlock(ctx, f) }
                            addBlock(b)
                        }
                    }
                }
            }
        }
    }

    // Printing: a modal in the middle of the screen, over everything, until the Save-as opens.
    if (compiling) androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Box(Modifier.background(c.bgCard, RoundedCornerShape(16.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(16.dp)).padding(24.dp)) {
            PrintingAnimation()
        }
    }
}

private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifEmpty { "boar-document" }

// ---- The bars ----


/** Press's tools are the plugin pattern's round tools (team.sopa.boar.labs.PluginTool, adam Nov 14). */
@Composable
private fun IconAction(icon: Int, label: String, enabled: Boolean = true, on: Boolean = false, danger: Boolean = false, dot: Boolean = false,
                       size: Dp = 44.dp, onClick: () -> Unit) = team.sopa.boar.labs.PluginTool(icon, label, enabled, on, danger, dot, size, onClick)

@Composable
private fun ActionBar(b: Block, index: Int, count: Int, editing: Boolean, onMove: (Int) -> Unit, onEdit: () -> Unit, onCaption: () -> Unit, onPlace: (PressDoc.Place) -> Unit,
                      onRemove: () -> Unit, onClose: () -> Unit) {
    val c = Boar.colors
    Column(Modifier.fillMaxWidth().background(c.bgSurface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        // One short row that fits without scrolling (adam, Nov 12: the toolbox took a lot of the page): up, down, the
        // pencil (a text: edit or ask BOAR; a picture: its caption), where a picture sits (one button, each tap the next
        // place), remove, close.
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconAction(R.drawable.lab_printer_ic_up, t("native.labs.printer.moveUp"), enabled = index > 0) { onMove(-1) }
            IconAction(R.drawable.lab_printer_ic_down, t("native.labs.printer.moveDown"), enabled = index < count - 1) { onMove(1) }
            if (textOf(b) != null)
                IconAction(R.drawable.lab_printer_ic_edit, t(if (editing) "native.labs.printer.doneEditing" else "native.labs.printer.edit"), on = editing, onClick = onEdit)
            if (b is Block.Picture) {
                IconAction(R.drawable.lab_printer_ic_caption, t(if (editing) "native.labs.printer.doneCaption" else "native.labs.printer.caption"), dot = editing, onClick = onCaption)
                val (icon, key) = when (b.place) {
                    PressDoc.Place.LEFT -> R.drawable.lab_printer_ic_left to "placeLeft"
                    PressDoc.Place.RIGHT -> R.drawable.lab_printer_ic_right to "placeRight"
                    else -> R.drawable.lab_printer_ic_center to "placeCenter"
                }
                val next = when (b.place) { PressDoc.Place.LEFT -> PressDoc.Place.CENTER; PressDoc.Place.CENTER -> PressDoc.Place.RIGHT; else -> PressDoc.Place.LEFT }
                IconAction(icon, t("native.labs.printer.$key") + ". " + t("native.labs.printer.placeNext"), on = true) { onPlace(next) }
            }
            IconAction(R.drawable.lab_printer_ic_remove, t("native.labs.printer.remove"), danger = true, onClick = onRemove)
            IconAction(R.drawable.lab_printer_ic_close, t("native.labs.printer.close"), onClick = onClose)
        }
    }
}

@Composable
private fun Sep() = Box(Modifier.width(1.dp).height(28.dp).background(Boar.colors.borderDefault))


// ---- The sheets ----

@Composable
private fun SheetTitle(text: String) = Text(text, style = Boar.type.titleSm, color = Boar.colors.textHeading, modifier = Modifier.padding(bottom = 8.dp))

@Composable
private fun SheetRow(text: String, small: String? = null, icon: Int? = null, accent: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val c = Boar.colors
    val color = when { danger -> c.crimson400; accent -> Boar.colors.emerald500; else -> c.textPrimary }
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderSubtle))
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Icon(painterResource(icon), null, Modifier.size(18.dp), tint = color)
            Text(text, style = Boar.type.body, color = color, modifier = Modifier.weight(1f))
            small?.let { Text(it, style = Boar.type.caption, color = c.textMuted) }
        }
    }
}

@Composable
private fun ProjectsSheet(library: PressDoc.Library, onSwitch: (String) -> Unit, onNew: (String) -> Unit, onRename: () -> Unit,
                          onDelete: () -> Unit, onMarkdown: () -> Unit,
                          onPdf: () -> Unit = {}, canSave: Boolean = true, onAll: () -> Unit = {}) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val current = library.current
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    // One menu, grouped (adam, Nov 12): add, save as, the projects, the page.
    @Composable fun Group(key: String) = Text(t(key).uppercase(), style = Boar.type.caption.copy(letterSpacing = 1.sp), color = c.textMuted,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
    // "Save as…": the format on the right, one tap each (adam, Nov 12).
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.lab_printer_ic_download), null, Modifier.size(22.dp), tint = c.textPrimary)
        Text(t("native.labs.printer.saveAs"), style = Boar.type.body, color = c.textPrimary, modifier = Modifier.weight(1f).padding(start = 6.dp))
        for ((label, action, on) in listOf(Triple(".pdf", onPdf, canSave), Triple(".md", onMarkdown, true)))
            Text(label, style = Boar.type.body, color = if (label == ".pdf") Boar.colors.textInverse else c.textPrimary, fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(if (on) 1f else 0.4f).clip(RoundedCornerShape(999.dp)).background(if (label == ".pdf") Boar.colors.emerald500 else c.bgCard)
                    .border(1.dp, if (label == ".pdf") Boar.colors.emerald500 else c.borderSubtle, RoundedCornerShape(999.dp))
                    .clickable(enabled = on, role = Role.Button, onClickLabel = t("native.labs.printer.saveAs") + " " + label, onClick = action)
                    .padding(horizontal = 16.dp, vertical = 10.dp))
    }
    Group("native.labs.printer.projects")
    // The open one and the most recent (PressDoc.SHORT_LIST); the rest behind "All projects", so the menu stays short.
    library.shortList().forEach { p ->
        SheetRow(p.name, t("native.labs.printer.blocks", "count" to p.blocks.size), icon = if (p.id == current.id) R.drawable.lab_printer_ic_check else R.drawable.lab_printer_ic_folder,
            accent = p.id == current.id) { onSwitch(p.id) }
    }
    if (library.projects.size > PressDoc.SHORT_LIST)
        SheetRow(t("native.labs.printer.allProjects", "count" to library.projects.size), "›", icon = R.drawable.lab_printer_ic_folder, onClick = onAll)
    if (!naming) SheetRow(t("native.labs.printer.newProject"), icon = R.drawable.lab_printer_ic_add) { naming = true }
    else {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderSubtle))
        BasicTextField(name, { name = it.take(60) }, singleLine = true, textStyle = Boar.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(Boar.colors.emerald500),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).focusRequester(focus).background(c.bgCard, RoundedCornerShape(10.dp))
                .border(1.dp, c.borderSubtle, RoundedCornerShape(10.dp)).padding(12.dp),
            decorationBox = { inner -> if (name.isEmpty()) Text(t("native.labs.printer.newProjectHint"), style = Boar.type.body, color = c.textDim); inner() })
        Text(t("native.labs.printer.create"), style = Boar.type.body, color = Boar.colors.textInverse, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(12.dp)).background(Boar.colors.emerald500)
                .clickable(role = Role.Button) { onNew(name.trim()) }.padding(vertical = 14.dp))
    }
    SheetRow(t("native.labs.printer.rename", "name" to current.name), icon = R.drawable.lab_printer_ic_edit, onClick = onRename)
    // Any project can be deleted, the last one too (adam, Nov 14): a blank one takes its place. Two taps, as before.
    SheetRow(t(if (confirm) "native.labs.printer.deleteConfirm" else "native.labs.printer.delete", "name" to current.name),
        icon = R.drawable.lab_printer_ic_remove, danger = true) {
        if (!confirm) confirm = true else onDelete()
    }
}

/** The text "Ask BOAR to rewrite it" works on: a heading's, note's, paragraph's or list's text, an answer's text (not its heading). */
private fun textOf(b: Block): String? = when (b) {
    is Block.Heading -> b.text
    is Block.Note -> b.text
    is Block.Paragraph -> b.text
    is Block.Bullets -> b.text
    is Block.Answer -> b.text
    else -> null
}

/** One version of the text in the edit sheet: NOW or REWRITTEN, with a warning line under it if any. */
@Composable
private fun Version(label: String, text: String, new: Boolean, warning: String? = null) {
    val c = Boar.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(c.bgCard)
        .border(1.dp, if (new) c.emeraldBorder else c.borderSubtle, RoundedCornerShape(10.dp)).padding(12.dp)) {
        Text(label, style = Boar.type["mono.xs"], color = c.textMuted, fontWeight = FontWeight.Bold)
        Text(text, style = Boar.type.body, color = c.textPrimary, modifier = Modifier.padding(top = 4.dp))
        warning?.let { Text(it, style = Boar.type.caption, color = c.amber400, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun SheetButton(text: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Boar.colors
    Text(text, style = Boar.type.body, color = if (primary) Boar.colors.textInverse else c.textPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(12.dp))
            .background(if (primary) Boar.colors.emerald500 else c.bgCard).border(1.dp, if (primary) Boar.colors.emerald500 else c.borderSubtle, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 14.dp))
}

/**
 * The pencil on a text block (adam's mock-up): "Edit it yourself" (type on the page) or "Ask BOAR to rewrite it" (Shorter,
 * Simpler words, Fix the wording, As a list, or the user's own words). The rewrite streams, then NOW and REWRITTEN side by
 * side; nothing in the block changes before "Use the new text". Keep mine, Stop, Back and closing the sheet leave it as it
 * was. Not while a chat answer is being written.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditSheet(block: Block, typed: String? = null, onTypedUsed: () -> Unit = {}, document: String = "", onSelf: () -> Unit, onClose: () -> Unit) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = textOf(block).orEmpty()
    val languageOf = if (block is Block.Answer) block.question + "\n" + block.text else current
    var own by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var streamed by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PressRewriter.Outcome.Done?>(null) }
    var line by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Leaving the sheet (Back, a tap outside) stops a rewrite still running; the block stays as it was.
    DisposableEffect(block.id) { onDispose { job?.cancel() } }
    // The chat writing an answer: say so, and the options wait.
    var chatBusy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val services = team.sopa.boar.app.BoarServices.get(ctx)
        while (true) { if (!running) chatBusy = services.isModelBusy; delay(1000) }
    }
    fun ask(option: PressRewrite.Option, ownText: String?) {
        if (running) return
        line = null; result = null; streamed = ""; running = true
        job = scope.launch {
            val o = try {
                PressRewriter.run(ctx, current, option, ownText, languageOf, document) { streamed = it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                PressRewriter.Outcome.Failed
            }
            running = false
            when (o) {
                PressRewriter.Outcome.Busy -> { chatBusy = true; line = I18n.t(ctx, "native.labs.printer.rwBusy") }
                PressRewriter.Outcome.NoModel -> line = I18n.t(ctx, "native.labs.printer.rwNoModel")
                PressRewriter.Outcome.Failed -> line = I18n.t(ctx, "native.labs.printer.rwFailed")
                PressRewriter.Outcome.Unchanged -> line = I18n.t(ctx, "native.labs.printer.rwNoChange")
                is PressRewriter.Outcome.Done -> result = o
            }
        }
    }
    // Typed in the input bar for this text: BOAR starts on it as the sheet opens (adam, Nov 12), once: the instruction is
    // used up as its rewrite starts (t9 0136), and stays in the sheet's own field.
    LaunchedEffect(typed) { if (!typed.isNullOrBlank()) { own = typed; ask(PressRewrite.Option.OWN, typed); onTypedUsed() } }
    val done = result
    when {
        running -> {
            SheetTitle(t("native.labs.printer.askTitle"))
            Version(t("native.labs.printer.rwNow"), PressReceipt.plain(current), new = false)
            Version(t("native.labs.printer.rwRewriting"), streamed, new = true)
            SheetButton(t("native.labs.printer.rwStop"), primary = false) {
                job?.cancel()
                running = false
                line = I18n.t(ctx, "native.labs.printer.rwStopped")
            }
        }
        done != null -> {
            SheetTitle(t("native.labs.printer.askTitle"))
            Version(t("native.labs.printer.rwNow"), PressReceipt.plain(current), new = false)
            Version(t("native.labs.printer.rwRewritten"), PressReceipt.plain(done.text), new = true,
                warning = if (done.marksChanged) t("native.labs.printer.rwMarks") else null)
            // The one place the rewrite reaches the block.
            SheetButton(t("native.labs.printer.rwUse"), primary = true) {
                PressStore.change { it.editText(block.id, PressDoc.Field.TEXT, done.text) }
                Toasts.show(I18n.t(ctx, "native.labs.printer.rwUpdated"))
                onClose()
            }
            SheetButton(t("native.labs.printer.rwKeep"), primary = false, onClick = onClose)
        }
        else -> {
            SheetTitle(t("native.labs.printer.editTitle"))
            SheetRow(t("native.labs.printer.editSelf"), t("native.labs.printer.editSelfSmall"), icon = R.drawable.lab_printer_ic_edit, onClick = onSelf)
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderSubtle))
            Text(t("native.labs.printer.askTitle"), style = Boar.type.body, color = c.textHeading, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            if (chatBusy) Text(t("native.labs.printer.rwBusy"), style = Boar.type.caption, color = c.amber400, modifier = Modifier.padding(bottom = 6.dp))
            // Six presets on two full lines at Standard text (adam, Nov 14: "2 more to fill the second line"): Shorter, Simpler
            // words, Fix the wording / As a list, As a paragraph, More formal. 8 dp sides and 6 dp gaps make the English lines
            // 348 and 336 dp of the 361 dp a 393 dp phone gives them; longer words (Large, Portuguese) wrap, never cut.
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((option, key) in REWRITE_PRESETS)
                    Text(t(key), style = Boar.type.body, color = c.textPrimary,
                        modifier = Modifier.alpha(if (chatBusy) 0.4f else 1f).clip(RoundedCornerShape(999.dp)).background(c.bgCard)
                            .border(1.dp, c.borderSubtle, RoundedCornerShape(999.dp)).clickable(enabled = !chatBusy, role = Role.Button) { ask(option, null) }
                            .padding(horizontal = 8.dp, vertical = 9.dp))
            }
            BasicTextField(own, { own = it.take(120) }, singleLine = true, textStyle = Boar.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(Boar.colors.emerald500),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).background(c.bgCard, RoundedCornerShape(10.dp))
                    .border(1.dp, c.borderSubtle, RoundedCornerShape(10.dp)).padding(12.dp),
                decorationBox = { inner -> if (own.isEmpty()) Text(t("native.labs.printer.rwOwnHint"), style = Boar.type.body, color = c.textDim); inner() })
            SheetButton(t("native.labs.printer.rwAsk"), primary = false, enabled = !chatBusy && own.isNotBlank()) { ask(PressRewrite.Option.OWN, own) }
            line?.let { Text(it, style = Boar.type.caption, color = c.amber400, modifier = Modifier.padding(top = 8.dp)) }
            Text(t("native.labs.printer.rwHint"), style = Boar.type.caption, color = c.textMuted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** The edit sheet's presets, in their order on the two lines. */
private val REWRITE_PRESETS = listOf(
    PressRewrite.Option.SHORTER to "native.labs.printer.rwShorter", PressRewrite.Option.SIMPLER to "native.labs.printer.rwSimpler",
    PressRewrite.Option.FIX to "native.labs.printer.rwFix", PressRewrite.Option.LIST to "native.labs.printer.rwList",
    PressRewrite.Option.PARAGRAPH to "native.labs.printer.rwParagraph", PressRewrite.Option.FORMAL to "native.labs.printer.rwFormal",
)

/** Every project, the open one first, then the most recent; a search box to find one by name (adam, Nov 12). */
@Composable
private fun AllProjectsSheet(library: PressDoc.Library, onSwitch: (String) -> Unit) {
    val c = Boar.colors
    var query by remember { mutableStateOf("") }
    SheetTitle(t("native.labs.printer.allProjects", "count" to library.projects.size))
    BasicTextField(query, { query = it.take(60) }, singleLine = true, textStyle = Boar.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(Boar.colors.emerald500),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).background(c.bgCard, RoundedCornerShape(10.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(10.dp)).padding(12.dp),
        decorationBox = { inner -> if (query.isEmpty()) Text(t("native.labs.printer.searchProjects"), style = Boar.type.body, color = c.textDim); inner() })
    val found = library.search(query)
    if (found.isEmpty()) Text(t("native.labs.printer.noProjects"), style = Boar.type.caption, color = c.textMuted, modifier = Modifier.padding(vertical = 12.dp))
    found.forEach { p ->
        SheetRow(p.name, t("native.labs.printer.blocks", "count" to p.blocks.size),
            icon = if (p.id == library.current.id) R.drawable.lab_printer_ic_check else R.drawable.lab_printer_ic_folder, accent = p.id == library.current.id) { onSwitch(p.id) }
    }
}

/**
 * What can be added, as one row of icons with spoken names (a long press shows the name): heading, paragraph, list,
 * divider, photo (adam's order, Nov 13), then a drawing and a chat's answers. Press settings left the toolbar for the
 * header's gear (adam, Nov 14). From the left, 44 dp at most, never scrolling (the plugin pattern's toolbar).
 */
@Composable
private fun AddBar(onParagraph: () -> Unit, onList: () -> Unit, onDivider: () -> Unit, onPhoto: () -> Unit, onHeading: () -> Unit, onDrawing: () -> Unit, onChat: () -> Unit) {
    // No "Add" word: each icon says its name (spoken, and on a long press). Left-aligned, never scrolling (PluginToolbar).
    team.sopa.boar.labs.PluginToolbar(ADD_BUTTONS) { size ->
        IconAction(R.drawable.lab_printer_ic_heading, t("native.labs.printer.heading"), size = size, onClick = onHeading)
        IconAction(R.drawable.lab_printer_ic_paragraph, t("native.labs.printer.paragraph"), size = size, onClick = onParagraph)
        IconAction(R.drawable.lab_printer_ic_list, t("native.labs.printer.list"), size = size, onClick = onList)
        IconAction(R.drawable.lab_printer_ic_divider, t("native.labs.printer.divider"), size = size, onClick = onDivider)
        IconAction(R.drawable.lab_printer_ic_photo, t("native.labs.printer.addPhoto"), size = size, onClick = onPhoto)
        IconAction(R.drawable.lab_printer_ic_drawing, t("native.labs.printer.drawing"), size = size, onClick = onDrawing)
        IconAction(R.drawable.lab_printer_ic_chat, t("native.labs.printer.fromChat"), size = size, onClick = onChat)
    }
}

private const val ADD_BUTTONS = 7

/**
 * Press settings (adam, Nov 14): the paper's page breaks, and what the document prints besides its blocks: the date under
 * the title, the Sources section at the end, the models, and their speed (as rc2's receipt had). "Made offline with BOAR"
 * always prints, so it has no switch. They apply to every project, the paper, the PDF and the Markdown.
 */
@Composable
private fun SettingsSheet(lab: LabPlugin, showBreaks: Boolean, o: PressReceipt.Options, onBreaks: (Boolean) -> Unit, onOptions: (PressReceipt.Options) -> Unit) {
    SheetTitle(t("native.labs.printer.settings"))
    team.sopa.boar.ui.settings.SwitchRow(t("native.labs.printer.pageBreaks"), t("native.labs.printer.pageBreaksNote"), showBreaks, onBreaks)
    team.sopa.boar.ui.settings.SwitchRow(t("native.labs.printer.showDate"), null, o.date) { onOptions(o.copy(date = it)) }
    team.sopa.boar.ui.settings.SwitchRow(t("native.labs.printer.showSources"), t("native.labs.printer.showSourcesNote"), o.sources) { onOptions(o.copy(sources = it)) }
    team.sopa.boar.ui.settings.SwitchRow(t("native.labs.printer.showModels"), null, o.models) { onOptions(o.copy(models = it, speed = it && o.speed)) }
    if (o.models) team.sopa.boar.ui.settings.SwitchRow(t("native.labs.printer.showSpeed"), null, o.speed) { onOptions(o.copy(speed = it)) }
    Text(t("native.labs.printer.madeAlways"), style = Boar.type.caption, color = Boar.colors.textMuted, modifier = Modifier.padding(top = 10.dp))
    // About this plugin lives here, not in the menu (adam, Nov 14). Press has no How it works text.
    Spacer(Modifier.height(8.dp))
    team.sopa.boar.labs.AboutPluginFold(lab)
}

/** The latest chats: a tap adds that chat's answers at the end, as "Add to Press" in the menu does. */
@Composable
private fun ChatsSheet(onChat: (String, String) -> Unit) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val chats by produceState<List<Pair<String, String>>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { team.sopa.boar.app.BoarServices.get(ctx).chats.listSessions().take(12).map { it.id to it.title.ifBlank { "…" } } }.getOrDefault(emptyList())
        }
    }
    SheetTitle(t("native.labs.printer.fromChat"))
    chats?.forEach { (id, title) -> SheetRow(title, icon = R.drawable.lab_printer_ic_add) { onChat(id, title) } }
    Text(t("native.labs.printer.addHint"), style = Boar.type.caption, color = c.textMuted, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun DrawingsSheet(onPick: (File) -> Unit) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val files by produceState<List<File>?>(null) { value = withContext(Dispatchers.IO) { PressPictures.gallery(ctx) } }
    SheetTitle(t("native.labs.printer.pickTitle"))
    val list = files ?: return
    if (list.isEmpty()) { Text(t("native.labs.printer.noImages"), style = Boar.type.caption, color = c.textMuted); return }
    list.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { f ->
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF111111))
                    .clickable(role = Role.Button, onClickLabel = t("native.labs.printer.add")) { onPick(f) }) { Picture(f, ContentScale.Crop) }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Picture(f: File, scale: ContentScale) {
    val bmp by produceState<ImageBitmap?>(null, f.path) { value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() } }
    bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = scale) }
}

// ---- The paper ----

/** The paper's type at its scale: [Paper]'s points times the page's pixels per point, as text sizes that ignore the font scale. */
private class PaperScale(val pxPerPt: Float, val density: androidx.compose.ui.unit.Density) {
    fun sp(pt: Float): TextUnit = with(density) { (pt * pxPerPt).toSp() }
    fun dp(pt: Float): Dp = with(density) { (pt * pxPerPt).toDp() }
    private val serif = FontFamily.Serif
    private val sans = FontFamily.SansSerif
    private fun style(size: Float, family: FontFamily = serif, bold: Boolean = false, italic: Boolean = false, color: Int = Paper.INK, line: Float = 1.25f) =
        TextStyle(fontFamily = family, fontSize = sp(size), lineHeight = sp(size * line), color = Color(color),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)
    val title = style(Paper.TITLE, bold = true, line = 1.2f)
    val date = style(Paper.DATE, sans, color = Paper.DATE_INK).copy(letterSpacing = 0.04.em)
    val heading = style(Paper.HEADING, bold = true)
    val question = style(Paper.QUESTION, italic = true, color = Paper.MUTED)
    val body = style(Paper.BODY, line = Paper.LINE)
    val refs = style(Paper.REFS, sans, color = Paper.META)
    val note = style(Paper.NOTE, italic = true, color = Paper.MUTED, line = Paper.LINE)
    val caption = style(Paper.CAPTION, italic = true, color = Paper.MUTED)
    val receiptTitle = style(Paper.RECEIPT_TITLE, sans, bold = true).copy(letterSpacing = 0.06.em)
    val label = style(Paper.RECEIPT, sans, bold = true, color = Paper.LABEL_INK, line = 1.35f)
    val value = style(Paper.RECEIPT, sans, color = Paper.RECEIPT_INK, line = 1.35f)
    val foot = style(Paper.FOOT, sans, color = Paper.FOOT_INK)
    val footer = style(Paper.FOOTER, sans, color = Paper.FOOT_INK, line = 1.3f)
    val sourcesTitle = style(Paper.RECEIPT_TITLE, sans, bold = true)
    val sourceLine = style(Paper.REFS, sans, color = Paper.RECEIPT_INK)
}

@Composable
private fun PaperView(page: PressPage, starts: List<PdfCompiler.PageStart>, selected: String?, editing: String?, fresh: String?, titleFocus: FocusRequester,
                      onSelect: (String) -> Unit, onTitleFocus: (Boolean) -> Unit, onFreshUsed: () -> Unit) {
    val project = page.project
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val s = remember(maxWidth, density) { PaperScale(with(density) { maxWidth.toPx() } / Paper.W, density) }
        val measurer = rememberTextMeasurer()
        val pageLabel = t("native.labs.printer.page", "n" to "{{n}}")
        // Each block's top and height on the paper (px), by the ids PdfCompiler.pageStarts uses.
        val tops = remember { androidx.compose.runtime.mutableStateMapOf<String, Pair<Float, Float>>() }
        fun Modifier.unit(vararg ids: String) = onGloballyPositioned { c -> val v = c.positionInParent().y to c.size.height.toFloat(); ids.forEach { tops[it] = v } }
        val breakColor = Color(0xFFB9BFD3)
        Column(Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(2.dp)).background(Color.White, RoundedCornerShape(2.dp))
            // Where the PDF's pages start (PdfCompiler.pageStarts): a line at the top of the block that starts a page, or as far
            // into a text block as the PDF got. Never through a picture or the receipt, which move whole.
            .drawWithContent {
                drawContent()
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                starts.forEachIndexed { i, st ->
                    val (inner, height) = tops[st.unit] ?: return@forEachIndexed
                    // Block positions are inside the paper's margin; the lines are drawn from its outer edge.
                    val top = inner + Paper.M * s.pxPerPt
                    val y = if (st.intoPt <= 1f) top - 4.dp.toPx() else top + minOf(st.intoPt * s.pxPerPt, height)
                    drawLine(breakColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
                    val text = measurer.measure(pageLabel.replace("{{n}}", (i + 2).toString()), TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D1D1F)))
                    val w = text.size.width + 12.dp.toPx()
                    val h = text.size.height.toFloat()
                    drawRoundRect(breakColor, Offset(size.width - w - 8.dp.toPx(), y - h / 2), Size(w, h), CornerRadius(h / 2))
                    drawText(text, topLeft = Offset(size.width - w - 2.dp.toPx(), y - h / 2))
                }
            }
            .padding(s.dp(Paper.M))) {
            Box(Modifier.unit(PdfCompiler.TITLE)) { TitleField(project, s, titleFocus, onTitleFocus) }
            if (page.options.date) Text(page.date.uppercase(), style = s.date, modifier = Modifier.padding(top = s.dp(4f), bottom = s.dp(Paper.GAP + 4f)))
            else Spacer(Modifier.height(s.dp(Paper.GAP)))
            if (project.blocks.isEmpty()) {
                Text(t("native.labs.printer.empty") + "\n" + t("native.labs.printer.howTo"), style = s.note, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = s.dp(60f)))
            }
            // The same plan as the PDF: a side picture with the next text block beside it (PressLayout).
            PressLayout.plan(project.blocks).forEach { item ->
                when (item) {
                    is PressLayout.Item.Single -> key(item.block.id) {
                        val b = item.block
                        Box(Modifier.unit(b.id)) { BlockView(b, page, s, selected == b.id, editing == b.id, fresh == b.id, onSelect = { onSelect(b.id) }, onFreshUsed = onFreshUsed) }
                        Spacer(Modifier.height(s.dp(Paper.GAP)))
                    }
                    is PressLayout.Item.Beside -> key(item.picture.id) {
                        Box(Modifier.unit(*listOfNotNull(item.picture.id, item.text?.id).toTypedArray())) { BesideView(item, page, s, selected, editing, fresh, onSelect, onFreshUsed) }
                        Spacer(Modifier.height(s.dp(Paper.GAP)))
                    }
                }
            }
            // The Sources section at the end (adam, Nov 14), as the PDF prints it.
            if (page.sourceLines.isNotEmpty()) Column(Modifier.unit(PdfCompiler.SOURCES).padding(top = s.dp(Paper.GAP))) {
                Text(page.sourcesLabel, style = s.sourcesTitle, modifier = Modifier.padding(bottom = s.dp(6f)))
                page.sourceLines.forEach { Text(it, style = s.sourceLine, modifier = Modifier.padding(bottom = s.dp(3f))) }
            }
            Box(Modifier.unit(PdfCompiler.RECEIPT)) {
                ReceiptView(page, s)
            }
        }
    }
}

@Composable
private fun TitleField(project: PressDoc.Project, s: PaperScale, focus: FocusRequester, onFocus: (Boolean) -> Unit) {
    // The title is the project's name: typed on the page, saved a moment after typing stops; empty keeps the old name.
    var draft by remember(project.id) { mutableStateOf(project.name) }
    LaunchedEffect(project.id, draft) {
        delay(600)
        if (draft.isNotBlank() && draft.trim() != project.name) PressStore.change { it.rename(project.id, draft) }
    }
    val label = t("native.labs.printer.docTitle")
    BasicTextField(draft, { draft = it.replace("\n", " ") }, textStyle = s.title, cursorBrush = SolidColor(Boar.colors.emerald500),
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
            .onFocusChanged { onFocus(it.isFocused); if (!it.isFocused && draft.isBlank()) draft = project.name }
            .semantics { contentDescription = label })
}

@Composable
private fun BlockView(b: Block, page: PressPage, s: PaperScale, selected: Boolean, editing: Boolean, fresh: Boolean, onSelect: () -> Unit, onFreshUsed: () -> Unit) {
    val ctx = LocalContext.current
    val project = page.project
    Column(Modifier.fillMaxWidth().selectable(selected, onSelect)) {
        when (b) {
            is Block.Heading ->
                if (editing) EditField(b.text, s.heading, fresh, onFreshUsed) { v -> PressStore.change { it.editText(b.id, PressDoc.Field.TEXT, v) } }
                else Text(b.text, style = s.heading)
            is Block.Note -> Box(Modifier.fillMaxWidth().drawBehind {
                drawLine(Color(Paper.NOTE_BAR), Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), strokeWidth = 2.dp.toPx())
            }.padding(start = s.dp(12f))) {
                if (editing) EditField(b.text, s.note, fresh, onFreshUsed) { v -> PressStore.change { it.editText(b.id, PressDoc.Field.TEXT, v) } }
                else Text(b.text, style = s.note)
            }
            is Block.Answer -> {
                if (editing) EditField(b.heading.ifBlank { b.shownHeading }, s.heading, false, onFreshUsed) { v -> PressStore.change { it.editText(b.id, PressDoc.Field.HEADING, v) } }
                else Text(PressPageText.oneLine(b.shownHeading), style = s.heading)
                b.shownQuestion?.let { Text(it.trim(), style = s.question, modifier = Modifier.padding(top = s.dp(3f))) }
                Spacer(Modifier.height(s.dp(3f)))
                if (editing) EditField(b.text, s.body, false, onFreshUsed) { v -> PressStore.change { it.editText(b.id, PressDoc.Field.TEXT, v) } }
                else Text(page.answerText(b), style = s.body)
                page.refsLine(b)?.let { Text(it, style = s.refs, modifier = Modifier.padding(top = s.dp(4f))) }
            }
            // A paragraph and a list start empty and open for typing; empty, they show their hint on the screen only (they don't print).
            is Block.Paragraph ->
                if (editing) EditField(b.text, s.body, fresh, onFreshUsed, placeholder = t("native.labs.printer.paragraphHint")) { v -> PressStore.change { it.editText(b.id, PressDoc.Field.TEXT, v) } }
                else if (b.text.isBlank()) Text(t("native.labs.printer.paragraphHint"), style = s.body.copy(color = HINT))
                else Text(b.text.trim(), style = s.body)
            is Block.Bullets ->
                if (editing) EditField(b.text, s.body, fresh, onFreshUsed, placeholder = t("native.labs.printer.listHint")) { v -> PressStore.change { it.editText(b.id, PressDoc.Field.TEXT, v) } }
                else if (b.items.isEmpty()) Text(t("native.labs.printer.listHint"), style = s.body.copy(color = HINT))
                else Text(PressDoc.bulleted(b.text), style = s.body)
            // Tall enough to tap; the line itself is thin, as in the PDF.
            is Block.Divider -> Box(Modifier.fillMaxWidth().height(s.dp(14f)), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(Paper.RULE)))
            }
            is Block.Picture -> PictureContent(b, project.id, s, Paper.INNER, editing, fresh, onFreshUsed, center = true)
            is Block.Unknown -> {}
        }
    }
}

/** A block's selection on the paper: the theme's accent around it, a tap selects it. */
@Composable
private fun Modifier.selectable(selected: Boolean, onSelect: () -> Unit): Modifier {
    val ring = Boar.colors.emerald500
    return this.drawBehind {
        if (selected) {
            val o = 6.dp.toPx()
            drawRoundRect(Color(0xFFFFFAF0), Offset(-o, -o), Size(size.width + 2 * o, size.height + 2 * o), CornerRadius(4.dp.toPx()))
            drawRoundRect(ring, Offset(-o, -o), Size(size.width + 2 * o, size.height + 2 * o), CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }
    }
    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onSelect)
}

/**
 * A picture in a box [maxW] points wide, in its own shape, its caption under it: typed on the page while [editing]; an
 * empty one doesn't show, as it doesn't print.
 */
@Composable
private fun PictureContent(b: Block.Picture, projectId: String, s: PaperScale, maxW: Float, editing: Boolean, fresh: Boolean, onFreshUsed: () -> Unit, center: Boolean) {
    val ctx = LocalContext.current
    val file = remember(b.file, projectId) { PressPictures.fileOf(ctx, projectId, b) }
    val (pw, ph) = if (b.width > 0 && b.height > 0) b.width to b.height else 1 to 1
    val (w, h) = team.sopa.boar.engine.labs.PictureFit.within(pw, ph, maxW, Paper.PICTURE_MAX_H)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = if (center) Alignment.Center else if (b.place == PressDoc.Place.LEFT) Alignment.CenterStart else Alignment.CenterEnd) {
            if (file != null) Box(Modifier.size(s.dp(w), s.dp(h)).background(Color(0xFFEEEEEE))) { Picture(file, ContentScale.Fit) }
            else Text(t("native.labs.printer.missing"), style = s.caption, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F2F5)).padding(s.dp(20f)))
        }
        val align = if (center) TextAlign.Center else TextAlign.Start
        if (editing) Box(Modifier.padding(top = s.dp(4f))) {
            EditField(b.caption, s.caption.copy(textAlign = align), fresh, onFreshUsed, placeholder = t("native.labs.printer.captionHint")) { v ->
                PressStore.change { it.editText(b.id, PressDoc.Field.CAPTION, v) }
            }
        }
        // Empty captions don't show, as they don't print.
        else if (b.caption.isNotBlank()) Text(b.caption.trim(), style = s.caption, textAlign = align, modifier = Modifier.fillMaxWidth().padding(top = s.dp(4f)))
    }
}

/** A piece of a text block, for the flow beside a picture: its text, its style, the space above it. */
private data class Piece(val text: String, val style: TextStyle, val gap: Float)

private fun pieces(b: Block, page: PressPage, s: PaperScale): List<Piece> = when (b) {
    is Block.Answer -> listOfNotNull(
        Piece(PressPageText.oneLine(b.shownHeading), s.heading, 0f),
        b.shownQuestion?.let { Piece(it.trim(), s.question, 3f) },
        Piece(page.answerText(b), s.body, 3f),
        page.refsLine(b)?.let { Piece(it, s.refs, 4f) },
    )
    is Block.Note -> listOf(Piece(b.text, s.note, 0f))
    is Block.Paragraph -> listOf(Piece(b.text.trim(), s.body, 0f))
    is Block.Bullets -> listOf(Piece(PressDoc.bulleted(b.text), s.body, 0f))
    else -> emptyList()
}

/**
 * A picture on the left or right with the next text block beside it (PressLayout.plan): the lines whose top is above the
 * picture's bottom go beside it (PressLayout.linesBeside, as in the PDF), the rest continue below at the full width. While
 * the text is being typed it stays beside the picture whole.
 */
@Composable
private fun BesideView(item: PressLayout.Item.Beside, page: PressPage, s: PaperScale, selected: String?, editing: String?, fresh: String?,
                       onSelect: (String) -> Unit, onFreshUsed: () -> Unit) {
    val pic = item.picture
    val text = item.text
    val density = LocalDensity.current
    val side = PressLayout.sideWidth(Paper.INNER)
    val (pw, ph) = if (pic.width > 0 && pic.height > 0) pic.width to pic.height else 1 to 1
    var picH by remember(pic.id) { mutableStateOf(with(density) { s.dp(team.sopa.boar.engine.labs.PictureFit.within(pw, ph, side, Paper.PICTURE_MAX_H).second).toPx() }) }
    val picture = @Composable {
        Box(Modifier.width(s.dp(side)).onSizeChanged { picH = it.height.toFloat() }.selectable(selected == pic.id) { onSelect(pic.id) }) {
            PictureContent(pic, page.project.id, s, side, editing == pic.id, fresh == pic.id, onFreshUsed, center = false)
        }
    }
    val gutter = s.dp(PressLayout.GUTTER)
    if (text == null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.left) Arrangement.Start else Arrangement.End) { picture() }
        return
    }
    val note = text is Block.Note
    val indent = if (note) 12f else 0f
    val textSelected = selected == text.id
    if (editing == text.id) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gutter)) {
            if (item.left) picture()
            Box(Modifier.weight(1f)) { BlockView(text, page, s, textSelected, true, fresh == text.id, onSelect = { onSelect(text.id) }, onFreshUsed = onFreshUsed) }
            if (!item.left) picture()
        }
        return
    }
    val measurer = rememberTextMeasurer()
    val widthPx = with(density) { s.dp(PressLayout.besideWidth(Paper.INNER) - indent).roundToPx() }
    val room = picH + with(density) { s.dp(8f).toPx() }
    val beside = mutableListOf<Piece>()
    val below = mutableListOf<Piece>()
    var y = 0f
    for (p in pieces(text, page, s)) {
        if (p.text.isBlank()) continue
        if (y >= room) { below += p; continue }
        val gap = with(density) { s.dp(p.gap).toPx() }
        val lay = measurer.measure(p.text, p.style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = widthPx))
        val n = PressLayout.linesBeside((0 until lay.lineCount).map { y + gap + lay.getLineTop(it) }, room)
        when {
            n >= lay.lineCount -> { beside += p; y += gap + lay.size.height }
            n == 0 -> { below += p; y = room }
            else -> {
                val cut = lay.getLineEnd(n - 1)
                beside += p.copy(text = p.text.substring(0, cut).trimEnd())
                below += p.copy(text = p.text.substring(cut).trimStart(), gap = 0f)
                y = room
            }
        }
    }
    val pieceView = @Composable { list: List<Piece>, m: Modifier ->
        Column(m.then(if (note) Modifier.drawBehind {
            drawLine(Color(Paper.NOTE_BAR), Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), strokeWidth = 2.dp.toPx())
        }.padding(start = s.dp(indent)) else Modifier)) {
            list.forEach { p -> Text(p.text, style = p.style, modifier = Modifier.padding(top = s.dp(p.gap))) }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gutter)) {
            if (item.left) picture()
            if (beside.isNotEmpty()) pieceView(beside, Modifier.weight(1f).selectable(textSelected) { onSelect(text.id) })
            else Spacer(Modifier.weight(1f))
            if (!item.left) picture()
        }
        if (below.isNotEmpty()) {
            Spacer(Modifier.height(s.dp(if (beside.isEmpty()) 8f else 2f)))
            pieceView(below, Modifier.fillMaxWidth().selectable(textSelected) { onSelect(text.id) })
        }
    }
}

/** A text typed on the page, in the paper's own style; saved a moment after typing stops, and when it closes. */
/** After a removed photo's Undo toast: time for toasts queued before it, then its copy is deleted. */
private const val UNDO_GRACE_MS = 10_000L

/** A paragraph's or list's hint while it is empty (the placeholder's grey). */
private val HINT = Color(0xFFA2A6B4)

@Composable
private fun EditField(value: String, style: TextStyle, fresh: Boolean, onFreshUsed: () -> Unit, placeholder: String? = null, onSave: (String) -> Unit) {
    var draft by remember { mutableStateOf(TextFieldValue(value, if (fresh) TextRange(0, value.length) else TextRange(value.length))) }
    val latest by rememberUpdatedState(value)
    val save by rememberUpdatedState(onSave)
    LaunchedEffect(draft.text) {
        delay(500)
        if (draft.text != latest) save(draft.text)
    }
    DisposableEffect(Unit) { onDispose { if (draft.text != latest) save(draft.text) } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (fresh) { runCatching { focus.requestFocus() }; onFreshUsed() } }
    BasicTextField(draft, { draft = it }, textStyle = style, cursorBrush = SolidColor(Boar.colors.emerald500),
        modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF3D6), RoundedCornerShape(2.dp)).focusRequester(focus),
        decorationBox = { inner ->
            Box { if (draft.text.isEmpty() && placeholder != null) Text(placeholder, style = style.copy(color = Color(0xFFA2A6B4)), modifier = Modifier.fillMaxWidth()); inner() }
        })
}

@Composable
private fun ReceiptView(page: PressPage, s: PaperScale) {
    // The footer as the PDF draws it (adam, Nov 13): a short hairline, two small light lines, centered.
    Column(Modifier.fillMaxWidth().padding(top = s.dp(18f)), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(s.dp(48f)).height(1.dp).background(Color(Paper.RULE)))
        Spacer(Modifier.height(s.dp(8f)))
        page.footer.forEach { Text(it, style = s.footer, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = s.dp(2f))) }
    }
}

/** A sheet feeding out of the printer, its lines printing top to bottom, for the length of a print run. */
@Composable
private fun PrintingAnimation() {
    val c = Boar.colors
    val feed = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { feed.animateTo(1f, androidx.compose.animation.core.tween(PRINT_MS.toInt(), easing = androidx.compose.animation.core.LinearEasing)) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(160.dp, 44.dp).background(c.bgCardElevated, RoundedCornerShape(10.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.size(8.dp).align(Alignment.TopEnd).padding(1.dp).background(if ((feed.value * 12).toInt() % 2 == 0) c.emerald400 else c.emeraldBgSubtle, RoundedCornerShape(4.dp)))
            Box(Modifier.size(120.dp, 4.dp).background(Color.Black, RoundedCornerShape(2.dp)))
        }
        val sheetH = 150.dp * feed.value
        Box(Modifier.size(112.dp, sheetH).background(Color.White).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val lines = (feed.value * 14).toInt()
                repeat(lines) { i ->
                    val w = when { i == 0 -> 0.7f; i % 5 == 4 -> 0.45f; else -> 0.92f - (i % 3) * 0.08f }
                    Box(Modifier.fillMaxWidth(w).size(width = 0.dp, height = if (i == 0) 5.dp else 3.dp).background(if (i == 0) Color(0xFF1D1D1F) else Color(0xFF888888), RoundedCornerShape(1.dp)))
                }
            }
        }
        Text(t("native.labs.printer.compiling"), style = Boar.type.caption, color = c.textAccentEmerald, modifier = Modifier.padding(top = 10.dp))
    }
}

/** "Show page breaks" (Press's project sheet): on unless switched off. */
private const val BREAKS_PREF = "printer.pageBreaks"
