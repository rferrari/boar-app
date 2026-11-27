package team.sopa.boar.ui.eval

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import team.sopa.boar.ui.settings.Choices
import team.sopa.boar.ui.settings.LinkButton
import team.sopa.boar.ui.settings.Note
import team.sopa.boar.ui.settings.Section
import team.sopa.boar.ui.settings.SwitchRow
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * Evaluation › Device setup (2.1.0, BOAR-81): the builders' rows that were in Settings (Advanced tweaks, Experimental,
 * Hardware telemetry), so Settings is one simple page for everyone. Four groups: Engine, Answering, Memory, Diagnostics.
 * The engine's knobs still apply to the chat on its next answer, as before; runs don't use them yet (2.2.0).
 */
@Composable
fun DeviceSetup(services: BoarServices) {
    val s = services.settings
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Note(t("native.eval.deviceSetupNote"))
        Section(t("native.deviceSetup.engine"), team.sopa.boar.R.drawable.ic_settings_tweaks) {
            EngineRows(services)
        }
        Section(t("native.deviceSetup.answering"), team.sopa.boar.R.drawable.ic_settings_tone) {
            // Max answer length: Auto by style (and Deep Research), or a fixed limit.
            var tokens by remember { mutableStateOf(s.maxTokensChoice) }
            val auto = AppSettings.autoMaxTokens(s.personalityId ?: services.personalities.defaultId, s.deepResearchMode)
            Choices(t("personalitySettings.maxOutputTokens"),
                listOf<Pair<Int?, String>>(null to "${t("native.tweaks.auto")} ($auto)") + services.personalities.maxTokensOptions.map { it to "$it" },
                tokens) { tokens = it; s.maxTokensChoice = it }
            Note(t("personalitySettings.maxOutputTokensNote"))
            var adaptive by remember { mutableStateOf(s.adaptiveRoutingEnabled) }
            SwitchRow(t("native.settings.adaptiveTitle"), t("native.settings.adaptiveNote"), adaptive) { adaptive = it; s.adaptiveRoutingEnabled = it }
            // Staged answers, core since Nov 12: on by default, this is the fallback switch.
            var stagedOn by remember { mutableStateOf(s.stagedQuotes) }
            SwitchRow(t("native.tweaks.stagedQuotes"), t("native.tweaks.stagedQuotesNote"), stagedOn) { stagedOn = it; s.stagedQuotes = it }
            var turns by remember { mutableStateOf(s.historyTurnThreshold) }
            Choices(t("memorySettings.turnsBeforeSummarizing"), listOf(4, 6, 8, 12).map { it to "$it" }, turns) { turns = it; s.historyTurnThreshold = it }
        }
        Section(t("native.deviceSetup.memory"), team.sopa.boar.R.drawable.ic_settings_memory) {
            MemoryRows(services)
        }
        Section(t("native.deviceSetup.diagnostics"), team.sopa.boar.R.drawable.ic_settings_hardware) {
            // One switch for everything technical in the chat and the menu (the receipt's time and speed stay for everyone).
            var technical by remember { mutableStateOf(s.showLiveStats) }
            SwitchRow(t("native.tweaks.technicalDetails"), t("native.tweaks.technicalDetailsNote"), technical) { technical = it; s.showLiveStats = it }
            TelemetryRows(services)
            if (team.sopa.boar.app.DebugLog.available) DebugLogRows()
        }
    }
}

/** Free memory when I leave BOAR, and after how long (Settings › Advanced tweaks / Experimental until 2.1.0). */
@Composable
internal fun MemoryRows(services: BoarServices) {
    val s = services.settings
    @Composable fun autoOf(value: Any) = t("native.tweaks.autoValue", "value" to value)
    var free by remember { mutableStateOf(s.freeMemoryInBackground) }
    SwitchRow(t("native.settings.freeMemory"), t("native.settings.freeMemoryNote"), free) { free = it; s.freeMemoryInBackground = it }
    var freeAfter by remember { mutableStateOf(s.freeMemoryAfterMinutes) }
    Choices(
        t("native.tweaks.freeMemoryAfter"),
        listOf<Pair<Int?, String>>(null to autoOf(t("native.tweaks.immediately"))) +
            team.sopa.boar.engine.settings.FreeMemory.CHOICES_MINUTES.map { it to t("native.tweaks.minutes", "n" to it) },
        // A saved Immediately (0) is what Auto does now, so it shows as Auto.
        freeAfter.takeIf { it != 0 },
    ) { freeAfter = it; s.freeMemoryAfterMinutes = it }
    Note(t("native.tweaks.freeMemoryAfterNote"))
}

