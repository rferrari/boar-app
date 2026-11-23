package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, Nov 14: "display vitals dashboard during engine bench? a compact version. above logs". */
class BenchVitalsTest {
    private val bench = java.io.File("../app/src/main/java/team/sopa/boar/bench/BenchActivity.kt").readText()
    private val vitals = java.io.File("../app/src/main/java/team/sopa/boar/bench/BenchVitals.kt").readText()

    @Test fun `the vitals card sits above the log`() {
        assertTrue(bench.indexOf("BenchVitals()") in 0 until bench.indexOf("team.sopa.boar.ui.BoarSection(\"Log\")"))
    }

    @Test fun `it reads heat, throttling, cores, memory and battery every second, with no plugin`() {
        for (k in listOf("EXTRA_TEMPERATURE", "currentThermalStatus", "scaling_cur_freq", "cpuinfo_max_freq", "availMem", "AppMemory.residentBytes()", "EXTRA_LEVEL", "delay(1_000)"))
            assertTrue(k, vitals.contains(k))
        assertTrue(!vitals.contains("team.sopa.boar.labs."))
    }

    @Test fun `CPU and BOAR on one line, the free RAM on its own, in words`() {
        assertTrue(vitals.contains("\"RAM %s GB free of %s GB\"") && !vitals.contains("RAM %s / %s GB free"))
        assertTrue(vitals.contains("\"BOAR %s GB\"") && vitals.contains("maxLines = 1"))
    }
}
