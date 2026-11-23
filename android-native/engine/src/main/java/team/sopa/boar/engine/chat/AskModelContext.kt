package team.sopa.boar.engine.chat

/**
 * "Ask BOAR instead" on a plugin's answer (adam, Nov 12): the model answers, and reads the plugin's result first, so it
 * explains it instead of working it out again (a compact model got the numbers wrong).
 */
object AskModelContext {
    const val MAX_CHARS = 800

    fun of(pluginAnswer: String): String =
        "A built-in tool already answered this question exactly:\n\"\"\"\n${pluginAnswer.trim().take(MAX_CHARS)}\n\"\"\"\n" +
            "Explain this result to the user in your own words. Keep its numbers as they are; don't redo the calculation."
}
