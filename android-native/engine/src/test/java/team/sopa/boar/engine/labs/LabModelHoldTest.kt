package team.sopa.boar.engine.labs

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validator P-A1, P-A2 (t9 0435): a plugin that holds the chat's model (claimModelForLab) asks for the session as the
 * lab (activeSession(forLab = true)), or it waits 150 s for its own hold; and the hold is released however its job ends.
 */
class LabModelHoldTest {
    private val sources = java.io.File("../app/src/labs").walk().filter { it.extension == "kt" }.toList()

    @Test fun `every plugin that holds the model asks for it as the lab`() {
        val holders = sources.filter { it.readText().contains("claimModelForLab()") }
        assertTrue(holders.map { it.name }.toString(), holders.size >= 2)
        for (f in holders) {
            val calls = Regex("""activeSession\(([^)]*)\)""").findAll(f.readText()).map { it.groupValues[1] }.toList()
            assertTrue("${f.name}: $calls", calls.isNotEmpty() && calls.all { it.contains("forLab = true") })
        }
    }

    @Test fun `Cards releases its hold when the job completes, not inside its body`() {
        val canvas = java.io.File("../app/src/labs/plugins/canvas/java/team/sopa/boar/labs/canvas/Canvas.kt").readText()
        val start = canvas.substringAfter("fun start(").substringBefore("\n    }\n")
        assertTrue(start.contains("job.invokeOnCompletion { services.releaseModelForLab() }") && !start.contains("finally { services.releaseModelForLab() }"))
    }
}
