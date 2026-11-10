package team.sopa.boar.engine.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.settings.AppSettings
import team.sopa.boar.engine.settings.UserProfile
import java.io.File
import java.nio.file.Files

/** Layout B+ as an experiment: Pro's setting, default "today", and what the chat draws with it on. */
class SourcesLayoutTest {
    private val chat = File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
    private val above = File("../app/src/main/java/team/sopa/boar/ui/chat/SourcesAbove.kt").readText()

    @Test fun `the layout is today by default and above only in Pro`() {
        val f = Files.createTempDirectory("boar-layout").toFile().resolve("settings.json")
        val s = AppSettings(f)
        s.userProfile = UserProfile.PRO
        assertEquals(AppSettings.SOURCES_TODAY, s.sourcesLayout)
        s.sourcesLayout = AppSettings.SOURCES_ABOVE
        assertEquals(AppSettings.SOURCES_ABOVE, AppSettings(f).sourcesLayout)
        s.userProfile = UserProfile.EXPLORER
        assertEquals(AppSettings.SOURCES_TODAY, s.sourcesLayout)
        s.userProfile = UserProfile.PRO
        s.resetPreferences()
        assertEquals(AppSettings.SOURCES_TODAY, AppSettings(f).sourcesLayout)
    }

    @Test fun `with B+ the block is above, the list under the answer and Staged's cards are not drawn`() {
        assertTrue(chat.contains("if (above) SourcesAbove("))
        assertTrue(chat.contains("if (m.sources.isNotEmpty() && !above && !sourcesShownAbove) SourceFootnotes("))
        assertTrue(chat.contains("if (!above) m.staged?.let { StagedCard("))
        // Drawn from the sources alone, whether the answer streams or not: it isn't removed when the answer starts.
        assertTrue(chat.indexOf("if (above) SourcesAbove(") < chat.indexOf("MarkdownMessage(shown, streaming"))
    }

    @Test fun `the block is capped at 35 percent of the screen, folds per answer, and n opens the passage`() {
        assertTrue(above.contains("screenHeightDp * 0.35f"))
        assertTrue(above.contains("heightIn(max = cap)"))
        assertTrue(above.contains("SourcesFold.isOpen(answerId)"))
        assertTrue(chat.contains("onCite = if (above && !streaming)"))
        assertTrue(File("../app/src/main/java/team/sopa/boar/ui/chat/Markdown.kt").readText().contains("LinkAnnotation.Clickable"))
    }

    @Test fun `its strings are in English and Portuguese`() {
        for (dir in listOf("values", "values-pt")) {
            val xml = File("../app/src/main/res/$dir/strings_native.xml").readText()
            for (k in listOf("native_sources_title", "native_sources_passages", "native_tweaks_sourcesLayout", "native_tweaks_sourcesToday", "native_tweaks_sourcesAbove"))
                assertTrue("$dir $k", xml.contains("name=\"$k\""))
        }
    }
}
