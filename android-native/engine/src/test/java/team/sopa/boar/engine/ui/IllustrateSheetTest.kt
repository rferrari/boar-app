package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** t9, Nov 10: Illustrate asks style and size (Pro), one tap draws with the defaults, the pick can be kept and reset. */
class IllustrateSheetTest {
    private val screen = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt").readText()
    private val lab = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLab.kt").readText()
    private val sheet = screen.substringAfter("private fun IllustrateSheet(")

    @Test fun `the row draws with the defaults, the edit button opens the sheet, in every profile`() {
        assertTrue(screen.contains("onEdit = { Illustrator.asking = a.id }) { Illustrator.start(ctx, a, notReady) }"))
        assertTrue(!screen.contains("LabPrefs.proProfile) Illustrator.asking"))
        val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()
        assertTrue(hooks.contains("if (edit != null) androidx.compose.material3.Text(\"✎\""))
    }

    @Test fun `the edit sheet has one Draw button, with the style and size picked there`() {
        assertTrue(!sheet.contains("Illustrator.start(ctx, a, notReady, defStyle, defSize)"))
        assertTrue(Regex("SheetButton\\(").findAll(sheet.substringBefore("private fun SheetButton(")).count() == 1)
        assertTrue(sheet.contains("Illustrator.start(ctx, a, notReady, style, size)"))
    }

    @Test fun `the pick is kept only when asked, and reset goes back to Engraving, Square, Fast`() {
        // Style, shape and speed together, as "★ Default" on the canvas (Nov 13).
        assertTrue(sheet.contains("if (keep) ImageLab.setDefaults(style, size)"))
        assertTrue(lab.contains("fun resetDefaults() = setDefaults(Style.ENGRAVING, Canvas.of(DesignerCanvas.Shape.SQUARE, DesignerCanvas.Tier.FAST))"))
    }

    @Test fun `the sheet says shape and speed in words, like the canvas, never pixels`() {
        assertTrue(sheet.contains("DesignerCanvas.Shape.entries.map { it to shapeName(it) }") && sheet.contains("DesignerCanvas.Tier.entries.map { it to tierLabel("))
        assertTrue(sheet.contains("\"size\" to shapeTier(size)") && !sheet.contains(".label"))
    }

    @Test fun `strings in English and Portuguese`() {
        for (d in listOf("values", "values-pt")) {
            val s = java.io.File("../app/src/labs/plugins/designer/res/$d").listFiles()!!.joinToString("") { it.readText() }
            for (k in listOf("sheetTitle", "styleLabel", "shapeLabel", "speedLabel", "remember", "reset", "drawPicked"))
                assertTrue("$d $k", s.contains("name=\"native_labs_image_$k\""))
        }
    }
}
