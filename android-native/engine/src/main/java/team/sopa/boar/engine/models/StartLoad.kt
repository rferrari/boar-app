package team.sopa.boar.engine.models

/**
 * What the chat loads on its own (at start, or for a question while no model is loaded). It never loads a model whose
 * last load closed the app ([LoadGuard.isBlocked]), nor one rated RED for this phone ([Compatibility.compute]) unless the
 * user chose "Load anyway" for it. Nothing is hard-blocked: the chat offers the choice ([Decision]).
 */
object StartLoad {
    data class Model(val id: String, val sizeBytes: Long)

    sealed interface Decision {
        /** Load [modelId] (GREEN, YELLOW, unknown RAM, or RED the user already chose to load). */
        data class Load(val modelId: String) : Decision

        /** RED without "Load anyway": nothing loads; the chat asks. [onlyModel]: no other model is installed. */
        data class AskRisky(val modelId: String, val onlyModel: Boolean) : Decision

        /** [blockedId] closed the app last time: [modelId] answers instead (the last that loaded, else the smallest). */
        data class Fallback(val blockedId: String, val modelId: String) : Decision

        /** [blockedId] closed the app last time and no other installed model can answer. [onlyModel]: it's the only one. */
        data class NoFallback(val blockedId: String, val onlyModel: Boolean) : Decision

        /** No model installed. */
        data object NoModel : Decision
    }

    /**
     * [active] is the user's model (null when none is installed); [installed] every installed LLM; [lastLoadedId] the
     * last model that loaded; [loadAnyway] the models the user chose to load although RED.
     */
    fun decide(
        active: Model?,
        installed: List<Model>,
        isBlocked: (String) -> Boolean,
        lastLoadedId: String?,
        totalRamBytes: Long,
        loadAnyway: Set<String>,
    ): Decision {
        if (active == null) return Decision.NoModel
        val onlyModel = installed.none { it.id != active.id }
        if (isBlocked(active.id)) {
            val fallback = fallback(installed, isBlocked, lastLoadedId)
            return if (fallback != null) Decision.Fallback(active.id, fallback.id) else Decision.NoFallback(active.id, onlyModel)
        }
        if (risky(active, totalRamBytes) && active.id !in loadAnyway) return Decision.AskRisky(active.id, onlyModel)
        return Decision.Load(active.id)
    }

    /** The model that answers instead of a blocked one: the last that loaded, else the smallest installed, never a blocked one. */
    fun fallback(installed: List<Model>, isBlocked: (String) -> Boolean, lastLoadedId: String?): Model? {
        val ok = installed.filter { !isBlocked(it.id) }
        return lastLoadedId?.let { id -> ok.firstOrNull { it.id == id } } ?: ok.minByOrNull { it.sizeBytes }
    }

    /** RED for this phone; unknown RAM is never risky. */
    fun risky(model: Model, totalRamBytes: Long): Boolean = Compatibility.compute(model.sizeBytes, totalRamBytes) == Compatibility.RED
}
