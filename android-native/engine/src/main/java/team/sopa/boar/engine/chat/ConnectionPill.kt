package team.sopa.boar.engine.chat

/**
 * When the chat header shows the connection as a pill ("● ONLINE" / "● OFFLINE") before it folds back to a dot (adam,
 * Nov 11): once when BOAR opens, and again on a real change, read after the connection has held for [SETTLE_MS] so a
 * flapping signal doesn't flash it. Kept for the process, so coming back to the chat or redrawing it doesn't repeat it.
 */
object ConnectionPill {
    const val SHOW_MS = 2_000L
    /** After a tap on the dot: long enough to read it and tap the pill for the details (adam, Nov 13). */
    const val TAP_SHOW_MS = 4_000L
    const val SETTLE_MS = 1_500L

    /** The state the pill last showed this process; null: not shown yet. */
    @Volatile var lastShown: Boolean? = null

    /** Whether the settled state [online] is news: the first one this process, or different from the last shown. */
    fun shouldShow(online: Boolean, last: Boolean? = lastShown): Boolean = last == null || last != online
}
