package team.sopa.boar.engine.answer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

/**
 * A model that reasons first (LFM2.5-8B-A1B, X6, Nov 10): it spends a small budget inside <think> and writes nothing
 * after it. Every internal step must leave the reasoning out, try once more with room, and never show an empty answer
 * or the sub-questions as the answer.
 */
class ThinkingModelTest {
    private val canberra = Chunk("c1", "canberra", "Canberra", "Canberra was chosen in 1908 as a compromise between Sydney and Melbourne.")
    private val act = Chunk("a1", "act", "Australian Capital Territory", "The territory was created in 1911 for the new capital.")

    /** Answers like a thinking model: a budget under [thinkTokens] gets only an unfinished <think>, a bigger one the reply. */
    private class Thinker(val thinkTokens: Int, val reply: (system: String, user: String) -> String) : StageGenerator {
        val calls = ArrayList<Triple<String, String, Int>>()
        override suspend fun generate(system: String, user: String, nPredict: Int, temperature: Float, timeoutMs: Long?, onToken: ((String) -> Unit)?): GenResult {
            calls += Triple(system, user, nPredict)
            val text = if (nPredict < thinkTokens) "<think>We need to respond to the question: let me think about it" else "<think>short</think>" + reply(system, user)
            onToken?.invoke(text)
            return GenResult(text)
        }
    }

    private fun research(gen: StageGenerator) = DeepResearch(gen, { _, _ -> listOf(canberra, act) })

    @Test fun `internal steps hand on the answer, never the reasoning`() = runBlocking {
        val gen = Thinker(0) { system, _ -> when {
            system.startsWith("You plan") -> "Why a compromise between Sydney and Melbourne?\nWhat security concerns mattered?"
            system.contains("synthesizing") -> "Canberra was a compromise between Sydney and Melbourne [1], set in its own territory [2]."
            else -> "It was a compromise [1]."
        } }
        val r = research(gen).run("Canberra: why Australia built a new capital", null, null, 1024)
        assertEquals(listOf("Why a compromise between Sydney and Melbourne?", "What security concerns mattered?"), r.subQuestions)
        // What the write-up reads: the perspectives' answers, no <think>.
        val synthesis = gen.calls.last { it.first.contains("synthesizing") }
        assertFalse(synthesis.second.contains("<think>"))
        assertTrue(synthesis.second.contains("It was a compromise [1]."))
    }

    @Test fun `a step that only reasoned is asked again with room, once`() = runBlocking {
        val gen = Thinker(400) { system, _ -> when {
            system.startsWith("You plan") -> "Why a compromise between Sydney and Melbourne?\nWhat security concerns mattered?"
            system.contains("synthesizing") -> "Canberra was a compromise between Sydney and Melbourne [1]."
            else -> "It was a compromise [1]."
        } }
        val r = research(gen).run("Canberra: why Australia built a new capital", null, null, 1024)
        val decompose = gen.calls.filter { it.first.startsWith("You plan") }
        // The retry gets 512 more, not the answer's 1024 (9 min 52 s on the X6 with 2048).
        assertEquals(listOf(150, 150 + DeepResearch.RETRY_ROOM), decompose.map { it.third })
        assertEquals(2, r.subQuestions.size)
        assertTrue(r.answer.contains("compromise"))
    }

    @Test fun `no perspective at all answers straight from the sources`() = runBlocking {
        // Every sub-question step reasons past any budget; the write-up gets the full budget and answers.
        val gen = object : StageGenerator {
            val systems = ArrayList<String>()
            override suspend fun generate(system: String, user: String, nPredict: Int, temperature: Float, timeoutMs: Long?, onToken: ((String) -> Unit)?): GenResult {
                systems += system
                val text = when {
                    system.startsWith("You plan") -> "Why a compromise?\nWhat security concerns?"
                    system.contains("short paragraph") -> "<think>endless"
                    else -> "Canberra was a compromise between Sydney and Melbourne [1]."
                }
                onToken?.invoke(text); return GenResult(text)
            }
        }
        val r = research(gen).run("Canberra: why Australia built a new capital", null, null, 1024)
        assertEquals("Canberra was a compromise between Sydney and Melbourne [1].", r.answer)
        assertFalse(gen.systems.any { it.contains("synthesizing") })
        assertTrue(r.citations.any { it.title == "Canberra" })
    }

    @Test fun `a write-up that only reads the sub-questions back is replaced by an answer from the sources`() = runBlocking {
        val shown = StringBuilder()
        var restarts = 0
        val gen = object : StageGenerator {
            override suspend fun generate(system: String, user: String, nPredict: Int, temperature: Float, timeoutMs: Long?, onToken: ((String) -> Unit)?): GenResult {
                val text = when {
                    system.startsWith("You plan") -> "Why was a compromise between Sydney and Melbourne a factor?\nWhat security concerns motivated the decision?"
                    system.contains("synthesizing") -> "<think>hmm</think>Why was a compromise between Sydney and Melbourne a factor?\nWhat security concerns motivated the decision?"
                    system.contains("short paragraph") -> "A compromise [1]."
                    else -> "Canberra was a compromise between Sydney and Melbourne [1], away from the coast [2]."
                }
                onToken?.invoke(text); return GenResult(text)
            }
        }
        val r = research(gen).run("Canberra: why Australia built a new capital", null, null, 1024,
            onToken = { shown.append(it) }, onRestart = { restarts++; shown.setLength(0) })
        assertEquals(1, restarts)
        assertEquals("Canberra was a compromise between Sydney and Melbourne [1], away from the coast [2].", r.answer)
        assertEquals(r.answer, shown.toString())
    }

