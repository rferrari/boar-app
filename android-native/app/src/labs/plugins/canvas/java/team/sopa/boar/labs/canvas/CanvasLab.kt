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
import team.sopa.boar.engine.canvas.Card
import team.sopa.boar.engine.canvas.CardSpec
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
 * - 📊 Card: the chat model writes a small spec (table, bars, steps, compare, facts) and the app draws it natively;
 *   a chart's numbers must all be in the answer (CardSpec).
 * - 🧪 Widget: the chat model writes a small HTML page (a calculator, a quiz, a slider) shown in a WebView that
 *   can't reach the network, files or the app (WidgetHtml's policy plus the WebView's settings).
 * Both run on the model that answered, after the answer, never while the chat is answering.
 */
object CanvasLab : LabPlugin {
    override val id = "canvas"
    override val emoji = "📊"
    // TODO(rc3): its own icon. An existing Boar avatar stands in for now (adam, Nov 10).
    override val art = team.sopa.boar.R.drawable.truffle_pixel_hacker
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
        return listOf(
            LabAction("📊", label(Canvas.Kind.CARD, "card"), done = (st as? Canvas.Status.Done)?.kind == Canvas.Kind.CARD, enabled = free) { Canvas.start(ctx, a, Canvas.Kind.CARD) },
            LabAction("🧪", label(Canvas.Kind.WIDGET, "widget"), done = (st as? Canvas.Status.Done)?.kind == Canvas.Kind.WIDGET, enabled = free) { Canvas.start(ctx, a, Canvas.Kind.WIDGET) },
        )
    }

    @Composable
    override fun AnswerExtra(a: AnswerRef) {
        val ctx = LocalContext.current
        val c = Boar.colors
        when (val st = Canvas.status(ctx, a.id)) {
            null -> {}
            is Canvas.Status.Making -> Text(t(if (st.kind == Canvas.Kind.CARD) "native.labs.canvas.makingCard" else "native.labs.canvas.makingWidget"),
                style = Boar.type.caption, color = c.textAccentEmerald)
            is Canvas.Status.Failed -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t(st.key), style = Boar.type.caption, color = c.textMuted, modifier = Modifier.weight(1f, fill = false))
                Text("✕", style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.clickable { Canvas.forget(ctx, a.id) }.padding(4.dp))
            }
            is Canvas.Status.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (st.kind) {
                    Canvas.Kind.CARD -> (CardSpec.parse(st.raw, a.text) as? CardSpec.Parsed.Ok)?.let { CardView(it.card) }
                    Canvas.Kind.WIDGET -> WidgetHtml.extract(st.raw)?.let { WidgetView(it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((if (st.kind == Canvas.Kind.CARD) "📊 " else "🧪 ") + t("native.labs.canvas.madeBy", "seconds" to String.format(Locale.US, "%.0f", st.seconds)),
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
            Text(t("native.labs.canvas.howCard"), style = Boar.type.body, color = c.textPrimary)
            Text(t("native.labs.canvas.howWidget"), style = Boar.type.body, color = c.textPrimary)
            Text(t("native.labs.canvas.safety"), style = Boar.type.subtext, color = c.textMuted)
            Text(t("native.labs.canvas.example"), style = Boar.type.subtext, color = c.textMuted)
            CardView(Card.Bars(t("native.labs.canvas.exampleTitle"), "m", listOf("Everest" to 8849.0, "K2" to 8611.0, "Kangchenjunga" to 8586.0)))
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

@Composable
internal fun CardView(card: Card) {
    val c = Boar.colors
    val type = Boar.type
    CardFrame(card.title) {
        when (card) {
            is Card.Table -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                card.columns.forEachIndexed { i, col ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(col.uppercase(), style = type.micro, color = c.textSecondary, fontWeight = FontWeight.Bold)
                        card.rows.forEach { r -> Text(r.getOrElse(i) { "" }, style = type.body, color = c.textPrimary, modifier = Modifier.widthIn(max = 180.dp)) }
                    }
                }
            }
            is Card.Bars -> {
                val max = card.items.maxOf { kotlin.math.abs(it.second) }.takeIf { it > 0 } ?: 1.0
                card.items.forEach { (label, v) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row {
                            Text(label, style = type.caption, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Text(number(v) + (card.unit?.let { " $it" } ?: ""), style = type["mono.xs"], color = c.textSecondary)
                        }
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.bgSubtle)) {
                            Box(Modifier.fillMaxWidth((kotlin.math.abs(v) / max).toFloat().coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.emerald500))
                        }
                    }
                }
            }
            is Card.Steps -> card.steps.forEachIndexed { i, s ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}", style = type["mono.xs"], color = c.textAccentEmerald, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                    Text(s, style = type.body, color = c.textPrimary)
                }
            }
            is Card.Compare -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                card.items.forEach { (name, points) ->
                    Column(Modifier.width(170.dp).clip(RoundedCornerShape(10.dp)).background(c.bgSubtle).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, style = type.titleSm, color = c.textAccentEmerald, fontWeight = FontWeight.SemiBold)
                        points.forEach { Text("• $it", style = type.caption, color = c.textPrimary) }
                    }
                }
            }
            is Card.Facts -> card.facts.forEach { (label, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(label, style = type.caption, color = c.textSecondary, modifier = Modifier.weight(0.4f))
                    Text(value, style = type.body, color = c.textPrimary, modifier = Modifier.weight(0.6f))
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
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                        WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
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
