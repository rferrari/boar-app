package team.sopa.boar.engine.labs

import org.junit.Assert.assertTrue
import org.junit.Test

/** Press laid out as Designer (adam, Nov 12): the page, a caption line, a strip of things to add, the chat's own input bar. */
class PressDesignerLayoutTest {
    private val s = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt").readText()
    private val screen = s.substringAfter("var note by remember").substringBefore("if (focusTitle)")

    @Test fun `the page, then the caption line, the strip and the shared input bar, in that order`() {
        val at = listOf("PaperView(", "CaptionLine(project", "AddStrip(project", "team.sopa.boar.ui.chat.PromptBar(note").map { screen.indexOf(it) }
        assertTrue(at.toString(), at.all { it >= 0 } && at == at.sorted())
        assertTrue(!s.contains("private fun BottomBar(") && !s.contains("private fun ProjectBar("))
    }

    @Test fun `the input bar writes a note, its left button opens the project menu, the mic is the chat's`() {
        val bar = screen.substringAfter("team.sopa.boar.ui.chat.PromptBar(note")
        assertTrue(bar.contains("addBlock(Block.Note(PressStore.newId(\"n\"), note.trim())); note = \"\""))
        assertTrue(bar.contains("onLeft = { sheet = Sheet.PROJECTS }") && bar.contains("voiceEnabled = voice") && bar.contains("queues = false"))
        assertTrue(bar.contains("placeholder = t(\"native.labs.printer.notePlaceholder\")"))
    }

    @Test fun `Save PDF stays one tap away on the caption line, with the pages from the PDF's own layout`() {
        assertTrue(screen.contains("CaptionLine(project, pages = pageStarts.size + 1, canSave = project.blocks.isNotEmpty() && !compiling, onSave = ::savePdf)"))
        val cap = s.substringAfter("private fun CaptionLine(").substringBefore("\n}\n")
        assertTrue(cap.contains("background(GOLD)") && cap.contains("onClickLabel = save, onClick = onSave"))
    }

    @Test fun `the strip adds a photo, a heading, a chat's answers or a drawing, and the menu's Add to Press uses the same code`() {
        val strip = s.substringAfter("private fun AddStrip(").substringBefore("\n@Composable\nprivate fun AddSheet(")
        for (x in listOf("onPhoto", "onHeading", "onChat(id, title)", "onDrawing(f)", "onAllDrawings")) assertTrue(x, strip.contains(x))
        assertTrue(screen.contains("onChat = { id, title -> PressStore.addChat(ctx, id, title) }"))
        val lab = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PrinterLab.kt").readText()
        assertTrue(lab.contains("PressStore.addChat(ctx, sessionId, title)"))
        for (d in listOf("values", "values-pt")) {
            val x = java.io.File("../app/src/labs/plugins/printer/res/$d/strings_lab_printer.xml").readText()
            for (k in listOf("notePlaceholder", "addNote", "pages", "photo", "heading", "allDrawings")) assertTrue("$d $k", x.contains("name=\"native_labs_printer_$k\""))
        }
    }
}
