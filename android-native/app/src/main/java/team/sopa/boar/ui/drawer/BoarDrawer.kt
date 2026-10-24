package team.sopa.boar.ui.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import team.sopa.boar.engine.models.Units
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import team.sopa.boar.R
import team.sopa.boar.engine.chat.ChatSession
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.util.Locale
import team.sopa.boar.ui.theme.track

class DrawerItem(val key: String, val label: String, val onClick: () -> Unit)

/** Drawer.tsx: the brand, a new chat, recent sessions (open, delete), the screens, and the RAM/disk footer. */
@Composable
fun BoarDrawer(
    sessions: List<ChatSession>,
    activeSessionId: String?,
    items: List<DrawerItem>,
    lastTokPerSec: Double?,
    storageUsed: () -> Long,
    /** What the running downloads still have to fetch: they count against BOAR's limit before they land. */
    downloading: () -> Long,
    storageBudgetBytes: Long,
    /** The memory and storage bars at the bottom: a technical detail (Advanced tweaks → Show technical details). */
    showStats: Boolean,
    /** BOAR is answering: chats can't be switched, opened or deleted until it ends (the list is dimmed, with a hint). */
    answering: Boolean = false,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    /** The BOAR mark and name at the top open About. */
    onAbout: () -> Unit = {},
) {
    val c = Boar.colors
    Column(Modifier.fillMaxHeight().widthIn(max = 310.dp).background(c.bgCardElevated).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        // Fixed parts at the top and bottom; the chat history takes the height left between them and scrolls inside
        // it, so the menu doesn't move when the sessions load.
        Column(Modifier.weight(1f)) {
            val aboutLabel = t("chatScreen.drawerItems.about")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = aboutLabel, onClick = onAbout)) {
                Image(painterResource(team.sopa.boar.appearance.TruffleVault.avatar(team.sopa.boar.appearance.LocalTruffle.current, R.drawable.boar)), aboutLabel, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                Column {
                    Text("BOAR", style = Boar.type.title, color = c.textHeading)
                    Text(t("drawer.subtitle"), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.textAccentEmerald, modifier = Modifier.padding(top = 1.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(c.emeraldBgSubtle).border(1.dp, c.emeraldBorder, RoundedCornerShape(10.dp))
                .alpha(if (answering) 0.4f else 1f).clickable(enabled = !answering, onClick = onNewChat).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("＋", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.textAccentEmerald)
                Text(t("drawer.newChat"), style = Boar.type.titleSm.copy(fontSize = 13.sp), color = c.textAccentEmerald)
            }
            Text(t("drawer.recentSessions"), style = Boar.type["mono.xs"], color = c.textDim, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            if (answering) Text(t("native.drawer.answeringHint"), style = Boar.type.caption, color = c.textAccentAmber, modifier = Modifier.padding(bottom = 6.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(sessions, key = { it.id }) { s ->
                        val active = s.id == activeSessionId
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .then(if (active) Modifier.background(c.cyanBgSubtle).border(1.dp, c.cyanBorder, RoundedCornerShape(6.dp)) else Modifier)
                            .alpha(if (answering && !active) 0.4f else 1f).clickable(enabled = !answering) { onSelect(s.id) }.padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = Boar.type.caption, color = c.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(relativeTime(s.updatedAt), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.textDim, modifier = Modifier.padding(top = 2.dp))
                            }
                            if (!answering) Text("🗑️", fontSize = 13.sp, modifier = Modifier.alpha(0.7f).clickable { onDelete(s.id) }.padding(4.dp))
                        }
                    }
                }
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(1.dp).background(c.borderSubtle))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    Text(item.label, style = Boar.type.titleSm, color = c.textHeading,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = item.onClick).padding(horizontal = 10.dp, vertical = 12.dp))
                }
            }
        }
        if (showStats) FooterStats(lastTokPerSec, storageUsed, downloading, storageBudgetBytes)
    }
}

@Composable
private fun relativeTime(ms: Long): String {
    val min = (System.currentTimeMillis() - ms) / 60000.0
    return when {
        min < 1 -> t("time.justNow")
        min < 60 -> t("time.minutesAgo", "count" to min.toInt())
        min < 60 * 24 -> t("time.hoursAgo", "count" to (min / 60).toInt())
        else -> t("time.daysAgo", "count" to (min / 60 / 24).toInt())
    }
}

/** "1.8/12GB": one decimal, a whole number without it, the unit once. */
/** Decimal GB, like the catalog's model sizes and the phone's own storage screen. */
internal fun usage(used: Long, total: Long): String {
    fun gb(b: Long) = if (b <= 0) "0" else String.format(Locale.US, "%.1f", b / 1e9).removeSuffix(".0")
    return "${gb(used)}/${gb(total)}GB"
}

