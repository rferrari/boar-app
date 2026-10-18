package team.sopa.boar.engine.routing

/**
 * Why a question picked under Detailed is answered in Succinct instead: "health" (first aid, symptoms) or "seed" (seed
 * phrases, wallet backups), else null. A follow-up in such a chat counts too ("and for how long?" after a burn
 * question): it doesn't name the topic, but the answer is still first aid (r4to, Oct 18).
 */
object ShortAnswer {
    fun reason(chosenStyle: String, question: String, previousQuestion: String?): String? {
        if (chosenStyle != "detailed") return null
        return reasonFor(question) ?: previousQuestion?.let(::reasonFor)
    }

    private fun reasonFor(q: String): String? = when {
        QueryContext.isHealthQuestion(q) -> "health"
        QueryContext.isSeedPhraseQuestion(q) -> "seed"
        else -> null
    }
}
