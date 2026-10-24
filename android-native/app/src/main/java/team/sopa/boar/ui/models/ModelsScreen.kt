package team.sopa.boar.ui.models

import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sopa.boar.engine.models.AssetKind
import team.sopa.boar.engine.models.CatalogAsset
import team.sopa.boar.engine.models.Compatibility
import team.sopa.boar.engine.models.ModelOrder
import team.sopa.boar.models.ModelsState
import team.sopa.boar.models.ModelsViewModel
import team.sopa.boar.models.Row as AssetRow
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import team.sopa.boar.ui.theme.BrandType
import kotlin.math.ceil
import kotlin.math.floor
import team.sopa.boar.ui.theme.wash
import team.sopa.boar.ui.theme.track

/** The models and knowledge catalog (ModelCatalogScreen.tsx with CatalogItemCard.tsx's layout and styles). */
@Composable
fun ModelsScreen(state: ModelsState, vm: ModelsViewModel, onBrowse: () -> Unit = {}, onBack: () -> Unit) {
    val c = Boar.colors
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(c.bgTerminal).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(c.bgSurface).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("‹ " + t("common.back"), style = Boar.type.body, color = c.textAccentEmerald, modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            Column {
                Text(t("modelCatalogScreen.title"), style = Boar.type.titleLg, color = c.textHeading)
                Text(t("modelCatalogScreen.subtitle"), style = Boar.type.caption, color = c.textMuted)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        // The user's sort and filter (ModelOrder): Smart puts the active model first, then downloads, then the
        // phone's models by last use. Knowledge packs are on the Knowledge screen, as in the Expo app (KnowledgePacks
        // below); the embedding model is BOAR's own search engine, not a choice, so it's in Settings → Models.
        val all = state.items.filter { it.kind != AssetKind.CORPUS && it.kind != AssetKind.EMBEDDING }
        val facts = ModelOrder.Facts(
            active = state.activeLlm, onPhone = state.rows.filterValues { it.present }.keys,
            downloading = state.rows.filterValues { it.downloading }.keys, mine = state.mine, lastUsed = state.lastUsed, added = state.added,
        )
        val sorted = ModelOrder.apply(all, state.sort, state.filter, facts)
        // The order holds while the catalog is open: a download that starts, fails or ends doesn't move its card away
        // from the user's finger (Oct 20). It's sorted again when the sort, the filter or the set of models changes.
        val order = remember(state.sort, state.filter, all.size) { sorted.map { it.id } }
        val models = sorted.sortedBy { a -> order.indexOf(a.id).let { if (it < 0) Int.MAX_VALUE else it } }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // More models first: today Hugging Face, later other providers.
            item { HfCta(onBrowse) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChipRow(t("native.models.sort"), listOf(
                        ModelOrder.Sort.SMART to t("native.models.sortSmart"), ModelOrder.Sort.LAST_USED to t("native.models.sortLastUsed"),
                        ModelOrder.Sort.ADDED to t("native.models.sortAdded"),
                    ), state.sort, vm::setSort)
                    ChipRow(t("native.models.show"), listOf(
                        ModelOrder.Filter.ALL to t("native.models.filterAll"), ModelOrder.Filter.ON_PHONE to t("native.models.filterOnPhone"),
                        ModelOrder.Filter.DOWNLOADING to t("native.models.filterDownloading"), ModelOrder.Filter.MINE to t("native.models.filterMine"),
                    ), state.filter, vm::setFilter)
                }
            }
            if (models.isEmpty()) item { Text(t("native.models.emptyFilter"), style = Boar.type.body, color = c.textMuted, modifier = Modifier.padding(vertical = 8.dp)) }
            items(models, key = { it.id }) { a ->
                val active = if (a.kind == AssetKind.LLM) a.id == state.activeLlm else state.rows[a.id]?.present == true
                CatalogCard(a, state.rows[a.id], active, state.totalRamBytes, state.activating == a.id, state.activating != null, vm)
            }
        }
    }
    state.toast?.let { androidx.compose.runtime.LaunchedEffect(it) { team.sopa.boar.ui.Toasts.show(it); vm.toastShown() } }
    }
}

/** The knowledge packs, "BOAR Knowledge" (KnowledgeScreen.tsx "Topic packs"): download, verify and remove, on the Knowledge screen. */
@Composable
fun KnowledgePacks(state: ModelsState, vm: ModelsViewModel) {
    val c = Boar.colors
    val packs = state.items.filter { it.kind == AssetKind.CORPUS }
    // Folded by default: the packs rarely change, and Personal Files above is what people come here for (Oct 21).
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (packs.isEmpty()) return
    team.sopa.boar.ui.BoarSection(t("flows.knowledge.topicPacksTitle"), open, { open = !open }) {
        Text(t("flows.knowledge.topicPacksFooter"), style = Boar.type.subtext, color = c.textMuted)
        for (a in packs) CatalogCard(a, state.rows[a.id], state.rows[a.id]?.present == true, state.totalRamBytes, state.activating == a.id, state.activating != null, vm)
    }
}

