package team.sopa.boar.ui.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.BoarEngine
import team.sopa.boar.engine.CpuVariants
import team.sopa.boar.engine.EngineTweaks
import team.sopa.boar.engine.FlashAttn
import team.sopa.boar.engine.KvType
import team.sopa.boar.engine.LazyMode
import team.sopa.boar.engine.LoadMode
import team.sopa.boar.engine.settings.AppSettings
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/** Display settings the app applies live (theme, text size) or by recreating (language). */
interface DisplayPrefs {
    fun setTheme(id: String)
    fun setFontScale(scale: String)
    fun setOrientation(mode: String)
    fun setLanguage(id: String?)
}

/**
 * Settings, one simple page for everyone (2.1.0, BOAR-81): how BOAR looks and speaks, the chats it keeps, who can open
 * it, its plugins, recovery and About. No profiles. How BOAR answers is the chat's style button; the builders' rows
 * (engine knobs, answer length, memory, diagnostics) are in Evaluation › Device setup, from the Model Catalog.
 */
@Composable
fun SettingsScreen(services: BoarServices, prefs: DisplayPrefs, onPin: (String) -> Unit, onSetup: () -> Unit, onOpen: (String) -> Unit = {}, onBack: () -> Unit) {
    // Back from a page Settings opened (a sub page, the PIN, About), Settings is where it was: the same scroll and the
    // same sections open (adam, Nov 11). Back to the chat, the next visit starts at the top, folded.
    androidx.compose.runtime.remember { SettingsMemory.enter() }
    val onOpen: (String) -> Unit = { route -> SettingsMemory.keep = true; onOpen(route) }
    val onPin: (String) -> Unit = { flow -> SettingsMemory.keep = true; onPin(flow) }
    val c = Boar.colors
    val s = services.settings
    Column(Modifier.fillMaxSize().background(c.bgTerminal).statusBarsPadding().navigationBarsPadding()) {
        team.sopa.boar.ui.PageHeader(t("modelSetupScreen.header.title"), onBack)
        val scroll = rememberScrollState(SettingsMemory.scroll)
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { SettingsMemory.scroll = scroll.value } }
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The chat's engine setup isn't Auto (set in Evaluation): said here, with the way back (BOAR-81).
            CustomSetupLine(services)
            val avatarsOn by services.vault.enabled.collectAsState()
            Section(t("modelSetupScreen.sections.displayTheme"), team.sopa.boar.R.drawable.ic_settings_display, summary = {
                val theme = team.sopa.boar.ui.theme.Themes.byId(s.themeId)
                "${theme.icon} ${theme.name} · " + t("native.settings.${s.fontScale}")
            }) {
                var theme by remember { mutableStateOf(s.themeId) }
                team.sopa.boar.ui.appearance.ThemePicker(services.vault, theme,
                    onTheme = { theme = it; s.themeId = it; prefs.setTheme(it); services.vault.record { f -> f.copy(themeChanged = true) } })
                // Boar Avatars live here, a few with the last box opening them all (adam, 23 Nov): no section of their own.
                if (avatarsOn) team.sopa.boar.ui.appearance.AvatarPicker(services.vault) { onOpen("vault") }
                var scale by remember { mutableStateOf(s.fontScale) }
                Choices(t("native.settings.textSize"), listOf("compact", "standard", "large").map { it to t("native.settings.$it") }, scale) { scale = it; s.fontScale = it; prefs.setFontScale(it) }
                var orientation by remember { mutableStateOf(s.screenOrientation) }
                Choices(t("native.settings.orientation"), listOf("auto", "portrait", "landscape").map { it to t("native.settings.orientation.$it") }, orientation) {
                    orientation = it; s.screenOrientation = it; prefs.setOrientation(it)
                }
                var haptics by remember { mutableStateOf(s.hapticsEnabled) }
                SwitchRow(t("interfaceSettings.hapticFeedbackLabel"), t("native.interfaceSettings.hapticFeedbackValue"), haptics) { haptics = it; s.hapticsEnabled = it }
                // The live tok/s while BOAR answers, off by default (adam, 16 Nov).
                var speed by remember { mutableStateOf(s.showLiveSpeed) }
                SwitchRow(t("native.settings.liveSpeed"), t("native.settings.liveSpeedNote"), speed) { speed = it; s.showLiveSpeed = it }
                // Boarverse: Boar Avatars to unlock and wear, off by default (from Experimental, BOAR-81).
                if (team.sopa.boar.appearance.TruffleVault.SWITCH_SHOWN) SwitchRow(t("native.vault.experimental"), t("native.vault.experimentalNote"), avatarsOn) { services.vault.setEnabled(it) }
            }
            // Language and voice in one section (adam: "both has only 1 option each"). Voice listens in the app's language.
            Section(t("native.settings.languageVoice"), team.sopa.boar.R.drawable.ic_settings_language, summary = {
                (when (s.languageId) { "en" -> "English"; "pt" -> "Português"; else -> "System" }) +
                    " · " + t("native.settings.summary.voice", "state" to onOff(s.voiceInputEnabled))
            }) {
                var lang by remember { mutableStateOf(s.languageId ?: "system") }
                Choices(null, listOf("system" to "System", "en" to "English", "pt" to "Português"), lang) { lang = it; val id = it.takeIf { v -> v != "system" }; s.languageId = id; prefs.setLanguage(id) }
                VoiceRows(services, lang)
            }
            Section(t("native.settings.chats"), team.sopa.boar.R.drawable.ic_settings_memory, summary = {
                if (s.maxSavedSessions <= 0) t("native.settings.summary.memoryAll") else t("native.settings.summary.memory", "max" to s.maxSavedSessions)
            }) {
                var auto by remember { mutableStateOf(s.autoSummarize) }
                SwitchRow(t("memorySettings.autoSummarizeLabel"), t("memorySettings.autoSummarizeValue"), auto) { auto = it; s.autoSummarize = it }
                var titles by remember { mutableStateOf(s.autoGenerateTitles) }
                SwitchRow(t("memorySettings.autoTitlesLabel"), t("memorySettings.autoTitlesValue"), titles) { titles = it; s.autoGenerateTitles = it }
                // No "Ask before deleting a chat" since Nov 12: deleting has Undo instead. Turns before summarising: Device setup.
                var max by remember { mutableStateOf(s.maxSavedSessions) }
                // A limit under the chats there are asks first, naming the oldest (validator R-A4; adam, Nov 15: "ok").
                var lowering by remember { mutableStateOf<Pair<Int, List<team.sopa.boar.engine.chat.ChatSession>>?>(null) }
                Choices(t("memorySettings.maxSavedSessions"), listOf(team.sopa.boar.engine.settings.AppSettings.KEEP_ALL_CHATS to t("native.settings.memory.all")) + listOf(5, 10, 25, 50).map { it to "$it" }, max) { v ->
                    val going = runCatching { services.chats.pushedOutAt(v) }.getOrDefault(emptyList())
                    if (going.isEmpty()) { max = v; s.maxSavedSessions = v } else lowering = v to going
                }
                lowering?.let { (v, going) ->
                    androidx.compose.material3.AlertDialog({ lowering = null },
                        title = { Text(t("native.settings.lower.title")) },
                        text = { Text(team.sopa.boar.plugins.UserData.withPluginsLine(
                            if (going.size > 1) t("native.settings.lower.textMore", "max" to v, "title" to going.first().title, "more" to going.size - 1)
                            else t("native.settings.lower.text", "max" to v, "title" to going.first().title), t("native.chat.prune.plugins"))) },
                        confirmButton = { androidx.compose.material3.TextButton({
                            max = team.sopa.boar.engine.settings.AppSettings.KEEP_ALL_CHATS; s.maxSavedSessions = max; lowering = null
                        }) { Text(t("native.chat.prune.keepAll")) } },
                        dismissButton = { androidx.compose.material3.TextButton({
                            max = v; s.maxSavedSessions = v; lowering = null
                            // Now, with what the plugins made for them (P-1), as the chat's own prune does.
                            runCatching { services.chats.prune(v) { id -> team.sopa.boar.plugins.UserData.eraseAnswers(services.chats.messages(id).filter { it.role == "assistant" }.map { it.id }) } }
                        }) { Text(t("native.settings.lower.delete"), color = Boar.colors.crimson400) } },
                        containerColor = Boar.colors.bgCard, titleContentColor = Boar.colors.textHeading, textContentColor = Boar.colors.textSecondary)
                }
                ClearChats(services)
            }
            SecuritySection(services, onPin)
            if (team.sopa.boar.BuildConfig.EXPERIMENTAL_FEATURES) PluginsSection(onOpen)
            RecoverySection(services, prefs, onSetup)
            // The last row: About BOAR (also on the BOAR mark in the menu).
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Boar.colors.bgCard).clickable { onOpen("about") }.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Like the sections' rows: the pack's icon in the heading colour, the name, the chevron (adam, Nov 12).
                team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_info, 22.dp, Boar.colors.textHeading)
                Text(t("chatScreen.drawerItems.about"), style = Boar.type.title, color = Boar.colors.textHeading, modifier = Modifier.weight(1f))
                team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_chevron_right, 20.dp, Boar.colors.textMuted)
            }
        }
    }
}

