package team.sopa.boar.labs

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
        CrashRestart.install(context)
        // BOAR's storage limit, the user's pick while Labs is on (Advanced tweaks); off, the catalog's 50 GB.
        team.sopa.boar.engine.models.StorageBudget.userLimit = { if (LabPrefs.labsOn) storageLimitBytes(LabPrefs.string(STORAGE_LIMIT)) else null }
        CrashRestart.noteKey(context)?.let { team.sopa.boar.ui.Toasts.show(team.sopa.boar.ui.I18n.t(context, it)) }
        // Who wrote an instant answer (AnswerTools.speaker): the plugin whose tool id it carries. The CO safety answer can't be
        // bypassed: it exists because a model answered "yes, with ventilation".
        team.sopa.boar.plugins.AnswerTools.speaker = { tool ->
            val id = when {
                tool == "safety-fact-co-hazard" -> "cohazard"
                tool.startsWith("calculator-travel-distance") -> "distance"
                tool.startsWith("calculator-") -> "calc"
                tool.startsWith("facts-") -> "facts"
                tool == team.sopa.boar.engine.routing.LanguageGate.TOOL -> "polyglot"
                else -> null
            }
            // Each speaks by its tile's name, so the chat and the switch to turn it off say the same thing.
            id?.let(LabRegistry::byId)?.let { team.sopa.boar.plugins.AnswerTools.Speaker("native.labs.${it.id}.title", it.art, it.emoji, canBypass = it.id != "cohazard") }
        }
        // The engine slot: the engines of the labs switched on now, read each time the app lists models.
        team.sopa.boar.plugins.EnginePlugins.providers = { LabRegistry.active.mapNotNull { it.engine } }
        // A model of BOAR's own an active lab's engine runs instead (Big Boar, by the user's choice on its card).
        team.sopa.boar.plugins.EnginePlugins.route = { a -> LabRegistry.active.firstNotNullOfOrNull { lab -> lab.engine?.takeIf { it.claims(a) }?.id } }
        // A model of an active lab's engine the user moved to BOAR's own (the BOAR chip on the 35B's card).
        team.sopa.boar.plugins.EnginePlugins.builtIn = { a -> LabRegistry.active.any { lab -> lab.engine?.let { it.id == a.engine && it.prefersBuiltIn(a) } == true } }
        // About → credits: the engines Boar Labs ships and the models its labs use (android-native/NOTICE lists them too).
        team.sopa.boar.ui.about.AboutCredits.extra = {
            listOf(
                "stable-diffusion.cpp (Boar Designer)" to "MIT",
                "BigMoeOnEdge (Big Boar)" to "Apache-2.0",
                "colibri (Boar Wisp)" to "Apache-2.0",
                "SDXS-512 image model (Boar Designer)" to "OpenRAIL++-M",
                "Qwen3.6-35B-A3B (Big Boar)" to "Apache-2.0",
                "OLMoE-1B-7B (Boar Wisp)" to "Apache-2.0",
                "r4to's calculator, BOAR R&D (Boar Calculator)" to "MIT",
                "r4to's travel distance, BOAR R&D (Boar Distance)" to "MIT",
                "GeoNames cities15000 (Boar Distance)" to "CC BY 4.0",
                "r4to's offline facts, BOAR R&D (Boar Facts)" to "MIT",
                "Wikidata facts table (Boar Facts)" to "CC0",
                "r4to's verdict guard, BOAR R&D (Boar Guard)" to "MIT",
                "r4to's CO hazard check, BOAR R&D (Boar Cohazard)" to "MIT",
            )
        }
        team.sopa.boar.plugins.PipelineStages.search = { LabRegistry.active.mapNotNull { it.searchStage } }
        // Safety tools first (Boar Cohazard), then grid order.
        team.sopa.boar.plugins.AnswerTools.tools = { LabRegistry.active.sortedBy { team.sopa.boar.engine.routing.LabTools.slotRank(it.id) }.mapNotNull { it.answerTool } }
        team.sopa.boar.plugins.AnswerChecks.checks = { LabRegistry.active.mapNotNull { it.answerCheck } }
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
        if (LabPrefs.labsOn) Row(androidx.compose.ui.Modifier.padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(androidx.compose.ui.Modifier.weight(1f)) {
                androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.crash.setting"), style = team.sopa.boar.ui.theme.Boar.type.body, color = c.textPrimary)
                androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.crash.settingNote"), style = team.sopa.boar.ui.theme.Boar.type.subtext, color = c.textMuted)
            }
            androidx.compose.material3.Switch(LabPrefs.autoRestart, { LabPrefs.autoRestart = it },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.emerald500, checkedThumbColor = c.textInverse))
        }
        if (LabPrefs.labsOn) Column(androidx.compose.ui.Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.storage.setting"), style = team.sopa.boar.ui.theme.Boar.type.body, color = c.textPrimary)
            var limit by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(LabPrefs.string(STORAGE_LIMIT) ?: "auto") }
            team.sopa.boar.ui.models.ChipRow("", listOf(
                "auto" to team.sopa.boar.ui.t("native.labs.storage.auto"), "25" to "25 GB", "100" to "100 GB", "200" to "200 GB",
                "none" to team.sopa.boar.ui.t("native.labs.storage.none"),
            ), limit) { limit = it; LabPrefs.putString(STORAGE_LIMIT, it) }
            androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.storage.settingNote"), style = team.sopa.boar.ui.theme.Boar.type.subtext, color = c.textMuted)
        }
    }

    private const val STORAGE_LIMIT = "storageLimit"

    /** Close BOAR shows the rain while Labs is on, unless the phone's animations are off (Android's remove animations). */
    fun closeAnimation(context: android.content.Context): Boolean = LabPrefs.labsOn &&
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    /** Full screen, over everything: the green rain with the closing step in the middle. */
    @Composable
    fun ClosingOverlay(stage: String) {
        val c = team.sopa.boar.ui.theme.Boar.colors
        // Each step as it happens, ticked off below the current one; the memory given back is measured, not guessed.
        val seen = remember { androidx.compose.runtime.mutableStateListOf<String>() }
        val startBytes = remember { team.sopa.boar.app.AppMemory.residentBytes() }
        var freedBytes by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
        androidx.compose.runtime.LaunchedEffect(stage) {
            if (stage !in seen) seen += stage
            if (stage == "freeing") freedBytes = (startBytes - team.sopa.boar.app.AppMemory.residentBytes()).coerceAtLeast(0L)
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false,
        )) {
            // A window over the chat, not the whole screen: the rain runs inside it.
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.widthIn(max = 360.dp).fillMaxWidth().height(250.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .border(1.dp, c.emerald400.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
                ) {
                    MatrixRain(androidx.compose.ui.Modifier.fillMaxSize())
                    Column(
                        androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)).padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.close.title"), style = team.sopa.boar.ui.theme.Boar.type.title,
                            color = c.textHeading, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        seen.forEachIndexed { i, s ->
                            val done = i < seen.lastIndex || s == "closing"
                            val line = if (s == "freeing" && freedBytes > 100L * 1024 * 1024)
                                team.sopa.boar.ui.t("native.labs.close.freed", "gb" to String.format(java.util.Locale.US, "%.1f", freedBytes / 1_073_741_824.0))
                            else team.sopa.boar.ui.t("native.labs.close.$s")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.Text(if (done) "✓" else "›", style = team.sopa.boar.ui.theme.Boar.type["mono.xs"],
                                    color = if (done) c.textAccentEmerald else c.textPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                androidx.compose.material3.Text(line, style = team.sopa.boar.ui.theme.Boar.type["mono.xs"],
                                    color = if (done) c.textSecondary else c.textAccentEmerald, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }


    /** "auto" or unknown: null (the catalog's 50 GB); "none": only the phone's free space; else that many GB. */
    fun storageLimitBytes(choice: String?): Long? = when (choice) {
        null, "auto" -> null
        "none" -> team.sopa.boar.engine.models.StorageBudget.NO_LIMIT
        else -> choice.toLongOrNull()?.takeIf { it > 0 }?.let { it * 1_000_000_000L }
    }

    @Composable
    fun Screen(route: String, go: (String) -> Unit) {
        Init()
        // Leaving Boar Labs (back to the chat) covers the leaked teasers again; moving between labs keeps them.
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { LabPrefs.coverAll() } }
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

    /** A model's card in the Model Catalog: each active lab's rows for it; nothing while Labs is off. */
    @Composable
    fun CatalogCardExtra(asset: team.sopa.boar.engine.models.CatalogAsset) {
        Init()
        LabRegistry.active.forEach { it.CatalogCardExtra(asset) }
    }

    /** A model's card in the Model Catalog: a line from each lab switched off that could help with it; nothing while Labs is off. */
    @Composable
    fun CatalogCardHint(asset: team.sopa.boar.engine.models.CatalogAsset) {
        Init()
        if (!LabPrefs.labsOn) return
        LabRegistry.visible.filter { it.status != LabStatus.CLASSIFIED && !LabPrefs.enabled(it.id) }.forEach { it.CatalogCardHint(asset) }
    }

    /** The Hugging Face search's Mixture of experts filter: offered while a lab that runs them is on. */
    fun moeFilter(): Boolean = LabRegistry.active.any { it.browseMoeFilter }

    /** A line for a Hugging Face result from the active labs (a string key), or null. */
    fun browseHint(repoId: String, architecture: String?): String? =
        LabRegistry.active.firstNotNullOfOrNull { runCatching { it.browseHint(repoId, architecture) }.getOrNull() }

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
