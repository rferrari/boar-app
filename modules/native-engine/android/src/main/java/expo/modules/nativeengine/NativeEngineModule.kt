package expo.modules.nativeengine

import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Runs one bundled inference engine (an executable shipped as lib<name>.so, the only kind of
 * file Android extracts into nativeLibraryDir and lets an app execute) as a child process and
 * streams its stdout to JS. No JNI: the engines are separate programs with their own stdin/
 * stdout protocols, parsed in JS (src/inference/externalEngines.ts).
 *
 * stdout is split into lines, except colibri's "DATA <id> <size>" frames, whose payload is
 * <size> raw bytes that may contain newlines: those are read by byte count and delivered as one
 * { kind: "data" } event.
 */
class NativeEngineModule : Module() {
  private var process: Process? = null
  private var stdin: OutputStream? = null
  private var generation = 0

  override fun definition() = ModuleDefinition {
    Name("NativeEngine")

    Events("onOutput", "onExit")

    Function("nativeLibraryDir") {
      appContext.reactContext?.applicationInfo?.nativeLibraryDir ?: ""
    }

    // Size of a file, or of a directory's files added up; -1 when it's missing or unreadable.
    // External models live outside app storage (placed with adb), where expo-file-system can't look.
    Function("pathSize") { path: String ->
      val f = File(path)
      when {
        !f.exists() || !f.canRead() -> -1.0
        f.isDirectory -> (f.listFiles() ?: emptyArray()).filter { it.isFile }.sumOf { it.length() }.toDouble()
        else -> f.length().toDouble()
      }
    }

    // CPU feature flags (from /proc/cpuinfo) and each core's highest frequency in kHz, so JS can
    // tell whether an engine build's instructions are supported and which cores are the fast ones.
    Function("cpuInfo") {
      val features = try {
        File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Features") }?.substringAfter(':')?.trim() ?: ""
      } catch (_: Exception) { "" }
      val maxFreq = (0 until Runtime.getRuntime().availableProcessors()).map { cpu ->
        try {
          File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toInt()
        } catch (_: Exception) { 0 }
      }
      mapOf("features" to features, "maxFreqKHz" to maxFreq)
    }

    Function("isRunning") {
      process?.isAlive == true
    }

    AsyncFunction("start") { binary: String, args: List<String>, env: Map<String, String> ->
      stopNow()
      val dir = appContext.reactContext?.applicationInfo?.nativeLibraryDir
        ?: throw IllegalStateException("no application context")
      val exe = File(dir, binary)
      if (!exe.exists()) throw IllegalStateException("engine not bundled: ${exe.path}")
      val pb = ProcessBuilder(listOf(exe.path) + args)
      pb.environment()["LD_LIBRARY_PATH"] = "$dir:/system/lib64:/vendor/lib64"
      env.forEach { (k, v) -> pb.environment()[k] = v }
      val p = pb.start()
      val gen = ++generation
      process = p
      stdin = p.outputStream
      Thread({ readStdout(p.inputStream, gen) }, "engine-stdout").start()
      Thread({ readStderr(p.errorStream, gen) }, "engine-stderr").start()
      Thread({
        val code = try { p.waitFor() } catch (e: InterruptedException) { -1 }
        if (gen == generation) sendEvent("onExit", mapOf("code" to code))
      }, "engine-wait").start()
      true
    }

    Function("write") { text: String ->
      val out = stdin ?: throw IllegalStateException("engine not running")
      out.write(text.toByteArray(Charsets.UTF_8))
      out.flush()
    }

    AsyncFunction("stop") {
      stopNow()
    }

    OnDestroy {
      stopNow()
    }
  }

  private fun stopNow() {
    val p = process ?: return
    process = null
    stdin = null
    generation++
    try { p.outputStream.close() } catch (_: Exception) {}
    p.destroy()
    try {
      if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
    } catch (_: InterruptedException) {}
  }

  private fun readStdout(stream: InputStream, gen: Int) {
    val input = BufferedInputStream(stream)
    val line = ByteArrayOutputStream()
    try {
      while (true) {
        val b = input.read()
        if (b < 0) break
        if (b != '\n'.code) { line.write(b); continue }
        val text = line.toString("UTF-8")
        line.reset()
        val frame = DATA_HEADER.matchEntire(text)
        if (frame != null) {
          val size = frame.groupValues[2].toInt()
          val payload = ByteArray(size)
          var read = 0
          while (read < size) {
            val n = input.read(payload, read, size - read)
            if (n < 0) break
            read += n
          }
          input.read() // the frame's trailing newline
          if (gen == generation) sendEvent("onOutput", mapOf(
            "kind" to "data", "id" to frame.groupValues[1], "text" to String(payload, 0, read, Charsets.UTF_8)))
        } else if (gen == generation) {
          sendEvent("onOutput", mapOf("kind" to "line", "text" to text))
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "stdout reader stopped: ${e.message}")
    }
  }

  private fun readStderr(stream: InputStream, gen: Int) {
    try {
      stream.bufferedReader().forEachLine { l ->
        Log.i(TAG, l)
        if (gen == generation) sendEvent("onOutput", mapOf("kind" to "stderr", "text" to l))
      }
    } catch (_: Exception) {}
  }

  companion object {
    const val TAG = "BOAR-Engine"
    private val DATA_HEADER = Regex("DATA (\\S+) (\\d+)(?: .*)?")
  }
}