/** The engine's knobs (Advanced tweaks until 2.1.0): presets first, then each knob, Auto by default; Reset all. */
@Composable
internal fun EngineRows(services: BoarServices) {
    val s = services.settings
    var tw by remember { mutableStateOf(s.engineTweaks) }
    val topology = services.topology
    val resolved = tw.resolve(topology, services.totalRamBytes)
    val auto = EngineTweaks().resolve(topology, services.totalRamBytes).params
    val features = remember { CpuVariants.readFeatures() }
    fun save(next: EngineTweaks) {
        tw = next
        s.engineTweaks = next
        if (!next.isAuto) services.vault.record { f -> f.copy(tweaksChanged = true) }
        // The model reloads with the new settings on the next answer.
        // An answer being written stops first, so the unload doesn't cut it mid-sentence (validator Q-5).
        // Still answering after the wait: the model stays loaded and picks the new settings up on its next load (validator).
        services.reloadForTweaks()
    }
    val autoLabel = t("native.tweaks.auto")
    @Composable fun autoOf(value: Any) = t("native.tweaks.autoValue", "value" to value)
    Note(t("native.tweaks.intro"))
    // Presets first: one tap sets the knobs below; any knob changed by hand afterwards makes it Custom.
    val preset = team.sopa.boar.engine.TweakPreset.of(tw)
    var runCard by remember { mutableStateOf(false) }
    Choices(
        t("native.tweaks.presets"),
        team.sopa.boar.engine.TweakPreset.entries.map<team.sopa.boar.engine.TweakPreset, Pair<team.sopa.boar.engine.TweakPreset?, String>> { it to "${it.emoji} ${t("native.tweaks.preset.${it.key}")}" },
        preset,
    ) { p ->
        // Run Boar Run picked by hand for the first time: say what it costs before switching (once).
        if (p == team.sopa.boar.engine.TweakPreset.RUN && !s.runPresetWarned) runCard = true
        else p?.let { save(it.applyTo(tw)) }
    }
    if (runCard) {
        val lowRam = services.totalRamBytes in 1 until 8_000_000_000L
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { runCard = false },
            title = { Text(t("native.tweaks.preset.run")) },
            text = { Text(t("native.tweaks.runWarning") + if (lowRam) "\n\n" + t("native.tweaks.runWarningLowRam") else "") },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    s.runPresetWarned = true; runCard = false; save(team.sopa.boar.engine.TweakPreset.RUN.applyTo(tw))
                }) { Text(t("native.tweaks.runWarningGo"), color = Boar.colors.textAccentEmerald) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton({ runCard = false }) {
                    Text(if (preset == team.sopa.boar.engine.TweakPreset.PATH) t("native.tweaks.runWarningKeepPath") else t("common.cancel"), color = Boar.colors.textSecondary)
                }
            },
        )
    }
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
    Choices(t("native.tweaks.moe"), listOf<Pair<LazyMode?, String>>(null to autoOf(t("native.tweaks.off")), LazyMode.AUTO to t("native.tweaks.moeWhenNeeded"), LazyMode.ON to t("native.tweaks.on")), tw.lazyExperts) { save(tw.copy(lazyExperts = it)) }
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
}

