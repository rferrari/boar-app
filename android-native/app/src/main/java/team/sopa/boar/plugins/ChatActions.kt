package team.sopa.boar.plugins

/** What a plugin adds to a chat's long-press sheet in the menu (Press: "Add to Press", adam Nov 12). Set by Labs. */
data class ChatAction(val labelKey: String, val run: (context: android.content.Context, sessionId: String, title: String) -> Unit)

object ChatActions {
    @Volatile var actions: () -> List<ChatAction> = { emptyList() }
}
