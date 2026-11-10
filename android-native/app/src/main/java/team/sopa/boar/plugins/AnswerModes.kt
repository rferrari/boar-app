package team.sopa.boar.plugins

/**
 * The answer-mode slot: a Boar Lab that changes how the chat writes an answer, not what it reads. Today one mode,
 * Staged answers (sources first, a short intro, then the details; team.sopa.boar.engine.answer.StagedAnswer). With no
 * lab on, the chat answers as usual.
 */
object AnswerModes {
    /** Set by Boar Labs at start: a switched-on lab asks for staged answers. */
    @Volatile var staged: () -> Boolean = { false }
}
