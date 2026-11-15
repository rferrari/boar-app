package team.sopa.boar.engine.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 0226: the Portuguese word readings that picked weak sources, fixed case by case (measured 15 Nov). */
class PtReadingsTest {
    private val lx = RagTextGoldenTest.lexicon
    private fun names(q: String) = PtLexicon.englishNamesIn(q, lx)

    @Test fun `the tides are Tide, not Sea`() = assertEquals(listOf("Tide"), names("O que causa as marés?"))

    @Test fun `why the sky is blue is the sky's light, not the colour Blue`() {
        assertEquals(listOf("Diffuse sky radiation"), names("Por que o céu é azul?"))
        assertEquals(listOf("Blue"), names("O que é a cor azul?"))
    }

    @Test fun `a device is not Brickwork, a time unit names nothing, the capital of is not Capital city`() {
        assertFalse("Brickwork" in names("Um aparelho tem 12 GB de RAM. O sistema e o app ocupam 2 GB"))
        assertTrue(names("Um trem sai às 15h40 e a viagem leva 2 horas e 35 minutos. A que horas ele chega?").none { it in setOf("Minute", "Hour") })
        assertEquals(listOf("Australia"), names("Qual é a capital da Austrália?"))
    }

    @Test fun `Earth's tilt is Axial tilt, supply and demand with its article is Supply and demand`() {
        assertTrue("Axial tilt" in names("Como a inclinação da Terra cria as estações, e por que elas se invertem entre os hemisférios"))
        assertFalse("Orbital inclination" in names("Como a inclinação da Terra cria as estações"))
        assertTrue("Supply and demand" in names("Compare como a oferta e a demanda explicam a mudança de preços"))
    }

    @Test fun `Portuguese function words are not search terms (Pará came from para)`() {
        val q = RagPure.buildLexicalQuery("Como as monções se formam, e por que importam para a agricultura no sul da Ásia")!!
        assertTrue(q.match, "\"para\"" !in q.match && "\"como\"" !in q.match && "\"por\"" !in q.match)
        // English keeps its words: OS is the operating system.
        assertTrue(RagPure.buildLexicalQuery("The OS and app overhead take 2GB")!!.match.contains("\"os\""))
    }
}
