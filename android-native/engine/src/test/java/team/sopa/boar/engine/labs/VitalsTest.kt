package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Boar Vitals' readings. */
class VitalsTest {
    @Test fun `heat bands`() {
        assertEquals(Vitals.Heat.UNKNOWN, Vitals.heat(null))
        assertEquals(Vitals.Heat.COOL, Vitals.heat(30.0))
        assertEquals(Vitals.Heat.WARM, Vitals.heat(38.0))
        assertEquals(Vitals.Heat.WARM, Vitals.heat(41.9))
        assertEquals(Vitals.Heat.HOT, Vitals.heat(42.0))
    }

    @Test fun `cpufreq kHz text becomes MHz`() {
        assertEquals(1804, Vitals.mhzOf("1804800\n"))
        assertEquals(3000, Vitals.mhzOf(" 3000000 "))
        assertNull(Vitals.mhzOf(null))
        assertNull(Vitals.mhzOf(""))
        assertNull(Vitals.mhzOf("offline"))
        assertNull(Vitals.mhzOf("-5"))
    }

    @Test fun `core ids are the cpuN folders only, in order, gaps kept`() {
        assertEquals(listOf(0, 1, 2, 4, 10), Vitals.coreIds(listOf("cpu10", "cpufreq", "cpu1", "cpuidle", "cpu0", "cpu4", "cpu2", "possible", "cpu")))
    }

    @Test fun `battery percent and temperature`() {
        assertEquals(81, Vitals.batteryPercent(81, 100))
        assertEquals(0, Vitals.batteryPercent(0, 100))
        assertEquals(100, Vitals.batteryPercent(100, 100))
        assertEquals(50, Vitals.batteryPercent(128, 256))
        assertNull(Vitals.batteryPercent(-1, 100))
        assertNull(Vitals.batteryPercent(50, 0))
        assertEquals(36.5, Vitals.batteryTempC(365)!!, 0.001)
        assertNull(Vitals.batteryTempC(Int.MIN_VALUE))
    }
}
