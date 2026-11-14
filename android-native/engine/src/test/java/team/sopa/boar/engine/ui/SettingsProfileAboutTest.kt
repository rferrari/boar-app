package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 0620 (adam, Nov 12): profile pills, the About row, About's text. Read from the source. */
class SettingsProfileAboutTest {
    private val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
    private fun res(d: String, f: String) = java.io.File("../app/src/main/res/$d/$f").readText()

    @Test fun `the profile is three small pills in the header, and what a pick gives and what pauses going down are toasts`() {
        // adam, Nov 13: "move the profile pills to settings header. smaller pills. the notes, use as toasts".
        assertTrue(settings.contains("actions = if (shown(SettingsPart.PROFILE)) ({ ProfilePills(profile, pick) }) else null") && !settings.contains("private fun ProfileSection("))
        val pills = settings.substringAfter("private fun ProfilePills(").substringBefore("\n}\n")
        assertTrue(pills.contains("style = Boar.type.caption") && !pills.contains("Text(t(\"native.settings.profile.\${profile.key}Short\")") && !pills.contains("Note("))
        assertTrue(settings.contains("Toasts.Toast(shorts.getValue(p) + if (down) \" \$downNote\" else \"\"") && settings.contains("ProfileUpdateToast(services, profile)"))
        val header = java.io.File("../app/src/main/java/team/sopa/boar/ui/PageHeader.kt").readText()
        assertTrue(header.contains("actions: (@Composable () -> Unit)? = null") && header.indexOf("actions?.invoke()") in 0 until header.indexOf("if (onClose != null)"))
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
