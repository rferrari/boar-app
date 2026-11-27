package team.sopa.boar.engine.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import team.sopa.boar.engine.EngineTweaks

/**
 * 2.1.0 (BOAR-81): no more profiles. A one-time migration at the first launch keeps everyone's behaviour as it was:
 * below Pro the seven paused Pro values were not in effect, so they go; on Pro they were, so they stay.
 */
class ProfilesRetiredTest {
    @get:Rule val tmp = TemporaryFolder()

    private val tweak = EngineTweaks(threads = 2)

    /** An install as 2.0.0 left it: a model chosen, [profile] stored, every paused Pro value set. */
    private fun install(profile: String?, allPro: Boolean = true, model: Boolean = true): java.io.File {
        val f = java.io.File(tmp.newFolder(), "settings.json")
        val pro = if (!allPro) "" else
            """"engineTweaks":{"threads":2},"adaptiveRoutingEnabled":true,"showLiveStats":true,"freeMemoryAfterMinutes":10,""" +
                """"sourcesLayout":"above","showDrawerStats":true,"debugLogEnabled":true,"""
        f.writeText("{" + pro + (if (profile != null) "\"userProfile\":\"$profile\"," else "") +
            (if (model) "\"activeModelId\":{\"llm\":\"qwen3-1.7b\"}," else "") + "\"themeId\":\"moonlight\",\"stagedQuotes\":false}")
        return f
    }

    private fun assertProValuesGone(s: AppSettings) {
        assertTrue(s.engineTweaks.isAuto); assertFalse(s.adaptiveRoutingEnabled); assertFalse(s.showLiveStats)
        assertNull(s.freeMemoryAfterMinutes); assertEquals(AppSettings.SOURCES_TODAY, s.sourcesLayout)
        assertFalse(s.showDrawerStats); assertFalse(s.debugLogEnabled)
        for (k in AppSettings.PAUSED_PRO_KEYS) assertFalse(k, s.raw().containsKey(k))
    }

    @Test fun `below Pro the seven paused values go, so the chat stays on Auto as it was`() {
        for (p in listOf("simple", "explorer")) {
            val f = install(p)
            AppSettings(f).retireProfiles()
            val s = AppSettings(f)
            assertProValuesGone(s)
            // Everything else is kept, Staged quotes' stored off too (it held in every profile).
            assertEquals("moonlight", s.themeId); assertFalse(s.stagedQuotes); assertTrue(s.profilesRetired)
        }
    }

    @Test fun `on Pro every value stays in effect, technical details on as Pro had them`() {
        val f = install("pro")
        AppSettings(f).retireProfiles()
        val s = AppSettings(f)
        assertEquals(tweak, s.engineTweaks); assertTrue(s.adaptiveRoutingEnabled); assertTrue(s.showLiveStats)
        assertEquals(10, s.freeMemoryAfterMinutes); assertEquals(AppSettings.SOURCES_ABOVE, s.sourcesLayout)
        assertTrue(s.showDrawerStats); assertTrue(s.debugLogEnabled)
        // Pro with nothing stored for technical details had them on (Pro's default): written on.
        val g = install("pro", allPro = false)
        AppSettings(g).retireProfiles()
        assertTrue(AppSettings(g).showLiveStats); assertEquals(true, AppSettings(g).raw()["showLiveStats"]?.toString()?.toBooleanStrictOrNull())
    }

    @Test fun `an install from before profiles had everything in effect, and keeps it`() {
        val f = install(null)
        AppSettings(f).retireProfiles()
        val s = AppSettings(f)
        assertEquals(tweak, s.engineTweaks); assertTrue(s.showLiveStats); assertTrue(s.debugLogEnabled)
    }

    @Test fun `a fresh install gets the new default - technical details off`() {
        val f = java.io.File(tmp.newFolder(), "settings.json")
        AppSettings(f).retireProfiles()
        val s = AppSettings(f)
        assertFalse(s.showLiveStats); assertTrue(s.engineTweaks.isAuto); assertTrue(s.profilesRetired)
        assertFalse(AppSettings(java.io.File(tmp.newFolder(), "settings.json")).showLiveStats)
    }

    @Test fun `it runs once - a tweak set after it holds for everyone, whatever profile was stored`() {
        val f = install("simple", allPro = false)
        AppSettings(f).retireProfiles()
        AppSettings(f).engineTweaks = tweak
        AppSettings(f).retireProfiles()
        // No profile gates a value any more: what is stored is what counts.
        assertEquals(tweak, AppSettings(f).engineTweaks)
        AppSettings(f).showLiveStats = true
        assertTrue(AppSettings(f).showLiveStats)
    }

    @Test fun `a settings file that can't be read is left alone`() {
        val f = java.io.File(tmp.newFolder(), "settings.json")
        f.writeText("{\"userProfile\":\"simple\",\"engineTweaks\":{\"threads\":2")
        AppSettings(f).retireProfiles()
        assertEquals("{\"userProfile\":\"simple\",\"engineTweaks\":{\"threads\":2", f.readText())
    }

    @Test fun `the seven paused values are the ones read as defaults below Pro before`() {
        assertEquals(setOf("engineTweaks", "adaptiveRoutingEnabled", "showLiveStats", "freeMemoryAfterMinutes", "sourcesLayout", "showDrawerStats", "debugLogEnabled"),
            AppSettings.PAUSED_PRO_KEYS.toSet())
        val src = java.io.File("src/main/java/team/sopa/boar/engine/settings/AppSettings.kt").readText()
        assertFalse(src.contains("proActive"))
    }

    @Test fun `the app runs it before anything reads a setting - the CPU build, the debug log (CodeRabbit)`() {
        val services = java.io.File("../app/src/main/java/team/sopa/boar/app/BoarServices.kt").readText()
        val get = services.substringAfter("fun get(context: Context): BoarServices").substringBefore("\n        }\n")
        val retire = get.indexOf("AppSettings(File(app.filesDir, \"settings.json\")).also { it.retireProfiles() }")
        assertTrue(retire >= 0 && retire < get.indexOf("BoarEngine.init(") && retire < get.indexOf("BoarServices(app)"))
    }

    @Test fun `an install from before profiles with settings but no model chosen keeps technical details on (CodeRabbit)`() {
        val f = install(null, allPro = false, model = false)
        AppSettings(f).retireProfiles()
        assertTrue(AppSettings(f).showLiveStats)
    }
}