/**
 * "Chat setup: custom, from Evaluation" with Reset to Auto: only when the chat's engine setup isn't Auto (set in
 * Evaluation › Device setup), so a researcher is never left on a bad setup without seeing it (BOAR-81).
 */
@Composable
private fun CustomSetupLine(services: BoarServices) {
    val c = Boar.colors
    var tweaks by remember { mutableStateOf(services.settings.engineTweaks) }
    if (tweaks.isAuto) return
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.amberBgSubtle).border(1.dp, c.amberBorder, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("native.settings.customSetup"), style = Boar.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text(t("native.settings.customSetupReset"), style = Boar.type.body, color = c.textAccentEmerald, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                services.settings.engineTweaks = EngineTweaks(); tweaks = EngineTweaks()
                // As a change in Device setup: the model reloads with Auto on the next answer, after one being written.
                services.reloadForTweaks()
            }.padding(horizontal = 8.dp, vertical = 8.dp))
    }
}

/** Settings › Plugins (BOAR-81): what plugins are, the Boar Labs switch with its own settings, and the plugins list. */
@Composable
private fun PluginsSection(onOpen: (String) -> Unit) {
    Section(t("native.settings.plugins"), team.sopa.boar.R.drawable.ic_settings_experimental) {
        // The Boar Labs switch, its note adam's line ("Extra tools in the menu and under answers…"), then (when on) restart after a crash, the waiting animation and the storage limit.
        team.sopa.boar.labs.LabHooks.SettingsRow()
        if (team.sopa.boar.labs.LabPrefs.labsOn) LinkButton(t("native.labs.openList")) { onOpen("labs") }
    }
}

