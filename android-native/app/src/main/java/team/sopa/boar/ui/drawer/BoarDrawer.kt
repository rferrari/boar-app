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

class DrawerItem(val key: String, val label: String, val onClick: () -> Unit)

/** Drawer.tsx: the brand, a new chat, recent sessions (open, delete), the screens, and the RAM/disk footer. */
@Composable
fun BoarDrawer(
    sessions: List<ChatSession>,
    activeSessionId: String?,
    items: List<DrawerItem>,
    lastTokPerSec: Double?,
    storageUsed: () -> Long,
    ramBudgetBytes: Long,
    storageBudgetBytes: Long,
    /** The memory and storage bars at the bottom: a technical detail (Advanced tweaks → Show technical details). */
    showStats: Boolean,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val c = Boar.colors
    Column(Modifier.fillMaxHeight().widthIn(max = 310.dp).background(c.bgCardElevated).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        // Fixed parts at the top and bottom; the chat history takes the height left between them and scrolls inside
        // it, so the menu doesn't move when the sessions load.
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                Image(painterResource(R.drawable.boar), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                Column {
                    Text("BOAR", style = Boar.type.title, color = c.textHeading)
                    Text(t("drawer.subtitle"), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.textAccentEmerald, modifier = Modifier.padding(top = 1.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(c.emeraldBgSubtle).border(1.dp, c.emeraldBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onNewChat).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("＋", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.textAccentEmerald)
                Text(t("drawer.newChat"), style = Boar.type.titleSm.copy(fontSize = 13.sp), color = c.textAccentEmerald)
            }
            Text(t("drawer.recentSessions"), style = Boar.type["mono.xs"], color = c.textDim, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(sessions, key = { it.id }) { s ->
                        val active = s.id == activeSessionId
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .then(if (active) Modifier.background(c.cyanBgSubtle).border(1.dp, c.cyanBorder, RoundedCornerShape(6.dp)) else Modifier)
                            .clickable { onSelect(s.id) }.padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = Boar.type.caption, color = c.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(relativeTime(s.updatedAt), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.textDim, modifier = Modifier.padding(top = 2.dp))
                            }
                            Text("🗑️", fontSize = 13.sp, modifier = Modifier.alpha(0.7f).clickable { onDelete(s.id) }.padding(4.dp))
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
        if (showStats) FooterStats(lastTokPerSec, storageUsed, ramBudgetBytes, storageBudgetBytes)
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
private fun usage(used: Long, total: Long): String {
    fun gb(b: Long) = if (b <= 0) "0" else String.format(Locale.US, "%.1f", b / 1073741824.0).removeSuffix(".0")
    return "${gb(used)}/${gb(total)}GB"
}

private fun rssBytes(): Long = team.sopa.boar.app.AppMemory.residentBytes()

/** DrawerFooterStats.tsx: the app's RAM and the models' disk use against BOAR's budgets, and the last answer's speed. */
@Composable
private fun FooterStats(lastTokPerSec: Double?, storageUsed: () -> Long, ramBudget: Long, storageBudget: Long) {
    val c = Boar.colors
    var rss by remember { mutableLongStateOf(0L) }
    var disk by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) { rss = rssBytes(); disk = storageUsed() }
            delay(5000)
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderSubtle))
        Bar(t("drawerFooterStats.ram"), rss, ramBudget, c.emerald400)
        Bar(t("drawerFooterStats.disk"), disk, storageBudget, c.cyan400)
        if (lastTokPerSec != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
            Text("●", fontSize = 8.sp, color = c.cyan400)
            Text(t("drawerFooterStats.lastQuery", "rate" to String.format(Locale.US, "%.1f", lastTokPerSec)), style = Boar.type["mono.xs"].copy(fontSize = 9.sp),
                color = c.textAccentCyan, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Bar(label: String, used: Long, total: Long, color: Color) {
    val c = Boar.colors
    val frac = if (total > 0) used.toFloat() / total else 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.textDim, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.widthIn(min = 40.dp))
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, c.borderDefault, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(if (frac > 1f) c.crimson500 else color))
        }
        Text(usage(used, total), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.textMuted, maxLines = 1)
    }
}