/** UsageStatsContent / SystemMonitor: this phone's hardware, what BOAR uses now, and the evaluations it ran. Read only (Diagnostics). */
@Composable
internal fun TelemetryRows(services: BoarServices) {
    val c = Boar.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sharing = remember { team.sopa.boar.share.ResultsSharing(ctx, services.settings) }
    val d = remember { sharing.device() }
    // Read every 5 s while the section is open, like the menu's bars.
    var app by remember { mutableStateOf(0L) }
    var phone by remember { mutableStateOf(team.sopa.boar.app.AppMemory.Phone(0L, 0L)) }
    var storage by remember { mutableStateOf(0L) }
    var fetching by remember { mutableStateOf(0L) }
    var chatCount by remember { mutableStateOf<Int?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                app = team.sopa.boar.app.AppMemory.residentBytes(); phone = team.sopa.boar.app.AppMemory.phone(ctx)
                storage = services.storageUsedBytes(); fetching = team.sopa.boar.engine.models.AssetStore.downloadingBytes(services.filesDir)
                chatCount = runCatching { services.chats.listSessions().size }.getOrNull()
            }
            kotlinx.coroutines.delay(5000)
        }
    }
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
    // Decimal GB, like the catalog's model sizes and the drawer.
    fun gb(b: Long) = String.format(java.util.Locale.US, "%.1f GB", b / 1e9)
    run {
        // For anyone first: memory and storage as bars, the phone and the chats. Then the technical rows (Oct 18).
        // Boar Labs can set another limit, or none: then the bar runs to what the phone still has free.
        val limit = team.sopa.boar.engine.models.StorageBudget.limit(services.catalog.storageBudgetBytes)
        val noLimit = limit == team.sopa.boar.engine.models.StorageBudget.NO_LIMIT
        val budget = if (noLimit) storage + fetching + ctx.filesDir.usableSpace else limit
        val others = maxOf(phone.usedBytes - app, 0L)
        team.sopa.boar.ui.drawer.UsageBar(t("native.telemetry.memory"), listOf(minOf(app, phone.usedBytes) to c.emerald400, others to c.textDim),
            phone.totalBytes, team.sopa.boar.ui.drawer.usage(phone.usedBytes, phone.totalBytes), large = true)
        team.sopa.boar.ui.drawer.UsageCaption(t("native.telemetry.memoryCaption", "app" to gb(app), "others" to gb(others)), large = true)
        team.sopa.boar.ui.drawer.UsageBar(t("native.telemetry.disk"), listOf(storage to c.cyan400, fetching to c.cyan400.copy(alpha = 0.35f)),
            budget, team.sopa.boar.ui.drawer.usage(storage + fetching, budget), large = true)
        team.sopa.boar.ui.drawer.UsageCaption(
            when {
                noLimit -> t("native.drawerStats.diskNoLimit")
                fetching > 0 -> t("native.drawerStats.diskDownloading", "size" to gb(fetching))
                else -> t("native.drawerStats.diskLimit")
            }, large = true,
        )
        @Composable fun rows(list: List<Pair<String, String>>) = list.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(k, style = Boar.type.subtext, color = c.textMuted, modifier = Modifier.weight(0.45f))
                Text(v, style = Boar.type["mono.xs"], color = c.textPrimary, modifier = Modifier.weight(0.55f))
            }
        }
        rows(listOf(
            t("native.telemetry.device") to listOfNotNull(d.brand, d.model).joinToString(" ") + " · Android ${d.osVersion}",
            t("native.telemetry.chats") to (chatCount?.toString() ?: "—"),
        ))
        Text(t("native.telemetry.technical"), style = Boar.type["mono.xs"], color = c.textDim, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        val inf = d.cpuFeatures.orEmpty()
        rows(listOf(
            t("native.telemetry.chip") to (team.sopa.boar.engine.eval.ShareResults.describeChipset(d) ?: "—"),
            t("native.telemetry.cores") to (team.sopa.boar.engine.eval.ShareResults.describeCores(d.coreMaxFreqKHz) ?: "${d.cpuCores ?: "—"}"),
            t("native.telemetry.features") to listOf("dotprod" to "asimddp", "i8mm" to "i8mm", "SVE" to "sve", "SME" to "sme").joinToString(" · ") { (n, f) -> (if (f in inf) "✓ " else "✗ ") + n },
            t("native.telemetry.build") to (team.sopa.boar.engine.BoarEngine.loadedVariant ?: "—"),
            t("native.telemetry.evals") to "${evals.size}",
            t("native.telemetry.lastEval") to last,
        ))
        // The sharing half only in builds that can share (EVAL_SHARING); otherwise nothing is ever sent.
        Text(t(if (sharing.available) "native.telemetry.noteSharing" else "native.telemetry.note"), style = Boar.type.micro, color = c.textDim)
    }
}

/** The Debug log switch (builds with ALLOW_DEBUG_LOG only); while it's on, the log's size, Save-as and Clear. */
@Composable
internal fun DebugLogRows() {
    val log = team.sopa.boar.app.DebugLog
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var on by remember { mutableStateOf(log.enabled) }
    var size by remember { mutableStateOf(log.sizeBytes()) }
    var status by remember { mutableStateOf<String?>(null) }
    val saveAs = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { log.copyTo(it) } }.isSuccess
            status = team.sopa.boar.ui.I18n.t(ctx, if (ok) "native.debugLog.saved" else "native.debugLog.saveFailed")
        }
    }
    SwitchRow(t("native.debugLog.title"), t("native.debugLog.note"), on) { log.setEnabled(it); on = it; status = null }
    if (on || size > 0) {
        androidx.compose.runtime.LaunchedEffect(on) { while (true) { size = log.sizeBytes(); kotlinx.coroutines.delay(5_000) } }
        Note(t("native.debugLog.size", "size" to String.format(java.util.Locale.US, "%.1f MB", size / 1048576.0)))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinkButton(t("native.debugLog.save")) {
                saveAs.launch("boar-debug-" + java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".log")
            }
            LinkButton(t("native.debugLog.clear"), danger = true) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) { log.clear(); size = log.sizeBytes(); status = null }
            }
        }
        status?.let { Note(it) }
    }
}
