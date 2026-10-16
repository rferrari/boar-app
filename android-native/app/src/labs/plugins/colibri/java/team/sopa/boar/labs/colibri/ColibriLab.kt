package team.sopa.boar.labs.colibri

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
import team.sopa.boar.labs.LabContext
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.labs.engines.EngineProcess
import team.sopa.boar.plugins.EngineProvider
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.io.File

/**
 * Boar Wisp (was Hummingbird): OLMoE-1B-7B on colibri (https://github.com/JustVugg/colibri, Apache-2.0), a pure-C engine that keeps
 * part of each layer's experts in RAM and streams the rest from storage. The converted model folder (about 7 GB) is
 * copied over USB; the engine is built by scripts/build-lab-engines.sh into this folder's jniLibs.
 */
object ColibriLab : LabPlugin, EngineProvider {
    override val id = "colibri"
    override val emoji = "🐦"
    override val art = team.sopa.boar.R.drawable.truffle_spirit
    /** Measured too slow to use (0.8 tok/s on the X6, Oct 14): kept open to try, marked under review. */
    override val status = LabStatus.REVIEW

    private const val MODEL_PATH = "/data/local/tmp/colibri/olmoe-colibri"

    val MODEL = CatalogAsset(
        id = "olmoe-1b-7b-colibri", kind = AssetKind.LLM,
        label = "OLMoE-1B-7B · colibri (experimental)", displayName = "OLMoE 1B-7B · colibri",
        filename = "external/olmoe-colibri", externalPath = MODEL_PATH, engine = "colibri",
        sizeBytes = 0, sha256 = "", sourceUrl = "", license = "Apache-2.0",
        description = "Runs on colibri, a pure-C engine that keeps 16 of each layer's 64 experts in RAM and streams the rest from storage (about 3 GB peak). Copy the converted model folder over USB first (Boar Labs → Hummingbird).",
        required = false,
    )

    override val engine: EngineProvider get() = this
    override val engineLabel = "Boar Wisp"
    override val engineEmoji get() = emoji
    override fun models() = listOf(MODEL)

    override suspend fun load(asset: CatalogAsset, params: EngineParams): TextModel? {
        val ctx = LabContext.app ?: return null
        return ColibriModel.start(ctx, asset.externalPath ?: return null, params)
    }

    @Composable
    override fun Content() {
        val c = Boar.colors
        val ctx = LocalContext.current
        val services = remember { BoarServices.get(ctx) }
        val scope = rememberCoroutineScope()
        val engineOk = remember { EngineProcess.executable(ctx, ColibriModel.BINARY) != null }
        val present = remember { File(MODEL_PATH, "config.json").canRead() }
        var using by remember { mutableStateOf(services.settings.activeModelId("llm") == MODEL.id) }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(t("native.labs.colibri.engine")) {
                Text(t(if (engineOk) "native.labs.colibri.engineOk" else "native.labs.colibri.engineMissing"), style = Boar.type.body, color = if (engineOk) c.textAccentEmerald else c.textMuted)
            }
            Card(t("native.labs.colibri.model")) {
                Text(MODEL.label, style = Boar.type.body, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(if (present) t("native.labs.colibri.modelOk") else t("native.labs.colibri.modelMissing", "path" to MODEL_PATH),
                    style = Boar.type["mono.xs"], color = if (present) c.textAccentEmerald else c.textMuted)
            }
            if (engineOk && present) {
                if (using) Text(t("native.labs.colibri.using"), style = Boar.type.body, color = c.textAccentEmerald)
                else Text(
                    t("native.labs.colibri.use"), style = Boar.type.body, color = c.textAccentCyan, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(10.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(10.dp))
                        .clickable {
                            services.settings.setActiveModelId("llm", MODEL.id)
                            using = true
                            scope.launch { services.host.unload() }
                        }.padding(14.dp),
                )
            }
            Text(t("native.labs.colibri.note"), style = Boar.type.subtext, color = c.textMuted)
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
