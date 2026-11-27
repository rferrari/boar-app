package team.sopa.boar.ui.eval

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import androidx.compose.foundation.lazy.itemsIndexed
import team.sopa.boar.engine.eval.BenchLevel
import team.sopa.boar.engine.eval.BenchPresets
import team.sopa.boar.engine.eval.RunMeta
import team.sopa.boar.engine.eval.Scorecard
import team.sopa.boar.eval.EvaluationState
import team.sopa.boar.eval.EvaluationViewModel
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.util.Locale
import team.sopa.boar.ui.theme.wash

private fun formatMs(ms: Double?): String = when {
    ms == null -> "—"
    ms < 1000 -> String.format(Locale.US, "%.0fms", ms)
    else -> String.format(Locale.US, "%.1fs", ms / 1000)
}

private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull == true
private fun JsonObject.list(k: String) = (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

/** Runs the fixed evaluation set against the chosen models (EvaluationScreen.tsx). Results save as they come. */
@Composable
fun EvaluationScreen(state: EvaluationState, vm: EvaluationViewModel, chatBusy: Boolean, deviceSetup: (@Composable () -> Unit)? = null, onClose: () -> Unit) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf<String?>(null) }
    // The model list closes when a run starts and the answers start closed, so progress and score fit the first screen.
    var modelsOpen by remember { mutableStateOf(true) }
    var answersOpen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.running) { if (state.running) modelsOpen = false }
    val byHand = state.level == null
    val chosen = if (byHand) state.configs.orEmpty().count { it.id in state.selected } else state.plan?.models?.size ?: 0
    val canRun = !state.running && !chatBusy && chosen > 0 && state.configs != null && (byHand || state.plan != null)
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var savedOpen by remember { mutableStateOf(true) }
    // Device setup (2.1.0, BOAR-81): the builders' rows from Settings, folded until opened.
    var setupOpen by remember { mutableStateOf(false) }
    val export: (String) -> Unit = { format -> scope.launch { vm.exportIntent(format)?.let(ctx::startActivity) } }

    Column(Modifier.fillMaxSize().background(c.bgSurface).statusBarsPadding().navigationBarsPadding()) {
        // The app's page header; Back waits while a run is going (as Done did).
        team.sopa.boar.ui.PageHeader(t("evaluation.title"), { if (state.opened != null) vm.closeOpened() else if (!state.running) onClose() })
        // Runs started over adb since the page was last open show up too.
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshSaved() }
        // The set and app version under the header, not in it (header subtitles only where a first-timer needs one).
        Text(t("evaluation.subtitle", "version" to state.setVersion, "count" to state.queries.size) + " · BOAR " + vm.sharing.appVersion(),
            style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 12.dp, 12.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val configs = state.configs
            // A saved run opened from the list: its rows and scorecard below, as right after it ran.
            state.opened?.let { run ->
                item { OpenedBar(run, onClose = { vm.closeOpened() }, onDelete = { confirmDelete = run.runId }) }
            }
            // Device setup (BOAR-81): not while a run is going, whose model an engine change would unload (CodeRabbit).
            if (deviceSetup != null && !state.running) {
                item { SectionToggle(t("native.eval.deviceSetup"), setupOpen) { setupOpen = !setupOpen } }
                if (setupOpen) item { deviceSetup() }
            }
            if (state.opened == null) item { PresetSection(state, vm) }
            if (state.opened == null && byHand) item {
                SectionToggle(t("native.eval.modelsTitle", "chosen" to chosen, "count" to configs.orEmpty().size), modelsOpen) { modelsOpen = !modelsOpen }
            }
            // The ticked models only by hand; a preset says its own models in its section.
            val showModels = state.opened == null && byHand
            if (!showModels) { /* nothing */ }
            else if (configs == null) item { CircularProgressIndicator(color = c.textAccentEmerald, modifier = Modifier.size(24.dp)) }
            else if (modelsOpen) {
                if (configs.isEmpty()) item { Note(t("evaluation.noModels")) }
                items(configs, key = { it.id }) { cfg ->
                    val on = cfg.id in state.selected
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.bgCardElevated).border(1.dp, c.borderDefault, RoundedCornerShape(6.dp))
                        .clickable { vm.toggle(cfg.id) }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        team.sopa.boar.ui.PackIcon(if (on) team.sopa.boar.R.drawable.ic_checkbox_on else team.sopa.boar.R.drawable.ic_checkbox_off, 18.dp, if (on) c.textAccentEmerald else c.textDim)
                        Text(cfg.label, style = Boar.type.subtext, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.opened == null) {
                        Note(t("evaluation.summary", "queries" to state.queries.size, "configs" to chosen, "total" to (state.plan?.takeIf { !byHand }?.answers ?: (state.queries.size * chosen))))
                        Note(if (chatBusy) t("evaluation.chatBusy") else t("native.eval.keepScreenOn"))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.running) {
                            Action(if (state.stopping) t("evaluation.stopping") else t("evaluation.stop"), enabled = !state.stopping, danger = true) { vm.stop() }
                        } else if (state.opened == null) {
                            Action(t("evaluation.run"), enabled = canRun) { vm.run() }
                        }
                        if (state.runId != null && !state.running) {
                            Action(t("evaluation.exportJsonl")) { export("jsonl") }
                            Action(t("evaluation.exportCsv")) { export("csv") }
                            if (vm.sharing.available) Action(if (state.sharedRunId == state.runId) t("evaluation.shared") else t("evaluation.share"),
                                enabled = state.sharedRunId != state.runId) { vm.previewShare() }
                        }
                    }
                }
            }
            // Under the buttons, so Run and Stop stay put: progress and each model's score, live while it runs.
            if (state.running || state.rows.isNotEmpty()) item { ScoreSection(state, total = state.total) }
            state.scorecard?.takeIf { !state.running }?.let { card -> item { ScorecardSection(card) } }
            state.error?.let { item { Text(t("evaluation.runFailedTitle") + ": " + it, style = Boar.type["mono.xs"], color = c.crimson400) } }
            state.savedPath?.let { path ->
                item { Note((if (state.stopped) t("evaluation.stoppedEarly") + " " else "") + t("evaluation.savedTo", "path" to path)) }
            }
            if (state.rows.isNotEmpty()) item {
                SectionToggle(t("native.eval.answersTitle", "count" to state.rows.size), answersOpen) { answersOpen = !answersOpen }
            }
            // Keyed by place: a preset answers the same question once per pass (Succinct, Detailed, Deep Research).
            if (answersOpen) itemsIndexed(state.rows, key = { i, it -> "$i/${it.str("configId")}/${it.str("queryId")}" }) { i, r ->
                val key = "$i/${r.str("configId")}/${r.str("queryId")}"
                val open = expanded == key
                val outcome = r.str("outcome")
                val outcomeColor = when (outcome) { "failure" -> c.crimson400; "cancelled" -> c.amber400; else -> c.textAccentEmerald }
                val titles = r.list("retrievedTitles")
                val verdict = state.scorecard?.rows?.getOrNull(i)?.takeIf { it.queryId == r.str("queryId") }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.bgCardElevated).border(1.dp, c.borderDefault, RoundedCornerShape(10.dp))
                    .clickable { expanded = if (open) null else key }.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${r.str("queryId")} · ${r.str("configLabel")}" + (r.str("style")?.let { " · $it" } ?: "") + (if (r.str("path") == "deep") " · deep" else ""),
                            style = Boar.type.subtext, color = c.textHeading, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        verdict?.let { v ->
                            Text(t("native.eval.verdict.${v.verdict.name}").uppercase(), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), fontWeight = FontWeight.SemiBold,
                                color = when (v.verdict) { Scorecard.Verdict.PASS -> c.textAccentEmerald; Scorecard.Verdict.FAIL -> c.crimson400; Scorecard.Verdict.KNOWN -> c.textAccentAmber; else -> c.textMuted })
                        }
                        Text((outcome ?: "—").uppercase(), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = outcomeColor, fontWeight = FontWeight.SemiBold)
                    }
                    // What failed, and what right looks like (the topic's note), when open.
                    if (open) verdict?.let { v ->
                        v.checks.filter { it.ok == false }.forEach { ch -> Text("✗ ${ch.rule}: ${ch.detail}", style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.crimson400) }
                        if (v.toRead.isNotEmpty()) Text(v.toRead, style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.textMuted)
                    }
                    val meta = Boar.type["mono.xs"].copy(fontSize = 9.sp)
                    r.str("query")?.let { q -> Text("Q: $q", style = Boar.type.subtext, color = c.textMuted, maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis) }
                    Text("${r.str("modelId") ?: "—"} · ${r.str("taskType") ?: "—"} · ${r.str("modelResidency") ?: "—"} · " +
                        (if (r.bool("retrievalUsed")) titles.take(2).joinToString(", ") else t("evaluation.noRetrieval")), style = meta, color = c.textDim)
                    Text((r.num("tokPerSec")?.let { String.format(Locale.US, "%.1f t/s", it) } ?: "—") +
                        " · load ${formatMs(r.num("modelLoadMs"))} · TTFT ${formatMs(r.num("ttftMs"))} · total ${formatMs(r.num("totalLatencyMs"))}", style = meta, color = c.textDim)
                    Text(if (outcome == "failure" && r.str("errorMessage") != null) r.str("errorMessage")!! else "A: " + r.str("answer").orEmpty(),
                        style = Boar.type.subtext, color = c.textPrimary, maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                }
            }
            // Every run kept on the phone, newest first: from this page and from adb, old ones too (adam, Nov 14).
            item { SectionToggle(t("native.eval.saved.title", "count" to state.saved.size), savedOpen) { savedOpen = !savedOpen } }
            if (savedOpen && state.saved.isEmpty()) item { Note(t("native.eval.saved.empty")) }
            if (savedOpen) items(state.saved, key = { "saved/" + it.runId }) { run ->
                SavedRunRow(run, current = run.runId == state.opened?.runId, enabled = !state.running) { vm.open(run.runId) }
            }
        }
    }
    ShareDialog(state, vm)
    confirmDelete?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = c.bgCardElevated,
            title = { Text(t("native.eval.saved.deleteTitle"), style = Boar.type.title, color = c.textHeading) },
            text = { Text(t("native.eval.saved.deleteBody"), style = Boar.type.body, color = c.textPrimary) },
            confirmButton = { androidx.compose.material3.TextButton({ vm.delete(id); confirmDelete = null }) { Text(t("native.eval.saved.delete"), color = c.crimson400) } },
            dismissButton = { androidx.compose.material3.TextButton({ confirmDelete = null }) { Text(t("common.cancel"), color = c.textSecondary) } },
        )
    }
}

