package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** t9, Nov 10: one plugins icon in the chat header replaces the labs' own badges. */
class PluginsIconTest {
    private val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()
    private val icon = hooks.substringAfter("fun HeaderBadges(").substringBefore("\n    }\n")

    @Test fun `no lab on, no icon`() {
        assertTrue(icon.contains("val labs = LabRegistry.active\n        if (labs.isEmpty()) return"))
    }

    @Test fun `the list holds the last five used among the labs that are on, each opening its page`() {
        assertTrue(icon.contains("RecentPlugins.pick(labs.map { it.id }, recent)") && icon.contains("shown.forEach { lab ->"))
        assertTrue(icon.contains("go(\"lab:\${lab.id}\")"))
    }

    @Test fun `managing labs stays in Pro`() {
        assertTrue(icon.contains("if (LabPrefs.proProfile) androidx.compose.material3.Text(team.sopa.boar.ui.t(\"native.labs.manage\")"))
    }

    @Test fun `no lab draws its own header badge any more, Press counts on the icon`() {
        val plugin = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabPlugin.kt").readText()
        assertTrue(!plugin.contains("fun HeaderBadge("))
        val press = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PrinterLab.kt").readText()
        assertTrue(press.contains("override fun headerCount(): Int"))
    }

    @Test fun `strings in English and Portuguese`() {
        for (d in listOf("values", "values-pt")) {
            val s = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
            for (k in listOf("native_labs_headerIcon", "native_labs_headerSheet", "native_labs_manage", "native_labs_printer_inBasketCount"))
                assertTrue("$d $k", s.contains("name=\"$k\""))
        }
    }
}
