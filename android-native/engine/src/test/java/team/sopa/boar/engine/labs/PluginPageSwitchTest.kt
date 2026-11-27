package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validator A-5 (adam, Nov 15: "send plugin switch fix"): every plugin page has its own on/off switch, in every profile. */
class PluginPageSwitchTest {
    private val labs = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabsScreen.kt").readText()
    private val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()

    @Test fun `the switch is on both kinds of plugin page, tied to the plugin's own setting`() {
        val scaffold = labs.substringAfter("fun LabScaffold(").substringBefore("\n}\n")
        // Golden change (adam, 16 Nov; t9 0450): a full page with a gear (Press, Designer) shows only the gear; its on/off is
        // the first row of the gear's sheet (PluginSwitchRow, same rules). Other pages: at the end of the header block.
        assertTrue(scaffold.contains("actions = { if (lab.hasSettings) PluginSettingsGear(name) { PluginSettings.open = lab.id } else PluginPageSwitch(lab) }"))
        val row = labs.substringAfter("fun PluginSwitchRow(").substringBefore("\n}\n")
        assertTrue(row.contains("PluginReach.asksBeforeOff(lab.id, v)") && row.contains("fromPage = true"))
        val press = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt").readText().substringAfter("private fun SettingsSheet(").substringBefore("\n}\n")
        val designer = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt").readText().substringAfter("private fun SettingsSheet(").substringBefore("\n}\n")
        assertTrue(press.contains("team.sopa.boar.labs.PluginSwitchRow(lab)") && designer.contains("team.sopa.boar.labs.PluginSwitchRow(ImageGenLab)"))
        assertTrue(scaffold.contains("            PluginPageSwitch(lab)\n        }\n    }) {"))
        val sw = labs.substringAfter("fun PluginPageSwitch(").substringBefore("\n}\n")
        assertTrue(sw.contains("LabPrefs.enabled(lab.id)") && sw.contains("LabPrefs.setEnabled(lab.id, v, fromPage = true)") && sw.contains("contentDescription = label"))
        // Golden change (validator Q-B1, t9 0115): tied to the profile for safety plugins only, through PluginReach
        // (PluginReachTest has the behaviour); every other plugin's switch shows in every profile.
        // Golden change (t9 1725): no profile rule; the question before a safety plugin goes off, in every profile.
        assertTrue(!sw.contains("pageSwitchShown") && sw.contains("PluginReach.asksBeforeOff(lab.id, v)"))
    }

    @Test fun `adam's plugins note, and no test-status words in Settings, EN and PT`() {
        val en = "Extra tools in the menu and under answers. Each one can be switched on or off."
        val pt = "Ferramentas extras no menu e nas respostas. Podem ser ligadas ou desligadas."
        fun v(f: String, k: String) = Regex("name=\"$k\" formatted=\"false\">([^<]*)<").find(java.io.File(f).readText())!!.groupValues[1]
        assertEquals(en, v("../app/src/labs/res/values/strings_labs.xml", "native_labs_settingsNote"))
        assertEquals(pt, v("../app/src/labs/res/values-pt/strings_labs.xml", "native_labs_settingsNote"))
        assertEquals(en, v("../app/src/main/res/values/strings_native.xml", "native_settings_experimentalNoteBelowPro"))
        assertEquals(pt, v("../app/src/main/res/values-pt/strings_native.xml", "native_settings_experimentalNoteBelowPro"))
        val words = Regex("unfinished|being tested|alpha|beta|inacabad|em teste|testad", RegexOption.IGNORE_CASE)
        for ((d, f) in listOf("values" to "strings_native.xml", "values-pt" to "strings_native.xml")) for (k in listOf("native_settings_experimentalNote", "native_settings_experimentalNoteBelowPro"))
            assertTrue("$d $k", !words.containsMatchIn(v("../app/src/main/res/$d/$f", k)))
        for (d in listOf("values", "values-pt")) assertTrue(d, !words.containsMatchIn(v("../app/src/labs/res/$d/strings_labs.xml", "native_labs_settingsNote")))
    }
}
