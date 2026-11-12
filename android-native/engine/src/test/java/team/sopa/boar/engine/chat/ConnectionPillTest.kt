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
        assertTrue(dot.contains("delay(ConnectionPill.SETTLE_MS)") && dot.contains("ConnectionPill.lastShown = online") && dot.contains("delay(ConnectionPill.SHOW_MS)"))
        assertTrue(!chat.contains("offlineReady"))
    }

    @Test fun `ONLINE or OFFLINE crossfades in the model line, so nothing in the header moves`() {
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        val header = chat.substringAfter("private fun Header(").substringBefore("\n}\n")
        assertTrue(header.contains("Crossfade(connectionFlash.value") && header.contains("ANIMATOR_DURATION_SCALE"))
        val dot = chat.substringAfter("private fun ConnectionDot(").substringBefore("\n}\n")
        assertTrue(!dot.contains("animateContentSize") && !dot.contains("if (expanded)"))
        assertTrue(dot.contains("if (online) Modifier.background(c.amber400, CircleShape) else Modifier.border(1.5.dp, MOON.copy(alpha = 0.55f), CircleShape)"))
        assertTrue(dot.contains("boldMarks(t(if (online) \"chatHeader.onlineBody\" else \"chatHeader.offlineBody\"))"))
    }

    @Test fun `every page header is the chat header's height`() {
        val ph = java.io.File("../app/src/main/java/team/sopa/boar/ui/PageHeader.kt").readText()
        assertTrue(ph.contains("val ROW = 42.dp") && ph.contains("val PAD = 10.dp") && ph.contains("val HEIGHT = ROW + PAD * 2"))
        assertTrue(ph.contains(".height(HeaderMetrics.HEIGHT)") && ph.contains("FixedSizeChrome"))
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("Row(Modifier.height(team.sopa.boar.ui.HeaderMetrics.ROW)") && chat.contains("vertical = team.sopa.boar.ui.HeaderMetrics.PAD"))
    }

    @Test fun `the dialog texts are short paragraphs with bold marks, in English and Portuguese`() {
        for (d in listOf("values", "values-pt")) {
            val s = java.io.File("../app/src/main/res/$d/strings.xml").readText()
            for (k in listOf("onlineTitle", "onlineBody", "offlineTitle", "offlineBody")) assertTrue("$d $k", s.contains("name=\"chatHeader_$k\""))
            assertTrue(d, Regex("name=\"chatHeader_onlineBody\"[^>]*>[^<]*\\*\\*").containsMatchIn(s))
        }
        assertTrue(java.io.File("../app/src/main/java/team/sopa/boar/ui/BoldMarks.kt").readText().contains("fun boldMarks(text: String): AnnotatedString"))
    }
}
