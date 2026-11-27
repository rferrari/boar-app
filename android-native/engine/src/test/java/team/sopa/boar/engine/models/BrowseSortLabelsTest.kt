package team.sopa.boar.engine.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, 27 Nov: Discover GGUF models' sort row says "Downloads", not "Most downloaded": short words, one line. */
class BrowseSortLabelsTest {
    private fun label(dir: String, key: String): String {
        val xml = java.io.File("../app/src/main/res/$dir/strings_native.xml").readText()
        val m = Regex("<string name=\"$key\"[^>]*>([^<]*)</string>").find(xml)
        assertTrue("$dir $key", m != null)
        return m!!.groupValues[1]
    }

    @Test fun `the sort chips are one short word or two, in English and Portuguese`() {
        assertEquals("Downloads", label("values", "native_browse_sortDownloads"))
        assertEquals("Likes", label("values", "native_browse_sortLikes"))
        assertEquals("Downloads", label("values-pt", "native_browse_sortDownloads"))
        assertEquals("Curtidas", label("values-pt", "native_browse_sortLikes"))
        assertEquals("Recentes", label("values-pt", "native_browse_sortNewest"))
        for (dir in listOf("values", "values-pt")) for (k in listOf("Trending", "Downloads", "Likes", "Newest"))
            assertTrue("$dir $k", label(dir, "native_browse_sort$k").length <= 9)
    }
}
