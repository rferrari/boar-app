package team.sopa.boar.engine.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A device request can name the Boar Labs plugins that answer, to run the same questions with plugins on and off. */
class DeviceEvalLabsTest {
    @Test fun `without labs no plugin answers`() {
        assertNull(DeviceEval.parseRequest("""{"requestId":"r"}""").labs)
    }

    @Test fun `labs are kept in order`() {
        assertEquals(listOf("cohazard", "calc", "guard"), DeviceEval.parseRequest("""{"requestId":"r","labs":["cohazard","calc","guard"]}""").labs)
    }

    @Test fun `labs must be a list of names`() {
        for (v in listOf("\"calc\"", "[1]", "[\"\"]")) {
            val e = runCatching { DeviceEval.parseRequest("""{"requestId":"r","labs":$v}""") }.exceptionOrNull()
            assertTrue(v, e is EvalRequestException)
        }
    }
}
