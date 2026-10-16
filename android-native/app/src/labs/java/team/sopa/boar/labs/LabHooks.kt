package team.sopa.boar.labs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import team.sopa.boar.plugins.AnswerRef

/**
 * The plugin slots the app calls: the Boar Labs drawer entry and its routes, the master switch in Settings, and each
 * active lab's answer actions, answer extras and header badges. With the switch off every slot is empty.
 */
object LabHooks {
    fun init(context: android.content.Context) {
        LabPrefs.init(context)
        LabContext.app = context.applicationContext
        // The engine slot: the engines of the labs switched on now, read each time the app lists models.
        team.sopa.boar.plugins.EnginePlugins.providers = { LabRegistry.active.mapNotNull { it.engine } }
        team.sopa.boar.plugins.PipelineStages.search = { LabRegistry.active.mapNotNull { it.searchStage } }
        // Every lab, on or off, erases what it made when the user erases the chats or forgets the PIN.
        val app = context.applicationContext
        team.sopa.boar.plugins.UserData.erase = { scope -> LabRegistry.ALL.forEach { lab -> runCatching { lab.eraseUserData(app, scope) } } }
    }

    /** route to label key; empty while Labs is off. */
    fun drawerItems(): List<Pair<String, String>> = if (LabPrefs.labsOn) listOf("labs" to "native.labs.drawer") else emptyList()

    fun handles(route: String) = LabPrefs.labsOn && (route == "labs" || route.startsWith("lab:"))

    /** Settings → Advanced tweaks, at the very bottom: the switch builders find. */
    @Composable
    fun SettingsRow() {
        Init()
        val c = team.sopa.boar.ui.theme.Boar.colors
        Row(androidx.compose.ui.Modifier.padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(androidx.compose.ui.Modifier.weight(1f)) {
                // Same look as Boar Avatars just above (Settings' SwitchRow).
                androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.settingsTitle"), style = team.sopa.boar.ui.theme.Boar.type.body, color = c.textPrimary)
                androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.settingsNote"), style = team.sopa.boar.ui.theme.Boar.type.subtext, color = c.textMuted)
            }
            androidx.compose.material3.Switch(LabPrefs.labsOn, { LabPrefs.labsOn = it },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.emerald500, checkedThumbColor = c.textInverse))
        }
    }

    @Composable
    fun Screen(route: String, go: (String) -> Unit) {
        Init()
        if (route == "labs") return LabsScreen(onOpen = go) { go("chat") }
        val lab = LabRegistry.byId(route.removePrefix("lab:"))
        if (lab == null) LabsScreen(onOpen = go) { go("chat") } else LabScaffold(lab) { go("labs") }
    }

    /** One 🧩 in the answer's footer; it opens a sheet with every active lab's actions for this answer. */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    fun AnswerActions(a: AnswerRef) {
        Init()
        val actions = LabRegistry.active.flatMap { it.answerActions(a) }
        if (actions.isEmpty()) return
        val c = team.sopa.boar.ui.theme.Boar.colors
        var open by androidx.compose.runtime.remember(a.id) { androidx.compose.runtime.mutableStateOf(false) }
        team.sopa.boar.ui.chat.FooterButton("🧩", null, actions.any { it.done }, c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder) { open = true }
        if (open) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { open = false }, containerColor = c.bgCardElevated) {
            Column(androidx.compose.ui.Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.sheetTitle"), style = team.sopa.boar.ui.theme.Boar.type["mono.xs"], color = c.textDim,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = androidx.compose.ui.Modifier.padding(bottom = 6.dp))
                actions.forEach { act ->
                    Row(androidx.compose.ui.Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .clickable(enabled = act.enabled) { act.onClick(); open = false }.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // The lab's avatar when it has one, else the action's emoji.
                        act.art?.let { r -> androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(r), null, androidx.compose.ui.Modifier.size(28.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                            ?: androidx.compose.material3.Text(act.emoji, fontSize = 20.sp)
                        androidx.compose.material3.Text(act.label, style = team.sopa.boar.ui.theme.Boar.type.body, color = if (act.enabled) c.textPrimary else c.textDim, modifier = androidx.compose.ui.Modifier.weight(1f))
                        if (act.done) androidx.compose.material3.Text("✓", color = c.textAccentEmerald, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
    }

    @Composable
    fun AnswerExtras(a: AnswerRef) {
        Init()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { LabRegistry.active.forEach { it.AnswerExtra(a) } }
    }

    @Composable
    fun HeaderBadges(go: (String) -> Unit) {
        Init()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { LabRegistry.active.forEach { it.HeaderBadge(go) } }
    }

    @Composable
    private fun Init() {
        val ctx = LocalContext.current
        remember { LabPrefs.init(ctx) }
    }
}
