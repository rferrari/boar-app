package team.sopa.boar.labs.engines

import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.Channel
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * One engine executable from an engine plugin's jniLibs (shipped as lib<name>.so, the only kind of file Android
 * extracts into the app's native library folder and lets it run), started as a child process. Ported from the
 * experimental-engines branch's native-engine module (modules/native-engine), which did the same for the Expo app.
 *
 * Its stdout arrives on [output] line by line, except colibri's "DATA <id> <size>" frames, whose payload is <size>
 * raw bytes that may hold newlines: those are read by byte count and arrive as one [Output.Data]. stderr goes to
 * logcat (tag BoarLabs) and the last lines are kept for an error message.
 */
class EngineProcess private constructor(private val process: Process) {
    sealed interface Output {
        data class Line(val text: String) : Output
        data class Data(val id: String, val text: String) : Output
        data class Exit(val code: Int) : Output
    }

    val output = Channel<Output>(Channel.UNLIMITED)
    private val stdin: OutputStream = process.outputStream
    private val stderrTail = ArrayDeque<String>()

    val isAlive: Boolean get() = process.isAlive

    /** The last stderr lines, for an error message. */
    fun tail(): String = synchronized(stderrTail) { stderrTail.joinToString(" | ") }

    @Synchronized fun write(text: String) {
        stdin.write(text.toByteArray(Charsets.UTF_8))
        stdin.flush()
    }

    /** Closes stdin, then stops the process (forcibly after 3 s). */
    fun stop() {
        runCatching { stdin.close() }
        process.destroy()
        runCatching { if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly() }
    }

    private fun start() {
        Thread({ readStdout() }, "lab-engine-stdout").apply { isDaemon = true }.start()
        Thread({
            runCatching { process.errorStream.bufferedReader().forEachLine { l ->
                Log.i(TAG, l)
                synchronized(stderrTail) { stderrTail.addLast(l); if (stderrTail.size > 8) stderrTail.removeFirst() }
            } }
        }, "lab-engine-stderr").apply { isDaemon = true }.start()
        Thread({
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            output.trySend(Output.Exit(code))
        }, "lab-engine-wait").apply { isDaemon = true }.start()
    }

    private fun readStdout() {
        val input = BufferedInputStream(process.inputStream)
        val line = ByteArrayOutputStream()
        try {
            while (true) {
                val b = input.read()
                if (b < 0) break
                if (b != '\n'.code) { line.write(b); continue }
                val text = line.toString("UTF-8")
                line.reset()
                val frame = DATA_HEADER.matchEntire(text)
                if (frame == null) { output.trySend(Output.Line(text)); continue }
                val payload = ByteArray(frame.groupValues[2].toInt())
                var read = 0
                while (read < payload.size) {
                    val n = input.read(payload, read, payload.size - read)
                    if (n < 0) break
                    read += n
                }
                input.read() // the frame's trailing newline
                output.trySend(Output.Data(frame.groupValues[1], String(payload, 0, read, Charsets.UTF_8)))
            }
        } catch (e: Exception) {
            Log.w(TAG, "engine stdout stopped: ${e.message}")
        }
    }

    companion object {
        const val TAG = "BoarLabs"
        private val DATA_HEADER = Regex("DATA (\\S+) (\\d+)(?: .*)?")

        /** The executable bundled as [binary] (e.g. "libbmoe_cli.so"), or null when this build doesn't ship it. */
        fun executable(context: Context, binary: String): File? =
            File(context.applicationInfo.nativeLibraryDir, binary).takeIf { it.isFile }

        fun start(context: Context, binary: String, args: List<String>, env: Map<String, String> = emptyMap()): EngineProcess {
            val exe = executable(context, binary) ?: error("engine not in this build: $binary")
            val pb = ProcessBuilder(listOf(exe.path) + args)
            pb.environment()["LD_LIBRARY_PATH"] = "${exe.parent}:/system/lib64:/vendor/lib64"
            env.forEach { (k, v) -> pb.environment()[k] = v }
            return EngineProcess(pb.start()).also { it.start() }
        }
    }
}
