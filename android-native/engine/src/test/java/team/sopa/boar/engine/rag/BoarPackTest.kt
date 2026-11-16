package team.sopa.boar.engine.rag

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.db.JdbcSqlite

/** adam, 16 Nov (t9 0225, 0233): BOAR's guide and FAQ as one built-in pack, both languages, so BOAR can answer about itself. */
class BoarPackTest {
    private val guides = listOf("en", "pt").associateWith { java.io.File("../app/src/main/assets/boar-guide/$it.md").readText() }
    private val faq = java.io.File("../app/src/main/assets/boar-faq/faq.json").readText()
    private val all = BoarPack.passages(guides, faq)

    /** A stand-in for the search model: one dimension per word, so the same words score high. */
    private val vocab = HashMap<String, Int>()
    private fun vec(t: String): FloatArray {
        val v = FloatArray(4096)
        Regex("\\p{L}+").findAll(t.lowercase()).forEach { v[vocab.getOrPut(it.value) { vocab.size % 4096 }] += 1f }
        val n = kotlin.math.sqrt(v.sumOf { (it * it).toDouble() }).toFloat().takeIf { it > 0 } ?: 1f
        return FloatArray(v.size) { v[it] / n }
    }

    @Test fun `every guide section and FAQ entry, in both languages, each knowing its language`() {
        val sections = guides.mapValues { (_, md) -> Regex("(?m)^## ").findAll(md).count() }
        for (lang in listOf("en", "pt")) {
            assertTrue(lang, all.count { it.lang == lang && it.id.contains("-guide-") } >= sections.getValue(lang))
            assertEquals(lang, 10, all.count { it.lang == lang && it.id.contains("-faq-") })
        }
        assertTrue(all.all { BoarPack.langOf(it.id) == it.lang && it.body.length <= BoarPack.MAX_CHARS + 400 })
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(all.any { it.title == "BOAR Guide · Privacy" } && all.any { it.title == "Guia do BOAR · Privacidade" })
        assertTrue(all.any { it.title == "BOAR FAQ · What is the BOAR app?" && it.source == "BOAR FAQ, written by Amandita" })
        assertTrue(all.any { it.source == "FAQ do BOAR, escrito pela Amandita" })
    }

