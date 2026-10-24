package team.sopa.boar.engine.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.db.JdbcSqlite

/** Personal Files knows the user's files as files (Oct 23): whole-file questions, a named file, the list, the cards. */
class OwnFilesTest {
    // --- Questions about the files as a whole -------------------------------------------------------------------

    @Test fun `questions about the files as a whole, in English and Portuguese`() {
        for (q in listOf(
            "What are the main points of my files?", "Summarize my notes", "Give me an overview of my documents",
            "Which files do I have?", "Do I have a medical file?", "How many documents did I import?",
            "Resuma meus arquivos", "Quais são os pontos principais dos meus documentos?", "Tenho algum arquivo sobre o carro?",
            "O que tem nas minhas anotações?", "Resuma a minha documentação",
        )) assertTrue(q, OwnFiles.aboutTheFiles(q))
    }

    @Test fun `a question about something in the files, or not about files at all, is not one`() {
        for (q in listOf(
            "When is the boar festival?", "What is the gate code for volunteers?", "Summarize the French revolution",
            "Which river is the longest?", "Quando é o festival?",
        )) assertFalse(q, OwnFiles.aboutTheFiles(q))
    }

    // --- A file the question names --------------------------------------------------------------------------------

    private val files = listOf(
        OwnFiles.File("c-doc0-blood-test-2026", "blood-test-2026.txt", 1),
        OwnFiles.File("c-doc1-car-insurance", "car-insurance.pdf", 3),
        OwnFiles.File("c-doc2-festival-notes", "festival-notes.txt", 1),
        OwnFiles.File("c-doc3-patagonia-trip-plan", "patagonia-trip-plan.md", 4),
    )

    @Test fun `a file named by its own words`() {
        assertEquals("blood-test-2026.txt", OwnFiles.named("What's in my blood test?", files)?.name)
        assertEquals("car-insurance.pdf", OwnFiles.named("When does my car insurance end?", files)?.name)
        // A longer name needs two of its words.
        assertEquals("patagonia-trip-plan.md", OwnFiles.named("What did I plan for the Patagonia trip?", files)?.name)
        assertEquals("festival-notes.txt", OwnFiles.named("What do my festival notes say?", files)?.name)
    }

    @Test fun `generic words, half a name or a tie name no file`() {
        assertNull(OwnFiles.named("What do my notes say?", files))
        assertNull(OwnFiles.named("Is a blood donation safe?", files)) // one of "blood test"'s two words
        assertNull(OwnFiles.named("Tell me about Patagonia", files)) // one of three words
        val twins = listOf(OwnFiles.File("a", "car-insurance.pdf", 1), OwnFiles.File("b", "car-insurance-2025.pdf", 1))
        assertNull(OwnFiles.named("my car insurance", twins))
        // Two different files named: both stay in the search.
        assertNull(OwnFiles.named("Compare my car insurance with the Patagonia trip plan", files))
    }

    // --- The list in the prompt -----------------------------------------------------------------------------------

    @Test fun `the prompt lists the files once each, up to twenty`() {
        assertNull(OwnFiles.note(emptyList()))
        assertEquals("The user's Personal Files (2): a.txt; b.pdf.", OwnFiles.note(listOf(OwnFiles.File("1", "a.txt", 1), OwnFiles.File("2", "b.pdf", 1), OwnFiles.File("3", "a.txt", 1))))
        val many = (1..25).map { OwnFiles.File("$it", "f$it.txt", 1) }
        assertTrue(OwnFiles.note(many)!!.endsWith("f20.txt; and 5 more."))
    }

    @Test fun `the list sits before the context, and prompts without it don't change`() {
        val c = listOf(Chunk("x-c0", "x", "notes", "The festival is in May."))
        val without = RagPure.assembleChatMessages("When?", c, filesOnly = true)
        assertEquals(without, RagPure.assembleChatMessages("When?", c, filesOnly = true, filesNote = null))
        val system = RagPure.assembleChatMessages("When?", c, filesOnly = true, filesNote = "The user's Personal Files (1): notes.txt.").first().content
        assertTrue(system, system.contains("The user's Personal Files (1): notes.txt.\n\nContext:\n[1] notes"))
        val plain = RagPure.assemblePrompt("When?", c, filesOnly = true, filesNote = "The user's Personal Files (1): notes.txt.")
        assertTrue(plain, plain.contains("The user's Personal Files (1): notes.txt.\n\nContext:"))
    }

