package team.sopa.boar.engine.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaPickerTest {
    private val pool = (1..10).map { "q$it" }

    @Test fun `never the set shown last, and three distinct`() {
        val last = setOf("q1", "q2", "q3")
        repeat(200) { seed ->
            val p = IdeaPicker.pick(pool, emptySet(), last, 3, java.util.Random(seed.toLong()))
            assertEquals(3, p.toSet().size)
            assertTrue(p.none { it in last })
        }
    }

    @Test fun `ideas from downloaded packs come up more often`() {
        val packs = setOf("q9", "q10")
        val r = java.util.Random(1)
        var withPack = 0
        repeat(2000) { if (IdeaPicker.pick(pool, packs, emptySet(), 1, r).single() in packs) withPack++ }
        // 2 of 10 at double weight: about 2/6 = 33%, against 20% unweighted.
        assertTrue("$withPack", withPack in 560..780)
    }

    @Test fun `a small pool still gives what it can`() {
        assertEquals(setOf("a", "b"), IdeaPicker.pick(listOf("a", "b"), emptySet(), setOf("a"), 3).toSet())
    }
}
