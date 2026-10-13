package team.sopa.boar.plugins

/**
 * What the chat hands the plugin slots (team.sopa.boar.labs.LabHooks) about one finished answer. The app knows the
 * slots, never the plugins: with Boar Labs off (or a build without EXPERIMENTAL_FEATURES) every slot is empty.
 */
data class AnswerRef(
    val id: String,
    val sessionId: String?,
    /** The user's question this answers. */
    val question: String,
    /** The answer as shown (citations cleaned). */
    val text: String,
    /** title to source. */
    val sources: List<Pair<String, String?>>,
    /** The model is answering something: a plugin shouldn't start its own generation now. */
    val chatBusy: Boolean,
)
