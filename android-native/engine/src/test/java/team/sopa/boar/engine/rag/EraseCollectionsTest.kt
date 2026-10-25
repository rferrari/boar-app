package team.sopa.boar.engine.rag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.db.JdbcSqlite
import java.io.File
import java.nio.file.Files

/** Forgot PIN's erase (Oct 24, t9's review): a Personal Files text must not stay readable in the database's files. */
class EraseCollectionsTest {
    private fun onDisk(dir: File) = dir.walkTopDown().filter { it.isFile }.joinToString("") { String(it.readBytes(), Charsets.ISO_8859_1) }

    @Test fun `erased files leave no text in the database, its index or its log`() {
        val dir = Files.createTempDirectory("erase").toFile()
        val db = JdbcSqlite(File(dir, "kb.db").path)
        db.exec("PRAGMA journal_mode = WAL")
        val kb = KnowledgeBase(db)
        kb.insertChunk(Chunk("wiki-a", "wiki-a", "Lisbon", "Lisbon is the capital of Portugal.", source = "wiki"), floatArrayOf(1f, 0f, 0f))
        kb.createCollection("c-diary", "diary", "diary.txt", 1, 1, 10, now = 1)
        repeat(20) { i ->
            kb.insertChunk(Chunk("c-diary-doc0-c$i", "c-diary-doc0", "diary", "zebracorn lighthouse appointment number $i", source = "diary.txt", collectionId = "c-diary"), floatArrayOf(0f, 1f, 0f))
        }
        assertTrue(onDisk(dir).contains("zebracorn"))
        kb.eraseAllCollections()
        assertTrue(kb.collections().isEmpty())
        assertFalse("the erased text is still in the database's files", onDisk(dir).contains("zebracorn"))
        // The built-in library is untouched.
        assertTrue(onDisk(dir).contains("Lisbon"))
        db.close()
        dir.deleteRecursively()
    }

    @Test fun `an erase that can't empty the log fails instead of reporting success`() {
        val dir = Files.createTempDirectory("erase-busy").toFile()
        val db = JdbcSqlite(File(dir, "kb.db").path)
        db.exec("PRAGMA journal_mode = WAL")
        val kb = KnowledgeBase(db)
        kb.createCollection("c-diary", "diary", "diary.txt", 1, 1, 10, now = 1)
        kb.insertChunk(Chunk("c-diary-doc0-c0", "c-diary-doc0", "diary", "zebracorn", source = "diary.txt", collectionId = "c-diary"), floatArrayOf(0f, 1f, 0f))
        // Another connection in the middle of a read: the checkpoint can't truncate the log under it.
        val reader = java.sql.DriverManager.getConnection("jdbc:sqlite:" + File(dir, "kb.db").path)
        reader.autoCommit = false
        reader.createStatement().executeQuery("SELECT count(*) FROM chunks").use { it.next() }
        assertTrue(runCatching { kb.eraseAllCollections() }.isFailure)
        reader.rollback(); reader.close()
        db.close()
        dir.deleteRecursively()
    }
}