    @Test fun `the repeat guard keeps short real answers`() {
        val subs = listOf("Why a compromise?", "What security concerns?")
        assertTrue(DeepResearch.onlyRepeats("", subs))
        assertTrue(DeepResearch.onlyRepeats("Why a compromise?\nWhat security concerns?", subs))
        assertTrue(DeepResearch.onlyRepeats("1. Why a compromise? 2. What security concerns?", subs))
        assertFalse(DeepResearch.onlyRepeats("ok", subs))
        assertFalse(DeepResearch.onlyRepeats("Why a compromise? Because Sydney and Melbourne both wanted it [1].", subs))
    }

    @Test fun `an answer that restates an unsplit question is an answer`() {
        // The split kept the question as its only sub-question; a short answer that restates it is real (validator T-1).
        val q = "What is the capital of Australia?"
        assertFalse(DeepResearch.onlyRepeats("The capital of Australia is Canberra.", listOf(q), q))
        assertFalse(DeepResearch.onlyRepeats("What is the capital of Australia? Canberra.", listOf(q), q))
        assertTrue(DeepResearch.onlyRepeats("", listOf(q), q))
        assertTrue(DeepResearch.onlyRepeats("Why a compromise?\nWhat security concerns?", listOf("Why a compromise?", "What security concerns?"), "Canberra: why a new capital"))
    }

    @Test fun `the title of a thinking model is its answer, or the question's start`() = runBlocking {
        assertEquals("Canberra history", Summarize.title(Thinker(0) { _, _ -> "Canberra history" }, "Canberra: why Australia built a new capital"))
        assertEquals("Canberra: why Australia built a new", Summarize.title(Thinker(100) { _, _ -> "x" }, "Canberra: why Australia built a new capital"))
    }

    // ── Staged Answers ──

    /** A TextModel that reasons through any budget under [thinkTokens]. */
    private class ThinkingModel(val thinkTokens: Int, val reply: String) : TextModel {
        val budgets = ArrayList<Int>()
        override val hasChatTemplate = false
        override suspend fun formatChat(messages: List<ChatMessage>, enableThinking: Boolean) = FormattedPrompt("", false, "", emptyList(), emptyList())
        override fun generate(prompt: String, sampling: Sampling): Flow<GenerationEvent> = flow {
            budgets += sampling.nPredict
            if (sampling.nPredict < thinkTokens) { emit(GenerationEvent.Piece("<think>We need to")); emit(GenerationEvent.Piece(" respond")) }
            else { emit(GenerationEvent.Piece("<think>ok</think>")); for (w in reply.split(" ")) emit(GenerationEvent.Piece("$w ")) }
        }
        override fun cancel() {}
        override var onGenerated: ((GenerationMetrics) -> Unit)? = null
        override fun close() {}
    }

    private fun staged() = StagedAnswer({ _, _ -> listOf(canberra) }, { "explanation" }, { false })
    private val req = StagedRequest("Why was Canberra built?", "You are BOAR.", "Keep it short.", "Be thorough.", 1024)

    @Test fun `Staged writes the whole answer when the short one was only reasoning`() = runBlocking {
        val model = ThinkingModel(500, "Canberra was a compromise between Sydney and Melbourne [1].")
        val events = staged().answer(req, { model }, { false }).toList()
        assertEquals(listOf(StagedAnswer.INTRO_TOKENS, 1024), model.budgets)
        assertEquals(1, events.count { it is StagedEvent.Restart })
        val done = events.filterIsInstance<StagedEvent.Done>().single()
        assertEquals("Canberra was a compromise between Sydney and Melbourne [1].", done.text)
        // The redo is the whole answer: no details step after it.
        assertFalse(events.filterIsInstance<StagedEvent.Step>().any { it.step == StagedStep.DETAILS })
    }

    @Test fun `Staged with a model that doesn't reason makes the same calls as before`() = runBlocking {
        val model = ThinkingModel(0, "Canberra was a compromise [1].")
        val events = staged().answer(req, { model }, { false }).toList()
        assertEquals(listOf(StagedAnswer.INTRO_TOKENS, 1024), model.budgets)
        assertEquals(0, events.count { it is StagedEvent.Restart })
        assertTrue(events.filterIsInstance<StagedEvent.Step>().any { it.step == StagedStep.DETAILS })
    }

    @Test fun `streamed pieces read as running text, one break between intro and details`() {
        val intro = StringBuilder(); val details = StringBuilder()
        var shown = ""
        for (p in listOf("Ke", "ih", "ans", "hin ", "is ", "short.")) { intro.append(p); shown = StagedAnswer.join(intro.toString(), details.toString()) }
        assertEquals("Keihanshin is short.", shown)
        for (p in listOf("More", " details", " here.")) { details.append(p); shown = StagedAnswer.join(intro.toString(), details.toString()) }
        assertEquals("Keihanshin is short.\n\nMore details here.", shown)
        assertEquals(1, Regex("\n\n").findAll(shown).count())
    }
}
