package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.labs.PressDoc.Block
import team.sopa.boar.engine.labs.PressLayout.Item
import java.io.File

/** Press phase 2 (t9 0625 items 3 and 5): photos copied into the project, captions, and left / center / right with the text beside. */
class PressPicturesTest {
    private fun pic(id: String, place: PressDoc.Place = PressDoc.Place.CENTER, kind: PressDoc.Kind = PressDoc.Kind.DRAWING, caption: String = "") =
        Block.Picture(id, kind, "$id.jpg", caption = caption, place = place, width = 400, height = 300)
    private fun answer(id: String) = Block.Answer(id, "Chat", "Question $id", "Text $id")

    private fun tempDir() = kotlin.io.path.createTempDirectory("press").toFile()

    @Test fun `a photo is copied into its project's folder and goes with the project`() {
        val press = tempDir()
        try {
            var lib = PressDoc.emptyLibrary("p1", "Field notes", 0).newProject("p2", "Trip", 1)
            val f = PressFiles.copyInto(press, "p2", "photo-1.jpg") { it.write(byteArrayOf(1, 2, 3)) }
            assertEquals(File(press, "p2"), f.parentFile)
            assertTrue(f.isFile)
            assertEquals(3L, f.length())
            // No half copy left behind.
            assertFalse(File(f.path + ".tmp").exists())
            lib = lib.insert(Block.Picture("ph1", PressDoc.Kind.PHOTO, f.name, width = 4, height = 3), 0)
            assertEquals(f, PressFiles.photoFile(press, "p2", (lib.current.blocks[0] as Block.Picture).file))
            // A name can't leave the folder.
            assertEquals(File(press, "p2/x.jpg"), PressFiles.photoFile(press, "p2", "../../x.jpg"))
            // A copy that fails leaves nothing.
            runCatching { PressFiles.copyInto(press, "p2", "broken.jpg") { error("gone") } }
            assertFalse(File(press, "p2/broken.jpg").exists() || File(press, "p2/broken.jpg.tmp").exists())

            // Deleting the project deletes its folder and photo; the other project's folder stays.
            PressFiles.copyInto(press, "p1", "keep.jpg") { it.write(1) }
            lib = lib.deleteProject("p2")
            PressFiles.deleteProject(press, "p2")
            assertFalse(f.exists())
            assertFalse(File(press, "p2").exists())
            assertTrue(File(press, "p1/keep.jpg").isFile)
            assertEquals(listOf("p1"), lib.projects.map { it.id })

            // Clear all / Forgot PIN: everything.
            PressFiles.eraseAll(press)
            assertFalse(press.exists())
        } finally { press.deleteRecursively() }
    }

    @Test fun `files no project uses any more are found, the used ones kept`() {
        val press = tempDir()
        try {
            val lib = PressDoc.emptyLibrary("p1", "A", 0).insert(Block.Picture("ph", PressDoc.Kind.PHOTO, "used.jpg"), 0)
            PressFiles.copyInto(press, "p1", "used.jpg") { it.write(1) }
            PressFiles.copyInto(press, "p1", "removed.jpg") { it.write(1) }
            PressFiles.copyInto(press, "gone", "a.jpg") { it.write(1) }
            val orphans = PressFiles.orphans(press, lib).map { it.relativeTo(press).path }.toSet()
            assertEquals(setOf("p1/removed.jpg", "gone"), orphans)
        } finally { press.deleteRecursively() }
    }

