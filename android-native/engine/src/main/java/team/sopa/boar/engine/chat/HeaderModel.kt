package team.sopa.boar.engine.chat

/**
 * The model the chat header names: the one that writes the answer, from one source, never a name kept apart. While
 * adaptive routing has another model write the answer: "→ that model". Else the phone's answering model
 * (BoarServices.answerModel: the user's pick, or the model answering in its place). Null: none (the header's default line).
 */
object HeaderModel {
    fun label(answeringNow: Boolean, answeringModel: String?, phoneModel: String?): String? =
        answeringModel?.takeIf { answeringNow }?.let { "→ $it" } ?: phoneModel
}
