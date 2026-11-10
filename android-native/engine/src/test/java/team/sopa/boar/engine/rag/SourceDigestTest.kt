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
}
