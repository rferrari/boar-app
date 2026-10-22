package team.sopa.boar.plugins

import team.sopa.boar.engine.rag.Chunk

/**
 * A step a Boar Labs plugin can add to the answer pipeline right after search: it reorders or filters the passages
 * (a reranker), given the question. Every answer path uses it (the chat, Deep Research, adaptive routing), through
 * BoarServices.search. With no stage, search is exactly the built-in one.
 */
fun interface SearchStage {
    /** [chunks] in search order (twice [topK] when a stage is on, for it to choose from); return at most [topK]. */
    fun process(query: String, chunks: List<Chunk>, topK: Int): List<Chunk>
}

object PipelineStages {
    /** Set by Boar Labs at start: the search stages of the labs switched on now, in grid order. */
    @Volatile var search: () -> List<SearchStage> = { emptyList() }
}
