package team.sopa.boar.labs.designer

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
import team.sopa.boar.engine.labs.DesignerCanvas
import team.sopa.boar.engine.labs.DesignerCrop
import java.io.File

/**
 * Labs → Image: one illustration from a subject and a style, with stable-diffusion.cpp's sd-cli and SDXS-512
 * (one step). sd-cli ships as libsdcli.so (scripts/build-sd-cli.sh) and runs as its own process: its ggml can't
 * clash with the chat engine's, a crash or an out-of-memory kill takes only the image down, and its memory is
 * freed when it exits.
 */
object ImageLab {
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

    /**
     * A drawing's size in pixels. New drawings are 512x512 (BOAR-88); an older one or a crop has its own, read from its
     * file name. Only sd-cli, the file name and the recipe see the numbers: the screen never shows them.
     */
    data class Canvas(val w: Int, val h: Int) {
        val size: DesignerCanvas.Size get() = DesignerCanvas.Size(w, h)
        /** "512" for a square (the key older saves and times used), "384x512" otherwise. */
        val key: String get() = size.key
        companion object {
            /** Any saved key, as it is. */
            fun of(key: String?): Canvas? = DesignerCanvas.parse(key)?.let { Canvas(it.w, it.h) }
            /** A saved key as the size Designer draws at now: 512 (DesignerCanvas.migrate). */
            fun offered(key: String?): Canvas? = DesignerCanvas.migrate(key)?.let { Canvas(it.w, it.h) }
        }
    }

    /** What Designer draws at: 512x512 only, the model's own size (BOAR-88). */
    val CANVASES = DesignerCanvas.OFFERED.map { Canvas(it.w, it.h) }

    /** The size a saved drawing was drawn at, from its file name ("…-512.png", "…-384x512.png"); null for an odd name. */
    fun canvasOf(image: File): Canvas? = Regex("-(\\d+(?:x\\d+)?)\\.png$").find(image.name)?.groupValues?.get(1)?.let { Canvas.of(it) }

    /**
     * The styles chosen on Oct 14 from 22 drawn on the X6 (same subject and seed, 384 px; docs/…image-gen-styles):
     * 8 chips in two rows. The subject goes where {s} is. Dropped: textbook, vector, watercolor, pencil (look-alikes of
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

    /** The user's pick ("★ Default"), else engraving. */
    fun defaultStyle(): Style = DesignerCanvas.loadDefault(team.sopa.boar.labs.LabPrefs::string).first?.let { k -> Style.entries.firstOrNull { it.key == k } } ?: Style.ENGRAVING

    /** The size every drawing is made at: 512 (a saved 640 or 384x512 reads as 512, DesignerCanvas.migrate). */
    fun defaultCanvas(): Canvas = DesignerCanvas.loadDefault(team.sopa.boar.labs.LabPrefs::string).second.let { Canvas(it.w, it.h) }

    /** The style picked in Designer and "Remember" in Illustrate: the default for both. */
    fun setDefaults(style: Style) = DesignerCanvas.saveDefault(team.sopa.boar.labs.LabPrefs::putString, style.key)

    /** Reset in Illustrate's sheet: back to Engraving. */
    fun resetDefaults() = setDefaults(Style.ENGRAVING)

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
    /** The size the next drawing is made at: 512 (BOAR-88). */
    val size: ImageLab.Canvas = ImageLab.CANVASES.first(),
    /** "loading", "drawing", "decoding" while sd-cli runs; null when idle. */
    val phase: String? = null,
    val image: File? = null,
    val seconds: Double? = null,
    val error: String? = null,
    val gallery: List<File> = emptyList(),
    /** The latest chat's title, offered as a subject. */
    val lastChat: String? = null,
    /** What the shown picture was drawn from, for Draw again. */
    val subject: String? = null,
    /** The shown picture's seed (kept for its recipe; the seed lock is hidden, adam Nov 13). */
    val seed: Int? = null,
    val seedLocked: Boolean = false,
    /** The shown picture's recipe (its style and size for the caption and the "i" sheet); null for an older drawing. */
    val recipe: ImageLab.Recipe? = null,
    /** "4 variations": the ones drawn so far, shown as a 2×2 on the canvas while the run lasts and after; null otherwise. */
    val variations: List<File>? = null,
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
        _state.value.image?.takeIf { !it.isFile }?.let { _state.update { s -> s.copy(image = null, seconds = null, subject = null, recipe = null) } }
        _state.value.variations?.let { v -> _state.update { s -> s.copy(variations = v.filter { it.isFile }.ifEmpty { null }) } }
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

