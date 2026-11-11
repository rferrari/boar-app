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

    @Test fun `the PDF and the preview use it, the chooser thumbnail stays square`() {
        val pdf = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PdfCompiler.kt").readText()
        assertTrue(pdf.contains("PictureFit.fit(bmp.width, bmp.height, box)"))
        val press = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PrinterLab.kt").readText()
        assertTrue(press.contains("Thumb(f, whole = true)") && press.contains("{ Thumb(drawing) }"))
    }
}
