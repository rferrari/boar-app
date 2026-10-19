package team.sopa.boar.engine.settings

/**
 * Settings → "Free memory when I leave BOAR": a while after BOAR leaves the screen, the model (3-6 GB) is unloaded.
 * The next answer loads it again. The rules are here, apart from Android, so they're tested.
 */
object FreeMemory {
    /**
     * Auto's delay, in seconds. Was 5 minutes: Xiaomi's memory manager force-stopped BOAR 10-35 s after it left the screen
     * with Qwen3 4B loaded (F3 and X6, Oct 19 chaos runs), so the model goes before that.
     */
    const val AUTO_SECONDS = 10
    /** Advanced tweaks → "Free memory after": Immediately, 5 min, 30 min (Auto is null). */
    val CHOICES_MINUTES = listOf(0, 5, 30)
    /** How often a timer that fired during an answer looks again. */
    const val BUSY_POLL_MS = 2_000L
    /** How often Auto looks at the phone's free memory while it waits. */
    const val AUTO_CHECK_MS = 1_000L

    /**
     * Auto doesn't wait out its delay when the phone is short of memory: Android says memory is low, or less than a sixth
     * of the RAM is free. X6, Oct 19 chaos run: Android closed BOAR for low memory 4 s after Home, idle with a 1.5 GB model.
     */
    fun shortOfMemory(availBytes: Long, totalBytes: Long, androidSaysLow: Boolean): Boolean =
        androidSaysLow || (totalBytes > 0 && availBytes in 0 until totalBytes / 6)

    /** The delay for the user's choice in minutes: Auto (null) is [AUTO_SECONDS]; a negative value counts as Immediately. */
    fun delayMs(minutes: Int?): Long = if (minutes == null) AUTO_SECONDS * 1_000L else minutes.coerceAtLeast(0) * 60_000L

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
