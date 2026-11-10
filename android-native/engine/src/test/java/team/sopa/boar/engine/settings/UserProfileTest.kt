package team.sopa.boar.engine.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.EngineTweaks
import team.sopa.boar.engine.KvType
import java.io.File
import java.nio.file.Files

class UserProfileTest {
    private fun file(text: String?) = Files.createTempDirectory("boar-profile").toFile().resolve("settings.json").also { f -> text?.let { f.writeText(it) } }

    /** An rc2 settings.json: no profile yet. */
    private val rc2 = """{"activeModelId":{"llm":"qwen3-4b"},"personalityId":"detailed","themeId":"moonlight"}"""

    // ── Gating: each part shows from its profile on, and in every profile above it ──

    private val simple = setOf(SettingsPart.PROFILE, SettingsPart.LANGUAGE, SettingsPart.THEME, SettingsPart.TEXT_SIZE, SettingsPart.SECURITY,
        SettingsPart.MODELS, SettingsPart.RECOVERY)
    private val explorer = simple + setOf(SettingsPart.TONE, SettingsPart.KNOWLEDGE, SettingsPart.MEMORY, SettingsPart.VOICE, SettingsPart.AVATARS,
        SettingsPart.BOARVERSE_SWITCH, SettingsPart.ORIENTATION, SettingsPart.HAPTICS)
    private val pro = SettingsPart.entries.toSet()

    @Test fun `Simple shows only the essentials`() =
        assertEquals(simple, SettingsPart.entries.filter { it.shownIn(UserProfile.SIMPLE) }.toSet())

    @Test fun `Explorer adds answers, knowledge, chats, voice and Boarverse`() =
        assertEquals(explorer, SettingsPart.entries.filter { it.shownIn(UserProfile.EXPLORER) }.toSet())

    @Test fun `Pro shows everything`() =
        assertEquals(pro, SettingsPart.entries.filter { it.shownIn(UserProfile.PRO) }.toSet())

    @Test fun `Advanced tweaks, technical details, telemetry, labs settings and adaptive routing are Pro only`() {
        for (part in listOf(SettingsPart.TWEAKS, SettingsPart.TECHNICAL_DETAILS, SettingsPart.TELEMETRY, SettingsPart.LABS_SETTINGS, SettingsPart.ADAPTIVE_ROUTING)) {
            assertFalse(part.name, part.shownIn(UserProfile.SIMPLE))
            assertFalse(part.name, part.shownIn(UserProfile.EXPLORER))
            assertTrue(part.name, part.shownIn(UserProfile.PRO))
        }
    }

    @Test fun `Experimental shows from Explorer, for the Boarverse switch`() {
        assertFalse(SettingsPart.experimentalShownIn(UserProfile.SIMPLE))
        assertTrue(SettingsPart.experimentalShownIn(UserProfile.EXPLORER))
        assertTrue(SettingsPart.experimentalShownIn(UserProfile.PRO))
    }

    @Test fun `every part is wired into the Settings screen`() {
        val screen = File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
        for (part in SettingsPart.entries) assertTrue("SettingsPart.${part.name} is not used in SettingsScreen.kt", screen.contains("SettingsPart.${part.name}"))
        assertTrue(screen.contains("SettingsPart.experimentalShownIn"))
    }

    // ── Fresh install and update ──

    @Test fun `a fresh install starts on Simple`() {
        val f = file(null)
        val s = AppSettings(f)
        assertEquals(UserProfile.SIMPLE, s.userProfile)
        assertTrue(s.profileSettled)
        assertFalse(s.profileNotePending)
        assertEquals(UserProfile.SIMPLE, AppSettings(f).userProfile)
    }

    @Test fun `an rc2 install with nothing changed becomes Simple, with the note`() {
        val s = AppSettings(file(rc2))
        assertFalse(s.profileSettled)
        assertEquals(UserProfile.SIMPLE, s.settleProfile(anyLabOn = false))
        assertTrue(s.profileNotePending)
    }

    @Test fun `an rc2 install with a tweak becomes Pro`() {
        val s = AppSettings(file("""{"engineTweaks":{"threads":4}}"""))
        assertEquals(UserProfile.PRO, s.settleProfile(anyLabOn = false))
        assertEquals(UserProfile.PRO, s.userProfile)
    }

    @Test fun `an rc2 install with a lab on becomes Pro`() =
        assertEquals(UserProfile.PRO, AppSettings(file(rc2)).settleProfile(anyLabOn = true))

