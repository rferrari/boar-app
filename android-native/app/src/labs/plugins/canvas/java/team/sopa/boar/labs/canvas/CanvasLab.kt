package team.sopa.boar.labs.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.drawText
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import team.sopa.boar.engine.canvas.Visual
import team.sopa.boar.engine.canvas.VisualScale
import team.sopa.boar.engine.canvas.WidgetHtml
import team.sopa.boar.labs.LabAction
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.plugins.AnswerRef
import team.sopa.boar.plugins.UserData
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Boar Canvas: an answer becomes something to look at or play with, the idea of CopilotKit's Open Intelligent UI
 * (github.com/CopilotKit/OpenIntelligentUI) brought on the phone and offline. From an answer's 🧩 sheet:
 * - 📊 Visual: the chat model reads the answer (and earlier answers' facts with numbers, VisualContext), picks a type
 *   (bars, line, pie, table, steps, compare, facts, or none) and copies the data; the app checks every number and label
 *   against those answers (Visual) and draws it, with a switch between bars, line, pie and table on the same data.
 * - 🧪 Widget: the chat model writes a small HTML page (a calculator, a quiz, a slider) shown in a WebView that
 *   can't reach the network, files or the app (WidgetHtml's policy plus the WebView's settings).
 * Both run on the model that answered, after the answer, never while the chat is answering.
 */
object CanvasLab : LabPlugin {
    override val id = "canvas"
    override val emoji = "📊"
    override val status = LabStatus.ALPHA

    @Composable
    override fun answerActions(a: AnswerRef): List<LabAction> {
        val ctx = LocalContext.current
        if (!Canvas.allowed(a)) return emptyList()
        val st = Canvas.status(ctx, a.id)
        val making = Canvas.busy()
        val free = !making && !a.chatBusy
        @Composable fun label(kind: Canvas.Kind, key: String) = when {
            st is Canvas.Status.Making && st.kind == kind -> t("native.labs.canvas.making")
            st is Canvas.Status.Done && st.kind == kind -> t("native.labs.canvas.again.$key")
            else -> t("native.labs.canvas.make.$key")
        }
        // One visual, the model picks its type; the free-form widget stays as a second, Labs-only way until the eval decides.
        val visualDone = (st as? Canvas.Status.Done)?.kind.let { it == Canvas.Kind.VISUAL || it == Canvas.Kind.CARD }
        return listOf(
            LabAction("📊", label(Canvas.Kind.VISUAL, "visual"), done = visualDone, enabled = free) { Canvas.start(ctx, a, Canvas.Kind.VISUAL) },
            LabAction("🧪", label(Canvas.Kind.WIDGET, "widget"), done = (st as? Canvas.Status.Done)?.kind == Canvas.Kind.WIDGET, enabled = free) { Canvas.start(ctx, a, Canvas.Kind.WIDGET) },
        )
    }

    @Composable
    override fun AnswerExtra(a: AnswerRef) {
        val ctx = LocalContext.current
        val c = Boar.colors
        when (val st = Canvas.status(ctx, a.id)) {
            null -> {}
            is Canvas.Status.Making -> Text(t(if (st.kind == Canvas.Kind.WIDGET) "native.labs.canvas.makingWidget" else "native.labs.canvas.makingVisual"),
                style = Boar.type.caption, color = c.textAccentEmerald)
            is Canvas.Status.Failed -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t(st.key) + (st.detail?.let { " (" + t("native.labs.canvas.refused", "what" to it) + ")" } ?: ""),
                    style = Boar.type.caption, color = c.textMuted, modifier = Modifier.weight(1f, fill = false))
                Text("✕", style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.clickable { Canvas.forget(ctx, a.id) }.padding(4.dp))
            }
            is Canvas.Status.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (st.kind) {
                    // A card saved before the merge reads as a visual (Visual is a superset of CardSpec).
                    Canvas.Kind.CARD, Canvas.Kind.VISUAL ->
                        (Visual.parse(st.raw, Canvas.grounding(a, st.context), a.question) as? Visual.Parsed.Ok)?.let { VisualView(it.visual) }
                    Canvas.Kind.WIDGET -> WidgetHtml.extract(st.raw)?.let { WidgetView(it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((if (st.kind == Canvas.Kind.WIDGET) "🧪 " else "📊 ") + t("native.labs.canvas.madeBy", "seconds" to String.format(Locale.US, "%.0f", st.seconds)) +
                        if (st.context.isNotBlank()) " · " + t("native.labs.canvas.withEarlier") else "",
                        style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.weight(1f, fill = false))
                    Text("✕", style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.clickable { Canvas.forget(ctx, a.id) }.padding(4.dp))
                }
            }
        }
    }

    override fun eraseUserData(context: Context, scope: UserData.Scope) = Canvas.eraseAll(context)

    @Composable
    override fun Content() {
        val c = Boar.colors
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("native.labs.canvas.intro"), style = Boar.type.body, color = c.textPrimary)
            Text(t("native.labs.canvas.howVisual"), style = Boar.type.body, color = c.textPrimary)
            Text(t("native.labs.canvas.howWidget"), style = Boar.type.body, color = c.textPrimary)
            Text(t("native.labs.canvas.safety"), style = Boar.type.subtext, color = c.textMuted)
            Text(t("native.labs.canvas.example"), style = Boar.type.subtext, color = c.textMuted)
            VisualView(Visual.Series(t("native.labs.canvas.exampleTitle"), Visual.SeriesKind.BARS, "m", listOf("Everest" to 8849.0, "K2" to 8611.0, "Kangchenjunga" to 8586.0)))
            Text(t("native.labs.canvas.credit"), style = Boar.type.subtext, color = c.textDim)
        }
    }
}

