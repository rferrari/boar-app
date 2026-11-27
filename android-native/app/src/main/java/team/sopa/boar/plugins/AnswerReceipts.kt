package team.sopa.boar.plugins

/**
 * Work a plugin does for an answer after it was saved, on its receipt: "draw me a cat" is answered at once by Designer,
 * and the drawing under it takes 15 s more; the receipt said 0.0 s (adam, F3, 26 Nov). Its time joins the answer's total
 * and shows as its own row. Set by the chat while it is open; [NONE] otherwise.
 */
object AnswerReceipts {
    val NONE: (answerId: String, ms: Double, stage: String) -> Unit = { _, _, _ -> }

    /** Adds [ms] to answer [answerId]'s receipt, as [stage] ("drawing"), saved with the answer. */
    @Volatile var addTime: (answerId: String, ms: Double, stage: String) -> Unit = NONE
}
