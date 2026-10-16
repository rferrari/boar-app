package team.sopa.boar.labs.lunar

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Moon phases from the date alone, offline: Meeus, Astronomical Algorithms ch. 49 (true new and full moon with the main
 * periodic terms; the planetary terms, under a minute, are left out). Checked against Meeus's example 49.a (new moon
 * 1977 Feb 18 03:37 TD, within a minute) and Oct 2026 (new 10 Oct 15:51, full 26 Oct 04:12 UTC). Times are UTC.
 */
object MoonPhase {
    private const val SYNODIC = 29.530588861
    private const val J2000_NEW = 2451550.09766
    private const val UNIX_EPOCH_JD = 2440587.5

    fun julianDay(epochMs: Long): Double = epochMs / 86_400_000.0 + UNIX_EPOCH_JD
    fun epochMs(jd: Double): Long = ((jd - UNIX_EPOCH_JD) * 86_400_000.0).toLong()

    /** The new moon (k a whole number) or full moon (k + 0.5) for lunation [k] counted from Jan 2000, as a Julian day. */
    fun phaseJd(k: Double, full: Boolean): Double {
        val t = k / 1236.85
        val jde = J2000_NEW + SYNODIC * k + 0.00015437 * t * t - 0.000000150 * t * t * t + 0.00000000073 * t * t * t * t
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        fun r(d: Double) = d * PI / 180
        val m = r(2.5534 + 29.10535670 * k - 0.0000014 * t * t - 0.00000011 * t * t * t)
        val mp = r(201.5643 + 385.81693528 * k + 0.0107582 * t * t + 0.00001238 * t * t * t - 0.000000058 * t * t * t * t)
        val f = r(160.7108 + 390.67050284 * k - 0.0016118 * t * t - 0.00000227 * t * t * t + 0.000000011 * t * t * t * t)
        val o = r(124.7746 - 1.56375588 * k + 0.0020672 * t * t + 0.00000215 * t * t * t)
        val c = if (full) doubleArrayOf(-0.40614, 0.17302, 0.01614, 0.01043, 0.00734, -0.00515, 0.00209)
        else doubleArrayOf(-0.40720, 0.17241, 0.01608, 0.01039, 0.00739, -0.00514, 0.00208)
        return jde + c[0] * sin(mp) + c[1] * e * sin(m) + c[2] * sin(2 * mp) + c[3] * sin(2 * f) + c[4] * e * sin(mp - m) +
            c[5] * e * sin(mp + m) + c[6] * e * e * sin(2 * m) - 0.00111 * sin(mp - 2 * f) - 0.00057 * sin(mp + 2 * f) +
            0.00056 * e * sin(2 * mp + m) - 0.00042 * sin(3 * mp) + 0.00042 * e * sin(m + 2 * f) + 0.00038 * e * sin(m - 2 * f) -
            0.00024 * e * sin(2 * mp - m) - 0.00017 * sin(o)
    }

    /** The moon at an instant: days since the last new moon, the lunation's length, the lit fraction, the phase (0..1). */
    data class State(val ageDays: Double, val lengthDays: Double, val illumination: Double, val phase: Double,
        val lastNewMs: Long, val nextNewMs: Long, val nextFullMs: Long)

    fun at(epochMs: Long): State {
        val jd = julianDay(epochMs)
        var k = floor((jd - J2000_NEW) / SYNODIC)
        while (phaseJd(k, false) > jd) k -= 1
        while (phaseJd(k + 1, false) <= jd) k += 1
        val lastNew = phaseJd(k, false)
        val nextNew = phaseJd(k + 1, false)
        val full = phaseJd(k + 0.5, true)
        val nextFull = if (full > jd) full else phaseJd(k + 1.5, true)
        val age = jd - lastNew
        val length = nextNew - lastNew
        val phase = age / length
        return State(age, length, (1 - cos(2 * PI * phase)) / 2, phase, epochMs(lastNew), epochMs(nextNew), epochMs(nextFull))
    }

    /** One of eight names, by the phase: new, waxing crescent, first quarter, waxing gibbous, full, waning gibbous… */
    fun nameKey(phase: Double): String = KEYS[((phase * 8 + 0.5).toInt()) % 8]

    /** The phase as an emoji, drawn as seen from the north; [south] mirrors it (a waxing moon is lit on the left there). */
    fun emoji(phase: Double, south: Boolean): String {
        val i = ((phase * 8 + 0.5).toInt()) % 8
        return (if (south) SOUTH else NORTH)[i]
    }

    private val KEYS = listOf("new", "waxingCrescent", "firstQuarter", "waxingGibbous", "full", "waningGibbous", "lastQuarter", "waningCrescent")
    private val NORTH = listOf("🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘")
    private val SOUTH = listOf("🌑", "🌘", "🌗", "🌖", "🌕", "🌔", "🌓", "🌒")
}
