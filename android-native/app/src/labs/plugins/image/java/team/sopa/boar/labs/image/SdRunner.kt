package team.sopa.boar.labs.image

import kotlinx.coroutines.launch
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import team.sopa.boar.engine.CpuVariants
import java.io.File
import kotlin.random.Random

/**
 * sd-cli as its own process, one drawing at a time (the lab screen and Illustrate share it). Its ggml can't clash
 * with the chat engine's, a crash or an out-of-memory kill takes only the drawing down, and its memory is freed
 * when it exits. Images go to files/labs/images, where Image Gen and the Printer find them.
 */
object SdRunner {
    private val lock = Mutex()
    @Volatile private var process: Process? = null

    /** sd-cli built for i8mm when the CPU has it (the X6), else the armv8.2 + dotprod build (the F3). */
    fun engine(ctx: Context): File? {
        val dir = File(ctx.applicationInfo.nativeLibraryDir)
        val i8mm = File(dir, "libsdcli_i8mm.so")
        val base = File(dir, "libsdcli.so")
        return if (i8mm.exists() && "i8mm" in CpuVariants.readFeatures()) i8mm else base.takeIf { it.exists() }
    }

    fun modelFile(ctx: Context) = File(ctx.filesDir, ImageLab.MODEL.filename)

    fun ready(ctx: Context) = engine(ctx) != null && modelFile(ctx).length() == ImageLab.MODEL.sizeBytes

    fun outDir(ctx: Context) = File(ctx.filesDir, "labs/images")

    data class Drawn(val file: File, val seconds: Double?)

    /** Draws [subject] in [style]; [onPhase] gets "loading", "drawing", "decoding". Throws on failure. */
    suspend fun draw(ctx: Context, subject: String, style: ImageLab.Style, size: Int, onPhase: (String) -> Unit = {}): Drawn = lock.withLock {
        withContext(Dispatchers.IO) {
            val bin = engine(ctx) ?: error("no image engine in this build")
            val out = File(outDir(ctx).apply { mkdirs() }, "${System.currentTimeMillis()}-${style.key}-$size.png")
            onPhase("loading")
            // The 4 fast cores on an 8-core phone, as measured; elsewhere the scheduler picks.
            val pin = if (Runtime.getRuntime().availableProcessors() == 8 && File("/system/bin/taskset").exists()) listOf("/system/bin/taskset", "f0") else emptyList()
            val cmd = pin + listOf(bin.path, "-m", modelFile(ctx).path, "-p", style.prompt(subject), "--cfg-scale", "1", "--steps", "1",
                "-W", "$size", "-H", "$size", "-t", "4", "--seed", "${Random.nextInt(1, Int.MAX_VALUE)}", "-o", out.path)
            try {
                val p = ProcessBuilder(cmd).redirectErrorStream(true).start().also { process = it }
                val tail = ArrayDeque<String>()
                var seconds: Double? = null
                p.inputStream.bufferedReader().forEachLine { line ->
                    when {
                        "sampling" in line && "completed" !in line -> onPhase("drawing")
                        "sampling completed" in line -> onPhase("decoding")
                        "generate_image completed in" in line -> seconds = Regex("in ([0-9.]+)s").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                    }
                    tail.addLast(line); if (tail.size > 6) tail.removeFirst()
                }
                val code = p.waitFor()
                if (code != 0 || !out.exists()) { out.delete(); error("sd-cli exit $code: ${tail.joinToString(" | ").take(400)}") }
                Drawn(out, seconds)
            } finally {
                process = null
            }
        }
    }

    fun cancel() { process?.destroy() }
}
