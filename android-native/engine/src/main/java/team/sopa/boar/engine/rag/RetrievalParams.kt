package team.sopa.boar.engine.rag

/**
 * The knobs of [Retriever.retrieve]: how keyword and meaning results are weighed in [RagPure.fuse], and the similarity
 * floors a hit must clear. [DEFAULT] is today's retrieval exactly.
 */
data class RetrievalParams(
    /** Weight of the keyword (BM25) results in the fusion. */
    val lexicalWeight: Double = 0.5,
    /** Weight of the meaning (embedding) results in the fusion. */
    val semanticWeight: Double = 0.5,
    /** Cosine floor for a semantic hit from the knowledge base or a format-1 pack to count. */
    val minSemanticSimilarity: Double = RagPure.MIN_SEMANTIC_SIMILARITY,
    /** Real similarity a knowledge-base or format-1 hit needs before it can reach the model. */
    val answerMinSimilarity: Double = RagPure.ANSWER_MIN_SIMILARITY,
) {
    companion object {
        val DEFAULT = RetrievalParams()
    }
}

/** How retrieval ranks what it finds; CUSTOM keeps whatever weights and floors were set by hand. */
enum class RetrievalMode {
    BALANCED, KEYWORD, MEANING, CUSTOM;

    /** This mode's parameters; [custom] is used for CUSTOM only. */
    fun params(custom: RetrievalParams = RetrievalParams.DEFAULT): RetrievalParams = when (this) {
        BALANCED -> RetrievalParams.DEFAULT
        KEYWORD -> RetrievalParams.DEFAULT.copy(semanticWeight = 0.0)
        MEANING -> RetrievalParams.DEFAULT.copy(lexicalWeight = 0.0)
        CUSTOM -> custom
    }
}
