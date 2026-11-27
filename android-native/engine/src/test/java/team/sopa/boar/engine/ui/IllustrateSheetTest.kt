package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** t9, Nov 10: Illustrate asks the style (only the style since BOAR-88), one tap draws with the defaults, the pick can be kept and reset. */
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

    @Test fun `the sheet has no shape or quality, it draws at 512 (BOAR-88)`() {
        val body = sheet.substringBefore("private fun SheetButton(")
        assertTrue(!body.contains("Shape.entries") && !body.contains("Quality") && !body.contains("shapeLabel") && !body.contains(".label"))
        assertTrue(body.contains("t(\"native.labs.image.drawPicked\", \"style\" to styleName(style.key))"))
        assertTrue(body.contains("val size = ImageLab.defaultCanvas()"))
    }

    @Test fun `tapping the selected style again leaves no style, as on the Designer page (BOAR-88)`() {
        val body = sheet.substringBefore("private fun SheetButton(")
        // The Designer page's own tiles (adam, 27 Nov), with its hint on how, and what no style means.
        assertTrue(body.contains("StyleTiles(style) { style = it }"))
        // The style drawn with is kept, None too: the next one-tap Illustrate draws the words alone.
        assertTrue(lab.contains("Style.entries.firstOrNull { it.key == k }") && lab.contains("NONE(\"none\", \"{s}\")"))
    }

    @Test fun `strings in English and Portuguese`() {
        for (d in listOf("values", "values-pt")) {
            val s = java.io.File("../app/src/labs/plugins/designer/res/$d").listFiles()!!.joinToString("") { it.readText() }
            for (k in listOf("sheetTitle", "styleLabel", "drawPicked"))
                assertTrue("$d $k", s.contains("name=\"native_labs_image_$k\""))
        }
    }
}
