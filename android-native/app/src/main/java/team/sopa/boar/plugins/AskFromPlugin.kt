package team.sopa.boar.plugins

/**
 * A plugin page's "Try" row (adam, Nov 12): [ask] opens a new chat and asks it ([send] true), or puts it in the input box
 * to edit first ([send] false). Set by the app at start; a plugin page only calls it.
 */
object AskFromPlugin {
    @Volatile var handler: (question: String, send: Boolean) -> Unit = { _, _ -> }
    fun ask(question: String, send: Boolean) = handler(question, send)
}
