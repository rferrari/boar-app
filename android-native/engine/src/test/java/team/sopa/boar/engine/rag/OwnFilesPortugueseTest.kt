package team.sopa.boar.engine.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * t9 0026 (blood test e): a Portuguese question about the user's English file gets its English key terms, in Local Files
 * and in the mixed search (Retriever.retrieveAll runs the English pass when the question looks Portuguese). On the F3,
 * "resuma meu exame de sangue" and "meu colesterol está alto?" missed blood-test-2026: neither read as Portuguese.
 */
class OwnFilesPortugueseTest {
    private val lex get() = RagTextGoldenTest.lexicon

    @Test fun `the four Portuguese questions read as Portuguese and name their English terms`() {
        val cases = mapOf(
            "resuma meu exame de sangue" to listOf("Blood test"),
            "quais são os resultados do meu exame de sangue?" to listOf("Blood test"),
            "meu colesterol está alto?" to listOf("Cholesterol"),
            "o que diz meu exame de sangue sobre vitamina D?" to listOf("Blood test", "Vitamin D"),
        )
        for ((q, names) in cases) {
            assertTrue(q, PtLexicon.looksPortuguese(q))
            assertEquals(q, names, PtLexicon.englishNamesIn(q, lex))
        }
    }

    @Test fun `the English questions stay English`() {
        for (q in listOf("summarize my blood test", "is my cholesterol high?", "What is my resume about?"))
            assertTrue(q, !PtLexicon.looksPortuguese(q))
    }

    @Test fun `lab terms the lexicon lacks`() {
        assertEquals(listOf("Triglyceride"), PtLexicon.englishNamesIn("meus triglicerídeos estão altos?", lex))
        assertEquals(listOf("Creatinine"), PtLexicon.englishNamesIn("minha creatinina está normal?", lex))
        assertEquals(listOf("Prescription"), PtLexicon.englishNamesIn("onde está minha receita médica?", lex))
        // A recipe stays a recipe: bare "receita" isn't read as a prescription.
        assertTrue(!PtLexicon.englishNamesIn("receita de bolo de cenoura", lex).contains("Prescription"))
    }
}
