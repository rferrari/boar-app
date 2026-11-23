package team.sopa.boar.engine.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.models.StartLoad
import java.io.File

/** The device eval runner lists a model only when its file is complete, as the app does (X6: a partial Qwen3-4B, Nov 10). */
class EvalRunnerModelsTest {
    @Test fun `a partial file is not installed, a complete one is`() {
        assertFalse(StartLoad.complete(true, 1_200_000_000L, 2_497_281_120L))
        assertTrue(StartLoad.complete(true, 2_497_281_120L, 2_497_281_120L))
        assertFalse(StartLoad.complete(false, 0L, 2_497_281_120L))
    }

    @Test fun `every way the runner lists a model checks the file is complete`() {
        val src = File("../app/src/main/java/team/sopa/boar/eval/DeviceEvalRunner.kt").readText()
        val start = src.indexOf("fun installedModels()")
        val end = src.indexOf("private fun labEngineModels").let { src.indexOf("\n    }\n", it) }
        val listing = src.substring(start, end)
        assertEquals(3, Regex("StartLoad\\.complete\\(").findAll(listing).count())
        assertFalse("a bare isFile check is left", Regex("if \\(f\\.isFile\\)").containsMatchIn(listing))
    }
}
