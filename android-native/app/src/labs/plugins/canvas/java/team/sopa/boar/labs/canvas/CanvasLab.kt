package team.sopa.boar.labs.canvas

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import team.sopa.boar.engine.canvas.AnswerCard
import team.sopa.boar.engine.canvas.CardRules
import team.sopa.boar.engine.canvas.CardRules.Shape
import team.sopa.boar.engine.canvas.VisualScale
import team.sopa.boar.labs.LabAction
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.plugins.AnswerRef
import team.sopa.boar.plugins.UserData
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * Cards (the Canvas plugin redesigned with adam, Nov 14; t9 0207 item 3; the id stays "canvas", so switches and saved
 * cards still read). Part of an answer becomes a native card: a chart, a table, a checklist, a comparison or key facts.
 * - Only when asked: the answer's plugins sheet has one row, which already says what the rules found ("Create a card" ·
 *   "A chart of 3 numbers"). Rules first (CardRules): the app reads the answer's own text and draws at once, no model.
 * - Nothing found: "Nothing to draw here" · "Ask BOAR to make one · slower": the model makes it as Visual did, its card
 *   still checked against the answer and marked "made by the model · checked against the answer".
 * - The card sits under the answer's text, in the same answer card: title, where it came from, the body, one switch with
 *   only the shapes that fit, ✕ to remove it. No other button (adam: "remove those buttons… keep it simple"); the
 *   answer's own Add to Press takes the card along.
 * - Steps are a checklist; the ticks are saved with the answer (Canvas keeps both in the message's meta).
 * - Gone (adam, Nov 14): the model-written page in a WebView, "Make it interactive", Copy and Expand.
 */
object CanvasLab : LabPlugin {
    override val id = "canvas"
    override val emoji = "📊"
    // TODO(rc3): its own icon. An existing Boar avatar stands in for now (adam, Nov 10).
    override val art = team.sopa.boar.R.drawable.truffle_pixel_hacker
    // BETA so it can start on (t9 0207 item 5: "switch it on by default, so the test APK carries it"); it counts as done
    // only after the validator's gate and adam's hand test.
    override val status = LabStatus.BETA

    /**
     * The one row in the answer's plugins sheet, with what the rules found as its second line (adam, Nov 14). The rules
     * run when the row is drawn, so the line is true before the tap.
     */
    @Composable
    override fun answerActions(a: AnswerRef): List<LabAction> {
        val ctx = LocalContext.current
        if (!Canvas.allowed(a)) return emptyList()
        val cards = remember(a.question, a.text) { runCatching { CardRules.find(a.question, a.text) }.getOrDefault(emptyList()) }
        val shown = Canvas.shown(ctx, a)
        val st = Canvas.status(a.id)
        val free = !Canvas.busy() && !a.chatBusy
        val name = t("native.labs.canvas.title")
        return listOf(when {
            st is Canvas.Status.Making -> LabAction("📊", name, enabled = false, art = art, detail = t("native.labs.canvas.making")) {}
            shown != null -> LabAction("📊", name, done = true, enabled = false, art = art, detail = t("native.labs.canvas.underAnswer")) {}
            cards.isNotEmpty() -> {
                val best = cards.first()
                LabAction("📊", t("native.labs.canvas.make.card"), art = art, detail = foundLine(cards)) {
                    Canvas.show(ctx, a.id, AnswerCard.of(best, best.shapes.first(), AnswerCard.titleFor(best, a.question, a.sources.map { it.first })), a.text)
                }
            }
            else -> {
                val fallback = AnswerCard.titleFor(CardRules.Card.Steps(emptyList()), a.question, a.sources.map { it.first })
                LabAction("📊", t("native.labs.canvas.nothingHere"), enabled = free, art = art, detail = t("native.labs.canvas.askModel")) {
                    Canvas.start(ctx, a, fallback)
                }
            }
        })
    }

    /** "A chart of 3 numbers", or the best two: "A checklist of 4 steps, or a chart of 4 numbers". */
    @Composable
    private fun foundLine(cards: List<CardRules.Card>): String {
        val lines = cards.take(2).map { c ->
            val (kind, n) = AnswerCard.found(c)
            when (kind) {
                AnswerCard.Found.CHART -> t("native.labs.canvas.found.chart", "count" to n)
                AnswerCard.Found.TABLE -> t("native.labs.canvas.found.table", "count" to n)
                AnswerCard.Found.STEPS -> t("native.labs.canvas.found.steps", "count" to n)
                AnswerCard.Found.FACTS -> t("native.labs.canvas.found.facts", "count" to n)
                AnswerCard.Found.COMPARE -> t("native.labs.canvas.found.compare${n.coerceIn(2, 4)}")
            }
        }
        return if (lines.size < 2) lines.first()
        else t("native.labs.canvas.found.or", "first" to lines[0], "second" to lines[1].replaceFirstChar { it.lowercase() })
    }

    /** Under the answer's text: the wait line, a refusal, or the card. */
    @Composable
    override fun AnswerExtra(a: AnswerRef) {
        val ctx = LocalContext.current
        val c = Boar.colors
        when (val st = Canvas.status(a.id)) {
            Canvas.Status.Making -> Text(t("native.labs.canvas.makingVisual"), style = Boar.type.caption, color = c.textAccentEmerald)
            is Canvas.Status.Failed -> Failed(a, st)
            null -> {}
        }
        val card = Canvas.shown(ctx, a) ?: return
        // The switch: every shape of every card the rules found; none for the model's card (it has the one it made).
        val cards = remember(a.question, a.text, card.byModel) {
            if (card.byModel) emptyList() else runCatching { CardRules.find(a.question, a.text) }.getOrDefault(emptyList())
        }
        val choices = remember(cards) { AnswerCard.choices(cards) }
        // The pill that is on: the choice whose card, in its shape, is the one shown.
        val current = remember(choices, card) { choices.firstOrNull { ch -> ch.shape == card.shape && AnswerCard.of(cards[ch.card], ch.shape, "").body == card.body } }
        CardView(card, choices, current, ticks = Canvas.ticksOf(a.id),
            onPick = { ch -> Canvas.show(ctx, a.id, AnswerCard.of(cards[ch.card], ch.shape, AnswerCard.titleFor(cards[ch.card], a.question, a.sources.map { it.first })), a.text) },
            onTick = { Canvas.tick(ctx, a.id, it) },
            onRemove = { Canvas.show(ctx, a.id, null, a.text) })
    }

    /** Why the model's card wasn't drawn, what was refused, and the model's reply on demand; ✕ forgets it. */
    @Composable
    private fun Failed(a: AnswerRef, st: Canvas.Status.Failed) {
        val c = Boar.colors
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t(st.key), style = Boar.type.caption, color = c.textMuted, modifier = Modifier.weight(1f, fill = false))
                Text("✕", style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.minimumInteractiveComponentSize()
                    .clickable(onClickLabel = t("native.a11y.close")) { Canvas.cancel(a.id) }.padding(4.dp))
            }
            st.detail?.let { Text(t("native.labs.canvas.refused", "what" to it), style = Boar.type["mono.xs"], color = c.amber400, fontWeight = FontWeight.Bold) }
            st.raw?.takeIf { it.isNotBlank() }?.let { raw ->
                var open by remember(a.id) { mutableStateOf(false) }
                Text(t(if (open) "native.labs.canvas.hideReply" else "native.labs.canvas.showReply"), style = Boar.type["mono.xs"], color = c.textAccentCyan,
                    fontWeight = FontWeight.Bold, modifier = Modifier.clickable { open = !open }.padding(vertical = 2.dp))
                if (open) androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(raw.take(4000), style = Boar.type["mono.xs"], color = c.textSecondary,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.bgSubtle).padding(8.dp))
                }
            }
        }
    }

    /** Cards are kept in the answers' meta: they go with the chats. Here the memory and any file from before Cards. */
    override fun eraseUserData(context: Context, scope: UserData.Scope) = Canvas.eraseAll(context)

    override fun eraseAnswers(context: Context, answerIds: List<String>) = answerIds.forEach { Canvas.forget(context, it) }

    @Composable
    override fun Content() {
        // The plugin page template (Nov 12): its example is a live card, and the Try rows ask questions whose answers hold one.
        val title = t("native.labs.canvas.exampleTitle")
        val example = remember(title) {
            AnswerCard(Shape.BARS, title, AnswerCard.Body.Series("m", listOf(CardRules.Point("Everest", "8,849 m", 8849.0),
                CardRules.Point("K2", "8,611 m", 8611.0), CardRules.Point("Kangchenjunga", "8,586 m", 8586.0))))
        }
        var shownExample by remember(example) { mutableStateOf(example) }
        team.sopa.boar.labs.PluginPage(null, t("native.labs.canvas.example"), null, (1..2).map { t("native.labs.canvas.example$it") },
            t("native.labs.canvas.how"), exampleLabel = t("native.labs.page.example"), tryHint = t("native.labs.canvas.tryHint")) {
            val exampleChoices = listOf(AnswerCard.Choice(0, Shape.BARS), AnswerCard.Choice(0, Shape.TABLE))
            CardView(shownExample, exampleChoices, exampleChoices.first { it.shape == shownExample.shape }, ticks = emptySet(),
                onPick = { shownExample = example.copy(shape = it.shape) }, onTick = {}, onRemove = null)
        }
    }
}

