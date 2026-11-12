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

    @Test fun `ONLINE or OFFLINE is the pill beside BOAR, in kept space, so nothing in the header moves (t9 1950)`() {
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        val header = chat.substringAfter("private fun Header(").substringBefore("\n}\n")
        assertTrue(header.contains("ConnectionDot(state.online)") && !header.contains("Crossfade("))
        // The model line is only the model.
        assertTrue(header.contains("Text((headerModel ?: t(\"chatHeader.localLlmCore\")).uppercase(), style = team.sopa.boar.ui.HeaderMetrics.subtitleStyle(type)"))
        val dot = chat.substringAfter("private fun ConnectionDot(").substringBefore("\n}\n")
        // Both drawn, one box: the pill and the dot cross-fade by alpha; nothing is added or removed, so nothing resizes.
        assertTrue(!dot.contains("animateContentSize") && !dot.contains("if (expanded) Row("))
        assertTrue(dot.contains("Modifier.alpha(pill)") && dot.contains("Modifier.alpha(1f - pill)") && dot.contains("tween(if (animOff) 0 else 250)"))
        // Two steps (adam, Nov 13): the dot expands into the pill for 4 s, a tap on the pill opens the dialog; TalkBack opens it at once.
        assertTrue(dot.contains(".clickable { if (expanded) info = true else tapped++ }"))
        assertTrue(dot.contains("delay(ConnectionPill.TAP_SHOW_MS)") && ConnectionPill.TAP_SHOW_MS == 4_000L && ConnectionPill.SHOW_MS == 2_000L)
        assertTrue(dot.contains("clearAndSetSemantics { contentDescription = spoken; role = androidx.compose.ui.semantics.Role.Button; onClick(label) { info = true; true } }"))
        assertTrue(dot.contains("if (online) Modifier.background(c.amber400, CircleShape) else Modifier.border(1.5.dp, MOON.copy(alpha = 0.55f), CircleShape)"))
        assertTrue(dot.contains("boldMarks(t(if (online) \"chatHeader.onlineBody\" else \"chatHeader.offlineBody\"))"))
    }

    @Test fun `every page header is the chat header, 62 dp, both lines get the whole bar, the same styles`() {
        val ph = java.io.File("../app/src/main/java/team/sopa/boar/ui/PageHeader.kt").readText()
        assertTrue(ph.contains("val ROW = 42.dp") && ph.contains("val PAD = 10.dp") && ph.contains("val HEIGHT = ROW + PAD * 2"))
        assertTrue(ph.contains(".height(HeaderMetrics.HEIGHT).padding(horizontal = 12.dp),") && ph.contains("FixedSizeChrome"))
        assertTrue(ph.contains("style = HeaderMetrics.titleStyle(Boar.type)") && ph.contains("style = HeaderMetrics.subtitleStyle(Boar.type)"))
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("Row(Modifier.height(team.sopa.boar.ui.HeaderMetrics.HEIGHT), verticalAlignment = Alignment.CenterVertically)"))
        assertTrue(chat.contains("Text(\"BOAR\", style = team.sopa.boar.ui.HeaderMetrics.titleStyle(type)"))
        assertTrue(!chat.contains("HeaderMetrics.ROW)"))
    }

    @Test fun `both lines fit the 62 dp bar up to the 1_3 font scale, at every text size`() {
        // titleLg and mono.xs at Compact and Standard (Large is held at Standard in the header): line heights x 1.3.
        for ((title, sub) in listOf(18.5f to 9.25f, 20f to 10f)) {
            fun scaled(x: Float) = when { x <= 11 -> 12f; x == 12f -> 13f; x == 13f -> 14f; x <= 15 -> 16f; else -> x }
            val lines = (scaled(title) * 1.15f + scaled(sub) * 1.25f) * 1.3f
            assertTrue("$title: $lines", lines <= 62f - 4f)
        }
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
