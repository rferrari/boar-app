package team.sopa.boar.engine.answer

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceGroupsTest {
    private data class P(val doc: String, val score: Double?, val body: String)

    private fun group(ps: List<P>) = SourceGroups.group(ps, { it.doc }, { it.score }) { g -> g.first().copy(body = g.joinToString(" | ") { it.body }) }

    @Test fun mergesPassagesOfOneArticleAndSortsByBestScore() {
        // Seen on the X6: Methane 85%, Methane clathrate 76%, Methane 85%, Methane clathrate 74%; 86% under 78%.
        val g = group(listOf(P("clathrate", 0.76, "c1"), P("methane", 0.85, "m1"), P("clathrate", 0.74, "c2"), P("methane", 0.86, "m2")))
        assertEquals(listOf(P("methane", 0.86, "m2 | m1"), P("clathrate", 0.76, "c1 | c2")), g.items)
        assertEquals(mapOf(1 to 2, 2 to 1, 3 to 2, 4 to 1), g.renumber)
    }

    @Test fun unknownScoresGoLastAndTiesKeepTheirOrder() {
        val g = group(listOf(P("a", null, "a"), P("b", 0.5, "b"), P("c", 0.5, "c")))
        assertEquals(listOf("b", "c", "a"), g.items.map { it.doc })
    }

    @Test fun samePageFromTwoPacksIsOneArticle() {
        val vital = SourceGroups.articleKey("Wikipedia — https://en.wikipedia.org/wiki/Methane", "pack:wiki-vital5:Methane", "Methane")
        val big = SourceGroups.articleKey("Wikipedia — https://en.wikipedia.org/wiki/Methane (CC BY-SA 4.0)", "pack:enwiki:a42", "Methane")
        assertEquals(vital, big)
        assertEquals("doc-7", SourceGroups.articleKey(null, "doc-7", "Notes"))
        assertEquals("Notes", SourceGroups.articleKey("My notes", null, "Notes"))
    }

    @Test fun renumbersCitations() {
        val map = mapOf(1 to 2, 2 to 1, 3 to 2, 4 to 1)
        assertEquals("Methane [1] traps heat [2]; ice [2].", SourceGroups.renumber("Methane [2][4] traps heat [1]; ice [3].", map))
        assertEquals("Out of range [9] stays.", SourceGroups.renumber("Out of range [9] stays.", map))
        assertEquals("Same [1] [2]", SourceGroups.renumber("Same [1] [2]", mapOf(1 to 1, 2 to 2)))
    }
}
