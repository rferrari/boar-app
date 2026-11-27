package team.sopa.boar.engine.labs

import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 0740 (adam, 27 Nov: "lets remove the printing animation"): Save as opens as soon as the PDF is built. */
class PressNoPrintAnimationTest {
    private val dir = "../app/src/labs/plugins/printer"
    private val screen = java.io.File("$dir/java/team/sopa/boar/labs/printer/PressScreen.kt").readText()

    @Test fun `no animation, no modal, no minimum wait`() {
        for (gone in listOf("PrintingAnimation", "PRINT_MS", "if (compiling) androidx.compose.ui.window.Dialog(")) assertTrue(gone, !screen.contains(gone))
        val save = screen.substringAfter("fun savePdf() {").substringBefore("\n    }\n")
        assertTrue(save.isNotEmpty() && !save.contains("delay("))
        // Still one build at a time, and a failure still says so.
        assertTrue(save.contains("if (compiling || project.blocks.isEmpty()) return") && save.contains(".onFailure { Toasts.show("))
        assertTrue(screen.contains("canSave = project.blocks.isNotEmpty() && !compiling"))
    }

    @Test fun `its string is gone, English and Portuguese`() {
        for (d in listOf("values", "values-pt")) assertTrue(d, !java.io.File("$dir/res/$d/strings_lab_printer.xml").readText().contains("name=\"native_labs_printer_compiling\""))
    }
}
