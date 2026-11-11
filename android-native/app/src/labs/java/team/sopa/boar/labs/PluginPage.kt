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
 * The plugin page template (t9 and adam, Nov 12), in this order: one sentence of what it does, one real before and
 * after, the Try rows, and "How it works" folded with the long note. Guard uses it first; the others follow once adam
 * approves their copy.
 */
@Composable
fun PluginPage(summary: String, before: String?, after: String?, tries: List<String>, howItWorks: String?) {
    val c = Boar.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(summary, style = Boar.type.bodyLg, color = c.textPrimary)
        if (before != null && after != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bgCard).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(t("native.labs.page.before"))
            Text(before, style = Boar.type.body, color = c.textSecondary)
            Label(t("native.labs.page.after"))
            Text(after, style = Boar.type.body, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
        }
        if (tries.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(t("native.labs.page.try"))
            for (q in tries) TryRow(q)
            Text(t("native.labs.page.tryHint"), style = Boar.type.caption, color = c.textMuted)
        }
        howItWorks?.let { Fold(t("native.labs.page.how"), it) }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = Boar.type.caption, color = Boar.colors.textMuted, fontWeight = FontWeight.Bold)

@Composable
private fun Fold(title: String, body: String) {
    val c = Boar.colors
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.bgCard)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Boar.type.body, color = c.textHeading, modifier = Modifier.weight(1f))
            Text(if (open) "▴" else "▾", style = Boar.type.body, color = c.textMuted)
        }
        if (open) Text(body, style = Boar.type.body, color = c.textSecondary, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
    }
}
