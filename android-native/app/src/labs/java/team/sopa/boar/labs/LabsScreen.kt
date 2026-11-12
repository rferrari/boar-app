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
import team.sopa.boar.ui.theme.wash

/** The grid's scroll position while a lab's page is open (this process only). */
private var gridScroll = 0

/** Boar Labs: a terminal-style hero, then the experiments two per row, each with what it is underneath. */
@Composable
fun LabsScreen(onOpen: (String) -> Unit, onClose: () -> Unit) {
    val c = Boar.colors
    val labs = LabRegistry.visible
    var note by remember { mutableStateOf<String?>(null) }
    val taps = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    val revealAt = remember { HashMap<String, Int>() }
    // The tile that just leaked flashes red once.
    var alert by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Back from a lab's page (Done) to the grid where it was: the grid leaves the screen while a lab is open.
    val scroll = rememberScrollState(gridScroll)
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { gridScroll = scroll.value } }
    Shell(t("native.labs.title"), onClose, scroll = scroll, backTo = t("native.labs.title")) {
        Hero(labs)
        // Three groups by status: the labs you can use (live first, then beta, in plugin.properties order), the ones
        // under review, and the locked ones still in testing with no release date.
        val groups = listOf(
            null to labs.filter { it.status == LabStatus.LIVE || it.status == LabStatus.BETA || it.status == LabStatus.ALPHA },
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
                            if (LabRegistry.opened(lab) != null) onOpen("lab:${lab.id}")
                            else {
                                // Easter egg: 11 to 20 taps (a new count per tile each visit) declassify it, like Android's developer options.
                                val n = (taps[lab.id] ?: 0) + 1
                                taps[lab.id] = n
                                if (n == revealAt.getOrPut(lab.id) { REVEAL_TAPS.random() } && !LabPrefs.revealed(lab.id)) {
                                    LabPrefs.setRevealed(lab.id)
                                    // A lab with code behind the teaser (the Daily Quiz) stays unlocked; its next tap opens it.
                                    if (lab is ClassifiedLab && lab.inner != null) LabPrefs.unlock(lab.id)
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
            val text = when {
                LabPrefs.unlocked(id) -> t("native.labs.unlockedNote", "name" to name)
                LabPrefs.revealed(id) -> t("native.labs.declassifiedNote", "name" to name)
                // The taps count in silence: no countdown gives the easter egg away.
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
    if (lab.fullPage) {
        Shell(t("native.labs.${lab.id}.title"), onClose, backTo = if (LabPrefs.proProfile) t("native.labs.title") else t("native.nav.chat"), full = true, subtitle = lab.pageSubtitle()) {
            if (lab.status == LabStatus.REVIEW) Text(t("native.labs.${lab.id}.review"), style = Boar.type.body, color = c.amber400, modifier = Modifier.padding(12.dp))
            if (!LabPrefs.enabled(lab.id)) Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { OffAlert(if (LabPrefs.proProfile) onClose else null) }
            Box(Modifier.fillMaxWidth().weight(1f)) { lab.Content() }
        }
        return
    }
    // The lab's own header in place of the "Boar Labs" bar: its picture, name, description and status, Done back to the
    // grid, and the on/off switch, all in one block; the page below is the lab's.
    // Back names where it goes: the Plugins list in Pro, the chat below Pro (LabHooks.backFrom).
    Shell(t("native.labs.${lab.id}.title"), onClose, backTo = if (LabPrefs.proProfile) t("native.labs.title") else t("native.nav.chat"), header = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            lab.shownArt?.let { r -> androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(r), null, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                ?: Text(lab.emoji, fontSize = 36.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("native.labs.${lab.id}.title"), style = Boar.type.title, color = c.textHeading, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (PluginsUi.SHOW_STATUS) StatusChip(lab.status)
                }
                Text(t("native.labs.${lab.id}.body"), style = Boar.type.caption, color = c.textMuted)
            }
        }
    }) {
        // UNDER REVIEW: the lab works, but the user reads why it's under review before using it.
        if (lab.status == LabStatus.REVIEW) Row(Modifier.fillMaxWidth().background(c.amberBgSubtle, RoundedCornerShape(10.dp)).border(1.dp, c.amberBorder, RoundedCornerShape(10.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReviewMark(20.dp)
            Text(t("native.labs.${lab.id}.review"), style = Boar.type.body, color = c.amber400)
        }
        // The switch is in the Plugins list only (adam, Nov 12). Off, the page still shows everything: an alert on top, the
        // settings editable (saved for when it's on; editing never turns it on, and an off plugin fills no slot).
        // In Pro, Back goes to the Plugins list (LabHooks.backFrom), so the alert's link is the same way back.
        if (!LabPrefs.enabled(lab.id)) OffAlert(if (LabPrefs.proProfile) onClose else null)
        lab.Content()
        AboutPlugin(lab)
    }
}

/**
 * The same footer on every plugin page (adam, Nov 12): "About this plugin", folded, with the longer explanation the
 * list's two-sentence line leaves out.
 */
@Composable
private fun AboutPlugin(lab: LabPlugin) = AboutPluginFold(lab)

/** "About this plugin", folded: a full-page plugin (LabPlugin.fullPage) shows it in its own settings sheet. */
@Composable
fun AboutPluginFold(lab: LabPlugin) {
    val c = Boar.colors
    val key = "native.labs.${lab.id}.about"
    val about = t(key).takeIf { it != key && it.isNotBlank() } ?: return
    var open by androidx.compose.runtime.saveable.rememberSaveable(lab.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(c.bgCard), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ⓘ " + t("native.labs.aboutPlugin"), style = Boar.type.body, color = c.textHeading, modifier = Modifier.weight(1f))
            Text(if (open) "▴" else "▾", style = Boar.type.body, color = c.textMuted)
        }
        if (open) Text(about, style = Boar.type.body, color = c.textSecondary, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
    }
}

@Composable
private fun Shell(
    title: String, onClose: () -> Unit,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    scroll: androidx.compose.foundation.ScrollState? = null,
    backTo: String? = null,
    subtitle: String? = null,
    /** The plugin lays out the whole height itself (LabPlugin.fullPage): no scroll, no padding. */
    full: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Boar.colors
    Column(Modifier.fillMaxSize().background(c.bgSurface).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // The app's page header (t9, Nov 12: the Plugins pages had their own); a plugin's own block comes under it.
        team.sopa.boar.ui.PageHeader(title, onClose, subtitle = subtitle, backTo = backTo)
        if (header != null) {
            Column(Modifier.fillMaxWidth().background(c.bgCardElevated).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = header)
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        }
        if (full) Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        else Column(Modifier.weight(1f).verticalScroll(scroll ?: rememberScrollState()).padding(12.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
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
    val locked = lab.status == LabStatus.CLASSIFIED && !LabPrefs.revealed(lab.id) && !LabPrefs.unlocked(lab.id)
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(if (locked) c.bgCard else c.emeraldBgSubtle)
            .border(1.dp, if (locked) c.borderSubtle else c.emeraldBorder, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            // Classified: the boar in near-black with a "?", as a locked truffle in the vault (VaultScreen SILHOUETTE).
            lab.shownArt?.let { res ->
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(res), null, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop, colorFilter = if (locked) SILHOUETTE else null)
            } ?: Text(lab.emoji, fontSize = 56.sp, modifier = Modifier.alpha(if (locked) 0.25f else 1f))
            if (locked) {
                if (lab.shownArt != null) Text("?", style = Boar.type.titleLg, color = c.textDim)
                else team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_settings_pin, 24.dp, c.textDim)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { StatusChip(lab.status, leaked = lab.status == LabStatus.CLASSIFIED && (LabPrefs.revealed(lab.id) || LabPrefs.unlocked(lab.id))) }
            if (red.value > 0f) Box(Modifier.fillMaxSize().background(c.crimson500.copy(alpha = red.value)))
            // A revealed classified lab can't be switched on, so OFF would only confuse: it has LEAKED instead.
            if (!locked && lab.status != LabStatus.CLASSIFIED && !LabPrefs.enabled(lab.id)) Text(t("native.labs.off"), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.textDim, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Text(if (locked) redact(t("native.labs.${lab.id}.title")) else t("native.labs.${lab.id}.title"), style = Boar.type.titleSm,
            color = if (locked) c.textSecondary else c.textHeading, modifier = Modifier.padding(top = 4.dp))
        if (lab.status == LabStatus.REVIEW) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ReviewMark(12.dp)
            Text(t("native.labs.${lab.id}.reviewShort"), style = Boar.type["mono.xs"], color = c.amber400, fontWeight = FontWeight.Bold)
        }
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
        LabStatus.ALPHA -> c.amber400 to c.amberBgSubtle
        LabStatus.REVIEW -> c.crimson400 to c.crimsonBgSubtle
        LabStatus.CLASSIFIED -> c.textDim to Boar.colors.wash(0.06f)
    }
    // UNDER REVIEW reads as a warning: red and a size up from the other tags.
    val review = status == LabStatus.REVIEW
    Text(t("native.labs.status.${status.name.lowercase()}"), style = Boar.type["mono.xs"].copy(fontSize = if (review) 11.sp else 9.sp), color = fg, fontWeight = FontWeight.ExtraBold,
        // A dark base under the tint keeps the tag readable over a tile's avatar.
        modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = if (review) 8.dp else 6.dp, vertical = if (review) 3.dp else 2.dp))
}

/** Taps on one classified tile that declassify it: drawn again each time Boar Labs opens, so no number gets passed around. */
private val REVEAL_TAPS = 11..20

/** The Boar icon pack's warning triangle, in the review amber: a lab under review (Oct 24, was "⚠"). */
@Composable
private fun ReviewMark(size: androidx.compose.ui.unit.Dp) = androidx.compose.material3.Icon(
    androidx.compose.ui.res.painterResource(team.sopa.boar.R.drawable.ic_settings_recovery), null,
    Modifier.size(size), tint = Boar.colors.amber400)

/**
 * Boar Labs as a compact list (rc3, adam, Nov 10): one row per lab with its picture, name, one line, status and an
 * on/off switch, so nothing needs a page just to switch on. Tapping a row opens the lab's page for its own settings.
 * Classified labs are listed by status only, blacked out like the tiles. The tile grid stays one tap away ([onTiles]).
 */
@Composable
fun LabsList(onOpen: (String) -> Unit, onTiles: () -> Unit, onClose: () -> Unit) {
    val c = Boar.colors
    val labs = LabRegistry.visible
    // The page's intro is the header's subtitle (adam, Nov 12: subtitles only where a first-timer needs one).
    Shell(t("native.labs.title"), onClose, subtitle = t("native.labs.listSubtitle")) {
        // Search (adam, Nov 11): by name or the line under it; the teasers hide while searching.
        var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
        androidx.compose.foundation.text.BasicTextField(query, { query = it }, singleLine = true,
            textStyle = Boar.type.body.copy(color = c.textPrimary), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.textAccentEmerald),
            modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(8.dp)).border(1.dp, c.borderElevated, RoundedCornerShape(8.dp)).padding(12.dp),
            decorationBox = { inner -> if (query.isEmpty()) Text(t("native.labs.search"), style = Boar.type.body, color = c.textMuted); inner() })
        val (allOpen, classified) = labs.partition { LabRegistry.opened(it) != null }
        val open = allOpen.filter { team.sopa.boar.engine.labs.RecentPlugins.matches(query, t("native.labs.${it.id}.title"), t("native.labs.${it.id}.body")) }
        for (lab in open) LabRow(lab, onOpen)
        if (open.isEmpty()) Text(t("native.labs.searchNone"), style = Boar.type.caption, color = c.textMuted)
        // Plugins in testing with no release date are hidden (adam, Nov 12); PluginsUi.SHOW_UPCOMING brings them back to
        // promote what's next.
        if (PluginsUi.SHOW_UPCOMING && classified.isNotEmpty() && query.isBlank()) {
            Text(t("native.labs.section.testing"), style = Boar.type["mono.xs"], color = c.textDim, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            for (lab in classified) Row(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(10.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabArt(lab, locked = true)
                Column(Modifier.weight(1f)) {
                    Text(redact(t("native.labs.${lab.id}.title")), style = Boar.type.body, color = c.textSecondary)
                    Text(redactAll(t("native.labs.${lab.id}.body")), style = Boar.type.caption, color = c.textDim, maxLines = 1)
                }
                StatusChip(lab.status)
            }
        }
        // The list is the only view (adam, Nov 12); the tiles stay behind PluginsUi.SHOW_TILES.
        if (PluginsUi.SHOW_TILES) Text(t("native.labs.showTiles"), style = Boar.type.body, color = c.textAccentEmerald, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onTiles).padding(vertical = 6.dp))
    }
}

/** One lab in [LabsList]: tap the row for its page, flip the switch to turn it on or off right here. */
@Composable
private fun LabRow(lab: LabPlugin, onOpen: (String) -> Unit) {
    val c = Boar.colors
    val on = LabPrefs.enabled(lab.id)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) c.emeraldBgSubtle else c.bgCard)
        .border(1.dp, if (on) c.emeraldBorder else c.borderSubtle, RoundedCornerShape(10.dp)).clickable { onOpen("lab:${lab.id}") }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LabArt(lab, locked = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("native.labs.${lab.id}.title"), style = Boar.type.body, color = c.textHeading, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (PluginsUi.SHOW_STATUS) StatusChip(lab.status)
            }
            Text(t("native.labs.${lab.id}.body"), style = Boar.type.caption, color = c.textMuted, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        androidx.compose.material3.Switch(on, { LabPrefs.setEnabled(lab.id, it) },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.emerald500, checkedThumbColor = c.textInverse))
    }
}

/** A lab's picture at list size, or its emoji until it has one (Staged Answers, Canvas); a classified one dark. */
@Composable
private fun LabArt(lab: LabPlugin, locked: Boolean) {
    val c = Boar.colors
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(c.bgCardElevated), contentAlignment = Alignment.Center) {
        lab.shownArt?.let { res ->
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(res), null, Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, colorFilter = if (locked) SILHOUETTE else null)
        } ?: Text(lab.emoji, fontSize = 24.sp, modifier = Modifier.alpha(if (locked) 0.25f else 1f))
    }
}

/** What hides upcoming plugins and the tile view, kept so they can come back (adam, Nov 12). */
object PluginsUi {
    /** Plugins in testing with no release date (the classified teasers): hidden until there's something to promote. */
    const val SHOW_UPCOMING = false
    /** The tile view of the Plugins page: the list is the only view. */
    const val SHOW_TILES = false
    /** The ALPHA/BETA/LIVE chips (adam, Nov 12: dropped from what users see); the status stays an internal gate. */
    const val SHOW_STATUS = false
}

/** On a plugin's page while it's off: it's off, where to turn it on, and that settings changed now apply then. */
@Composable
private fun OffAlert(onList: (() -> Unit)?) {
    val c = Boar.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(if (onList != null) Modifier.clickable(onClick = onList) else Modifier).background(c.amberBgSubtle, RoundedCornerShape(10.dp)).border(1.dp, c.amberBorder, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(t(if (LabPrefs.proProfile) "native.labs.offInList" else "native.labs.offPage"), style = Boar.type.body, color = c.textAccentAmber, fontWeight = FontWeight.SemiBold)
        Text(t("native.labs.offSettings"), style = Boar.type.caption, color = c.textSecondary)
    }
}
