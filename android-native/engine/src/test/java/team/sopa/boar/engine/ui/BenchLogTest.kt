package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** The bench screen (adam, Nov 12): the log on top, newest line first, a clock since the last line, a line per question. */
class BenchLogTest {
    private val bench = java.io.File("../app/src/main/java/team/sopa/boar/bench/BenchActivity.kt").readText()
    private val runner = java.io.File("../app/src/main/java/team/sopa/boar/eval/DeviceEvalRunner.kt").readText()

    @Test fun `the log is the first section, newest first, with the clock`() {
        val screen = bench.substringAfter("verticalScroll(rememberScrollState())")
        assertTrue(screen.indexOf("BoarSection(\"Log\")") in 0 until screen.indexOf("BoarSection(\"Model\")"))
        assertTrue(bench.contains("log.trimEnd('\\n').lines().takeLast(MAX).asReversed()") && bench.contains("BenchLog.since(now - lastAt)"))
    }

    @Test fun `each question says it started`() {
        assertTrue(runner.indexOf("log(\"[EVAL] \${q.id}: answering on \${config.modelId}…\")") in 0 until runner.indexOf("val r = answerRow("))
    }
}
