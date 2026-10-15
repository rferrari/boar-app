package team.sopa.boar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import team.sopa.boar.ui.theme.ThemeTokens

/** Display settings the app applies live (theme, text size) or by recreating (language). */
interface DisplayPrefs {
    fun setTheme(id: String)
    fun setFontScale(scale: String)
    fun setLanguage(id: String?)
}

/**
 * Settings (port of ModelSetupScreen.tsx's sections that the native app has so far: tone, memory, display, language),
 * plus Advanced tweaks: every engine knob, Auto by default.
 */
@Composable
fun SettingsScreen(services: BoarServices, prefs: DisplayPrefs, onPin: (String) -> Unit, onSetup: () -> Unit, onOpen: (String) -> Unit = {}, onBack: () -> Unit) {
    val c = Boar.colors
    val s = services.settings
    var tech by remember { mutableStateOf(s.showLiveStats) }
    Column(Modifier.fillMaxSize().background(c.bgTerminal).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(c.bgSurface).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("←", style = Boar.type.titleLg, color = c.textPrimary, modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            Text(t("modelSetupScreen.header.title"), style = Boar.type.titleLg, color = c.textHeading)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The agreed order (Oct 14): answering, what BOAR knows, how it looks and speaks, who can open it, then the
            // technical corner. Hardware telemetry is a technical detail, shown with Advanced tweaks → Show technical details.
            ToneSection(services)
            ModelsSection(services, onOpen)
            KnowledgeSection(services, onOpen)
            Section(t("modelSetupScreen.sections.memory")) {
                var auto by remember { mutableStateOf(s.autoSummarize) }
                SwitchRow(t("memorySettings.autoSummarizeLabel"), t("memorySettings.autoSummarizeValue"), auto) { auto = it; s.autoSummarize = it }
                var titles by remember { mutableStateOf(s.autoGenerateTitles) }
                SwitchRow(t("memorySettings.autoTitlesLabel"), t("memorySettings.autoTitlesValue"), titles) { titles = it; s.autoGenerateTitles = it }
                var turns by remember { mutableStateOf(s.historyTurnThreshold) }
                Choices(t("memorySettings.turnsBeforeSummarizing"), listOf(4, 6, 8, 12).map { it to "$it" }, turns) { turns = it; s.historyTurnThreshold = it }
                var max by remember { mutableStateOf(s.maxSavedSessions) }
                Choices(t("memorySettings.maxSavedSessions"), listOf(5, 10, 25, 50).map { it to "$it" }, max) { max = it; s.maxSavedSessions = it }
                ClearChats(services)
            }
            Section(t("modelSetupScreen.sections.displayTheme")) {
                var theme by remember { mutableStateOf(ThemeTokens.byId(s.themeId).id) }
                Choices(null, ThemeTokens.all.map { it.id to "${it.icon} ${it.name}" }, theme) { theme = it; s.themeId = it; prefs.setTheme(it) }
                var scale by remember { mutableStateOf(s.fontScale) }
                Choices(t("native.settings.textSize"), listOf("compact", "standard", "large").map { it to t("native.settings.$it") }, scale) { scale = it; s.fontScale = it; prefs.setFontScale(it) }
                var haptics by remember { mutableStateOf(s.hapticsEnabled) }
                SwitchRow(t("interfaceSettings.hapticFeedbackLabel"), t("interfaceSettings.hapticFeedbackValue"), haptics) { haptics = it; s.hapticsEnabled = it }
            }
            Section(t("modelSetupScreen.sections.language")) {
                var lang by remember { mutableStateOf(s.languageId ?: "system") }
                Choices(null, listOf("system" to "System", "en" to "English", "pt" to "Português"), lang) { lang = it; val id = it.takeIf { v -> v != "system" }; s.languageId = id; prefs.setLanguage(id) }
            }
            SecuritySection(services, onPin)
            VoiceSection(services)
            if (tech) TelemetrySection(services)
            TweaksSection(services, tech) { tech = it }
            RecoverySection(onSetup)
        }
    }
}

