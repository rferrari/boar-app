package team.sopa.boar.engine.canvas

import java.text.Normalizer

/**
 * What the visual's model reads besides the answer (adam, Nov 10): numbers often sit in earlier answers of the same chat
 * ("population of Brazil", then "and Russia?"). Step one of the visual, with no model: from the last few earlier answers
 * that share the topic, keep the sentences that hold a number, deduplicated and short, each with the question it
 * answered. The model then picks what to draw from the answer and these facts; the grounding check accepts a number
 * from any of them, never one from none.
 */
object VisualContext {
    data class Exchange(val question: String, val answer: String)

    /** [facts]: "question — sentence" lines for the prompt; [sourceText]: the same sentences, for the grounding check. */
    data class Earlier(val facts: List<String>, val sourceText: String) {
        val isEmpty get() = facts.isEmpty()
    }

    const val MAX_EXCHANGES = 5
    const val MAX_FACTS = 12
    const val MAX_CHARS = 1500

    /**
     * The earlier facts for a visual of [current], from [previous] (oldest first, the current exchange not included):
     * only exchanges that share at least two content words with the current question and answer, newest first, then
     * their sentences with a digit, at most [MAX_FACTS] and [MAX_CHARS] in all.
     */
    fun earlier(current: Exchange, previous: List<Exchange>): Earlier {
        val topic = words(current.question + " " + current.answer)
        val seen = HashSet<String>()
        val facts = ArrayList<String>()
        val sentences = ArrayList<String>()
        var chars = 0
        for (e in previous.asReversed().take(MAX_EXCHANGES)) {
            if ((words(e.question + " " + e.answer) intersect topic).size < 2) continue
            for (s in sentencesWithNumbers(e.answer)) {
                val key = fold(s).replace(Regex("\\s+"), " ")
                if (!seen.add(key) || key in fold(current.answer)) continue
                val line = "${e.question.trim().take(80)} — $s"
                if (facts.size >= MAX_FACTS || chars + line.length > MAX_CHARS) break
                facts += line; sentences += s; chars += line.length
            }
        }
        return Earlier(facts, sentences.joinToString("\n"))
    }

    /** The user message for the visual's model: the question, the answer, then the earlier facts when there are any. */
    fun prompt(question: String, answer: String, earlier: Earlier): String = buildString {
        append("QUESTION: ").append(question.take(400)).append("\n\nANSWER:\n").append(answer.take(3000))
        if (!earlier.isEmpty) {
            append("\n\nEARLIER IN THIS CHAT (facts from earlier answers; use them only for the same visual as the ANSWER):\n")
            earlier.facts.forEach { append("- ").append(it).append('\n') }
        }
    }.trimEnd()

    /** Sentences that hold a digit, cut at . ! ? or a line break; citation marks like [3] removed. */
    fun sentencesWithNumbers(text: String): List<String> =
        text.replace(Regex("\\s*\\[\\d{1,3}]"), "").split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim().trimStart('-', '•', '*', ' ') }.filter { s -> s.length in 8..300 && s.any(Char::isDigit) }

    private val STOP = setOf("the", "and", "for", "with", "from", "that", "this", "what", "how", "why", "which", "are", "was", "were", "about",
        "de", "da", "do", "das", "dos", "para", "com", "por", "que", "qual", "como", "uma", "mais", "and", "also")

    private fun fold(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    private fun words(s: String): Set<String> =
        Regex("\\p{L}{4,}").findAll(fold(s)).map { it.value.removeSuffix("s") }.filter { it !in STOP }.toSet()
}