/**
 * The card under an answer's text, in the same answer card (adam, Nov 14): a rule above it, the title and where it came
 * from, ✕, the body, then the switch when more than one shape fits. Theme tokens only.
 */
@Composable
internal fun CardView(card: AnswerCard, choices: List<AnswerCard.Choice>, current: AnswerCard.Choice?, ticks: Set<Int>, onPick: (AnswerCard.Choice) -> Unit,
                      onTick: (Int) -> Unit, onRemove: (() -> Unit)?) {
    val c = Boar.colors
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderSubtle))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                if (card.title.isNotBlank()) Text(card.title, style = Boar.type.titleSm, color = c.textHeading, fontWeight = FontWeight.SemiBold)
                Text(t(if (card.byModel) "native.labs.canvas.byModel" else "native.labs.canvas.fromAnswer"), style = Boar.type["mono.xs"], color = c.textDim)
            }
            onRemove?.let {
                Text("✕", style = Boar.type.body, color = c.textMuted, modifier = Modifier.clip(CircleShape)
                    .clickable(onClickLabel = t("native.labs.canvas.remove"), onClick = it).minimumInteractiveComponentSize().padding(4.dp))
            }
        }
        CardBody(card, ticks, onTick)
        if (card.leftOut.isNotEmpty()) Text(t("native.labs.canvas.leftOut", "what" to card.leftOut.take(8).joinToString(", ")), style = Boar.type.caption, color = c.amber400)
        if (choices.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (ch in choices) ShapePill(t("native.labs.canvas.view.${ch.shape.name.lowercase()}"), ch == current) { onPick(ch) }
        }
    }
}

