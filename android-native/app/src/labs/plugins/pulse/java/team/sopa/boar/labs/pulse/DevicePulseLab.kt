package team.sopa.boar.labs.pulse

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import team.sopa.boar.app.AppMemory
import team.sopa.boar.engine.BoarEngine
import team.sopa.boar.engine.CpuVariants
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.io.File
import java.util.Locale
import team.sopa.boar.ui.theme.track

/**
 * Boar Vitals (was Device Pulse): what the phone feels while BOAR works. Once a second: battery temperature (with the last two minutes
 * as a line), Android's thermal status, each core's clock, free RAM and BOAR's own share, and the CPU features that
 * pick the engine build. Leave it open, then run a chat or an image in another lab and come back to see the heat.
 */
object DevicePulseLab : LabPlugin {
    override val id = "pulse"
    override val emoji = "🌡️"
    override val art = team.sopa.boar.R.drawable.truffle_synthetic
    override val status = LabStatus.LIVE

    private const val HISTORY = 120

    private data class Sample(
        val tempC: Double?,
        val thermal: Int?,
        val coresMhz: List<Int?>,
        val coresMaxMhz: List<Int?>,
        val ramFree: Long,
        val ramTotal: Long,
        val boar: AppMemory.Breakdown,
        val batteryPct: Int?,
        val charging: Boolean,
    )

    private fun read(ctx: Context): Sample {
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.div(10.0)
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val thermal = if (Build.VERSION.SDK_INT >= 29) (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else null
        val mem = ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        // The phone's own core ids (cpu0…cpuN, not always without gaps), not the count of online cores.
        val cores = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }.orEmpty()
            .map { it.name.removePrefix("cpu").toInt() }.sorted()
        fun khz(core: Int, file: String) = runCatching { File("/sys/devices/system/cpu/cpu$core/cpufreq/$file").readText().trim().toInt() / 1000 }.getOrNull()
        return Sample(
            tempC = temp,
            thermal = thermal,
            coresMhz = cores.map { khz(it, "scaling_cur_freq") },
            coresMaxMhz = cores.map { khz(it, "cpuinfo_max_freq") },
            ramFree = mem.availMem,
            ramTotal = mem.totalMem,
            boar = AppMemory.breakdown(),
            batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = plugged,
        )
    }

