package team.sopa.boar.engine.rag

import org.junit.Assert.assertTrue
import org.junit.Test

/** Validator P-C6 (t9 0435): a Portuguese question with "do" kept every Portuguese function word as a keyword. */
class PtStopwordsDoTest {
    private fun words(q: String) = RagPure.buildLexicalQuery(q)!!.terms.flatMap { it.forms }.toSet()

    @Test fun `Portuguese questions with do drop their function words`() {
        val moncoes = words("Como as monções se formam e por que importam para a agricultura do Brasil")
        for (w in listOf("como", "se", "por", "que", "para")) assertTrue("$w in $moncoes", w !in moncoes)
        assertTrue(moncoes.any { it.startsWith("agricultur") } && moncoes.any { it.startsWith("brasil") })
        val sol = words("Qual o tamanho do Sol para a Terra")
        for (w in listOf("qual", "para")) assertTrue("$w in $sol", w !in sol)
    }

    @Test fun `English questions keep their keywords (R-D4)`() {
        assertTrue(words("What is DOS?").any { it == "dos" })
        assertTrue(words("Who is Uma Thurman?").any { it == "uma" })
    }
}
