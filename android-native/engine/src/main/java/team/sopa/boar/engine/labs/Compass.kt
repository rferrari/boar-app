package team.sopa.boar.engine.labs

import kotlin.math.abs
import kotlin.math.roundToInt

/** Boar Nomad's compass arithmetic, kept off the sensors so it can be tested: headings in degrees, 0 = north. */
object Compass {
    /** "n", "ne", "e"… for the 8 directions, each 45° wide and centered on its own (north is 338° to 22°). */
    fun cardinal(deg: Int): String = DIRS[((deg % 360 + 360 + 22) % 360) / 45]

    /** An orientation azimuth in radians (−π..π, Android's getOrientation) as a heading in 0..360. */
    fun headingOf(azimuthRad: Double): Float = ((Math.toDegrees(azimuthRad) + 360) % 360).toFloat()

    /** The signed turn from [from] to [to] the short way round, in −180..180: 359° → 1° is +2°, not −358°. */
    fun turn(from: Float, to: Float): Float = ((to - from + 540) % 360) - 180

    /** The rose's next heading: a [k] step toward [deg] along the shortest way; the first reading is taken as is. */
    fun smooth(heading: Float, deg: Float, first: Boolean, k: Float): Float = if (first) deg else (heading + k * turn(heading, deg) + 360) % 360

    /** The whole degree shown: it moves only once the heading is a full degree away, so a still phone doesn't flicker. */
    fun shown(current: Int, heading: Float): Int = if (abs(turn(current.toFloat(), heading)) >= 1f) heading.roundToInt() % 360 else current

    /** One raw sensor axis low-passed: the first value as is, then a [k] step toward each new one. */
    fun lowPass(previous: Float, value: Float, first: Boolean, k: Float): Float = if (first) value else previous + k * (value - previous)

    private val DIRS = listOf("n", "ne", "e", "se", "s", "sw", "w", "nw")
}
