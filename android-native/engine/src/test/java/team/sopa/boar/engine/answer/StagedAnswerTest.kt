package team.sopa.boar.engine.answer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.ChatMessage
import team.sopa.boar.engine.FormattedPrompt
import team.sopa.boar.engine.GenerationEvent
import team.sopa.boar.engine.GenerationMetrics
import team.sopa.boar.engine.Sampling
import team.sopa.boar.engine.TextModel
import team.sopa.boar.engine.rag.Chunk

/** Boar Staged answers: the steps' order, Stop between them, the research step and the number check. */
class StagedAnswerTest {
    private class Model(private vararg val replies: String) : TextModel {
        val prompts = ArrayList<String>()
        override val hasChatTemplate = false
        override suspend fun formatChat(messages: List<ChatMessage>, enableThinking: Boolean) = FormattedPrompt("", false, "", emptyList(), emptyList())
        override fun generate(prompt: String, sampling: Sampling): Flow<GenerationEvent> {
            prompts += prompt
            return flowOf(GenerationEvent.Piece(replies[prompts.size - 1]))
        }
        override fun cancel() {}
        override var onGenerated: ((GenerationMetrics) -> Unit)? = null
        override fun close() {}
    }

    private val volcano = Chunk("v1", "volcano", "Volcano", "A volcano forms where magma rises through the crust. Mount Etna is 3,357 m high.")
    private val plate = Chunk("p1", "plates", "Plate tectonics", "Most volcanoes form at plate boundaries where magma rises.")
    private val q = "How do volcanoes form?"
    private fun req(details: String? = "Be thorough.") = StagedRequest(q, "You are BOAR.", "Keep it short.", details, 512)
    private fun staged(found: (String) -> List<Chunk>) = StagedAnswer({ s, _ -> found(s) }, { "explanation" }, { false })

    @Test fun `the cards come before the model loads`() = runBlocking {
        val model = Model("Magma rises [1].", "Etna is 3,357 m [1].")
        var loadedAfterCards = false
        var cards = false
        val events = staged { listOf(volcano) }.answer(req(), { loadedAfterCards = cards; model }, { false }).onEachCards { cards = true }.toList()
        assertTrue(loadedAfterCards)
        assertEquals(listOf(StagedStep.SOURCES, StagedStep.INTRO, StagedStep.RESEARCH, StagedStep.DETAILS, StagedStep.CHECK),
            events.filterIsInstance<StagedEvent.Step>().map { it.step })
        val done = events.filterIsInstance<StagedEvent.Done>().single()
        assertEquals("Magma rises [1].\n\nEtna is 3,357 m [1].", done.text)
        assertEquals(emptyList<String>(), done.unsupported)
        // The details go on from the intro, the model's own turn.
        assertTrue(model.prompts[1].contains("Magma rises [1]."))
        assertTrue(model.prompts[1].contains(StagedAnswer.DETAILS_ASK))
    }

    @Test fun `the research step adds passages from articles not yet read, numbered after the first`() = runBlocking {
        val model = Model("Magma rises at plate boundaries.", "More [3].")
        val done = staged { s -> if (s == q) listOf(volcano) else listOf(volcano, plate) }.answer(req(), { model }, { false })
            .toList().filterIsInstance<StagedEvent.Done>().single()
        assertEquals(listOf("volcano", "plates"), done.sources.map { it.docId })
        assertTrue(model.prompts[1].contains("[2] Plate tectonics"))
    }

    @Test fun `stop after the intro keeps the intro and skips the details`() = runBlocking {
        val model = Model("Magma rises [1].", "never")
        var stop = false
        val done = staged { listOf(volcano) }.answer(req(), { model }, { stop }).onEachIntro { stop = true }
            .toList().filterIsInstance<StagedEvent.Done>().single()
        assertEquals("Magma rises [1].", done.text)
        assertTrue(done.stopped)
        assertEquals(1, model.prompts.size)
    }

    @Test fun `a health question kept short is the intro alone`() = runBlocking {
        val model = Model("Cool the burn under running water [1].")
        val done = staged { listOf(volcano) }.answer(req(details = null), { model }, { false }).toList().filterIsInstance<StagedEvent.Done>().single()
        assertEquals(1, model.prompts.size)
        assertNull(done.times.detailsFirstMs)
    }

