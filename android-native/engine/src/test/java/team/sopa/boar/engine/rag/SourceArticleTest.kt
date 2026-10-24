package team.sopa.boar.engine.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.db.JdbcSqlite
import team.sopa.boar.engine.db.jvmZstd
import java.io.File
import java.nio.file.Files

/** A source under an answer, read whole (Oct 24), from each store it can come from, on a real SQLite. */
class SourceArticleTest {
    private val v = floatArrayOf(1f, 0f, 0f)

    @Test fun `a Personal Files document cut with overlap reads back as the file, once`() {
        val text = (1..900).joinToString(" ") { "word$it" } // about 7,000 characters: four parts with overlap
        val parts = DocumentImport.chunkText(text)
        assertTrue(parts.size >= 3)
        val kb = KnowledgeBase(JdbcSqlite()).apply {
            createCollection("c", "notes", "trip.txt", 1, parts.size.toLong(), text.length.toLong(), now = 1)
            // Inserted out of order, and past c9: the parts sort by their number, not as text.
            parts.indices.reversed().forEach { i -> insertChunk(Chunk("c-doc0-trip-c$i", "c-doc0-trip", "trip (part ${i + 1}/${parts.size})", parts[i], source = "trip.txt", collectionId = "c"), v) }
        }
        val a = SourceArticle.read("c-doc0-trip", "trip (part 2/4)", kb, emptyList())!!
        assertEquals("trip", a.title)
        assertEquals("trip.txt", a.source)
        assertEquals(text, a.text)
        assertTrue(!a.markdown)
    }

    @Test fun `a file's card is not read as part of the file`() {
        val text = (1..900).joinToString(" ") { "word$it" }
        val parts = DocumentImport.chunkText(text)
        val kb = KnowledgeBase(JdbcSqlite()).apply {
            createCollection("c", "notes", "trip.txt", 1, parts.size.toLong(), text.length.toLong(), now = 1)
            parts.forEachIndexed { i, p -> insertChunk(Chunk("c-doc0-trip-c$i", "c-doc0-trip", "trip (part ${i + 1}/${parts.size})", p, source = "trip.txt", collectionId = "c"), v) }
            // The card OwnFiles makes for a long file: same doc_id, its own chunk id (CodeRabbit: it showed inside the file).
            insertChunk(Chunk("c-doc0-trip${OwnFiles.CARD_SUFFIX}", "c-doc0-trip", "trip (about this file)", "File: trip.txt. Starts: word1 word2", source = "trip.txt", collectionId = "c"), v)
        }
        assertEquals(text, SourceArticle.read("c-doc0-trip", "trip", kb, emptyList())!!.text)
    }

    @Test fun `parts past c9 keep their order`() {
        val joined = SourceArticle.join(listOf("a".repeat(30) + "XYZ", "XYZ" + "b".repeat(30)))
        assertEquals("a".repeat(30) + "XYZ\n\nXYZ" + "b".repeat(30), joined) // 3 chars repeat: under the 20 a cut overlaps by
        val kb = KnowledgeBase(JdbcSqlite()).apply {
            for (i in listOf(10, 2, 0, 1)) insertChunk(Chunk("d-c$i", "d", "d", "part$i.", source = "d.md"), v)
        }
        val a = SourceArticle.read("d", "d", kb, emptyList())!!
        assertEquals("part0.\n\npart1.\n\npart2.\n\npart10.", a.text)
        assertTrue(a.markdown)
    }

    @Test fun `a built-in library document reads back`() {
        val kb = KnowledgeBase(JdbcSqlite()).apply { insertChunk(Chunk("wiki-min-boiling", "wiki-min-boiling", "Boiling", "Boiling is the rapid vaporization of a liquid.", source = "https://en.wikipedia.org/wiki/Boiling"), v) }
        val a = SourceArticle.read("wiki-min-boiling", "Boiling", kb, emptyList())!!
        assertEquals("Boiling", a.title)
        assertEquals("Boiling is the rapid vaporization of a liquid.", a.text)
    }

    @Test fun `a source that is no longer on the phone reads as null`() {
        val kb = KnowledgeBase(JdbcSqlite())
        assertNull(SourceArticle.read("custom-gone-doc0-x", "x", kb, emptyList()))
        assertNull(SourceArticle.read("pack:wiki-vital5:a12", "Nosebleed", kb, emptyList())) // the pack was removed
        assertNull(SourceArticle.read("pack:old:Nosebleed", "Nosebleed", kb, emptyList()))
    }

    private val dir = File("src/test/resources/golden/packs")
    private fun copy(name: String) = Files.createTempDirectory("boar-read").toFile().let { d -> File(d, name).also { File(dir, name).copyTo(it) }.path }

    @Test fun `a format-2 pack article reads whole, with its source line`() {
        val db = JdbcSqlite(copy("mini.sqlite"))
        val (id, title) = db.query("SELECT id, title FROM articles ORDER BY id LIMIT 1") { it.long(0) to it.string(1)!! }.first()
        val pack = OpenPack.Format2("local-mini", WikiPack.open(db, jvmZstd))
        val a = SourceArticle.read("pack:local-mini:a$id", title, KnowledgeBase(JdbcSqlite()), listOf(pack))
        assertNotNull(a)
        assertEquals(title, a!!.title)
        assertEquals(pack.pack.article(id).text, a.text)
        assertTrue(a.source!!, a.source!!.contains("https://"))
        assertTrue(a.markdown)
    }

    @Test fun `a format-1 pack's passages with that title read back in order`() {
        val db = JdbcSqlite(copy("format1.sqlite"))
        val title = db.query("SELECT title FROM chunks ORDER BY id LIMIT 1") { it.string(0)!! }.first()
        val bodies = db.query("SELECT body FROM chunks WHERE title = ? ORDER BY id", listOf(title)) { it.string(0)!! }
        val a = SourceArticle.read("pack:local-fixture:$title", title, KnowledgeBase(JdbcSqlite()), listOf(OpenPack.Format1("local-fixture", db)))!!
        assertEquals(bodies.joinToString("\n\n"), a.text)
    }

    @Test fun `the passage the model read is found as is, without its section, or by its start`() {
        val text = "Intro.\n\n## Treatment\nPinch the soft part of the nose for ten minutes. Lean forward, not back.\n\nMore text."
        val exact = "Pinch the soft part of the nose for ten minutes."
        assertEquals(text.indexOf(exact) until text.indexOf(exact) + exact.length, SourceArticle.passageAt(text, exact))
        // Packs.packHitToChunk prefixes the section.
        val r = SourceArticle.passageAt(text, "Treatment: $exact")!!
        assertEquals(exact, text.substring(r))
        // A passage the answer shortened (Compress drops sentences) still finds its unchanged start, 120 characters.
        val article = "Intro.\n\nPinch the soft part of the nose for ten minutes, leaning forward and breathing through the mouth, " +
            "then keep pinching for another five minutes if it still bleeds. Later sentence one. Later sentence two."
        val shortened = "Pinch the soft part of the nose for ten minutes, leaning forward and breathing through the mouth, " +
            "then keep pinching for another five minutes if it still bleeds. Later sentence two."
        assertEquals(article.indexOf("Pinch"), SourceArticle.passageAt(article, shortened)!!.first)
        assertNull(SourceArticle.passageAt(text, "Nothing like this is in the article."))
    }
}
