package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** t9, Nov 10: Discover's header differed; the pages reached with Back share one header. */
class PageHeaderTest {
    private val ui = "../app/src/main/java/team/sopa/boar/ui"

    @Test fun `the back pages use the shared header, none draws its own`() {
        for (f in listOf("models/ModelsScreen.kt", "models/BrowseScreen.kt", "documents/DocumentsScreen.kt", "settings/SettingsScreen.kt",
            "appearance/VaultScreen.kt", "chat/SourceReader.kt")) {
            val s = java.io.File("$ui/$f").readText()
            assertTrue(f, s.contains("team.sopa.boar.ui.PageHeader("))
            assertTrue(f, !s.contains("Text(\"‹ \" + t(\"common.back\")"))
        }
    }

    @Test fun `one height with or without a subtitle`() {
        val h = java.io.File("$ui/PageHeader.kt").readText()
        assertTrue(h.contains("heightIn(min = 72.dp)"))
    }

    @Test fun `the Plugins pages, About and Evaluation use it too, a plugin's Back names its target (t9, Nov 12)`() {
        val labs = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabsScreen.kt").readText()
        assertTrue(labs.contains("team.sopa.boar.ui.PageHeader(title, onClose, backTo = backTo)") && !labs.contains("DoneButton("))
        assertTrue(labs.contains("backTo = if (LabPrefs.proProfile) t(\"native.labs.title\") else t(\"native.nav.chat\")"))
        assertTrue(java.io.File("$ui/about/AboutScreen.kt").readText().contains("team.sopa.boar.ui.PageHeader(t(\"aboutScreen.title\"), onClose)"))
        assertTrue(java.io.File("$ui/eval/EvaluationScreen.kt").readText().contains("team.sopa.boar.ui.PageHeader(t(\"evaluation.title\")"))
    }
}
