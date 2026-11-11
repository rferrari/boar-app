package team.sopa.boar.engine.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPillTest {
    @Test fun `it shows on open and on a real change, not again for the same state`() {
        assertTrue(ConnectionPill.shouldShow(online = false, last = null))
        assertTrue(ConnectionPill.shouldShow(online = true, last = false))
        assertFalse(ConnectionPill.shouldShow(online = true, last = true))
    }

    @Test fun `the header waits for the connection to settle and remembers what it showed`() {
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        val dot = chat.substringAfter("private fun ConnectionDot(").substringBefore("\n}\n")
        assertTrue(dot.contains("delay(ConnectionPill.SETTLE_MS)") && dot.contains("ConnectionPill.lastShown = online"))
        assertTrue(dot.contains("delay(ConnectionPill.SHOW_MS)"))
        // Reduce motion: no animation, the same time on screen.
        assertTrue(dot.contains("ANIMATOR_DURATION_SCALE"))
        assertTrue(!chat.contains("offlineReady"))
    }

    @Test fun `the dot is amber online and a greyed blue ring offline`() {
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("if (online) Modifier.background(c.amber400, CircleShape) else Modifier.border(1.5.dp, MOON.copy(alpha = 0.55f), CircleShape)"))
    }
}
