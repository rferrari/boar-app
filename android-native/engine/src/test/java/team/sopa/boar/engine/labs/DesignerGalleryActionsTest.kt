package team.sopa.boar.engine.labs

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * adam, 27 Nov: "on media gallery when show all, i miss the info button, so user can ask draw again or variations. we
 * also could add a delete image button on image toolbar".
 */
class DesignerGalleryActionsTest {
    private val screen = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt").readText()
    private fun strings(d: String) = java.io.File("../app/src/labs/plugins/designer/res/$d/strings_lab_image.xml").readText()

    @Test fun `the drawing viewed in All drawings has the i button - it opens on the canvas with its sheet`() {
        val marker = "private fun MediaManager("
        assertTrue(screen.contains(marker))
        val manager = screen.substringAfter(marker).substringBefore("\n}\n")
        assertTrue(manager.contains("onInfo: ((File) -> Unit)?") && manager.contains("onInfo?.let { open -> RoundOverlay(team.sopa.boar.R.drawable.ic_info, t(\"native.labs.image.infoButton\")) { open(f) } }"))
        assertTrue(screen.contains("MediaManager(state.gallery, vm, onInfo = if (state.phase == null) { f -> vm.show(f); infoNext = true; managing = false } else null) { managing = false }"))
        val canvas = screen.substringAfter("fun DesignerCanvasScreen(").substringBefore("\n}\n")
        assertTrue(canvas.contains("androidx.compose.runtime.LaunchedEffect(openInfo) { if (openInfo) { info = true; onInfoOpened() } }"))
    }

    @Test fun `the picture's own buttons include delete, which asks first`() {
        val marker = "private fun CanvasArt("
        assertTrue(screen.contains(marker))
        val art = screen.substringAfter(marker).substringBefore("\n}\n")
        assertTrue(art.contains("RoundOverlay(team.sopa.boar.R.drawable.ic_delete, t(\"native.labs.image.delete\"), onDelete)"))
        val canvas = screen.substringAfter("fun DesignerCanvasScreen(").substringBefore("\n}\n")
        assertTrue(canvas.contains("t(\"native.labs.image.deleteOneConfirm\")") && canvas.contains("vm.delete(f)"))
        for (d in listOf("values", "values-pt")) assertTrue(d, strings(d).contains("name=\"native_labs_image_deleteOneConfirm\""))
    }

    @Test fun `while Designer draws, All drawings has no i button - showing a drawing then would drop the variations made so far (CodeRabbit)`() {
        assertTrue(screen.contains("onInfo = if (state.phase == null) {"))
        assertTrue(!screen.contains("MediaManager(state.gallery, vm, onInfo = { f -> vm.show(f)"))
    }
}