@Composable
private fun CardFrame(title: String, content: @Composable () -> Unit) {
    val c = Boar.colors
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bgCard).border(1.dp, c.borderSubtle, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotBlank()) Text(title, style = Boar.type.titleSm, color = c.textHeading, fontWeight = FontWeight.SemiBold)
        content()
    }
}

/**
 * A visual, drawn by the app: the model picked its type and data, the app checked them against the answer. A toolbar
 * switches the same numbers between bars, line, pie and a table (no new model call), copies the visual as Markdown, and
 * opens it full screen.
 */
@Composable
internal fun VisualView(visual: Visual) {
    var expanded by remember { mutableStateOf(false) }
    var shown by remember(visual) { mutableStateOf(visual) }
    CardFrame(visual.title) {
        VisualToolbar(visual, shown, { shown = it }, onExpand = { expanded = true })
        VisualBody(shown, large = false)
    }
    if (expanded) androidx.compose.ui.window.Dialog(onDismissRequest = { expanded = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        val c = Boar.colors
        Column(Modifier.fillMaxSize().background(c.bgSurface).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(visual.title, style = Boar.type.title, color = c.textHeading, modifier = Modifier.weight(1f))
                Text(t("common.done"), style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { expanded = false }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            VisualToolbar(visual, shown, { shown = it }, onExpand = null)
            VisualBody(shown, large = true)
        }
    }
}

/** The views the data allows (VisualScale.kinds), Copy, and Expand. */
@Composable
private fun VisualToolbar(original: Visual, shown: Visual, onShow: (Visual) -> Unit, onExpand: (() -> Unit)?) {
    val c = Boar.colors
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val series = original as? Visual.Series
        if (series != null) for (k in VisualScale.kinds(series)) {
            val target: Visual? = when (k) {
                "table" -> VisualScale.asTable(series)
                "line" -> VisualScale.asKind(series, Visual.SeriesKind.LINE)
                "share" -> VisualScale.asKind(series, Visual.SeriesKind.SHARE)
                else -> VisualScale.asKind(series, Visual.SeriesKind.BARS)
            }
            val on = when (shown) { is Visual.Series -> k == shown.kind.name.lowercase().let { if (it == "share") "share" else it }; is Visual.Table -> k == "table"; else -> false }
            Chip(t("native.labs.canvas.view.$k"), on) { target?.let(onShow) }
        }
        Chip(if (copied) t("native.labs.canvas.copied") else t("native.labs.canvas.copy"), false) {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(VisualScale.markdown(shown)))
            copied = true
        }
        onExpand?.let { Chip(t("native.labs.canvas.expand"), false, it) }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = Boar.colors
    Text(label, style = Boar.type["mono.xs"], color = if (on) c.textInverse else c.textSecondary, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) c.emerald500 else c.bgSubtle).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp))
}

