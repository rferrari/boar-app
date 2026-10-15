package team.sopa.boar.engine.answer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.rag.ConversationHistory
import team.sopa.boar.engine.rag.ConversationTurn

/** "conclusion" after a research answer was split as written, searched for "conclusion" and refused (X6, Oct 13). */
class DeepResearchFollowUpTest {
    private val history = ConversationHistory(turns = listOf(
        ConversationTurn("user", "How does the greenhouse effect warm the planet?"),
        ConversationTurn("assistant", "Gases like CO2 trap outgoing heat [1]."),
    ))

    private class Recorder(val replies: (String, String) -> String) : StageGenerator {
        val calls = ArrayList<Pair<String, String>>()
        override suspend fun generate(system: String, user: String, nPredict: Int, temperature: Float, timeoutMs: Long?, onToken: ((String) -> Unit)?): GenResult {
            calls += system to user
            return GenResult(replies(system, user))
        }
    }

    @Test fun `a follow-up is rewritten from the last exchange, then split and synthesized with it`() = runBlocking {
        val gen = Recorder { system, user -> when {
            system.startsWith("You rewrite") -> "What is the conclusion about how the greenhouse effect warms the planet?"
            system.startsWith("You plan") -> "I'm sorry, but I can't help with that.\nWhat gases drive the greenhouse effect?\nHow much has the planet warmed?"
            else -> "ok"
        } }
        val searched = ArrayList<String>()
        val r = DeepResearch(gen, { q, _ -> searched += q; emptyList() }, { null }).run("conclusion", null, history, 256)
        assertTrue(gen.calls[0].second.contains("Previous question: How does the greenhouse effect"))
        assertTrue(gen.calls[1].second.contains("Question: What is the conclusion about how the greenhouse effect"))
        assertEquals(listOf("What gases drive the greenhouse effect?", "How much has the planet warmed?"), r.subQuestions)
        assertEquals(r.subQuestions, searched)
        val synthesis = gen.calls.last().second
        assertTrue(synthesis.contains("Previous answer: Gases like CO2 trap outgoing heat [1]."))
        assertTrue(synthesis.contains("Follow-up as asked: conclusion"))
    }

    @Test fun `a question that names its own subject is not rewritten`() = runBlocking {
        val gen = Recorder { _, _ -> "ok" }
        DeepResearch(gen, { _, _ -> emptyList() }, { null }).run("How do Australian bushfires start?", null, history, 256)
        assertTrue(gen.calls.none { it.first.startsWith("You rewrite") })
        assertNull(DeepResearch.lastExchange("How do Australian bushfires start?", history))
        assertNull(DeepResearch.lastExchange("conclusion", null))
    }

    @Test fun `a refused rewrite keeps the follow-up as asked`() = runBlocking {
        val gen = Recorder { system, _ -> if (system.startsWith("You rewrite")) "Sorry, I cannot do that." else "ok" }
        DeepResearch(gen, { _, _ -> emptyList() }, { null }).run("continue", null, history, 256)
        assertTrue(gen.calls[1].second.contains("Question: continue"))
    }

    @Test fun `Stop during the rewrite ends the answer before the split`() = runBlocking {
        var stop = false
        val gen = Recorder { system, _ -> if (system.startsWith("You rewrite")) { stop = true; "What is the conclusion about the greenhouse effect?" } else "ok" }
        val r = DeepResearch(gen, { _, _ -> emptyList() }, { null }).run("conclusion", null, history, 256, shouldStop = { stop })
        assertEquals(1, gen.calls.size)
        assertTrue(r.subQuestions.isEmpty())
    }

    @Test fun `the write-up gets its own time limit`() = runBlocking {
        val limits = ArrayList<Long?>()
        val gen = object : StageGenerator {
            override suspend fun generate(system: String, user: String, nPredict: Int, temperature: Float, timeoutMs: Long?, onToken: ((String) -> Unit)?): GenResult {
                limits += timeoutMs; return GenResult(if (system.startsWith("You plan")) "Sub one about gases?\nSub two about heat?" else "ok")
            }
        }
        DeepResearch(gen, { _, _ -> emptyList() }, { 1000L }, { null }).run("How does the greenhouse effect warm the planet?", null, null, 256)
        assertEquals(listOf(1000L, 1000L, 1000L, null), limits)
    }

    @Test fun `a stopped question with no answer is skipped for the one before it`() {
        val h = ConversationHistory(turns = history.turns + ConversationTurn("user", "conclusion"))
        assertEquals("How does the greenhouse effect warm the planet?", DeepResearch.lastExchange("continue", h)?.question)
    }

    @Test fun `a second continue builds on the first one's answer`() {
        val h = ConversationHistory(turns = history.turns + ConversationTurn("user", "continue") + ConversationTurn("assistant", "More on trapped heat [2]."))
        assertEquals("continue", DeepResearch.lastExchange("continue", h)?.question)
        assertEquals("More on trapped heat [2].", DeepResearch.lastExchange("continue", h)?.answer)
    }

    @Test fun `refusal lines are not sub-questions`() {
        assertEquals(listOf("q"), DeepResearch.parseSubQuestions("Desculpe, não posso ajudar com isso.\nI cannot answer that question", "q"))
    }
}
