package team.sopa.boar.labs.image

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.models.AssetKind
import team.sopa.boar.engine.models.AssetStore
import team.sopa.boar.engine.models.CatalogAsset
import java.io.File

/**
 * Labs → Image: one illustration from a subject and a style, with stable-diffusion.cpp's sd-cli and SDXS-512
 * (one step). sd-cli ships as libsdcli.so (scripts/build-sd-cli.sh) and runs as its own process: its ggml can't
 * clash with the chat engine's, a crash or an out-of-memory kill takes only the image down, and its memory is
 * freed when it exits.
 */
object ImageLab {
    /** Measured on the X6 Pro, Oct 13 2026 (CPU, 4 fast cores): 384 px ~5.5 s, 512 px ~9.5 s; the labels promise ~15 s and ~20 s, untested on other phones. 256 px breaks the shapes. The first is the default. */
    val SIZES = listOf(384, 512)

    /** The styles from the Oct 13 tests (8 at most, two rows of chips); the subject goes where {s} is. */
    enum class Style(val key: String, val template: String) {
        ENGRAVING("engraving", "{s}, vintage natural history engraving, black ink, white background"),
        TEXTBOOK("textbook", "{s}, textbook illustration, clean line art with flat watercolor, white background"),
        VECTOR("vector", "{s}, flat vector illustration, simple shapes, bold colors"),
        SCIENTIFIC("scientific", "scientific illustration of {s}, encyclopedia plate, detailed ink drawing, white background"),
        // Added after the second test (X6 Pro, 384 px, 5.9-6.9 s). Blueprint, pixel art, line art and solarpunk comic didn't hold up; oil gave way to the HUD.
        PHOTO("photo", "{s}, photorealistic, natural light, sharp focus, 35mm photo"),
        PENCIL("pencil", "{s}, detailed graphite pencil sketch, cross-hatching, white paper"),
        WATERCOLOR("watercolor", "{s}, loose watercolor painting, soft washes, paper texture"),
        CYBER("cyber", "{s}, cyberpunk HUD schematic, dark background, glowing neon cyan telemetry lines, wireframe");

        fun prompt(subject: String) = template.replace("{s}", subject.trim())
    }

    /** The user's pick ("Set as default"), else engraving. */
    fun defaultStyle(): Style = team.sopa.boar.labs.LabPrefs.string("image.style")?.let { k -> Style.entries.firstOrNull { it.key == k } } ?: Style.ENGRAVING

    fun setDefaultStyle(s: Style) = team.sopa.boar.labs.LabPrefs.putString("image.style", s.key)

    /** The user's size ("Set as default" saves style and size together), else the first of [SIZES]. */
    fun defaultSize(): Int = team.sopa.boar.labs.LabPrefs.string("image.size")?.toIntOrNull()?.takeIf { it in SIZES } ?: SIZES.first()

    fun setDefaultSize(px: Int) = team.sopa.boar.labs.LabPrefs.putString("image.size", "$px")

    private const val TIMES_KEPT = 10

    /** A finished drawing's time, tap to picture (model load included), kept per size: the last [TIMES_KEPT]. */
    fun recordTime(size: Int, seconds: Double) {
        val key = "image.times.$size"
        val kept = (times(size) + seconds).takeLast(TIMES_KEPT)
        team.sopa.boar.labs.LabPrefs.putString(key, kept.joinToString(",") { String.format(java.util.Locale.US, "%.2f", it) })
    }

    private fun times(size: Int): List<Double> =
        team.sopa.boar.labs.LabPrefs.string("image.times.$size")?.split(',')?.mapNotNull { it.toDoubleOrNull() }.orEmpty()

    /** What this phone takes at [size]: the average of its last drawings, rounded up; null before the first one. */
    fun expectedSeconds(size: Int): Int? = times(size).takeIf { it.isNotEmpty() }?.average()?.let { kotlin.math.ceil(it).toInt() }

    val MODEL = CatalogAsset(
        id = "lab-sdxs-512-q8",
        kind = AssetKind.LLM,
        label = "SDXS-512 tinySD distilled Q8_0",
        filename = "labs/sdxs-512-tinySDdistilled_Q8_0.gguf",
        sizeBytes = 682_847_200,
        sha256 = "409ab23582ee074c6b9d5395784fc0741b0599fb9d138686c69087c71678eb6a",
        sourceUrl = "https://huggingface.co/concedo/sdxs-512-tinySDdistilled-GGUF/resolve/main/sdxs-512-tinySDdistilled_Q8_0.gguf",
        license = "unverified",
        description = "Image lab model",
        required = false,
    )
}

