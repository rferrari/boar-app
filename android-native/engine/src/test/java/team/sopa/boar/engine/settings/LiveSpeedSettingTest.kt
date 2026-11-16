package team.sopa.boar.engine.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, 16 Nov: the live tok/s while BOAR answers gets its own on/off in Display & theme, off by default. */
class LiveSpeedSettingTest {
    @Test fun `off by default, kept when set, cleared by Reset settings, in every profile`() {
        val f = kotlin.io.path.createTempFile("settings", ".json").toFile().also { it.delete() }
        val s = AppSettings(f)
        assertEquals(false, s.showLiveSpeed)
        s.showLiveSpeed = true
        assertEquals(true, AppSettings(f).showLiveSpeed)
        // Not tied to Pro, unlike the technical details.
        for (p in UserProfile.entries) { s.userProfile = p; assertEquals(p.name, true, s.showLiveSpeed) }
        s.resetPreferences()
        assertEquals(false, s.showLiveSpeed)
        f.delete()
    }

    @Test fun `the header's speed follows this switch, not the technical details, and the switch is in Display & theme`() {
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("state.activity == Activity.Generating && state.liveSpeed) state.liveTokPerSec"))
        val display = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
            .substringAfter("modelSetupScreen.sections.displayTheme").substringBefore("// Boarverse on")
        assertTrue(display.contains("SwitchRow(t(\"native.settings.liveSpeed\"), t(\"native.settings.liveSpeedNote\"), speed) { speed = it; s.showLiveSpeed = it }"))
        for (d in listOf("values", "values-pt")) {
            val x = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
            for (k in listOf("native_settings_liveSpeed", "native_settings_liveSpeedNote")) assertTrue("$d $k", x.contains("name=\"$k\""))
        }
    }
}
