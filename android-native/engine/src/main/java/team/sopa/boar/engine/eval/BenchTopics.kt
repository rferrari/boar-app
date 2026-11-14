package team.sopa.boar.engine.eval

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A topic of bench questions (adam, Nov 14: "make categories, so we can test comparison, research, quick questions,
 * calculators... a smaller generic one, another with the most problematic questions"): one versioned file in
 * assets/boar-data/topics/<id>.v<version>.json. t9 reviews each file before its numbers count ([status]).
 *
 * A question either names one of the fixed set's (eval-set.json, [TopicQuestion.fromSet]) and runs it exactly as it is
 * there, or brings its own text. Its [TopicRules] say what the computer can check; [TopicQuestion.toRead] says what
 * right looks like for a person.
 */
@Serializable
data class BenchTopic(
    val id: String,
    val version: Int,
    /** "draft, for t9's review" until t9 signs it off. */
    val status: String = "",
    val title: String,
    val titlePt: String? = null,
    val about: String = "",
    /** The Boar Labs plugins that answer and check while this topic runs; null is the labs switched on in the app. */
    val labs: List<String>? = null,
    /** At the complete level its questions also run through Deep Research (adam, Nov 14: "complete ... and deep research"). */
    val deepResearch: Boolean = false,
    val questions: List<TopicQuestion>,
) {
    /** The set version its own questions' rows carry: "plugins-v1". */
    val setVersion get() = "$id-v$version"

    /** Its questions as the runner takes them; one from the fixed set is that question unchanged (ids, text, set version). */
    fun queries(sets: EvalSets): List<EvalQuery> {
        val fixed = (sets.evalSet + sets.extraSets).associateBy { it.id }
        return questions.map { q ->
            if (q.fromSet) fixed[q.id] ?: throw IllegalArgumentException("topic $id: ${q.id} is not in the fixed set")
            else EvalQuery(q.id, q.category ?: id, q.query ?: throw IllegalArgumentException("topic $id: ${q.id} has no query"), setVersion = setVersion)
        }
    }

    fun question(id: String) = questions.firstOrNull { it.id == id }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): BenchTopic = json.decodeFromString(serializer(), text)

        /** "plugins.v1.json" -> ("plugins", 1); null for any other name. */
        fun fileName(name: String): Pair<String, Int>? =
            Regex("""^([a-z][a-z0-9-]*)\.v(\d+)\.json$""").matchEntire(name)?.let { it.groupValues[1] to it.groupValues[2].toInt() }

        /** The newest version of each topic among [names]: the file names to read. */
        fun newest(names: List<String>): List<String> =
            names.mapNotNull { n -> fileName(n)?.let { n to it } }.groupBy { it.second.first }.values
                .map { v -> v.maxBy { it.second.second }.first }.sorted()
    }
}

@Serializable
data class TopicQuestion(
    val id: String,
    val query: String? = null,
    val category: String? = null,
    /** The fixed set's question with this id, as it is there. */
    val fromSet: Boolean = false,
    val rules: TopicRules? = null,
    /** What right looks like, for the person reading the answer. */
    val toRead: String = "",
    /**
     * Today fails this, and a fix is known (a board card or a brief): its failure shows as "known", apart from a
     * regression (t9 1058: "a rule must say what right looks like, never what happens today").
     */
    val knownIssue: String? = null,
    /** Asked only with this plugin on (Polyglot is off by default): skipped, and said so, when the run had it off. */
    val needsLab: String? = null,
)

/**
 * What the computer checks on one answer (adam, Nov 14: "we could have a score and topics. like, bad on this, good on
 * those"). Every rule set must hold for a pass; an answer with no rule is "to read".
 */
@Serializable
data class TopicRules(
    /** The plugin that must answer: its receipt code ("calculator-battery"), from the row's tool or reason codes. */
    val tool: String? = null,
    /** No plugin answers it: the model does (a health question a calculator must leave alone). */
    val noTool: Boolean = false,
    /** The plugin's exact text. */
    val answerIs: String? = null,
    val answerStartsWith: String? = null,
    /** Each must be in the answer (case and accents as written, case ignored). */
    val answerHas: List<String> = emptyList(),
    /** At least one of these must be in the answer (spellings of one fact: "Canberra"). */
    val answerHasAny: List<String> = emptyList(),
    val answerHasNot: List<String> = emptyList(),
    /** "en" or "pt": the language the answer is clearly in (AnswerLanguage.clear). */
    val language: String? = null,
    /** No [n] without a real source: every [n] is one of the sources the answer had. */
    val citesOnlyRealSources: Boolean = false,
    /** Sources whose titles must not be among the answer's (the bread that cited "Cutlet"). */
    val sourcesNot: List<String> = emptyList(),
    /** None of the user's own files among the sources (the Portuguese mountains that cited the user's file). */
    val noFileSources: Boolean = false,
    /** Numbers read right: each must appear in the answer, within its tolerance. */
    val numbers: List<NumberRule> = emptyList(),
    /** At least one of these: one fact in several units ("5,895 m" or "19,341 ft"). */
    val numbersAny: List<NumberRule> = emptyList(),
    /** Known wrong numbers that must not appear. */
    val numbersNot: List<NumberRule> = emptyList(),
    /** The cards CardRules.find must find: "steps", "chart", "table", "facts", "compare". */
    val cards: List<String> = emptyList(),
    /** A chart card with at least this many points. */
    val minChartPoints: Int? = null,
    /** Kept short: answered in Succinct whatever the run's style (ShortAnswer: health, seed phrases, small talk). */
    val keptShort: Boolean = false,
) {
    val isEmpty get() = this == TopicRules()
}

/** A number and how far off it may be, as a share of it (0.01 is 1 %). */
@Serializable
data class NumberRule(val value: Double, val tolerance: Double = 0.01, val label: String = "")