data class ImageLabState(
    val engineFound: Boolean = true,
    val modelPresent: Boolean = false,
    val downloadPct: Int? = null,
    val style: ImageLab.Style = ImageLab.Style.ENGRAVING,
    val defaultStyle: ImageLab.Style = ImageLab.Style.ENGRAVING,
    val defaultSize: Int = ImageLab.SIZES.first(),
    val size: Int = ImageLab.SIZES.first(),
    /** "loading", "drawing", "decoding" while sd-cli runs; null when idle. */
    val phase: String? = null,
    val image: File? = null,
    val seconds: Double? = null,
    val error: String? = null,
    val gallery: List<File> = emptyList(),
    /** The latest chat's title, offered as a subject. */
    val lastChat: String? = null,
)

class ImageLabViewModel(app: Application) : AndroidViewModel(app) {
    private val services = BoarServices.get(app)
    private val store = AssetStore(app.filesDir, services.catalog)
    private val _state = MutableStateFlow(ImageLabState())
    val state: StateFlow<ImageLabState> = _state
    private var job: Job? = null

    init {
        team.sopa.boar.labs.LabPrefs.init(app)
        val ds = ImageLab.defaultStyle()
        val dz = ImageLab.defaultSize()
        _state.update { it.copy(style = ds, defaultStyle = ds, size = dz, defaultSize = dz) }
        refresh()
        viewModelScope.launch {
            val title = withContext(Dispatchers.IO) { runCatching { services.chats.listSessions().firstOrNull { it.title.isNotBlank() }?.title }.getOrNull() }
            _state.update { it.copy(lastChat = title?.trim()?.trimEnd('?', '.', '!')?.take(80)) }
        }
    }

    private fun refresh() {
        val ctx = getApplication<Application>()
        val gallery = SdRunner.outDir(ctx).listFiles { f -> f.extension == "png" }.orEmpty().sortedByDescending { it.lastModified() }
        _state.update { it.copy(engineFound = SdRunner.engine(ctx) != null, modelPresent = SdRunner.modelFile(ctx).length() == ImageLab.MODEL.sizeBytes, gallery = gallery) }
    }

    fun download() {
        if (_state.value.downloadPct != null) return
        _state.update { it.copy(downloadPct = 0, error = null) }
        viewModelScope.launch {
            runCatching { store.download(ImageLab.MODEL) { p -> _state.update { it.copy(downloadPct = (p.bytesDone * 100 / p.bytesTotal.coerceAtLeast(1)).toInt()) } } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(downloadPct = null) }
            refresh()
        }
    }

    fun setStyle(s: ImageLab.Style) = _state.update { it.copy(style = s) }
    fun setSize(px: Int) = _state.update { it.copy(size = px) }
    fun show(f: File) = _state.update { it.copy(image = f, seconds = null, error = null) }

    fun generate(subject: String) {
        if (subject.isBlank() || _state.value.phase != null || SdRunner.engine(getApplication()) == null) return
        val s = _state.value
        _state.update { it.copy(phase = "loading", error = null, seconds = null) }
        job = viewModelScope.launch {
            runCatching { SdRunner.draw(getApplication(), subject, s.style, s.size) { ph -> _state.update { it.copy(phase = ph) } } }
                .onSuccess { d -> _state.update { it.copy(phase = null, image = d.file, seconds = d.seconds) } }
                .onFailure { e -> if (e !is kotlinx.coroutines.CancellationException) _state.update { it.copy(phase = null, error = e.message) } }
            refresh()
        }
    }

    /** The style and size Illustrate (in the chat) and a new Image Gen session use. */
    fun setDefaults() {
        val s = _state.value
        ImageLab.setDefaultStyle(s.style)
        ImageLab.setDefaultSize(s.size)
        _state.update { it.copy(defaultStyle = s.style, defaultSize = s.size) }
    }

    fun cancel() { SdRunner.cancel(); job?.cancel(); _state.update { it.copy(phase = null) } }

    fun deleteAll(files: Collection<File>) {
        files.forEach { it.delete() }
        if (_state.value.image in files) _state.update { it.copy(image = null, seconds = null) }
        refresh()
    }

    fun delete(f: File) {
        f.delete()
        if (_state.value.image == f) _state.update { it.copy(image = null, seconds = null) }
        refresh()
    }



}
