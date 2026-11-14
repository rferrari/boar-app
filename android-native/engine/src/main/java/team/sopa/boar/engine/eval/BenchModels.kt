package team.sopa.boar.engine.eval

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import team.sopa.boar.engine.models.AssetKind
import team.sopa.boar.engine.models.Catalog
import team.sopa.boar.engine.models.CatalogAsset

/**
 * Models only the bench runs (t9 0443, 0452: Qwen3-0.6B against the 1.7B, "a hidden catalog entry; no change to the
 * release catalog, setup, tiers or onboarding"). Their entries are in assets/boar-data/[ASSET], in catalog.json's shape
 * (pinned revision, SHA-256, size), and only DeviceEvalRunner reads that file: setup, the Model Catalog, the chat and the
 * downloader read catalog.json through [Catalog.parse], which drops any [CatalogAsset.testOnly] entry besides.
 *
 * The app never downloads one: its file is copied to the phone over USB (docs in the file's "about").
 */
object BenchModels {
    const val ASSET = "bench-models.json"

    @Serializable
    private data class BenchFile(val about: String = "", val models: List<CatalogAsset>)

    private val json = Json { ignoreUnknownKeys = true }

    /** The bench-only models in [text]; throws when one could reach a user list or isn't a language model. */
    fun parse(text: String): List<CatalogAsset> {
        val models = json.decodeFromString(BenchFile.serializer(), text).models
        for (m in models) {
            require(m.testOnly && m.hidden) { "${m.id}: a bench model must be testOnly and hidden" }
            require(m.kind == AssetKind.LLM) { "${m.id}: a bench model is a language model" }
            require(m.answerTier == null && !m.required && m.pick == null) { "${m.id}: a bench model has no tier, pick or required flag" }
            require(m.sha256.matches(Regex("[0-9a-f]{64}")) && m.sizeBytes > 0) { "${m.id}: a bench model is pinned by SHA-256 and size" }
        }
        return models
    }

    /**
     * The language models the bench can run: [catalog]'s, in its order, then [benchOnly] (an id or file already in the
     * catalog is the catalog's).
     */
    fun llms(catalog: Catalog, benchOnly: List<CatalogAsset>): List<CatalogAsset> {
        val own = catalog.models.filter { it.kind == AssetKind.LLM }
        return own + benchOnly.filter { b -> own.none { it.id == b.id || it.filename == b.filename } }
    }
}