/** VoiceSettings.tsx: the mic in the chat, and whether this phone has an on-device recognizer. */
@Composable
private fun VoiceRows(services: BoarServices, language: String) {
    val c = Boar.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val available = remember { team.sopa.boar.voice.VoiceInput.available(ctx) }
    var on by remember { mutableStateOf(services.settings.voiceInputEnabled) }
    SwitchRow(t("voiceSettings.enabledLabel"), t("voiceSettings.enabledValue"), on) { on = it; services.settings.voiceInputEnabled = it }
    Text(t("voiceSettings.speechEngineLabel"), style = Boar.type.subtext, color = c.textMuted)
    Text(if (available) t("native.voice.onDevice") else t("voiceSettings.unavailable"), style = Boar.type.body, color = if (available) c.textAccentEmerald else c.textSecondary)
    if (!available) Text(t("voiceSettings.unavailableNote"), style = Boar.type.micro, color = c.textDim)
    // Voice follows the app's language; with System, the phone's. A phone without that speech pack listens in its own.
    Text(t(if (language == "system") "native.voice.followsPhone" else "native.voice.followsApp"), style = Boar.type.micro, color = c.textDim)
}

@Composable
private fun SecuritySection(services: BoarServices, onPin: (String) -> Unit) {
    val c = Boar.colors
    val on = services.pinLock.isSet
    Section(t("native.settings.security"), team.sopa.boar.R.drawable.ic_settings_pin, summary = { t(if (services.pinLock.isSet) "native.settings.summary.pinOn" else "native.settings.summary.pinOff") }) {
        Text(t(if (on) "native.settings.pinOn" else "native.settings.pinOff"), style = Boar.type.subtext, color = if (on) c.textAccentEmerald else c.textSecondary)
        if (on) {
            LinkButton(t("native.settings.pinChange")) { onPin("CHANGE") }
            LinkButton(t("native.settings.pinRemove"), danger = true) { onPin("REMOVE") }
        } else LinkButton(t("native.settings.pinSet")) { onPin("NEW") }
    }
}