    @Test fun `the app copies photos there, and erases them with the project and Clear all`() {
        val dir = "../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer"
        val store = File("$dir/PressStore.kt").readText()
        val pictures = File("$dir/PressPictures.kt").readText()
        val screen = File("$dir/PressScreen.kt").readText()
        // Android's photo picker: no permission.
        assertTrue(screen.contains("ActivityResultContracts.PickVisualMedia()"))
        assertTrue(screen.contains("PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)"))
        // Copied, downscaled, into the project's folder.
        val copy = pictures.substringAfter("fun copyPhoto(").substringBefore("\n    }")
        assertTrue(copy.contains("PressFiles.sampleSize(") && copy.contains("PressFiles.scaled(") && copy.contains("PressFiles.copyInto(PressStore.dir(ctx), projectId"))
        assertTrue(pictures.contains("PressDoc.Kind.PHOTO -> PressFiles.photoFile(PressStore.dir(ctx), projectId, p.file)"))
        assertTrue(store.substringAfter("fun deleteProject(context: Context, id: String)").substringBefore("\n    }").contains("PressFiles.deleteProject(dir(context), id)"))
        assertTrue(store.substringAfter("fun eraseAll(context: Context)").substringBefore("\n    }").contains("PressFiles.eraseAll(dir(context))"))
        // Left-over copies go only after the projects were read from their file.
        assertTrue(store.substringAfter("is PressDoc.Loaded.Read ->").substringBefore("is PressDoc.Loaded.Migrated").contains("PressFiles.orphans("))
        val press = File("$dir/PrinterLab.kt").readText()
        assertTrue(press.contains("PressStore.eraseAll(context)"))
    }

    @Test fun `a photo is downscaled to a sane size, in its shape`() {
        assertEquals(1600 to 1200, PressFiles.scaled(4000, 3000))
        assertEquals(900 to 1600, PressFiles.scaled(2250, 4000))
        assertEquals(800 to 600, PressFiles.scaled(800, 600))
        assertEquals(2, PressFiles.sampleSize(4000, 3000))
        assertEquals(1, PressFiles.sampleSize(3000, 2000))
        assertEquals(4, PressFiles.sampleSize(8000, 6000))
    }

    @Test fun `captions and places survive a save and a load`() {
        var lib = PressDoc.emptyLibrary("p1", "A", 0).insert(pic("d1"), 0).insert(pic("ph", kind = PressDoc.Kind.PHOTO), 1)
        lib = lib.editText("d1", PressDoc.Field.CAPTION, "Morning on the cliffs").place("d1", PressDoc.Place.RIGHT).place("ph", PressDoc.Place.LEFT)
        val back = PressDoc.parse(PressDoc.toJson(lib))
        val d1 = back.current.blocks[0] as Block.Picture
        assertEquals("Morning on the cliffs", d1.caption)
        assertEquals(PressDoc.Place.RIGHT, d1.place)
        assertEquals(PressDoc.Place.LEFT, (back.current.blocks[1] as Block.Picture).place)
        assertEquals(PressDoc.Kind.PHOTO, (back.current.blocks[1] as Block.Picture).kind)
        // place() leaves text blocks alone.
        assertEquals(lib, lib.place("nope", PressDoc.Place.LEFT))
    }

    @Test fun `an empty caption doesn't print, in the PDF or the Markdown`() {
        val p = PressDoc.Project("p", "P", 0, listOf(pic("a", caption = "  "), pic("b", caption = "A crab")))
        val md = PressReceipt.markdown(p, PressReceipt.Labels(), "d", "v", "Sources", "Drawing")
        assertTrue(md.contains("_Drawing_\n"))
        assertTrue(md.contains("_Drawing: A crab_"))
        val pdf = File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PdfCompiler.kt").readText()
        assertTrue(pdf.contains("b.caption.trim().takeIf { it.isNotEmpty() }"))
        val screen = File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt").readText()
        assertTrue(screen.contains("if (b.caption.isNotBlank())"))
    }

    @Test fun `the receipt counts the photos`() {
        val p = PressDoc.Project("p", "P", 0, listOf(pic("a", kind = PressDoc.Kind.PHOTO), pic("b", kind = PressDoc.Kind.PHOTO), pic("c")))
        val r = PressReceipt.build(p)
        assertEquals(2, r.photos)
        assertEquals(1, r.drawings.size)
        assertEquals(listOf("2 from this phone"), PressReceipt.rows(r, PressReceipt.Labels(), "d", "v").single { it.label == "Photos" }.lines)
    }

