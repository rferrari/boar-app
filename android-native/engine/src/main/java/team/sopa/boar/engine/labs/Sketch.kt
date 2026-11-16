package team.sopa.boar.engine.labs

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Designer's waiting picture (adam, Nov 12): pen strokes drawn little by little on the canvas while sd-cli works. It is
 * decoration only: the model draws in one step and reports no progress, so nothing here measures anything. Strokes come
 * from a fixed seed (the same every time, no flicker), each a short curve that grows from its start time.
 */
object Sketch {
    /** One stroke: points in 0..1 of the canvas, and when it starts and how long it takes to draw (seconds). */
    data class Stroke(val points: List<Pair<Float, Float>>, val start: Double, val draw: Double, val width: Float)

    const val STROKES = 26
    /** After this the drawing starts over, faded: the wait can be longer than one sketch. */
    const val CYCLE_S = 9.0

    fun strokes(seed: Int = 7): List<Stroke> {
        val r = java.util.Random(seed.toLong())
        return (0 until STROKES).map { i ->
            val cx = 0.15f + r.nextFloat() * 0.7f
            val cy = 0.15f + r.nextFloat() * 0.7f
            val len = 0.08f + r.nextFloat() * 0.22f
            val a0 = r.nextFloat() * 2 * PI.toFloat()
            val bend = (r.nextFloat() - 0.5f) * 2.2f
            val pts = (0..12).map { k ->
                val t = k / 12f
                val a = a0 + bend * t
                (cx + cos(a) * len * t).coerceIn(0.03f, 0.97f) to (cy + sin(a) * len * t).coerceIn(0.03f, 0.97f)
            }
            Stroke(pts, start = i * (CYCLE_S * 0.8 / STROKES), draw = 0.5 + r.nextDouble() * 0.6, width = 0.6f + r.nextFloat() * 1.4f)
        }
    }

    /** How long a stroke stays drawn before it starts to fade, and how long the fade takes (seconds). */
    const val HOLD_S = 4.0
    const val FADE_S = 1.2

    /**
     * Each stroke at [seconds], written into [progress] (how much is drawn, 0..1) and [alpha] (0..1), with no allocation:
     * every stroke runs its own draw, hold and fade on a [CYCLE_S] period from its own start, and the starts are spread
     * over the cycle, so some strokes draw while others fade and the loop never restarts all at once (adam, Nov 12).
     * [still]: animations off, one calm frame.
     */
    fun frame(strokes: List<Stroke>, seconds: Double, still: Boolean, progress: FloatArray, alpha: FloatArray) {
        val t = if (still) CYCLE_S * 0.5 else seconds
        for (i in strokes.indices) {
            val s = strokes[i]
            if (t < s.start) { progress[i] = 0f; alpha[i] = 0f; continue }
            val local = (t - s.start) % CYCLE_S
            progress[i] = (local / s.draw).coerceIn(0.0, 1.0).toFloat()
            val fadeAt = s.draw + HOLD_S
            alpha[i] = when {
                local < fadeAt -> 1f
                local < fadeAt + FADE_S -> (1.0 - (local - fadeAt) / FADE_S).toFloat()
                else -> 0f
            }
        }
    }

    /** [frame] as new lists, for tests and one-off use. */
    fun progress(strokes: List<Stroke>, seconds: Double, still: Boolean): List<Float> {
        val p = FloatArray(strokes.size); val a = FloatArray(strokes.size)
        frame(strokes, seconds, still, p, a)
        return p.indices.map { p[it] * a[it] }
    }

    /** The ink and paper for a style key (ImageLab.Style): dark ink on paper for the drawn styles, light lines on dark for the night ones. */
    data class Palette(val paper: Long, val ink: Long)

    fun palette(style: String?): Palette = when (style) {
        "engraving", "scientific", null, "none" -> Palette(0xFFF4EFE4, 0xFF1C1C1C)
        "photo" -> Palette(0xFFE9E9E9, 0xFF5A5A5A)
        "cyber" -> Palette(0xFF0B0F1A, 0xFF35E0FF)
        "solarpunk" -> Palette(0xFFF3F0DA, 0xFF2E7D32)
        "y2k" -> Palette(0xFFF2EAF7, 0xFFC04FD8)
        "lofi" -> Palette(0xFFFBEFE6, 0xFF8C6BB1)
        "watercolor" -> Palette(0xFFFAF7F0, 0xFF5B8DB8)
        else -> Palette(0xFFF4EFE4, 0xFF1C1C1C)
    }
}
