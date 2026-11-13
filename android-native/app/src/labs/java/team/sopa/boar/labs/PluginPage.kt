package team.sopa.boar.labs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * The plugin page template (t9 and adam, Nov 12), in this order: one sentence of what it does, the Try rows, and "How it
 * works" folded with one real example (a before and after, or what you ask and what BOAR replies) above the long note.
 * A page with a live tool (Nomad, Lunar, Vitals, Canvas, Designer, Press) shows the tool first and this under it, with
 * no summary. [example] is drawn in the example card under its text (Canvas's chart). A plugin whose page has this fold
 * has no `_about` string, so the shared "About this plugin" fold (LabsScreen AboutPlugin) hides itself: one fold a page.
 */
@Composable
fun PluginPage(
    summary: String?, before: String?, after: String?, tries: List<String>, howItWorks: String?,
    beforeLabel: String = t("native.labs.page.before"), afterLabel: String = t("native.labs.page.after"),
    exampleLabel: String? = null, tryHint: String = t("native.labs.page.tryHint"),
    example: (@Composable () -> Unit)? = null,
) {
    val c = Boar.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        summary?.let { Text(it, style = Boar.type.bodyLg, color = c.textPrimary) }
        val hasExample = before != null || after != null || example != null
        val exampleBody: @Composable () -> Unit = {
            exampleLabel?.let { Label(it) }
            // Labels only for a pair; a lone text (Press's file, Designer's drawing) is the example itself.
            val pair = before != null && after != null
            if (before != null) {
                if (pair) Label(beforeLabel)
                Text(before, style = Boar.type.body, color = c.textSecondary)
            }
            if (after != null) {
                if (pair) Label(afterLabel)
                Text(after, style = Boar.type.body, color = c.textPrimary, fontWeight = if (pair) FontWeight.SemiBold else null)
            }
            example?.invoke()
        }
        // The example sits inside "How it works", above its note (adam, Nov 13: "all example on plugin page, move to how it
        // works"); a page with no such fold keeps it in a card of its own.
        if (hasExample && howItWorks == null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bgCard).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) { exampleBody() }
        if (tries.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(t("native.labs.page.try"))
            for (q in tries) TryRow(q)
            Text(tryHint, style = Boar.type.caption, color = c.textMuted)
        }
        howItWorks?.let { Fold(t("native.labs.page.how"), it, if (hasExample) exampleBody else null) }
    }
}

/** The template for a plugin that answers in the chat: its example is what you ask and what BOAR replies. */
@Composable
fun AnswerPage(
    summary: String?, question: String?, reply: String?, tries: List<String>, howItWorks: String?,
    tryHint: String = t("native.labs.page.tryHint"), example: (@Composable () -> Unit)? = null,
) = PluginPage(summary, question, reply, tries, howItWorks, t("native.labs.page.ask"), t("native.labs.page.reply"),
    t("native.labs.page.example"), tryHint, example)

@Composable
private fun Label(text: String) = Text(text, style = Boar.type.caption, color = Boar.colors.textMuted, fontWeight = FontWeight.Bold)

@Composable
private fun Fold(title: String, body: String, top: (@Composable () -> Unit)? = null) {
    val c = Boar.colors
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.bgCard)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Boar.type.body, color = c.textHeading, modifier = Modifier.weight(1f))
            Text(if (open) "▴" else "▾", style = Boar.type.body, color = c.textMuted)
        }
        if (open) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            top?.invoke()
            Text(body, style = Boar.type.body, color = c.textSecondary, modifier = if (top != null) Modifier.padding(top = 6.dp) else Modifier)
        }
    }
}