    @Test fun `the next text block flows beside a side picture, a heading never does`() {
        val h = Block.Heading("h", "Day 2")
        val n = Block.Note("n", "note")
        val plan = PressLayout.plan(listOf(
            pic("c"), answer("a1"),                              // centered: nothing beside
            pic("r", PressDoc.Place.RIGHT), answer("a2"), n,     // a2 beside, the note below
            pic("l", PressDoc.Place.LEFT), h, answer("a3"),      // a heading after: it starts below, and nothing flows
            pic("l2", PressDoc.Place.LEFT), n.copy(id = "n2"),   // a note flows too
            pic("r2", PressDoc.Place.RIGHT), pic("c2"),          // a picture after: nothing flows
            pic("end", PressDoc.Place.RIGHT),                    // the last block
        ))
        assertEquals(listOf(
            Item.Single(pic("c")), Item.Single(answer("a1")),
            Item.Beside(pic("r", PressDoc.Place.RIGHT), answer("a2"), left = false), Item.Single(n),
            Item.Beside(pic("l", PressDoc.Place.LEFT), null, left = true), Item.Single(h), Item.Single(answer("a3")),
            Item.Beside(pic("l2", PressDoc.Place.LEFT), n.copy(id = "n2"), left = true),
            Item.Beside(pic("r2", PressDoc.Place.RIGHT), null, left = false), Item.Single(pic("c2")),
            Item.Beside(pic("end", PressDoc.Place.RIGHT), null, left = false),
        ), plan)
        // Every block is placed once, in order.
        val ids = plan.flatMap { when (it) { is Item.Single -> listOf(it.block.id); is Item.Beside -> listOfNotNull(it.picture.id, it.text?.id) } }
        assertEquals(listOf("c", "a1", "r", "a2", "n", "l", "h", "a3", "l2", "n2", "r2", "c2", "end"), ids)
        assertFalse(plan.any { it is Item.Beside && it.text is Block.Heading })
        assertNull((plan[4] as Item.Beside).text)
    }

    @Test fun `widths and the lines beside`() {
        assertEquals(515f * 0.45f, PressLayout.sideWidth(515f), 0.001f)
        assertEquals(515f - 515f * 0.45f - 14f, PressLayout.besideWidth(515f), 0.001f)
        // Lines whose top is above the picture's bottom go beside; the rest continue at the full width.
        assertEquals(3, PressLayout.linesBeside(listOf(0f, 20f, 40f, 60f, 80f), 55f))
        assertEquals(0, PressLayout.linesBeside(listOf(60f, 80f), 55f))
        assertEquals(2, PressLayout.linesBeside(listOf(0f, 20f), 500f))
    }

    @Test fun `the PDF and the paper use the same plan and widths`() {
        val dir = "../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer"
        for (f in listOf("PdfCompiler.kt", "PressScreen.kt")) {
            val src = File("$dir/$f").readText()
            assertTrue(f, src.contains("PressLayout.plan(") && src.contains("PressLayout.sideWidth(Paper.INNER)") && src.contains("PressLayout.besideWidth(Paper.INNER)"))
        }
        assertTrue(File("$dir/PressScreen.kt").readText().contains("PressLayout.linesBeside("))
        // The PDF ends the flow before the next block (a heading included).
        assertTrue(File("$dir/PdfCompiler.kt").readText().contains("p.endBeside()"))
    }
}

class PressPhotoOpenTest {
    @Test fun `a bounds-only decode's null result is not taken as "can't open" (every photo failed on the F3)`() {
        val src = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressPictures.kt").readText()
        val copy = src.substringAfter("fun copyPhoto(").substringBefore("val sample =")
        org.junit.Assert.assertTrue(copy.contains("?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: false"))
        org.junit.Assert.assertTrue(copy.contains("if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error(\"not a picture\")"))
        org.junit.Assert.assertTrue(!copy.contains("BitmapFactory.decodeStream(it, null, bounds) } ?: error"))
        val screen = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt").readText()
        org.junit.Assert.assertTrue(screen.contains("android.util.Log.w(\"BoarLabs\", \"press: photo copy failed\", e)"))
    }
}
