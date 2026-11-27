package team.sopa.boar.engine.labs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * adam, 27 Nov: the last style used is always the default, as on the Designer page; the answer's ✎ sheet has only the
 * styles, as the same tiles as the Designer page (a little drawing for each), and Draw. No Remember, no Reset.
 */
class DesignerLastStyleTest {
    private val screen = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt").readText()
    private fun section(marker: String): String { assertTrue(marker, screen.contains(marker)); return screen.substringAfter(marker).substringBefore("\n}\n") }

    @Test fun `the answer's sheet is the Designer page's style tiles and Draw, which keeps its style as the default`() {
        val sheet = section("private fun IllustrateSheet(")
        assertTrue(sheet.contains("StyleTiles(style) { style = it }"))
        assertFalse(sheet.contains("ChipRow(") || sheet.contains("Checkbox(") || sheet.contains("native.labs.image.remember") || sheet.contains("native.labs.image.reset"))
        assertTrue(sheet.contains("ImageLab.setDefaults(style)\n                onClose(); Illustrator.start(ctx, a, notReady, style, size)"))
        // The Designer page's Style sheet uses the same tiles.
        assertTrue(section("private fun StyleSection(").contains("StyleTiles(state.style) { vm.setStyle(it) }"))
        val tiles = section("private fun StyleTiles(")
        assertTrue(tiles.contains("StyleSwatch(s.key") && tiles.contains("onPick(if (on) ImageLab.Style.NONE else s)"))
        for (d in listOf("values", "values-pt")) {
            val x = java.io.File("../app/src/labs/plugins/designer/res/$d/strings_lab_image.xml").readText()
            assertFalse(d, x.contains("native_labs_image_remember\"") || x.contains("native_labs_image_reset\""))
        }
    }
}