/** One shape in the switch: the accent ring and tint when on, as the plugin pages' pills. */
@Composable
private fun ShapePill(label: String, on: Boolean, onClick: () -> Unit) {
    val c = Boar.colors
    Text(label, style = Boar.type.caption, maxLines = 1, color = if (on) c.textAccentEmerald else c.textSecondary,
        modifier = Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50)).background(if (on) c.emeraldBgSubtle else c.bgCard)
            .border(1.dp, if (on) c.emerald400 else c.borderDefault, RoundedCornerShape(50))
            .semantics { role = Role.Tab; stateDescription = if (on) "✓" else "" }
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
private fun CardBody(card: AnswerCard, ticks: Set<Int>, onTick: (Int) -> Unit) {
    val c = Boar.colors
    val type = Boar.type
    when (val b = card.body) {
        is AnswerCard.Body.Series -> when (card.shape) {
            Shape.PIE -> Pie(b)
            Shape.LINE -> Line(b)
            Shape.TABLE -> TableView(emptyList(), b.points.map { listOf(it.label, it.written) })
            else -> Bars(b)
        }
        is AnswerCard.Body.Table -> TableView(b.header, b.rows)
        is AnswerCard.Body.Steps -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            b.items.forEachIndexed { i, s ->
                val done = i in ticks
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(role = Role.Checkbox) { onTick(i) }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (done) c.emerald500 else c.bgSubtle)
                        .border(1.dp, if (done) c.emerald500 else c.borderDefault, CircleShape), contentAlignment = Alignment.Center) {
                        Text(if (done) "✓" else "${i + 1}", style = type["mono.xs"], color = if (done) c.textInverse else c.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    Text(s, style = type.body, color = if (done) c.textMuted else c.textPrimary, textDecoration = if (done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f))
                }
            }
            Text(t("native.labs.canvas.doneCount", "done" to ticks.count { it in b.items.indices }, "total" to b.items.size), style = type["mono.xs"], color = c.textDim)
        }
        is AnswerCard.Body.Compare -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            b.sides.forEach { (name, points) ->
                Column(Modifier.width(if (b.sides.size <= 2) 160.dp else 140.dp).clip(RoundedCornerShape(10.dp)).background(c.bgSubtle).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = type.titleSm, color = c.textAccentEmerald, fontWeight = FontWeight.SemiBold)
                    points.forEach { Text("• $it", style = type.caption, color = c.textPrimary) }
                }
            }
        }
        is AnswerCard.Body.Facts -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            b.pairs.forEach { (label, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(label, style = type.caption, color = c.textSecondary, modifier = Modifier.weight(0.4f))
                    Text(value, style = type.body, color = c.textPrimary, modifier = Modifier.weight(0.6f))
                }
            }
        }
    }
}