    /**
     * Every Style pick is the default at once (adam, Nov 14: "lets hide this default, and the default
     * become [the] setup [the] user did"): the next visit and the one-tap 🎨 Illustrate use the last pick. Showing an old
     * drawing (show) puts its recipe on the canvas without changing the default.
     */
    fun setStyle(s: ImageLab.Style) { _state.update { it.copy(style = s) }; setDefaults() }

    /**
     * Shows [f] and puts back what it was drawn from (style, seed) when it has a recipe; the screen fills
     * the subject.
     */
    fun show(f: File) {
        val r = ImageLab.recipeOf(f)
        _state.update { s ->
            s.copy(image = f, seconds = null, error = null, subject = r?.subject ?: s.subject, seed = r?.seed, recipe = r, variations = null,
                style = r?.style?.let { k -> ImageLab.Style.entries.firstOrNull { it.key == k } } ?: s.style,
                size = (r?.size ?: ImageLab.canvasOf(f)?.key)?.let { ImageLab.Canvas.offered(it) } ?: s.size)
        }
    }

    fun setSeedLocked(on: Boolean) = _state.update { it.copy(seedLocked = on && it.seed != null) }

    /** What the user typed is checked too (ImageSafety), on every way to draw: refused with a plain line, nothing drawn. */
    private fun refused(subject: String): Boolean {
        if (!team.sopa.boar.engine.routing.ImageSafety.blocked(subject)) return false
        _state.update { it.copy(error = team.sopa.boar.ui.I18n.t(getApplication(), "native.labs.image.refused")) }
        return true
    }

    fun generate(subject: String) {
        if (subject.isBlank() || _state.value.phase != null || SdRunner.engine(getApplication()) == null) return
        if (refused(subject)) { regenerating = false; return }
        val s = _state.value
        // A crop's copy is never replaced: Draw again on it draws a new 512 picture and keeps the crop (BOAR-88).
        val replacing = s.image?.takeIf { s.subject == subject.trim() && regenerating && !DesignerCrop.isCrop(it.name) }
        regenerating = false
        // The old picture goes: its frame shows the drawing in progress until the new one arrives.
        _state.update { it.copy(phase = "loading", error = null, seconds = null, image = null, variations = null) }
        job = viewModelScope.launch {
            runCatching { SdRunner.draw(getApplication(), subject, s.style, s.size, seed = s.seed.takeIf { s.seedLocked }) { ph -> _state.update { it.copy(phase = ph) } } }
                .onSuccess { d ->
                    // Draw again: the new picture takes the old one's place, which is deleted, not kept in the gallery.
                    replacing?.takeIf { it != d.file }?.let { ImageLab.deleteDrawing(it) }
                    _state.update { it.copy(phase = null, image = d.file, seconds = d.seconds, subject = subject.trim(), seed = d.seed, recipe = ImageLab.recipeOf(d.file)) }
                }
                .onFailure { e -> if (e !is kotlinx.coroutines.CancellationException) _state.update { it.copy(phase = null, error = team.sopa.boar.ui.I18n.t(getApplication(), "native.labs.image.drawFailed")) } }
            refresh()
        }
    }

