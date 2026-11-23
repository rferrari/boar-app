package team.sopa.boar.engine.labs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validator F-R1 (t9 1805): setup's plugin list turned Cohazard off with one tap. The rule now sits in LabPrefs.setEnabled
 * (PluginReach.allowsSet), so no switch, present or future, can turn a safety plugin off without the question.
 */
class SafetyOffGuardTest {
    @Test fun `a safety plugin goes off only once the question was answered`() {
        assertFalse(PluginReach.allowsSet("cohazard", on = false, confirmed = false))
        assertTrue(PluginReach.allowsSet("cohazard", on = false, confirmed = true))
        // On, or any other plugin, needs no question.
        assertTrue(PluginReach.allowsSet("cohazard", on = true, confirmed = false))
        for (id in listOf("calc", "facts", "printer", "image", "canvas")) for (on in listOf(true, false)) assertTrue(PluginReach.allowsSet(id, on, confirmed = false))
    }

    @Test fun `LabPrefs applies it before anything changes, and only the question confirms`() {
        val prefs = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabPlugin.kt").readText().substringAfter("fun setEnabled(").substringBefore("\n    }\n")
        assertTrue(prefs.startsWith("id: String, on: Boolean, fromPage: Boolean = false, confirmed: Boolean = false)"))
        assertTrue(prefs.indexOf("if (!team.sopa.boar.engine.labs.PluginReach.allowsSet(id, on, confirmed)) return") < prefs.indexOf("switches[id] = on"))
        // confirmed = true appears only inside the question's own action (rememberSafetyOff { … }).
        val labs = java.io.File("../app/src/labs").walk().filter { it.extension == "kt" }.map { it.readText() }.joinToString("\n")
        val confirmed = Regex("""setEnabled\([^)]*confirmed = true\)""").findAll(labs).toList()
        assertTrue(confirmed.size >= 4)
        for (m in confirmed) assertTrue(m.value, labs.substring(maxOf(0, m.range.first - 60), m.range.first).contains("rememberSafetyOff(lab.id) {"))
    }
}
