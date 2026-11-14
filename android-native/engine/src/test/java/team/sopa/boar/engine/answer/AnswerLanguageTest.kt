package team.sopa.boar.engine.answer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.answer.AnswerLanguage.Lang
import team.sopa.boar.engine.rag.Chunk
import team.sopa.boar.engine.rag.ConversationHistory
import team.sopa.boar.engine.rag.ConversationTurn
import team.sopa.boar.engine.rag.RagPure

/** t9 0420 (adam: "send language fix. all prompts… this is important fix"): r4to's Portuguese line, back. */
class AnswerLanguageTest {
    private val src = listOf(Chunk("c1", "d1", "Seawater", "Seawater is salty because rivers carry dissolved salts to the sea."))
    @After fun reset() { AnswerLanguage.appLanguage = null }

    @Test fun `a Portuguese question gets r4to's line next to it, with and without sources, in both prompt shapes`() {
        val q = "Por que a água do mar é salgada?"
        val chat = RagPure.assembleChatMessages(q, src).last().content
        assertTrue(chat, chat.startsWith(q) && chat.endsWith("\n\n" + AnswerLanguage.PT_WITH_SOURCES))
        assertTrue(RagPure.assembleChatMessages(q, emptyList()).last().content.endsWith("\n\n" + AnswerLanguage.PT_NO_SOURCES))
        val plain = RagPure.assemblePrompt(q, src)
        assertTrue(plain, plain.endsWith("Question: $q\n\n${AnswerLanguage.PT_WITH_SOURCES}\n\nAnswer:"))
        // The system head (kept prefilled in the cache) is the same as for English.
        assertEquals(RagPure.assembleChatMessages("Why is seawater salty?", src).first().content, RagPure.assembleChatMessages(q, src).first().content)
    }

    @Test fun `an English question's prompt is exactly as before`() {
        val q = "Why is seawater salty?"
        assertEquals(q, RagPure.assembleChatMessages(q, src).last().content)
        assertFalse(RagPure.assemblePrompt(q, src).contains("Responda"))
        // Even in a Portuguese app: a clearly English question is answered in English.
        AnswerLanguage.appLanguage = "pt"
        assertEquals(q, RagPure.assembleChatMessages(q, src).last().content)
    }

    @Test fun `Staged's details step reads the language from the real question, not its English ask`() {
        val h = ConversationHistory(null, listOf(ConversationTurn("user", "Por que o mar é salgado?"), ConversationTurn("assistant", "Porque os rios…")))
        val details = RagPure.assembleChatMessages(StagedAnswer.DETAILS_ASK, src, history = h, languageOf = "Por que o mar é salgado?").last().content
        assertTrue(details.endsWith(AnswerLanguage.PT_WITH_SOURCES))
    }

    @Test fun `Personal Files in Portuguese says its own line, and dropHedge knows it`() {
        val p = RagPure.assembleChatMessages("Qual é o meu tipo sanguíneo?", src, filesOnly = true).last().content
        assertTrue(p.endsWith(AnswerLanguage.PT_FILES_ONLY) && p.contains(PersonalFiles.NOT_FOUND_PT))
        val a = PersonalFiles.NOT_FOUND_PT + " Porém, seu exame mostra tipo O positivo [1], segundo o laudo de março de 2026 que você salvou."
        assertEquals("Seu exame mostra tipo O positivo [1], segundo o laudo de março de 2026 que você salvou.", PersonalFiles.dropHedge(a))
    }

    @Test fun `which language - the question, then the conversation's last clear one, then the app`() {
        fun check(q: String, earlier: List<String>, app: String?, want: Lang) = assertEquals("$q / $earlier / $app", want, AnswerLanguage.of(q, earlier, app))
        check("Quantos ossos tem o corpo humano?", listOf(), null, Lang.PT)
        check("How many bones are in the human body?", listOf(), "pt", Lang.EN)
        check("e por quê?", listOf("Por que o céu é azul?"), null, Lang.PT)
        check("and why?", listOf("Why is the sky blue?"), "pt", Lang.EN)
        check("Everest?", listOf(), "pt", Lang.PT)
        check("Everest?", listOf(), "en", Lang.EN)
        check("26 km", listOf("Quanto é 26 milhas em km?"), null, Lang.PT)
    }

    @Test fun `r4to's lines, word for word`() {
        assertEquals("Responda em português do Brasil, mesmo que as fontes estejam em inglês, e cite cada afirmação com o número da fonte, como [1].", AnswerLanguage.PT_WITH_SOURCES)
        assertEquals("Responda em português do Brasil.", AnswerLanguage.PT_NO_SOURCES)
    }
}
