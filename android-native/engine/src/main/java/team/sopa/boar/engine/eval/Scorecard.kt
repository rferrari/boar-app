package team.sopa.boar.engine.eval

import kotlinx.serialization.json.jsonPrimitive

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import team.sopa.boar.engine.answer.AnswerLanguage
import team.sopa.boar.engine.canvas.CardRules
import team.sopa.boar.engine.eval.BenchPresets.num
import team.sopa.boar.engine.eval.BenchPresets.str
import team.sopa.boar.engine.routing.Calculators
import kotlin.math.abs

/**
 * The scorecard at the end of every run (adam, Nov 14: "we could have a score and topics. like, bad on this, good on
 * those"; t9 0521 item 4): one line per topic, passed over checked by its rules, next to the previous run of the same
 * level and topic (up, down, same); one line per model and pass with its first word, whole answer and peak memory.
 * The computer scores only what a rule can check; the rest is "to read". Pure: rows in, scorecard out.
 */
object Scorecard {
    /** KNOWN: failed, with a known issue (TopicQuestion.knownIssue); SKIPPED: its plugin was off in that run. */
    enum class Verdict { PASS, FAIL, TO_READ, KNOWN, SKIPPED }
    enum class Trend { UP, DOWN, SAME }

    /** One rule on one answer: [ok] null when the row can't tell (an old run without the field). */
    data class Check(val rule: String, val ok: Boolean?, val detail: String = "")

    data class RowResult(
        val queryId: String, val topic: String, val modelId: String?, val pass: String,
        val verdict: Verdict, val checks: List<Check>, val toRead: String,
    )

    data class TopicLine(
        val topic: String, val passed: Int, val checked: Int, val toRead: Int, val total: Int,
        /** The previous run's passed and checked for this topic, when it ran it. */
        val previousPassed: Int? = null, val previousChecked: Int? = null, val trend: Trend? = null,
        /** Failures with a known issue, and questions skipped (their plugin off): counted apart from [checked]. */
        val known: Int = 0, val skipped: Int = 0,
    )

    data class ModelLine(
        val modelId: String, val pass: String, val answers: Int,
        /** Medians: the first word, the whole answer. */
        val firstWordMs: Double?, val wholeAnswerMs: Double?, val peakRssBytes: Double?,
    )

    data class Card(val topics: List<TopicLine>, val models: List<ModelLine>, val rows: List<RowResult>)

    /**
     * A row's topic: the one it says (rows since presets carry it), else the first topic holding its question (old runs:
     * the fixed set's ids are "core", the language set's "portuguese"), else "custom".
     */
    fun topicOf(row: JsonObject, topics: Collection<BenchTopic>): String {
        row.str("topic")?.let { return it }
        val id = row.str("queryId")
        return topics.sortedBy { if (it.id == BenchPresets.CORE) 0 else 1 }.firstOrNull { t -> t.question(id ?: "") != null }?.id ?: "custom"
    }

    fun judge(row: JsonObject, topics: Map<String, BenchTopic>): RowResult {
        val topic = topicOf(row, topics.values)
        val q = topics[topic]?.question(row.str("queryId").orEmpty())
        val checks = ArrayList<Check>()
        // Its plugin off in this run (rows say which were on): not asked, said so.
        q?.needsLab?.let { lab ->
            val on = (row["labsOn"] as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content }
            if (on == null || lab !in on) return RowResult(row.str("queryId").orEmpty(), topic, row.str("modelId"), BenchPresets.passKey(row), Verdict.SKIPPED,
                listOf(Check("needsLab", null, "$lab off")), q.toRead)
        }
        val outcome = row.str("outcome")
        if (outcome != "success") checks += Check("answered", false, outcome ?: "no outcome")
        q?.rules?.takeIf { !it.isEmpty }?.let { checks += check(row, it) }
        val known = checks.filter { it.ok != null }
        val verdict = when {
            known.any { it.ok == false } && q?.knownIssue != null -> Verdict.KNOWN
            known.any { it.ok == false } -> Verdict.FAIL
            known.isEmpty() -> Verdict.TO_READ
            else -> Verdict.PASS
        }
        return RowResult(row.str("queryId").orEmpty(), topic, row.str("modelId"), BenchPresets.passKey(row), verdict, checks, q?.toRead.orEmpty())
    }