/** MemorySettings.tsx: Clear All Chat History, after a confirmation. */
@Composable
private fun ClearChats(services: BoarServices) {
    var asking by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LinkButton(if (done) t("native.settings.clearChatsDone") else t("memorySettings.clearAllChatHistory"), danger = !done) { if (!done) asking = true }
    // Press projects are the user's documents: kept unless they tick the box (adam, Nov 15: "let user choose"; t9 0115).
    var press by remember { mutableStateOf(0) }
    var alsoPress by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(asking) { if (asking) { alsoPress = false; press = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { team.sopa.boar.plugins.UserData.pressProjects() }.getOrDefault(0) } } }
    if (asking) androidx.compose.material3.AlertDialog({ asking = false },
        title = { Text(t("memorySettings.clearAllConfirmTitle")) },
        text = if (press > 0) { {
            Row(Modifier.fillMaxWidth().clickable { alsoPress = !alsoPress }, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(alsoPress, { alsoPress = it })
                Text(t("native.settings.clearChatsPress", "count" to press), style = Boar.type.body, color = Boar.colors.textSecondary)
            }
        } } else null,
        confirmButton = { androidx.compose.material3.TextButton({
            services.chats.clearAll()
            // Press goes only when the box was ticked: a count that couldn't be read (0) keeps it (validator R-A6).
            val scope = if (alsoPress) team.sopa.boar.plugins.UserData.Scope.CHATS else team.sopa.boar.plugins.UserData.Scope.CHATS_KEEP_PRESS
            runCatching { team.sopa.boar.plugins.UserData.erase(scope) }
            asking = false; done = true
        }) { Text(t("memorySettings.clearAllButton"), color = Boar.colors.crimson400) } },
        dismissButton = { androidx.compose.material3.TextButton({ asking = false }) { Text(t("common.cancel")) } })
}

/** ModelSetupScreen.tsx's System Recovery & Danger Zone: run setup again, or wipe everything and start over. */
@Composable
private fun RecoverySection(services: BoarServices, prefs: DisplayPrefs, onSetup: () -> Unit) {
    val c = Boar.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var purging by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    Section(t("modelSetupScreen.sections.recovery"), team.sopa.boar.R.drawable.ic_settings_recovery, iconTint = Boar.colors.crimson400) {
        Text(t("modelSetupScreen.recovery.wizardTitle"), style = Boar.type.titleSm, color = c.textHeading)
        Text(t("modelSetupScreen.recovery.wizardDesc"), style = Boar.type.subtext, color = c.textSecondary)
        LinkButton(t("modelSetupScreen.recovery.wizardButton"), onClick = onSetup)
        // No "See the tour" since 23 Nov (adam): the longer setup guide replaced the tour, which nothing opens now.
        // Defaults back without the multi-GB re-download "Clear all data" costs.
        Text(t("native.settings.resetTitle"), style = Boar.type.titleSm, color = c.textHeading, modifier = Modifier.padding(top = 8.dp))
        Text(t("native.settings.resetDesc"), style = Boar.type.subtext, color = c.textSecondary)
        LinkButton(t("native.settings.resetButton")) { resetting = true }
        Text(t("modelSetupScreen.recovery.dangerBadge"), style = Boar.type["mono.xs"], color = c.crimson400, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(t("modelSetupScreen.recovery.dangerDesc"), style = Boar.type.subtext, color = c.textSecondary)
        LinkButton(t("modelSetupScreen.recovery.dangerButton"), danger = true) { purging = true }
    }
    if (resetting) androidx.compose.material3.AlertDialog({ resetting = false },
        title = { Text(t("native.settings.resetConfirmTitle")) },
        text = { Text(t("native.settings.resetConfirmBody")) },
        confirmButton = { androidx.compose.material3.TextButton({
            resetting = false
            services.settings.resetPreferences()
            // BOAR's own avatar again; the boars unlocked in the vault stay unlocked.
            services.vault.equip(null)
            prefs.setTheme(team.sopa.boar.ui.theme.ThemeTokens.DEFAULT_ID)
            prefs.setFontScale("standard")
            prefs.setOrientation(team.sopa.boar.engine.settings.AppSettings.DEFAULT_ORIENTATION)
            onSetup()
        }) { Text(t("native.settings.resetConfirmButton"), color = Boar.colors.crimson400) } },
        dismissButton = { androidx.compose.material3.TextButton({ resetting = false }) { Text(t("common.cancel")) } })
    if (purging) androidx.compose.material3.AlertDialog({ purging = false },
        title = { Text(t("modelSetupScreen.dangerModal.title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("modelSetupScreen.dangerModal.subtitle"))
                Text(t("modelSetupScreen.dangerModal.consequencesHeader"), fontWeight = FontWeight.Bold)
                listOf("consequenceModels", "consequenceEmbeddings", "consequenceHistory").forEach { Text("• " + t("modelSetupScreen.dangerModal.$it")) }
            }
        },
        // Android's own "clear data": every file, setting and key of the app, then it closes; next start is setup.
        confirmButton = { androidx.compose.material3.TextButton({ ctx.getSystemService(android.app.ActivityManager::class.java).clearApplicationUserData() }) {
            Text(t("modelSetupScreen.dangerModal.confirmButton"), color = Boar.colors.crimson400) } },
        dismissButton = { androidx.compose.material3.TextButton({ purging = false }) { Text(t("common.cancel")) } })
}

@Composable
internal fun LinkButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Boar.colors
    Text(label, style = Boar.type.body, fontWeight = FontWeight.SemiBold, color = if (danger) c.crimson400 else c.textAccentCyan,
        modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(if (danger) c.crimsonBgSubtle else c.bgCard).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp))
}