/** Each label, its value as the answer writes it, and a bar from zero. */
@Composable
private fun Bars(s: AnswerCard.Body.Series) {
    val c = Boar.colors
    val max = s.points.maxOf { kotlin.math.abs(it.value) }.takeIf { it > 0 } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.points.forEach { p ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row {
                    Text(p.label, style = Boar.type.caption, color = c.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(p.written, style = Boar.type["mono.xs"], color = c.textSecondary)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.bgSubtle)) {
                    Box(Modifier.fillMaxWidth((kotlin.math.abs(p.value) / max).toFloat().coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.emerald500))
                }
            }
        }
    }
}

/** A table: an optional header in small capitals, a hairline between rows; wide ones scroll sideways. */
@Composable
private fun TableView(header: List<String>, rows: List<List<String>>) {
    val c = Boar.colors
    val n = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    val wide = n > 2
    Column(Modifier.then(if (wide) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth())) {
        @Composable
        fun line(cells: List<String>, head: Boolean) = Row(if (wide) Modifier else Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (i in 0 until n) {
                val v = cells.getOrElse(i) { "" }
                val m = if (wide) Modifier.widthIn(min = 72.dp, max = 180.dp) else Modifier.weight(1f)
                if (head) Text(v.uppercase(), style = Boar.type.micro, color = c.textSecondary, fontWeight = FontWeight.Bold, modifier = m.padding(bottom = 4.dp))
                else Text(v, style = Boar.type.body, color = c.textPrimary, modifier = m.padding(vertical = 6.dp),
                    textAlign = if (!wide && i == n - 1 && n == 2) androidx.compose.ui.text.style.TextAlign.End else null)
            }
        }
        if (header.any { it.isNotBlank() }) line(header, true)
        rows.forEach { r -> Box(Modifier.then(if (wide) Modifier.width((n * 100).dp) else Modifier.fillMaxWidth()).height(1.dp).background(c.borderSubtle)); line(r, false) }
    }
}

/** The parts of a whole (a pie only when CardRules or the model says they are): a donut and each part's value and share. */
@Composable
private fun Pie(s: AnswerCard.Body.Series) {
    val c = Boar.colors
    val colors = palette()
    val values = s.points.map { it.value.coerceAtLeast(0.0) }
    val total = values.sum().takeIf { it > 0 } ?: 1.0
    val pct = remember(s) { AnswerCard.percents(s) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(104.dp)) {
            var start = -90f
            values.forEachIndexed { i, x ->
                val sweep = (x / total * 360).toFloat()
                drawArc(colors[i % colors.size], start, sweep, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.22f))
                start += sweep
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            s.points.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colors[i % colors.size]))
                    Text("${p.label}  ${p.written}" + if (s.unit != "%") "  (${pct[i]}%)" else "", style = Boar.type.caption, color = c.textPrimary)
                }
            }
        }
    }
}

/** Points in order (a line only for years in order, or the model's line), each with its value; the labels under it. */
@Composable
private fun Line(s: AnswerCard.Body.Series) {
    val c = Boar.colors
    val values = s.points.map { it.value }
    val axis = remember(s) { VisualScale.axis(values, fromZero = false) }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val valueStyle = Boar.type["mono.xs"].copy(color = c.textSecondary)
    val line = c.emerald500
    val grid = c.borderSubtle
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 14.dp, start = 6.dp, end = 6.dp)) {
            val span = (axis.max - axis.min).takeIf { it > 0 } ?: 1.0
            fun y(x: Double) = (size.height * (1 - (x - axis.min) / span)).toFloat()
            fun xAt(i: Int) = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
            axis.ticks.forEach { tick -> drawLine(grid, androidx.compose.ui.geometry.Offset(0f, y(tick)), androidx.compose.ui.geometry.Offset(size.width, y(tick)), 1f) }
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { i, x -> if (i == 0) path.moveTo(xAt(i), y(x)) else path.lineTo(xAt(i), y(x)) }
            drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            values.forEachIndexed { i, x ->
                drawCircle(line, radius = 5f, center = androidx.compose.ui.geometry.Offset(xAt(i), y(x)))
                val text = measurer.measure(s.points[i].written, valueStyle)
                drawText(text, topLeft = androidx.compose.ui.geometry.Offset((xAt(i) - text.size.width / 2).coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f)), y(x) - text.size.height - 4))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            s.points.forEach { p -> Text(p.label, style = Boar.type.micro, color = c.textSecondary, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
    }
}

/** The parts' colours: the theme's own accents and their shades, no fixed hues. */
@Composable
private fun palette(): List<Color> {
    val c = Boar.colors
    return listOf(c.emerald500, c.cyan400, c.amber400, c.crimson400, c.textAccentViolet, c.emerald600, c.cyan600, c.amber600)
}
