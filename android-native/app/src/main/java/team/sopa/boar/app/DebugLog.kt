package team.sopa.boar.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import team.sopa.boar.BuildConfig
import team.sopa.boar.engine.GenerationMetrics
import team.sopa.boar.engine.settings.AppSettings
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Settings → Advanced tweaks → Debug log: how BOAR used the phone, to chase a slow answer, a crash or a hot phone.
 * Only in builds with ALLOW_DEBUG_LOG=true (app/build.gradle.kts), and off until the user turns it on.
 *
 * One line per record in files/debug/ (two files of [MAX_BYTES] at most, the older one dropped): a sample of memory,
 * battery, heat and CPU speed every [SAMPLE_MS] (less often in the background), plus model loads, answer speed,
 * downloads, memory warnings and crashes. Numbers and ids only, never what was asked or answered. Nothing is sent:
 * the user saves the file (Save-as) and shares it themselves.
 */
object DebugLog {
    private const val MAX_BYTES = 2L * 1024 * 1024
    private const val SAMPLE_MS = 15_000L
    private const val SAMPLE_BACKGROUND_MS = 60_000L

    private val io = Executors.newSingleThreadExecutor { Thread(it, "BoarDebugLog").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    @Volatile private var app: Context? = null
    @Volatile private var settings: AppSettings? = null
    @Volatile private var foreground = false
    private var sampler: Job? = null

    /** The build has the switch. */
    val available: Boolean get() = BuildConfig.ALLOW_DEBUG_LOG

    val enabled: Boolean get() = available && settings?.debugLogEnabled == true

    private val dir get() = app?.let { File(it.filesDir, "debug") }
    private val current get() = dir?.let { File(it, "boar-debug.log") }
    private val previous get() = dir?.let { File(it, "boar-debug.1.log") }

    fun init(context: Context, s: AppSettings) {
        if (app != null || !available) return
        app = context.applicationContext
        settings = s
        s.onChange = ::setting
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            if (enabled) runCatching { write(line("crash", "thread" to t.name, "error" to describe(e))) } // synchronously: the process is ending
            prior?.uncaughtException(t, e)
        }
        if (enabled) start()
    }

    fun setEnabled(on: Boolean) {
        if (!available) return
        if (!on) event("log.off")
        settings?.debugLogEnabled = on
        if (on) start() else stopSampler()
    }

    fun foreground(on: Boolean) {
        foreground = on
        event(if (on) "app.foreground" else "app.background")
    }

    /** A memory warning from Android (onTrimMemory). */
    fun trimMemory(level: Int) = event("memory.trim", "level" to level, "rssMb" to AppMemory.residentBytes() / MB)

    fun modelLoad(id: String, ms: Long, ok: Boolean, threads: Int, nCtx: Int, repack: Boolean) =
        event("model.load", "id" to id, "ok" to ok, "ms" to ms, "threads" to threads, "ctx" to nCtx, "repack" to repack, "rssMb" to AppMemory.residentBytes() / MB,
            "nativeHeapMb" to android.os.Debug.getNativeHeapAllocatedSize() / MB, "javaHeapMb" to javaHeapMb())

    /** Settings that hold secrets or the user's own words: the change is logged, the value isn't. */
    private val REDACTED = setOf("pinWrapped", "pinSalt", "customSystemPrompt", "shareDeviceId")
    /** Counters the app updates by itself, not settings. */
    private val QUIET = setOf("truffleVault", "debugLogEnabled")

    /** A setting changed: key, old → new. An object (engineTweaks) is logged one changed field at a time. */
    private fun setting(key: String, old: kotlinx.serialization.json.JsonElement?, new: kotlinx.serialization.json.JsonElement?) {
        if (key in QUIET) return
        if (key in REDACTED) return event("setting", "key" to key, "change" to "redacted")
        val o = old as? kotlinx.serialization.json.JsonObject
        val n = new as? kotlinx.serialization.json.JsonObject
        if ((o != null || old == null) && (n != null || new == null) && (o != null || n != null)) {
            val a = o.orEmpty(); val b = n.orEmpty()
            (a.keys + b.keys).filter { a[it] != b[it] }.forEach { k -> event("setting", "key" to "$key.$k", "old" to (a[k] ?: "auto"), "new" to (b[k] ?: "auto")) }
        } else event("setting", "key" to key, "old" to (old?.toString()?.take(120) ?: "unset"), "new" to (new?.toString()?.take(120) ?: "unset"))
    }