    @Test fun `an rc2 install with adaptive routing or a free-memory delay becomes Pro`() {
        assertEquals(UserProfile.PRO, AppSettings(file("""{"adaptiveRoutingEnabled":true}""")).settleProfile(false))
        assertEquals(UserProfile.PRO, AppSettings(file("""{"freeMemoryAfterMinutes":5}""")).settleProfile(false))
    }

    @Test fun `rc2's technical details on by default is not a reason for Pro`() =
        assertEquals(UserProfile.SIMPLE, AppSettings(file("""{"showLiveStats":true,"themeId":"campfire"}""")).settleProfile(false))

    @Test fun `the profile is settled once and kept`() {
        val f = file(rc2)
        AppSettings(f).settleProfile(anyLabOn = false)
        val again = AppSettings(f)
        assertTrue(again.profileSettled)
        assertEquals(UserProfile.SIMPLE, again.settleProfile(anyLabOn = true))
    }

    @Test fun `before it is settled an rc2 install keeps its tweaks, as in rc2`() {
        val s = AppSettings(file("""{"engineTweaks":{"threads":4},"showLiveStats":true}"""))
        assertEquals(4, s.engineTweaks.threads)
        assertTrue(s.showLiveStats)
    }

    @Test fun `Reset settings keeps the profile`() {
        val f = file(null)
        val s = AppSettings(f)
        s.userProfile = UserProfile.EXPLORER
        s.resetPreferences()
        assertEquals(UserProfile.EXPLORER, AppSettings(f).userProfile)
    }

    // ── Lowered profile: Pro's values read as defaults below Pro, and come back with Pro ──

    @Test fun `below Pro the tweaks read as Auto and come back with Pro`() {
        val f = file(null)
        val s = AppSettings(f)
        s.userProfile = UserProfile.PRO
        val tweaks = EngineTweaks(threads = 6, kvCache = KvType.Q8_0)
        s.engineTweaks = tweaks
        s.adaptiveRoutingEnabled = true
        s.freeMemoryAfterMinutes = 10
        s.showDrawerStats = true
        s.showLiveStats = true
        for (lower in listOf(UserProfile.SIMPLE, UserProfile.EXPLORER)) {
            s.userProfile = lower
            assertTrue(s.engineTweaks.isAuto)
            assertFalse(s.adaptiveRoutingEnabled)
            assertNull(s.freeMemoryAfterMinutes)
            assertFalse(s.showDrawerStats)
            assertFalse(s.showLiveStats)
            assertFalse(s.debugLogEnabled)
        }
        s.userProfile = UserProfile.PRO
        val back = AppSettings(f)
        assertEquals(tweaks, back.engineTweaks)
        assertTrue(back.adaptiveRoutingEnabled)
        assertEquals(10, back.freeMemoryAfterMinutes)
        assertTrue(back.showDrawerStats)
        assertTrue(back.showLiveStats)
    }

    @Test fun `lowering the profile doesn't touch what everyone sees`() {
        val s = AppSettings(file(null))
        s.userProfile = UserProfile.PRO
        s.personalityId = "succinct"; s.deepResearchMode = true; s.themeId = "campfire"; s.fontScale = "large"; s.voiceInputEnabled = false
        s.userProfile = UserProfile.SIMPLE
        assertEquals("succinct", s.personalityId)
        assertTrue(s.deepResearchMode)
        assertEquals("campfire", s.themeId)
        assertEquals("large", s.fontScale)
        assertFalse(s.voiceInputEnabled)
    }

    // ── Strings ──

    private fun strings(dir: String): Map<String, String> {
        val re = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return re.findAll(File("../app/src/main/res/$dir/strings_native.xml").readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test fun `every profile string is in English and Portuguese`() {
        val en = strings("values"); val pt = strings("values-pt")
        val keys = listOf("title", "updated") + UserProfile.entries.flatMap { listOf(it.key, it.key + "Note") }
        for (k in keys.map { "native_settings_profile_$it" }) {
            assertTrue("$k missing in English", en[k]?.isNotBlank() == true)
            assertTrue("$k missing in Portuguese", pt[k]?.isNotBlank() == true)
        }
        assertTrue(en["native_settings_profile_updated"]!!.contains("{{profile}}"))
        assertTrue(pt["native_settings_profile_updated"]!!.contains("{{profile}}"))
    }
}
