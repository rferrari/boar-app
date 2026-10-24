package team.sopa.boar.engine.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.db.JdbcSqlite
import team.sopa.boar.engine.rag.Chunk
import team.sopa.boar.engine.rag.KnowledgeBase
import java.time.LocalDate

/** Boar Labs' Daily Quiz (Oct 24): questions from the library's opening sentences, the title hidden. */
class DailyQuizTest {
    private val library = listOf(
        "Boiling" to "Boiling is the rapid vaporization of a liquid, which occurs when it is heated to its boiling point. More.",
        "Monsoon" to "A monsoon is traditionally a seasonal reversing wind accompanied by corresponding changes in precipitation.",
        "Water purification" to "Water purification is the process of removing undesirable chemicals and contaminants from water.",
        "Nosebleed" to "A nosebleed, also known as epistaxis, is an instance of bleeding from the nose.",
        "Plate tectonics" to "Plate tectonics is the scientific theory that the Earth's lithosphere comprises a number of large plates.",
        "Greenhouse effect" to "The greenhouse effect occurs when greenhouse gases in a planet's atmosphere insulate the planet from losing heat.",
        "Kyoto" to "Kyoto is the capital city of Kyoto Prefecture in the Kansai region of Japan, and the former imperial capital.",
        "Tsunami" to "A tsunami is a series of waves in a water body caused by the displacement of a large volume of water.",
    )

    @Test fun `the title's words are hidden, with case, plural and accents`() {
        assertEquals("▢▢▢ is the rapid vaporization of a liquid.", DailyQuiz.mask("Boiling is the rapid vaporization of a liquid.", "Boiling"))
        assertEquals("▢▢▢ ▢▢▢ are processes that remove chemicals.", DailyQuiz.mask("Water purifications are processes that remove chemicals.", "Water purification"))
        assertEquals("▢▢▢ é a capital do Brasil.", DailyQuiz.mask("Brasília é a capital do Brasil.", "Brasilia"))
        // Short words of the title ("of") stay; every other word of it goes, wherever it appears.
        assertEquals("The ▢▢▢ of ▢▢▢ is a legal ▢▢▢.", DailyQuiz.mask("The Code of Hammurabi is a legal code.", "Code of Hammurabi"))
    }

    @Test fun `the clue is the article's first sentence, never a fragment`() {
        assertEquals("Boiling is the rapid vaporization of a liquid, which occurs when it is heated to its boiling point.", DailyQuiz.opening(library[0].second))
        assertNull(DailyQuiz.opening("Too short."))
    }

    @Test fun `the same day gives the same quiz, another day another`() {
        val d = LocalDate.of(2026, 10, 24)
        val a = DailyQuiz.daily(library, d)
        assertEquals(a, DailyQuiz.daily(library, d))
        assertEquals(5, a.size)
        assertNotEquals(a, DailyQuiz.daily(library, d.plusDays(1)))
    }

    @Test fun `four different options, the answer among them, the clue hiding it`() {
        for (q in DailyQuiz.daily(library, LocalDate.of(2026, 10, 24))) {
            assertEquals(4, q.options.size)
            assertEquals(4, q.options.distinct().size)
            assertTrue(q.answer in 0..3)
            assertTrue(q.clue, q.clue.contains(DailyQuiz.MASK))
            assertFalse(q.clue, q.clue.contains(q.options[q.answer], ignoreCase = true))
        }
    }

    @Test fun `a small library gives fewer questions, never a crash`() {
        assertEquals(emptyList<DailyQuiz.Question>(), DailyQuiz.daily(library.take(3), LocalDate.of(2026, 10, 24)))
        // An opening that never names its title can't hide anything: skipped.
        val noTitle = library.take(4) + ("Zebra" to "This striped animal of Africa lives in herds on the open plains and grasslands.")
        assertTrue(DailyQuiz.daily(noTitle, LocalDate.of(2026, 10, 24), count = 10).none { it.options[it.answer] == "Zebra" })
    }

    @Test fun `the library's articles, never the user's files, a different handful per seed`() {
        val kb = KnowledgeBase(JdbcSqlite()).apply {
            library.forEachIndexed { i, (t, b) -> insertChunk(Chunk("wiki-$i", "wiki-$i", t, b), floatArrayOf(1f)) }
            createCollection("c", "mine", "secret.txt", 1, 1, 10, now = 1)
            insertChunk(Chunk("c-doc0-secret-c0", "c-doc0-secret", "secret", "My bank PIN is 4471 and nobody should see it.", collectionId = "c"), floatArrayOf(1f))
        }
        val all = kb.seedArticles(1, 100)
        assertEquals(library.size, all.size)
        assertTrue(all.none { it.first == "secret" })
        assertNotEquals(kb.seedArticles(1, 3), kb.seedArticles(2, 3))
    }
}
