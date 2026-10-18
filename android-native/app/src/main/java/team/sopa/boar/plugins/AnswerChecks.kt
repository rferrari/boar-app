package team.sopa.boar.plugins

import team.sopa.boar.engine.routing.LabTools

/**
 * The check slot: a Boar Labs plugin reads a model's finished answer with its question and may return a repaired text
 * (a verdict its own facts contradict). Only the chat's default and adaptive paths call it, after generation and before
 * the answer is saved; never instant tool answers, Deep Research or answers about the conversation. With no check,
 * nothing changes.
 */
fun interface AnswerCheck {
    /** Null: the answer stays as the model wrote it. */
    fun check(question: String, answer: String): Repair?
}

/** [tool]: short and stable, kept in the answer's receipt ("verdict-guard-date"); [text]: the answer that replaces it. */
data class Repair(val tool: String, val text: String)

object AnswerChecks {
    /** Set by Boar Labs at start: the checks of the labs switched on now, in grid order. */
    @Volatile var checks: () -> List<AnswerCheck> = { emptyList() }

    /** The first check with a repair; one that throws is skipped, as a tool is. */
    fun run(question: String, answer: String): Repair? =
        LabTools.first(runCatching { checks() }.getOrDefault(emptyList())) { it.check(question, answer) }
}
