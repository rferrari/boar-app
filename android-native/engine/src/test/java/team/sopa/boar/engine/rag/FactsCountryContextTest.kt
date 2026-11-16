package team.sopa.boar.engine.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 0110 item 5: a question naming two or more countries gets the table's dated figures as one more source. */
class FactsCountryContextTest {
    private val facts = Facts(java.io.File("../app/src/labs/plugins/facts/res/raw/lab_facts_facts_v1.json").readText())

    @Test fun `two countries named - their population, area and capital, dated, as one source`() {
        val c = facts.countryContext("Compare Brazil and Russia")!!
        assertEquals("Facts: Brazil and Russia", c.title)
        assertEquals(
            "Brazil: population 214,211,951 (2026); area 8,515,767 km²; capital Brasília.\n" +
                "Russia: population 146,119,928 (2025); area 17,075,400 km²; capital Moscow.\n" +
                "Figures from Wikidata (CC0), offline table as of 2026-10-06.",
            c.body,
        )
        assertEquals("Wikidata (CC0), offline table as of 2026-10-06", c.source)
        // Not answered at once by Facts (no single property asked): the model writes it, with this source.
        assertNull(facts.answer("Compare Brazil and Russia"))
        assertTrue(facts.countryContext("Which is bigger, Japan, Germany or France?")!!.body.lines().size == 4)
    }

    @Test fun `fewer than two countries - nothing added`() {
        for (q in listOf("Tell me about Brazil", "Compare Paris and Brazil", "Compare cats and dogs", "What is photosynthesis?"))
            assertNull(q, facts.countryContext(q))
    }

    @Test fun `the Facts lab adds it after the search's own sources, and search keeps room for one`() {
        val lab = java.io.File("../app/src/labs/plugins/facts/java/team/sopa/boar/labs/facts/FactsLab.kt").readText()
        assertTrue(lab.contains("val extra = table?.countryContext(query)") && lab.contains("chunks.take(topK) + extra"))
        val services = java.io.File("../app/src/main/java/team/sopa/boar/app/BoarServices.kt").readText()
        assertTrue(services.contains("return chunks.take(topK + team.sopa.boar.plugins.EXTRA)"))
    }
}
