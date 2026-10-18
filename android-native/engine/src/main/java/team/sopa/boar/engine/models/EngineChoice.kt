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

    /** What [choice] picks for a model rated [fit], if Big Boar can run it; the header check can wait until this says yes. */
    fun prefersBigBoar(choice: Choice, fit: Compatibility): Boolean = when (choice) {
        Choice.BOAR -> false
        Choice.BIG_BOAR -> true
        Choice.AUTO -> fit == Compatibility.RED
    }

    fun resolve(choice: Choice, fit: Compatibility, eligible: Boolean): Engine =
        if (eligible && prefersBigBoar(choice, fit)) Engine.BIG_BOAR else Engine.BOAR
}
