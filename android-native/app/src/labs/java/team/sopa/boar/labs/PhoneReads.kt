package team.sopa.boar.labs

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import team.sopa.boar.engine.labs.PluginAnswers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * What Nomad and Vitals read when they answer a question in the chat (adam, Nov 12). Called from the answer's own
 * thread, never the main one: the compass waits up to [waitMs] for the rotation sensor's first readings.
 */
object PhoneReads {
    /** The phone's heading (degrees from magnetic north) and its tilt from flat (degrees); nulls without a sensor. */
    fun heading(ctx: Context, waitMs: Long = 900): Pair<Float?, Float?> {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null to null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return null to null
        val got = CountDownLatch(3)
        var heading: Float? = null
        var tilt: Float? = null
        val rot = FloatArray(9)
        val angles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, angles)
                heading = team.sopa.boar.engine.labs.Compass.headingOf(angles[0].toDouble())
                // Pitch and roll together: how far the screen is from facing up.
                tilt = Math.toDegrees(kotlin.math.acos((kotlin.math.cos(angles[1].toDouble()) * kotlin.math.cos(angles[2].toDouble())).coerceIn(-1.0, 1.0))).toFloat()
                got.countDown()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        val thread = android.os.HandlerThread("boar-compass").apply { start() }
        return try {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME, android.os.Handler(thread.looper))
            got.await(waitMs, TimeUnit.MILLISECONDS)
            heading to tilt
        } finally {
            sm.unregisterListener(listener)
            thread.quitSafely()
        }
    }

    fun phone(ctx: Context): PluginAnswers.PhoneNow {
        val battery = runCatching { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.let(team.sopa.boar.engine.labs.Vitals::batteryTempC)
        val mem = runCatching {
            android.app.ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it) }
        }.getOrNull()
        return PluginAnswers.PhoneNow(
            batteryPct = team.sopa.boar.engine.labs.Vitals.batteryPercent(level, scale),
            charging = if (status < 0) null else status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            batteryTempC = temp,
            ramFreeMb = mem?.availMem?.div(1_048_576), ramTotalMb = mem?.totalMem?.div(1_048_576),
            cores = Runtime.getRuntime().availableProcessors(),
        )
    }
}
