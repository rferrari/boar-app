package team.sopa.boar.labs.bmoe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.EngineParams
import team.sopa.boar.engine.TextModel
import team.sopa.boar.engine.models.AssetKind
import team.sopa.boar.engine.models.CatalogAsset
import team.sopa.boar.engine.models.Compatibility
import team.sopa.boar.engine.models.EngineChoice
import team.sopa.boar.engine.models.GgufHeader
import team.sopa.boar.engine.models.HfQuery
import team.sopa.boar.labs.LabContext
import team.sopa.boar.labs.LabPrefs
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.labs.engines.EngineProcess
import team.sopa.boar.plugins.EngineProvider
import team.sopa.boar.ui.models.ChipRow
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.io.File
import java.util.Locale

/**
 * Big Boar: BigMoeOnEdge (https://github.com/Helldez/BigMoeOnEdge, Apache-2.0) runs a mixture of experts bigger than
 * the phone's RAM. Its own model, Qwen3.6-35B-A3B (12.3 GB), is a catalog entry like any other while this lab is on:
 * downloaded into BOAR's models folder, or used from where it was copied over USB. Any other mixture of experts on the
 * phone of a [SUPPORTED_ARCHS] architecture can run on it too, by the user's engine choice on its card (Auto: when it's
 * too big for the phone's memory). The engine is built by scripts/build-lab-engines.sh into this folder's jniLibs and
 * answers through the engine slot (BmoeModel).
 */
object BmoeLab : LabPlugin, EngineProvider {
    override val id = "bmoe"
    override val emoji = "🦣"
    override val art = team.sopa.boar.R.drawable.lab_big_boar
    override val status = LabStatus.BETA

    /** Where a copy over USB goes (the experimental-engines branch's path): used when it's there, never downloaded again. */
    private const val USB_PATH = "/data/local/tmp/bmoe/Qwen3.6-35B-A3B-UD-Q2_K_XL.gguf"

    val MODEL = CatalogAsset(
        id = "qwen3.6-35b-a3b-bmoe", kind = AssetKind.LLM,
        label = "Qwen3.6-35B-A3B · BigMoeOnEdge (experimental)", displayName = "Qwen3.6 35B · Big Boar",
        filename = "models/qwen3.6-35b-a3b-ud-q2-k-xl.gguf", engine = "bmoe", architecture = "qwen35moe",
        // Pinned to one commit, checked against its LFS sha256 (both from the Hugging Face API, Oct 19 2026): a file
        // replaced on main later isn't downloaded, and a damaged download is caught.
        sizeBytes = 12_290_628_576, sha256 = "96b9c0af5c77a4ecaabe3983175112b5ece763261c1ece12b2494b692a70dad7",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3.6-35B-A3B-GGUF/resolve/a483e9e6cbd595906af30beda3187c2663a1118c/Qwen3.6-35B-A3B-UD-Q2_K_XL.gguf",
        license = "Apache-2.0",
        description = "A 35B mixture of experts (about 3B used per word), bigger than the phone's RAM: BigMoeOnEdge keeps the always-used weights in memory and reads each word's experts from storage. Slow, but a real 35B. Download it here (12.3 GB) or copy it over USB (Boar Labs → Big Boar).",
        required = false,
    )

    override val engine: EngineProvider get() = this
    override val engineLabel = "Big Boar"
    override val engineEmoji get() = emoji
    override val engineArt get() = art
    override fun models() = listOf(model35())

    /**
     * The 35B as the catalog lists it now: BOAR's own copy once fully downloaded; else a copy over USB at [USB_PATH]
     * when there is one (externalPath: not downloaded again, never deleted by the app); else a model to download.
     */
    fun model35(): CatalogAsset {
        val ctx = LabContext.app ?: return MODEL
        val own = File(ctx.filesDir, MODEL.filename)
        if (own.isFile && own.length() == MODEL.sizeBytes) return MODEL
        val usb = File(USB_PATH)
        return if (runCatching { usb.isFile && usb.canRead() }.getOrDefault(false)) MODEL.copy(externalPath = USB_PATH) else MODEL
    }

    /** From BOAR's own storage (a downloaded model) or the path it was copied to over USB: wherever the app finds it. */
    override suspend fun load(asset: CatalogAsset, params: EngineParams): TextModel? {
        val ctx = LabContext.app ?: return null
        val file = BoarServices.get(ctx).fileOf(asset).takeIf { it.isFile } ?: return null
        return BmoeModel.start(ctx, file.path, params)
    }

