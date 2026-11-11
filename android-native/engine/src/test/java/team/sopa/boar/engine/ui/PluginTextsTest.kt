package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, Nov 12: each plugin's line fits the list (two short sentences at most); the rest is in "About this plugin". */
class PluginTextsTest {
    private fun bodies(dir: String) = java.io.File("../app/src/labs/plugins").walkTopDown()
        .filter { it.isFile && it.extension == "xml" && it.parentFile.name == dir }
        .flatMap { f -> Regex("<string name=\"native_labs_([A-Za-z]+)_body\"[^>]*>([^<]*)</string>").findAll(f.readText()).map { it.groupValues[1] to it.groupValues[2] } }.toList()

    @Test fun `every description is at most two sentences and about two lines`() {
        for (d in listOf("values", "values-pt")) for ((id, b) in bodies(d)) {
            val sentences = b.split(Regex("(?<=[.!?])\\s+")).count { it.isNotBlank() }
            assertTrue("$d $id: $b", sentences <= 2 && b.length <= 90)
        }
    }

    @Test fun `each plugin page ends with About this plugin, folded`() {
        val labs = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabsScreen.kt").readText()
        assertTrue(labs.contains("AboutPlugin(lab)") && labs.contains("t(\"native.labs.aboutPlugin\")"))
        for ((id, _) in bodies("values")) assertTrue(id, java.io.File("../app/src/labs/plugins/$id").walkTopDown().any { it.isFile && it.readText().contains("native_labs_${id}_about\"") })
    }

    @Test fun `Vitals puts heat and battery side by side and the cores as vertical bars`() {
        val v = java.io.File("../app/src/labs/plugins/pulse/java/team/sopa/boar/labs/pulse/DevicePulseLab.kt").readText()
        assertTrue(v.contains("Modifier.weight(3f).fillMaxHeight()") && v.contains("Modifier.weight(1f).fillMaxHeight()"))
        assertTrue(v.contains("fillMaxHeight(frac.coerceIn(0f, 1f))") && v.contains("t(\"native.labs.pulse.cores\") + \" · GHz\""))
    }
}
