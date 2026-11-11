package team.sopa.boar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import team.sopa.boar.ui.theme.Boar

/**
 * The header of a page reached from the menu or another page: "‹ Back", the title (and a subtitle when it has one), a
 * line under it. One height for all, with or without a subtitle (t9, Nov 10: Discover's looked different). Pages
 * that open over the chat (About, Boar Labs, the tour, Evaluation) close with Done instead.
 */
@Composable
fun PageHeader(title: String, onBack: () -> Unit, subtitle: String? = null, maxTitleLines: Int = 1) {
    val c = Boar.colors
    Row(Modifier.fillMaxWidth().background(c.bgSurface).heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("‹ " + t("common.back"), style = Boar.type.body, color = c.textAccentEmerald, modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Boar.type.titleLg, color = c.textHeading, maxLines = maxTitleLines,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = Boar.type.caption, color = c.textMuted) }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
}
