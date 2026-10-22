package team.sopa.boar.engine.routing

/**
 * Why a question picked under Detailed is answered in Succinct instead: "health" (first aid, symptoms) or "seed" (seed
 * phrases, wallet backups), else null. A follow-up in such a chat counts too ("and for how long?" after a burn
 * question): it doesn't name the topic, but the answer is still first aid (r4to, Oct 18).
 */
object ShortAnswer {
    /** A thank-you or greeting ("thanks, that helps"): a short reply, with no note under it. */
    const val SMALL_TALK = "smalltalk"

    fun reason(chosenStyle: String, question: String, previousQuestion: String?): String? {
        if (chosenStyle != "detailed") return null
        // Before the follow-up check: "thanks" after a burn question is small talk, not first aid.
        if (isSmallTalk(question)) return SMALL_TALK
        return reasonFor(question) ?: previousQuestion?.let(::reasonFor)
    }

    /** Small talk answers in Succinct, at most its 512 tokens, not a Detailed essay (Oct 18, X6). */
    fun isSmallTalk(question: String): Boolean = Classify.classifyTask(question) == TaskType.GREETING

    private fun reasonFor(q: String): String? = when {
        QueryContext.isHealthQuestion(q) -> "health"
        QueryContext.isSeedPhraseQuestion(q) -> "seed"
        else -> null
    }
}
