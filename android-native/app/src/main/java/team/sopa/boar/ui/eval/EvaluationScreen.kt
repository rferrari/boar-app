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
import team.sopa.boar.eval.EvaluationState
import team.sopa.boar.eval.EvaluationViewModel
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.util.Locale

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
fun EvaluationScreen(state: EvaluationState, vm: EvaluationViewModel, chatBusy: Boolean, onClose: () -> Unit) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf<String?>(null) }
    val chosen = state.configs.orEmpty().count { it.id in state.selected }
    val canRun = !state.running && !chatBusy && chosen > 0 && state.configs != null
    val export: (String) -> Unit = { format -> scope.launch { vm.exportIntent(format)?.let(ctx::startActivity) } }

    Column(Modifier.fillMaxSize().background(c.bgSurface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(c.bgCardElevated).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("evaluation.title"), style = Boar.type.titleSm.copy(letterSpacing = 0.5.sp), color = c.textHeading)
                Text(t("evaluation.subtitle", "version" to state.setVersion, "count" to state.queries.size) + " · BOAR " + vm.sharing.appVersion(), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = c.textDim)
            }
            if (!state.running) {
                Text(t("common.done"), style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 12.dp, 12.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(t("evaluation.configsTitle"), style = Boar.type.subtext, color = c.textHeading, fontWeight = FontWeight.Bold) }
            val configs = state.configs
            if (configs == null) item { CircularProgressIndicator(color = c.textAccentEmerald, modifier = Modifier.size(24.dp)) }
            else {
                if (configs.isEmpty()) item { Note(t("evaluation.noModels")) }
                items(configs, key = { it.id }) { cfg ->
                    val on = cfg.id in state.selected
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.bgCardElevated).border(1.dp, c.borderDefault, RoundedCornerShape(6.dp))
                        .clickable { vm.toggle(cfg.id) }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (on) "☑" else "☐", fontSize = 16.sp, color = if (on) c.textAccentEmerald else c.textDim)
                        Text(cfg.label, style = Boar.type.subtext, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
                item { Note(t("native.eval.adaptiveLater")) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Note(t("evaluation.summary", "queries" to state.queries.size, "configs" to chosen, "total" to state.queries.size * chosen))
                    Note(if (chatBusy) t("evaluation.chatBusy") else t("native.eval.keepScreenOn"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.running) {
                            Action(if (state.stopping) t("evaluation.stopping") else t("evaluation.stop"), enabled = !state.stopping, danger = true) { vm.stop() }
                        } else {
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
            state.progress?.let { p ->
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(6.dp)).background(c.bgCardElevated).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t("evaluation.progress", "config" to p.configIndex + 1, "configs" to p.configCount, "query" to p.queryIndex + 1, "queries" to p.queryCount),
                            style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.SemiBold)
                        Text("${p.config.label} — ${p.query.query}", style = Boar.type.subtext, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            state.error?.let { item { Text(t("evaluation.runFailedTitle") + ": " + it, style = Boar.type["mono.xs"], color = c.crimson400) } }
            state.savedPath?.let { path ->
                item { Note((if (state.stopped) t("evaluation.stoppedEarly") + " " else "") + t("evaluation.savedTo", "path" to path)) }
            }
            items(state.rows, key = { "${it.str("configId")}/${it.str("queryId")}" }) { r ->
                val key = "${r.str("configId")}/${r.str("queryId")}"
                val open = expanded == key
                val outcome = r.str("outcome")
                val outcomeColor = when (outcome) { "failure" -> c.crimson400; "cancelled" -> c.amber400; else -> c.textAccentEmerald }
                val titles = r.list("retrievedTitles")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.bgCardElevated).border(1.dp, c.borderDefault, RoundedCornerShape(10.dp))
                    .clickable { expanded = if (open) null else key }.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${r.str("queryId")} · ${r.str("configLabel")}", style = Boar.type.subtext, color = c.textHeading, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text((outcome ?: "—").uppercase(), style = Boar.type["mono.xs"].copy(fontSize = 9.sp), color = outcomeColor, fontWeight = FontWeight.SemiBold)
                    }
                    val meta = Boar.type["mono.xs"].copy(fontSize = 9.sp)
                    Text("${r.str("modelId") ?: "—"} · ${r.str("taskType") ?: "—"} · ${r.str("modelResidency") ?: "—"} · " +
                        (if (r.bool("retrievalUsed")) titles.take(2).joinToString(", ") else t("evaluation.noRetrieval")), style = meta, color = c.textDim)
                    Text((r.num("tokPerSec")?.let { String.format(Locale.US, "%.1f t/s", it) } ?: "—") +
                        " · load ${formatMs(r.num("modelLoadMs"))} · TTFT ${formatMs(r.num("ttftMs"))} · total ${formatMs(r.num("totalLatencyMs"))}", style = meta, color = c.textDim)
                    Text(if (outcome == "failure" && r.str("errorMessage") != null) r.str("errorMessage")!! else r.str("answer").orEmpty(),
                        style = Boar.type.subtext, color = c.textPrimary, maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    ShareDialog(state, vm)
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
