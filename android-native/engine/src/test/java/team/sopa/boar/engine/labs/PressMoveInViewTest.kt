package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 0143 (adam, Nov 14: "when i move [a] paragraph to top, can we scroll to it if not visible on screen anymore?"). */
class PressMoveInViewTest {
    private val view = 1000f
    private val room = 40f

    @Test fun `fully visible, no scroll`() {
        assertNull(PressLayout.scrollToShow(view, 0f, 200f, room))
        assertNull(PressLayout.scrollToShow(view, 800f, 200f, room))
    }

    @Test fun `moved above the top, scroll up to it with room above`() {
        assertEquals(-340f, PressLayout.scrollToShow(view, -300f, 200f, room)!!, 0.01f)
        // Partly hidden at the top counts too.
        assertEquals(-60f, PressLayout.scrollToShow(view, -20f, 200f, room)!!, 0.01f)
    }

    @Test fun `moved below the bottom, scroll down so its bottom shows with room under it`() {
        assertEquals(140f, PressLayout.scrollToShow(view, 900f, 200f, room)!!, 0.01f)
        assertEquals(1440f, PressLayout.scrollToShow(view, 2200f, 200f, room)!!, 0.01f)
    }

    @Test fun `a block taller than the screen lines up its top, and stays put once it is there`() {
        assertEquals(460f, PressLayout.scrollToShow(view, 500f, 1500f, room)!!, 0.01f)
        assertNull(PressLayout.scrollToShow(view, room, 1500f, room))
    }

    @Test fun `the screen follows every move and measures after layout`() {
        val s = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt").readText()
        assertTrue(s.contains("follow = id to (follow?.second ?: 0) + 1"))
        assertTrue(s.contains("PressLayout.scrollToShow(viewHeight, top - viewTop, height, with(density) { 16.dp.toPx() })"))
        assertTrue(s.contains("if (still) scroll.scrollBy(delta) else scroll.animateScrollBy(delta)"))
    }
}
