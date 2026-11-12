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
/** The chat header's bar, shared (adam, Nov 12: "make the main boar chat header height for all others"). */
object HeaderMetrics {
    /** The row's height: the chat header's 42 dp round buttons. */
    val ROW = 42.dp
    /** Above and below the row. */
    val PAD = 10.dp
    /** The whole bar: 10 + 42 + 10 = 62 dp, plus the 1 dp line under it. */
    val HEIGHT = ROW + PAD * 2
}

@Composable
fun PageHeader(title: String, onBack: () -> Unit, subtitle: String? = null, maxTitleLines: Int = 1, backTo: String? = null) {
    val c = Boar.colors
    // The chat header's height exactly, with or without a subtitle: one line each, sizes held at Standard as the chat's
    // header does (FixedSizeChrome), so Large text can't make the bar taller.
    team.sopa.boar.ui.theme.FixedSizeChrome {
        Row(Modifier.fillMaxWidth().background(c.bgSurface).height(HeaderMetrics.HEIGHT).padding(horizontal = 12.dp, vertical = HeaderMetrics.PAD),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // [backTo] names where Back goes when it isn't obvious: "‹ Plugins", "‹ Chat" (t9, Nov 12).
            Text("‹ " + (backTo ?: t("common.back")), style = Boar.type.body, color = c.textAccentEmerald, maxLines = 1,
                modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Boar.type.titleLg, color = c.textHeading, maxLines = if (subtitle != null) 1 else maxTitleLines.coerceAtMost(1),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = Boar.type.caption, color = c.textMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
}
