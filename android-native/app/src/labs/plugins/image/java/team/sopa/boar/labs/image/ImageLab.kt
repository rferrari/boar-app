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

    /**
     * The styles chosen on Oct 14 from 22 drawn on the X6 (same subject and seed, 384 px; docs/…image-gen-styles):
     * 7 chips in two rows. The subject goes where {s} is. Dropped: textbook, vector, watercolor, pencil (look-alikes of
     * the ink styles with this one-step model), mecha, glitch, tactical, vaporwave, synthwave, dreamcore, cottagecore.
     */
    enum class Style(val key: String, val template: String) {
        /**
         * No style: no chip of its own, it's what's left when the user taps the selected style off. The subject is the
         * whole prompt, for people who write their own ("a boar, ukiyo-e woodblock").
         */
        NONE("none", "{s}"),
        ENGRAVING("engraving", "{s}, vintage natural history engraving, black ink, white background"),
        // Not "encyclopedia plate": with one step the model drew a dinner plate under the subject.
        SCIENTIFIC("scientific", "scientific illustration of {s}, encyclopedia illustration, detailed ink drawing, white background"),
        PHOTO("photo", "{s}, photorealistic, natural light, sharp focus, 35mm photo"),
        // Cyan telemetry wireframe, not the neon city. Key "cyber" kept: a saved default carries over.
        CYBER("cyber", "{s}, cyberpunk, dark background, glowing neon cyan telemetry lines, wireframe"),
        SOLARPUNK("solarpunk", "{s}, solarpunk, green technology, golden sunlight, vines on chrome"),
        Y2K("y2k", "{s}, y2k aesthetic, chrome and glossy bubbles, iridescent, early 2000s digital art"),
        // Shown as "Lo-fi"; the anime words are what give the look.
        LOFI("lofi", "{s}, lo-fi anime illustration, soft pastel colors, cozy evening light, cel shading");

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
    /** What the shown picture was drawn from, for Regenerate. */
    val subject: String? = null,
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
    }

    /** "From your last chat": read each time the screen opens (the view model outlives it), so it's the latest chat. */
    fun refreshLastChat() {
        viewModelScope.launch {
            val title = withContext(Dispatchers.IO) { runCatching { services.chats.listSessions().firstOrNull { it.title.isNotBlank() }?.title }.getOrNull() }
            _state.update { it.copy(lastChat = title?.trim()?.trimEnd('?', '.', '!')?.take(80)) }
        }
    }

    /** The gallery as it is on disk now (pictures may have been erased while the screen was closed). */
    fun reload() {
        refresh()
        _state.value.image?.takeIf { !it.isFile }?.let { _state.update { s -> s.copy(image = null, seconds = null, subject = null) } }
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
        val replacing = s.image?.takeIf { s.subject == subject.trim() && regenerating }
        regenerating = false
        // The old picture goes: its square shows the drawing in progress until the new one arrives.
        _state.update { it.copy(phase = "loading", error = null, seconds = null, image = null) }
        job = viewModelScope.launch {
            runCatching { SdRunner.draw(getApplication(), subject, s.style, s.size) { ph -> _state.update { it.copy(phase = ph) } } }
                .onSuccess { d ->
                    // Regenerate: the new picture takes the old one's place, which is deleted, not kept in the gallery.
                    replacing?.takeIf { it != d.file }?.delete()
                    _state.update { it.copy(phase = null, image = d.file, seconds = d.seconds, subject = subject.trim()) }
                }
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

    private var regenerating = false

    /** Draws the shown picture's subject again (same style and size) and replaces it. */
    fun regenerate() {
        val subject = _state.value.subject ?: return
        regenerating = true
        generate(subject)
    }

    fun delete(f: File) {
        f.delete()
        if (_state.value.image == f) _state.update { it.copy(image = null, seconds = null) }
        refresh()
    }



}
