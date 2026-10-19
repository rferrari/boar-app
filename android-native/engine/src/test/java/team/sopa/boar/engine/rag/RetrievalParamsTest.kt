package team.sopa.boar.engine.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Retrieval's default parameters are today's constants; each mode changes only its own weight. */
class RetrievalParamsTest {
    @Test fun `the default is today's retrieval`() {
        val d = RetrievalParams.DEFAULT
        assertEquals(0.5, d.lexicalWeight, 0.0)
        assertEquals(0.5, d.semanticWeight, 0.0)
        assertEquals(RagPure.MIN_SEMANTIC_SIMILARITY, d.minSemanticSimilarity, 0.0)
        assertEquals(RagPure.ANSWER_MIN_SIMILARITY, d.answerMinSimilarity, 0.0)
        assertEquals(RetrievalParams(), d)
    }

    @Test fun `modes map to their weights`() {
        assertEquals(RetrievalParams.DEFAULT, RetrievalMode.BALANCED.params())
        assertEquals(RetrievalParams.DEFAULT.copy(semanticWeight = 0.0), RetrievalMode.KEYWORD.params())
        assertEquals(RetrievalParams.DEFAULT.copy(lexicalWeight = 0.0), RetrievalMode.MEANING.params())
        val custom = RetrievalParams(lexicalWeight = 0.3, semanticWeight = 0.7, minSemanticSimilarity = 0.6, answerMinSimilarity = 0.75)
        assertEquals(custom, RetrievalMode.CUSTOM.params(custom))
        assertEquals(RetrievalParams.DEFAULT, RetrievalMode.CUSTOM.params())
    }

    @Test fun `fusion with default weights equals fusion with the params' weights`() {
        val lex = listOf(Chunk("a", "a", "A", "x", score = 3.0), Chunk("b", "b", "B", "x", score = 1.0))
        val sem = listOf(Chunk("b", "b", "B", "x", score = 0.9, similarity = 0.9, matchType = MatchType.SEMANTIC))
        val d = RetrievalParams.DEFAULT
        assertEquals(RagPure.fuse(lex, sem, 4), RagPure.fuse(lex, sem, 4, d.lexicalWeight, d.semanticWeight))
    }

    @Test fun `a set weighted zero is left out of the fusion`() {
        val lex = listOf(Chunk("a", "a", "A", "x", score = 3.0))
        val sem = listOf(Chunk("b", "b", "B", "x", score = 0.9, similarity = 0.9, matchType = MatchType.SEMANTIC))
        val keyword = RetrievalMode.KEYWORD.params()
        assertEquals(listOf("a"), RagPure.fuse(lex, sem, 4, keyword.lexicalWeight, keyword.semanticWeight).map { it.chunkId })
        val meaning = RetrievalMode.MEANING.params()
        val got = RagPure.fuse(lex, sem, 4, meaning.lexicalWeight, meaning.semanticWeight)
        assertEquals(listOf("b"), got.map { it.chunkId })
        assertTrue(got.all { it.score.isFinite() })
    }
}
