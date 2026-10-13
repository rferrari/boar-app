package team.sopa.boar.labs

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/** Boar Labs: a terminal-style hero, then the experiments two per row, each with what it is underneath. */
@Composable
fun LabsScreen(onOpen: (String) -> Unit, onClose: () -> Unit) {
    val c = Boar.colors
    val labs = LabRegistry.visible
    var note by remember { mutableStateOf<String?>(null) }
    Shell(t("native.labs.title"), onClose) {
        Hero(labs)
        labs.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { lab ->
                    Tile(lab, Modifier.weight(1f)) {
                        if (lab.status == LabStatus.CLASSIFIED) note = lab.id else onOpen("lab:${lab.id}")
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        note?.let { Text(t("native.labs.classifiedNote", "name" to t("native.labs.$it.title")), style = Boar.type.caption, color = c.textAccentEmerald) }
    }
}

/** One experiment's page: the shared header (emoji, name, status) above the plugin's own content. */
@Composable
fun LabScaffold(lab: LabPlugin, onClose: () -> Unit) {
    val c = Boar.colors
    Shell(t("native.labs.title"), onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(lab.emoji, fontSize = 36.sp)
            Column(Modifier.weight(1f)) {
                Text(t("native.labs.${lab.id}.title"), style = Boar.type.title, color = c.textHeading)
                Text(t("native.labs.${lab.id}.body"), style = Boar.type.caption, color = c.textMuted)
            }
            StatusChip(lab.status)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderSubtle))
        lab.Content()
    }
}

@Composable
private fun Shell(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Boar.colors
    Column(Modifier.fillMaxSize().background(c.bgSurface).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(c.bgCardElevated).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Boar.type.titleSm, color = c.textHeading, modifier = Modifier.weight(1f))
            Text(t("common.done"), style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** The lab's console: the lines type themselves once, with a blinking cursor after. */
@Composable
private fun Hero(labs: List<LabPlugin>) {
    val c = Boar.colors
    val open = labs.count { it.status != LabStatus.CLASSIFIED }
    val lines = listOf(
        "BOAR LABS",
        "> " + t("native.labs.hero.count", "open" to open, "locked" to labs.size - open),
        "> " + t("native.labs.hero.offline"),
        "> " + t("native.labs.hero.warning"),
    )
    val full = lines.joinToString("\n")
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (shown < full.length) { delay(14); shown++ } }
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(1f, 0f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "cursor")
    Column(Modifier.fillMaxWidth().background(c.bgTerminal, RoundedCornerShape(12.dp)).border(1.dp, c.emeraldBorder, RoundedCornerShape(12.dp)).padding(14.dp)) {
        val text = full.take(shown)
        val title = text.substringBefore('\n')
        Text(title, style = Boar.type.headline.copy(letterSpacing = 3.sp), color = c.textAccentEmerald, fontWeight = FontWeight.ExtraBold)
        if ('\n' in text) Text(text.substringAfter('\n'), style = Boar.type["mono.xs"].copy(lineHeight = 18.sp), color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
        Text("█", style = Boar.type["mono.xs"], color = c.textAccentEmerald, modifier = Modifier.alpha(blink))
    }
}

@Composable
private fun Tile(lab: LabPlugin, modifier: Modifier, onClick: () -> Unit) {
    val c = Boar.colors
    val locked = lab.status == LabStatus.CLASSIFIED
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(if (locked) c.bgCard else c.emeraldBgSubtle)
            .border(1.dp, if (locked) c.borderSubtle else c.emeraldBorder, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(lab.emoji, fontSize = 56.sp, modifier = Modifier.alpha(if (locked) 0.25f else 1f))
            if (locked) Text("🔒", fontSize = 22.sp)
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { StatusChip(lab.status) }
        }
        Text(if (locked) redact(t("native.labs.${lab.id}.title")) else t("native.labs.${lab.id}.title"), style = Boar.type.titleSm,
            color = if (locked) c.textSecondary else c.textHeading, modifier = Modifier.padding(top = 4.dp))
        Text(t("native.labs.${lab.id}.body"), style = Boar.type.caption, color = c.textMuted)
    }
}

/** "Big Brain" → "Big ▇▇▇▇▇": the first word stays, the rest is blacked out. */
private fun redact(s: String): String {
    val words = s.split(' ')
    return if (words.size < 2) s.take(1) + "▇".repeat((s.length - 1).coerceAtLeast(2)) else words.first() + " " + words.drop(1).joinToString(" ") { "▇".repeat(it.length) }
}

@Composable
fun StatusChip(status: LabStatus) {
    val c = Boar.colors
    val (fg, bg) = when (status) {
        LabStatus.LIVE -> c.textAccentEmerald to c.emeraldBgSubtle
        LabStatus.BETA -> c.textAccentCyan to c.cyanBgSubtle
        LabStatus.CLASSIFIED -> c.textDim to Color.White.copy(alpha = 0.06f)
    }
    Text(t("native.labs.status.${status.name.lowercase()}"), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = fg, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
}