private fun runDate(ms: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(ms))

/** A saved run in the list: date and build, models, level and topic, how it ended. */
@Composable
private fun SavedRunRow(run: RunMeta, current: Boolean, enabled: Boolean, onOpen: () -> Unit) {
    val c = Boar.colors
    val meta = Boar.type["mono.xs"].copy(fontSize = 10.sp)
    val stateColor = when (run.state) {
        RunMeta.STATE_DONE -> c.textAccentEmerald
        RunMeta.STATE_FAILED, RunMeta.STATE_INTERRUPTED -> c.crimson400
        else -> c.amber400
    }
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).clip(RoundedCornerShape(8.dp)).background(c.bgCardElevated)
        .border(1.dp, if (current) c.textAccentEmerald else c.borderDefault, RoundedCornerShape(8.dp))
        .clickable(enabled = enabled, onClick = onOpen).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(runDate(run.createdAt), style = Boar.type.subtext, color = c.textHeading, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(t("native.eval.saved.state.${run.state}").uppercase(), style = meta.copy(fontSize = 9.sp), color = stateColor, fontWeight = FontWeight.SemiBold)
        }
        val preset = run.level?.let { l -> t("native.eval.level.$l") + (run.topics.filter { it != BenchPresets.CORE }.takeIf { it.isNotEmpty() }?.joinToString("+", " · ") ?: "") }
            ?: t("native.eval.saved.byHand")
        Text(listOfNotNull(preset, t("native.eval.saved.answers", "count" to run.rows), run.appVersion?.let { "BOAR $it" },
            if (run.source == "adb") t("native.eval.saved.adb") else null).joinToString(" · "), style = meta, color = c.textMuted)
        if (run.models.isNotEmpty()) Text(run.models.joinToString(", "), style = meta, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** On top of an opened saved run: which one it is, Close and Delete. */
@Composable
private fun OpenedBar(run: RunMeta, onClose: () -> Unit, onDelete: () -> Unit) {
    val c = Boar.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("native.eval.saved.opened", "date" to runDate(run.createdAt)), style = Boar.type.body, color = c.textHeading, fontWeight = FontWeight.Bold)
        Text(run.runId, style = Boar.type["mono.xs"].copy(fontSize = 10.sp), color = c.textDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(t("native.eval.saved.close"), onClick = onClose)
            Action(t("native.eval.saved.delete"), danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Boar.colors
    Text(text, style = Boar.type.caption, color = if (on) c.textInverse else c.textPrimary,
        modifier = Modifier.alpha(if (enabled || on) 1f else 0.5f).background(if (on) c.emerald600 else c.bgCard, RoundedCornerShape(16.dp))
            .border(1.dp, if (on) c.emerald600 else c.borderDefault, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

private fun duration(seconds: Double): String {
    val s = seconds.toLong()
    return when {
        s < 60 -> "$s s"
        s < 3600 -> "${(s + 30) / 60} min"
        else -> "${s / 3600} h ${(s % 3600 + 30) / 60} min"
    }
}

/**
 * Levels and topics (adam, Nov 14: "fast, only succinct mode. medium, complete. and by topics"), what the chosen preset
 * runs and how long it should take before it starts ("this waiting sucks").
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PresetSection(state: EvaluationState, vm: EvaluationViewModel) {
    val c = Boar.colors
    val pt = LocalContext.current.resources.configuration.locales[0]?.language == "pt"
    val enabled = !state.running
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("native.eval.preset.title"), style = Boar.type.subtext, color = c.textHeading, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(t("native.eval.preset.byHand"), state.level == null, enabled) { vm.setLevel(null) }
            BenchLevel.entries.forEach { l -> Chip(t("native.eval.level.${l.id}"), state.level == l, enabled) { vm.setLevel(l) } }
        }
        Note(t(state.level?.let { "native.eval.level.${it.id}.note" } ?: "native.eval.level.byHand.note"))
        if (state.topics.isNotEmpty()) {
            Text(t("native.eval.topic.title"), style = Boar.type.subtext, color = c.textHeading, fontWeight = FontWeight.Bold)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.topics.forEach { tp -> Chip((if (pt) tp.titlePt else null) ?: tp.title, state.topic == tp.id, enabled) { vm.setTopic(tp.id) } }
            }
            state.topics.firstOrNull { it.id == state.topic }?.let { tp ->
                if (tp.about.isNotEmpty()) Note(tp.about)
                if (tp.status.startsWith("draft")) Note(t("native.eval.topic.draft"))
            }
        }
        state.plan?.takeIf { state.level != null }?.let { plan ->
            Note(t("native.eval.planModels", "models" to plan.models.joinToString(", ") { it.label }))
            state.estimate?.let { e ->
                val line = t("native.eval.expected", "time" to duration(e.seconds), "answers" to plan.answers) +
                    (if (e.untimed > 0) ", " + t("native.eval.expected.untimed", "count" to e.untimed) else "") + " (" +
                    (if (e.fromPrevious) t("native.eval.expected.fromPrevious", "level" to t("native.eval.level.${plan.preset.level.id}")) else t("native.eval.expected.fromSeed")) + ")"
                Text(line, style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** One line per topic, passed over checked, against the previous run of the same preset; one line per model and pass. */
@Composable
private fun ScorecardSection(card: Scorecard.Card) {
    val c = Boar.colors
    val meta = Boar.type["mono.xs"].copy(fontSize = 10.sp)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(8.dp)).background(c.bgCardElevated).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("native.eval.scorecard.title"), style = Boar.type.body, color = c.textHeading, fontWeight = FontWeight.Bold)
        card.topics.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.topic, style = Boar.type.subtext, color = c.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(t("native.eval.scorecard.passed", "passed" to line.passed, "checked" to line.checked), style = meta, color = c.textPrimary)
                line.trend?.let { tr ->
                    val (arrow, color) = when (tr) { Scorecard.Trend.UP -> "↑" to c.textAccentEmerald; Scorecard.Trend.DOWN -> "↓" to c.crimson400; else -> "=" to c.textMuted }
                    Text("$arrow " + t("native.eval.scorecard.${tr.name.lowercase()}"), style = meta, color = color, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(listOfNotNull(
                line.previousChecked?.let { t("native.eval.scorecard.was", "passed" to line.previousPassed, "checked" to it) },
                t("native.eval.scorecard.toRead", "count" to line.toRead),
            ).joinToString(" · "), style = meta, color = c.textDim)
        }
        card.models.forEach { m ->
            Text("${m.modelId} · ${m.pass}", style = Boar.type.subtext, color = c.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t("native.eval.scorecard.model", "first" to formatMs(m.firstWordMs), "whole" to formatMs(m.wholeAnswerMs),
                "peak" to (m.peakRssBytes?.let { String.format(Locale.US, "%.2f GB", it / 1e9) } ?: "—")), style = meta, color = c.textDim)
        }
        Note(t("native.eval.scorecard.note"))
    }
}

/** SharePreview.tsx + ShareProgress.tsx: what will be sent, then each step and the outcome. */
@Composable
private fun ShareDialog(state: EvaluationState, vm: EvaluationViewModel) {
    val d = state.sharePreview ?: return
    val c = Boar.colors
    val outcome = state.shareOutcome
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!state.shareSending) vm.closeShare() },
        containerColor = c.bgCardElevated,
        title = {
            Text(when {
                outcome != null -> t("evaluation.shareProgress.title.${outcome.result.key}")
                state.shareSending -> t("evaluation.shareProgress.sendingTitle")
                else -> t("evaluation.shareTitle")
            }, style = Boar.type.title, color = c.textHeading)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!state.shareSending && outcome == null) {
                    val ram = d.ramBytes?.let { String.format(Locale.US, "%.1f GB RAM", it / 1073741824.0) }
                    listOfNotNull(
                        listOfNotNull(d.brand, d.model).joinToString(" ").ifEmpty { null },
                        "Android ${d.osVersion} (API ${d.apiLevel})",
                        team.sopa.boar.engine.eval.ShareResults.describeChipset(d),
                        team.sopa.boar.engine.eval.ShareResults.describeCores(d.coreMaxFreqKHz),
                        ram,
                        "BOAR ${vm.sharing.appVersion()}",
                        t("evaluation.shareProgress.log.prepare", "count" to state.rows.size),
                    ).forEach { Text("• $it", style = Boar.type.subtext, color = c.textPrimary) }
                }
                state.shareLog.forEach { s ->
                    val line = when {
                        s.challenge != null -> t("evaluation.shareProgress.log.challengeGot", "code" to s.challenge)
                        s.key != null -> t("evaluation.shareProgress.log.key-${s.key}")
                        s.step == team.sopa.boar.share.ResultsSharing.Step.SEND -> t("evaluation.shareProgress.log.send",
                            "size" to "${maxOf(1, (s.bytes ?: 0) / 1024)} KB", "count" to (s.rows ?: 0))
                        else -> t("evaluation.shareProgress.log.${s.step.name.lowercase()}", "count" to (s.rows ?: 0))
                    }
                    Text("› $line", style = Boar.type["mono.xs"], color = c.textSecondary)
                }
                if (outcome != null) {
                    outcome.status?.let { Text("› " + t("evaluation.shareProgress.log.status", "status" to it), style = Boar.type["mono.xs"], color = c.textSecondary) }
                    Text(t("evaluation.shareProgress.why.${outcome.result.key}"), style = Boar.type.body, color = c.textPrimary)
                    outcome.detail?.let { Text(t("evaluation.shareProgress.detail", "detail" to it), style = Boar.type["mono.xs"], color = c.textDim) }
                }
            }
        },
        confirmButton = {
            when {
                outcome != null && outcome.result.canRetry -> androidx.compose.material3.TextButton({ vm.confirmShare() }) { Text(t("evaluation.shareProgress.retry"), color = c.textAccentCyan) }
                outcome != null -> androidx.compose.material3.TextButton({ vm.closeShare() }) { Text(t("common.done"), color = c.textAccentCyan) }
                !state.shareSending -> androidx.compose.material3.TextButton({ vm.confirmShare() }) { Text(t("evaluation.shareConfirm"), color = c.textAccentCyan) }
                else -> CircularProgressIndicator(color = c.textAccentEmerald, modifier = Modifier.size(20.dp))
            }
        },
        dismissButton = {
            if (!state.shareSending && (outcome == null || outcome.result.canRetry)) androidx.compose.material3.TextButton({ vm.closeShare() }) { Text(t("common.cancel"), color = c.textSecondary) }
        },
    )
}

