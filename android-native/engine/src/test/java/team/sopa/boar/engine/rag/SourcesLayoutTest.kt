package team.sopa.boar.engine.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.settings.AppSettings
import java.io.File
import java.nio.file.Files

/** Layout B+ as an experiment: Pro's setting, default "today", and what the chat draws with it on. */
class SourcesLayoutTest {
    private val chat = File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
    private val above = File("../app/src/main/java/team/sopa/boar/ui/chat/SourcesAbove.kt").readText()

    @Test fun `the layout is today by default, above when stored (no profile gates it since 2_1_0)`() {
        val f = Files.createTempDirectory("boar-layout").toFile().resolve("settings.json")
        val s = AppSettings(f)
        assertEquals(AppSettings.SOURCES_TODAY, s.sourcesLayout)
        s.sourcesLayout = AppSettings.SOURCES_ABOVE
        assertEquals(AppSettings.SOURCES_ABOVE, AppSettings(f).sourcesLayout)
        s.resetPreferences()
        assertEquals(AppSettings.SOURCES_TODAY, AppSettings(f).sourcesLayout)
    }

    @Test fun `its strings are in English and Portuguese`() {
        for (dir in listOf("values", "values-pt")) {
            val xml = File("../app/src/main/res/$dir/strings_native.xml").readText()
            for (k in listOf("native_sources_title", "native_sources_passages", "native_tweaks_sourcesLayout", "native_tweaks_sourcesToday", "native_tweaks_sourcesAbove"))
                assertTrue("$dir $k", xml.contains("name=\"$k\""))
        }
    }


    // adam, Nov 12: one design for everyone; a tap goes straight to the article at the passage; nothing shown twice.
    @Test fun `one sources design, the block above the answer unless Staged shows its quotes, no list under the answer`() {
        assertTrue(chat.contains("val above = m.sources.isNotEmpty() && m.staged == null"))
        assertTrue(chat.contains("if (above) SourcesAbove(m.id, question, m.sources) { read(it) }"))
        assertTrue(!chat.contains("SourceFootnotes(") && !chat.contains("PassageSheet("))
        assertTrue(chat.indexOf("if (above) SourcesAbove(") < chat.indexOf("MarkdownMessage(shown, streaming"))
    }

    @Test fun `a tap on a source, a quote or an n opens the article at the passage`() {
        assertTrue(chat.contains("fun read(n: Int) { m.sources.getOrNull(n - 1)?.let(onReadSource) }"))
        assertTrue(chat.contains("onCite = if (m.sources.isNotEmpty() && !streaming) ({ n -> if (n in 1..m.sources.size) read(n) }) else null"))
        val staged = File("../app/src/main/java/team/sopa/boar/ui/chat/StagedCard.kt").readText()
        assertTrue(staged.contains(".clickable { onRead(k.source) }"))
        // The reader scrolls to the passage it's given and marks it.
        val reader = File("../app/src/main/java/team/sopa/boar/ui/chat/SourceReader.kt").readText()
        assertTrue(reader.contains("SourceArticle.passageAt(a.text, passage)") && reader.contains("scroll.animateScrollTo("))
        assertTrue(above.contains("SourcesFold.isOpen(answerId)") && above.contains("screenHeightDp * 0.35f"))
    }

    @Test fun `back from the article, the chat is where it was`() {
        assertTrue(chat.contains("val returning = remember { ChatScroll.restore(state.sessionId) }"))
        assertTrue(chat.contains("rememberLazyListState(returning?.first ?: 0, returning?.second ?: 0)"))
        assertTrue(chat.contains("var follow by remember { mutableStateOf(returning == null) }"))
    }
}
