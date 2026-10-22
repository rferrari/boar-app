package team.sopa.boar.engine.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A device request can pick how search ranks (balanced, keyword, meaning), to compare answers across modes. */
class DeviceEvalRetrievalTest {
    @Test fun `without a mode search is balanced`() {
        assertNull(DeviceEval.parseRequest("""{"requestId":"r"}""").retrieval)
    }

    @Test fun `the three modes are accepted`() {
        for (m in listOf("balanced", "keyword", "meaning")) {
            assertEquals(m, DeviceEval.parseRequest("""{"requestId":"r","retrieval":"$m"}""").retrieval)
        }
    }

    @Test fun `custom and unknown modes are refused`() {
        for (m in listOf("custom", "fast", "")) {
            val e = runCatching { DeviceEval.parseRequest("""{"requestId":"r","retrieval":"$m"}""") }.exceptionOrNull()
            assertTrue(m, e is EvalRequestException && e.message.orEmpty().contains("\"retrieval\""))
        }
    }
}
