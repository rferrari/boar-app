package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.settings.UserProfile
import java.io.File

class LabDefaultsTest {
    private val plugins = File("../app/src/labs/plugins")

    /**
     * A plugin's statuses as its code declares them: every `override val status = …LabStatus.X` outside comments (the
     * image lab writes it fully qualified). Empty for a classified teaser with no code.
     */
    private fun statuses(id: String): List<String> = File(plugins, id).walk().filter { it.extension == "kt" }.flatMap { declaredStatuses(it.readText()) }.toList()

    private fun declaredStatuses(source: String): List<String> {
        val code = source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("""//[^\n]*"""), "")
        return Regex("""override\s+val\s+status\s*(?::\s*[\w.]+\s*)?=\s*(?:[\w.]+\.)?LabStatus\.([A-Z]+)""").findAll(code).map { it.groupValues[1] }.toList()
    }

    private fun startsOnAllowed(statuses: List<String>) = statuses.isNotEmpty() && statuses.all { it == "LIVE" || it == "BETA" }

    @Test fun `the default-on set is adam's list without the ALPHA labs still pending`() {
        assertEquals(setOf("pulse", "image", "printer", "calc", "distance", "facts", "cohazard"), LabDefaults.ON)
        // Polyglot starts off (adam, Nov 11) but ships: it stays in the release list.
        assertTrue("polyglot" !in LabDefaults.ON && File("../app/src/labs/released.txt").readText().contains("polyglot:"))
        assertEquals(setOf("compass", "lunar", "staged", "guard"), LabDefaults.PENDING_ALPHA)
        assertTrue((LabDefaults.ON intersect LabDefaults.PENDING_ALPHA).isEmpty())
    }

    @Test fun `every lab in either list is a plugin in this build`() {
        for (id in LabDefaults.ON + LabDefaults.PENDING_ALPHA) assertTrue("$id has no plugin folder", File(plugins, "$id/plugin.properties").isFile)
    }

    @Test fun `no ALPHA, under-review or classified lab starts on`() {
        for (id in LabDefaults.ON) assertTrue("$id is ${statuses(id)}", startsOnAllowed(statuses(id)))
        for (id in LabDefaults.PENDING_ALPHA) assertEquals(id, listOf("ALPHA"), statuses(id))
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

    @Test fun `the status guard reads the declaration, not a comment or another mention`() {
        val alpha = "// LabStatus.BETA, once tested\n/* override val status = LabStatus.LIVE */\nval x = LabStatus.BETA\n    override val status = LabStatus.ALPHA\n"
        assertEquals(listOf("ALPHA"), declaredStatuses(alpha))
        assertFalse(startsOnAllowed(declaredStatuses(alpha)))
        assertEquals(listOf("BETA"), declaredStatuses("    override val status = team.sopa.boar.labs.LabStatus.BETA"))
        assertEquals(listOf("LIVE"), declaredStatuses("    override val status: LabStatus = LabStatus.LIVE"))
        assertFalse("no declaration", startsOnAllowed(declaredStatuses("val s = LabStatus.BETA")))
        assertFalse("one of two is ALPHA", startsOnAllowed(listOf("BETA", "ALPHA")))
    }

    @Test fun `every plugin with code declares its status once`() {
        for (dir in plugins.listFiles().orEmpty().filter { File(it, "plugin.properties").isFile && File(it, "java").isDirectory })
            assertEquals(dir.name, 1, statuses(dir.name).size)
    }

    @Test fun `a lab left on counts only while Boar Labs is on`() {
        assertTrue(LabDefaults.userChoseOn(true, listOf(null, true, false)))
        assertFalse(LabDefaults.userChoseOn(false, listOf(true)))
        assertFalse(LabDefaults.userChoseOn(true, listOf(null, false)))
    }

    @Test fun `only Pro manages labs`() {
        assertFalse(LabDefaults.managementShown(UserProfile.SIMPLE))
        assertFalse(LabDefaults.managementShown(UserProfile.EXPLORER))
        assertTrue(LabDefaults.managementShown(UserProfile.PRO))
    }
}
