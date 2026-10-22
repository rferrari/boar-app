package team.sopa.boar.engine.answer

import team.sopa.boar.engine.rag.RetrievalParams

/**
 * The Personal Files answer style ("personal"): answers only from the user's own files (Knowledge → Personal Files).
 * The one place the chat (ChatViewModel) and the device runner (DeviceEvalRunner) decide what the style changes, so a
 * benchmark run answers as the chat does. The owner's choices (Oct 21): nothing found means the fixed line, never an
 * answer from memory; Labs tools step aside except the safety cards; no Deep Research, adaptive routing or answer check.
 */
object PersonalFiles {
    const val STYLE = "personal"

    /** The answer when nothing in the user's files matched (the chat shows its own language's line). */
    const val NOT_FOUND = "I couldn't find this in your Personal Files."

    fun isOn(style: String?): Boolean = style == STYLE

    /** A Labs tool's answer is kept only from a safety card (`safety-*`), as under Deep Research. */
    fun keepsTool(toolId: String): Boolean = toolId.startsWith("safety-")

    /** [base] (the user's or the run's ranking) kept to the user's own files. */
    fun retrieval(base: RetrievalParams = RetrievalParams.DEFAULT): RetrievalParams = base.copy(ownOnly = true)

    /**
     * [base] as this style sends it: whole parts within [contextSize] (the context the model was opened with), the
     * files-only instruction, and [notFound] written without the model when nothing is found.
     */
    fun request(base: AnswerRequest, contextSize: Int, notFound: String = NOT_FOUND): AnswerRequest =
        base.copy(filesOnly = true, contextSize = contextSize, notFoundText = notFound)

    /** An answer this long after the not-found line is a real answer, not a sign-off. */
    const val ANSWER_AFTER_HEDGE_CHARS = 40
    private val LEAD = Regex("^(however|but|that said|still|nevertheless)[,:]?\\s+", RegexOption.IGNORE_CASE)

    /**
     * A small model sometimes opens with [NOT_FOUND] "to be safe" and then answers from the files anyway ("I couldn't find
     * this in your Personal Files. According to your blood test…", F3, Oct 22): one or the other, never both. The line goes
     * when a real answer follows it; an answer that is only the line, or the line and a short sign-off, stays as it is.
     */
    fun dropHedge(answer: String): String {
        val t = answer.trimStart()
        if (!t.startsWith(NOT_FOUND)) return answer
        val rest = t.removePrefix(NOT_FOUND).trimStart()
        if (rest.length < ANSWER_AFTER_HEDGE_CHARS) return answer
        val body = rest.replaceFirst(LEAD, "")
        return body.replaceFirstChar { it.uppercaseChar() }
    }
}