/** A section heading that opens and closes what follows (▸ closed, ▾ open). */
@Composable
private fun SectionToggle(title: String, open: Boolean, onToggle: () -> Unit) {
    val c = Boar.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onToggle).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Boar.type.subtext, color = c.textHeading, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        team.sopa.boar.ui.PackIcon(if (open) team.sopa.boar.R.drawable.ic_chevron_down else team.sopa.boar.R.drawable.ic_chevron_right, 16.dp, c.textMuted)
    }
}

/**
 * The run's progress and each model's score v1 (EvalScore: speed 60 %, reliability 25 %, sources found 15 %) from the
 * answers so far: a ring with the score and a bar per part. Speed and reliability on this phone, not correctness.
 */
@Composable
private fun ScoreSection(state: EvaluationState, total: Int) {
    val c = Boar.colors
    val scores = remember(state.rows) { team.sopa.boar.engine.eval.EvalScore.scoreRunJson(state.rows) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(8.dp)).background(c.bgCardElevated).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t("native.eval.score.title"), style = Boar.type.body, color = c.textHeading, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val done = state.rows.size
            Text(t(if (state.running) "native.eval.score.running" else "native.eval.score.finished", "done" to done, "total" to total),
                style = Boar.type["mono.xs"], color = if (state.running) c.textAccentCyan else c.textMuted, fontWeight = FontWeight.SemiBold)
        }
        if (state.running) state.progress?.let { p ->
            Text("${p.config.label} — ${p.query.query}", style = Boar.type.subtext, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        scores.forEach { ScoreRow(it) }
        if (scores.isNotEmpty()) Note(t("native.eval.score.note"))
    }
}

