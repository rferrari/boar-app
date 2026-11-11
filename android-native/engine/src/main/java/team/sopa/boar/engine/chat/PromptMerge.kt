package team.sopa.boar.engine.chat

import team.sopa.boar.engine.routing.QueryContext

/**
 * "Merge" on the queue (adam, Nov 12): the waiting questions become one, answered together in one answer. The model
 * writes that answer as usual, so no extra model run happens now (one at 4 to 5 tokens a second would delay the queue).
 */
object PromptMerge {
    fun of(questions: List<String>): String {
        val qs = questions.map { it.trim() }.filter { it.isNotEmpty() }
        if (qs.size <= 1) return qs.firstOrNull().orEmpty()
        val pt = qs.count(QueryContext::isPortugueseQuestion) * 2 > qs.size
        val head = if (pt) "Responda a estas perguntas juntas, numa só resposta que trate de cada uma:" else "Answer these questions together, in one answer that covers each of them:"
        return head + "\n" + qs.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n")
    }
}
