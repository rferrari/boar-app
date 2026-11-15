package team.sopa.boar.engine.answer

import team.sopa.boar.engine.routing.ShortAnswer

/**
 * The line under a model's answer when nothing in the library backs it (t9 0110 item 6, 0226 point 4; the texts are
 * t9's and adam may change them): the search ran and found nothing, no plugin answered, and the answer cites nothing.
 * Never under small talk ("hey, what's up?" isn't looked up) or a plugin's instant answer.
 */
object NoSource {
    private val CITE = Regex("""\[\d{1,3}]""")

    fun shows(searched: Boolean, sources: Int, instant: Boolean, question: String, answer: String): Boolean =
        searched && sources == 0 && !instant && answer.isNotBlank() && !CITE.containsMatchIn(answer) && !ShortAnswer.isSmallTalk(question)
}
