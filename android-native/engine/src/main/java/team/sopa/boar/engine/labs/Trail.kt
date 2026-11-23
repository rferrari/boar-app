package team.sopa.boar.engine.labs

import kotlin.math.floor
import kotlin.math.sin

/**
 * The waiting animation next to the matrix rain (rc3 slice 4, adam, Nov 10): a boar trots across the screen and leaves
 * hoofprints that fade behind it, then comes round again. Positions only, in 0..1 of the drawing area, so the screen
 * draws it at any size and the math is tested here. Designer runs one step (SDXS), so a real denoising preview has
 * nothing to show: the trail serves both the closing screen and Designer's wait.
 */
object Trail {
    /** One crossing, in seconds. */
    const val PERIOD = 6.0
    /** A print every this many seconds, so about 12 per crossing. */
    const val STEP = 0.5
    /** A print fades out over this many seconds. */
    const val FADE = 4.0

    /** Where the boar is: [x] left to right, [y] a slight bob, [facingRight] always (it crosses one way). */
    data class Boar(val x: Float, val y: Float)

    /** One hoofprint: where, which side of the track (alternating), and how faded (1 new, 0 gone). */
    data class Print(val x: Float, val y: Float, val upper: Boolean, val alpha: Float)

    data class Frame(val boar: Boar, val prints: List<Print>)

    /** The track's height in the drawing area. */
    private const val TRACK_Y = 0.62f
    private const val BOB = 0.015f
    private const val SPREAD = 0.035f

    /**
     * The frame at [seconds]. [still]: the phone's animations are off (Android's remove animations), so one calm frame:
     * the boar mid-way, its prints so far, none fading.
     */
    fun at(seconds: Double, still: Boolean = false): Frame {
        val t = if (still) PERIOD / 2 else seconds
        val phase = ((t % PERIOD) + PERIOD) % PERIOD / PERIOD
        val boar = Boar(x = (-0.1 + 1.2 * phase).toFloat(), y = (TRACK_Y - 0.05 + if (still) 0.0 else BOB * sin(t * 8)).toFloat())
        val prints = ArrayList<Print>()
        val last = floor(t / STEP).toLong()
        val count = (FADE / STEP).toInt()
        for (k in 0..count) {
            val n = last - k
            if (n < 0) break
            val made = n * STEP
            val age = t - made
            val alpha = if (still) 1f else (1 - age / FADE).toFloat()
            if (alpha <= 0f) continue
            val p = ((made % PERIOD) + PERIOD) % PERIOD / PERIOD
            val x = (-0.1 + 1.2 * p).toFloat()
            // Only prints of this crossing: a print from the last crossing ahead of the boar would read as a wrong way.
            if (x > boar.x || x < 0f || x > 1f) continue
            val upper = n % 2 == 0L
            prints += Print(x, TRACK_Y + if (upper) -SPREAD else SPREAD, upper, alpha.coerceIn(0f, 1f))
        }
        return Frame(boar, prints)
    }
}
