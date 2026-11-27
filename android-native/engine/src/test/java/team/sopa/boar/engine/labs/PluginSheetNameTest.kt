package team.sopa.boar.engine.labs

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * adam, 27 Nov: in the chat's plugins sheet, Press's row read "5 in <project>…" with its own name squeezed out; the
 * plugin's name must always show. The count on the right gets at most a third of the row, cut with an ellipsis.
 */
class PluginSheetNameTest {
    private val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()

    @Test fun `the name keeps its room, the count is capped and cut`() {
        val marker = "private fun SheetRow("
        assertTrue(hooks.contains(marker))
        val row = hooks.substringAfter(marker).substringBefore("\n    }\n")
        assertTrue(row.contains("trailing?.let { t -> androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(0.5f, fill = false)) { t() } }"))
        assertTrue(row.contains("androidx.compose.ui.Modifier.weight(1f)") && !row.contains("trailing?.invoke()"))
        val sheet = hooks.substringAfter("lab.headerCountLabel(n)").substringBefore("go(\"lab:")
        assertTrue(sheet.contains("maxLines = 1") && sheet.contains("TextOverflow.Ellipsis"))
    }
}
