package team.sopa.boar.labs

import androidx.compose.foundation.layout.size
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
    val taps = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    // The tile that just leaked flashes red once.
    var alert by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Shell(t("native.labs.title"), onClose) {
        Hero(labs)
        // Three groups by status: the labs you can use (live first, then beta, in plugin.properties order), the ones
        // under review, and the locked ones still in testing with no release date.
        val groups = listOf(
            null to labs.filter { it.status == LabStatus.LIVE || it.status == LabStatus.BETA },
            "native.labs.section.review" to labs.filter { it.status == LabStatus.REVIEW },
            "native.labs.section.testing" to labs.filter { it.status == LabStatus.CLASSIFIED },
        )
        for ((title, group) in groups) {
            if (group.isEmpty()) continue
            if (title != null) Text(t(title), style = Boar.type["mono.xs"], color = c.textDim, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp))
            group.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { lab ->
                        Tile(lab, Modifier.weight(1f), flash = alert == lab.id) {
                            if (lab.status != LabStatus.CLASSIFIED) onOpen("lab:${lab.id}")
                            else {
                                // Easter egg: ten taps on a classified tile declassify it, like Android's developer options.
                                val n = (taps[lab.id] ?: 0) + 1
                                taps[lab.id] = n
                                if (n == REVEAL_TAPS && !LabPrefs.revealed(lab.id)) {
                                    LabPrefs.setRevealed(lab.id)
                                    alert = lab.id
                                    team.sopa.boar.app.Haptics.leak(ctx)
                                }
                                note = lab.id
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        note?.let { id ->
            val name = t("native.labs.$id.title")
            val left = REVEAL_TAPS - (taps[id] ?: 0)
            val text = when {
                LabPrefs.revealed(id) -> t("native.labs.declassifiedNote", "name" to name)
                left in 1..5 -> t("native.labs.classifiedTaps", "count" to left)
                else -> t("native.labs.classifiedNote", "name" to name)
            }
            Text(text, style = Boar.type.caption, color = c.textAccentEmerald)
        }
    }
}

/** One experiment's page: the shared header (emoji, name, status) above the plugin's own content. */
@Composable
fun LabScaffold(lab: LabPlugin, onClose: () -> Unit) {
    val c = Boar.colors
    // The lab's own header in place of the "Boar Labs" bar: its picture, name, description and status, Done back to the
    // grid, and the on/off switch, all in one block; the page below is the lab's.
    Shell(t("native.labs.${lab.id}.title"), onClose, header = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            lab.art?.let { r -> androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(r), null, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                ?: Text(lab.emoji, fontSize = 36.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("native.labs.${lab.id}.title"), style = Boar.type.title, color = c.textHeading, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    StatusChip(lab.status)
                }
                Text(t("native.labs.${lab.id}.body"), style = Boar.type.caption, color = c.textMuted)
            }
            DoneButton(onClose)
        }
        Row(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("native.labs.enabled"), style = Boar.type.titleSm.copy(fontSize = 13.sp), color = c.textHeading)
                Text(t("native.labs.enabledNote"), style = Boar.type["mono.xs"], color = c.textDim)
            }
            androidx.compose.material3.Switch(LabPrefs.enabled(lab.id), { LabPrefs.setEnabled(lab.id, it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.emerald500))
        }
    }) {
        // UNDER REVIEW: the lab works, but the user reads why it's under review before using it.
        if (lab.status == LabStatus.REVIEW) Text("⚠ " + t("native.labs.${lab.id}.review"), style = Boar.type.body, color = c.amber400,
            modifier = Modifier.fillMaxWidth().background(c.amberBgSubtle, RoundedCornerShape(10.dp)).border(1.dp, c.amberBorder, RoundedCornerShape(10.dp)).padding(12.dp))
        if (LabPrefs.enabled(lab.id)) lab.Content() else Text(t("native.labs.disabled"), style = Boar.type.caption, color = c.textMuted)
    }
}

