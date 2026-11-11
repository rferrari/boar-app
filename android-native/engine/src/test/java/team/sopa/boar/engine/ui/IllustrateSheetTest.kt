package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** t9, Nov 10: Illustrate asks style and size (Pro), one tap draws with the defaults, the pick can be kept and reset. */
class IllustrateSheetTest {
    private val screen = java.io.File("../app/src/labs/plugins/image/java/team/sopa/boar/labs/image/ImageLabScreen.kt").readText()
    private val lab = java.io.File("../app/src/labs/plugins/image/java/team/sopa/boar/labs/image/ImageLab.kt").readText()
    private val sheet = screen.substringAfter("private fun IllustrateSheet(")

    @Test fun `Pro gets the sheet, below Pro draws at once`() {
        assertTrue(screen.contains("if (team.sopa.boar.labs.LabPrefs.proProfile) Illustrator.asking = a.id else Illustrator.start(ctx, a, notReady)"))
    }

    @Test fun `the first button draws with the defaults`() {
        assertTrue(sheet.indexOf("Illustrator.start(ctx, a, notReady, defStyle, defSize)") in 0 until sheet.indexOf("ChipRow("))
    }

    @Test fun `the pick is kept only when asked, and reset goes back to Engraving at the first size`() {
        assertTrue(sheet.contains("if (keep) { ImageLab.setDefaultStyle(style); ImageLab.setDefaultCanvas(size) }"))
        assertTrue(lab.contains("fun resetDefaults() { setDefaultStyle(Style.ENGRAVING); setDefaultCanvas(CANVASES.first()) }"))
    }

    @Test fun `strings in English and Portuguese`() {
        for (d in listOf("values", "values-pt")) {
            val s = java.io.File("../app/src/labs/plugins/image/res/$d").listFiles()!!.joinToString("") { it.readText() }
            for (k in listOf("sheetTitle", "drawDefault", "orPick", "styleLabel", "sizeLabel", "remember", "reset", "drawPicked"))
                assertTrue("$d $k", s.contains("name=\"native_labs_image_$k\""))
        }
    }
}
