package team.sopa.boar.engine.chat

import team.sopa.boar.engine.routing.QueryContext

/**
 * "Merge" on the queue (adam, Nov 12): the waiting questions become one, answered together in one answer. The model
 * writes that answer as usual, so no extra model run happens now (one at 4 to 5 tokens a second would delay the queue).
 * A merged question goes to the model, never to a plugin's instant answer, which would answer one part and drop the
 * others (validator Q-8; the chat's send checks [isMerged]).
 */
object PromptMerge {
    private const val EN = "Answer these questions together, in one answer that covers each of them:"
    private const val PT = "Responda a estas perguntas juntas, numa só resposta que trate de cada uma:"
    private const val ES = "Responde a estas preguntas juntas, en una sola respuesta que trate cada una:"
    private val HEADS = listOf(EN, PT, ES)
    private val NUMBERED = Regex("^\\d+\\. ")

    fun of(questions: List<String>): String {
        // Merging a merged question again keeps one flat list, not a list inside a list (validator Q-9).
        val qs = questions.flatMap(::parts).map { it.trim() }.filter { it.isNotEmpty() }
        if (qs.size <= 1) return qs.firstOrNull().orEmpty()
        val head = when {
            // Half Portuguese and none Spanish: the Portuguese head (validator Q-9: a PT pair with one miss got English).
            qs.count(QueryContext::isPortugueseQuestion).let { n -> n * 2 > qs.size || (n * 2 == qs.size && qs.none(QueryContext::isSpanishQuestion)) } -> PT
            qs.count(QueryContext::isSpanishQuestion) * 2 > qs.size -> ES
            else -> EN
        }
        return head + "\n" + qs.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n")
    }

    fun isMerged(text: String): Boolean = HEADS.any { text.trimStart().startsWith(it) }

    /** The questions inside a merged text; the text itself otherwise. */
    fun parts(text: String): List<String> =
        if (!isMerged(text)) listOf(text) else text.trim().lines().drop(1).map { it.trim().replace(NUMBERED, "") }.filter { it.isNotEmpty() }
}