    private val CITATION = Regex("""\[(\d{1,3})]""")
    private val NUMBER = Regex("""(?<![\d.,])\d+(?:[.,]\d+)*""")

    /** The numbers in [text], read the way the calculators read them (its own marks first, then its language). */
    fun numbersIn(text: String): List<Double> {
        val t = text.replace(' ', ' ').replace(' ', ' ')
            .replace(Regex("""(?<=\d) (?=\d{3}\b)"""), "") // "8 849 m": a space between thousands
        val pt = AnswerLanguage.clear(t) == AnswerLanguage.Lang.PT
        val comma = Calculators.readsComma(t, pt)
        return NUMBER.findAll(t).mapNotNull { m -> runCatching { Calculators.parseNumber(m.value.trimEnd('.', ','), comma) }.getOrNull()?.takeIf { it.isFinite() } }.toList()
    }

    private fun near(n: Double, rule: NumberRule) = abs(n - rule.value) <= abs(rule.value) * rule.tolerance + 1e-9

    private fun cardName(c: CardRules.Card) = when (c) {
        is CardRules.Card.Steps -> "steps"
        is CardRules.Card.Table -> "table"
        is CardRules.Card.Facts -> "facts"
        is CardRules.Card.Compare -> "compare"
        is CardRules.Card.Chart -> "chart"
    }

    fun check(row: JsonObject, r: TopicRules): List<Check> {
        val answer = row.str("answer").orEmpty()
        val lower = answer.lowercase()
        val out = ArrayList<Check>()
        r.tool?.let { want ->
            val tools = row.str("tool")?.split('+').orEmpty()
            val codes = row.list("reasonCodes")
            out += Check("tool", want in tools || "answer:$want" in codes, row.str("tool") ?: "no plugin answered")
        }
        if (r.noTool) out += Check("noTool", row.str("tool") == null && row.list("reasonCodes").none { it.startsWith("answer:") }, row.str("tool") ?: "the model answered")
        r.answerIs?.let { out += Check("answerIs", answer.trim() == it.trim(), answer.take(80)) }
        r.answerStartsWith?.let { out += Check("answerStartsWith", answer.trimStart().startsWith(it), answer.take(80)) }
        for (s in r.answerHas) out += Check("answerHas", s.lowercase() in lower, s)
        if (r.answerHasAny.isNotEmpty()) out += Check("answerHasAny", r.answerHasAny.any { it.lowercase() in lower }, r.answerHasAny.joinToString(" / "))
        for (g in r.answerHasEach) if (g.isNotEmpty()) out += Check("answerHasEach", g.any { it.lowercase() in lower }, g.joinToString(" / "))
        for (s in r.answerHasNot) out += Check("answerHasNot", s.lowercase() !in lower, s)
        r.language?.let { want ->
            val got = AnswerLanguage.clear(answer)?.name?.lowercase()
            out += Check("language", got == want.lowercase(), got ?: "unclear")
        }
        val sources = row.list("retrievedTitles")
        if (r.citesOnlyRealSources) {
            val cited = CITATION.findAll(answer).map { it.groupValues[1].toInt() }.toSet()
            val bad = cited.filter { it < 1 || it > sources.size }
            out += Check("citesOnlyRealSources", bad.isEmpty(), if (bad.isEmpty()) "${cited.size} cited of ${sources.size}" else "[${bad.joinToString("], [")}] with ${sources.size} sources")
        }
        for (s in r.sourcesNot) out += Check("sourcesNot", sources.none { it.contains(s, ignoreCase = true) }, s)
        if (r.noFileSources) {
            // Rows since presets list the user's own files among the sources (fileSources); an older row can't tell.
            val files = row["fileSources"] as? JsonArray
            out += Check("noFileSources", files?.isEmpty(), files?.joinToString { (it as? JsonPrimitive)?.contentOrNull.orEmpty() } ?: "not recorded")
        }
        if (r.numbers.isNotEmpty() || r.numbersAny.isNotEmpty() || r.numbersNot.isNotEmpty()) {
            val found = numbersIn(answer)
            for (n in r.numbers) out += Check("numbers", found.any { near(it, n) }, n.label.ifEmpty { n.value.toString() })
            if (r.numbersAny.isNotEmpty()) out += Check("numbersAny", r.numbersAny.any { n -> found.any { near(it, n) } },
                r.numbersAny.joinToString(" / ") { it.label.ifEmpty { it.value.toString() } })
            for (n in r.numbersNot) out += Check("numbersNot", found.none { near(it, n) }, n.label.ifEmpty { n.value.toString() })
        }
        if (r.cards.isNotEmpty() || r.minChartPoints != null) {
            val cards = CardRules.find(row.str("query").orEmpty(), answer)
            val names = cards.map(::cardName)
            for (c in r.cards) out += Check("cards", c in names, "$c in [${names.joinToString()}]")
            r.minChartPoints?.let { min ->
                val points = cards.filterIsInstance<CardRules.Card.Chart>().maxOfOrNull { it.points.size } ?: 0
                out += Check("minChartPoints", points >= min, "$points points")
            }
        }
        if (r.keptShort) out += Check("keptShort", row.str("personalityId") == "succinct", row.str("personalityId") ?: "?")
        return out
    }