/**
 * Every section folds (Oct 24): How BOAR answers starts open, the rest folded with a one-line [summary] of what they're
 * set to, so the page reads at a glance. What the user opens stays open while Settings is on screen (rotation too);
 * the next visit starts folded again. [summary] is read when the section folds, so it shows what was just changed.
 */
@Composable
internal fun Section(title: String, icon: Int? = null, startOpen: Boolean = false, summary: (@Composable () -> String?)? = null,
    iconTint: androidx.compose.ui.graphics.Color? = null, content: @Composable () -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(SettingsMemory.open[title] ?: startOpen) }
    androidx.compose.runtime.SideEffect { SettingsMemory.open[title] = open }
    team.sopa.boar.ui.BoarSection(title, open, { open = !open }, if (open) null else summary?.invoke(), icon, iconTint) { content() }
}


@Composable
private fun onOff(on: Boolean) = t(if (on) "native.settings.summary.on" else "native.settings.summary.off")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> Choices(label: String?, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = Boar.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { Text(it, style = Boar.type.caption, color = c.textSecondary, fontWeight = FontWeight.SemiBold) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((value, text) in options) {
                val on = value == selected
                Text(text, style = Boar.type.caption, color = if (on) c.textAccentEmerald else c.textSecondary,
                    modifier = Modifier.background(if (on) c.emeraldBgSubtle else c.bgSubtle, RoundedCornerShape(16.dp))
                        .border(1.dp, if (on) c.emeraldBorder else c.borderSubtle, RoundedCornerShape(16.dp))
                        .clickable { onSelect(value) }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
internal fun SwitchRow(label: String, note: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Boar.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Boar.type.body, color = c.textPrimary)
            note?.let { Text(it, style = Boar.type.subtext, color = c.textMuted) }
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.emerald500, checkedThumbColor = c.textInverse))
    }
}

@Composable
internal fun Note(text: String) = Text(text, style = Boar.type.subtext, color = Boar.colors.textMuted)


/**
 * Where Settings was when it opened another page: its scroll and the sections open. [keep] is set by that page's link;
 * coming back restores it once. Any other way in (from the menu) starts at the top, folded.
 */
internal object SettingsMemory {
    @Volatile var keep = false
    var scroll = 0
    val open = HashMap<String, Boolean>()

    fun enter() {
        if (!keep) { scroll = 0; open.clear() }
        keep = false
    }
}
