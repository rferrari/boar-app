package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 0620 (adam, Nov 12): profile pills, the About row, About's text. Read from the source. */
class SettingsProfileAboutTest {
    private val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
    private fun res(d: String, f: String) = java.io.File("../app/src/main/res/$d/$f").readText()

    @Test fun `the profile is three pills with a short caption, and going down says what pauses`() {
        assertTrue(settings.contains("ProfilePills(services, profile)") && !settings.contains("private fun ProfileSection("))
        assertTrue(settings.contains("t(\"native.settings.profile.\${profile.key}Short\")") && settings.contains("if (wentDown) Text(t(\"native.settings.profile.downNote\")"))
        for (d in listOf("values", "values-pt")) for (k in listOf("simpleShort", "explorerShort", "proShort", "downNote"))
            assertTrue("$d $k", res(d, "strings_native.xml").contains("name=\"native_settings_profile_$k\""))
    }

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
