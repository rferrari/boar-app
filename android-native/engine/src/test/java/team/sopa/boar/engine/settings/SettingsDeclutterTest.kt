package team.sopa.boar.engine.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** rc3 slice 5: Settings without the menu's duplicates, language and voice together, the menu's Close BOAR (spec §8). */
class SettingsDeclutterTest {
    private val app = File("../app/src/main")
    private fun src(path: String) = File(app, "java/team/sopa/boar/$path").readText()
    private val settings = src("ui/settings/SettingsScreen.kt")
    private fun strings(dir: String): Map<String, String> =
        listOf("strings.xml", "strings_native.xml").flatMap { Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(File(app, "res/$dir/$it").readText()).map { m -> m.groupValues[1] to m.groupValues[2] } }.toMap()

    @Test fun `Models and Knowledge are only in the menu`() {
        assertFalse(settings.contains("ModelsSection"))
        assertFalse(settings.contains("KnowledgeSection"))
        val main = src("MainActivity.kt")
        assertTrue(main.contains("DrawerItem(\"models\""))
        assertTrue(main.contains("DrawerItem(\"knowledge\""))
    }

    @Test fun `the search model's missing warning is in the Model Catalog`() =
        assertTrue(src("ui/models/ModelsScreen.kt").contains("native.settings.embeddingMissing"))

    // Golden change (adam, 16 Nov; t9 0450): Staged quotes, Free memory and Show technical details moved to Experimental
    // (Pro); a build without Boar Labs keeps them in Advanced tweaks. One place writes each.
    @Test fun `free memory, staged quotes and technical details are in Experimental for Pro, in Advanced without Boar Labs`() {
        assertEquals(1, Regex("freeMemoryInBackground = ").findAll(settings).count())
        val moved = settings.substring(settings.indexOf("private fun MovedToExperimental(")).let { it.substring(0, it.indexOf("\n}\n")) }
        for (w in listOf("freeMemoryInBackground = ", "freeMemoryAfterMinutes = ", "s.stagedQuotes = ", "s.showLiveStats = ")) assertTrue(w, moved.contains(w))
        val experimental = settings.substring(settings.indexOf("private fun ExperimentalSection(")).let { it.substring(0, it.indexOf("\n}\n")) }
        assertTrue(experimental.contains("if (shown(SettingsPart.TWEAKS)) MovedToExperimental("))
        val tweaks = settings.substring(settings.indexOf("private fun TweaksSection(")).let { it.substring(0, it.indexOf("\n}\n")) }
        assertTrue(tweaks.contains("if (!team.sopa.boar.BuildConfig.EXPERIMENTAL_FEATURES) MovedToExperimental("))
    }

    @Test fun `free memory stays in effect below Pro, the exception to Pro's rule`() {
        val f = Files.createTempDirectory("boar-free").toFile().resolve("settings.json")
        val s = AppSettings(f)
        s.userProfile = UserProfile.PRO
        s.freeMemoryInBackground = false
        s.userProfile = UserProfile.SIMPLE
        assertFalse(s.freeMemoryInBackground)
        assertTrue(AppSettings(Files.createTempDirectory("boar-free2").toFile().resolve("settings.json")).freeMemoryInBackground)
    }

    @Test fun `language and voice are one section, voice from Explorer`() {
        val section = settings.substring(settings.indexOf("shown(SettingsPart.LANGUAGE)")).let { it.substring(0, it.indexOf("shown(SettingsPart.SECURITY)")) }
        assertTrue(section.contains("native.settings.languageVoice"))
        assertTrue(section.contains("if (shown(SettingsPart.VOICE)) VoiceRows("))
        assertFalse(settings.contains("VoiceSection("))
        assertTrue(settings.substring(settings.indexOf("private fun VoiceRows(")).contains("voiceInputEnabled = it"))
        assertEquals(UserProfile.SIMPLE, SettingsPart.LANGUAGE.from)
        assertEquals(UserProfile.EXPLORER, SettingsPart.VOICE.from)
    }

    @Test fun `voice listens in the app's language`() {
        val v = src("voice/VoiceInput.kt")
        assertTrue(v.contains("RecognizerIntent.EXTRA_LANGUAGE"))
        assertTrue(v.contains("\"pt\" -> \"pt-BR\""))
        assertTrue(v.contains("\"en\" -> \"en-US\""))
    }

    @Test fun `the menu says Close BOAR with a power icon, and no last-query row`() {
        val en = strings("values"); val pt = strings("values-pt")
        assertEquals("Close BOAR", en["native_drawer_close"])
        assertEquals("Fechar o BOAR", pt["native_drawer_close"])
        val drawer = src("ui/drawer/BoarDrawer.kt")
        assertTrue(drawer.contains("key == \"close\" -> team.sopa.boar.R.drawable.ic_power"))
        assertTrue(File(app, "res/drawable/ic_power.xml").isFile)
        assertFalse(drawer.contains("drawerFooterStats.lastQuery"))
        assertFalse(drawer.contains("lastTokPerSec"))
    }

    @Test fun `every new string is in English and Portuguese`() {
        val en = strings("values"); val pt = strings("values-pt")
        for (k in listOf("native_settings_languageVoice", "native_voice_followsApp", "native_voice_followsPhone", "native_settings_embeddingMissing")) {
            assertTrue("$k EN", en[k]?.isNotBlank() == true)
            assertTrue("$k PT", pt[k]?.isNotBlank() == true)
        }
        assertTrue(en["native_settings_embeddingMissing"]!!.contains("Start over"))
    }
}
