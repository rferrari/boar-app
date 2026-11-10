package team.sopa.boar.engine.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step one of a visual: facts with numbers from earlier answers of the same chat, no model. */
class VisualContextTest {
    private val brazil = VisualContext.Exchange("What is the population of Brazil?", "Brazil's population is 203.1 million [1]. It is the largest country in South America.")
    private val volcano = VisualContext.Exchange("How do volcanoes form?", "Mount Etna is 3,357 m high. Magma rises through the crust.")
    private val russia = VisualContext.Exchange("And Russia?", "Russia's population is 146.4 million, spread over 17,098,246 km².")

    @Test fun `a follow-up gets the numbers of the earlier answer on the same topic`() {
        val e = VisualContext.earlier(russia, listOf(volcano, brazil))
        assertEquals(listOf("What is the population of Brazil? — Brazil's population is 203.1 million."), e.facts)
        assertTrue(e.sourceText.contains("203.1 million"))
        assertFalse(e.sourceText.contains("Etna"))
    }

    @Test fun `with the earlier facts, a visual across both answers is grounded`() {
        val e = VisualContext.earlier(russia, listOf(brazil))
        val raw = """{"type":"bars","title":"Population","unit":"million","items":[{"label":"Brazil","value":203.1},{"label":"Russia","value":146.4}]}"""
        assertTrue(Visual.parse(raw, russia.answer + "\n" + e.sourceText) is Visual.Parsed.Ok)
        // Without them, Brazil's number isn't in the answer: refused.
        assertEquals(Visual.Reason.INVENTED_NUMBERS, (Visual.parse(raw, russia.answer) as Visual.Parsed.Rejected).reason)
    }

    @Test fun `no earlier answer, or none on the topic, adds nothing`() {
        assertTrue(VisualContext.earlier(russia, emptyList()).isEmpty)
        assertTrue(VisualContext.earlier(russia, listOf(volcano)).isEmpty)
    }

    @Test fun `only sentences with a number, deduplicated, not already in the answer`() {
        val again = VisualContext.Exchange("Brazil population again?", "Brazil's population is 203.1 million. Brasília is its capital.")
        val e = VisualContext.earlier(russia, listOf(brazil, again))
        assertEquals(1, e.facts.size)
        val inAnswer = VisualContext.Exchange("Russia population?", "Russia's population is 146.4 million, spread over 17,098,246 km².")
        assertTrue(VisualContext.earlier(russia, listOf(inAnswer)).isEmpty)
    }

    @Test fun `the earlier part stays short`() {
        val many = (1..10).map { i -> VisualContext.Exchange("Population of country $i?", (1..5).joinToString(" ") { j -> "Region $j of country $i has a population of ${i * 100 + j} million." }) }
        val e = VisualContext.earlier(russia, many)
        assertTrue(e.facts.size <= VisualContext.MAX_FACTS)
        assertTrue(e.facts.sumOf { it.length } <= VisualContext.MAX_CHARS)
        // The newest earlier exchanges come first; at most MAX_EXCHANGES of them are read.
        assertTrue(e.facts.first().startsWith("Population of country 10?"))
        assertFalse(e.facts.any { it.startsWith("Population of country 5?") })
    }

    @Test fun `the prompt lists the earlier facts only when there are some`() {
        val e = VisualContext.earlier(russia, listOf(brazil))
        val p = VisualContext.prompt(russia.question, russia.answer, e)
        assertTrue(p.startsWith("QUESTION: And Russia?\n\nANSWER:\nRussia's population"))
        assertTrue(p.contains("EARLIER IN THIS CHAT"))
        assertTrue(p.contains("- What is the population of Brazil? — Brazil's population is 203.1 million."))
        assertFalse(VisualContext.prompt(russia.question, russia.answer, VisualContext.Earlier(emptyList(), "")).contains("EARLIER"))
        assertTrue(Visual.PROMPT.contains("EARLIER IN THIS CHAT"))
    }

    @Test fun `sentences split at stops and line breaks, citations removed`() =
        assertEquals(listOf("Etna is 3,357 m high.", "It erupted in 2021"),
            VisualContext.sentencesWithNumbers("Etna is 3,357 m high [2]. Magma rises.\n- It erupted in 2021"))

    @Test fun `the answer's sources give their numbers, the closest to the topic first`() {
        val passages = listOf(
            "Economy of Brazil" to "Brazil has the largest economy in South America. Its GDP grew 2.9% in 2023.",
            "Brazil" to "Brazil covers 8,515,767 km², the fifth-largest area in the world. Its capital is Brasília.",
            "Football" to "The 1950 World Cup was held in Brazil.",
        )
        val s = VisualContext.fromSources("Compare the area and economy of Brazil and Russia", "Brazil's GDP was $2.642 trillion.", passages)
        assertTrue(s.facts.any { it.startsWith("Brazil — Brazil covers 8,515,767 km²") })
        assertTrue(s.sourceText.contains("8,515,767"))
        assertFalse(s.facts.any { it.contains("Brasília") })
        val p = VisualContext.prompt("q", "a", VisualContext.Earlier(emptyList(), ""), s)
        assertTrue(p.contains("FROM THE SOURCES"))
        assertTrue(Visual.PROMPT.contains("FROM THE SOURCES"))
    }

    @Test fun `no passages, no sources part`() =
        assertFalse(VisualContext.prompt("q", "a", VisualContext.Earlier(emptyList(), ""), VisualContext.fromSources("q", "a", emptyList())).contains("FROM THE SOURCES"))
}
