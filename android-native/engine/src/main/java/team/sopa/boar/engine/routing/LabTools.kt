package team.sopa.boar.engine.routing

import team.sopa.boar.engine.answer.AnswerText
import team.sopa.boar.engine.rag.Facts

/**
 * The Boar Labs answer plugins' decisions, pure: which question each tool takes (with the language and health guards
 * that keep it off the rest), what Boar Guard repairs, the order the tool slot tries them in, and when the check slot
 * runs. The lab objects in app/src/labs/plugins wrap these; LabToolsCombinationTest runs them together.
 */
object LabTools {
    /** [tool]: the receipt code ("calculator-battery", "facts-population", "verdict-guard-date"); [text]: the answer. */
    data class Hit(val tool: String, val text: String)

    private fun notEnglish(q: String) = QueryContext.isPortugueseQuestion(q) || QueryContext.isSpanishQuestion(q)

    /** A seed phrase or private key anywhere in the question: an instant fact or distance would drop that half of it. */
    private fun seed(q: String) = QueryContext.isSeedPhraseQuestion(q)

    /** Boar Calculator: CalcShortcut, with its own NumGuard lexicon over this and the earlier user turns. */
    fun calc(query: String, userTurns: List<String>): Hit? = CalcShortcut.answer(query, userTurns)?.let { Hit(it.code, it.text) }

    /** Boar Distance: English only, not a seed phrase; NumGuard's health lexicon over this and the earlier user turns keeps it off health. */
    fun distance(table: TravelDistance?, query: String, userTurns: List<String>): Hit? {
        if (notEnglish(query) || seed(query) || (listOf(query) + userTurns).any(NumGuard::healthLexicon)) return null
        return table?.answer(query)?.let { Hit("calculator-travel-distance", it.text) }
    }

    /** Boar Facts: English only, not a seed phrase; QueryContext's health detector over this and the earlier user turns keeps it off health. */
    fun facts(table: Facts?, query: String, userTurns: List<String>): Hit? {
        if (notEnglish(query) || seed(query) || (listOf(query) + userTurns).any(QueryContext::isHealthQuestion)) return null
        return table?.answer(query)?.let { Hit("facts-${it.template}", it.text) }
    }

    /** Boar Cohazard: English planning questions about a fuel-burning device in an enclosed space. */
    fun coHazard(query: String): Hit? =
        if (notEnglish(query) || !CoHazard.isCoHazardQuestion(query)) null else Hit("safety-fact-co-hazard", CoHazard.coHazardAnswer(query))

    /** Boar Guard: English, not health, no thinking block; else VerdictGuard's repair. */
    fun guard(question: String, answer: String): Hit? {
        if (notEnglish(question) || QueryContext.isHealthQuestion(question)) return null
        if (AnswerText.splitThinking(answer).let { it.thinking != null || it.thinkingInProgress }) return null
        return VerdictGuard.check(question, answer)?.let { Hit("verdict-guard-${it.kind}", it.text) }
    }

    /** Safety answers before the rest: Boar Cohazard's CO answer must win over a calculation or a fact in the same
     * question ("How long can I run a propane heater in the camper at 0.5 kg per hour?"). Grid order within a rank. */
    fun slotRank(labId: String): Int = if (labId == "cohazard") 0 else 1

    /** The first item with an answer; one that throws is skipped. Both slots dispatch through this. */
    fun <T, R : Any> first(items: List<T>, run: (T) -> R?): R? = items.firstNotNullOfOrNull { runCatching { run(it) }.getOrNull() }

    /** The check slot reads only a model's finished answer: not an instant tool answer, a stopped, timed-out or failed one,
     * an empty one, or one written by Deep Research or a recap ([path] other than null or "adaptive"). */
    fun checksAnswer(instant: Boolean, stopped: Boolean, timedOut: Boolean, failed: Boolean, path: String?, blank: Boolean): Boolean =
        !instant && !stopped && !timedOut && !failed && (path == null || path == "adaptive") && !blank
}
