package team.sopa.boar.plugins

import team.sopa.boar.engine.EngineParams
import team.sopa.boar.engine.TextModel
import team.sopa.boar.engine.models.CatalogAsset

/**
 * The engine slot: a Boar Labs plugin that runs models on another engine (its own process, e.g. BigMoeOnEdge). The app
 * lists its models with the rest ([CatalogAsset.engine] set, [CatalogAsset.externalPath] for a file placed over USB),
 * and the model host hands their load to the plugin; the answer paths then use the [TextModel] it returns, so the
 * chat, Stop, sources and the debug log work unchanged. Empty unless Labs and that plugin are switched on.
 */
interface EngineProvider {
    /** Matches [CatalogAsset.engine]. */
    val id: String
    /** The models this engine runs, for the catalog. */
    fun models(): List<CatalogAsset>
    /** Starts the engine with [asset]; null when it can't (its error is logged). [params]: the user's Advanced tweaks. */
    suspend fun load(asset: CatalogAsset, params: EngineParams): TextModel?
}

object EnginePlugins {
    /** Set by Boar Labs at start: the engines of the labs switched on right now. */
    @Volatile var providers: () -> List<EngineProvider> = { emptyList() }

    fun models(): List<CatalogAsset> = runCatching { providers().flatMap { it.models() } }.getOrDefault(emptyList())

    fun forEngine(id: String): EngineProvider? = providers().firstOrNull { it.id == id }
}