    // --- Any mixture of experts on the phone ----------------------------------------------------------------------

    /**
     * Architectures Big Boar runs as a streamed mixture of experts. Its llama.cpp also knows "llama" (Mixtral and
     * other llama-based MoEs), left out until one is measured on a phone.
     */
    val SUPPORTED_ARCHS = setOf("qwen3moe", "qwen35moe", "lfm2moe")

    private const val CHOICE_KEY = "bmoe.engine."

    /** The user's engine for [modelId] (Engine chips on its card): Auto until they pick. */
    fun choice(modelId: String): EngineChoice.Choice = EngineChoice.Choice.of(LabPrefs.string(CHOICE_KEY + modelId))

    fun setChoice(modelId: String, choice: EngineChoice.Choice) = LabPrefs.putString(CHOICE_KEY + modelId, choice.value)

    private fun engineInBuild(ctx: android.content.Context) = EngineProcess.executable(ctx, BmoeModel.BINARY) != null

    /**
     * A model of BOAR's own Big Boar can run: an LLM fully on the phone whose GGUF header says it's a mixture of
     * experts of a [SUPPORTED_ARCHS] architecture (read once per file, GgufHeader). Reads the file: call off the main thread
     * the first time.
     */
    fun eligible(asset: CatalogAsset, services: BoarServices): Boolean {
        if (asset.kind != AssetKind.LLM || asset.engine != null) return false
        val ctx = LabContext.app ?: return false
        if (!engineInBuild(ctx)) return false
        val file = services.fileOf(asset)
        // A download still running isn't the model yet.
        if (!file.isFile || (asset.sizeBytes > 0 && file.length() != asset.sizeBytes)) return false
        val moe = GgufHeader.moe(file) ?: return false
        return moe.experts > 0 && moe.arch in SUPPORTED_ARCHS
    }

    /** The memory fit the catalog shows for [asset] on this phone. */
    fun fit(asset: CatalogAsset, services: BoarServices): Compatibility = Compatibility.compute(asset.sizeBytes, services.totalRamBytes)

    /** The engine [asset] answers with now: the user's choice, Auto = Big Boar when it's too big for this phone's memory. */
    fun resolve(asset: CatalogAsset, services: BoarServices): EngineChoice.Engine {
        val choice = choice(asset.id)
        val fit = fit(asset, services)
        // The header is read only when the choice could pick Big Boar.
        if (!EngineChoice.prefersBigBoar(choice, fit)) return EngineChoice.Engine.BOAR
        return EngineChoice.resolve(choice, fit, eligible(asset, services))
    }

    /** The routing slot (EnginePlugins.route): a model of BOAR's own that resolves to Big Boar answers here. */
    override fun claims(asset: CatalogAsset): Boolean {
        if (asset.engine != null || asset.kind != AssetKind.LLM) return false
        val services = LabContext.app?.let { BoarServices.get(it) } ?: return false
        return runCatching { resolve(asset, services) == EngineChoice.Engine.BIG_BOAR }.getOrDefault(false)
    }

    /** The 35B with the BOAR chip picked: BOAR's own engine loads it whole (the chat asks first when it's too big). */
    override fun prefersBuiltIn(asset: CatalogAsset): Boolean = asset.engine == id && choice(asset.id) == EngineChoice.Choice.BOAR

    // --- Model Catalog and Hugging Face search --------------------------------------------------------------------

    override val browseMoeFilter: Boolean get() = true

    /** Known architecture: only one Big Boar runs. Not listed: the repo's name (a hint; the header decides after download). */
    override fun browseHint(repoId: String, architecture: String?): String? {
        val runs = if (architecture != null) architecture in SUPPORTED_ARCHS else HfQuery.moeHint(repoId, null)
        return if (runs) "native.labs.bmoe.browseHint" else null
    }

