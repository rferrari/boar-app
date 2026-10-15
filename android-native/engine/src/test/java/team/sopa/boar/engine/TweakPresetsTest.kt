package team.sopa.boar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TweakPresetsTest {
    /** Dimensity 8300 (X6 Pro): 4 little, then 1 prime + 3 big. */
    private val x6 = CpuTopology(List(4) { CpuTopology.Core(it, 2_200_000) } + listOf(CpuTopology.Core(4, 3_350_000)) + List(3) { CpuTopology.Core(5 + it, 3_200_000) })

    @Test fun `Boar's Path is Auto`() {
        assertTrue(TweakPreset.PATH.applyTo(EngineTweaks()).isAuto)
        assertEquals(TweakPreset.PATH, TweakPreset.of(EngineTweaks()))
    }

    @Test fun `each preset is recognised after it is applied`() {
        for (p in TweakPreset.entries) assertEquals(p, TweakPreset.of(p.applyTo(EngineTweaks())))
    }

    @Test fun `a preset keeps the phone's CPU build`() {
        val mine = EngineTweaks(cpuBuild = "armv8.6_1")
        for (p in TweakPreset.entries) {
            assertEquals("armv8.6_1", p.applyTo(mine).cpuBuild)
            assertEquals(p, TweakPreset.of(p.applyTo(mine)))
        }
    }

    @Test fun `a knob changed by hand makes it Custom`() {
        assertNull(TweakPreset.of(TweakPreset.RUN.applyTo(EngineTweaks()).copy(threads = 3)))
        assertNull(TweakPreset.of(EngineTweaks(kvCache = KvType.Q8_0)))
    }

    @Test fun `Run Boar Run keeps Auto's threads on the fast cores and adds no slow ones`() {
        val r = TweakPreset.RUN.applyTo(EngineTweaks()).resolve(x6)
        assertEquals(4, r.params.threads)
        assertEquals("4,5,6,7", r.params.cpus)
        assertTrue(r.params.strictCpu)
        assertEquals(LoadMode.MMAP_MLOCK, r.params.loadMode)
        assertNull(TweakPreset.RUN.applyTo(EngineTweaks()).stageTimeoutMs(1024))
        assertTrue(r.notes.isEmpty())
    }

    @Test fun `the gentle presets resolve without clamping`() {
        for (p in listOf(TweakPreset.FORAGE, TweakPreset.COOL)) {
            val t = p.applyTo(EngineTweaks())
            val r = t.resolve(x6)
            assertTrue(r.notes.isEmpty())
            assertEquals(2048, r.params.nCtx)
            assertEquals(false, t.thinkingEnabled)
            assertEquals(300_000L, t.stageTimeoutMs(1024))
        }
        assertEquals(1, TweakPreset.COOL.applyTo(EngineTweaks()).resolve(x6).params.threads)
    }
}
