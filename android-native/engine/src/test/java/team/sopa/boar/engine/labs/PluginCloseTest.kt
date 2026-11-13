package team.sopa.boar.engine.labs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** t9 0335 and 0342 (adam's hand test of all43): ✕ on every plugin page, and Press's own menu icon. */
class PluginCloseTest {
    private fun read(p: String) = File("../app/src/$p").readText()

    @Test fun `a plugin page's close goes to the chat, its back to the previous page`() {
        val hooks = read("labs/java/team/sopa/boar/labs/LabHooks.kt")
        assertTrue(hooks.contains("LabScaffold(lab, onExit = { go(\"chat\") }) { go(backFrom(route)) }"))
        // Back from a plugin opened via the Plugins list (Pro) lands in the list; below Pro in the chat.
        val back = hooks.substringAfter("fun backFrom(route: String): String = when {").substringBefore("}")
        assertTrue(back.contains("LabProfileCheck".let { "LabPrefs.proProfile -> \"labs\"" }) && back.contains("else -> \"chat\""))
        val labs = read("labs/java/team/sopa/boar/labs/LabsScreen.kt")
        // Both kinds of plugin page (full-page Press and Designer, and the others) pass it to the shared header.
        assertTrue(Regex("onExit = onExit").findAll(labs).count() == 2)
        assertTrue(labs.contains("team.sopa.boar.ui.PageHeader(title, onClose, subtitle = subtitle, backTo = backTo, onClose = onExit)"))
    }

    @Test fun `the close button is off unless a page asks for it, and other pages don't`() {
        val header = read("main/java/team/sopa/boar/ui/PageHeader.kt")
        assertTrue(header.contains("onClose: (() -> Unit)? = null") && header.contains("if (onClose != null)"))
        assertTrue(header.contains("Modifier.size(HeaderMetrics.ROW)") && header.contains("t(\"common.close\")"))
        for (p in listOf("main/java/team/sopa/boar/ui/settings/SettingsScreen.kt", "main/java/team/sopa/boar/ui/models/ModelsScreen.kt", "main/java/team/sopa/boar/ui/about/AboutScreen.kt"))
            assertFalse(p, Regex("PageHeader\\([^\\n]*onClose =").containsMatchIn(read(p)))
    }

    @Test fun `Press's menu button is a page with lines, not the paragraph's bare lines`() {
        val press = read("labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt")
        assertTrue(press.contains("PackIcon(R.drawable.lab_printer_ic_menu_doc, 22.dp, tint = c.textHeading, description = t(\"native.labs.printer.menu\"))"))
        val icon = read("labs/plugins/printer/res/drawable/lab_printer_ic_menu_doc.xml")
        val para = read("labs/plugins/printer/res/drawable/lab_printer_ic_paragraph.xml")
        assertTrue(icon.contains("M14,3v4h4") && icon != para)
    }
}

/** t9 0345 (adam): no "N new answers · Show" banner in Press; old projects.json with seenAt still read. */
class PressNoInboxTest {
    @Test fun `the banner and its strings are gone, seenAt still orders recent projects and reads back`() {
        val s = File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt").readText()
        assertFalse(s.contains("InboxBanner") || s.contains("printer.inbox") || s.contains("\"native.labs.printer.show\""))
        for (d in listOf("values", "values-pt")) {
            val x = File("../app/src/labs/plugins/printer/res/$d/strings_lab_printer.xml").readText()
            assertFalse(d, x.contains("native_labs_printer_inbox_") || x.contains("name=\"native_labs_printer_show\""))
        }
        val j = """{"version":1,"open":"a","projects":[{"id":"a","name":"A","createdAt":1,"seenAt":50,"blocks":[]},{"id":"b","name":"B","createdAt":2,"seenAt":90,"blocks":[]},{"id":"c","name":"C","createdAt":3,"blocks":[]}]}"""
        val lib = PressDoc.parse(j)
        assertTrue(lib.projects.map { it.seenAt } == listOf(50L, 90L, 0L))
        assertTrue(lib.shortList().map { it.id } == listOf("a", "b", "c"))
        assertTrue(PressDoc.toJson(lib).contains("\"seenAt\":90"))
    }
}
