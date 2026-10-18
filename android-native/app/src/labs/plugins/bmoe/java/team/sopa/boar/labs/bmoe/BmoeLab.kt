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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.EngineParams
import team.sopa.boar.engine.TextModel
import team.sopa.boar.engine.models.AssetKind
import team.sopa.boar.engine.models.CatalogAsset
import team.sopa.boar.engine.models.Compatibility
import team.sopa.boar.engine.models.EngineChoice
import team.sopa.boar.engine.models.GgufHeader
import team.sopa.boar.labs.LabContext
import team.sopa.boar.labs.LabPrefs
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.labs.engines.EngineProcess
import team.sopa.boar.plugins.EngineProvider
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.io.File
import java.util.Locale

/**
 * Big Boar: Qwen3.6-35B-A3B on BigMoeOnEdge (https://github.com/Helldez/BigMoeOnEdge, Apache-2.0), a 35B mixture of
 * experts bigger than the phone's RAM. The model is copied to the phone over USB (12.3 GB), never downloaded; the
 * engine is built by scripts/build-lab-engines.sh into this folder's jniLibs. While this lab is on, the model is
 * listed in Models like any other and answers through the engine slot (BmoeModel).
 */
object BmoeLab : LabPlugin, EngineProvider {
    override val id = "bmoe"
    override val emoji = "🦣"
    override val art = team.sopa.boar.R.drawable.truffle_moe
    override val status = LabStatus.BETA

    /** Where `adb push` puts it (the experimental-engines branch used the same path). */
    private const val MODEL_PATH = "/data/local/tmp/bmoe/Qwen3.6-35B-A3B-UD-Q2_K_XL.gguf"

    val MODEL = CatalogAsset(
        id = "qwen3.6-35b-a3b-bmoe", kind = AssetKind.LLM,
        label = "Qwen3.6-35B-A3B · BigMoeOnEdge (experimental)", displayName = "Qwen3.6 35B · Big Boar",
        filename = "external/qwen3.6-35b-a3b-ud-q2-k-xl", externalPath = MODEL_PATH, engine = "bmoe",
        sizeBytes = 12_290_628_576, sha256 = "",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3.6-35B-A3B-GGUF/resolve/main/Qwen3.6-35B-A3B-UD-Q2_K_XL.gguf",
        license = "Apache-2.0",
        description = "A 35B mixture of experts (about 3B used per word), bigger than the phone's RAM: BigMoeOnEdge keeps the always-used weights in memory and reads each word's experts from storage. Slow, but a real 35B. Copy it over USB first (Boar Labs → Big Boar).",
        required = false,
    )

    override val engine: EngineProvider get() = this
    override val engineLabel = "Big Boar"
    override val engineEmoji get() = emoji
    override val engineArt get() = art
    override fun models() = listOf(MODEL)

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

    @Composable
    override fun Content() {
        val c = Boar.colors
        val ctx = LocalContext.current
        val services = remember { BoarServices.get(ctx) }
        val scope = rememberCoroutineScope()
        val engineOk = remember { EngineProcess.executable(ctx, BmoeModel.BINARY) != null }
        val file = remember { File(MODEL_PATH) }
        val present = remember { file.isFile && file.canRead() }
        var using by remember { mutableStateOf(services.settings.activeModelId("llm") == MODEL.id) }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(t("native.labs.bmoe.engine")) {
                Text(t(if (engineOk) "native.labs.bmoe.engineOk" else "native.labs.bmoe.engineMissing"), style = Boar.type.body, color = if (engineOk) c.textAccentEmerald else c.textMuted)
            }
            Card(t("native.labs.bmoe.model")) {
                Text(MODEL.label, style = Boar.type.body, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    if (present) t("native.labs.bmoe.modelOk", "size" to String.format(Locale.US, "%.1f GB", file.length() / 1e9))
                    else t("native.labs.bmoe.modelMissing", "file" to MODEL_PATH.substringAfterLast('/'), "path" to MODEL_PATH),
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
