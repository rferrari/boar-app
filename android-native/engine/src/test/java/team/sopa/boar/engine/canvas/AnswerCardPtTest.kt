package team.sopa.boar.engine.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coder's F3 check (15 Nov, t9 0020: with item 2 of 0010): a model's card under a Portuguese answer dropped Brasil, whose
 * area the answer gives twice, and wrote the areas English-style (3,287,263) under an answer that wrote 3.287.263.
 */
class AnswerCardPtTest {
    // The 1.7B's answer on the F3, as shown (its facts are wrong; the card is checked against it, not against the world).
    private val answer = "O Brasil é o país com maior área no mundo [2], com uma área total de aproximadamente 8.515.814 km². " +
        "A Índia é o segundo maior país em área [2], com uma área de aproximadamente 3.287.263 km². " +
        "A China é o terceiro maior país em área [2], com uma área de aproximadamente 9.599.185 km². " +
        "O Brasil, por sua vez, é o maior país do hemisfério sul, com uma área de 8.515.814 km², enquanto a China é o maior país do hemisfério norte."
    private val raw = """{"type":"table","title":"Maiores paises do mundo em area","columns":["Pais","Area (km²)"],"rows":[["Brasil","8,515,814"],["Índia","3,287,263"],["China","9,599,185"]]}"""

    @Test fun `a name in the next sentence doesn't take this sentence's number`() {
        val p = Visual.parse(raw, answer) as Visual.Parsed.Ok
        assertTrue(p.leftOut.toString(), p.leftOut.isEmpty())
        assertEquals(listOf("Brasil", "Índia", "China"), (p.visual as Visual.Table).rows.map { it[0] })
    }

    @Test fun `the card writes each number as the answer does`() {
        val p = Visual.parse(raw, answer) as Visual.Parsed.Ok
        val card = AnswerCard.ofVisual(p.visual, p.leftOut, "t", answer)
        assertEquals(listOf("8.515.814", "3.287.263", "9.599.185"), (card.body as AnswerCard.Body.Table).rows.map { it[1] })
        // An English answer keeps its own spelling; a number the answer doesn't hold stays as the model wrote it.
        assertEquals("8,849 m", VisualCheck.asWritten("8,849 m", "Everest is 8,849 m tall."))
        assertEquals("12,5 %", VisualCheck.asWritten("12,5 %", "Nothing here."))
    }

    @Test fun `the swap the pairing rule was made for is still refused`() {
        val en = "Paris has 2.1 million people. Lyon has 0.5 million people."
        val swapped = """{"type":"bars","title":"Pop","unit":"million","items":[{"label":"Paris","value":0.5},{"label":"Lyon","value":2.1}]}"""
        assertTrue(Visual.parse(swapped, en) !is Visual.Parsed.Ok || (Visual.parse(swapped, en) as Visual.Parsed.Ok).leftOut.isNotEmpty())
    }

    @Test fun `a swapped card is refused in every layout, sentences apart too (validator R-B1)`() {
        val swapped = """{"type":"bars","title":"Pop","unit":"million","items":[{"label":"Paris","value":0.5},{"label":"Lyon","value":2.1}]}"""
        val right = """{"type":"bars","title":"Pop","unit":"million","items":[{"label":"Paris","value":2.1},{"label":"Lyon","value":0.5}]}"""
        for (text in listOf(
            "Paris has 2.1 million people. Lyon has 0.5 million people.",
            "Paris. It has 2.1 million people. Lyon. It has 0.5 million people.",
            "Paris is a city. It has 2.1 million people. Lyon is a city. It has 0.5 million people.",
            "## Paris\nIt has 2.1 million people.\n## Lyon\nIt has 0.5 million people.",
        )) {
            val bad = Visual.parse(swapped, text)
            assertTrue(text + " -> " + bad, bad !is Visual.Parsed.Ok || (bad as Visual.Parsed.Ok).leftOut.isNotEmpty())
            val ok = Visual.parse(right, text)
            assertTrue(text + " -> " + ok, ok is Visual.Parsed.Ok && ok.leftOut.isEmpty())
        }
    }
}
