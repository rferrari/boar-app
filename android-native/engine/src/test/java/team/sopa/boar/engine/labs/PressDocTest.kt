package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.labs.PressDoc.Block

/** Press projects (adam's mock-up, Nov 13): the basket becomes "Field notes", several projects, a deleted chat leaves every one. */
class PressDocTest {
    private val basket = """[
        {"id":"m1","chatTitle":"Coast","question":"Why is the sea salty?","text":"Rivers bring **salt** [1].","addedAt":10,"model":"Qwen3-4B","tokPerSec":4.6,
         "image":null,"imageOff":false,"sources":[{"title":"Seawater","source":"Wikipedia"},{"title":"Red Sea","source":null}]},
        {"id":"m2","chatTitle":"Coast","question":"Tide pools","text":"Rock basins.","addedAt":11,"model":null,"tokPerSec":null,
         "image":"/data/labs/images/a.png","imageOff":false,"sources":[]},
        {"id":"m3","chatTitle":"Coast","question":"Crabs?","text":"Yes.","addedAt":12,"model":"Qwen3-4B","tokPerSec":4.8,
         "image":null,"imageOff":true,"sources":[]}
    ]"""

    private fun answer(id: String, vararg src: String, at: Long = 100) =
        Block.Answer(id, "Chat", "Question $id", "Text $id", src.map { PressDoc.Source(it, "Wikipedia") }, at, "Qwen3-4B", 5.0)

    @Test fun `the basket becomes Field notes, in order, nothing lost`() {
        val lib = PressDoc.fromBasket(basket, "p1", "Field notes", 1000, illustrations = mapOf("m1" to "/own/m1.png", "m3" to "/own/m3.png"))
        assertEquals(1, lib.projects.size)
        val p = lib.current
        assertEquals("Field notes", p.name)
        assertEquals("p1", lib.open)
        // m1, its own illustration; m2, the drawing picked for it; m3 had its drawing switched off.
        assertEquals(listOf("m1", "pic-m1", "m2", "pic-m2", "m3"), p.blocks.map { it.id })
        val a = p.blocks[0] as Block.Answer
        assertEquals("Why is the sea salty?", a.question)
        assertEquals("Rivers bring **salt** [1].", a.text)
        assertEquals(listOf(PressDoc.Source("Seawater", "Wikipedia"), PressDoc.Source("Red Sea", null)), a.sources)
        assertEquals("Qwen3-4B", a.model)
        assertEquals(4.6, a.tokPerSec!!, 0.0)
        assertEquals("Coast", a.chatTitle)
        assertEquals(10L, a.addedAt)
        assertNull((p.blocks[2] as Block.Answer).model)
        assertEquals("/own/m1.png", (p.blocks[1] as Block.Picture).file)
        assertEquals("m1", (p.blocks[1] as Block.Picture).answerId)
        assertEquals("/data/labs/images/a.png", (p.blocks[3] as Block.Picture).file)
        // Migrated answers aren't announced as new.
        assertEquals(0, p.unseen)
    }

    @Test fun `migrated once, and an unreadable file is never written over`() {
        // No projects file: the basket is migrated.
        assertTrue(PressDoc.load(null, basket, "p1", "Field notes", 1) is PressDoc.Loaded.Migrated)
        // A projects file exists: the basket is not read again, whatever it holds.
        val saved = PressDoc.toJson(PressDoc.emptyLibrary("p9", "Mine", 5))
        val read = PressDoc.load(saved, basket, "p1", "Field notes", 1)
        assertTrue(read is PressDoc.Loaded.Read)
        assertEquals("Mine", (read as PressDoc.Loaded.Read).library.current.name)
        assertTrue(read.library.current.blocks.isEmpty())
        // A projects file that can't be read: Unreadable, so the app writes nothing over it.
        assertTrue(PressDoc.load("{not json", basket, "p1", "Field notes", 1) is PressDoc.Loaded.Unreadable)
        assertTrue(PressDoc.load("""{"projects":[]}""", null, "p1", "Field notes", 1) is PressDoc.Loaded.Unreadable)
        // A basket that can't be read: a fresh project, and the app is told to keep the basket file.
        val bad = PressDoc.load(null, "[{\"broken\":", "p1", "Field notes", 1)
        assertTrue(bad is PressDoc.Loaded.Fresh && bad.basketUnreadable)
        // Nothing at all: a first empty project.
        val none = PressDoc.load(null, null, "p1", "Notas de campo", 1)
        assertTrue(none is PressDoc.Loaded.Fresh && !none.basketUnreadable)
        assertEquals("Notas de campo", (none as PressDoc.Loaded.Fresh).library.current.name)
    }

    @Test fun `round trip through JSON, a newer block kept as it is`() {
        var lib = PressDoc.emptyLibrary("p1", "Field notes", 1)
        lib = lib.addAnswer(answer("a1", "Lisbon"), listOf(Block.Picture("pic-a1", PressDoc.Kind.DRAWING, "/d.png", answerId = "a1",
            recipe = PressDoc.Recipe("a boar", "engraving", "384", 48213), width = 384, height = 512)))
        lib = lib.insert(Block.Heading("h1", "Day 1"), 0).insert(Block.Note("n1", "Ask about the lighthouse."), 99)
        lib = lib.editText("a1", PressDoc.Field.HEADING, "Lisbon")
        val text = PressDoc.toJson(lib)
        val back = PressDoc.parse(text)
        assertEquals(lib, back)
        // A block type from a later version survives a load and a save.
        val newer = text.replace("\"blocks\":[", "\"blocks\":[{\"type\":\"table\",\"id\":\"t1\",\"rows\":[[1,2]]},")
        val kept = PressDoc.parse(newer)
        assertTrue(kept.current.blocks.first() is Block.Unknown)
        assertTrue(PressDoc.toJson(kept).contains("\"rows\":[[1,2]]"))
    }

