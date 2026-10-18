package team.sopa.boar.engine.models

import org.junit.Assert.assertEquals
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

    @Test fun `smart puts the active one first, then downloads, then the phone's models by last use`() =
        assertEquals(listOf("c", "e", "d", "a", "b"), ids(Sort.SMART))

    @Test fun `last used is newest first and never used keep catalog order at the end`() =
        assertEquals(listOf("c", "d", "a", "b", "e"), ids(Sort.LAST_USED))

    @Test fun `recently added is newest first`() =
        assertEquals(listOf("d", "e", "b", "a", "c"), ids(Sort.ADDED))

    @Test fun `filters`() {
        assertEquals(listOf("c", "d", "a"), ids(Sort.SMART, Filter.ON_PHONE))
        assertEquals(listOf("e"), ids(Sort.SMART, Filter.DOWNLOADING))
        assertEquals(listOf("e", "d"), ids(Sort.SMART, Filter.MINE))
    }

    @Test fun `with no times at all every sort keeps catalog order after the active one`() {
        val bare = Facts(active = "c", onPhone = setOf("a", "c"))
        assertEquals(listOf("c", "a", "b", "d", "e"), ids(Sort.SMART, f = bare))
        assertEquals(listOf("a", "b", "c", "d", "e"), ids(Sort.LAST_USED, f = bare))
        assertEquals(listOf("a", "b", "c", "d", "e"), ids(Sort.ADDED, f = bare))
    }
}
