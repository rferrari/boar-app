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
import team.sopa.boar.engine.db.JdbcSqlite
import team.sopa.boar.engine.rag.DocumentImport
import team.sopa.boar.engine.rag.KnowledgeBase
import team.sopa.boar.engine.rag.OwnFiles
import team.sopa.boar.engine.rag.Retriever
import team.sopa.boar.engine.rag.RetrievalParams

/**
 * Personal Files with the files' metadata (Oct 24), end to end as the chat runs it: files imported by the real
 * importer into a real SQLite, searched by the real retriever, answered by the real pipeline. Only the model is a
 * stand-in, so the test reads the exact prompt it would get. The questions are the ones from the emulator (Oct 23).
 */
class PersonalFilesMetadataTest {
    private class Model : TextModel {
        val prompts = ArrayList<String>()
        override val hasChatTemplate = false
        override suspend fun formatChat(messages: List<ChatMessage>, enableThinking: Boolean) = FormattedPrompt("", false, "", emptyList(), emptyList())
        override fun generate(prompt: String, sampling: Sampling): Flow<GenerationEvent> {
            prompts += prompt
            return flowOf(GenerationEvent.Piece("An answer [1]."))
        }
        override fun cancel() {}
        override var onGenerated: ((GenerationMetrics) -> Unit)? = null
        override fun close() {}
    }

    private val festival = "Boar festival notes\n\nThe boar festival is on the third Saturday of May, in the village square. " +
        "Tickets cost 12 euros and the gate code for volunteers is 4471."
    private val trip = "# Patagonia trip\nPlan for March 2027.\n\n## Budget\nAbout 3,000 USD for two weeks.\n\n## Packing\n" +
        "Tent, stove, rain jacket.\n\n## Route\n" + "Day by day from El Chaltén to Torres del Paine. ".repeat(60)

    /** No question here is close in meaning to any part: like the emulator's miss, only metadata can find the files. */
    private fun embed(text: String) = if (text.startsWith("Q:")) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f)

    private fun setUp(): Pair<KnowledgeBase, Retriever> = runBlocking {
        val kb = KnowledgeBase(JdbcSqlite())
        DocumentImport.import(kb, { embed(it) }, listOf(
            DocumentImport.File("festival-notes.txt", festival.length.toLong(), festival),
            DocumentImport.File("patagonia-trip-plan.md", trip.length.toLong(), trip),
        ), "Setup files", collectionId = "c")
        kb to Retriever(kb, { emptyList() }, { embed("Q:$it") }, { emptyMap() })
    }

    /** The chat's Personal Files request (ChatViewModel): files-only, the not-found line, and the files' list. */
    private fun ask(r: Retriever, question: String): Pair<AnswerEvent.Done, Model> = runBlocking {
        val model = Model()
        val pipeline = AnswerPipeline(model, { q, k -> r.retrieve(q, k, params = PersonalFiles.retrieval(RetrievalParams.DEFAULT)) }, { "lookup" }, { false })
        val request = PersonalFiles.request(AnswerRequest(question, "Answer only from the files.", maxTokens = 512), 4096, filesNote = OwnFiles.note(r.ownFiles()))
        pipeline.answer(request).toList().filterIsInstance<AnswerEvent.Done>().single() to model
    }

    @Test fun `the import makes a card for the long file only`() {
        val (kb, _) = setUp()
        val ids = kb.collectionDocs("c").map { it.title }
        assertTrue(ids.toString(), ids.none { it.endsWith("(about this file)") }) // never exported
        assertEquals(listOf("festival-notes.txt", "patagonia-trip-plan.md"), kb.ownFiles().map { it.name }.distinct())
        assertEquals(1, kb.ownFiles().count { it.name == "festival-notes.txt" && it.parts == 1 })
    }

    @Test fun `main points of my files - every file's opening, with the list, and the model answers`() {
        val (_, r) = setUp()
        val (done, model) = ask(r, "What are the main points of my files?")
        assertFalse(done.notFound)
        assertEquals(listOf("festival-notes", "patagonia-trip-plan"), done.sources.map { it.docId.substringAfter("-doc").substringAfter("-") })
        assertTrue(done.sources.none(OwnFiles::isCard))
        val prompt = model.prompts.single()
        assertTrue(prompt, prompt.contains("The user's Personal Files (2): festival-notes.txt; patagonia-trip-plan.md."))
        assertTrue(prompt, prompt.contains("third Saturday of May") && prompt.contains("Patagonia trip"))
    }

    @Test fun `resumo dos meus arquivos - the same in Portuguese`() {
        val (_, r) = setUp()
        val (done, _) = ask(r, "Resuma meus arquivos")
        assertFalse(done.notFound)
        assertEquals(2, done.sources.map { it.docId }.distinct().size)
    }

    @Test fun `a named file - only its parts, no floor`() {
        val (_, r) = setUp()
        val (done, model) = ask(r, "What did I plan for the Patagonia trip?")
        assertFalse(done.notFound)
        assertTrue(done.sources.map { it.source }.toString(), done.sources.isNotEmpty() && done.sources.all { it.source == "patagonia-trip-plan.md" })
        assertTrue(model.prompts.single().contains("## Budget") || model.prompts.single().contains("Budget"))
    }

    @Test fun `do I have a medical file - the model sees the list and every file to say no`() {
        val (_, r) = setUp()
        val (done, model) = ask(r, "Do I have a medical file?")
        assertFalse(done.notFound)
        assertTrue(model.prompts.single().contains("The user's Personal Files (2):"))
    }

    @Test fun `a question nothing in the files answers - the fixed line, and the model never runs`() {
        val (_, r) = setUp()
        val (done, model) = ask(r, "What is my blood type?")
        assertTrue(done.notFound)
        assertEquals(PersonalFiles.NOT_FOUND, done.text)
        assertTrue(model.prompts.isEmpty())
    }

    @Test fun `switched-off files - nothing, even for a question about all of them`() {
        val (kb, r) = setUp()
        kb.setCollectionActive("c", false)
        val (done, model) = ask(r, "What are the main points of my files?")
        assertTrue(done.notFound)
        assertTrue(model.prompts.isEmpty())
    }
}
