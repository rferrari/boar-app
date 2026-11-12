package team.sopa.boar.engine.answer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, Nov 12: an answer with no sources shows no sources block, no empty Staged area, no "+0 sources". */
class NoSourcesTest {
    private val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
    private val card = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/StagedCard.kt").readText()

    @Test fun `Staged's area is drawn only with something in it`() {
        assertFalse(StagedShown.any(0, 0, 0, false))
        assertTrue(StagedShown.any(1, 0, 0, false))
        assertTrue(StagedShown.any(0, 0, 0, true))
        assertTrue(card.contains("if (!StagedShown.any(v.keyFacts.size, v.more.size, v.added, streaming && written && v.step != null)) return"))
    }

    @Test fun `no sources block, no number check and no plus-zero without sources`() {
        assertTrue(chat.contains("val above = m.sources.isNotEmpty() && m.staged == null"))
        assertTrue(chat.contains("if (!streaming && m.sources.isNotEmpty()) m.staged?.let { StagedCheck(it) }"))
        // "+N sources" and "added N" need N > 0.
        assertTrue(card.contains("if (v.more.isNotEmpty()) MoreSources(") && card.contains("if (v.added > 0) Text("))
    }

    @Test fun `sources but no matching sentence, the sources link, never an empty block (validator S-2)`() {
        assertTrue(card.contains("} else if (v.more.isNotEmpty()) {\n            // No sentence matched the question"))
    }
}
