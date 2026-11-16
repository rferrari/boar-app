package team.sopa.boar.engine.answer

/** Whether Staged's area above an answer has anything to show (adam, Nov 12: "no source, no empty block"). */
object StagedShown {
    fun any(quotes: Int, more: Int, added: Int, stepLine: Boolean): Boolean = quotes > 0 || more > 0 || added > 0 || stepLine
}
