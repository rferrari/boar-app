package team.sopa.boar.engine.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.models.ModelOrder.Facts
import team.sopa.boar.engine.models.ModelOrder.Filter
import team.sopa.boar.engine.models.ModelOrder.Sort

class ModelOrderTest {
    private fun m(id: String) = CatalogAsset(id = id, kind = AssetKind.LLM, label = id, filename = "models/$id.gguf", sizeBytes = 1, sha256 = "", sourceUrl = "", license = "", description = "", required = false)
    private val items = listOf("a", "b", "c", "d", "e").map(::m)
    private val facts = Facts(
        active = "c", onPhone = setOf("a", "c", "d"), downloading = setOf("e"), mine = setOf("d", "e"),
        lastUsed = mapOf("a" to 100L, "c" to 300L, "d" to 200L), added = mapOf("b" to 5L, "d" to 9L, "e" to 7L),
    )
    private fun ids(sort: Sort, filter: Filter = Filter.ALL, f: Facts = facts) = ModelOrder.apply(items, sort, filter, f).map { it.id }

    @Test fun `smart puts a download first, then the active one, then the phone's models by last use`() =
        assertEquals(listOf("e", "c", "d", "a", "b"), ids(Sort.SMART))

    @Test fun `last used is newest first and never used keep catalog order at the end, a download on top`() =
        assertEquals(listOf("e", "c", "d", "a", "b"), ids(Sort.LAST_USED))

    @Test fun `recently added is newest first, a download on top`() =
        assertEquals(listOf("e", "d", "b", "a", "c"), ids(Sort.ADDED))

    /** adam, Nov 13: no Downloading filter; a download shows on top whatever the filter. */
    @Test fun `filters keep a download on top`() {
        assertEquals(listOf("e", "c", "d", "a"), ids(Sort.SMART, Filter.ON_PHONE))
        assertEquals(listOf("e", "d"), ids(Sort.SMART, Filter.MINE))
        assertEquals(listOf("ALL", "ON_PHONE", "MINE"), Filter.values().map { it.name })
        val two = facts.copy(downloading = setOf("b", "e"))
        assertEquals("two downloads keep the sort's order between them", listOf("e", "b", "d", "a", "c"), ids(Sort.ADDED, f = two))
    }

    @Test fun `the catalog screen pins a download that starts while it is open, and has no Downloading chip`() {
        val src = java.io.File("../app/src/main/java/team/sopa/boar/ui/models/ModelsScreen.kt").readText()
        assertTrue(!src.contains("Filter.DOWNLOADING") && !src.contains("filterDownloading"))
        assertTrue(src.contains("ModelOrder.downloadsFirst(frozen, pinned)"))
    }

    @Test fun `with no times at all every sort keeps catalog order after the active one`() {
        val bare = Facts(active = "c", onPhone = setOf("a", "c"))
        assertEquals(listOf("c", "a", "b", "d", "e"), ids(Sort.SMART, f = bare))
        assertEquals(listOf("a", "b", "c", "d", "e"), ids(Sort.LAST_USED, f = bare))
        assertEquals(listOf("a", "b", "c", "d", "e"), ids(Sort.ADDED, f = bare))
    }
}
