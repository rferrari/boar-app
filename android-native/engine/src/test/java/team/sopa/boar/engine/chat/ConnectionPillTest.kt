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
        val dot = chat.substringAfter("private fun ConnectionLine(").substringBefore("\n}\n")
        assertTrue(dot.contains("delay(ConnectionPill.SETTLE_MS)") && dot.contains("ConnectionPill.lastShown = online") && dot.contains("delay(ConnectionPill.SHOW_MS)"))
        assertTrue(!chat.contains("offlineReady"))
    }

    @Test fun `the connection is on the model line, the name turns into ONLINE or OFFLINE for 2 s, the title never changes (option D)`() {
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        val header = chat.substringAfter("private fun Header(").substringBefore("\n}\n")
        // BOAR alone on the title line; the second line is the connection line with the model's name.
        assertTrue(header.contains("ConnectionLine(state.online, (headerModel ?: t(\"chatHeader.localLlmCore\")).uppercase(), busy = state.answeringId != null)"))
        assertTrue(!header.contains("ConnectionDot(") && !chat.contains("private fun ConnectionDot("))
        val line = chat.substringAfter("private fun ConnectionLine(").substringBefore("\n}\n")
        // One line, one style, cross-faded: nothing changes size. Reduce motion: instant.
        assertTrue(line.contains("Crossfade(expanded, animationSpec = androidx.compose.animation.core.tween(if (animOff) 0 else 250)"))
        assertTrue(line.contains("if (word) Text(label, style = style, color = tint") && line.contains("else Text(model, style = style, color = c.textDim"))
        assertTrue(!line.contains("animateContentSize") && !line.contains("RoundedCornerShape(4.dp)"))
        // Two steps (adam, Nov 13): a tap shows the word for 4 s, a tap on the word opens the dialog; TalkBack opens it at once.
        assertTrue(line.contains(".clickable { if (expanded) info = true else tapped++ }"))
        assertTrue(line.contains("delay(ConnectionPill.TAP_SHOW_MS)") && ConnectionPill.TAP_SHOW_MS == 4_000L && ConnectionPill.SHOW_MS == 2_000L)
        assertTrue(line.contains("clearAndSetSemantics { contentDescription = spoken; role = androidx.compose.ui.semantics.Role.Button; onClick(label) { info = true; true } }"))
        assertTrue(line.contains("if (online) Modifier.background(c.amber400, CircleShape) else Modifier.border(1.5.dp, MOON.copy(alpha = 0.55f), CircleShape)"))
        // The details are a sheet now (the connection, then the answer models), not a dialog.
        assertTrue(line.contains("if (info) ConnectionSheet(online, busy, onCatalog = { info = false; onCatalog() }) { info = false }") && !line.contains("AlertDialog"))
        assertTrue(chat.substringAfter("private fun ConnectionSheet(").substringBefore("\n}\n").contains("boldMarks(t(if (online) \"chatHeader.onlineBody\" else \"chatHeader.offlineBody\"))"))
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
            // It ends with what the connection is used for: no airplane-mode dare (adam chose (a), Nov 13).
            val online = Regex("name=\"chatHeader_onlineBody\"[^>]*>([^<]*)<").find(s)!!.groupValues[1]
            assertTrue(d, !online.contains("airplane") && !online.contains("avião") && (online.endsWith("turn that on.") || online.endsWith("ligar isso.")))
        }
        assertTrue(java.io.File("../app/src/main/java/team/sopa/boar/ui/BoldMarks.kt").readText().contains("fun boldMarks(text: String): AnnotatedString"))
    }

    /** adam, Nov 13: a tap opens a sheet that says the dot is only an indicator, lists the answer models on the phone and links the catalog. */
    @Test fun `the connection sheet lists the installed answer models and opens the catalog`() {
        val src = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        val sheet = src.substringAfter("private fun ConnectionSheet(").substringBefore("\n}\n")
        assertTrue(sheet.contains("native.chat.connectionOnly"))
        assertTrue(sheet.contains("AssetKind.LLM") && sheet.contains("?.present == true"))
        assertTrue("the switch goes through the catalog's own view model", sheet.contains("models.use(a)"))
        assertTrue("no switch while an answer is being written", sheet.contains("!busy"))
        assertTrue(sheet.contains("native.chat.openCatalog") && src.contains("onOpenRoute(\"models\")"))
        for (dir in listOf("values", "values-pt")) {
            val xml = java.io.File("../app/src/main/res/$dir/strings_native.xml").readText()
            for (k in listOf("connectionOnly", "answerModel", "noModels", "modelBusy", "openCatalog")) assertTrue("$dir $k", xml.contains("name=\"native_chat_$k\""))
        }
    }

    /** adam, Nov 13: the bar under the chat is less tall, and the round buttons keep their 48 dp. */
    @Test fun `the input bar is compact and keeps 48 dp buttons`() {
        val src = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/PromptBar.kt").readText()
        assertTrue(src.contains("private val BAR_PAD = 4.dp") && src.contains("vertical = BAR_PAD"))
        assertTrue(src.contains("size(48.dp)"))
    }
}