    /** For settings outside settings.json (the labs' and vault's own switches). */
    fun settingChanged(key: String, old: Any?, new: Any?) { if (old != new) event("setting", "key" to key, "old" to (old ?: "unset"), "new" to (new ?: "unset")) }

    fun modelUnload(id: String?) = event("model.unload", "id" to id, "rssMb" to AppMemory.residentBytes() / MB, "nativeHeapMb" to android.os.Debug.getNativeHeapAllocatedSize() / MB)

    /** [nCtx]: the context window; how full it got shows a slowdown from a long chat. */
    fun generation(id: String, m: GenerationMetrics, nCtx: Int) = event(
        "gen", "id" to id, "ctxUsed" to m.promptTokens + m.generatedTokens, "ctxMax" to nCtx,
        "ctxPct" to if (nCtx > 0) (m.promptTokens + m.generatedTokens) * 100 / nCtx else null, "prompt" to m.promptTokens, "decoded" to m.promptTokensDecoded, "tokens" to m.generatedTokens,
        "ttftMs" to m.ttftMs.toInt(), "prefillTps" to "%.1f".format(Locale.US, m.prefillTokPerSec), "decodeTps" to "%.1f".format(Locale.US, m.decodeTokPerSec),
        "totalMs" to m.totalMs.toInt(), "stop" to m.stopReason, "error" to m.error,
    )

    /** A model or pack download: when it started, how long it took, and why it failed. */
    inline fun <T> download(id: String, sizeBytes: Long, block: () -> T): T {
        val t0 = android.os.SystemClock.elapsedRealtime()
        event("download.start", "id" to id, "mb" to sizeBytes / (1024 * 1024))
        try {
            return block().also { event("download.done", "id" to id, "ms" to android.os.SystemClock.elapsedRealtime() - t0) }
        } catch (e: Throwable) {
            event("download.fail", "id" to id, "ms" to android.os.SystemClock.elapsedRealtime() - t0, "error" to "${e.javaClass.simpleName}:${e.message?.take(120) ?: ""}")
            throw e
        }
    }

    /** Records an event with its fields (null fields are left out). */
    fun event(name: String, vararg fields: Pair<String, Any?>) {
        if (!enabled) return
        val l = line(name, *fields)
        io.execute { runCatching { write(l) } }
    }

    fun sizeBytes(): Long = listOfNotNull(previous, current).filter { it.isFile }.sumOf { it.length() }

    /** The older file, then the current one, into [out] (the user's Save-as). */
    fun copyTo(out: OutputStream) {
        val header = buildString {
            append("# BOAR debug log, saved ").append(synchronized(time) { time.format(Date()) }).append('\n')
            append("# ").append(device().filter { it.second != null }.joinToString(" ") { "${it.first}=${it.second}" }).append('\n')
            append("# settings ").append(settings?.snapshot().orEmpty().mapValues { (k, v) -> if (k in REDACTED) "\"redacted\"" else v.toString() }.filterKeys { it !in QUIET }).append('\n')
        }
        io.submit {
            out.write(header.toByteArray())
            listOfNotNull(previous, current).filter { it.isFile }.forEach { f -> f.inputStream().use { it.copyTo(out) } }
        }.get()
    }

    fun clear() {
        io.submit { listOfNotNull(previous, current).forEach { it.delete() } }.get()
        event("log.cleared")
    }

    private fun start() {
        event("log.on", *device().toTypedArray())
        synchronized(this) {
            if (sampler?.isActive == true) return
            sampler = scope.launch {
                while (isActive && enabled) {
                    event("sample", *sample().toTypedArray())
                    delay(if (foreground) SAMPLE_MS else SAMPLE_BACKGROUND_MS)
                }
            }
        }
    }

    private fun stopSampler() = synchronized(this) { sampler?.cancel(); sampler = null }

    private fun line(name: String, vararg fields: Pair<String, Any?>): String = buildString {
        append(synchronized(time) { time.format(Date()) }).append(' ').append(name)
        for ((k, v) in fields) if (v != null) append(' ').append(k).append('=').append(v.toString().replace(Regex("\\s+"), "_"))
        append('\n')
    }