internal fun formatMB(bytes: Long): String = when {
    bytes <= 0 -> "0 MB"
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    else -> "%.0f MB".format(bytes / (1024.0 * 1024))
}

private fun formatSpeed(b: Double?, calculating: String) = when {
    b == null || b <= 0 -> calculating
    b >= 1024 * 1024 -> "%.1f MB/s".format(b / (1024 * 1024))
    else -> "%.0f KB/s".format(b / 1024)
}

private fun formatEta(s: Double?, calculating: String) = when {
    s == null || s <= 0 || s.isInfinite() || s.isNaN() -> calculating
    s < 60 -> "${ceil(s).toInt()}s"
    else -> "${floor(s / 60).toInt()}m ${ceil(s % 60).toInt()}s"
}

@Composable
private fun CatalogCard(item: CatalogAsset, row: AssetRow?, active: Boolean, ramBytes: Long, activating: Boolean, busy: Boolean, vm: ModelsViewModel) {
    val c = Boar.colors
    val mono = Boar.type["mono.xs"]
    val present = row?.present == true
    val corpus = item.kind == AssetKind.CORPUS
    var confirm by remember { mutableStateOf(false) }
    var confirmUnlist by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (active) Color(0x0F10B981).compositeOver(c.bgCardElevated) else c.bgCardElevated)
            .border(1.dp, if (active) c.emeraldBorder else c.borderDefault, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // The short name for every model: a Hugging Face file name reads as a name ("Meta Llama 3.1 8B Instruct · Q4_K_M"),
                // a Labs engine's model as "Qwen3.6 35B · Big Boar" (its engine's technical name is on its lab's page).
                Text(item.shortName, style = Boar.type.titleSm, color = c.textHeading)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(t("catalogItemCard.kind.${item.kind.name.lowercase()}"), style = mono.copy(fontSize = 9.sp), fontWeight = FontWeight.Bold, color = c.textSecondary,
                        modifier = Modifier.background(Boar.colors.wash(0.08f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                    Text(formatMB(item.sizeBytes), style = mono, color = c.textDim)
                    Text("•", style = mono, color = c.textDim)
                    Text(item.license, style = mono, color = c.textDim, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (item.required) { Text("•", style = mono, color = c.textDim); Text(t("catalogItemCard.default"), style = mono.copy(fontSize = 9.sp), color = c.textAccentCyan, fontWeight = FontWeight.Bold) }
                }
            }
            StatusBadge(present, active, row?.downloading == true)
        }
        // The memory badge for every LLM, a Labs engine's model too: the user sees it's bigger than the phone's RAM.
        if (item.kind == AssetKind.LLM) RamBadge(item.sizeBytes, ramBytes, streamedBy(item))
        // A Labs engine's model, while that engine runs it (not moved to BOAR's own by the user): it streams from storage.
        val engine = item.engine?.let { team.sopa.boar.plugins.EnginePlugins.forEngine(it) }
        val onEngine = engine?.takeIf { !team.sopa.boar.plugins.EnginePlugins.prefersBuiltIn(item) }
        if (onEngine != null) Row(
            Modifier.background(c.cyanBgSubtle, RoundedCornerShape(6.dp)).border(1.dp, c.cyanBorder, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            onEngine.engineArt?.let { r -> androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(r), null, Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                ?: Text(onEngine.engineEmoji, fontSize = 10.sp)
            Text(t("native.models.engineTag", "engine" to onEngine.engineLabel), style = mono, color = c.textAccentCyan, fontWeight = FontWeight.Bold)
        }
        // Boar Labs' rows for this model (Big Boar's engine choice), or a line from a lab that's off; nothing while Labs is off.
        team.sopa.boar.labs.LabHooks.CatalogCardExtra(item)
        team.sopa.boar.labs.LabHooks.CatalogCardHint(item)
        // Line height follows the text size (a fixed 18 sp packed the lines at Large).
        // Models added before the rename keep the old name in their saved description.
        Text(item.description.replace("not vetted by aoair", "not vetted by BOAR"), style = Boar.type.caption.let { it.copy(lineHeight = it.fontSize * 1.4f) }, color = c.textSecondary)
        row?.error?.let { e ->
            Row(Modifier.fillMaxWidth().background(c.crimsonBgSubtle, RoundedCornerShape(6.dp)).border(1.dp, c.crimsonBorder, RoundedCornerShape(6.dp)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⚠️", fontSize = 12.sp)
                Text(e, style = mono, color = c.crimson400)
            }
        }
        val calculating = t("catalogItemCard.calculating")
        if (row?.downloading == true) Column(
            Modifier.fillMaxWidth().background(c.bgTerminal, RoundedCornerShape(8.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(8.dp)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), color = c.emerald400, strokeWidth = 1.5.dp)
                Text(t("catalogItemCard.downloadingAsset"), style = mono, color = c.textAccentCyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                Spacer(Modifier.weight(1f))
                Text("%.0f%%".format(row.progress * 100), style = mono, color = c.textHeading, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Boar.colors.track(0.6f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(maxOf(row.progress, 0.02).toFloat()).clip(RoundedCornerShape(4.dp)).background(c.emerald500))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${if (row.bytesWritten > 0) formatMB(row.bytesWritten) else "0 MB"} / ${formatMB(item.sizeBytes)}", style = mono, color = c.textDim)
                Spacer(Modifier.weight(1f))
                Text(formatSpeed(row.speedBytesPerSec, calculating), style = mono, color = c.textAccentCyan, fontWeight = FontWeight.Bold)
                Text(" • ", style = mono, color = c.textDim)
                Text(t("catalogItemCard.eta", "eta" to formatEta(row.etaSeconds, calculating)), style = mono, color = c.textAccentAmber, fontWeight = FontWeight.SemiBold)
            }
            Text("✕ " + t("native.models.cancelDownload"), style = mono, color = c.crimson400, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, c.crimsonBorder, RoundedCornerShape(6.dp)).clickable { vm.cancelDownload(item) }.padding(horizontal = 10.dp, vertical = 5.dp))
        } else Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // The status takes the room left and wraps, so 🗑️ is never pushed off the card (Large text, Oct 15).
            Box(Modifier.weight(1f)) { when {
                // Copied over USB, never downloaded: say where it goes instead of offering a download.
                !present && item.externalPath != null -> Text(t("native.models.placeOverUsb", "engine" to (engine?.engineLabel ?: "")), style = mono, color = c.textMuted)
                !present -> SmallButton(if (row?.error != null) t("catalogItemCard.retryDownload") else t("catalogItemCard.downloadAsset")) { vm.download(item) }
                row?.indexing == true -> Busy(t("setupWizard.step3.buildingIndex"))
                corpus -> Check(t("catalogItemCard.indexedNote"))
                active -> Check(t("catalogItemCard.activeNote"))
                activating -> Busy(t("catalogItemCard.loadingModel"))
                onEngine != null -> SmallButton(t("native.models.selectUseEngine", "engine" to onEngine.engineLabel), enabled = !busy) { vm.use(item) }
                else -> SmallButton(t("catalogItemCard.selectUse"), enabled = !busy) { vm.use(item) }
            } }
            // A Labs engine's model was copied over USB: BOAR never deletes it.
            if (present && !item.required && item.externalPath == null) Text("🗑️", fontSize = 16.sp, modifier = Modifier.clickable(enabled = !busy) { confirm = true }.padding(4.dp))
            else if (!present && vm.isDiscovered(item)) Text("🗑️", fontSize = 16.sp, modifier = Modifier.clickable { confirmUnlist = true }.padding(4.dp))
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(t(if (corpus) "catalogItemCard.removeCorpusTitle" else "catalogItemCard.removeModelTitle")) },
        text = { Text(t(if (corpus) "catalogItemCard.removeCorpusMessage" else "catalogItemCard.removeModelMessage", "size" to formatMB(item.sizeBytes))) },
        confirmButton = { TextButton({ confirm = false; vm.remove(item) }) { Text(t("catalogItemCard.removeAssetButton"), color = c.crimson400) } },
        dismissButton = { TextButton({ confirm = false }) { Text(t("common.cancel")) } },
        containerColor = c.bgCard, titleContentColor = c.textHeading, textContentColor = c.textSecondary,
    )
    if (confirmUnlist) AlertDialog(
        onDismissRequest = { confirmUnlist = false },
        title = { Text(t("catalogItemCard.removeFromListTitle")) },
        text = { Text(t("catalogItemCard.removeFromListMessage")) },
        confirmButton = { TextButton({ confirmUnlist = false; vm.removeFromList(item) }) { Text(t("catalogItemCard.removeAssetButton"), color = c.crimson400) } },
        dismissButton = { TextButton({ confirmUnlist = false }) { Text(t("common.cancel")) } },
        containerColor = c.bgCard, titleContentColor = c.textHeading, textContentColor = c.textSecondary,
    )
}

/**
 * The Labs engine that runs [item] from storage (Big Boar for its 35B, or for a mixture of experts the user's chip sends
 * there), by its label; null when BOAR's own engine loads it into RAM.
 */
internal fun streamedBy(item: team.sopa.boar.engine.models.CatalogAsset): String? {
    if (team.sopa.boar.plugins.EnginePlugins.prefersBuiltIn(item)) return null
    return (item.engine?.let { team.sopa.boar.plugins.EnginePlugins.forEngine(it) } ?: team.sopa.boar.plugins.EnginePlugins.routed(item))?.engineLabel
}

/**
 * 🟢/🟡/🔴: how a model of [sizeBytes] fits this phone's [ramBytes] (Compatibility); nothing when the RAM is unknown.
 * A model too big for RAM that a Labs engine [streamedBy] reads from storage isn't "incompatible": it says so instead.
 */
@Composable
internal fun RamBadge(sizeBytes: Long, ramBytes: Long, streamedBy: String? = null) {
    if (ramBytes <= 0) return
    val c = Boar.colors
    val mono = Boar.type["mono.xs"]
    val comp = Compatibility.compute(sizeBytes, ramBytes)
    if (streamedBy != null && comp.value != "green") {
        Text("🌊 " + t("native.models.compat.streamed", "engine" to streamedBy), style = mono, color = c.textAccentCyan, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(c.cyanBgSubtle, RoundedCornerShape(6.dp)).border(1.dp, c.cyanBorder, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
        return
    }
    val (icon, fg, bg, border) = when (comp.value) {
        "green" -> listOf("🟢", c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder)
        "yellow" -> listOf("🟡", c.textAccentAmber, c.amberBgSubtle, c.amberBorder)
        "red" -> listOf("🔴", c.crimson400, c.crimsonBgSubtle, c.crimsonBorder)
        else -> listOf(null, null, null, null)
    }
    if (icon != null) Row(
        Modifier.background(bg as Color, RoundedCornerShape(6.dp)).border(1.dp, border as Color, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(icon as String, fontSize = 10.sp)
        Text(t("catalogItemCard.compat.${comp.value}"), style = mono, color = fg as Color, fontWeight = FontWeight.Bold)
    }
}

/**
 * Where more models come from, at the top: Hugging Face for now, searched on its own page (BrowseScreen). Not a model,
 * so it doesn't look like one: a dashed cyan outline and a tint, like an open slot.
 */
@Composable
private fun HfCta(onBrowse: () -> Unit) = DiscoverGgufCard(onBrowse)

/** "Discover GGUF models · Browse Hugging Face models": the same card in Models and in setup's model step. */
@Composable
fun DiscoverGgufCard(onBrowse: () -> Unit) {
    val c = Boar.colors
    val outline = c.cyan400
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.cyanBgSubtle)
            .drawBehind {
                drawRoundRect(outline, cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))))
            }
            .clickable(onClick = onBrowse).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("native.browse.discoverTitle"), style = Boar.type.titleSm, color = c.textAccentCyan)
            Text(t("native.browse.discoverSubtitle"), style = Boar.type.subtext, color = c.textSecondary)
        }
        Text("›", fontSize = 26.sp, color = c.textAccentCyan, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusBadge(present: Boolean, active: Boolean, downloading: Boolean) {
    val c = Boar.colors
    val mono = Boar.type["mono.xs"]
    val (text, fg, bg, border) = when {
        downloading -> listOf(t("catalogItemCard.badge.downloading"), c.textAccentCyan, c.cyanBgSubtle, c.cyanBorder)
        !present -> listOf(t("catalogItemCard.badge.notOnDisk"), c.textDim, Boar.colors.wash(0.04f), c.borderSubtle)
        active -> listOf(t("catalogItemCard.badge.active"), c.textAccentEmerald, c.emeraldBgSubtle, c.emeraldBorder)
        else -> listOf(t("catalogItemCard.badge.cached"), c.textSecondary, Boar.colors.wash(0.06f), c.borderDefault)
    }
    Row(
        Modifier.background(bg as Color, RoundedCornerShape(6.dp)).border(1.dp, border as Color, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (active && present) Box(Modifier.size(6.dp).background(c.emerald400, CircleShape))
        Text(text as String, style = mono.copy(fontSize = (9 * BrandType.factor(Boar.type.scale)).sp), color = fg as Color, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
internal fun SmallButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Boar.colors
    // Scaled with Settings → Text size: fixed at 12 sp they stayed tiny at Large (X6, Oct 16).
    Text(label, style = Boar.type.titleSm.copy(fontSize = (12 * BrandType.factor(Boar.type.scale)).sp), color = c.textAccentEmerald,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.emeraldBgSubtle).border(1.dp, c.emeraldBorder, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
internal fun Check(label: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("✓", color = Boar.colors.textAccentEmerald, fontWeight = FontWeight.Bold)
    Text(label, style = Boar.type.caption, color = Boar.colors.textAccentEmerald)
}

@Composable
internal fun Busy(label: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    CircularProgressIndicator(Modifier.size(14.dp), color = Boar.colors.emerald400, strokeWidth = 1.5.dp)
    Text(label, style = Boar.type.caption, color = Boar.colors.textAccentEmerald)
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}
