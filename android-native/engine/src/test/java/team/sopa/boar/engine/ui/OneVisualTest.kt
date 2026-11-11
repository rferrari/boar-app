package team.sopa.boar.engine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** t9, Nov 10: one "Create a card"; the model picks the form, the interactive widget only when asked. */
class OneVisualTest {
    private val lab = java.io.File("../app/src/labs/plugins/canvas/java/team/sopa/boar/labs/canvas/CanvasLab.kt").readText()
    private val canvas = java.io.File("../app/src/labs/plugins/canvas/java/team/sopa/boar/labs/canvas/Canvas.kt").readText()

    @Test fun `one action in the answer's sheet, the card`() {
        val actions = lab.substringAfter("override fun answerActions(").substringBefore("override fun AnswerExtra(")
        assertEquals(1, Regex("LabAction\\(").findAll(actions).count())
        assertTrue(actions.contains("Canvas.Kind.VISUAL") && !actions.contains("Canvas.Kind.WIDGET"))
    }

    @Test fun `the widget is built only from its own chip, kept beside the card`() {
        assertEquals(1, Regex("Canvas.start\\(ctx, a, Canvas.Kind.WIDGET\\)").findAll(lab).count())
        assertTrue(lab.contains("Text(\"🧪 \" + t(\"native.labs.canvas.interactive\")"))
        assertTrue(canvas.contains("fun keyOf(id: String, kind: Kind): String = if (kind == Kind.WIDGET) \"\$id~widget\" else id"))
    }

    @Test fun `the card keeps its grounding check`() {
        assertTrue(canvas.contains("Visual.parse(raw, grounding(a, context), a.question) as? Visual.Parsed.Rejected"))
        assertTrue(lab.contains("Visual.parse(st.raw, Canvas.grounding(a, context), a.question) as? Visual.Parsed.Ok"))
    }
}
