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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import team.sopa.boar.ui.theme.wash
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    /** The whole bar: 10 + 42 + 10 = 62 dp, plus the 1 dp line under it. The title's two lines get all of it, not the 42 dp row. */
    val HEIGHT = ROW + PAD * 2

    /**
     * The title ("BOAR" in the chat) and the line under it (the chat's model line, a page's subtitle), the same in every
     * header: titleLg and mono.xs with tight line heights, so both lines fit the bar up to the 1.3 font scale
     * FixedSizeChrome allows (t9 1950: the subtitle was cut off).
     */
    fun titleStyle(type: team.sopa.boar.ui.theme.BoarType) = type.titleLg.let { it.copy(lineHeight = (it.fontSize.value * 1.15f).sp) }
    fun subtitleStyle(type: team.sopa.boar.ui.theme.BoarType) = type["mono.xs"].let { it.copy(lineHeight = (it.fontSize.value * 1.25f).sp) }
}

@Composable
fun PageHeader(title: String, onBack: () -> Unit, subtitle: String? = null, maxTitleLines: Int = 1, backTo: String? = null,
               /** A ✕ on the right that leaves for the chat (plugin pages only, adam Nov 13); null: no ✕. */
               onClose: (() -> Unit)? = null,
               /** Small controls on the right, before the ✕ (Settings' profile pills, adam Nov 13). */
               actions: (@Composable () -> Unit)? = null) {
    val c = Boar.colors
    // The chat header's height exactly, with or without a subtitle: one line each, sizes held at Standard as the chat's
    // header does (FixedSizeChrome), so Large text can't make the bar taller.
    team.sopa.boar.ui.theme.FixedSizeChrome {
        Row(Modifier.fillMaxWidth().background(c.bgSurface).height(HeaderMetrics.HEIGHT).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // [backTo] names where Back goes when it isn't obvious: "‹ Plugins", "‹ Chat" (t9, Nov 12).
            Text("‹ " + (backTo ?: t("common.back")), style = Boar.type.body, color = c.textAccentEmerald, maxLines = 1,
                modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            Column(Modifier.weight(1f)) {
                // The chat header's look: the title as "BOAR", the subtitle as its model line (t9 1950).
                Text(title, style = HeaderMetrics.titleStyle(Boar.type), color = c.textHeading, maxLines = if (subtitle != null) 1 else maxTitleLines.coerceAtMost(1),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = HeaderMetrics.subtitleStyle(Boar.type), color = c.textMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            }
            actions?.invoke()
            // ✕: straight to the chat, where ‹ Back goes to the previous page (adam, Nov 13). The chat header's 42 dp round button.
            if (onClose != null) {
                val label = t("common.close")
                Box(Modifier.size(HeaderMetrics.ROW).clip(androidx.compose.foundation.shape.CircleShape).background(c.wash(0.05f))
                    .clickable(onClickLabel = label, role = androidx.compose.ui.semantics.Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                    PackIcon(team.sopa.boar.R.drawable.ic_close, 20.dp, description = label)
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
}
