package team.sopa.boar.labs.image

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
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
import team.sopa.boar.engine.CpuVariants
import team.sopa.boar.engine.models.AssetKind
import team.sopa.boar.engine.models.AssetStore
import team.sopa.boar.engine.models.CatalogAsset
import java.io.File
import kotlin.random.Random

/**
 * Labs → Image: one illustration from a subject and a style, with stable-diffusion.cpp's sd-cli and SDXS-512
 * (one step). sd-cli ships as libsdcli.so (scripts/build-sd-cli.sh) and runs as its own process: its ggml can't
 * clash with the chat engine's, a crash or an out-of-memory kill takes only the image down, and its memory is
 * freed when it exits.
 */
object ImageLab {
    /** Measured on the X6 Pro, Oct 13 2026 (CPU, 4 fast cores): 384 px ~5.5 s, 512 px ~9.5 s; the labels promise ~15 s and ~20 s, untested on other phones. 256 px breaks the shapes. The first is the default. */
    val SIZES = listOf(384, 512)

    /** The styles from the Oct 13 test; the subject goes where {s} is. */
    enum class Style(val key: String, val template: String) {
        ENGRAVING("engraving", "{s}, vintage natural history engraving, black ink, white background"),
        TEXTBOOK("textbook", "{s}, textbook illustration, clean line art with flat watercolor, white background"),
        VECTOR("vector", "{s}, flat vector illustration, simple shapes, bold colors"),
        SCIENTIFIC("scientific", "scientific illustration of {s}, encyclopedia plate, detailed ink drawing, white background");

        fun prompt(subject: String) = template.replace("{s}", subject.trim())
    }

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
    private val outDir = File(app.filesDir, "labs/images")
    private val _state = MutableStateFlow(ImageLabState())
    val state: StateFlow<ImageLabState> = _state
    private var job: Job? = null
    @Volatile private var process: Process? = null

    init {
        refresh()
        viewModelScope.launch {
            val title = withContext(Dispatchers.IO) { runCatching { services.chats.listSessions().firstOrNull { it.title.isNotBlank() }?.title }.getOrNull() }
            _state.update { it.copy(lastChat = title?.trim()?.trimEnd('?', '.', '!')?.take(80)) }
        }
    }

    /** sd-cli built for i8mm when the CPU has it (the X6), else the armv8.2 + dotprod build (the F3). */
    private fun engine(): File? {
        val dir = File(getApplication<Application>().applicationInfo.nativeLibraryDir)
        val i8mm = File(dir, "libsdcli_i8mm.so")
        val base = File(dir, "libsdcli.so")
        return if (i8mm.exists() && "i8mm" in CpuVariants.readFeatures()) i8mm else base.takeIf { it.exists() }
    }

    private fun refresh() {
        val present = store.status(ImageLab.MODEL).let { it.present && it.sizeOnDiskBytes == ImageLab.MODEL.sizeBytes }
        val gallery = outDir.listFiles { f -> f.extension == "png" }.orEmpty().sortedByDescending { it.lastModified() }
        _state.update { it.copy(engineFound = engine() != null, modelPresent = present, gallery = gallery) }
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
        val bin = engine() ?: return
        if (subject.isBlank() || _state.value.phase != null) return
        val s = _state.value
        val seed = Random.nextInt(1, Int.MAX_VALUE)
        outDir.mkdirs()
        val out = File(outDir, "${System.currentTimeMillis()}-${s.style.key}-${s.size}.png")
        _state.update { it.copy(phase = "loading", error = null, seconds = null) }
        job = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                // The 4 fast cores on an 8-core phone, as measured; elsewhere the scheduler picks.
                val pin = if (Runtime.getRuntime().availableProcessors() == 8 && File("/system/bin/taskset").exists()) listOf("/system/bin/taskset", "f0") else emptyList()
                val cmd = pin + listOf(bin.path, "-m", store.fileOf(ImageLab.MODEL).path, "-p", s.style.prompt(subject),
                    "--cfg-scale", "1", "--steps", "1", "-W", "${s.size}", "-H", "${s.size}", "-t", "4", "--seed", "$seed", "-o", out.path)
                runCatching {
                    val p = ProcessBuilder(cmd).redirectErrorStream(true).start().also { process = it }
                    val tail = ArrayDeque<String>()
                    var seconds: Double? = null
                    p.inputStream.bufferedReader().forEachLine { line ->
                        when {
                            "sampling" in line && "completed" !in line -> _state.update { it.copy(phase = "drawing") }
                            "sampling completed" in line -> _state.update { it.copy(phase = "decoding") }
                            "generate_image completed in" in line -> seconds = Regex("in ([0-9.]+)s").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                        }
                        tail.addLast(line); if (tail.size > 6) tail.removeFirst()
                    }
                    val code = p.waitFor()
                    if (code != 0 || !out.exists()) error("sd-cli exit $code: ${tail.joinToString(" | ").take(400)}")
                    seconds
                }.also { process = null }
            }
            result.onSuccess { sec -> _state.update { it.copy(phase = null, image = out, seconds = sec) } }
                .onFailure { e -> out.delete(); _state.update { it.copy(phase = null, error = if (job?.isCancelled == true) null else e.message) } }
            refresh()
        }
    }

    fun cancel() { process?.destroy(); job?.cancel(); _state.update { it.copy(phase = null) } }

    fun delete(f: File) {
        f.delete()
        if (_state.value.image == f) _state.update { it.copy(image = null, seconds = null) }
        refresh()
    }

    /** The share sheet for one image, through a copy in cache/exports (the FileProvider's only path). */
    fun shareIntent(f: File): Intent {
        val app = getApplication<Application>()
        val copy = File(File(app.cacheDir, "exports").apply { mkdirs() }, "boar-${f.name}")
        f.copyTo(copy, overwrite = true)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", copy)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, null)
    }

    override fun onCleared() { process?.destroy() }
}
