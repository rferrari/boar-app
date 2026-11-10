package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The boar's trail: it crosses, leaves alternating prints behind it that fade, and holds still with animations off. */
class TrailTest {
    @Test fun `the boar crosses left to right each period and comes round`() {
        val xs = (0..60).map { Trail.at(it * Trail.PERIOD / 60).boar.x }
        assertTrue(xs.first() < 0f && xs[59] > 1f)
        assertTrue((1 until 60).all { xs[it] > xs[it - 1] })
        assertEquals(Trail.at(1.0).boar.x, Trail.at(1.0 + Trail.PERIOD).boar.x, 1e-4f)
    }

    @Test fun `prints are behind the boar, inside the area, alternating sides, fading`() {
        for (s in listOf(2.0, 3.3, 5.9, 17.2)) {
            val f = Trail.at(s)
            assertTrue(f.prints.isNotEmpty())
            assertTrue(f.prints.all { it.x <= f.boar.x && it.x in 0f..1f && it.alpha in 0f..1f })
            assertTrue(f.prints.any { it.upper } && f.prints.any { !it.upper })
        }
        val f = Trail.at(5.0)
        // The newest print is the brightest.
        assertEquals(f.prints.maxOf { it.x }, f.prints.maxBy { it.alpha }.x, 1e-6f)
    }

    @Test fun `no print outlives its fade`() =
        assertTrue(Trail.at(40.0).prints.size <= (Trail.FADE / Trail.STEP).toInt() + 1)

    @Test fun `with animations off it is one calm frame`() {
        val a = Trail.at(1.0, still = true)
        val b = Trail.at(99.0, still = true)
        assertEquals(a, b)
        assertTrue(a.prints.all { it.alpha == 1f })
    }
}
