package team.sopa.boar.engine.models

/**
 * Which engine runs a model BOAR could run itself or Boar Labs' Big Boar could stream (a mixture of experts): the
 * user's choice per model, Auto by default. Auto picks Big Boar only when the model is RED for this phone's memory
 * ([Compatibility]), BOAR otherwise. A model Big Boar can't run (not [eligible]) always stays on BOAR.
 */
object EngineChoice {
    enum class Choice(val value: String) {
        AUTO("auto"), BOAR("boar"), BIG_BOAR("bigboar");

        companion object {
            /** A stored value; anything else (none yet, an old value) is Auto. */
            fun of(value: String?): Choice = entries.firstOrNull { it.value == value } ?: AUTO
        }
    }

    enum class Engine { BOAR, BIG_BOAR }

    /** Tokens of reasoning Big Boar may write on top of the answer's own budget. */
    const val REASONING_ALLOWANCE = 1024
    /** Room left in the context for the prompt when the reasoning allowance is added. */
    const val PROMPT_ROOM = 1536

    /**
     * Big Boar's token cap for an answer of [answerTokens]: the mixtures of experts it runs (LFM2.5 8B, Qwen3.6 35B) reason
     * before they answer, and the reasoning counts against the cap, so a 1,024-token answer budget was spent before the
     * answer began. They get [REASONING_ALLOWANCE] more, within an [nCtx] context that keeps [PROMPT_ROOM] for the prompt,
     * and never less than the answer's own budget.
     */
    fun bigBoarTokens(answerTokens: Int, nCtx: Int): Int =
        if (answerTokens <= 0) answerTokens  // no cap stays no cap
        else minOf(answerTokens + REASONING_ALLOWANCE, maxOf(answerTokens, nCtx - PROMPT_ROOM))

    /** What [choice] picks for a model rated [fit], if Big Boar can run it; the header check can wait until this says yes. */
    fun prefersBigBoar(choice: Choice, fit: Compatibility): Boolean = when (choice) {
        Choice.BOAR -> false
        Choice.BIG_BOAR -> true
        Choice.AUTO -> fit == Compatibility.RED
    }

    fun resolve(choice: Choice, fit: Compatibility, eligible: Boolean): Engine =
        if (eligible && prefersBigBoar(choice, fit)) Engine.BIG_BOAR else Engine.BOAR
}