    /**
     * On an eligible model's card: the Engine chips (Auto, showing what it picks; BOAR; Big Boar) and why Big Boar
     * helps on this phone. The 35B runs only on Big Boar (its engine tag says so), so it shows just the reason.
     */
    @Composable
    override fun CatalogCardExtra(asset: CatalogAsset) {
        if (asset.kind != AssetKind.LLM) return
        val ctx = LocalContext.current
        val services = remember { BoarServices.get(ctx) }
        val scope = rememberCoroutineScope()
        val memFit = remember(asset.id, asset.sizeBytes) { fit(asset, services) }
        if (asset.engine == id) {
            if (memFit == Compatibility.RED) Hint(t("native.labs.bmoe.card.red"))
            return
        }
        if (asset.engine != null) return
        // The header is read off the main thread, once per file.
        val canRun by produceState(false, asset.id) { value = withContext(Dispatchers.IO) { runCatching { eligible(asset, services) }.getOrDefault(false) } }
        if (!canRun) return
        var current by remember(asset.id) { mutableStateOf(choice(asset.id)) }
        val auto = if (EngineChoice.resolve(EngineChoice.Choice.AUTO, memFit, eligible = true) == EngineChoice.Engine.BIG_BOAR) engineLabel else "BOAR"
        ChipRow(t("native.labs.bmoe.card.engine"), listOf(
            EngineChoice.Choice.AUTO to t("native.labs.bmoe.card.auto", "engine" to auto),
            EngineChoice.Choice.BOAR to "BOAR",
            EngineChoice.Choice.BIG_BOAR to engineLabel,
        ), current) { picked ->
            if (picked != current) {
                current = picked
                setChoice(asset.id, picked)
                // Loaded by the other engine: the next answer loads it again with this one.
                if (services.host.residentId() == asset.id) scope.launch { services.host.unload() }
            }
        }
        when (memFit) {
            Compatibility.RED -> Hint(t("native.labs.bmoe.card.red"))
            Compatibility.YELLOW -> Hint(t("native.labs.bmoe.card.yellow"))
            else -> {}
        }
    }

    @Composable
    private fun Hint(text: String) = Text(text, style = Boar.type.subtext, color = Boar.colors.textAccentCyan)

    @Composable
    override fun Content() {
        val c = Boar.colors
        val ctx = LocalContext.current
        val services = remember { BoarServices.get(ctx) }
        val scope = rememberCoroutineScope()
        val engineOk = remember { EngineProcess.executable(ctx, BmoeModel.BINARY) != null }
        val asset = remember { model35() }
        val file = remember { services.fileOf(asset) }
        // Over USB: there and readable. Downloaded: all of it (a download in progress isn't the model yet).
        val present = remember { file.isFile && file.canRead() && (asset.externalPath != null || file.length() == asset.sizeBytes) }
        var using by remember { mutableStateOf(services.settings.activeModelId("llm") == MODEL.id) }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(t("native.labs.bmoe.engine")) {
                Text(t(if (engineOk) "native.labs.bmoe.engineOk" else "native.labs.bmoe.engineMissing"), style = Boar.type.body, color = if (engineOk) c.textAccentEmerald else c.textMuted)
            }
            Card(t("native.labs.bmoe.model")) {
                Text(MODEL.label, style = Boar.type.body, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    if (present) t("native.labs.bmoe.modelOk", "size" to String.format(Locale.US, "%.1f GB", file.length() / 1e9))
                    else t("native.labs.bmoe.modelMissing", "size" to String.format(Locale.US, "%.1f GB", MODEL.sizeBytes / 1e9), "path" to USB_PATH),
                    style = Boar.type["mono.xs"], color = if (present) c.textAccentEmerald else c.textMuted,
                )
            }
            if (engineOk && present) {
                if (using) Text(t("native.labs.bmoe.using"), style = Boar.type.body, color = c.textAccentEmerald)
                else Text(
                    t("native.labs.bmoe.use"), style = Boar.type.body, color = c.textAccentCyan, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(10.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(10.dp))
                        .clickable {
                            services.settings.setActiveModelId("llm", MODEL.id)
                            using = true
                            scope.launch { services.host.unload() }
                        }.padding(14.dp),
                )
            }
            Text(t("native.labs.bmoe.more"), style = Boar.type.subtext, color = c.textSecondary)
            Text(t("native.labs.bmoe.note"), style = Boar.type.subtext, color = c.textMuted)
        }
    }

    @Composable
    private fun Card(title: String, content: @Composable () -> Unit) {
        val c = Boar.colors
        Column(
            Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(12.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = Boar.type["mono.xs"], color = c.textDim, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
