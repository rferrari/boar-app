package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.settings.UserProfile
import java.io.File

class LabDefaultsTest {
    private val plugins = File("../app/src/labs/plugins")

    /** A plugin's status as its code declares it (LabStatus.X in its sources), or null for a classified teaser. */
    private fun status(id: String): String? = File(plugins, id).walk().filter { it.extension == "kt" }
        .flatMap { Regex("""LabStatus\.([A-Z]+)""").findAll(it.readText()).map { m -> m.groupValues[1] } }.firstOrNull()

    @Test fun `the default-on set is adam's list without the ALPHA labs still pending`() {
        assertEquals(setOf("pulse", "image", "printer", "polyglot", "calc", "distance", "facts", "cohazard"), LabDefaults.ON)
        assertEquals(setOf("compass", "lunar", "staged", "guard"), LabDefaults.PENDING_ALPHA)
        assertTrue((LabDefaults.ON intersect LabDefaults.PENDING_ALPHA).isEmpty())
    }

    @Test fun `every lab in either list is a plugin in this build`() {
        for (id in LabDefaults.ON + LabDefaults.PENDING_ALPHA) assertTrue("$id has no plugin folder", File(plugins, "$id/plugin.properties").isFile)
    }

    @Test fun `no ALPHA, under-review or classified lab starts on`() {
        for (id in LabDefaults.ON) assertTrue("$id is ${status(id)}", status(id) == "LIVE" || status(id) == "BETA")
        for (id in LabDefaults.PENDING_ALPHA) assertEquals("$id", "ALPHA", status(id))
    }

    @Test fun `the labs adam keeps off start off`() {
        for (id in listOf("bmoe", "colibri", "dailyQuiz", "sanctuary", "signboard", "canvas")) assertFalse(id, LabDefaults.switchOf(id, null))
    }

    @Test fun `a fresh install gets exactly the default set`() {
        val all = plugins.listFiles().orEmpty().filter { File(it, "plugin.properties").isFile }.map { it.name }
        assertEquals(LabDefaults.ON, all.filter { LabDefaults.switchOf(it, null) }.toSet())
    }

    @Test fun `the user's own switch wins over the default`() {
        assertFalse(LabDefaults.switchOf("calc", false))
        assertTrue(LabDefaults.switchOf("bmoe", true))
        assertTrue(LabDefaults.switchOf("calc", null))
    }

    @Test fun `the update note names the default-on labs the user never switched`() {
        val ids = listOf("pulse", "calc", "facts", "bmoe", "image", "compass", "printer")
        val stored = mapOf("calc" to false, "facts" to true, "bmoe" to true)
        assertEquals(listOf("pulse", "image", "printer"), LabDefaults.turnedOnByUpdate(ids) { stored[it] })
    }

    @Test fun `an update that changes nothing names no lab`() =
        assertEquals(emptyList<String>(), LabDefaults.turnedOnByUpdate(LabDefaults.ON.toList()) { false })

    @Test fun `the update note's lab line is in English and Portuguese`() {
        for (dir in listOf("values", "values-pt")) {
            val xml = File("../app/src/main/res/$dir/strings_native.xml").readText()
            val line = Regex("""<string name="native_settings_profile_labsOn"[^>]*>(.*?)</string>""").find(xml)?.groupValues?.get(1)
            assertTrue("$dir: native_settings_profile_labsOn", line != null && line.contains("{{labs}}"))
        }
    }

    @Test fun `Staged Answers and Canvas show an avatar until they have their own icon`() {
        for ((id, art) in listOf("staged" to "truffle_elder", "canvas" to "truffle_pixel_hacker")) {
            val src = File(plugins, id).walk().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
            assertTrue(id, src.contains("override val art = team.sopa.boar.R.drawable.$art"))
        }
    }

    @Test fun `only Pro manages labs`() {
        assertFalse(LabDefaults.managementShown(UserProfile.SIMPLE))
        assertFalse(LabDefaults.managementShown(UserProfile.EXPLORER))
        assertTrue(LabDefaults.managementShown(UserProfile.PRO))
    }
}
