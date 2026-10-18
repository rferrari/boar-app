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
     * A drawing's size in pixels: square, portrait (taller) or banner (wider), sides in multiples of 64. The portrait and
     * banner sizes are candidates to time on the X6 (Oct 16) before the list is cut to the ones kept; 256 px broke the
     * shapes on Oct 13, so 576×256 is the low end.
     */
    /** What a drawing was made from, kept beside it as <image>.png.json: subject, style key, size key, seed. */
    data class Recipe(val subject: String, val style: String, val size: String, val seed: Int)

    fun recipeFile(image: java.io.File) = java.io.File(image.path + ".json")

    fun writeRecipe(image: java.io.File, r: Recipe) {
        runCatching { recipeFile(image).writeText(org.json.JSONObject().put("subject", r.subject).put("style", r.style).put("size", r.size).put("seed", r.seed).toString()) }
    }

    /** The recipe saved with [image]; null for drawings made before recipes were kept. */
    fun recipeOf(image: java.io.File): Recipe? = runCatching {
        org.json.JSONObject(recipeFile(image).readText()).let { o -> Recipe(o.getString("subject"), o.getString("style"), o.getString("size"), o.getInt("seed")) }
    }.getOrNull()

    /** Deletes a drawing and its recipe. */
    fun deleteDrawing(image: java.io.File) { image.delete(); recipeFile(image).delete() }

    data class Canvas(val w: Int, val h: Int) {
        /** "384" for a square (the key older saves and times used), "384x512" otherwise. */
        val key: String get() = if (w == h) "$w" else "${w}x$h"
        val shape: String get() = when { w == h -> "square"; h > w -> "portrait"; else -> "banner" }
        val label: String get() = if (w == h) "$w px" else "$w×$h"
        companion object { fun of(key: String?): Canvas? = CANVASES.firstOrNull { it.key == key } }
    }

    /** Small, medium and high per shape (Oct 16): 768×320 drew in 13 s on the X6, so the high sizes are worth timing. */
    val CANVASES = listOf(
        Canvas(384, 384), Canvas(512, 512), Canvas(640, 640),
        Canvas(320, 448), Canvas(384, 512), Canvas(448, 640),
        Canvas(576, 256), Canvas(640, 320), Canvas(768, 320),
    )

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
        LOFI("lofi", "{s}, lo-fi anime illustration, soft pastel colors, cozy evening light, cel shading"),
        // Back on Oct 16 at the user's request (dropped Oct 14 as a look-alike of the ink styles with this one-step model).
        WATERCOLOR("watercolor", "{s}, loose watercolor, paper texture");

        fun prompt(subject: String) = template.replace("{s}", subject.trim())
    }

    /** The user's pick ("Set as default"), else engraving. */
    fun defaultStyle(): Style = team.sopa.boar.labs.LabPrefs.string("image.style")?.let { k -> Style.entries.firstOrNull { it.key == k } } ?: Style.ENGRAVING

    fun setDefaultStyle(s: Style) = team.sopa.boar.labs.LabPrefs.putString("image.style", s.key)

    /** The user's size ("Set as default" saves style and size together), else the first of [CANVASES]. */
    fun defaultCanvas(): Canvas = Canvas.of(team.sopa.boar.labs.LabPrefs.string("image.size")) ?: CANVASES.first()

    fun setDefaultCanvas(c: Canvas) = team.sopa.boar.labs.LabPrefs.putString("image.size", c.key)

    private const val TIMES_KEPT = 10

    /** A finished drawing's time, tap to picture (model load included), kept per size: the last [TIMES_KEPT]. */
    fun recordTime(size: Canvas, seconds: Double) {
        val key = "image.times.${size.key}"
        val kept = (times(size) + seconds).takeLast(TIMES_KEPT)
        team.sopa.boar.labs.LabPrefs.putString(key, kept.joinToString(",") { String.format(java.util.Locale.US, "%.2f", it) })
    }

    private fun times(size: Canvas): List<Double> =
        team.sopa.boar.labs.LabPrefs.string("image.times.${size.key}")?.split(',')?.mapNotNull { it.toDoubleOrNull() }.orEmpty()

    /** What this phone takes at [size]: the average of its last drawings, rounded up; null before the first one. */
    fun expectedSeconds(size: Canvas): Int? = times(size).takeIf { it.isNotEmpty() }?.average()?.let { kotlin.math.ceil(it).toInt() }

    val MODEL = CatalogAsset(
        id = "lab-sdxs-512-q8",
        kind = AssetKind.LLM,
        label = "SDXS-512 tinySD distilled Q8_0",
        filename = "labs/sdxs-512-tinySDdistilled_Q8_0.gguf",
        sizeBytes = 682_847_200,
        sha256 = "409ab23582ee074c6b9d5395784fc0741b0599fb9d138686c69087c71678eb6a",
        sourceUrl = "https://huggingface.co/concedo/sdxs-512-tinySDdistilled-GGUF/resolve/main/sdxs-512-tinySDdistilled_Q8_0.gguf",
        license = "CreativeML Open RAIL++-M",  // with use restrictions (Attachment A); the download screen says so
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
    val defaultSize: ImageLab.Canvas = ImageLab.CANVASES.first(),
    val size: ImageLab.Canvas = ImageLab.CANVASES.first(),
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
    /** The shown picture's seed, and whether the next drawing reuses it (same composition, new words). */
    val seed: Int? = null,
    val seedLocked: Boolean = false,
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
        val dz = ImageLab.defaultCanvas()
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

    /** Deletes the image model (its 683 MB come back); the drawings stay. Not while drawing or downloading. */
    fun removeModel() {
        if (_state.value.phase != null || _state.value.downloadPct != null) return
        val f = SdRunner.modelFile(getApplication())
        f.delete(); java.io.File(f.path + ".part").delete()
        refresh()
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
                .onFailure { _ -> _state.update { it.copy(error = team.sopa.boar.ui.I18n.t(getApplication(), "native.labs.image.downloadFailed")) } }
            _state.update { it.copy(downloadPct = null) }
            refresh()
        }
    }

    fun setStyle(s: ImageLab.Style) = _state.update { it.copy(style = s) }
    fun setSize(c: ImageLab.Canvas) = _state.update { it.copy(size = c) }
    /** Shows [f] and puts back what it was drawn from (style, size, seed) when it has a recipe; the screen fills the subject. */
    fun show(f: File) {
        val r = ImageLab.recipeOf(f)
        _state.update { s ->
            s.copy(image = f, seconds = null, error = null, subject = r?.subject ?: s.subject, seed = r?.seed,
                style = r?.style?.let { k -> ImageLab.Style.entries.firstOrNull { it.key == k } } ?: s.style,
                size = r?.size?.let { ImageLab.Canvas.of(it) } ?: s.size)
        }
    }

    fun setSeedLocked(on: Boolean) = _state.update { it.copy(seedLocked = on && it.seed != null) }

    fun generate(subject: String) {
        if (subject.isBlank() || _state.value.phase != null || SdRunner.engine(getApplication()) == null) return
        val s = _state.value
        val replacing = s.image?.takeIf { s.subject == subject.trim() && regenerating }
        regenerating = false
        // The old picture goes: its square shows the drawing in progress until the new one arrives.
        _state.update { it.copy(phase = "loading", error = null, seconds = null, image = null) }
        job = viewModelScope.launch {
            runCatching { SdRunner.draw(getApplication(), subject, s.style, s.size, seed = s.seed.takeIf { s.seedLocked }) { ph -> _state.update { it.copy(phase = ph) } } }
                .onSuccess { d ->
                    // Regenerate: the new picture takes the old one's place, which is deleted, not kept in the gallery.
                    replacing?.takeIf { it != d.file }?.let { ImageLab.deleteDrawing(it) }
                    _state.update { it.copy(phase = null, image = d.file, seconds = d.seconds, subject = subject.trim(), seed = d.seed) }
                }
                .onFailure { e -> if (e !is kotlinx.coroutines.CancellationException) _state.update { it.copy(phase = null, error = team.sopa.boar.ui.I18n.t(getApplication(), "native.labs.image.drawFailed")) } }
            refresh()
        }
    }

    /** The style and size Illustrate (in the chat) and a new Image Gen session use. */
    fun setDefaults() {
        val s = _state.value
        ImageLab.setDefaultStyle(s.style)
        ImageLab.setDefaultCanvas(s.size)
        _state.update { it.copy(defaultStyle = s.style, defaultSize = s.size) }
    }

    fun cancel() { SdRunner.cancel(); job?.cancel(); _state.update { it.copy(phase = null) } }

    fun deleteAll(files: Collection<File>) {
        files.forEach { ImageLab.deleteDrawing(it) }
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
        ImageLab.deleteDrawing(f)
        if (_state.value.image == f) _state.update { it.copy(image = null, seconds = null) }
        refresh()
    }



}
