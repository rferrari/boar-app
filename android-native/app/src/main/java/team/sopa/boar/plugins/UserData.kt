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
}
