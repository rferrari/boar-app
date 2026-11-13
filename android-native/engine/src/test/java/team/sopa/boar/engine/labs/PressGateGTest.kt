package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.labs.PressDoc.Block
import java.io.File
import java.nio.file.Files

/** t9 0217, the validator's Press gate (G-1 to G-6, G-9): each fix, apart from their harness (ZzPressGTest). */
class PressGateGTest {
    private fun app(p: String) = File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/$p").readText()
    private fun ans(id: String, text: String, vararg src: String) = Block.Answer(id, "chat", "Q?", text, src.map { PressDoc.Source(it, null) }, 1, "m", 5.0)
    private fun lib(vararg b: Block) = PressDoc.Library("p1", listOf(PressDoc.Project("p1", "Doc", 0, b.toList())))

    @Test fun `G-5 a used rewrite keeps the sources it still cites, renumbered, and the flag survives a save`() {
        val l = lib(ans("a1", "Paris [1]. Lyon [2].", "Paris", "Lyon")).editText("a1", PressDoc.Field.TEXT, "Lyon is calm [2].")
        val a = l.current.blocks[0] as Block.Answer
        assertTrue(a.edited)
        val r = PressReceipt.build(l.current)
        assertEquals(listOf("Lyon"), r.sources.map { it.title })
        assertEquals("Lyon is calm [1].", PressReceipt.renumber(a.text, r.local["a1"]!!))
        assertEquals(listOf(1), r.refs["a1"])
        assertEquals(l, PressDoc.parse(PressDoc.toJson(l)))
        // The chat's answer as it came keeps every source, cited or not.
        assertEquals(2, PressReceipt.build(lib(ans("a2", "No marks.", "X", "Y")).current).sources.size)
        // Changing only the heading is not a changed text.
        assertFalse((lib(ans("a3", "t [1]", "S")).editText("a3", PressDoc.Field.HEADING, "New").current.blocks[0] as Block.Answer).edited)
    }

    @Test fun `G-5b a mark with no source of its own is taken out, markdown line breaks stay`() {
        assertEquals("Fact. Next.", PressReceipt.renumber("Fact [4]. Next.", mapOf(1 to 1)))
        assertEquals("A [3] b.  \n  - nested", PressReceipt.renumber("A [1] b.  \n  - nested", mapOf(1 to 3)))
    }

    @Test fun `G-2 every shape, kept marks written plainly, and every option runs it`() {
        assertEquals("A fact [1].", PressRewrite.dropNewMarks("old [1]", "A fact [1, 5]."))
        assertEquals("A fact [1].", PressRewrite.dropNewMarks("old [1]", "A fact [1-5]."))
        assertEquals("Year [2024] stays.", PressRewrite.dropNewMarks("old [1]", "Year [2024] stays."))
        assertEquals(setOf(1, 2, 3), PressRewrite.marks("x [1–3]"))
        val rw = app("PressRewriter.kt")
        assertTrue(rw.contains("val out = PressRewrite.dropNewMarks(text, PressRewrite.clean(r.text))") && !rw.contains("if (option == PressRewrite.Option.OWN) PressRewrite.dropNewMarks"))
    }

    @Test fun `G-1 a removed photo's copy goes after the Undo, if nothing uses it again`() {
        val l = lib(Block.Picture("k", PressDoc.Kind.PHOTO, "keep.jpg"))
        assertTrue(PressFiles.unused(l, "p1", "gone.jpg"))
        assertFalse(PressFiles.unused(l, "p1", "keep.jpg"))
        assertTrue("another project's photo of that name is not this one", PressFiles.unused(l, "p2", "keep.jpg"))
        val s = app("PressScreen.kt")
        assertTrue(s.contains("PressStore.dropPhotoLater(ctx, pid, java.io.File(b.file).name, toast.millis + UNDO_GRACE_MS)"))
        assertTrue(app("PressStore.kt").contains("if (loaded && PressFiles.unused(lib, projectId, name))"))
    }

    @Test fun `G-9 an empty id or a dot name never names the Press folder, delete project of one leaves the rest`() {
        val press = Files.createTempDirectory("g9").toFile()
        File(press, "projects.json").writeText("{}")
        PressFiles.copyInto(press, "p1", "a.jpg") { it.write(1) }
        for (bad in listOf("", "  ", ".", "..")) assertEquals(bad, press.canonicalPath, PressFiles.projectDir(press, bad).parentFile.canonicalPath)
        for (bad in listOf("", ".", "..", "a/..")) assertEquals(bad, PressFiles.projectDir(press, "p1").canonicalPath, PressFiles.photoFile(press, "p1", bad).parentFile.canonicalPath)
        PressFiles.deleteProject(press, "")
        assertTrue(File(press, "projects.json").isFile && File(PressFiles.projectDir(press, "p1"), "a.jpg").isFile)
    }

    @Test fun `G-3 an old basket unreadable the first time is merged later as one more project`() {
        val basket = """[{"id":"a1","chatTitle":"T","question":"Q1","text":"Hello","sources":[],"addedAt":5}]"""
        val start = (PressDoc.load(null, "{broken", "p1", "Field notes", 1) as PressDoc.Loaded.Fresh).library
        val merged = PressDoc.mergeBasket(start, basket, "p2", "Field notes", 2)
        assertEquals(listOf("p1", "p2"), merged.projects.map { it.id })
        assertEquals(listOf("a1"), merged.projects[1].blocks.map { it.id })
        assertEquals(start, PressDoc.mergeBasket(start, "[]", "p3", "x", 3))
        assertTrue(runCatching { PressDoc.mergeBasket(start, "{still broken", "p3", "x", 3) }.isFailure)
        val store = app("PressStore.kt")
        assertTrue(store.contains("pendingMarker(context).writeText(\"1\")") && store.contains("PressDoc.mergeBasket(lib, basket,"))
    }

    @Test fun `G-6 mirrored orientations are mirrored back`() {
        assertEquals(listOf(0 to false, 0 to true, 180 to false, 180 to true, 90 to true, 90 to false, 270 to true, 270 to false),
            (1..8).map { PressFiles.upright(it) })
        assertTrue(app("PressPictures.kt").contains("setRotate(degrees.toFloat()); if (mirror) postScale(-1f, 1f)"))
    }

    @Test fun `G-4 Press claims the model in one step and the chat waits for it`() {
        val services = File("../app/src/main/java/team/sopa/boar/app/BoarServices.kt").readText()
        val claim = services.substringAfter("@Synchronized fun claimModelForLab(): Boolean {").substringBefore("\n    }")
        assertTrue(claim.contains("if (modelBusy()) return false") && claim.contains("labHolds.incrementAndGet()"))
        assertTrue(services.contains("private fun modelBusy(): Boolean = labHolds.get() > 0 ||"))
        val session = services.substringAfter("suspend fun activeSession(forLab: Boolean = false)").substringBefore("host.load(")
        assertTrue(session.contains("while (labHolds.get() > 0"))
        val rw = app("PressRewriter.kt")
        assertTrue(rw.contains("if (!services.claimModelForLab()) return Outcome.Busy") && rw.contains("services.activeSession(forLab = true)") && rw.contains("services.releaseModelForLab()"))
        assertFalse(rw.contains("addBusyCheck"))
    }
}
