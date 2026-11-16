package team.sopa.boar.engine.rag

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.answer.AnswerLanguage
import team.sopa.boar.engine.answer.AnswerLanguage.Lang

/** The validator's gate of eaa0161c, part D (t9 2226 item 6): R-D1 to R-D5. */
class PortugueseSearchLowsTest {
    private val lx = RagTextGoldenTest.lexicon
    private fun names(q: String) = PtLexicon.englishNamesIn(q, lx)
    @After fun reset() { AnswerLanguage.appLanguage = null }

    @Test fun `an English title among Portuguese words doesn't make the question English (R-D1)`() {
        AnswerLanguage.appLanguage = "pt"
        for (q in listOf("Resumo de The Matrix", "Letra de Let It Be")) assertEquals(q, Lang.PT, AnswerLanguage.of(q))
        assertEquals(Lang.EN, AnswerLanguage.of("What is the capital of Australia?"))
    }

    @Test fun `human nature and man in nature keep their articles (R-D2)`() {
        assertTrue("Human nature" in names("Existe maldade na natureza humana?"))
        assertTrue("Nature" in names("Qual o papel do homem na natureza?"))
        assertFalse("Nature" in names("Como purificar água na natureza, passo a passo?"))
    }

    @Test fun `céu azul is the sky's light only in a why or how question (R-D3)`() {
        assertEquals(listOf("Diffuse sky radiation"), names("Por que o céu é azul?"))
        for (q in listOf("Como fazer um céu azul no Photoshop?", "O que é o Céu Azul Futebol Clube?")) assertFalse(q, "Diffuse sky radiation" in names(q))
    }

    @Test fun `English acronyms keep their keyword, Portuguese questions still drop function words (R-D4)`() {
        for ((q, term) in listOf("What is DOS?" to "dos", "What is Na?" to "na", "What is a SEM?" to "sem", "Who is Uma Thurman?" to "uma"))
            assertTrue(q, RagPure.buildLexicalQuery(q)!!.match.contains("\"$term\""))
        assertFalse(RagPure.buildLexicalQuery("Como as monções se formam, e por que importam para a agricultura")!!.match.contains("\"para\""))
    }

    @Test fun `a foreign sentence with one marker word gets no Portuguese line (R-D5)`() {
        AnswerLanguage.appLanguage = "pt"
        for (q in listOf("Wie viele Einwohner hat Berlin", "Welche Hauptstadt hat Deutschland", "combien de temps dure un vol pour Paris", "Cómo funciona un motor de combustión"))
            assertEquals(q, Lang.OTHER, AnswerLanguage.of(q))
        assertEquals(Lang.PT, AnswerLanguage.of("gracias"))
    }
}