    /**
     * "4 variations" in the "i" sheet: the shown picture's words, style and size, four new seeds, drawn one after another
     * (DesignerCanvas.variations). Each waits for enough free memory (Illustrator's gate); the first failure stops the run
     * and what was drawn stays, in the gallery and on the canvas. Stop and Back cancel it the same way.
     */
    fun variations() {
        val s = _state.value
        val subject = s.subject ?: return
        if (s.phase != null || SdRunner.engine(getApplication()) == null || refused(subject)) return
        val style = s.recipe?.style?.let { k -> ImageLab.Style.entries.firstOrNull { it.key == k } } ?: s.style
        val size = s.image?.let { ImageLab.canvasOf(it) }?.key?.let { ImageLab.Canvas.offered(it) } ?: s.size
        val app = getApplication<Application>()
        _state.update { it.copy(phase = "loading", error = null, variations = emptyList()) }
        job = viewModelScope.launch {
            val out = DesignerCanvas.variations(
                enoughMemory = { Illustrator.enoughMemory(app) },
                onDrawn = { f: File -> _state.update { it.copy(variations = it.variations.orEmpty() + f) }; refresh() },
            ) { _ -> SdRunner.draw(app, subject, style, size) { ph -> _state.update { it.copy(phase = ph) } }.file }
            val error = when (out.stop) {
                DesignerCanvas.Stop.DONE -> null
                DesignerCanvas.Stop.FAILED -> team.sopa.boar.ui.I18n.t(app, "native.labs.image.drawFailed")
                DesignerCanvas.Stop.LOW_MEMORY -> team.sopa.boar.ui.I18n.t(app, "native.labs.image.lowMemory")
            }
            _state.update { it.copy(phase = null, error = error, variations = it.variations?.ifEmpty { null }) }
            refresh()
        }
    }

    /** The style Illustrate (in the chat) and a new Designer session use: saved on every pick. */
    fun setDefaults() {
        val s = _state.value
        ImageLab.setDefaults(s.style)
        _state.update { it.copy(defaultStyle = s.style, defaultSize = s.size) }
    }

    /** Stop (the Draw button while drawing) and Back: stops sd-cli; variations already drawn stay. */
    fun cancel() { SdRunner.cancel(); job?.cancel(); _state.update { it.copy(phase = null, variations = it.variations?.ifEmpty { null }) } }

    fun deleteAll(files: Collection<File>) {
        files.forEach { ImageLab.deleteDrawing(it) }
        if (_state.value.image in files) _state.update { it.copy(image = null, seconds = null, recipe = null) }
        _state.value.variations?.let { v -> _state.update { s -> s.copy(variations = (v - files.toSet()).ifEmpty { null }) } }
        refresh()
    }

    private var regenerating = false

    /** Draw again: the shown picture's words, style and size, and it replaces it. */
    fun regenerate() {
        val s = _state.value
        val subject = s.subject ?: return
        if (s.phase != null) return
        s.recipe?.let { r ->
            _state.update { st -> st.copy(style = ImageLab.Style.entries.firstOrNull { it.key == r.style } ?: st.style, size = ImageLab.Canvas.offered(r.size) ?: st.size) }
        }
        regenerating = true
        generate(subject)
    }

    /**
     * Crop (BOAR-88): [r] of [f], in its own pixels, saved as a COPY in the same folder (so it is in the gallery and can go
     * to Press like any drawing), with the original's recipe beside it; the original stays. The copy is shown after.
     */
    fun crop(f: File, r: DesignerCrop.Rect) {
        if (_state.value.phase != null) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val copy = withContext(Dispatchers.IO) {
                runCatching {
                    val src = android.graphics.BitmapFactory.decodeFile(f.path) ?: error("unreadable")
                    try {
                        val cut = android.graphics.Bitmap.createBitmap(src, r.x, r.y, r.w, r.h)
                        val out = File(f.parentFile, DesignerCrop.fileName(System.currentTimeMillis(), ImageLab.recipeOf(f)?.style, r))
                        out.outputStream().use { cut.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        if (cut !== src) cut.recycle()
                        ImageLab.recipeOf(f)?.let { ImageLab.writeRecipe(out, it.copy(size = "${r.w}x${r.h}")) }
                        out
                    } finally { src.recycle() }
                }.getOrNull()
            }
            refresh()
            if (copy != null) show(copy) else _state.update { it.copy(error = team.sopa.boar.ui.I18n.t(app, "native.labs.image.cropFailed")) }
        }
    }

    fun delete(f: File) {
        ImageLab.deleteDrawing(f)
        if (_state.value.image == f) _state.update { it.copy(image = null, seconds = null, recipe = null) }
        refresh()
    }
}
