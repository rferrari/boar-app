package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** A plugin can be a canvas (adam, Nov 13: Designer and Press): the whole height under the header, no page scroll. */
class PluginFullPageTest {
    private val screen = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabsScreen.kt").readText()
    private val plugin = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabPlugin.kt").readText()

    @Test fun `off by default, other pages unchanged`() {
        assertTrue(plugin.contains("val fullPage: Boolean get() = false"))
        assertTrue(screen.contains("else Column(Modifier.weight(1f).verticalScroll(scroll ?: rememberScrollState())"))
    }

    @Test fun `a full page has the header, the off alert, then the plugin's own space, and About is reusable`() {
        val full = screen.substringAfter("if (lab.fullPage) {").substringBefore("return\n")
        assertTrue(full.contains("full = true") && full.contains("OffAlert(") && full.contains("Box(Modifier.fillMaxWidth().weight(1f)) { lab.Content() }"))
        assertTrue(!full.contains("verticalScroll") && !full.contains("AboutPlugin("))
        assertTrue(screen.contains("fun AboutPluginFold(lab: LabPlugin)"))
    }
}