/** The palette for the parts of a pie and the points of a line: the theme's accents, then fixed hues that read on both themes. */
@Composable
private fun palette(): List<Color> {
    val c = Boar.colors
    return listOf(c.emerald500, c.textAccentCyan, c.amber400, c.crimson400, Color(0xFF8B7CF6), Color(0xFF5BA4F5), Color(0xFFE879A8), Color(0xFF9CA3AF))
}

@Composable
private fun VisualBody(v: Visual, large: Boolean) {
    val c = Boar.colors
    val type = Boar.type
    when (v) {
        is Visual.Table -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            v.columns.forEachIndexed { i, col ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(col.uppercase(), style = type.micro, color = c.textSecondary, fontWeight = FontWeight.Bold)
                    v.rows.forEach { r -> Text(r.getOrElse(i) { "" }, style = type.body, color = c.textPrimary, modifier = Modifier.widthIn(max = 180.dp)) }
                }
            }
        }
        is Visual.Series -> when (v.kind) {
            Visual.SeriesKind.BARS -> {
                val max = v.items.maxOf { kotlin.math.abs(it.second) }.takeIf { it > 0 } ?: 1.0
                v.items.forEach { (label, x) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row {
                            Text(label, style = type.caption, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Text(number(x) + (v.unit?.let { " $it" } ?: ""), style = type["mono.xs"], color = c.textSecondary)
                        }
                        Box(Modifier.fillMaxWidth().height(if (large) 16.dp else 10.dp).clip(RoundedCornerShape(5.dp)).background(c.bgSubtle)) {
                            Box(Modifier.fillMaxWidth((kotlin.math.abs(x) / max).toFloat().coerceIn(0.02f, 1f)).height(if (large) 16.dp else 10.dp).clip(RoundedCornerShape(5.dp)).background(c.emerald500))
                        }
                    }
                }
            }
            Visual.SeriesKind.LINE -> LineChart(v, large)
            Visual.SeriesKind.SHARE -> ShareChart(v, large)
        }
        is Visual.Steps -> v.steps.forEachIndexed { i, s ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${i + 1}", style = type["mono.xs"], color = c.textAccentEmerald, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                Text(s, style = type.body, color = c.textPrimary)
            }
        }
        is Visual.Compare -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            v.items.forEach { (name, points) ->
                Column(Modifier.width(170.dp).clip(RoundedCornerShape(10.dp)).background(c.bgSubtle).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = type.titleSm, color = c.textAccentEmerald, fontWeight = FontWeight.SemiBold)
                    points.forEach { Text("• $it", style = type.caption, color = c.textPrimary) }
                }
            }
        }
        is Visual.Facts -> v.facts.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(label, style = type.caption, color = c.textSecondary, modifier = Modifier.weight(0.4f))
                Text(value, style = type.body, color = c.textPrimary, modifier = Modifier.weight(0.6f))
            }
        }
    }
}

