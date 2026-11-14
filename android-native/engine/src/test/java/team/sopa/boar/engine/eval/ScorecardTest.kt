package team.sopa.boar.engine.eval

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 0521 item 4: what the computer scores, "to read" for the rest, and the comparison with the previous run. */
class ScorecardTest {
    private val topics = BenchPresetsTest.topics

    private fun row(s: String) = Json.parseToJsonElement(s) as JsonObject
    private fun plugin(id: String, tool: String?, answer: String, topic: String? = "plugins") = row(
        """{"queryId":"$id","modelId":"m","style":"succinct","outcome":"success","answer":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(answer))}""" +
            (tool?.let { ""","tool":"$it"""" } ?: "") + (topic?.let { ""","topic":"$it"""" } ?: "") + "}")

    @Test fun `a plugin's exact text and route pass, anything else fails`() {
        val ok = Scorecard.judge(plugin("calc-battery-en", "calculator-battery", "5,000 mAh × 3.85 V ÷ 1000 = 19.25 Wh."), topics)
        assertEquals(Scorecard.Verdict.PASS, ok.verdict)
        val wrongText = Scorecard.judge(plugin("calc-battery-en", "calculator-battery", "About 19 Wh."), topics)
        assertEquals(Scorecard.Verdict.FAIL, wrongText.verdict)
        val noPlugin = Scorecard.judge(plugin("calc-battery-en", null, "5,000 mAh × 3.85 V ÷ 1000 = 19.25 Wh."), topics)
        assertEquals(listOf("tool"), noPlugin.checks.filter { it.ok == false }.map { it.rule })
        assertEquals(Scorecard.Verdict.PASS, Scorecard.judge(plugin("calc-health-left-alone", null, "104 °F. Call a doctor."), topics).verdict)
        assertEquals(Scorecard.Verdict.FAIL, Scorecard.judge(plugin("calc-health-left-alone", "temperature-conversion", "40 °C = 104 °F"), topics).verdict)
    }

    @Test fun `no question rule means to read, a failed answer always fails`() {
        assertEquals(Scorecard.Verdict.TO_READ, Scorecard.judge(row("""{"queryId":"explanation-1","outcome":"success","answer":"..."}"""), topics).verdict)
        assertEquals(Scorecard.Verdict.FAIL, Scorecard.judge(row("""{"queryId":"explanation-1","outcome":"failure","answer":""}"""), topics).verdict)
    }

    @Test fun `old rows find their topic by question id`() {
        assertEquals("core", Scorecard.topicOf(row("""{"queryId":"factual-1"}"""), topics.values))
        assertEquals("portuguese", Scorecard.topicOf(row("""{"queryId":"pt-short-1"}"""), topics.values))
        assertEquals("custom", Scorecard.topicOf(row("""{"queryId":"warmup-000"}"""), topics.values))
    }

    @Test fun `language, citations, numbers, cards and kept short are read from the answer`() {
        val r = TopicRules(language = "pt", citesOnlyRealSources = true, numbers = listOf(NumberRule(8849.0, 0.001)), cards = listOf("chart"), minChartPoints = 3)
        // One enumeration: CardRules reads "K2" there; as a list line ("- K2: 8.611 m") its label can't hold a digit.
        val good = row("""{"query":"Quais são as três montanhas mais altas?","answer":"As três montanhas mais altas do mundo são o Everest com 8.849 m [1], o K2 com 8.611 m e o Kangchenjunga com 8.586 m.","retrievedTitles":["Mount Everest"]}""")
        assertTrue(Scorecard.check(good, r).toString(), Scorecard.check(good, r).all { it.ok == true })
        val bad = row("""{"query":"Quais são as três montanhas mais altas?","answer":"The highest is Everest at 8,000 m [3].","retrievedTitles":["Mount Everest"]}""")
        assertEquals(setOf("language", "citesOnlyRealSources", "numbers", "cards", "minChartPoints"), Scorecard.check(bad, r).filter { it.ok == false }.map { it.rule }.toSet())
        assertEquals(listOf(99.9), Scorecard.numbersIn("Dá 99,9 Wh."))
        assertEquals(listOf(27000.0, 3.7), Scorecard.numbersIn("27.000 mAh a 3,7 V"))
        val short = TopicRules(keptShort = true)
        assertEquals(true, Scorecard.check(row("""{"personalityId":"succinct","answer":"x"}"""), short).single().ok)
        assertEquals(false, Scorecard.check(row("""{"personalityId":"detailed","answer":"x"}"""), short).single().ok)
    }

    @Test fun `a rule an old row can't answer doesn't count`() {
        val r = TopicRules(noFileSources = true)
        assertNull(Scorecard.check(row("""{"answer":"x"}"""), r).single().ok)
        assertEquals(false, Scorecard.check(row("""{"answer":"x","fileSources":["notes.pdf"]}"""), r).single().ok)
        assertEquals(true, Scorecard.check(row("""{"answer":"x","fileSources":[]}"""), r).single().ok)
    }

    @Test fun `the scorecard against the previous run - up, down, same`() {
        val now = listOf(
            plugin("calc-battery-en", "calculator-battery", "5,000 mAh × 3.85 V ÷ 1000 = 19.25 Wh."),
            plugin("calc-conversion", "calculator-conversion", "42 km = 26.1 miles (1 mile = 1.609344 km)."),
            plugin("compass-north", "compass-heading", "Your phone points east."),
        )
        val before = listOf(
            plugin("calc-battery-en", "calculator-battery", "wrong"),
            plugin("calc-conversion", "calculator-conversion", "42 km = 26.1 miles (1 mile = 1.609344 km)."),
            plugin("compass-north", null, "North is that way."),
        )
        val card = Scorecard.of(now, topics, before)
        val line = card.topics.single()
        assertEquals("plugins", line.topic)
        assertEquals(3, line.passed)
        assertEquals(3, line.checked)
        assertEquals(1, line.previousPassed)
        assertEquals(Scorecard.Trend.UP, line.trend)
        assertEquals(Scorecard.Trend.DOWN, Scorecard.of(before, topics, now).topics.single().trend)
        assertEquals(Scorecard.Trend.SAME, Scorecard.of(now, topics, now).topics.single().trend)
        assertNull(Scorecard.of(now, topics).topics.single().trend)
        assertTrue(Scorecard.text(card).contains("plugins      3/3 (was 1/3) up"))
    }

    @Test fun `one line per model and pass - first word, whole answer, peak memory`() {
        val rows = listOf(
            row("""{"queryId":"a","modelId":"m1","style":"succinct","outcome":"success","ttftMs":1000,"totalLatencyMs":8000,"peakRssBytes":2.0e9}"""),
            row("""{"queryId":"b","modelId":"m1","style":"succinct","outcome":"success","ttftMs":3000,"totalLatencyMs":10000,"peakRssBytes":2.5e9}"""),
            row("""{"queryId":"a","modelId":"m1","style":"detailed","path":"deep","outcome":"success","ttftMs":9000,"totalLatencyMs":90000}"""),
        )
        val models = Scorecard.of(rows, topics).models
        assertEquals(listOf("succinct", "deep"), models.map { it.pass })
        assertEquals(2000.0, models[0].firstWordMs!!, 1e-9)
        assertEquals(9000.0, models[0].wholeAnswerMs!!, 1e-9)
        assertEquals(2.5e9, models[0].peakRssBytes!!, 1e-3)
    }
}
