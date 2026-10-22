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
    /** How the Models screen names it ("Big Boar"), with [emoji]. */
    val engineLabel: String
    val engineEmoji: String
    /** Its lab's avatar (a drawable), shown instead of [engineEmoji] when set. */
    val engineArt: Int? get() = null
    /** The models this engine runs, for the catalog. */
    fun models(): List<CatalogAsset>
    /** Starts the engine with [asset]; null when it can't (its error is logged). [params]: the user's Advanced tweaks. */
    suspend fun load(asset: CatalogAsset, params: EngineParams): TextModel?
    /**
     * A model of BOAR's own engine ([CatalogAsset.engine] null) this engine runs instead, by the user's choice (Big Boar
     * for a mixture of experts too big for the phone's memory). Asked through [EnginePlugins.route]; false by default.
     */
    fun claims(asset: CatalogAsset): Boolean = false
    /**
     * One of this engine's own models ([CatalogAsset.engine] = [id]) the user moved to BOAR's engine (the BOAR choice on
     * its card). Asked through [EnginePlugins.prefersBuiltIn]; false by default.
     */
    fun prefersBuiltIn(asset: CatalogAsset): Boolean = false
}

object EnginePlugins {
    /** Set by Boar Labs at start: the engines of the labs switched on right now. */
    @Volatile var providers: () -> List<EngineProvider> = { emptyList() }

    fun models(): List<CatalogAsset> = runCatching { providers().flatMap { it.models() } }.getOrDefault(emptyList())

    fun forEngine(id: String): EngineProvider? = providers().firstOrNull { it.id == id }

    /**
     * Set by Boar Labs at start: the id of the engine that runs [CatalogAsset] instead of its own (the user's engine
     * choice on the model's card). Null, the default and always with Labs off, keeps [CatalogAsset.engine]'s rule.
     */
    @Volatile var route: (CatalogAsset) -> String? = { null }

    /** The engine [route] picks for [asset], if any; a failing route is today's rule. */
    fun routed(asset: CatalogAsset): EngineProvider? = runCatching { route(asset) }.getOrNull()?.let(::forEngine)

    /**
     * Set by Boar Labs at start: a Labs engine's own model ([CatalogAsset.engine] set) that runs on BOAR's engine instead,
     * by the user's choice. False, the default and always with Labs off, keeps [CatalogAsset.engine]'s rule.
     */
    @Volatile var builtIn: (CatalogAsset) -> Boolean = { false }

    /** [builtIn] for [asset]; a failing hook is today's rule. */
    fun prefersBuiltIn(asset: CatalogAsset): Boolean = runCatching { builtIn(asset) }.getOrDefault(false)
}