    @Test fun `several projects, each its own document`() {
        var lib = PressDoc.emptyLibrary("p1", "Field notes", 1).addAnswer(answer("a1"))
        lib = lib.newProject("p2", "Trip to Lisbon", 2)
        assertEquals("p2", lib.open)
        assertEquals(listOf("p2", "p1"), lib.projects.map { it.id })
        assertTrue(lib.current.blocks.isEmpty())
        // 📎 goes to the open project, at the end, once.
        lib = lib.addAnswer(answer("a2")).addAnswer(answer("a3")).addAnswer(answer("a2"))
        assertEquals(listOf("a2", "a3"), lib.current.blocks.map { it.id })
        assertEquals(listOf("a1"), lib.projects.first { it.id == "p1" }.blocks.map { it.id })
        // The same answer can be in two projects.
        lib = lib.switchTo("p1").addAnswer(answer("a2"))
        assertEquals(listOf("a1", "a2"), lib.current.blocks.map { it.id })
        lib = lib.rename("p1", "  Coast  ").rename("p1", "   ")
        assertEquals("Coast", lib.current.name)
        // Delete: the open one goes, another opens. The last one goes too, a blank one in its place (adam, Nov 14).
        lib = lib.deleteProject("p1")
        assertEquals(listOf("p2"), lib.projects.map { it.id })
        assertEquals("p2", lib.open)
        assertEquals(lib, lib.deleteProject("p2"))
        val blank = PressDoc.Project("p9", "Untitled", 5, seenAt = 5)
        val after = lib.deleteProject("p2", blank)
        assertEquals(listOf("p9"), after.projects.map { it.id }); assertEquals("p9", after.open)
        assertTrue(after.current.blocks.isEmpty() && after.current.name == "Untitled")
        assertEquals(lib, lib.deleteProject("nope", blank))
    }

    @Test fun `blocks move, are removed and come back with Undo, and are edited in place`() {
        var lib = PressDoc.emptyLibrary("p1", "Field notes", 1).addAnswer(answer("a1")).addAnswer(answer("a2"))
            .insert(Block.Note("n1", "note"), 1)
        assertEquals(listOf("a1", "n1", "a2"), lib.current.blocks.map { it.id })
        lib = lib.move("a2", -1).move("a1", -1)
        assertEquals(listOf("a1", "a2", "n1"), lib.current.blocks.map { it.id })
        val removed = lib.current.blocks[1]
        lib = lib.remove("a2")
        assertEquals(listOf("a1", "n1"), lib.current.blocks.map { it.id })
        lib = lib.insert(removed, 1)
        assertEquals(listOf("a1", "a2", "n1"), lib.current.blocks.map { it.id })
        lib = lib.editText("n1", PressDoc.Field.TEXT, "changed").editText("a1", PressDoc.Field.TEXT, "new text")
        assertEquals("changed", (lib.current.blocks[2] as Block.Note).text)
        assertEquals("new text", (lib.current.blocks[0] as Block.Answer).text)
        // A heading typed back to the question is no heading of its own: the question stands, no italic line.
        lib = lib.editText("a1", PressDoc.Field.HEADING, "Short")
        assertEquals("Short", (lib.current.blocks[0] as Block.Answer).shownHeading)
        assertEquals("Question a1", (lib.current.blocks[0] as Block.Answer).shownQuestion)
        lib = lib.editText("a1", PressDoc.Field.HEADING, "Question a1")
        assertEquals("", (lib.current.blocks[0] as Block.Answer).heading)
        assertNull((lib.current.blocks[0] as Block.Answer).shownQuestion)
    }

    @Test fun `a deleted chat's answers leave every project, with their drawings`() {
        var lib = PressDoc.emptyLibrary("p1", "Field notes", 1)
            .addAnswer(answer("a1"), listOf(Block.Picture("pic-a1", PressDoc.Kind.DRAWING, "/d.png", answerId = "a1"))).addAnswer(answer("b1"))
            .insert(Block.Picture("pic-x", PressDoc.Kind.DRAWING, "/x.png"), 0)
        lib = lib.newProject("p2", "Two", 2).addAnswer(answer("a1")).addAnswer(answer("a2")).insert(Block.Note("n", "kept"), 0)
        assertEquals(3, lib.countOf(listOf("a1", "a2")))
        lib = lib.eraseAnswers(listOf("a1", "a2"))
        assertEquals(listOf("pic-x", "b1"), lib.projects.first { it.id == "p1" }.blocks.map { it.id })
        assertEquals(listOf("n"), lib.projects.first { it.id == "p2" }.blocks.map { it.id })
        assertEquals(0, lib.countOf(listOf("a1", "a2")))
        assertFalse(lib.has("a1"))
    }

    @Test fun `new answers since the last look make the banner`() {
        var lib = PressDoc.emptyLibrary("p1", "Field notes", 50)
        lib = lib.addAnswer(answer("a1", at = 60)).addAnswer(answer("a2", at = 70))
        assertEquals(2, lib.current.unseen)
        lib = lib.markSeen(80)
        assertEquals(0, lib.current.unseen)
        // Taking an answer out takes its drawing with it.
        lib = lib.addAnswer(answer("a3", at = 90), listOf(Block.Picture("pic-a3", PressDoc.Kind.DRAWING, "/d.png", answerId = "a3")))
        assertEquals(1, lib.current.unseen)
        lib = lib.removeAnswer("a3")
        assertEquals(listOf("a1", "a2"), lib.current.blocks.map { it.id })
    }
}