/**
 * DrawerFooterStats.tsx, with real numbers: the phone's memory in use (BOAR's part highlighted) against the phone's
 * total, and BOAR's disk use plus what running downloads still fetch against BOAR's own storage limit, which the
 * caption names so it isn't read as the phone's space. Then the last answer's speed.
 */
@Composable
private fun FooterStats(lastTokPerSec: Double?, storageUsed: () -> Long, downloading: () -> Long, storageBudget: Long) {
    val c = Boar.colors
    val ctx = LocalContext.current
    var app by remember { mutableLongStateOf(0L) }
    var phone by remember { mutableStateOf(team.sopa.boar.app.AppMemory.Phone(0L, 0L)) }
    var disk by remember { mutableLongStateOf(0L) }
    var fetching by remember { mutableLongStateOf(0L) }
    var phoneFree by remember { mutableLongStateOf(0L) }
    // No BOAR limit (Boar Labs): the bar is BOAR's use against what it could still take, the phone's free space.
    val noLimit = storageBudget == team.sopa.boar.engine.models.StorageBudget.NO_LIMIT
    val diskTotal = if (noLimit) disk + fetching + phoneFree else storageBudget
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                app = team.sopa.boar.app.AppMemory.residentBytes(); phone = team.sopa.boar.app.AppMemory.phone(ctx)
                disk = storageUsed(); fetching = downloading()
                phoneFree = runCatching { ctx.filesDir.usableSpace }.getOrDefault(0L)
            }
            delay(5000)
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderSubtle))
        Bar(t("drawerFooterStats.ram"), listOf(minOf(app, phone.usedBytes) to c.emerald400, maxOf(phone.usedBytes - app, 0L) to c.textDim), phone.totalBytes,
            usage(phone.usedBytes, phone.totalBytes))
        Caption(t("native.drawerStats.ramBoar", "app" to Units.formatBytes(app, Locale.getDefault())))
        Bar(t("drawerFooterStats.disk"), listOf(disk to c.cyan400, fetching to c.cyan400.copy(alpha = 0.35f)), diskTotal, usage(disk + fetching, diskTotal))
        Caption(
            when {
                noLimit -> t("native.drawerStats.diskNoLimit")
                fetching > 0 -> t("native.drawerStats.diskDownloading", "size" to Units.formatBytes(fetching, Locale.getDefault()))
                else -> t("native.drawerStats.diskLimit")
            },
        )
        if (lastTokPerSec != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
            Text("●", fontSize = 8.sp, color = c.cyan400)
            Text(t("drawerFooterStats.lastQuery", "rate" to String.format(Locale.US, "%.1f", lastTokPerSec)), style = Boar.type["mono.xs"].copy(fontSize = 9.sp),
                color = c.textAccentCyan, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Caption(text: String) = UsageCaption(text, large = false)

@Composable
private fun Bar(label: String, parts: List<Pair<Long, Color>>, total: Long, text: String) = UsageBar(label, parts, total, text, large = false)

/** The line under a [UsageBar], lined up with the bar. */
@Composable
internal fun UsageCaption(text: String, large: Boolean) =
    Text(text, style = if (large) Boar.type.subtext else Boar.type["mono.xs"].copy(fontSize = 9.sp), color = Boar.colors.textDim,
        modifier = Modifier.padding(start = if (large) 92.dp else 46.dp))

/**
 * A bar of stacked [parts] (bytes, color) out of [total]; red when they pass it. Small in the menu, [large] in
 * Settings (readable text, a taller bar).
 */
@Composable
internal fun UsageBar(label: String, parts: List<Pair<Long, Color>>, total: Long, text: String, large: Boolean) {
    val c = Boar.colors
    val sum = parts.sumOf { it.first }
    val over = total > 0 && sum > total
    val small = Boar.type["mono.xs"].copy(fontSize = 9.sp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = if (large) Boar.type.subtext else small, color = if (large) c.textSecondary else c.textDim, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.widthIn(min = if (large) 86.dp else 40.dp))
        Row(Modifier.weight(1f).height(if (large) 9.dp else 5.dp).clip(RoundedCornerShape(3.dp)).background(Boar.colors.track(0.5f)).border(1.dp, c.borderDefault, RoundedCornerShape(3.dp))) {
            if (total > 0) parts.forEach { (bytes, color) ->
                val frac = (bytes.toFloat() / maxOf(total, sum)).coerceIn(0f, 1f)
                if (frac > 0f) Box(Modifier.weight(frac).fillMaxHeight().background(if (over) c.crimson500 else color))
            }
            val rest = if (total > 0) 1f - (sum.toFloat() / maxOf(total, sum)).coerceIn(0f, 1f) else 1f
            if (rest > 0f) Box(Modifier.weight(rest))
        }
        Text(text, style = if (large) Boar.type["mono.xs"] else small, color = c.textMuted, maxLines = 1)
    }
}
