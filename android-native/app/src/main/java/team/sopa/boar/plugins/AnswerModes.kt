package team.sopa.boar.plugins

/**
 * The answer-mode slot: a Boar Lab that changes how the chat writes an answer, not what it reads. Today one mode,
 * Staged answers (sources first, a short intro, then the details; team.sopa.boar.engine.answer.StagedAnswer). With no
 * lab on, the chat answers as usual.
 */
object AnswerModes {
    /** Set by Boar Labs at start: a switched-on lab asks for staged answers. */
    @Volatile var staged: () -> Boolean = { false }

    /**
     * Set by Boar Labs at start: a switched-on lab shows the answer's sources itself (Staged Answers' quotes and "+N
     * sources"), so the chat doesn't list them again under the answer (adam, Nov 10). Off: the list is back.
     */
    @Volatile var ownsSources: () -> Boolean = { false }
}