    @Composable
    override fun Content() {
        val c = Boar.colors
        val ctx = LocalContext.current
        var now by remember { mutableStateOf<Sample?>(null) }
        var temps by remember { mutableStateOf(listOf<Double>()) }
        LaunchedEffect(Unit) {
            while (true) {
                val s = withContext(Dispatchers.IO) { read(ctx) }
                now = s
                s.tempC?.let { temps = (temps + it).takeLast(HISTORY) }
                delay(1000)
            }
        }
        val s = now ?: return
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(t("native.labs.pulse.heat")) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s.tempC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "—", style = Boar.type.headline, color = heatColor(s.tempC), fontWeight = FontWeight.ExtraBold)
                    Text(thermalLabel(s.thermal), style = Boar.type.caption, color = c.textMuted, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spark(temps, Modifier.fillMaxWidth().height(56.dp))
                Text(t("native.labs.pulse.heatNote", "min" to (temps.minOrNull()?.let { String.format(Locale.US, "%.1f", it) } ?: "—"),
                    "max" to (temps.maxOrNull()?.let { String.format(Locale.US, "%.1f", it) } ?: "—"), "seconds" to temps.size), style = Boar.type["mono.xs"], color = c.textDim)
            }
            Card(t("native.labs.pulse.cores")) {
                s.coresMhz.forEachIndexed { i, mhz ->
                    val max = s.coresMaxMhz.getOrNull(i)
                    Meter("cpu$i", mhz?.let { "$it MHz" } ?: "—", if (mhz != null && max != null && max > 0) mhz.toFloat() / max else 0f, c.cyan400)
                }
            }
            Card(t("native.labs.pulse.memory")) {
                val used = s.ramTotal - s.ramFree
                Meter(t("native.labs.pulse.ramPhone"), "${gb(used)} / ${gb(s.ramTotal)} GB", used.toFloat() / s.ramTotal, c.emerald400)
                // BOAR is the app and every engine it started (Big Boar, Image Gen): one total, then each part.
                val b = s.boar
                Meter(t("native.labs.pulse.ramBoar"), "${gb(b.totalBytes)} GB", b.totalBytes.toFloat() / s.ramTotal, c.textAccentEmerald)
                Line(t("native.labs.pulse.ramApp"), "${gb(b.appBytes)} GB" + if (b.mappedFileBytes > 300L * 1024 * 1024) " · " + t("native.labs.pulse.ramMapped", "size" to gb(b.mappedFileBytes)) else "")
                for (e in b.engines) Line(t("native.labs.pulse.ramEngine", "name" to e.name), "${gb(e.bytes)} GB")
            }
            Card(t("native.labs.pulse.chip")) {
                Line(t("native.labs.pulse.engine"), BoarEngine.loadedVariant ?: "—")
                Line(t("native.labs.pulse.features"), CpuVariants.readFeatures().filter { it in KEY_FEATURES }.sorted().joinToString(" ").ifEmpty { "—" })
                Line(t("native.labs.pulse.battery"), (s.batteryPct?.let { "$it%" } ?: "—") + if (s.charging) " ⚡" else "")
                Line(t("native.labs.pulse.device"), "${Build.MANUFACTURER} ${Build.MODEL} · ${socName()}")
            }
            Text(t("native.labs.pulse.tip"), style = Boar.type.caption, color = c.textMuted)
        }
    }

    private val KEY_FEATURES = setOf("asimddp", "asimdhp", "i8mm", "sve", "sve2", "sme", "bf16")

    private fun socName(): String = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE

    private fun gb(b: Long) = String.format(Locale.US, "%.1f", b / 1073741824.0)

    @Composable
    private fun heatColor(t: Double?): Color = when {
        t == null -> Boar.colors.textDim
        t < 38 -> Boar.colors.textAccentEmerald
        t < 42 -> Color(0xFFF5B83D)
        else -> Boar.colors.crimson500
    }

    @Composable
    private fun thermalLabel(status: Int?): String = when (status) {
        null -> ""
        PowerManager.THERMAL_STATUS_NONE -> t("native.labs.pulse.thermal.none")
        PowerManager.THERMAL_STATUS_LIGHT -> t("native.labs.pulse.thermal.light")
        PowerManager.THERMAL_STATUS_MODERATE -> t("native.labs.pulse.thermal.moderate")
        else -> t("native.labs.pulse.thermal.severe")
    }

    @Composable
    private fun Card(title: String, content: @Composable () -> Unit) {
        val c = Boar.colors
        Column(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = Boar.type["mono.xs"], color = c.textDim, fontWeight = FontWeight.Bold)
            content()
        }
    }

    @Composable
    private fun Meter(label: String, value: String, frac: Float, color: Color) {
        val c = Boar.colors
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = Boar.type["mono.xs"], color = c.textMuted, modifier = Modifier.width(64.dp))
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Boar.colors.track(0.4f))) {
                Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(color))
            }
            Text(value, style = Boar.type["mono.xs"].copy(fontSize = 10.sp), color = c.textSecondary, modifier = Modifier.width(96.dp))
        }
    }

    @Composable
    private fun Line(label: String, value: String) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = Boar.type["mono.xs"], color = Boar.colors.textMuted, modifier = Modifier.width(80.dp))
        Text(value, style = Boar.type["mono.xs"], color = Boar.colors.textSecondary)
    }

    @Composable
    private fun Spark(values: List<Double>, modifier: Modifier) {
        val line = Boar.colors.emerald400
        val grid = Boar.colors.borderSubtle
        Canvas(modifier) {
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            if (values.size < 2) return@Canvas
            val lo = values.min() - 0.5
            val hi = values.max() + 0.5
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = size.width * i / (HISTORY - 1)
                val y = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, line, style = Stroke(width = 3f))
        }
    }
}
