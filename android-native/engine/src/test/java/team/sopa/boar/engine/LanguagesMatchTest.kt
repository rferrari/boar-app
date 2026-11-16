package team.sopa.boar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * adam, 16 Nov: "the languages need to match." (t9 0125 item 1). Every user-facing string exists in English and
 * Portuguese, in the app and every plugin, with the same placeholders and never empty; the texts written in code have
 * each language too.
 */
class LanguagesMatchTest {
    private val res = java.io.File("../app/src").walkTopDown().filter { it.isFile && it.parentFile.name == "values" && it.extension == "xml" && it.parentFile.parentFile.name == "res" }.toList()
    private fun strings(f: java.io.File): Map<String, String> =
        if (!f.exists()) emptyMap() else Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    private fun holders(s: String) = Regex("""\{\{\w+}}|%\d*\$?[sd]""").findAll(s).map { it.value }.toSortedSet()
    /** Keys that are the same word in every language (the app's name). */
    private val SAME = setOf("app_name")

    @Test fun `every key in EN is in PT and the other way round`() {
        val missing = res.flatMap { f ->
            val en = strings(f).keys; val pt = strings(java.io.File(f.path.replace("/values/", "/values-pt/"))).keys
            (en - pt - SAME).map { "PT lacks ${f.name}:$it" } + (pt - en).map { "EN lacks ${f.name}:$it" }
        }
        assertEquals(missing.joinToString("\n"), 0, missing.size)
        assertTrue("found ${res.size} string files", res.size >= 10)
    }

    @Test fun `the same placeholders, and no empty translation`() {
        val bad = res.flatMap { f ->
            val en = strings(f); val pt = strings(java.io.File(f.path.replace("/values/", "/values-pt/")))
            en.filterKeys { it in pt }.mapNotNull { (k, v) ->
                when {
                    holders(v) != holders(pt.getValue(k)) -> "$k: ${holders(v)} vs ${holders(pt.getValue(k))}"
                    v.isNotBlank() && pt.getValue(k).isBlank() -> "$k: empty in PT"
                    else -> null
                }
            }
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    @Test fun `texts written in code have each language`() {
        // The CO card: EN, PT and ES.
        for (s in listOf(team.sopa.boar.engine.routing.CoHazard.CO_ACUTE_EN, team.sopa.boar.engine.routing.CoHazard.CO_ACUTE_PT, team.sopa.boar.engine.routing.CoHazard.CO_ACUTE_ES)) assertTrue(s.isNotBlank())
        for (l in listOf("en", "pt", "es")) assertTrue(l, team.sopa.boar.engine.routing.CoHazard.acuteAnswer("x", l).isNotBlank())
        assertTrue(team.sopa.boar.engine.routing.CoHazard.acuteAnswer("x", "pt") != team.sopa.boar.engine.routing.CoHazard.acuteAnswer("x", "en"))
        // The lab marks: the same rows and marks in EN and PT.
        val blood = "| Test | Result | Reference |\n|---|---|---|\n| LDL | 162 mg/dL | below 130 mg/dL |\n| HDL | 48 mg/dL | above 40 mg/dL |\n| Vitamin D | 24 ng/mL | 30 to 100 ng/mL |"
        val en = team.sopa.boar.engine.answer.LabRanges.note(blood, pt = false)!!.lines().drop(1)
        val pt = team.sopa.boar.engine.answer.LabRanges.note(blood, pt = true)!!.lines().drop(1)
        assertEquals(en.size, pt.size)
        for ((e, p) in en.zip(pt)) for ((a, b) in listOf("ABOVE" to "ACIMA", "BELOW" to "ABAIXO", "WITHIN" to "DENTRO")) assertEquals("$e | $p", e.contains(a), p.contains(b))
    }
}
