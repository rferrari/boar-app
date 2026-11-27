package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.labs.PressDoc.Block

/** adam, 27 Nov: "a image resize small medium big when select image can enhance page content. its one size only". */
class PressPictureSizeTest {
    private val app = "../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer"
    private fun src(f: String) = java.io.File("$app/$f").readText()
    private val pic = Block.Picture("p1", PressDoc.Kind.DRAWING, "/x.png", width = 512, height = 512)
    private fun lib(b: Block) = PressDoc.Library("p", listOf(PressDoc.Project("p", "P", 1, listOf(b))))

    @Test fun `three sizes, Large as before by default, kept in the project`() {
        assertEquals(listOf(PressDoc.PicSize.SMALL, PressDoc.PicSize.MEDIUM, PressDoc.PicSize.LARGE), PressDoc.PicSize.entries)
        assertEquals(PressDoc.PicSize.LARGE, pic.size)
        val small = lib(pic).resize("p1", PressDoc.PicSize.SMALL)
        val back = PressDoc.parse(PressDoc.toJson(small)).current.blocks.single() as Block.Picture
        assertEquals(PressDoc.PicSize.SMALL, back.size)
        // A project saved before sizes reads as Large.
        assertEquals(PressDoc.PicSize.LARGE, PressDoc.PicSize.of(null)); assertEquals(PressDoc.PicSize.MEDIUM, PressDoc.PicSize.of("medium"))
    }

    @Test fun `centred a share of the width, beside the text a narrower side, Large as today`() {
        assertEquals(1f, PressDoc.PicSize.LARGE.center); assertEquals(PressLayout.SIDE, PressDoc.PicSize.LARGE.side)
        assertTrue(PressDoc.PicSize.SMALL.center < PressDoc.PicSize.MEDIUM.center && PressDoc.PicSize.MEDIUM.center < 1f)
        assertTrue(PressDoc.PicSize.SMALL.side < PressDoc.PicSize.MEDIUM.side && PressDoc.PicSize.MEDIUM.side < PressLayout.SIDE)
        assertEquals(500f * PressDoc.PicSize.SMALL.side, PressLayout.sideWidth(500f, PressDoc.PicSize.SMALL))
        assertEquals(500f - PressLayout.sideWidth(500f, PressDoc.PicSize.MEDIUM) - PressLayout.GUTTER, PressLayout.besideWidth(500f, PressDoc.PicSize.MEDIUM))
    }

    @Test fun `the PDF, the paper on screen and the picture's tools use it`() {
        val pdf = src("PdfCompiler.kt")
        assertTrue(pdf.contains("p.picture(f, caption(b), b.size)") && pdf.contains("p.sidePicture(f, item.left, caption(item.picture), item.picture.size)"))
        assertTrue(pdf.contains("PressLayout.besideWidth(Paper.INNER, f.size)"))
        val screen = src("PressScreen.kt")
        assertTrue(screen.contains("PictureContent(b, project.id, s, Paper.INNER * b.size.center,") && screen.contains("PressLayout.sideWidth(Paper.INNER, pic.size)"))
        val bar = screen.substringAfter("private fun ActionBar(").substringBefore("\n}\n")
        assertTrue(bar.contains("onSize(nextSize)") && bar.contains("t(\"native.labs.printer.size_\${b.size.key}\")"))
        for (d in listOf("values", "values-pt")) {
            val x = java.io.File("../app/src/labs/plugins/printer/res/$d/strings_lab_printer.xml").readText()
            for (k in listOf("size_small", "size_medium", "size_large", "sizeNext")) assertTrue("$d $k", x.contains("name=\"native_labs_printer_$k\""))
        }
    }
}
