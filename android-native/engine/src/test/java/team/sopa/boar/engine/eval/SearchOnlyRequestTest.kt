package team.sopa.boar.engine.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 0321: the bench's search-only request, adb only; the sources with their scores, no answer. */
class SearchOnlyRequestTest {
    private val q = """"pipeline":"legacy","evalSetVersion":"x","questions":[{"id":"q1","query":"Quais são as três montanhas mais altas?","category":"c"}]"""

    @Test fun `a request asks for it with a boolean, and nothing else does`() {
        assertEquals(true, DeviceEval.parseRequest("""{"requestId":"s1",$q,"searchOnly":true}""").searchOnly)
        assertNull(DeviceEval.parseRequest("""{"requestId":"s2",$q}""").searchOnly)
        val bad = runCatching { DeviceEval.parseRequest("""{"requestId":"s3",$q,"searchOnly":"yes"}""") }.exceptionOrNull()
        assertTrue(bad?.message.orEmpty(), bad?.message?.contains("\"searchOnly\" must be a boolean") == true)
    }

    @Test fun `only the adb request reaches it - the Evaluation screen never does`() {
        val runner = java.io.File("../app/src/main/java/team/sopa/boar/eval/DeviceEvalRunner.kt").readText()
        assertTrue(runner.contains("searchOnly = request.searchOnly == true"))
        assertTrue(runner.contains("else if (searchOnly) searchRow(retrieval, runId, config, q, runStyle, maxTokens, residency, loadMs, retrievalMode)"))
        // The row: every source with its similarity, score, match and kind; no answer.
        for (k in listOf("\"retrievedSources\" to sources", "\"kind\" to", "\"similarity\" to", "\"path\" to \"search-only\"", "\"answer\" to \"\"")) assertTrue(k, runner.contains(k))
        val screens = java.io.File("../app/src/main/java").walkTopDown().filter { it.extension == "kt" && it.name != "DeviceEvalRunner.kt" }.map { it.readText() }
        assertFalse(screens.any { it.contains("searchOnly") })
    }
}
