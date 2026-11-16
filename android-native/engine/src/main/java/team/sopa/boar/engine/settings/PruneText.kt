package team.sopa.boar.engine.settings

/**
 * The text of the two "delete your oldest chats" dialogs (Settings' lower limit, and a new chat past the limit): the
 * chats named, then, while Boar Labs is on, what goes with their answers (validator Q-B2; t9 0115).
 */
object PruneText {
    fun of(chats: String, pluginsLine: String, pluginsOn: Boolean): String = if (pluginsOn && pluginsLine.isNotBlank()) "$chats\n\n$pluginsLine" else chats
}
