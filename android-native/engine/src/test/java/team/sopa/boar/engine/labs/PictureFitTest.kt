package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureFitTest {
    @Test fun `square, portrait and banner keep their shape in the box`() {
        assertEquals(300f to 300f, PictureFit.fit(384, 384, 300f))
        assertEquals(225f to 300f, PictureFit.fit(384, 512, 300f))
        assertEquals(200f to 100f, PictureFit.fit(640, 320, 200f))
    }

    @Test fun `a broken size falls back to the square box`() {
        assertEquals(200f to 200f, PictureFit.fit(0, 512, 200f))
    }

    @Test fun `on the paper a picture takes the column's width in its shape, no taller than the cap`() {
        assertEquals(515f to 386.25f, PictureFit.within(640, 480, 515f, 400f))
        // A tall one is held to the cap, and narrower.
        assertEquals(300f to 400f, PictureFit.within(384, 512, 515f, 400f))
        assertEquals(515f to 257.5f, PictureFit.within(640, 320, 515f, 400f))
        assertEquals(400f to 400f, PictureFit.within(0, 0, 515f, 400f))
    }

    @Test fun `the PDF and the paper on screen use the same fit`() {
        val dir = "../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer"
        val pdf = java.io.File("$dir/PdfCompiler.kt").readText()
        assertTrue(pdf.contains("PictureFit.within(size.first, size.second, Paper.INNER, Paper.PICTURE_MAX_H)"))
        val screen = java.io.File("$dir/PressScreen.kt").readText()
        // A centered picture gets the column, a side one its share (phase 2), through the same fit.
        assertTrue(screen.contains("PictureFit.within(pw, ph, maxW, Paper.PICTURE_MAX_H)") && screen.contains("PictureContent(b, project.id, s, Paper.INNER,"))
        assertTrue(pdf.contains("PictureFit.within(size.first, size.second, box, Paper.PICTURE_MAX_H)"))
    }
}
