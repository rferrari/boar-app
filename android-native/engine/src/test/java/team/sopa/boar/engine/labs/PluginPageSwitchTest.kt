package team.sopa.boar.engine.labs

import org.junit.Assert.assertTrue
import org.junit.Test

/** Validator A-5 (adam, Nov 15: "send plugin switch fix"): every plugin page has its own on/off switch, in every profile. */
class PluginPageSwitchTest {
    private val labs = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabsScreen.kt").readText()
    private val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()

    @Test fun `the switch is on both kinds of plugin page, tied to the plugin's own setting`() {
        val scaffold = labs.substringAfter("fun LabScaffold(").substringBefore("\n}\n")
        // Full pages (Press, Designer): before the gear; the others: at the end of the header block.
        assertTrue(scaffold.contains("actions = { PluginPageSwitch(lab); if (lab.hasSettings) PluginSettingsGear(name)"))
        assertTrue(scaffold.contains("            PluginPageSwitch(lab)\n        }\n    }) {"))
        val sw = labs.substringAfter("fun PluginPageSwitch(").substringBefore("\n}\n")
        assertTrue(sw.contains("LabPrefs.enabled(lab.id)") && sw.contains("LabPrefs.setEnabled(lab.id, v, fromPage = true)") && sw.contains("contentDescription = label"))
        // Golden change (validator Q-B1, t9 0115): tied to the profile for safety plugins only, through PluginReach
        // (PluginReachTest has the behaviour); every other plugin's switch shows in every profile.
        assertTrue(sw.contains("PluginReach.pageSwitchShown(lab.id, on, LabPrefs.proProfile)") && sw.contains("PluginReach.asksBeforeOff(lab.id, v)"))
    }

    @Test fun `below Pro, Settings says where the switch is, in EN and PT`() {
        assertTrue(settings.contains("if (shown(SettingsPart.LABS_SETTINGS)) \"native.settings.experimentalNote\" else \"native.settings.experimentalNoteBelowPro\""))
        for (d in listOf("values", "values-pt")) {
            val x = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
            for (k in listOf("native_settings_experimentalNoteBelowPro", "native_labs_pageSwitch")) assertTrue("$d $k", x.contains("name=\"$k\""))
        }
    }
}