    @Test fun `no model leaves the cards`() = runBlocking {
        val done = staged { listOf(volcano) }.answer(req(), { null }, { false }).toList().filterIsInstance<StagedEvent.Done>().single()
        assertTrue(done.noModel)
        assertEquals(1, done.sources.size)
        assertTrue(done.keyFacts.isNotEmpty())
    }

    @Test fun `key facts are the best answering sentence of each source`() {
        val facts = StagedAnswer.keyFacts(q, listOf(volcano, plate))
        assertEquals(2, facts.size)
        assertTrue(facts.all { "magma" in it.sentence.lowercase() })
    }

    @Test fun `numbers not in any source are flagged, citations and small counts are not`() {
        val evidence = listOf("Mount Etna is 3,357 m high.")
        assertEquals(listOf("4,000"), StagedAnswer.unsupportedNumbers("Etna is 3357 m [12], not 4,000 m. There are 3 kinds.\n1. One", evidence))
        assertEquals(emptyList<String>(), StagedAnswer.unsupportedNumbers("Etna is 3,357 m high [1].", evidence))
    }

    private fun Flow<StagedEvent>.onEachCards(f: () -> Unit) = kotlinx.coroutines.flow.flow { collect { if (it is StagedEvent.Cards) f(); emit(it) } }
    private fun Flow<StagedEvent>.onEachIntro(f: () -> Unit) = kotlinx.coroutines.flow.flow { collect { emit(it); if (it is StagedEvent.Piece && it.step == StagedStep.INTRO) f() } }

    @Test fun `a source outside the quotes gets its best sentence, or its first`() {
        val q = "How do volcanoes form?"
        assertEquals("A volcano forms where magma rises through the crust.", StagedAnswer.factOf(q, volcano))
        val off = Chunk("o1", "football", "Football", "The 1950 World Cup was held in Brazil. Uruguay won it.")
        assertEquals("The 1950 World Cup was held in Brazil.", StagedAnswer.factOf(q, off))
    }

    @Test fun `the chat keeps the quotes open, shows +N sources and hides its own list for a lab that owns them`() {
        val card = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/StagedCard.kt").readText()
        assertTrue(card.contains("val factsOpen = open ?: true"))
        assertTrue(card.contains("native.staged.moreSources"))
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
    }

    @Test fun `a tall answer keeps its taps, the bubble's shape is drawn and never clipped`() {
        // X6, Nov 10: with Staged's quotes open, a clip on the answer stopped every tap on its lower half (🧩, Copy, rating).
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        val bubble = chat.substringAfter("private fun Bubble(").substringBefore("Column(mod.padding(")
        assertTrue(bubble.contains(".background(if (user) c.bgCardHover else c.bgSurface, shape)"))
        assertTrue(!bubble.contains(".clip(shape)"))
    }

    @Test fun `a figure with a scale word is backed by the source's full number at the digits stated (t9 0400 item 3)`() {
        val facts = listOf("Russia: population 146,119,928 (2025); area 17,075,400 km². Brazil: population 214,211,951 (2026).")
        // The F3, 15 Nov: "146 million" was flagged against 146,119,928.
        assertEquals(emptyList<String>(), StagedAnswer.unsupportedNumbers("Russia has about 146 million people [1].", facts))
        assertEquals(emptyList<String>(), StagedAnswer.unsupportedNumbers("Russia covers 17.1 million km² and Brazil has 214 million people.", facts))
        // Portuguese, with the source written either way.
        assertEquals(emptyList<String>(), StagedAnswer.unsupportedNumbers("A Rússia tem cerca de 146 milhões de habitantes e 17,1 milhões de km².", facts))
        assertEquals(emptyList<String>(), StagedAnswer.unsupportedNumbers("O Brasil tem 214 milhões de habitantes.", listOf("Brasil: 214.211.951 habitantes.")))
        // Wrong at the digits stated is still flagged; a bare number keeps the old rule.
        assertEquals(listOf("150"), StagedAnswer.unsupportedNumbers("Russia has about 150 million people.", facts))
        assertEquals(listOf("17.2"), StagedAnswer.unsupportedNumbers("Russia covers 17.2 million km².", facts))
        assertEquals(listOf("146"), StagedAnswer.unsupportedNumbers("Russia has 146 regions.", facts))
    }
}