/** Points joined in order on an axis around the data (VisualScale.axis), each with its value; the labels under it. */
@Composable
private fun LineChart(v: Visual.Series, large: Boolean) {
    val c = Boar.colors
    val values = v.items.map { it.second }
    val axis = remember(v) { VisualScale.axis(values, fromZero = false) }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val valueStyle = Boar.type["mono.xs"].copy(color = c.textSecondary)
    val line = c.emerald500
    val grid = c.borderSubtle
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(if (large) 260.dp else 150.dp).padding(top = 14.dp, start = 6.dp, end = 6.dp)) {
            val span = (axis.max - axis.min).takeIf { it > 0 } ?: 1.0
            fun y(x: Double) = (size.height * (1 - (x - axis.min) / span)).toFloat()
            fun xAt(i: Int) = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
            axis.ticks.forEach { tick -> drawLine(grid, androidx.compose.ui.geometry.Offset(0f, y(tick)), androidx.compose.ui.geometry.Offset(size.width, y(tick)), 1f) }
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { i, x -> if (i == 0) path.moveTo(xAt(i), y(x)) else path.lineTo(xAt(i), y(x)) }
            drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            values.forEachIndexed { i, x ->
                drawCircle(line, radius = 5f, center = androidx.compose.ui.geometry.Offset(xAt(i), y(x)))
                val text = measurer.measure(number(x), valueStyle)
                drawText(text, topLeft = androidx.compose.ui.geometry.Offset((xAt(i) - text.size.width / 2).coerceIn(0f, size.width - text.size.width), y(x) - text.size.height - 4))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            v.items.forEach { (label, _) -> Text(label, style = Boar.type.micro, color = c.textSecondary, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        v.unit?.let { Text(it, style = Boar.type.micro, color = c.textDim) }
    }
}

/** A donut of the parts, and a legend with each value and its share of the whole (VisualScale.percents adds up to 100). */
@Composable
private fun ShareChart(v: Visual.Series, large: Boolean) {
    val c = Boar.colors
    val values = v.items.map { it.second }
    val pct = remember(v) { VisualScale.percents(values) }
    val colors = palette()
    val total = values.sum()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(if (large) 180.dp else 110.dp)) {
            var start = -90f
            values.forEachIndexed { i, x ->
                val sweep = (x / total * 360).toFloat()
                drawArc(colors[i % colors.size], start, sweep, useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.22f))
                start += sweep
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            v.items.forEachIndexed { i, (label, x) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colors[i % colors.size]))
                    Text("$label  " + number(x) + (v.unit?.let { " $it" } ?: "") + "  (${pct[i]}%)", style = Boar.type.caption, color = c.textPrimary)
                }
            }
        }
    }
}

private fun number(v: Double): String = if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) String.format(Locale.getDefault(), "%,d", v.toLong()) else String.format(Locale.getDefault(), "%,.2f", v)

private fun Color.hex() = String.format("#%06X", 0xFFFFFF and toArgb())

/**
 * The widget, locked down: script runs, but nothing loads (the page's policy, blockNetworkLoads, and every request
 * answered empty), no files or content URIs, no JavaScript bridge, no new windows, no navigation, no Safe Browsing
 * lookups (they'd send the page's URLs to Google).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WidgetView(page: String) {
    val c = Boar.colors
    val doc = WidgetHtml.wrap(page, WidgetHtml.Theme(c.bgCard.hex(), c.textPrimary.hex(), c.textMuted.hex(), c.emerald500.hex(), c.bgSubtle.hex(), dark = c.bgCard.luminance() < 0.5f))
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.blockNetworkImage = true
                settings.domStorageEnabled = false
                settings.setGeolocationEnabled(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.safeBrowsingEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                    // The widget's own page passes (WidgetHtml.allowedUrl); everything else gets an empty 403.
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        if (team.sopa.boar.engine.canvas.WidgetHtml.allowedUrl(request.url?.toString())) null
                        else WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadDataWithBaseURL(null, doc, "text/html", "utf-8", null)
            }
        },
        update = { /* the page is fixed once made */ },
        onRelease = { it.destroy() },
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().height(440.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(12.dp)),
    )
}
