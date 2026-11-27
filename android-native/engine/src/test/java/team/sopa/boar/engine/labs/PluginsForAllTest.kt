package team.sopa.boar.engine.labs

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, 23 Nov: "plugins for all before 2.0" (t9 1725): the Plugins list and its switches in every profile. */
class PluginsForAllTest {
    private val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()
    private val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()

    @Test fun `the menu entry, the list's route and its way back don't depend on the profile`() {
        assertTrue(hooks.contains("fun drawerItems(): List<Pair<String, String>> = if (LabPrefs.labsOn) listOf(\"labs\" to \"native.labs.drawer\") else emptyList()"))
        assertTrue(hooks.contains("fun handles(route: String) = LabPrefs.labsOn && (route == \"labs\" || route == TILES || route.startsWith(\"lab:\"))"))
        assertTrue(!hooks.contains("LabPrefs.proProfile"))
        // The header sheet's "Manage plugins" and the "is off" line lead to the list in every profile.
        val sheet = hooks.substringAfter("fun HeaderBadges(").substringBefore("\n    }\n")
        assertTrue(sheet.contains("native.labs.manage") && !sheet.contains("proProfile"))
        assertTrue(java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText().contains("onReadPlugin = { _ -> onOpenRoute(\"labs\") }"))
    }

    @Test fun `Settings › Plugins has Boar Labs' own settings and opens the list, for everyone (2_1_0)`() {
        val plugins = settings.substringAfter("private fun PluginsSection(").substringBefore("\n}\n")
        assertTrue(plugins.contains("team.sopa.boar.labs.LabHooks.SettingsRow()"))
        assertTrue(plugins.contains("        if (team.sopa.boar.labs.LabPrefs.labsOn) LinkButton(t(\"native.labs.openList\")) { onOpen(\"labs\") }"))
    }

    @Test fun `turning Cohazard off asks first, whatever the profile`() {
        // No profile in the rule: the list's switch and the page's both ask.
        assertTrue(PluginReach.asksBeforeOff("cohazard", turningOn = false) && !PluginReach.asksBeforeOff("cohazard", turningOn = true))
        val labs = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabsScreen.kt").readText()
        assertTrue(Regex("PluginReach.asksBeforeOff\\(lab.id, v\\)").findAll(labs).count() >= 3)
    }

    @Test fun `the guide says plugins are for everyone, EN and PT`() {
        val en = java.io.File("../app/src/main/assets/boar-guide/en.md").readText()
        val pt = java.io.File("../app/src/main/assets/boar-guide/pt.md").readText()
        // No profiles since 2.1.0: the guide says Plugins is in the menu, for everyone.
        assertTrue(en.contains("Open Plugins in the menu") && !en.contains("profile") && !en.contains("can be switched off only in Pro"))
        assertTrue(pt.contains("Abra Plugins no menu") && !pt.contains("perfil") && !pt.contains("só pode ser desligado no Pro"))
    }
}
