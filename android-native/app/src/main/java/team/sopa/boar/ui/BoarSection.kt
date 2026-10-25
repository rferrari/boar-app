package team.sopa.boar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import team.sopa.boar.ui.theme.Boar

/**
 * A section as Settings shows it, a card with its title, and on Knowledge too (Oct 21): with [onToggle], a tap on the
 * title folds or unfolds it (▴ open, ▾ folded); [open] is the caller's, so a folded title can still say what's inside.
 * [summary]: a folded section's one line of what it's set to ("Qwen3 4B", "PIN on"), so nobody opens it to check.
 * [icon]: a one-color drawable left of the title, in the title's color unless [iconTint] (Oct 24, the Boar icon pack).
 */
@Composable
fun BoarSection(title: String, open: Boolean = true, onToggle: (() -> Unit)? = null, summary: String? = null, icon: Int? = null,
    iconTint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Boar.colors
    val mark: @Composable () -> Unit = { if (icon != null) Icon(painterResource(icon), null, Modifier.size(22.dp), tint = iconTint ?: c.textHeading) }
    Column(
        Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onToggle != null) Row(Modifier.fillMaxWidth().clickable(onClickLabel = title, onClick = onToggle).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            mark()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Boar.type.title, color = c.textHeading)
                if (!open && !summary.isNullOrBlank()) Text(summary, style = Boar.type.subtext, color = c.textSecondary, maxLines = 2)
            }
            team.sopa.boar.ui.PackIcon(if (open) team.sopa.boar.R.drawable.ic_chevron_up else team.sopa.boar.R.drawable.ic_chevron_down, 18.dp, c.textMuted)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            mark()
            Text(title, style = Boar.type.title, color = c.textHeading)
        }
        if (open) content()
    }
}
