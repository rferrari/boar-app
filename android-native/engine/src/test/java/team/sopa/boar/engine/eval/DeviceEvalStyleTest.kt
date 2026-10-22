package team.sopa.boar.engine.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A device request can pick the answer style for its run ("personal" for Personal Files), to compare styles. */
class DeviceEvalStyleTest {
    @Test fun `without a style the run uses the default`() {
        assertNull(DeviceEval.parseRequest("""{"requestId":"r"}""").style)
    }

    @Test fun `a style id is kept as sent`() {
        for (s in listOf("personal", "detailed", "succinct")) {
            assertEquals(s, DeviceEval.parseRequest("""{"requestId":"r","style":"$s"}""").style)
        }
    }

    @Test fun `a style with other fields of a files run`() {
        val r = DeviceEval.parseRequest("""{"requestId":"pf","pipeline":"legacy","style":"personal","retrieval":"meaning","questions":[{"id":"q1","query":"What is my rent?"}]}""")
        assertEquals("personal", r.style)
        assertEquals("meaning", r.retrieval)
        assertEquals(listOf("q1"), r.questions?.map { it.id })
    }

    @Test fun `an empty or non-string style is refused`() {
        for (v in listOf("\"\"", "\"  \"", "3", "[]", "null")) {
            val e = runCatching { DeviceEval.parseRequest("""{"requestId":"r","style":$v}""") }.exceptionOrNull()
            assertTrue(v, e is EvalRequestException && e.message.orEmpty().contains("\"style\""))
        }
    }
}