@Composable
private fun Shell(
    title: String, onClose: () -> Unit,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Boar.colors
    Column(Modifier.fillMaxSize().background(c.bgSurface).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (header != null) Column(Modifier.fillMaxWidth().background(c.bgCardElevated).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = header)
        else Row(Modifier.fillMaxWidth().background(c.bgCardElevated).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Boar.type.titleSm, color = c.textHeading, modifier = Modifier.weight(1f))
            DoneButton(onClose)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun DoneButton(onClose: () -> Unit) {
    val c = Boar.colors
    Text(t("common.done"), style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 4.dp))
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
    // The whole console is laid out invisibly underneath from the first frame, so the box has its final height (all
    // four lines, however they wrap) before a letter is typed and nothing below it jumps.
    Box(Modifier.fillMaxWidth().background(c.bgTerminal, RoundedCornerShape(12.dp)).border(1.dp, c.emeraldBorder, RoundedCornerShape(12.dp)).padding(14.dp)) {
        Console(full, cursorAlpha = 0f, Modifier.alpha(0f))
        Console(full.take(shown), cursorAlpha = blink)
    }
}

@Composable
private fun Console(text: String, cursorAlpha: Float, modifier: Modifier = Modifier) {
    val c = Boar.colors
    Column(modifier.fillMaxWidth()) {
        Text(text.substringBefore('\n'), style = Boar.type.headline.copy(letterSpacing = 3.sp), color = c.textAccentEmerald, fontWeight = FontWeight.ExtraBold)
        if ('\n' in text) Text(text.substringAfter('\n'), style = Boar.type["mono.xs"].let { it.copy(lineHeight = it.fontSize * 1.6f) }, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
        Text("█", style = Boar.type["mono.xs"], color = c.textAccentEmerald, modifier = Modifier.alpha(cursorAlpha))
    }
}

@Composable
private fun Tile(lab: LabPlugin, modifier: Modifier, flash: Boolean = false, onClick: () -> Unit) {
    val c = Boar.colors
    // Red alert: the tile that just leaked flashes red once, fading over a second.
    val red = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(flash) {
        if (flash) { red.snapTo(0.7f); red.animateTo(0f, androidx.compose.animation.core.tween(1000)) }
    }
    // A classified lab the user declassified (ten taps) shows its boar, name and description; it still can't be opened.
    val locked = lab.status == LabStatus.CLASSIFIED && !LabPrefs.revealed(lab.id)
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(if (locked) c.bgCard else c.emeraldBgSubtle)
            .border(1.dp, if (locked) c.borderSubtle else c.emeraldBorder, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            // Classified: the boar in near-black with a "?", as a locked truffle in the vault (VaultScreen SILHOUETTE).
            lab.art?.let { res ->
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(res), null, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop, colorFilter = if (locked) SILHOUETTE else null)
            } ?: Text(lab.emoji, fontSize = 56.sp, modifier = Modifier.alpha(if (locked) 0.25f else 1f))
            if (locked) Text(if (lab.art != null) "?" else "🔒", style = if (lab.art != null) Boar.type.titleLg else Boar.type.body,
                fontSize = if (lab.art != null) Boar.type.titleLg.fontSize else 22.sp, color = c.textDim)
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { StatusChip(lab.status, leaked = lab.status == LabStatus.CLASSIFIED && LabPrefs.revealed(lab.id)) }
            if (red.value > 0f) Box(Modifier.fillMaxSize().background(c.crimson500.copy(alpha = red.value)))
            // A revealed classified lab can't be switched on, so OFF would only confuse: it has LEAKED instead.
            if (!locked && lab.status != LabStatus.CLASSIFIED && !LabPrefs.enabled(lab.id)) Text(t("native.labs.off"), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.textDim, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Text(if (locked) redact(t("native.labs.${lab.id}.title")) else t("native.labs.${lab.id}.title"), style = Boar.type.titleSm,
            color = if (locked) c.textSecondary else c.textHeading, modifier = Modifier.padding(top = 4.dp))
        if (lab.status == LabStatus.REVIEW) Text("⚠ " + t("native.labs.${lab.id}.reviewShort"), style = Boar.type["mono.xs"], color = c.amber400, fontWeight = FontWeight.Bold)
        // Classified: the description is blacked out word by word like the title, a redacted file.
        Text(if (locked) redactAll(t("native.labs.${lab.id}.body")) else t("native.labs.${lab.id}.body"), style = Boar.type.caption,
            color = if (locked) c.textDim else c.textMuted)
    }
}

/** A locked boar's look, the vault's: its shape in near-black. */
private val SILHOUETTE = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
    0f, 0f, 0f, 0f, 18f,
    0f, 0f, 0f, 0f, 18f,
    0f, 0f, 0f, 0f, 24f,
    0f, 0f, 0f, 1f, 0f,
)))

/** "Reads signs." → "▇▇▇▇▇ ▇▇▇▇▇.": every word blacked out, its length and punctuation kept, so the lines still wrap like text. */
private fun redactAll(s: String): String = s.replace(Regex("[\\p{L}\\p{N}]+")) { "▇".repeat(it.value.length) }

/** "Big Brain" → "Big ▇▇▇▇▇": the first word stays, the rest is blacked out. */
private fun redact(s: String): String {
    val words = s.split(' ')
    return if (words.size < 2) s.take(1) + "▇".repeat((s.length - 1).coerceAtLeast(2)) else words.first() + " " + words.drop(1).joinToString(" ") { "▇".repeat(it.length) }
}

@Composable
fun StatusChip(status: LabStatus, leaked: Boolean = false) {
    val c = Boar.colors
    // A classified lab the user declassified (the ten-tap easter egg) shows LEAKED in alert red.
    if (leaked) return Text(t("native.labs.status.leaked"), style = Boar.type["mono.xs"].copy(fontSize = 10.sp), color = Color.White, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.background(c.crimson500, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
    val (fg, bg) = when (status) {
        LabStatus.LIVE -> c.textAccentEmerald to c.emeraldBgSubtle
        LabStatus.BETA -> c.textAccentCyan to c.cyanBgSubtle
        LabStatus.REVIEW -> c.crimson400 to c.crimsonBgSubtle
        LabStatus.CLASSIFIED -> c.textDim to Color.White.copy(alpha = 0.06f)
    }
    // UNDER REVIEW reads as a warning: red and a size up from the other tags.
    val review = status == LabStatus.REVIEW
    Text(t("native.labs.status.${status.name.lowercase()}"), style = Boar.type["mono.xs"].copy(fontSize = if (review) 11.sp else 9.sp), color = fg, fontWeight = FontWeight.ExtraBold,
        // A dark base under the tint keeps the tag readable over a tile's avatar.
        modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = if (review) 8.dp else 6.dp, vertical = if (review) 3.dp else 2.dp))
}

/** Taps on one classified tile that declassify it. */
private const val REVEAL_TAPS = 10
