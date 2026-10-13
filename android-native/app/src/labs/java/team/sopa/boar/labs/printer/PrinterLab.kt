package team.sopa.boar.labs.printer

import android.content.Intent
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
import androidx.core.content.FileProvider
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
 * Field Printer: the research basket as a document. Answers come in from any chat ("＋ Basket"); here they're
 * reordered, edited or dropped, up to two Image Gen drawings are picked, and Compile writes an A4 PDF to share or save.
 */
object PrinterLab : LabPlugin {
    override val id = "printer"
    override val emoji = "🖨️"
    override val status = LabStatus.BETA

    const val MAX_IMAGES = 2

    /** 📎 in the answer's 🧩 sheet: into the basket with its question, sources and chat title. */
    @Composable
    override fun answerActions(a: team.sopa.boar.plugins.AnswerRef): List<team.sopa.boar.labs.LabAction> {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        remember { Basket.load(ctx) }
        val added = Basket.items.collectAsState().value.any { it.id == a.id }
        return listOf(team.sopa.boar.labs.LabAction("📎", t(if (added) "native.labs.printer.inBasket" else "native.labs.printer.addToBasket"), done = added) {
            if (added) Basket.remove(a.id)
            else scope.launch(Dispatchers.IO) {
                val title = runCatching { team.sopa.boar.app.BoarServices.get(ctx).chats.listSessions().firstOrNull { it.id == a.sessionId }?.title }.getOrNull().orEmpty()
                Basket.add(ctx, Basket.Item(a.id, title, a.question, a.text, a.sources.map { Basket.Source(it.first, it.second) }, System.currentTimeMillis()))
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
        Text("📎 $n", style = Boar.type["mono.xs"], color = c.textAccentEmerald, fontWeight = FontWeight.ExtraBold,
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
        var title by rememberSaveable { mutableStateOf("") }
        var editing by remember { mutableStateOf<String?>(null) }
        var picked by remember { mutableStateOf(listOf<File>()) }
        var compiling by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val gallery = remember { File(ctx.filesDir, "labs/images").listFiles { f -> f.extension == "png" }.orEmpty().sortedByDescending { it.lastModified() } }
        val defaultTitle = items.firstOrNull()?.chatTitle?.takeIf { it.isNotBlank() } ?: t("native.labs.printer.untitled")

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(t("native.labs.printer.items", "count" to items.size), Modifier.weight(1f))
                Text(t("native.labs.printer.clear"), style = Boar.type["mono.xs"], color = c.crimson400, modifier = Modifier.clickable { Basket.clear() }.padding(4.dp))
            }
            items.forEachIndexed { i, item ->
                val open = editing == item.id
                Column(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(10.dp)).border(1.dp, if (open) c.emeraldBorder else c.borderSubtle, RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${i + 1}", style = Boar.type["mono.xs"], color = c.textAccentEmerald, fontWeight = FontWeight.ExtraBold)
                        Text(item.question.ifBlank { item.chatTitle }, style = Boar.type.titleSm.copy(fontSize = 13.sp), color = c.textHeading, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon("▲", i > 0) { Basket.move(item.id, -1) }
                        Icon("▼", i < items.size - 1) { Basket.move(item.id, 1) }
                        Icon(if (open) "✓" else "✎", true) { editing = if (open) null else item.id }
                        Icon("✕", true) { Basket.remove(item.id) }
                    }
                    if (open) {
                        var draft by remember(item.id) { mutableStateOf(item.text) }
                        BasicTextField(draft, { draft = it; Basket.edit(item.id, it) }, textStyle = Boar.type.caption.copy(color = c.textPrimary), cursorBrush = SolidColor(c.textAccentEmerald),
                            modifier = Modifier.fillMaxWidth().background(c.bgSurface, RoundedCornerShape(6.dp)).padding(8.dp))
                    } else Text(PdfCompiler.plain(item.text), style = Boar.type.caption, color = c.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (item.sources.isNotEmpty()) Text(t("native.labs.printer.sources", "count" to item.sources.size), style = Boar.type["mono.xs"], color = c.textDim)
                }
            }

            Label(t("native.labs.printer.images", "max" to MAX_IMAGES))
            if (gallery.isEmpty()) Text(t("native.labs.printer.noImages"), style = Boar.type.caption, color = c.textMuted)
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(gallery, key = { it.path }) { f ->
                    val n = picked.indexOf(f)
                    Box(Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).border(2.dp, if (n >= 0) c.emerald400 else c.borderSubtle, RoundedCornerShape(8.dp))
                        .clickable { picked = if (n >= 0) picked - f else if (picked.size < MAX_IMAGES) picked + f else picked }) {
                        Thumb(f)
                        if (n >= 0) Text("${n + 1}", style = Boar.type["mono.xs"], color = c.textHeading, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(c.emeraldBgSubtle, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp))
                    }
                }
            }

            error?.let { Text(it, style = Boar.type.caption, color = c.crimson500) }
            if (compiling) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = c.emerald400, strokeWidth = 1.5.dp)
                Text(t("native.labs.printer.compiling"), style = Boar.type.caption, color = c.textAccentEmerald)
            } else Text(t("native.labs.printer.compile"), style = Boar.type.titleSm.copy(fontSize = 14.sp), color = c.textAccentEmerald,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.emeraldBgSubtle).border(1.dp, c.emeraldBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        compiling = true; error = null
                        val docTitle = title.ifBlank { defaultTitle }
                        val footer = I18n.t(ctx, "native.labs.printer.footer")
                        scope.launch {
                            val out = File(File(ctx.cacheDir, "exports"), slug(docTitle) + ".pdf")
                            val ok = withContext(Dispatchers.IO) { runCatching { PdfCompiler.compile(out, docTitle, items, picked, footer) } }
                            compiling = false
                            ok.onSuccess {
                                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, docTitle))
                            }.onFailure { error = it.message }
                        }
                    }.padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifEmpty { "boar-document" }

    @Composable
    private fun Label(text: String, modifier: Modifier = Modifier) = Text(text, style = Boar.type["mono.xs"], color = Boar.colors.textDim, fontWeight = FontWeight.Bold, modifier = modifier)

    @Composable
    private fun Icon(glyph: String, enabled: Boolean, onClick: () -> Unit) = Text(glyph, fontSize = 15.sp, color = Boar.colors.textAccentEmerald,
        modifier = Modifier.alpha(if (enabled) 1f else 0.25f).clip(RoundedCornerShape(4.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp))

    @Composable
    private fun Thumb(f: File) {
        val bmp by produceState<ImageBitmap?>(null, f.path) { value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() } }
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
