package team.sopa.boar.engine.settings

import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.1.0 (BOAR-81): Settings is one simple page for everyone; the builders' rows are in Evaluation › Device setup. */
class SimpleSettingsTest {
    private val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
    private val page = settings.substringAfter("fun SettingsScreen(").substringBefore("\n}\n")
    private fun strings(d: String) = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()

    @Test fun `no profiles, no answer styles, no tweaks, no telemetry, no Experimental`() {
        for (gone in listOf("UserProfile", "SettingsPart", "ProfilePills", "ProfileUpdateToast", "ToneSection", "TweaksSection", "TelemetrySection",
                "ExperimentalSection", "MovedToExperimental", "userProfile", "native.tweaks.title", "modelSetupScreen.sections.tone"))
            assertTrue(gone, !settings.contains(gone))
        assertTrue(page.contains("team.sopa.boar.ui.PageHeader(t(\"modelSetupScreen.header.title\"), onBack)"))
    }

    @Test fun `seven sections in order - Display, Language and voice, Chats, Security, Plugins, Recovery, About`() {
        val order = listOf("t(\"modelSetupScreen.sections.displayTheme\")", "t(\"native.settings.languageVoice\")", "t(\"native.settings.chats\")",
            "SecuritySection(services, onPin)", "PluginsSection(onOpen)", "RecoverySection(services, prefs, onSetup)", "onOpen(\"about\")")
        val at = order.map { page.indexOf(it) }
        assertTrue("$at", at.all { it >= 0 } && at == at.sorted())
    }

    @Test fun `Display has the Boarverse switch, Chats keeps the chats' rows (turns before summarising moved)`() {
        val display = page.substringAfter("t(\"modelSetupScreen.sections.displayTheme\")").substringBefore("t(\"native.settings.languageVoice\")")
        assertTrue(display.contains("t(\"native.vault.experimental\")") && display.contains("TruffleVault.SWITCH_SHOWN"))
        val chats = page.substringAfter("t(\"native.settings.chats\")").substringBefore("SecuritySection(")
        for (k in listOf("memorySettings.autoSummarizeLabel", "memorySettings.autoTitlesLabel", "memorySettings.maxSavedSessions", "ClearChats(services)")) assertTrue(k, chats.contains(k))
        assertTrue(!chats.contains("turnsBeforeSummarizing"))
    }

    @Test fun `Plugins - the line, the Boar Labs switch with the waiting animation, the list`() {
        val plugins = settings.substringAfter("private fun PluginsSection(").substringBefore("\n}\n")
        // The Boar Labs switch's own note is adam's line.
        assertTrue(plugins.contains("team.sopa.boar.labs.LabHooks.SettingsRow()"))
        val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()
        assertTrue(hooks.contains("SwitchRow(team.sopa.boar.ui.t(\"native.labs.settingsTitle\"), team.sopa.boar.ui.t(\"native.labs.settingsNote\"), LabPrefs.labsOn)"))
        assertTrue(plugins.contains("LinkButton(t(\"native.labs.openList\")) { onOpen(\"labs\") }"))
        // Only in a build with Boar Labs.
        assertTrue(page.contains("if (team.sopa.boar.BuildConfig.EXPERIMENTAL_FEATURES) PluginsSection(onOpen)"))
        assertTrue(java.io.File("../app/src/labs/res/values/strings_labs.xml").readText().contains(">Extra tools in the menu and under answers. Each one can be switched on or off.<"))
    }

    @Test fun `a chat setup that isn't Auto shows one line, with Reset to Auto`() {
        val custom = settings.substringAfter("private fun CustomSetupLine(").substringBefore("\n}\n")
        assertTrue(custom.contains("if (tweaks.isAuto) return") && custom.contains("t(\"native.settings.customSetup\")"))
        assertTrue(custom.contains("services.settings.engineTweaks = EngineTweaks()") && custom.contains("if (services.stopAnswersAndWait()) services.host.unload()"))
        assertTrue(page.contains("CustomSetupLine(services)"))
        for (d in listOf("values", "values-pt")) for (k in listOf("native_settings_customSetup", "native_settings_customSetupReset", "native_settings_chats", "native_settings_plugins"))
            assertTrue("$d $k", strings(d).contains("name=\"$k\""))
        assertTrue(strings("values").contains(">Chat setup: custom, from Evaluation<"))
    }
}
