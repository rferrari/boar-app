package team.sopa.boar.engine.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 0110 item 6, point 2: "na natureza" is "in the wild", not the article Nature. */
class InTheWildTest {
    private val lexicon = RagTextGoldenTest.lexicon

    @Test fun `in the wild never searches Nature`() {
        for (q in listOf("Como purificar água na natureza, passo a passo?", "Como sobreviver em meio à natureza?", "Acampar em plena natureza é seguro?",
            "Como achar comida no meio da natureza?"))
            assertTrue(q, "Nature" !in PtLexicon.englishNamesIn(q, lexicon))
    }

    @Test fun `nature itself still does`() {
        assertEquals(listOf("Nature"), PtLexicon.englishNamesIn("O que é a natureza?", lexicon))
        assertEquals(listOf("Nature", "Brazil"), PtLexicon.englishNamesIn("Proteção da natureza no Brasil", lexicon))
    }
}
