package team.sopa.boar.bench

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import team.sopa.boar.engine.labs.Vitals
import team.sopa.boar.ui.theme.Boar
import java.io.File
import java.util.Locale

/**
 * The phone's vitals, compact, above the bench's log (adam, Nov 14: "display vitals dashboard during engine bench? a
 * compact version. above logs"): heat with the thermal state, its peak and a trend; battery; each core's clock as a bar;
 * free RAM and BOAR's own memory. Read every second, as the Vitals plugin does, with no plugin needed (the bench is in
 * every build, the plugin is not). English only, like the rest of the bench (adb only).
 */
@Composable
internal fun BenchVitals() {
    val c = Boar.colors
    val type = Boar.type
    val ctx = LocalContext.current
    var now by remember { mutableStateOf<BenchSample?>(null) }
    var temps by remember { mutableStateOf(listOf<Double>()) }
    LaunchedEffect(Unit) {
        while (true) {
            val s = withContext(Dispatchers.IO) { runCatching { readSample(ctx) }.getOrNull() }
            if (s != null) { now = s; s.tempC?.let { temps = (temps + it).takeLast(HISTORY) } }
            delay(1_000)
        }
    }
    val s = now ?: return
    Column(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.tempC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "— °C", style = type.titleSm, color = heatColor(s.tempC), fontWeight = FontWeight.ExtraBold)
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(thermalWord(s.thermal), temps.maxOrNull()?.let { String.format(Locale.US, "peak %.1f", it) }).joinToString(" · "),
                    style = type["mono.xs"], color = c.textMuted, maxLines = 1)
                Spark(temps, Modifier.fillMaxWidth().height(14.dp))
            }
            Text((s.batteryPct?.let { "$it%" } ?: "—") + if (s.charging) " ⚡" else "", style = type["mono.xs"], color = c.textSecondary, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // One bar per core: its clock now against its own maximum.
            Row(Modifier.height(24.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                s.coresMhz.forEachIndexed { i, mhz ->
                    val max = s.coresMaxMhz.getOrNull(i)
                    val frac = if (mhz != null && max != null && max > 0) mhz.toFloat() / max else 0f
                    Box(Modifier.width(6.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.bgSubtle), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(frac.coerceIn(0.04f, 1f)).background(if (mhz == null) c.textDim else c.emerald400))
                    }
                }
            }
            val avg = s.coresMhz.filterNotNull().takeIf { it.isNotEmpty() }?.average()
            // Two lines, each whole (adam, Nov 14: "only a GB goes to second line… use 2 lines for cpu and ram"): the CPU and
            // BOAR's own memory, then the phone's free memory said in words ("RAM 1.0 / 7.3" read as a ratio).
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(avg?.let { String.format(Locale.US, "CPU %.1f GHz", it / 1000) }, String.format(Locale.US, "BOAR %s GB", gb(s.boarBytes))).joinToString(" · "),
                    style = type["mono.xs"], color = c.textSecondary, maxLines = 1)
                Text(String.format(Locale.US, "RAM %s GB free of %s GB", gb(s.ramFree), gb(s.ramTotal)), style = type["mono.xs"], color = c.textSecondary, maxLines = 1)
            }
        }
    }
}

internal data class BenchSample(
    val tempC: Double?, val thermal: Int?, val coresMhz: List<Int?>, val coresMaxMhz: List<Int?>,
    val ramFree: Long, val ramTotal: Long, val boarBytes: Long, val batteryPct: Int?, val charging: Boolean,
)

private const val HISTORY = 120

private fun readSample(ctx: Context): BenchSample {
    val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val mem = ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
    val cores = Vitals.coreIds(File("/sys/devices/system/cpu").list().orEmpty().toList())
    fun mhz(core: Int, file: String) = Vitals.mhzOf(runCatching { File("/sys/devices/system/cpu/cpu$core/cpufreq/$file").readText() }.getOrNull())
    return BenchSample(
        tempC = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.let(Vitals::batteryTempC),
        thermal = if (Build.VERSION.SDK_INT >= 29) (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else null,
        coresMhz = cores.map { mhz(it, "scaling_cur_freq") },
        coresMaxMhz = cores.map { mhz(it, "cpuinfo_max_freq") },
        ramFree = mem.availMem, ramTotal = mem.totalMem,
        boarBytes = team.sopa.boar.app.AppMemory.residentBytes(),
        batteryPct = Vitals.batteryPercent(battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1, battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1),
        charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
    )
}

private fun gb(b: Long) = String.format(Locale.US, "%.1f", b / 1073741824.0)

private fun thermalWord(status: Int?): String? = when (status) {
    null -> null
    PowerManager.THERMAL_STATUS_NONE -> "not throttling"
    PowerManager.THERMAL_STATUS_LIGHT -> "light throttling"
    PowerManager.THERMAL_STATUS_MODERATE -> "moderate throttling"
    else -> "severe throttling"
}

@Composable
private fun heatColor(t: Double?): Color = when (Vitals.heat(t)) {
    Vitals.Heat.UNKNOWN -> Boar.colors.textDim
    Vitals.Heat.COOL -> Boar.colors.textAccentEmerald
    Vitals.Heat.WARM -> Color(0xFFF5B83D)
    Vitals.Heat.HOT -> Boar.colors.crimson500
}

@Composable
private fun Spark(values: List<Double>, modifier: Modifier) {
    val line = Boar.colors.emerald400
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min() - 0.5
        val hi = values.max() + 0.5
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (HISTORY - 1)
            val y = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 2.5f))
    }
}