    // --- Cards ----------------------------------------------------------------------------------------------------

    private val longMarkdown = "# Patagonia trip\nPlan for March.\n\n## Budget\nAbout 3,000 USD.\n\n## Packing\nTent, stove.\n" + "x".repeat(2_100)

    @Test fun `a long file gets a card with its outline, a short one none`() {
        val card = OwnFiles.card("c-doc0-trip", "c", "trip", "trip.md", longMarkdown, parts = 4, size = "3 kB")
        assertNotNull(card)
        assertEquals("c-doc0-trip-card", card!!.chunkId)
        assertTrue(card.body, card.body.startsWith("File: trip.md (Markdown, 3 kB, 4 parts).\nSections: Patagonia trip; Budget; Packing.\nStarts: # Patagonia trip Plan for March."))
        assertNull(OwnFiles.card("c-doc1-n", "c", "n", "n.txt", "Short.", parts = 1, size = "1 kB"))
    }

    @Test fun `import cards only the long files that are one document`() {
        val files = listOf(
            DocumentImport.File("trip.md", 3_000, longMarkdown),
            DocumentImport.File("short.txt", 10, "Short note."),
            DocumentImport.File("list.json", 100, """[{"title":"A","body":"${"a".repeat(2_500)}"},{"title":"B","body":"b"}]"""),
        )
        assertEquals(listOf("c-doc0-trip-card"), DocumentImport.cards("c", files).map { it.chunkId })
    }

    @Test fun `a card goes once a part of its file is there`() {
        val card = Chunk("d-card", "d", "d (about this file)", "File: d.md")
        val part = Chunk("d-c1", "d", "d (part 2/3)", "text")
        val other = Chunk("e-card", "e", "e (about this file)", "File: e.md")
        assertEquals(listOf("d-c1", "e-card"), OwnFiles.dropCoveredCards(listOf(card, part, other)).map { it.chunkId })
    }

    // --- Through the retrieval, on a real SQLite -----------------------------------------------------------------

    private val questionVec = floatArrayOf(1f, 0f, 0f)
    /** Far from any question here: cosine 0 with [questionVec]. */
    private val farVec = floatArrayOf(0f, 1f, 0f)
    private val ownOnly = RetrievalParams.DEFAULT.copy(ownOnly = true)

    private fun kb(active: Boolean = true) = KnowledgeBase(JdbcSqlite()).apply {
        insertChunk(Chunk("wiki-1", "festival", "Festival", "A festival is an event celebrated by a community."), questionVec)
        createCollection("c", "Setup files", "festival-notes.txt, blood-test-2026.txt", 2, 4, 500, now = 1)
        insertChunk(Chunk("c-doc0-festival-notes-c0", "c-doc0-festival-notes", "festival-notes",
            "The boar festival is on the third Saturday of May. The gate code for volunteers is 4471.", source = "festival-notes.txt", collectionId = "c"), farVec)
        insertChunk(Chunk("c-doc1-blood-test-2026-c0", "c-doc1-blood-test-2026", "blood-test-2026 (part 1/2)",
            "Blood test, March 2026. Iron: 52 ug/dL (low).", source = "blood-test-2026.txt", collectionId = "c"), farVec)
        insertChunk(Chunk("c-doc1-blood-test-2026-c1", "c-doc1-blood-test-2026", "blood-test-2026 (part 2/2)",
            "Vitamin D: 18 ng/mL (low). Retest in June.", source = "blood-test-2026.txt", collectionId = "c"), floatArrayOf(0.3f, 0.95f, 0f))
        insertChunk(Chunk("c-doc1-blood-test-2026-card", "c-doc1-blood-test-2026", "blood-test-2026 (about this file)",
            "File: blood-test-2026.txt (text, 1 kB, 2 parts).\nStarts: Blood test, March 2026.", source = "blood-test-2026.txt", collectionId = "c"), farVec)
        if (!active) setCollectionActive("c", false)
    }

