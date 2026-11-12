package team.sopa.boar.engine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.labs.PluginAnswers
import team.sopa.boar.engine.rag.Facts
import team.sopa.boar.engine.routing.LabTools
import team.sopa.boar.engine.routing.LanguageGate
import team.sopa.boar.engine.routing.TravelDistance
import java.io.File

/**
 * The plugin pages' copy (adam, Nov 12): each example is what the plugin really replies, and each Try row is a question
 * it really answers, in English and Portuguese. Read from the strings.
 */
class PluginExamplesTest {
    private fun strings(id: String, d: String): Map<String, String> =
        Regex("<string name=\"native_labs_${id}_([A-Za-z0-9_]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(File("../app/src/labs/plugins/$id/res/$d/strings_lab_$id.xml").readText())
            .associate { it.groupValues[1] to unescape(it.groupValues[2]) }

    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private val distanceTable = TravelDistance(File("../app/src/labs/plugins/distance/res/raw/lab_distance_world_cities_v1.json").readText())
    private val factsTable = Facts(File("../app/src/labs/plugins/facts/res/raw/lab_facts_facts_v1.json").readText())

    private fun tool(id: String, q: String): String? = when (id) {
        "calc" -> LabTools.calc(q, emptyList())?.text
        "distance" -> LabTools.distance(distanceTable, q, emptyList())?.text
        "facts" -> LabTools.facts(factsTable, q, emptyList())?.text
        "cohazard" -> LabTools.coHazard(q)?.text
        else -> error(id)
    }

    @Test fun `the instant answer plugins' examples are their real replies, and every Try row is answered`() {
        for (d in listOf("values", "values-pt")) for (id in listOf("calc", "distance", "facts", "cohazard")) {
            val s = strings(id, d)
            val reply = tool(id, s.getValue("exampleQ"))
            assertNotNull("$d $id example", reply)
            // Cohazard's example is an excerpt of its fixed text: the first sentence is the same.
            if (id == "cohazard") assertTrue("$d $id", reply!!.startsWith(s.getValue("exampleA").substringBefore(" [")))
            else assertEquals("$d $id", s.getValue("exampleA"), reply)
            for (i in 1..3) assertNotNull("$d $id example$i", tool(id, s.getValue("example$i")))
        }
    }

    @Test fun `Nomad, Lunar and Vitals Try rows are questions they answer in the chat`() {
        for (d in listOf("values", "values-pt")) {
            for (k in listOf("exampleQ", "example1", "example2")) {
                assertTrue("$d compass $k", PluginAnswers.isDirectionQuestion(strings("compass", d).getValue(k)))
                assertTrue("$d lunar $k", PluginAnswers.isMoonQuestion(strings("lunar", d).getValue(k)))
                assertTrue("$d pulse $k", PluginAnswers.isPhoneQuestion(strings("pulse", d).getValue(k)))
            }
        }
    }

    @Test fun `Polyglot names the languages BOAR takes, not only English`() {
        assertEquals(setOf("en", "pt", "es"), LanguageGate.ALLOWED)
        val lab = File("../app/src/labs/plugins/polyglot/java/team/sopa/boar/labs/polyglot/PolyglotLab.kt").readText()
        assertTrue(lab.contains("LanguageGate.ALLOWED.map { languageName(it, ui) }"))
        for (d in listOf("values", "values-pt")) {
            val s = strings("polyglot", d)
            assertTrue(d, s.getValue("summary").contains("{{languages}}"))
            assertTrue(d, Regex("Portuguese|português").containsMatchIn(s.getValue("reply")))
            assertTrue(d, !s.values.any { Regex("other than English|que não seja inglês").containsMatchIn(it) })
        }
    }

    @Test fun `the old names and the build flag that doesn't exist are gone`() {
        for (d in listOf("values", "values-pt")) {
            val press = strings("printer", d).values.joinToString("\n")
            assertTrue(d, !Regex("Image Gen|Gerador de imagem|Printer|Impressora").containsMatchIn(press))
            assertTrue(d, !strings("image", d).values.any { it.contains("-Pboar.labs") })
        }
    }
}
