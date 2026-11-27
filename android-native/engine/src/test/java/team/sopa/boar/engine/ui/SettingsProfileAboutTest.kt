package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 0620 (adam, Nov 12): profile pills, the About row, About's text. Read from the source. */
class SettingsProfileAboutTest {
    private val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
    private fun res(d: String, f: String) = java.io.File("../app/src/main/res/$d/$f").readText()

    @Test fun `About in Settings is a row with the info icon and the chevron`() {
        assertTrue(settings.contains("PackIcon(team.sopa.boar.R.drawable.ic_info") && settings.contains("PackIcon(team.sopa.boar.R.drawable.ic_chevron_right"))
    }

    @Test fun `About's text points to where Evaluation really is, and the old About strings are gone`() {
        for (d in listOf("values", "values-pt")) {
            val n = res(d, "strings_native.xml")
            assertTrue(d, !n.contains("Menu → Evaluation") && !n.contains("Menu → Avaliação"))
            assertTrue(d, !res(d, "strings.xml").contains("aboutScreen_airGappedBody"))
        }
    }
}
