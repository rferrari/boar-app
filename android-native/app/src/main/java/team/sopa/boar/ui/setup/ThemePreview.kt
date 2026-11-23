package team.sopa.boar.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sopa.boar.R
import team.sopa.boar.chat.Source
import team.sopa.boar.ui.chat.SourceFootnotes
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/*
 * Setup's theme page preview (from the retired tour, Oct 20): a still picture of the chat drawn with the theme's colors,
 * so picking a theme repaints it live. Labelled EXAMPLE: nothing here reacts to a tap.
 */

/** The chat as the chosen theme draws it. */
@Composable
internal fun ThemePreview() = Frame { UserBubble(); BoarBubble(withSources = false) }

/** The EXAMPLE box every preview sits in: the chat's terminal background, a live dot and the label. */
@Composable
private fun Frame(content: @Composable ColumnScope.() -> Unit) {
    val c = Boar.colors
    Column(Modifier.fillMaxWidth().background(c.bgTerminal, RoundedCornerShape(12.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(c.emerald500, CircleShape))
            Text(t("native.tour.example").uppercase(), style = Boar.type.micro, color = c.textMuted, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun UserBubble() {
    val c = Boar.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.widthIn(max = 260.dp).background(c.bgCardHover, RoundedCornerShape(16.dp, 16.dp, 6.dp, 16.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(t("chatScreen.roleYou"), style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.SemiBold)
            Text(t("native.tour.sample.question"), style = Boar.type.body, color = c.textPrimary)
        }
    }
}

/** BOAR's answer as the chat draws it: the mascot and name, the text, then (with [withSources]) its sources and receipt. */
@Composable
private fun BoarBubble(withSources: Boolean) {
    val c = Boar.colors
    Column(Modifier.fillMaxWidth().background(c.bgSurface, RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(R.drawable.mascot), null, Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)))
            Text(t("chatScreen.roleAssistant"), style = Boar.type["mono.xs"], color = c.textAccentEmerald, fontWeight = FontWeight.SemiBold)
        }
        Text(t("native.tour.sample.answer"), style = Boar.type.body, color = c.textPrimary)
        if (withSources) SourceFootnotes(listOf(
            Source("Water purification", "Wikipedia", null, similarity = 0.82),
            Source("Boiling", "Wikipedia", null, similarity = 0.74),
        ))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.borderSubtle))
        Text(t("native.tour.sample.receipt"), style = Boar.type["mono.xs"], color = c.textMuted, modifier = Modifier.align(Alignment.End))
    }
}