    private fun write(l: String) {
        val f = current ?: return
        f.parentFile?.mkdirs()
        if (f.length() > MAX_BYTES) previous?.let { p -> p.delete(); f.renameTo(p) }
        f.appendText(l)
    }

    private fun device(): List<Pair<String, Any?>> = listOf(
        "app" to BuildConfig.VERSION_NAME, "device" to "${Build.MANUFACTURER}_${Build.MODEL}", "soc" to (if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null),
        "android" to Build.VERSION.SDK_INT, "cores" to Runtime.getRuntime().availableProcessors(), "engine" to team.sopa.boar.engine.BoarEngine.loadedVariant,
    )

    /** Memory, battery, heat and each core's current speed (MHz), read in a few milliseconds. */
    private fun sample(): List<Pair<String, Any?>> {
        val ctx = app ?: return emptyList()
        val mem = android.app.ActivityManager.MemoryInfo().also { ctx.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) }
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        fun khz(c: File, name: String) = runCatching { File(c, "cpufreq/$name").readText().trim().toLong() }.getOrNull()
        val cores = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }.orEmpty().sortedBy { it.name.removePrefix("cpu").toInt() }
        // A core whose allowed top speed is below what the chip can do: the phone is holding it back (heat or power).
        val capped = cores.filter { c -> val cap = khz(c, "scaling_max_freq"); val top = khz(c, "cpuinfo_max_freq"); cap != null && top != null && cap < top }
            .joinToString(",") { it.name.removePrefix("cpu") }
        val thermal = if (Build.VERSION.SDK_INT >= 29) pm?.currentThermalStatus else null
        if (capped != lastCapped || thermal != lastThermal) {
            if (lastCapped != null || lastThermal != null) event("thermal.change", "thermal" to "$lastThermal>$thermal", "cappedCores" to "${lastCapped?.ifEmpty { "none" }}>${capped.ifEmpty { "none" }}")
            lastCapped = capped; lastThermal = thermal
        }
        val gcCount = android.os.Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull()
        val gcMs = android.os.Debug.getRuntimeStat("art.gc.gc-time")?.toLongOrNull()
        val gc = if (gcCount != null && lastGcCount != null) gcCount - lastGcCount!! else null
        val gcTime = if (gcMs != null && lastGcMs != null) gcMs - lastGcMs!! else null
        lastGcCount = gcCount; lastGcMs = gcMs
        val freqs = cores.map { c -> khz(c, "scaling_cur_freq")?.div(1000) ?: 0 }
        return listOf(
            "fg" to foreground,
            "ramAvailMb" to mem.availMem / MB, "ramTotalMb" to mem.totalMem / MB, "lowMem" to mem.lowMemory,
            "rssMb" to AppMemory.residentBytes() / MB, "engineMb" to AppMemory.breakdown().engines.sumOf { it.bytes }.takeIf { it > 0 }?.let { it / MB }, "nativeHeapMb" to android.os.Debug.getNativeHeapAllocatedSize() / MB, "javaHeapMb" to javaHeapMb(),
            "gc" to gc, "gcMs" to gcTime, "blockingGc" to android.os.Debug.getRuntimeStat("art.gc.blocking-gc-count"),
            "battery" to bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            "charging" to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL),
            "battC" to battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10.0 },
            "currentMa" to bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.takeIf { it != Long.MIN_VALUE }?.let { it / 1000 },
            "thermal" to thermal, "cappedCores" to capped.ifEmpty { null },
            "headroom" to (if (Build.VERSION.SDK_INT >= 30) pm?.getThermalHeadroom(10)?.takeIf { !it.isNaN() }?.let { "%.2f".format(Locale.US, it) } else null),
            "freeMb" to ctx.filesDir.usableSpace / MB,
            "cpuMhz" to freqs.joinToString(","),
        )
    }

    /** The exception's type and where it was thrown; its message is cut short, it could quote anything. */
    private fun describe(e: Throwable): String =
        e.javaClass.name + ":" + (e.message?.take(120) ?: "") + " at " + e.stackTrace.take(6).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }

    private fun javaHeapMb() = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / MB }

    @Volatile private var lastCapped: String? = null
    @Volatile private var lastThermal: Int? = null
    @Volatile private var lastGcCount: Long? = null
    @Volatile private var lastGcMs: Long? = null

    private const val MB = 1024 * 1024
}