    fun of(rows: List<JsonObject>, topics: Map<String, BenchTopic>, previous: List<JsonObject>? = null): Card {
        val results = rows.map { judge(it, topics) }
        val before = previous?.map { judge(it, topics) }?.groupBy { it.topic }
        val lines = results.groupBy { it.topic }.map { (topic, rs) ->
            val passed = rs.count { it.verdict == Verdict.PASS }
            // Checked = pass or fail; known failures and skipped questions are counted apart, so the trend shows regressions.
            val checked = rs.count { it.verdict == Verdict.PASS || it.verdict == Verdict.FAIL }
            val prev = before?.get(topic)
            val pPassed = prev?.count { it.verdict == Verdict.PASS }
            val pChecked = prev?.count { it.verdict == Verdict.PASS || it.verdict == Verdict.FAIL }
            TopicLine(topic, passed, checked, rs.count { it.verdict == Verdict.TO_READ }, rs.size, pPassed, pChecked, trend(passed, checked, pPassed, pChecked),
                known = rs.count { it.verdict == Verdict.KNOWN }, skipped = rs.count { it.verdict == Verdict.SKIPPED })
        }
        val models = rows.groupBy { (it.str("modelId") ?: it.str("configId").orEmpty()) to BenchPresets.passKey(it) }.map { (k, rs) ->
            val done = rs.filter { it.str("outcome") == "success" }
            ModelLine(k.first, k.second, rs.size, EvalScore.median(done.mapNotNull { it.num("ttftMs") }),
                EvalScore.median(done.mapNotNull { it.num("totalLatencyMs") }), rs.mapNotNull { it.num("peakRssBytes") }.maxOrNull())
        }
        return Card(lines, models, results)
    }

    /** Up, down or same by the share passed; null when either run checked nothing in the topic. */
    fun trend(passed: Int, checked: Int, prevPassed: Int?, prevChecked: Int?): Trend? {
        if (prevPassed == null || prevChecked == null || prevChecked == 0 || checked == 0) return null
        val now = passed.toDouble() / checked
        val then = prevPassed.toDouble() / prevChecked
        return when {
            abs(now - then) < 1e-9 -> Trend.SAME
            now > then -> Trend.UP
            else -> Trend.DOWN
        }
    }

    /** The scorecard as text, for the script and the log: "plugins  9/10 (was 8/10) up · 0 to read". */
    fun text(card: Card): String = buildString {
        for (t in card.topics) {
            append(t.topic.padEnd(12)).append(' ').append("${t.passed}/${t.checked}")
            if (t.previousChecked != null) append(" (was ${t.previousPassed}/${t.previousChecked})")
            t.trend?.let { append(' ').append(it.name.lowercase()) }
            append(" · ${t.toRead} to read")
            if (t.known > 0) append(" · ${t.known} known issues")
            if (t.skipped > 0) append(" · ${t.skipped} skipped (plugin off)")
            append('\n')
        }
        for (m in card.models) {
            append("${m.modelId} · ${m.pass}: ${m.answers} answers, first word ${secs(m.firstWordMs)}, whole answer ${secs(m.wholeAnswerMs)}, peak ")
            append(m.peakRssBytes?.let { String.format(java.util.Locale.US, "%.2f GB", it / 1e9) } ?: "—").append('\n')
        }
    }

    private fun secs(ms: Double?) = ms?.let { String.format(java.util.Locale.US, "%.1f s", it / 1000) } ?: "—"

    private fun JsonObject.list(k: String) = (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
}
