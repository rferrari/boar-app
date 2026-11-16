package team.sopa.boar.engine.answer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 2200 d: a table in an answer shows as lines, not raw pipes. */
class TableLinesTest {
    private val blood = "Blood test results — March 2026\n\n| Test | Result | Reference |\n|---|---|---|\n| Fasting glucose | 98 mg/dL | 70 to 99 mg/dL |\n" +
        "| Total cholesterol | 231 mg/dL | below 190 mg/dL |\n| LDL | 162 mg/dL | below 130 mg/dL |\n\nNote from Dr. Helen Price: LDL above target."

    @Test fun `a table's rows become First - second (third) lines, the rest untouched`() {
        assertEquals("Blood test results — March 2026\n\nFasting glucose: 98 mg/dL (70 to 99 mg/dL)\nTotal cholesterol: 231 mg/dL (below 190 mg/dL)\n" +
            "LDL: 162 mg/dL (below 130 mg/dL)\n\nNote from Dr. Helen Price: LDL above target.", TableLines.of(blood))
        assertTrue('|' !in TableLines.of(blood))
    }

    @Test fun `text without a table, or a pipe that isn't one, stays as it was`() {
        for (t in listOf("No table here.", "a | b in a sentence", "| only one row |", "x || y")) assertEquals(t, TableLines.of(t))
        assertEquals("A: 1\nB: 2", TableLines.of("| Name | Value |\n| --- | --- |\n| A | 1 |\n| B | 2 |"))
    }

    @Test fun `the chat draws answers through it`() {
        val md = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/Markdown.kt").readText()
        assertTrue(md.contains("team.sopa.boar.engine.answer.TableLines.of(it.content)"))
    }
}