/** ModelSetupScreen.tsx's Reasoning Models: what answers now, and the catalog (download, use, remove, Hugging Face search). */
@Composable
private fun ModelsSection(services: BoarServices, onOpen: (String) -> Unit) {
    val c = Boar.colors
    Section(t("modelSetupScreen.sections.models")) {
        val llms = services.installed(team.sopa.boar.engine.models.AssetKind.LLM)
        Text(t("native.settings.activeModel", "model" to (services.answerModel()?.shortName ?: "—")), style = Boar.type.body, color = c.textPrimary)
        Text(t("modelSetupScreen.installedModels") + ": " + llms.joinToString(", ") { it.shortName }.ifEmpty { "—" }, style = Boar.type.subtext, color = c.textSecondary)
        // The embedding model searches the knowledge packs and documents; it isn't a choice, so not in the catalog.
        val emb = services.catalog.models.firstOrNull { it.kind == team.sopa.boar.engine.models.AssetKind.EMBEDDING }
        if (emb != null) Text(t(if (services.fileOf(emb).isFile) "native.settings.embeddingOk" else "native.settings.embeddingMissing", "model" to emb.shortName),
            style = Boar.type.subtext, color = c.textSecondary)
        LinkButton(t("native.settings.openCatalog")) { onOpen("models") }
    }
}

/** CorpusSettingsTab.tsx: the knowledge packs on the phone and your documents. */
@Composable
private fun KnowledgeSection(services: BoarServices, onOpen: (String) -> Unit) {
    val c = Boar.colors
    Section(t("modelSetupScreen.sections.knowledgeBase")) {
        val packs = services.catalog.corpusCatalog.filter { services.fileOf(it).isFile }
        Text(if (packs.isEmpty()) t("native.settings.noPacks") else t("native.settings.packsInstalled", "packs" to packs.joinToString(", ") { it.shortName }),
            style = Boar.type.subtext, color = c.textSecondary)
        LinkButton(t("corpusSettingsTab.downloadablePacks")) { onOpen("documents") }
        Text(t("native.settings.documents", "count" to services.knowledgeBase.collections().size), style = Boar.type.subtext, color = c.textSecondary)
        LinkButton(t("native.settings.openDocuments")) { onOpen("documents") }
    }
}

/** UsageStatsContent / SystemMonitor: this phone's hardware, what BOAR uses now, and the evaluations it ran. Read only. */
@Composable
private fun TelemetrySection(services: BoarServices) {
    val c = Boar.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sharing = remember { team.sopa.boar.share.ResultsSharing(ctx, services.settings) }
    val d = remember { sharing.device() }
    val resident = remember { team.sopa.boar.app.AppMemory.residentBytes() }
    val storage = remember { services.storageUsedBytes() }
    val evals = remember {
        java.io.File(ctx.filesDir, "eval").listFiles { f -> f.name.endsWith(".jsonl") }.orEmpty().sortedBy { it.lastModified() }
    }
    val last = remember(evals) {
        evals.lastOrNull()?.let { f ->
            val rows = f.readLines().filter { it.isNotBlank() }.mapNotNull { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) as kotlinx.serialization.json.JsonObject }.getOrNull() }
            val tps = rows.mapNotNull { (it["tokPerSec"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() }.sorted()
            val ok = rows.count { (it["outcome"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "success" }
            team.sopa.boar.ui.I18n.t(ctx, "native.telemetry.lastEvalValue", "ok" to ok, "n" to rows.size, "tps" to (if (tps.isEmpty()) "—" else String.format(java.util.Locale.US, "%.1f", tps[tps.size / 2])),
                "date" to java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(f.lastModified())))
        } ?: team.sopa.boar.ui.I18n.t(ctx, "native.telemetry.none")
    }
    fun gb(b: Long) = String.format(java.util.Locale.US, "%.1f GB", b / 1073741824.0)
    Section(t("modelSetupScreen.sections.telemetry")) {
        val inf = d.cpuFeatures.orEmpty()
        listOf(
            t("native.telemetry.device") to listOfNotNull(d.brand, d.model).joinToString(" ") + " · Android ${d.osVersion}",
            t("native.telemetry.chip") to (team.sopa.boar.engine.eval.ShareResults.describeChipset(d) ?: "—"),
            t("native.telemetry.cores") to (team.sopa.boar.engine.eval.ShareResults.describeCores(d.coreMaxFreqKHz) ?: "${d.cpuCores ?: "—"}"),
            t("native.telemetry.ram") to (d.ramBytes?.let(::gb) ?: "—"),
            t("native.telemetry.features") to listOf("dotprod" to "asimddp", "i8mm" to "i8mm", "SVE" to "sve", "SME" to "sme").joinToString(" · ") { (n, f) -> (if (f in inf) "✓ " else "✗ ") + n },
            t("native.telemetry.build") to (team.sopa.boar.engine.BoarEngine.loadedVariant ?: "—"),
            t("native.telemetry.appMemory") to gb(resident),
            t("native.telemetry.storage") to gb(storage),
            t("native.telemetry.chats") to "${services.chats.listSessions().size}",
            t("native.telemetry.evals") to "${evals.size}",
            t("native.telemetry.lastEval") to last,
        ).forEach { (k, v) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(k, style = Boar.type.subtext, color = c.textMuted, modifier = Modifier.weight(0.45f))
                Text(v, style = Boar.type["mono.xs"], color = c.textPrimary, modifier = Modifier.weight(0.55f))
            }
        }
        Text(t("native.telemetry.note"), style = Boar.type.micro, color = c.textDim)
    }
}