    private fun retriever(kb: KnowledgeBase) = Retriever(kb, { emptyList() }, { questionVec }, { emptyMap() })

    @Test fun `the switched-on files, with their part counts and no cards`() {
        assertEquals(listOf(OwnFiles.File("c-doc0-festival-notes", "festival-notes.txt", 1), OwnFiles.File("c-doc1-blood-test-2026", "blood-test-2026.txt", 2)), kb().ownFiles())
        assertEquals(emptyList<OwnFiles.File>(), kb(active = false).ownFiles())
    }

    @Test fun `"main points of my files" brings each file's opening part, where search alone found nothing`() {
        val k = kb()
        // Before: no passage is close to the question (the X6-style miss on the emulator, Oct 23).
        assertEquals(emptyList<String>(), k.semanticSearch(questionVec, 4, 0.55, ownOnly = true).map { it.chunkId })
        val found = retriever(k).retrieve("What are the main points of my files?", 4, params = ownOnly).map { it.chunkId }
        assertEquals(listOf("c-doc0-festival-notes-c0", "c-doc1-blood-test-2026-c0"), found)
    }

    @Test fun `a named file is searched alone and without the floor`() {
        val found = retriever(kb()).retrieve("What does my blood test say?", 4, params = ownOnly)
        assertEquals(listOf("c-doc1-blood-test-2026-c1", "c-doc1-blood-test-2026-c0"), found.map { it.chunkId })
    }

    @Test fun `the default style keeps its search, with no opening parts and no file filter`() {
        val found = retriever(kb()).retrieve("What are the main points of my files?", 4).map { it.chunkId }
        assertTrue(found.toString(), found.none { it.startsWith("c-") })
    }

    @Test fun `a card in the hits doesn't take its file's opening in an overview`() {
        val card = Chunk("d-card", "d", "d (about this file)", "File: d.md")
        val hitE1 = Chunk("e-c3", "e", "e (part 4/9)", "x")
        val hitE2 = Chunk("e-c5", "e", "e (part 6/9)", "y")
        val openings = listOf(Chunk("d-c0", "d", "d", "d starts"), Chunk("e-c0", "e", "e", "e starts"), Chunk("f-c0", "f", "f", "f starts"))
        // Hits first (up to 2 here), no card; then the opening of every file they don't cover.
        assertEquals(listOf("e-c3", "e-c5", "d-c0", "f-c0"), OwnFiles.withOpenings(listOf(card, hitE1, hitE2), openings, hits = 2, limit = 12).map { it.chunkId })
    }

    @Test fun `a named JSON list file searches all of its documents`() {
        val k = KnowledgeBase(JdbcSqlite()).apply {
            createCollection("j", "recipes", "recipes.json", 2, 2, 100, now = 1)
            insertChunk(Chunk("j-doc0-soup-c0", "j-doc0-soup", "Soup", "Onion soup.", source = "recipes.json", collectionId = "j"), farVec)
            insertChunk(Chunk("j-doc1-bread-c0", "j-doc1-bread", "Bread", "Sourdough bread.", source = "recipes.json", collectionId = "j"), questionVec)
        }
        val found = retriever(k).retrieve("What's in my recipes?", 4, params = ownOnly).map { it.chunkId }
        assertEquals(listOf("j-doc1-bread-c0", "j-doc0-soup-c0"), found)
    }

    @Test fun `an export leaves the cards out`() {
        assertEquals(listOf("festival-notes", "blood-test-2026 (part 1/2)", "blood-test-2026 (part 2/2)"), kb().collectionDocs("c").map { it.title })
    }

    @Test fun `switched-off files stay out of every Personal Files path`() {
        val r = retriever(kb(active = false))
        assertEquals(emptyList<Chunk>(), r.retrieve("What are the main points of my files?", 4, params = ownOnly))
        assertEquals(emptyList<Chunk>(), r.retrieve("What does my blood test say?", 4, params = ownOnly))
    }
}
