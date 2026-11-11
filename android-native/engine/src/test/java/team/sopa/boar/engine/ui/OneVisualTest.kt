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
        assertTrue(canvas.contains("fun keyOf(id: String, kind: Kind): String = if (kind == Kind.WIDGET) \"\$id\$WIDGET_SUFFIX\" else id"))
        assertTrue(canvas.contains("private const val WIDGET_SUFFIX = \"~widget\""))
    }

    @Test fun `the card keeps its grounding check`() {
        assertTrue(canvas.contains("Visual.parse(raw, grounding(a, context), a.question) as? Visual.Parsed.Rejected"))
        assertTrue(lab.contains("value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Visual.parse(st.raw, grounding, a.question) }"))
        assertTrue(lab.contains("val grounding = remember(a.text, context) { Canvas.grounding(a, context) }"))
    }

    @Test fun `the card is parsed once per reply, off the main thread (validator N-1)`() {
        assertTrue(lab.contains("produceState<Visual.Parsed?>(null, st.raw, grounding, a.question)"))
        assertTrue(Regex("Visual\\.parse\\(").findAll(lab).count() == 1)
    }

    @Test fun `an old widget under the card's key moves to the widget's place, so a card doesn't overwrite it (validator C-1)`() {
        val status = canvas.substringAfter("fun status(ctx: Context, id: String)").substringBefore("\n    }\n")
        assertTrue(status.contains("if (done.kind == Kind.WIDGET && !id.endsWith(WIDGET_SUFFIX))"))
        assertTrue(status.contains("save(ctx, widgetKey, done)") && status.contains("file(ctx, id).delete()"))
    }
}
