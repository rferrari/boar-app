package team.sopa.boar.engine.answer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.ChatMessage
import team.sopa.boar.engine.FormattedPrompt
import team.sopa.boar.engine.GenerationEvent
import team.sopa.boar.engine.GenerationMetrics
import team.sopa.boar.engine.Sampling
import team.sopa.boar.engine.TextModel
import team.sopa.boar.engine.rag.Chunk
import team.sopa.boar.engine.rag.ConversationHistory
import team.sopa.boar.engine.rag.ConversationTurn

/** BOAR-78 (F3, 25 Nov): the details step stays on the question's topic when the chat switches topic. */
class StagedTopicSwitchTest {
    private class Model(private val stop: String, private vararg val replies: String) : TextModel {
        val prompts = ArrayList<String>()
        override val hasChatTemplate = false
        override suspend fun formatChat(messages: List<ChatMessage>, enableThinking: Boolean) = FormattedPrompt("", false, "", emptyList(), emptyList())
        override fun generate(prompt: String, sampling: Sampling): Flow<GenerationEvent> {
            prompts += prompt
            return flowOf(GenerationEvent.Piece(replies[prompts.size - 1]),
                GenerationEvent.Done(GenerationMetrics(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, stop, null)))
        }
        override fun cancel() {}
        override var onGenerated: ((GenerationMetrics) -> Unit)? = null
        override fun close() {}
    }

    private val countries = ConversationHistory(null, listOf(
        ConversationTurn("user", "Compare the population and area of Brazil and Russia"),
        ConversationTurn("assistant", "Russia is larger in area; Brazil has more people [1]."),
    ))
    private val brazil = Chunk("b1", "brazil", "Brazil", "Brazil's capital is Brasília. Brazil has 203 million people.")

    private fun run(q: String, history: ConversationHistory?, model: Model, found: List<Chunk>, irrelevant: Boolean = false) = runBlocking {
        StagedAnswer({ _, _ -> found }, { if (irrelevant) "calculate" else "explanation" }, { irrelevant })
            .answer(StagedRequest(q, "You are BOAR.", "Keep it short.", "Be thorough.", 512, history), { model }, { false }).toList()
    }

    @Test fun `a new topic with no sources is the intro alone (the F3 case)`() {
        val model = Model("eos", "20% de 300 é 60.", "Brazil and Russia…")
        val events = run("quanto e 20% de 300", countries, model, emptyList(), irrelevant = true)
        assertEquals(1, model.prompts.size)
        assertFalse(events.filterIsInstance<StagedEvent.Step>().any { it.step == StagedStep.DETAILS })
        assertEquals("20% de 300 é 60.", events.filterIsInstance<StagedEvent.Done>().single().text)
    }

    @Test fun `a new topic cut at the intro's cap goes on, without the earlier turns`() {
        val model = Model("length", "def sort(xs):", "    return sorted(xs)")
        run("write a python function to sort a list", countries, model, emptyList(), irrelevant = true)
        assertEquals(2, model.prompts.size)
        assertFalse(model.prompts[1].contains("Brazil"))
        assertTrue(model.prompts[1].contains("def sort(xs):"))
    }

    @Test fun `a new topic with sources gets details from its own sources, not the earlier turns`() {
        val model = Model("eos", "Volcanoes form where magma rises [1].", "More [1].")
        val volcano = Chunk("v1", "volcano", "Volcano", "A volcano forms where magma rises through the crust.")
        run("How do volcanoes form in Iceland?", countries, model, listOf(volcano))
        assertEquals(2, model.prompts.size)
        assertFalse(model.prompts[1].contains("Russia"))
    }

    private fun runFound(q: String, model: Model, found: (String) -> List<Chunk>) = runBlocking {
        StagedAnswer({ s, _ -> found(s) }, { "explanation" }, { false })
            .answer(StagedRequest(q, "You are BOAR.", "Keep it short.", "Be thorough.", 512, countries), { model }, { false }).toList()
    }

    @Test fun `a follow-up keeps the earlier turns in its details`() {
        val model = Model("eos", "A capital do Brasil é Brasília [1].", "Mais [1].")
        // Its own words find nothing; the earlier question's do.
        runFound("e a capital?", model) { s -> if ("Brazil" in s) listOf(brazil) else emptyList() }
        assertEquals(2, model.prompts.size)
        assertTrue(model.prompts[1].contains("Russia is larger in area"))
    }

    @Test fun `a short new topic that finds its own sources leaves the earlier turns out (CodeRabbit)`() {
        val model = Model("eos", "Plants make sugar from light [1].", "More [1].")
        val leaf = Chunk("p1", "photosynthesis", "Photosynthesis", "Photosynthesis turns light, water and CO2 into sugar.")
        runFound("what is photosynthesis?", model) { s -> if ("photosynthesis" in s && "Brazil" !in s) listOf(leaf) else emptyList() }
        assertEquals(2, model.prompts.size)
        assertFalse(model.prompts[1].contains("Russia"))
    }
}
