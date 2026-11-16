package team.sopa.boar.engine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cards, the Canvas plugin redesigned (adam's interview, Nov 14; t9 0207 item 3), read from the source: one row that says
 * what the rules found, the card under the answer with one switch and ✕, ticks and card in the message's meta, the model
 * only behind the "slower" row, and the widget gone. Replaces OneVisualTest (t9 Nov 10: the widget beside the card).
 */
class CardsPluginTest {
    private val dir = File("../app/src/labs/plugins/canvas")
    private val lab = File(dir, "java/team/sopa/boar/labs/canvas/CanvasLab.kt").readText()
    private val canvas = File(dir, "java/team/sopa/boar/labs/canvas/Canvas.kt").readText()
    private fun strings(d: String) = Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        .findAll(File(dir, "res/$d/strings_lab_canvas.xml").readText()).associate { it.groupValues[1] to it.groupValues[2] }

    @Test fun `the widget, its WebView and Make it interactive are gone, with their strings`() {
        assertTrue(!File("src/main/java/team/sopa/boar/engine/canvas/WidgetHtml.kt").exists())
        // The code, comments left out (they say what went).
        val src = dir.walk().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\n]*"), "")
        for (gone in listOf("WidgetHtml", "WebView", "AndroidView", "Kind.WIDGET", "interactive", "LocalClipboardManager", "Dialog(")) assertTrue(gone, !src.contains(gone))
        for (d in listOf("values", "values-pt")) for (k in listOf("interactive", "makingWidget", "noWidget", "copy", "copied", "expand", "madeBy"))
            assertTrue("$d $k", "native_labs_canvas_$k" !in strings(d))
    }

    @Test fun `the name is Cards, the action Create a card, and EN and PT have the same strings`() {
        assertEquals("Cards", strings("values")["native_labs_canvas_title"])
        assertEquals("Create a card", strings("values")["native_labs_canvas_make_card"])
        assertEquals(strings("values").keys, strings("values-pt").keys)
        // Every key the plugin asks for exists.
        val used = Regex("\"native\\.labs\\.canvas\\.([A-Za-z0-9.]+)\"").findAll(lab + canvas).map { "native_labs_canvas_" + it.groupValues[1].replace('.', '_') }.toSet()
        for (k in used) assertTrue(k, k in strings("values"))
        for (s in listOf("bars", "table", "pie", "line", "checklist", "facts", "compare")) assertTrue(s, "native_labs_canvas_view_$s" in strings("values"))
        for (n in 2..4) assertTrue("native_labs_canvas_found_compare$n" in strings("values"))
    }

    @Test fun `one row in the sheet, which says what the rules found before the tap`() {
        val actions = lab.substringAfter("override fun answerActions(").substringBefore("override fun AnswerExtra(")
        // Found off the main thread now (validator R-E4): cardsOf runs CardRules.find on Dispatchers.Default.
        assertTrue(actions.contains("val cards = cardsOf(a.question, a.text)") && lab.contains("withContext(kotlinx.coroutines.Dispatchers.Default) {\n            runCatching { CardRules.find(question, text) }"))
        assertEquals(4, Regex("LabAction\\(").findAll(actions.substringBefore("private fun foundLine(")).count())
        assertTrue(actions.contains("detail = foundLine(cards)") && actions.contains("detail = t(\"native.labs.canvas.askModel\")"))
        // The model only from the "Nothing to draw here" row.
        assertEquals(1, Regex("Canvas\\.start\\(").findAll(lab).count())
        assertTrue(actions.substringAfter("native.labs.canvas.nothingHere").contains("Canvas.start(ctx, a, fallback)"))
        // LabAction has its second line, and the sheet shows it.
        assertTrue(File("../app/src/labs/java/team/sopa/boar/labs/LabPlugin.kt").readText().contains("val detail: String? = null"))
        assertTrue(File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText().contains("SheetRow(act.art, act.emoji, act.label, act.detail,"))
    }

    @Test fun `the card has its title, where it came from, the body, one switch and ✕, and no other button`() {
        val view = lab.substringAfter("internal fun CardView(").substringBefore("\n}\n")
        assertTrue(view.contains("native.labs.canvas.byModel") && view.contains("native.labs.canvas.fromAnswer"))
        assertTrue(view.contains("if (choices.size > 1) FlowRow"))
        assertEquals(1, Regex("\\.clickable\\(").findAll(view).count())
        // Theme tokens only.
        assertTrue(!Regex("Color\\(0x").containsMatchIn(lab))
    }

    @Test fun `the card and its ticks are saved with the answer, in its meta`() {
        assertTrue(canvas.contains("AnswerCard.withCard(it, card)") && canvas.contains("AnswerCard.withTicks(it, now)"))
        assertTrue(canvas.contains("store.setMessageMeta(id, it)") && canvas.contains("AnswerCard.fromMeta(meta)"))
        assertTrue(File("src/main/java/team/sopa/boar/engine/chat/ChatStore.kt").readText().contains("fun messageMeta(messageId: String): String?"))
    }

    @Test fun `the model's card keeps its grounding check and its left-out line`() {
        assertTrue(canvas.contains("Visual.parse(it, grounding(a, context), a.question)"))
        assertTrue(canvas.contains("AnswerCard.ofVisual(parsed.visual, parsed.leftOut, fallbackTitle, a.text)"))
        assertTrue(lab.contains("native.labs.canvas.leftOut"))
    }

    @Test fun `Press takes the shown card along, from the answer's meta`() {
        val printer = File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer")
        val press = File(printer, "PrinterLab.kt").readText()
        assertTrue(press.contains("answerBlock(a.id, title, a.question, a.text, a.sources, a.model, a.tokPerSec, cardOf(ctx, a.id))"))
        assertTrue(press.contains("AnswerCard.fromMeta(team.sopa.boar.app.BoarServices.get(ctx).chats.messageMeta(answerId))"))
        assertTrue(File(printer, "PressStore.kt").readText().contains("AnswerCard.fromMeta(m.meta)"))
        val pdf = File(printer, "PdfCompiler.kt").readText()
        assertTrue(pdf.contains("b.card?.let { card(p, page, it) }"))
        // A pie or a line prints as its table; no image, no share.
        assertTrue(pdf.contains("AnswerCard.asTable(card)") && !pdf.contains("Shape.PIE") && !pdf.contains("Shape.LINE"))
    }
}
