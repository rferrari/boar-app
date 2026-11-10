package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/** Boar Lunar's moon phases (Meeus ch. 49), from the date alone. */
class MoonPhaseTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun minutesApart(a: Long, b: Long) = abs(a - b) / 60_000.0

    @Test fun `Meeus example 49a, the new moon of 1977 Feb 18`() {
        // JDE 2443192.65118 (03:37:42 TD); k = -283 lunations from Jan 2000.
        assertEquals(2443192.65118, MoonPhase.phaseJd(-283.0, full = false), 0.001)
    }

    @Test fun `October 2026, new on the 10th and full on the 26th`() {
        val s = MoonPhase.at(ms("2026-10-20T12:00:00Z"))
        assertTrue(minutesApart(s.lastNewMs, ms("2026-10-10T15:51:00Z")) < 5)
        assertTrue(minutesApart(s.nextFullMs, ms("2026-10-26T04:12:00Z")) < 5)
        assertTrue(s.nextNewMs > s.nextFullMs)
    }

    @Test fun `the lunation is about 29 and a half days and the age falls inside it`() {
        for (day in listOf("2026-01-01", "2026-05-17", "2026-11-10", "2030-07-04")) {
            val s = MoonPhase.at(ms("${day}T00:00:00Z"))
            assertTrue(day, s.lengthDays in 29.2..29.9)
            assertTrue(day, s.ageDays in 0.0..s.lengthDays)
            assertTrue(day, s.illumination in 0.0..1.0)
            assertTrue(day, s.lastNewMs <= ms("${day}T00:00:00Z") && s.nextNewMs > ms("${day}T00:00:00Z"))
        }
    }

    @Test fun `full moon is fully lit and new moon dark`() {
        assertTrue(MoonPhase.at(ms("2026-10-26T04:12:00Z")).illumination > 0.99)
        assertTrue(MoonPhase.at(ms("2026-10-10T15:51:00Z")).illumination < 0.01)
        assertEquals("full", MoonPhase.nameKey(MoonPhase.at(ms("2026-10-26T04:12:00Z"))))
        assertEquals("new", MoonPhase.nameKey(MoonPhase.at(ms("2026-10-10T15:51:00Z"))))
    }

    private fun state(age: Double) = MoonPhase.State(age, 29.53, 0.0, age / 29.53, 0, 0, 0)

    @Test fun `the eight phases, quarters within a day of their moment`() {
        val q = 29.53 / 4
        val cases = listOf(0.5 to 0, 3.0 to 1, q to 2, q + 0.9 to 2, q + 1.1 to 3, 2 * q to 4, 2 * q + 2 to 5, 3 * q to 6, 3 * q + 2 to 7, 29.0 to 0)
        for ((age, index) in cases) assertEquals("age $age", index, MoonPhase.index(state(age)))
        // Day 5.9 (34 % lit) is still a crescent, not the first quarter (X6, Oct 16).
        assertEquals("waxingCrescent", MoonPhase.nameKey(state(5.9)))
    }

    @Test fun `the southern sky mirrors the crescents`() {
        assertEquals("🌒", MoonPhase.emoji(state(3.0), south = false))
        assertEquals("🌘", MoonPhase.emoji(state(3.0), south = true))
        assertEquals("🌕", MoonPhase.emoji(state(29.53 / 2), south = true))
    }
}
