package team.sopa.boar.ui.tour

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
 * The tour's previews (Oct 20): small, still pictures of the chat drawn with the theme's colors, so the theme card
 * repaints them live. The chat's own composables are private to ChatScreen, so these copy their look; nothing here
 * reacts to a tap. Each one is labelled EXAMPLE: its numbers are made up.
 */

/** The preview of [key]'s card, or nothing for a card without one. */
@Composable
internal fun TourPreview(key: String) {
    when (key) {
        "theme" -> Frame { UserBubble(); BoarBubble(withSources = false) }
        "styles" -> Frame { StylesPreview() }
        "deep" -> Frame { DeepPreview() }
        "sources" -> Frame { BoarBubble(withSources = true) }
        "knowledge" -> Frame { DocumentPreview() }
        "models" -> Frame { ModelPreview() }
        "adaptive" -> Frame { AdaptivePreview() }
    }
}

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

/** The chat's style button, then the three styles as its sheet lists them, Thorough & Detailed picked. */
@Composable
private fun StylesPreview() {
    val c = Boar.colors
    val styles = team.sopa.boar.app.BoarServices.get(LocalContext.current).personalities.personalities.filter { it.id != "custom" }
    val picked = "detailed"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(40.dp).background(c.bgInput, CircleShape).border(1.dp, c.borderDefault, CircleShape), contentAlignment = Alignment.Center) {
            Text(styles.firstOrNull { it.id == picked }?.icon ?: "", fontSize = 16.sp)
        }
        Text(t("native.tour.sample.styleButton"), style = Boar.type.subtext, color = c.textMuted)
    }
    for (s in styles) {
        val on = s.id == picked
        val shape = RoundedCornerShape(10.dp)
        Row(Modifier.fillMaxWidth().background(if (on) c.emeraldBgSubtle else c.bgCard, shape).border(1.dp, if (on) c.emeraldBorder else c.borderSubtle, shape).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.icon, fontSize = 16.sp)
            Column(Modifier.weight(1f)) {
                Text(t("personalities.${s.id}.label"), style = Boar.type.body, color = if (on) c.textAccentEmerald else c.textPrimary)
                Text(t("personalities.${s.id}.description"), style = Boar.type.subtext, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (on) Text("✓", color = c.textAccentEmerald, fontSize = 16.sp)
        }
    }
}

/** Deep Research on, as the chat shows it: the lit 🔬 button, the banner, and the input bar's brighter border. */
@Composable
private fun DeepPreview() {
    val c = Boar.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(34.dp).background(c.frontierBadgeBg, CircleShape).border(1.dp, c.frontierBadgeBorder, CircleShape), contentAlignment = Alignment.Center) {
            team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_deep_research, 18.dp, c.frontierText)
        }
        Text(t("native.chat.deepResearchOn"), style = Boar.type.subtext, color = c.frontierText, fontWeight = FontWeight.SemiBold)
    }
    Row(Modifier.fillMaxWidth().background(c.frontierBadgeBg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_deep_research, 11.dp, c.frontierText)
        Text(t("chatScreen.deepResearchBanner"), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.frontierText, fontWeight = FontWeight.Bold)
    }
    Box(Modifier.fillMaxWidth().background(c.bgInput, RoundedCornerShape(50)).border(1.5.dp, c.frontierBadgeBorder, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(t("native.chat.deepResearchPlaceholder"), style = Boar.type.body.copy(fontSize = 14.sp), color = c.frontierText, maxLines = 1)
    }
}

/** An imported document, as My Documents lists it. */
@Composable
private fun DocumentPreview() {
    val c = Boar.colors
    Row(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(10.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("📄", fontSize = 22.sp)
        Column(Modifier.weight(1f)) {
            Text(t("native.tour.sample.doc"), style = Boar.type.body, color = c.textPrimary)
            Text(t("native.tour.sample.docMeta"), style = Boar.type["mono.xs"], color = c.textMuted)
        }
        Chip(t("native.tour.sample.ask"), c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder)
    }
}

/** A Model Catalog card: the default model, its size, how it fits this phone, the setup badge. */
@Composable
private fun ModelPreview() {
    val c = Boar.colors
    Column(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(10.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Qwen3 4B", style = Boar.type.title, color = c.textHeading, modifier = Modifier.weight(1f))
            Chip(t("flows.onboarding.recommended"), c.textInverse, c.emerald500, c.emerald500)
        }
        Text("LLM · 2.5 GB · Apache-2.0", style = Boar.type["mono.xs"], color = c.textDim)
        Chip("●  " + t("catalogItemCard.compat.green"), c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder)
    }
}

/** The Adaptive routing switch as Settings shows it, off. */
@Composable
private fun AdaptivePreview() {
    val c = Boar.colors
    Row(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t("native.settings.adaptiveTitle"), style = Boar.type.body, color = c.textHeading)
            Text(t("native.settings.adaptiveNote"), style = Boar.type.subtext, color = c.textMuted)
        }
        Switch(false, null, colors = SwitchDefaults.colors(checkedTrackColor = c.emerald500))
    }
}

@Composable
private fun Chip(text: String, fg: Color, bg: Color, border: Color) =
    Text(text, style = Boar.type["mono.xs"], color = fg, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(bg, RoundedCornerShape(6.dp)).border(1.dp, border, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
