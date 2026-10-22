package team.sopa.boar.engine.settings

/** An answer that ended at its token limit rather than by itself, and the next limit to retry it with. */
object AnswerLimit {
    /** Streamed pieces can count a token or two short of what the engine generated (bytes merged into one piece). */
    fun reached(generated: Int, limit: Int): Boolean = limit > 0 && generated >= limit - 2

    /** The next length in Settings above [limit] that still leaves room for the prompt; null when none does. */
    fun next(limit: Int, options: List<Int>, openedContext: Int?): Int? =
        options.sorted().firstOrNull { it > limit && (openedContext == null || it <= openedContext - AppSettings.PROMPT_RESERVE) }
}
