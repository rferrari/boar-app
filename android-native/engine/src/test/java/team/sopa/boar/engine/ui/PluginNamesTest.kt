package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, Nov 10: "boar labs become plugins", and no "Boar" in a plugin's name. Ids, folders and classes keep "labs". */
class PluginNamesTest {
    private fun strings(): List<Pair<String, String>> = java.io.File("../app/src").walkTopDown()
        .filter { it.isFile && it.extension == "xml" && (it.parentFile.name == "values" || it.parentFile.name == "values-pt") }
        .flatMap { f -> Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(f.readText()).map { it.groupValues[1] to it.groupValues[2] } }
        .toList()

    /** Big Boar and Boar Wisp are out of rc3 and keep their names (t9). */
    private val outOfRc3 = setOf("native_labs_bmoe_title", "native_labs_colibri_title")

    @Test fun `no plugin's name starts with Boar`() {
        for ((k, v) in strings()) if (Regex("native_labs_[A-Za-z]+_title").matches(k) && k !in outOfRc3) assertTrue("$k = $v", !v.startsWith("Boar "))
    }

    @Test fun `no Labs left in what the user reads, but for names and code`() {
        for ((k, v) in strings()) {
            val text = v.replace(Regex("\\{\\{[^}]*\\}\\}|[\\w./-]*[/.-][\\w./-]*|R&amp;D Labs"), "")
            assertTrue("$k = $v", !Regex("\\blabs?\\b|Boar Labs", RegexOption.IGNORE_CASE).containsMatchIn(text))
        }
    }

    @Test fun `About's credits and the user guide don't name a plugin with Boar (validator R-A)`() {
        val names = Regex("Boar (Calculator|Distance|Facts|Guard|Cohazard|Vitals|Designer|Press|Polyglot|Nomad|Lunar|Canvas|Labs)")
        val credits = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()
        // What the user reads: the quoted strings (comments keep the code name).
        val quoted = Regex("\"[^\"\\n]*\"").findAll(credits).map { it.value }.filter { names.containsMatchIn(it) }.toList()
        assertTrue(quoted.toString(), quoted.isEmpty())
        assertTrue(names.find(java.io.File("../../docs/USING.md").readText())?.value ?: "", !names.containsMatchIn(java.io.File("../../docs/USING.md").readText()))
    }
}
