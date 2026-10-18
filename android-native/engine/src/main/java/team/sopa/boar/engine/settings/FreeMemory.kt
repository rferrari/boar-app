package team.sopa.boar.engine.settings

/**
 * Settings → "Free memory when I leave BOAR": a while after BOAR leaves the screen, the model (3-6 GB) is unloaded.
 * The next answer loads it again. The rules are here, apart from Android, so they're tested.
 */
object FreeMemory {
    /** Auto's delay, in minutes. */
    const val AUTO_MINUTES = 5
    /** Advanced tweaks → "Free memory after": Immediately, 5 min, 30 min (Auto is null). */
    val CHOICES_MINUTES = listOf(0, 5, 30)
    /** How often a timer that fired during an answer looks again. */
    const val BUSY_POLL_MS = 2_000L

    /** The delay for the user's choice: Auto (null) is [AUTO_MINUTES]; a negative value counts as Immediately. */
    fun delayMs(minutes: Int?): Long = (minutes ?: AUTO_MINUTES).coerceAtLeast(0) * 60_000L

    enum class Decision { UNLOAD, WAIT, SKIP }

    /**
     * When the timer fires: nothing to do if the setting is off, BOAR is back on screen or no model is loaded; an answer,
     * a chat title or summary, or a load still running is waited for; otherwise the model goes.
     */
    fun decide(enabled: Boolean, inForeground: Boolean, loaded: Boolean, busy: Boolean): Decision = when {
        !enabled || inForeground -> Decision.SKIP
        // Busy first: a model still loading isn't "loaded" yet, and skipping would leave it in memory once it is.
        busy -> Decision.WAIT
        !loaded -> Decision.SKIP
        else -> Decision.UNLOAD
    }
}
