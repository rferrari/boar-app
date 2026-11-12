package team.sopa.boar.plugins

/**
 * What the plugins made from the user's chats or for the user (Boar Labs: pictures, illustrations, the Printer's
 * basket), erased with the app's own data. [CHATS]: what came from chats, when the chats are cleared. [EVERYTHING]:
 * all of it, when the PIN is forgotten (the chats are erased with the key) or the user purges the app.
 */
object UserData {
    enum class Scope { CHATS, EVERYTHING }

    /** Set by Boar Labs at start; runs every lab's eraser, switched on or not. */
    @Volatile var erase: (Scope) -> Unit = {}

    /**
     * One chat deleted (the drawer's 🗑️): what the plugins made for its answers ([answerIds]) goes with it, as the chat's
     * own messages do (validator P-1: a visual's file stayed). Set by Boar Labs at start.
     */
    @Volatile var eraseAnswers: (answerIds: List<String>) -> Unit = {}

    /**
     * The lines the chat's delete row shows: what deleting it takes from the plugins switched on (Press: "Its answers
     * leave the Press basket", only when some are in it; adam, Nov 12). Already in the user's language. Set by Boar Labs.
     */
    @Volatile var deleteNotes: (answerIds: List<String>) -> List<String> = { emptyList() }
}
