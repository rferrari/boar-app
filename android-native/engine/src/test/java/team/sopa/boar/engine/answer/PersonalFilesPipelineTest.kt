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

/** The Personal Files style through the answer pipeline: nothing found writes the fixed line without the model. */
class PersonalFilesPipelineTest {
    private class Model : TextModel {
        val prompts = ArrayList<String>()
        override val hasChatTemplate = false
        override suspend fun formatChat(messages: List<ChatMessage>, enableThinking: Boolean) = FormattedPrompt("", false, "", emptyList(), emptyList())
        override fun generate(prompt: String, sampling: Sampling): Flow<GenerationEvent> {
            prompts += prompt
            return flowOf(GenerationEvent.Piece("Rent is 1,250 EUR [1]."))
        }
        override fun cancel() {}
        override var onGenerated: ((GenerationMetrics) -> Unit)? = null
        override fun close() {}
    }

    private val part = Chunk("p", "d", "budget", "Rent: 1,250 EUR. ".repeat(20).trim())
    private fun pipeline(model: Model, found: List<Chunk>) = AnswerPipeline(model, { _, _ -> found }, { "lookup" }, { false })
    private fun request() = PersonalFiles.request(AnswerRequest("What is my rent?", "Answer only from the files.", maxTokens = 768), 4096)

    @Test fun `nothing found writes the fixed line and never calls the model`() = runBlocking {
        val model = Model()
        val events = pipeline(model, emptyList()).answer(request()).toList()
        val done = events.filterIsInstance<AnswerEvent.Done>().single()
        assertEquals(PersonalFiles.NOT_FOUND, done.text)
        assertTrue(done.notFound)
        assertTrue(model.prompts.isEmpty())
    }

    @Test fun `found parts go in whole with the files-only instruction`() = runBlocking {
        val model = Model()
        val done = pipeline(model, listOf(part)).answer(request()).toList().filterIsInstance<AnswerEvent.Done>().single()
        assertFalse(done.notFound)
        assertEquals(listOf(part), done.sources)
        assertTrue(model.prompts.single().contains(part.body))
        assertTrue(model.prompts.single().contains("I couldn't find this in your Personal Files."))
    }

    // F3, Oct 22: "How is my blood?" opened with the not-found line, then answered from the blood test.
    @Test fun `the not-found line goes when an answer from the files follows it`() {
        val a = PersonalFiles.NOT_FOUND + " However, according to your blood test from March, your cholesterol was 231 [1]."
        org.junit.Assert.assertEquals("According to your blood test from March, your cholesterol was 231 [1].", PersonalFiles.dropHedge(a))
    }

    @Test fun `the not-found line alone, or with a short sign-off, stays`() {
        org.junit.Assert.assertEquals(PersonalFiles.NOT_FOUND, PersonalFiles.dropHedge(PersonalFiles.NOT_FOUND))
        val short = PersonalFiles.NOT_FOUND + " Try another collection."
        org.junit.Assert.assertEquals(short, PersonalFiles.dropHedge(short))
    }

    @Test fun `without a citation the rest may be from memory, so the line stays`() {
        val a = PersonalFiles.NOT_FOUND + " However, cholesterol is generally considered healthy below 200 mg/dL for most adults."
        org.junit.Assert.assertEquals(a, PersonalFiles.dropHedge(a))
    }

    @Test fun `an answer that doesn't open with the line is untouched`() {
        val a = "Your blood type is O negative [1]. " + PersonalFiles.NOT_FOUND
        org.junit.Assert.assertEquals(a, PersonalFiles.dropHedge(a))
    }
}
