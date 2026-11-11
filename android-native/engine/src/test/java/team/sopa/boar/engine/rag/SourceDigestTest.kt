package team.sopa.boar.engine.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sources block above the answer (B+): rows, the key sentence, the lead fallback, the marks and [n] taps. */
class SourceDigestTest {
    private val canberra1 = Triple("Canberra", "canberra", "Canberra is the capital city of Australia. It was chosen in 1908 as a compromise between Sydney and Melbourne, the two largest cities.")
    private val act = Triple("Australian Capital Territory", "act", "The territory was created in 1911. It surrounds the city.")
    private val canberra2 = Triple("Canberra", "canberra", "The city was designed by Walter Burley Griffin and Marion Mahony Griffin after an international competition.")
    private val q = "Canberra: why Australia built a new capital as a compromise"

    @Test fun `passages of one article make one row, numbered as the answer cites them`() {
        val rows = SourceDigest.rows(q, listOf(canberra1, act, canberra2))
        assertEquals(listOf("Canberra", "Australian Capital Territory"), rows.map { it.title })
        assertEquals(setOf(1, 3), rows[0].passages.map { it.number }.toSet())
        assertEquals(listOf(2), rows[1].passages.map { it.number })
    }

    @Test fun `the key sentence is the one that answers, with the question's words marked`() {
        val best = SourceDigest.rows("Why was Canberra chosen as a compromise between Sydney and Melbourne?", listOf(canberra1)).single().best
        assertTrue(best.sentence.startsWith("It was chosen in 1908 as a compromise"))
        assertFalse(best.lead)
        val marked = best.marks.map { best.sentence.substring(it) }
        assertTrue("compromise" in marked)
        assertFalse("the" in marked)
    }

    @Test fun `no sentence matching gives the article's first sentence, as a lead, nothing marked`() {
        val off = Triple("Football", "football", "The 1950 World Cup was held in Brazil. Uruguay won it.")
        val p = SourceDigest.rows(q, listOf(off)).single().best
        assertTrue(p.lead)
        assertEquals("The 1950 World Cup was held in Brazil.", p.sentence)
        assertTrue(p.marks.isEmpty())
    }

    @Test fun `a long sentence is cut at 25 whole words`() {
        val long = (1..40).joinToString(" ") { "word$it" }
        val cut = SourceDigest.cut(long)
        assertEquals(SourceDigest.MAX_WORDS, cut.removeSuffix("…").split(" ").size)
        assertTrue(cut.endsWith("word25…"))
        assertEquals("Short one.", SourceDigest.cut("Short one."))
    }

    @Test fun `a tap on n opens the row that holds it`() {
        val rows = SourceDigest.rows(q, listOf(canberra1, act, canberra2))
        assertEquals("Canberra", SourceDigest.rowOf(rows, 3)?.title)
        assertEquals("Australian Capital Territory", SourceDigest.rowOf(rows, 2)?.title)
        assertNull(SourceDigest.rowOf(rows, 9))
    }

    @Test fun `Portuguese questions mark folded words`() {
        val pt = Triple("Brasil", "brasil", "A população do Brasil é de 203 milhões de habitantes.")
        val p = SourceDigest.rows("Qual é a populacao do Brasil?", listOf(pt)).single().best
        assertTrue(p.marks.map { p.sentence.substring(it) }.containsAll(listOf("população", "Brasil")))
    }

    @Test fun `the sheet goes on after the card's sentence, never repeating it`() {
        val body = "Canberra is the capital of Australia. It was chosen in 1908 as a compromise between Sydney and Melbourne. Building began in 1913."
        val rows = SourceDigest.rows("why was Canberra chosen as capital", listOf(Triple("Canberra", "canberra", body)))
        val p = rows.single().best
        val sheet = SourceDigest.sheet(p)
        assertTrue(!sheet.after.contains(p.sentence.removeSuffix("…")))
        assertTrue(sheet.after.isNotEmpty() || sheet.before != null)
    }

    @Test fun `a sentence that ends the passage shows what came before it`() {
        val p = SourceDigest.Passage(1, "Brasília was built in 41 months. It became the capital in 1960.", "It became the capital in 1960.", false, emptyList())
        assertEquals(SourceDigest.Sheet("", "Brasília was built in 41 months."), SourceDigest.sheet(p))
    }

    @Test fun `a lead shows the passage after its first sentence, in Portuguese too`() {
        val body = "Brasília é a capital do Brasil. Foi inaugurada em 1960 por Juscelino Kubitschek."
        val p = SourceDigest.rows("xyz", listOf(Triple("Brasília", "brasilia", body))).single().best
        assertTrue(p.lead)
        assertEquals("Foi inaugurada em 1960 por Juscelino Kubitschek.", SourceDigest.sheet(p).after)
    }

    @Test fun `a cut sentence is found by its start and not repeated`() {
        val long = (1..40).joinToString(" ") { "word$it" } + "."
        val p = SourceDigest.Passage(1, "$long Next sentence.", SourceDigest.cut(long), false, emptyList())
        val after = SourceDigest.sheet(p).after
        assertTrue(!after.contains("word1 word2"))
        assertTrue(after.endsWith("Next sentence."))
    }

    @Test fun `the same article's other passages exclude the one shown`() {
        val rows = SourceDigest.rows("capital", listOf(Triple("Canberra", "c", "Canberra is the capital."), Triple("Sydney", "s", "Sydney is big."),
            Triple("Canberra", "c", "The capital moved in 1927.")))
        assertEquals(listOf(3), SourceDigest.others(rows, 1).map { it.number })
        assertEquals(listOf(1), SourceDigest.others(rows, 3).map { it.number })
        assertTrue(SourceDigest.others(rows, 2).isEmpty())
    }

    // The validator's four cases (S-1, Nov 11), and the sentence written twice.
    @Test fun `the card's sentence isn't repeated when the passage has a newline, a double space or a no-break space in it`() {
        fun norm(x: String) = x.replace(Regex("[\\s\u00A0]+"), " ").trim()
        val words = (1..40).joinToString(" ") { "capital$it" }
        for ((name, body) in listOf(
            "newline in a long sentence" to words.replaceFirst("capital5 ", "capital5\n") + ". Next sentence here.",
            "double space in a long sentence" to words.replaceFirst("capital5 ", "capital5  ") + ". Next sentence here.",
            "double space in a short one" to "The capital of  Australia is Canberra. It was chosen in 1908.",
            "newline in a short one" to "The capital of Australia\nis Canberra. It was chosen in 1908.",
            "no-break space" to "The capital of\u00A0Australia is Canberra. It was chosen in 1908.",
            "the same sentence twice" to "The capital of Australia is Canberra. It was chosen in 1908. The capital of Australia is Canberra.",
        )) {
            val p = SourceDigest.rows("capital of Australia capital1", listOf(Triple("T", "t", body))).single().best
            val sheet = SourceDigest.sheet(p)
            val key = norm(p.sentence.removeSuffix("…"))
            assertTrue("$name: <${sheet.after}>", !norm(sheet.after).contains(key) && !norm(sheet.before ?: "").contains(key))
            assertTrue("$name: empty", sheet.after.isNotEmpty() || sheet.before != null)
        }
    }
}