/** VoiceSettings.tsx: the mic in the chat, and whether this phone has an on-device recognizer. */
@Composable
private fun VoiceSection(services: BoarServices) {
    val c = Boar.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val available = remember { team.sopa.boar.voice.VoiceInput.available(ctx) }
    Section(t("modelSetupScreen.sections.voice")) {
        var on by remember { mutableStateOf(services.settings.voiceInputEnabled) }
        SwitchRow(t("voiceSettings.enabledLabel"), t("voiceSettings.enabledValue"), on) { on = it; services.settings.voiceInputEnabled = it }
        Text(t("voiceSettings.speechEngineLabel"), style = Boar.type.subtext, color = c.textMuted)
        Text(if (available) t("native.voice.onDevice") else t("voiceSettings.unavailable"), style = Boar.type.body, color = if (available) c.textAccentEmerald else c.textSecondary)
        if (!available) Text(t("voiceSettings.unavailableNote"), style = Boar.type.micro, color = c.textDim)
    }
}

@Composable
private fun SecuritySection(services: BoarServices, onPin: (String) -> Unit) {
    val c = Boar.colors
    val on = services.pinLock.isSet
    Section(t("native.settings.security")) {
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
    if (asking) androidx.compose.material3.AlertDialog({ asking = false },
        title = { Text(t("memorySettings.clearAllConfirmTitle")) },
        confirmButton = { androidx.compose.material3.TextButton({ services.chats.clearAll(); asking = false; done = true }) { Text(t("memorySettings.clearAllButton"), color = Boar.colors.crimson400) } },
        dismissButton = { androidx.compose.material3.TextButton({ asking = false }) { Text(t("common.cancel")) } })
}

/** ModelSetupScreen.tsx's System Recovery & Danger Zone: run setup again, or wipe everything and start over. */
@Composable
private fun RecoverySection(onSetup: () -> Unit) {
    val c = Boar.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var purging by remember { mutableStateOf(false) }
    Section(t("modelSetupScreen.sections.recovery")) {
        Text(t("modelSetupScreen.recovery.wizardTitle"), style = Boar.type.titleSm, color = c.textHeading)
        Text(t("modelSetupScreen.recovery.wizardDesc"), style = Boar.type.subtext, color = c.textSecondary)
        LinkButton(t("modelSetupScreen.recovery.wizardButton"), onClick = onSetup)
        Text(t("modelSetupScreen.recovery.dangerBadge"), style = Boar.type["mono.xs"], color = c.crimson400, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(t("modelSetupScreen.recovery.dangerDesc"), style = Boar.type.subtext, color = c.textSecondary)
        LinkButton(t("modelSetupScreen.recovery.dangerButton"), danger = true) { purging = true }
    }
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
private fun LinkButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Boar.colors
    Text(label, style = Boar.type.body, fontWeight = FontWeight.SemiBold, color = if (danger) c.crimson400 else c.textAccentCyan,
        modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(if (danger) c.crimsonBgSubtle else c.bgCard).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp))
}

@Composable
private fun ToneSection(services: BoarServices) {
    val s = services.settings
    val personalities = services.personalities
    Section(t("modelSetupScreen.sections.tone")) {
        var id by remember { mutableStateOf(s.personalityId ?: personalities.defaultId) }
        Choices(null, personalities.personalities.map { it.id to "${it.icon} ${t("personalities.${it.id}.label")}" }, id) { id = it; s.personalityId = it }
        Text(t("personalities.$id.description"), style = Boar.type.subtext, color = Boar.colors.textMuted)
        if (id == "custom") {
            var prompt by remember { mutableStateOf(s.customSystemPrompt ?: "") }
            Field(prompt, t("personalitySettings.customPromptPlaceholder")) { prompt = it; s.customSystemPrompt = it }
        }
        var tokens by remember { mutableStateOf(s.maxTokensChoice) }
        var deep by remember { mutableStateOf(s.deepResearchMode) }
        val auto = AppSettings.autoMaxTokens(id, deep)
        Choices(t("personalitySettings.maxOutputTokens"),
            listOf<Pair<Int?, String>>(null to "${t("native.tweaks.auto")} ($auto)") + personalities.maxTokensOptions.map { it to "$it" },
            tokens) { tokens = it; s.maxTokensChoice = it }
        Note(t("personalitySettings.maxOutputTokensNote"))
        SwitchRow(t("personalitySettings.deepResearchTitle"), t("personalitySettings.deepResearchNote"), deep) { deep = it; s.deepResearchMode = it }
        var adaptive by remember { mutableStateOf(s.adaptiveRoutingEnabled) }
        SwitchRow(t("native.settings.adaptiveTitle"), t("native.settings.adaptiveNote"), adaptive) { adaptive = it; s.adaptiveRoutingEnabled = it }
    }
}

@Composable
private fun TweaksSection(services: BoarServices, technical: Boolean, onTechnical: (Boolean) -> Unit) {
    val s = services.settings
    val scope = rememberCoroutineScope()
    var tw by remember { mutableStateOf(s.engineTweaks) }
    val topology = services.topology
    val resolved = tw.resolve(topology, services.totalRamBytes)
    val auto = EngineTweaks().resolve(topology, services.totalRamBytes).params
    val features = remember { CpuVariants.readFeatures() }
    fun save(next: EngineTweaks) {
        tw = next
        s.engineTweaks = next
        // The model reloads with the new settings on the next answer.
        scope.launch { services.host.unload() }
    }
    val autoLabel = t("native.tweaks.auto")
    @Composable fun autoOf(value: Any) = t("native.tweaks.autoValue", "value" to value)
    Section(t("native.tweaks.title"), collapsible = true) {
        Note(t("native.tweaks.intro"))
        // Presets first: one tap sets the knobs below; any knob changed by hand afterwards makes it Custom.
        val preset = team.sopa.boar.engine.TweakPreset.of(tw)
        Choices(
            t("native.tweaks.presets"),
            team.sopa.boar.engine.TweakPreset.entries.map<team.sopa.boar.engine.TweakPreset, Pair<team.sopa.boar.engine.TweakPreset?, String>> { it to "${it.emoji} ${t("native.tweaks.preset.${it.key}")}" },
            preset,
        ) { p -> p?.let { save(it.applyTo(tw)) } }
        Note(preset?.let { t("native.tweaks.preset.${it.key}Note") } ?: t("native.tweaks.presetCustom"))
        val cpuAuto = CpuVariants.auto(features)?.let { n -> CpuVariants.ALL.first { it.name == n }.label } ?: t("native.tweaks.cpuBest")
        Choices(
            t("native.tweaks.cpuBuild"),
            listOf(CpuVariants.AUTO to autoOf(cpuAuto), CpuVariants.BEST to t("native.tweaks.cpuBest")) + CpuVariants.supported(features).map { it.name to it.label },
            tw.cpuBuild,
        ) { save(tw.copy(cpuBuild = it)) }
        Note(t("native.tweaks.cpuBuildNote") + " " + t("native.tweaks.appliesRestart") + " (" + (BoarEngine.loadedVariant ?: "—") + ")")
        val n = topology.cores.size
        Choices(t("native.tweaks.threads"), listOf<Pair<Int?, String>>(null to autoOf(auto.threads)) + (1..n).map { it to "$it" }, tw.threads) { save(tw.copy(threads = it)) }
        Note(t("native.tweaks.threadsNote"))
        Choices(t("native.tweaks.threadsBatch"), listOf<Pair<Int?, String>>(null to autoOf(auto.threadsBatch)) + (1..n).map { it to "$it" }, tw.threadsBatch) { save(tw.copy(threadsBatch = it)) }
        Choices(t("native.tweaks.cores"), listOf(EngineTweaks.AUTO to autoOf(t("native.tweaks.coresFast")), EngineTweaks.ALL to t("native.tweaks.coresAll")), tw.cores) { save(tw.copy(cores = it)) }
        SwitchRow(t("native.tweaks.strict"), null, tw.strictCpu) { save(tw.copy(strictCpu = it)) }
        Choices(t("native.tweaks.context"), listOf<Pair<Int?, String>>(null to autoOf(auto.nCtx)) + listOf(2048, 3072, 4096, 8192, 16384).map { it to "$it" }, tw.contextTokens) { save(tw.copy(contextTokens = it)) }
        Choices(t("native.tweaks.batch"), listOf<Pair<Int?, String>>(null to autoOf(auto.nBatch)) + listOf(128, 256, 512, 1024, 2048).map { it to "$it" }, tw.batch) { save(tw.copy(batch = it)) }
        Choices(t("native.tweaks.microBatch"), listOf<Pair<Int?, String>>(null to autoOf(auto.nUbatch)) + listOf(64, 128, 256, 512).map { it to "$it" }, tw.microBatch) { save(tw.copy(microBatch = it)) }
        Choices(t("native.tweaks.kv"), listOf<Pair<KvType?, String>>(null to autoOf(auto.typeK.name)) + KvType.entries.map { it to it.name }, tw.kvCache) { save(tw.copy(kvCache = it)) }
        Note(t("native.tweaks.kvNote"))
        Choices(t("native.tweaks.flash"), listOf(FlashAttn.AUTO to autoLabel, FlashAttn.ON to t("native.tweaks.on"), FlashAttn.OFF to t("native.tweaks.off")), tw.flashAttention) { save(tw.copy(flashAttention = it)) }
        Choices(
            t("native.tweaks.load"),
            listOf<Pair<LoadMode?, String>>(null to autoOf(t("native.tweaks.loadMmap")), LoadMode.MMAP_MLOCK to t("native.tweaks.loadLock"), LoadMode.NONE to t("native.tweaks.loadRead")),
            tw.loadMode,
        ) { save(tw.copy(loadMode = it)) }
        Choices(t("native.tweaks.repack"), listOf<Pair<Boolean?, String>>(null to autoOf(t("native.tweaks.on")), true to t("native.tweaks.on"), false to t("native.tweaks.off")), tw.weightRepack) { save(tw.copy(weightRepack = it)) }
        Note(t("native.tweaks.repackNote"))
        Choices(t("native.tweaks.thinking"), listOf<Pair<Boolean?, String>>(null to autoOf(t("native.tweaks.off")), true to t("native.tweaks.on"), false to t("native.tweaks.off")), tw.thinking) { save(tw.copy(thinking = it)) }
        Note(t("native.tweaks.thinkingNote"))
        Choices(t("native.tweaks.moe"), listOf<Pair<LazyMode?, String>>(null to autoOf(t("native.tweaks.off")), LazyMode.AUTO to autoLabel, LazyMode.ON to t("native.tweaks.on")), tw.lazyExperts) { save(tw.copy(lazyExperts = it)) }
        val autoMin = (EngineTweaks.autoStageTimeoutMs(s.maxTokens) / 60_000.0).let { "%.0f".format(it) }
        Choices(
            t("native.tweaks.timeout"),
            listOf<Pair<Int?, String>>(null to autoOf(t("native.tweaks.minutes", "n" to autoMin))) + listOf(2, 5, 10, 20).map { it * 60 to t("native.tweaks.minutes", "n" to it) } + listOf(0 to t("native.tweaks.noLimit")),
            tw.stageTimeoutSeconds,
        ) { save(tw.copy(stageTimeoutSeconds = it)) }
        Note(t("native.tweaks.timeoutNote"))
        if (resolved.notes.isNotEmpty()) Note(t("native.tweaks.adjusted", "notes" to resolved.notes.joinToString("; ")))
        Note(t("native.tweaks.appliesNext"))
        if (!tw.isAuto) Text(
            t("native.tweaks.resetAll"), style = Boar.type.body, color = Boar.colors.textAccentEmerald, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { save(EngineTweaks()) }.padding(vertical = 6.dp),
        )
        // One switch for everything technical in the chat and the menu (the receipt's time and speed stay for everyone).
        SwitchRow(t("native.tweaks.technicalDetails"), t("native.tweaks.technicalDetailsNote"), technical) { s.showLiveStats = it; onTechnical(it) }
    }
}

@Composable
private fun Section(title: String, collapsible: Boolean = false, content: @Composable () -> Unit) {
    val c = Boar.colors
    // A collapsible section starts closed: Advanced tweaks are for people who go looking for them.
    var open by remember { mutableStateOf(!collapsible) }
    Column(
        Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (collapsible) Row(Modifier.fillMaxWidth().clickable(onClickLabel = title) { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Boar.type.title, color = c.textHeading, modifier = Modifier.weight(1f))
            Text(if (open) "▴" else "▾", style = Boar.type.title, color = c.textMuted)
        } else Text(title, style = Boar.type.title, color = c.textHeading)
        if (open) content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(label: String?, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
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
private fun SwitchRow(label: String, note: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
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
private fun Note(text: String) = Text(text, style = Boar.type.subtext, color = Boar.colors.textMuted)

@Composable
private fun Field(value: String, placeholder: String, onChange: (String) -> Unit) {
    val c = Boar.colors
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().background(c.bgInput, RoundedCornerShape(8.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(8.dp)).padding(10.dp)) {
        if (value.isEmpty()) Text(placeholder, style = Boar.type.body, color = c.textDim)
        BasicTextField(value, onChange, textStyle = Boar.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(c.emerald500), modifier = Modifier.fillMaxWidth())
    }
}