    @Test fun `the FAQ ships only text checked against rc3`() {
        val e = kotlinx.serialization.json.Json.parseToJsonElement(faq).jsonObjectEntries()
        assertTrue(e.all { it.startsWith("ok for rc3") || it.startsWith("corrected for rc3") })
        // October's facts that rc3 changed are gone.
        for (old in listOf("Qwen2.5-1.5B", "Executive Summary", "Resumo executivo", "copied over a cable", "copiados por cabo", "you can choose to share an evaluation")) assertFalse(old, faq.contains(old))
        assertTrue(faq.contains("Amandita"))
        // Credited in About & Info too, EN and PT (t9 0315).
        val credits = java.io.File("../app/src/main/java/team/sopa/boar/ui/about/AboutCredits.kt").readText()
        assertTrue(credits.contains("\"native.about.creditFaq\" to \"MIT\""))
        for (d in listOf("values", "values-pt")) assertTrue(d, java.io.File("../app/src/main/res/$d/strings_native.xml").readText().contains(Regex("name=\"native_about_creditFaq\"[^>]*>[^<]*Amandita \\(@AAmandita_\\)[^<]*Zebra Talks")))
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectEntries(): List<String> =
        (this as kotlinx.serialization.json.JsonObject)["entries"]!!.let { it as kotlinx.serialization.json.JsonArray }
            .map { ((it as kotlinx.serialization.json.JsonObject)["checked"] as kotlinx.serialization.json.JsonPrimitive).content }

    @Test fun `a changed text replaces its passage, an unchanged one isn't embedded again`() = runBlocking {
        val kb = KnowledgeBase(JdbcSqlite())
        var embeds = 0
        assertEquals(all.size, kb.syncBoar(all) { embeds++; vec(it) })
        assertEquals(0, kb.syncBoar(all) { embeds++; vec(it) })
        val edited = BoarPack.passages(mapOf("en" to guides.getValue("en").replace("## Privacy\n", "## Privacy\nEdited line.\n"), "pt" to guides.getValue("pt")), faq)
        assertEquals(1, kb.syncBoar(edited) { embeds++; vec(it) })
        assertEquals(all.size + 1, embeds)
        // No search model yet: nothing stored, tried again later.
        assertEquals(0, KnowledgeBase(JdbcSqlite()).syncBoar(all) { FloatArray(0) })
    }

    @Test fun `out of the general search, the seed count and the Daily Quiz, found by its own search`() = runBlocking {
        val kb = KnowledgeBase(JdbcSqlite())
        kb.insertChunk(Chunk("wiki-min-privacy", "wiki-min-privacy", "Privacy", "Privacy is the ability of an individual to keep information about themselves to themselves."), vec("Privacy is the ability of an individual to keep information"))
        kb.syncBoar(all) { vec(it) }
        val q = "Does my data leave the phone? Privacy"
        assertTrue(kb.semanticSearch(vec(q), 50, 0.0).none { it.chunkId.startsWith(BoarPack.PREFIX) })
        assertTrue(kb.lexicalSearch(q, vec(q), 50).none { it.chunkId.startsWith(BoarPack.PREFIX) })
        assertEquals(1L, kb.seedChunkCount())
        assertTrue(kb.seedArticles(1, 100).none { it.first.startsWith("BOAR") || it.first.startsWith("Guia") })
        val found = kb.boarSearch(q, vec(q), 6)
        assertTrue(found.isNotEmpty() && found.all { it.chunkId.startsWith(BoarPack.PREFIX) && it.source != null })
    }

    @Test fun `the question's language first, the other when it has nothing good`() {
        fun c(id: String, sim: Double) = Chunk(id, id, id, "", similarity = sim)
        val hits = listOf(c("boar-en-faq-a", 0.80), c("boar-pt-faq-a", 0.62), c("boar-en-guide-b", 0.70))
        assertEquals(listOf("boar-pt-faq-a", "boar-en-faq-a", "boar-en-guide-b"), BoarPack.rank(hits, "pt").map { it.chunkId })
        assertEquals(listOf("boar-en-faq-a", "boar-en-guide-b", "boar-pt-faq-a"), BoarPack.rank(hits, "en").map { it.chunkId })
        // A section only in English still answers a Portuguese question.
        assertEquals(listOf("boar-en-guide-b"), BoarPack.rank(listOf(c("boar-en-guide-b", 0.70), c("boar-pt-faq-z", 0.40)), "pt").map { it.chunkId })
    }

    @Test fun `questions about BOAR, EN and PT, and not the rest`() {
        for (q in listOf("What is BOAR?", "Does my data leave the phone?", "Does this app send telemetry?", "How do I set a PIN?", "Como funcionam os Arquivos locais?",
            "O que é a Pesquisa Profunda?", "Preciso comprar o token \$BOAR?", "O app funciona sem internet?")) assertTrue(q, BoarPack.aboutBoar(q))
        for (q in listOf("What are the models of the atom?", "How do wild pigs live?", "How to pin a message on Telegram?", "Is the battery of an electric car recyclable?"))
            assertFalse(q, BoarPack.aboutBoar(q))
    }

    @Test fun `the guide's list of plugins that start on is LabDefaults ON`() {
        val title = { id: String -> java.io.File("../app/src/labs").walk().filter { it.name.endsWith(".xml") && it.parentFile.name == "values" }
            .firstNotNullOfOrNull { Regex("name=\"native_labs_${id}_title\" formatted=\"false\">([^<]+)<").find(it.readText())?.groupValues?.get(1) } ?: id }
        val listed = Regex("The tested plugins start on \\(([^)]+)\\)").find(guides.getValue("en"))!!.groupValues[1].split(", ").toSet()
        assertEquals(team.sopa.boar.engine.labs.LabDefaults.ON.map(title).toSet(), listed)
    }

    @Test fun `a shared PDF prints BOAR's sources, they are not the user's files`() {
        assertTrue(team.sopa.boar.engine.labs.PressReceipt.shareable("BOAR Guide") && team.sopa.boar.engine.labs.PressReceipt.shareable("FAQ do BOAR, escrito pela Amandita"))
        assertFalse(team.sopa.boar.engine.labs.PressReceipt.shareable("BOAR Guide notes.pdf"))
    }
}
