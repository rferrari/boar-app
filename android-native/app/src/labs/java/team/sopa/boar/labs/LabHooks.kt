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
                "stable-diffusion.cpp (Designer)" to "MIT",
                "BigMoeOnEdge (Big Boar)" to "Apache-2.0",
                "colibri (Boar Wisp)" to "Apache-2.0",
                "SDXS-512 image model (Designer)" to "OpenRAIL++-M",
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
        team.sopa.boar.plugins.AnswerModes.staged = { LabRegistry.active.any { it.stagedAnswers } }
        team.sopa.boar.plugins.AnswerModes.ownsSources = { LabRegistry.active.any { it.ownsSources } }
        // Every lab, on or off, erases what it made when the user erases the chats or forgets the PIN.
        val app = context.applicationContext
        team.sopa.boar.plugins.UserData.erase = { scope -> LabRegistry.ALL.forEach { lab -> runCatching { lab.eraseUserData(app, scope) } } }
        team.sopa.boar.plugins.UserData.eraseAnswers = { ids -> LabRegistry.ALL.forEach { lab -> runCatching { lab.eraseAnswers(app, ids) } } }
    }

    /** route to label key; empty while Labs is off. */
    // rc3: the menu item, the list and the tiles are Pro's; a lab's own page (lab:<id>, from its header badge) opens in
    // every profile, since an enabled lab keeps working everywhere.
    fun drawerItems(): List<Pair<String, String>> = if (LabPrefs.labsOn && LabPrefs.proProfile) listOf("labs" to "native.labs.drawer") else emptyList()

    fun handles(route: String) = LabPrefs.labsOn && ((route == "labs" || route == TILES) && LabPrefs.proProfile || route.startsWith("lab:"))

    /** Where Back goes from a Labs route: the tiles and a lab's page back to the list on Pro, else to the chat. */
    fun backFrom(route: String): String = when {
        route == "labs" -> "chat"
        route == TILES -> "labs"
        LabPrefs.proProfile -> "labs"
        else -> "chat"
    }

    /** The old tile grid, kept one tap away from the list (its classified tiles still answer to taps). */
    const val TILES = "labs/tiles"

    /** Settings → Advanced tweaks, at the very bottom: the switch builders find. */
    @Composable
    fun SettingsRow() {
        Init()
        val c = team.sopa.boar.ui.theme.Boar.colors
        // The same switch row as the rest of Settings (UX review D2).
        team.sopa.boar.ui.settings.SwitchRow(team.sopa.boar.ui.t("native.labs.settingsTitle"), team.sopa.boar.ui.t("native.labs.settingsNote"), LabPrefs.labsOn) { LabPrefs.labsOn = it }
        if (LabPrefs.labsOn) team.sopa.boar.ui.settings.SwitchRow(team.sopa.boar.ui.t("native.labs.crash.setting"), team.sopa.boar.ui.t("native.labs.crash.settingNote"), LabPrefs.autoRestart) { LabPrefs.autoRestart = it }
        if (LabPrefs.labsOn) Column(androidx.compose.ui.Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.wait.setting"), style = team.sopa.boar.ui.theme.Boar.type.body, color = c.textPrimary)
            team.sopa.boar.ui.models.ChipRow("", listOf(LabPrefs.WAIT_TRAIL to team.sopa.boar.ui.t("native.labs.wait.trail"), LabPrefs.WAIT_MATRIX to team.sopa.boar.ui.t("native.labs.wait.matrix")),
                LabPrefs.waitAnimation) { LabPrefs.waitAnimation = it }
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

    /**
     * Setup's Boar Labs page: a switch for each lab that starts on, then the ones that start off (alpha, or that load a
     * model), to try now or later. The same switches as the Labs grid.
     */
    @Composable
    fun SetupList() {
        Init()
        val c = team.sopa.boar.ui.theme.Boar.colors
        val type = team.sopa.boar.ui.theme.Boar.type
        val usable = LabRegistry.visible.filter { it.status != LabStatus.CLASSIFIED && it.status != LabStatus.REVIEW }
        // Every lab starts off (owner, Oct 24): beta first (live ones with them), then alpha, for the user to pick.
        val (beta, alpha) = usable.partition { it.status == LabStatus.LIVE || it.status == LabStatus.BETA }
        @Composable fun group(title: String, labs: List<LabPlugin>) {
            if (labs.isEmpty()) return
            androidx.compose.material3.Text(title.uppercase(), style = type.micro, color = c.textSecondary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = androidx.compose.ui.Modifier.padding(top = 6.dp))
            // What each lab does (its page's own first line), so the choice means something; the status is a small tag.
            // Text only, no emoji or art: setup's pages follow the app's plain design.
            for (lab in labs) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(androidx.compose.ui.Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.${lab.id}.title"), style = type.body, color = c.textPrimary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = androidx.compose.ui.Modifier.weight(1f, fill = false))
                        androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.status.${lab.status.name.lowercase()}").uppercase(), style = type.micro, color = c.textMuted,
                            modifier = androidx.compose.ui.Modifier.border(1.dp, c.borderSubtle, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                    androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.${lab.id}.body"), style = type.subtext, color = c.textSecondary)
                }
                androidx.compose.material3.Switch(LabPrefs.enabled(lab.id), { LabPrefs.setEnabled(lab.id, it) },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.emerald500, checkedThumbColor = c.textInverse))
            }
        }
        group(team.sopa.boar.ui.t("native.setupGuide.labs.beta"), beta)
        group(team.sopa.boar.ui.t("native.setupGuide.labs.alpha"), alpha)
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
            if (stage == "freeing") {
                // A moment for the freed pages and a Labs engine's process to go before reading what's left.
                kotlinx.coroutines.delay(200)
                freedBytes = (startBytes - team.sopa.boar.app.AppMemory.residentBytes()).coerceAtLeast(0L)
            }
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false,
        )) {
            // A window over the chat, not the whole screen: the rain runs inside it.
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.widthIn(max = 360.dp).fillMaxWidth().height(250.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .border(1.dp, rainColor(c).copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
                ) {
                    // Closing BOAR: only the hoofprints, no boar (adam, Nov 10); Matrix rain unchanged.
                    WaitingAnimation(androidx.compose.ui.Modifier.fillMaxSize(), showBoar = false)
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
        if (route == "labs") return LabsList(onOpen = go, onTiles = { go(TILES) }) { go("chat") }
        if (route == TILES) return LabsScreen(onOpen = go) { go("labs") }
        val lab = LabRegistry.byId(route.removePrefix("lab:"))
        // Opening a plugin's page counts as using it (the header's sheet shows the last five used).
        if (lab != null) androidx.compose.runtime.LaunchedEffect(lab.id) { LabPrefs.used(lab.id) }
        if (lab == null) androidx.compose.runtime.LaunchedEffect(route) { go(backFrom(route)) } else LabScaffold(lab) { go(backFrom(route)) }
    }

    /** One 🧩 in the answer's footer; it opens a sheet with every active lab's actions for this answer. */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    fun AnswerActions(a: AnswerRef) {
        Init()
        // Each action with its plugin, so a tap counts as using that plugin.
        val owned = LabRegistry.active.flatMap { lab -> lab.answerActions(a).map { lab.id to it } }
        val actions = owned.map { it.second }
        if (actions.isEmpty()) return
        val c = team.sopa.boar.ui.theme.Boar.colors
        var open by androidx.compose.runtime.remember(a.id) { androidx.compose.runtime.mutableStateOf(false) }
        team.sopa.boar.ui.chat.FooterButton("🧩", null, actions.any { it.done }, c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder, team.sopa.boar.ui.t("native.a11y.pluginActions")) { open = true }
        if (open) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { open = false }, containerColor = c.bgCardElevated) {
            Column(androidx.compose.ui.Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.sheetTitle"), style = team.sopa.boar.ui.theme.Boar.type["mono.xs"], color = c.textDim,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = androidx.compose.ui.Modifier.padding(bottom = 6.dp))
                owned.forEach { (labId, act) ->
                    SheetRow(act.art, act.emoji, act.label, null, LabRegistry.byId(labId)?.status, act.enabled,
                        trailing = if (act.done) ({ team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_check, 18.dp, c.textAccentEmerald) }) else null) {
                        LabPrefs.used(labId); act.onClick(); open = false
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

    /**
     * One plugins icon in the chat header (t9, Nov 10: it replaces each lab's own badge). Shown in every profile while a
     * lab is on; a tap lists the labs that are on, each opening its page. Managing them (the on/off list) stays in Pro.
     * Labs that wait on the user (Press's basket) add to the count on the icon.
     */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    fun HeaderBadges(go: (String) -> Unit) {
        Init()
        val labs = LabRegistry.active
        if (labs.isEmpty()) return
        val c = team.sopa.boar.ui.theme.Boar.colors
        val counts = labs.associate { it.id to it.headerCount() }
        val waiting = counts.values.sum()
        var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.foundation.layout.Box {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(team.sopa.boar.ui.theme.Boar.colors.let { it.emeraldBgSubtle }).border(1.dp, c.emeraldBorder, androidx.compose.foundation.shape.CircleShape)
                .clickable(onClickLabel = team.sopa.boar.ui.t("native.labs.headerIcon")) { open = true },
                contentAlignment = androidx.compose.ui.Alignment.Center) {
                team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_settings_experimental, 22.dp, c.textAccentEmerald, description = team.sopa.boar.ui.t("native.labs.headerIcon"))
            }
            // Never wider than "9+": the header is tight with Large text.
            if (waiting > 0) androidx.compose.material3.Text(if (waiting > 9) "9+" else "$waiting", style = team.sopa.boar.ui.theme.Boar.type["mono.xs"],
                color = c.textInverse, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, maxLines = 1, softWrap = false,
                modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                    .background(c.emerald500, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 4.dp))
        }
        if (open) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { open = false }, containerColor = c.bgCardElevated) {
            Column(androidx.compose.ui.Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.headerSheet"), style = team.sopa.boar.ui.theme.Boar.type["mono.xs"], color = c.textDim,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = androidx.compose.ui.Modifier.padding(bottom = 6.dp))
                // The last five used, newest first (adam, Nov 11); the rest are one tap away in Manage plugins (Pro).
                val recent = LabPrefs.recentIds()
                val shown = team.sopa.boar.engine.labs.RecentPlugins.pick(labs.map { it.id }, recent).mapNotNull { id -> labs.firstOrNull { it.id == id } }
                shown.forEach { lab ->
                    SheetRow(lab.shownArt, lab.emoji, team.sopa.boar.ui.t("native.labs.${lab.id}.title"), team.sopa.boar.ui.t("native.labs.${lab.id}.body"), lab.status, true,
                        trailing = counts[lab.id]?.takeIf { it > 0 }?.let { n -> { androidx.compose.material3.Text(lab.headerCountLabel(n), style = team.sopa.boar.ui.theme.Boar.type["mono.xs"],
                            color = c.textAccentEmerald, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) } }) { open = false; go("lab:${lab.id}") }
                }
                // Turning labs on and off is Pro's (the list in the menu).
                if (LabPrefs.proProfile) androidx.compose.material3.Text(team.sopa.boar.ui.t("native.labs.manage") + " ›", style = team.sopa.boar.ui.theme.Boar.type.body,
                    color = c.textAccentEmerald, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = androidx.compose.ui.Modifier.clickable { open = false; go("labs") }.padding(horizontal = 8.dp, vertical = 10.dp))
            }
        }
    }

    /** About's version block: each enabled lab as (name, "id status"); empty while Boar Labs is off. */
    @Composable
    fun enabledLabs(): List<Pair<String, String>> {
        Init()
        return LabRegistry.active.map { team.sopa.boar.ui.t("native.labs.${it.id}.title") to "${it.id} ${it.status.name.lowercase()}" }
    }

    /**
     * One row of a plugins sheet, the same in the header's sheet and the answer's 🧩 sheet (UX review E2): a 32 dp
     * picture, the name, an optional line under it, ALPHA or BETA when the plugin is (E3), and what's on the right.
     */
    @Composable
    private fun SheetRow(art: Int?, emoji: String, title: String, line: String?, status: LabStatus?, enabled: Boolean,
                         trailing: (@Composable () -> Unit)?, onClick: () -> Unit) {
        val c = team.sopa.boar.ui.theme.Boar.colors
        Row(androidx.compose.ui.Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            art?.let { r -> androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(r), null,
                androidx.compose.ui.Modifier.size(32.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                ?: androidx.compose.material3.Text(emoji, fontSize = 22.sp)
            Column(androidx.compose.ui.Modifier.weight(1f)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Text(title, style = team.sopa.boar.ui.theme.Boar.type.body, color = if (enabled) c.textPrimary else c.textDim, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = androidx.compose.ui.Modifier.weight(1f, fill = false))
                    if (status == LabStatus.ALPHA || status == LabStatus.BETA) StatusChip(status)
                }
                line?.let { androidx.compose.material3.Text(it, style = team.sopa.boar.ui.theme.Boar.type.caption, color = c.textMuted, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            }
            trailing?.invoke()
        }
    }

    @Composable
    private fun Init() {
        val ctx = LocalContext.current
        remember { LabPrefs.init(ctx) }
    }
}
