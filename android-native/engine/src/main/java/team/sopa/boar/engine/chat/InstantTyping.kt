package team.sopa.boar.engine.chat

/**
 * A plugin's instant answer shown at typing speed (adam, Nov 12: so fast that nothing seemed to change as the keyboard
 * closed). Presentation only: the receipt keeps the real time and says "instant", never a speed. Not for the safety
 * answer (shown at once), nor with animations off or a screen reader on.
 */
object InstantTyping {
    /** About 20 tokens a second. */
    const val CHARS_PER_SECOND = 80
    const val STEP_MS = 50L
    /** A long answer still ends within this. */
    const val MAX_MS = 6_000L

    fun typed(tool: String, animationsOff: Boolean, screenReader: Boolean): Boolean =
        !tool.startsWith("safety-") && !animationsOff && !screenReader

    /** The text in pieces, one per [STEP_MS], ending within [MAX_MS] (longer answers type faster), whole words kept together where they fit. */
    fun pieces(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val steps = maxOf(1, minOf((text.length * 1000L / CHARS_PER_SECOND / STEP_MS).toInt(), (MAX_MS / STEP_MS).toInt()))
        val size = (text.length + steps - 1) / steps
        return text.chunked(maxOf(1, size))
    }
}