@Composable
private fun ScoreRow(s: team.sopa.boar.engine.eval.EvalScore.ModelScore) {
    val c = Boar.colors
    // Real traffic-light colors for the ring only: the theme's "emerald" is gold in some themes, so a 96 looked like a warning.
    val tone = when { s.score >= 70 -> Color(0xFF22C55E); s.score >= 40 -> Color(0xFFF59E0B); else -> Color(0xFFEF4444) }
    val track = c.borderDefault
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val inset = 3.dp.toPx()
                val arc = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arc, style = stroke)
                if (s.score > 0) drawArc(tone, -90f, 360f * s.score / 100f, false, topLeft, arc, style = stroke)
            }
            Text("${s.score}", color = c.textHeading, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.modelLabel ?: s.configId, style = Boar.type.subtext, color = c.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PartBar(t("native.eval.score.speed"), s.speed, s.medianTokPerSec?.let { String.format(Locale.US, "%.1f tok/s", it) } ?: "—", c.cyan400)
            PartBar(t("native.eval.score.reliability"), s.reliability, t("native.eval.score.answered", "done" to s.completed, "total" to s.answers), c.emerald400)
            PartBar(t("native.eval.score.sources"), s.retrieval ?: 0.0,
                if (s.retrieval == null) t("native.eval.score.noneExpected") else t("native.eval.score.found", "hits" to s.retrievalHits, "total" to s.retrievalQuestions), c.amber400)
        }
    }
}

@Composable
private fun PartBar(label: String, fraction: Double, value: String, color: Color) {
    val c = Boar.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = Boar.type["mono.xs"].copy(fontSize = 10.sp), color = c.textMuted, modifier = Modifier.weight(1f))
            Text(value, style = Boar.type["mono.xs"].copy(fontSize = 10.sp), color = c.textPrimary)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.borderDefault)) {
            Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

@Composable
private fun Note(text: String) = Text(text, style = Boar.type["mono.xs"].copy(fontSize = 10.sp), color = Boar.colors.textDim)

@Composable
private fun RowScope.Action(label: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val c = Boar.colors
    Box(Modifier.weight(1f).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(6.dp))
        .background(if (danger) c.crimsonBgSubtle else c.bgCardElevated).border(1.dp, if (danger) c.crimsonBorder else c.borderDefault, RoundedCornerShape(6.dp))
        .clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, style = Boar.type["mono.xs"], color = if (danger) c.crimson400 else c.textAccentCyan, fontWeight = FontWeight.SemiBold)
    }
}
