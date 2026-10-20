package team.sopa.boar.labs.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Boar Nomad: a compass from the phone's own sensors, offline (the magnetometer for direction, the accelerometer for
 * tilt; plain Android sensors, no Google services). It points to magnetic north: true north needs the local declination,
 * which needs the user's location, and BOAR doesn't ask for it. A low accuracy report from the magnetometer shows the
 * figure-8 calibration hint.
 */
object CompassLab : LabPlugin {
    override val id = "compass"
    override val emoji = "🧭"
    override val art = team.sopa.boar.R.drawable.truffle_nomad
    override val status = LabStatus.ALPHA

    /** How much of each new reading goes into the shown heading: smooth enough to read, quick enough to follow. */
    private const val SMOOTHING = 0.15f
    /** The fallback's raw accelerometer and magnetometer readings are noisy: each new one counts this much. */
    private const val RAW_SMOOTHING = 0.12f

    @Composable
    override fun Content() {
        val c = Boar.colors
        val ctx = LocalContext.current
        val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
        val accel = remember { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
        val mag = remember { sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
        // Android's fused orientation (gyroscope, accelerometer and magnetometer) when the phone has it: far steadier
        // than the two raw sensors, which flicked the rose (Oct 18). The raw pair is the fallback.
        val fused = remember { sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
        var heading by remember { mutableFloatStateOf(0f) }
        var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_UNRELIABLE) }
        var seen by remember { mutableStateOf(false) }
        if (accel != null && mag != null) DisposableEffect(Unit) {
            val g = FloatArray(3); val m = FloatArray(3); var haveG = false; var haveM = false
            val rot = FloatArray(9); val orient = FloatArray(3)
            fun show(deg: Float) {
                // Smoothed along the shortest way round, so 359° → 1° doesn't swing through 180°.
                val delta = ((deg - heading + 540) % 360) - 180
                heading = if (!seen) deg else (heading + SMOOTHING * delta + 360) % 360
                seen = true
            }
            fun lowPass(v: FloatArray, into: FloatArray, first: Boolean) {
                for (i in 0 until 3) into[i] = if (first) v[i] else into[i] + RAW_SMOOTHING * (v[i] - into[i])
            }
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    when (e.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rot, e.values)
                            SensorManager.getOrientation(rot, orient)
                            show(((Math.toDegrees(orient[0].toDouble()) + 360) % 360).toFloat())
                            return
                        }
                        Sensor.TYPE_ACCELEROMETER -> { lowPass(e.values, g, !haveG); haveG = true }
                        // With the fused sensor, the magnetometer only reports whether it needs calibrating.
                        Sensor.TYPE_MAGNETIC_FIELD -> { lowPass(e.values, m, !haveM); haveM = true; accuracy = e.accuracy; if (fused != null) return }
                    }
                    if (haveG && haveM && SensorManager.getRotationMatrix(rot, null, g, m)) {
                        SensorManager.getOrientation(rot, orient)
                        show(((Math.toDegrees(orient[0].toDouble()) + 360) % 360).toFloat())
                    }
                }
                override fun onAccuracyChanged(s: Sensor, a: Int) { if (s.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy = a }
            }
            if (fused != null) sm.registerListener(listener, fused, SensorManager.SENSOR_DELAY_UI)
            else sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
            onDispose { sm.unregisterListener(listener) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (accel == null || mag == null) {
                Text(t("native.labs.compass.noSensor"), style = Boar.type.body, color = c.textSecondary)
                return@Column
            }
            // The number moves in whole degrees and only once the heading has moved at least 1°, so it doesn't flicker
            // between two values while the phone is still.
            var shownDeg by remember { mutableIntStateOf(0) }
            if (abs(((heading - shownDeg + 540) % 360) - 180) >= 1f) shownDeg = heading.roundToInt() % 360
            val deg = shownDeg
            Column(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (seen) "$deg° ${t("native.labs.compass.dir.${cardinal(deg)}")}" else "—", style = Boar.type.headline, color = c.textHeading, fontWeight = FontWeight.ExtraBold)
                Rose(heading, Modifier.fillMaxWidth(0.85f).aspectRatio(1f), c.textAccentEmerald, c.crimson400, c.textMuted, c.borderDefault)
            }
            if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
                Text("⚠ " + t("native.labs.compass.calibrate"), style = Boar.type.body, color = c.amber400,
                    modifier = Modifier.fillMaxWidth().background(c.amberBgSubtle, RoundedCornerShape(10.dp)).padding(12.dp))
            Text(t("native.labs.compass.note"), style = Boar.type["mono.xs"], color = c.textDim)
        }
    }

    /** "n", "ne", "e"… for the 8 directions, each 45° wide. */
    fun cardinal(deg: Int): String = listOf("n", "ne", "e", "se", "s", "sw", "w", "nw")[((deg + 22) % 360) / 45]

    /** The dial turns so its N points to magnetic north; the marker at the top is where the phone points. */
    @Composable
    private fun Rose(heading: Float, modifier: Modifier, accent: Color, north: Color, ticks: Color, ring: Color) = Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val r = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(ring, radius = r - 2, center = center, style = Stroke(width = 3f))
            rotate(-heading, center) {
                for (i in 0 until 72) {
                    val a = Math.toRadians(i * 5.0 - 90)
                    val len = if (i % 18 == 0) 0.16f else if (i % 6 == 0) 0.10f else 0.05f
                    val outer = Offset(center.x + (r - 6) * cos(a).toFloat(), center.y + (r - 6) * sin(a).toFloat())
                    val inner = Offset(center.x + (r - 6 - r * len) * cos(a).toFloat(), center.y + (r - 6 - r * len) * sin(a).toFloat())
                    drawLine(if (i == 0) north else ticks, inner, outer, strokeWidth = if (i % 18 == 0) 5f else 2f)
                }
                // The needle: red to the north, plain to the south.
                val n = Path().apply { moveTo(center.x, center.y - r * 0.62f); lineTo(center.x - r * 0.07f, center.y); lineTo(center.x + r * 0.07f, center.y); close() }
                val s = Path().apply { moveTo(center.x, center.y + r * 0.62f); lineTo(center.x - r * 0.07f, center.y); lineTo(center.x + r * 0.07f, center.y); close() }
                drawPath(n, north); drawPath(s, ticks)
            }
            // Where the phone points.
            val top = Path().apply { moveTo(center.x, 0f); lineTo(center.x - r * 0.06f, r * 0.1f); lineTo(center.x + r * 0.06f, r * 0.1f); close() }
            drawPath(top, accent)
            drawCircle(accent, radius = r * 0.04f, center = center)
        }
    }
}
