package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.labs.PressDoc.Block

/** adam, Nov 13: the add toolbar gets a paragraph (body text, not a note), a list and a divider. */
class PressMoreBlocksTest {
    private val blocks = listOf(
        Block.Paragraph("t1", "Plain body text."),
        Block.Bullets("l1", "- salt\n• water\n\n  sun  "),
        Block.Divider("d1"),
        Block.Note("n1", "An aside."),
    )

    @Test fun `the new blocks are saved and read back`() {
        val lib = PressDoc.Library("p", listOf(PressDoc.Project("p", "Coast", 1, blocks)))
        assertEquals(blocks, PressDoc.parse(PressDoc.toJson(lib)).current.blocks)
    }

    @Test fun `a list's items drop the marks the user typed and the blank lines`() {
        assertEquals(listOf("salt", "water", "sun"), (blocks[1] as Block.Bullets).items)
        assertEquals("•  salt\n•  water\n•  sun", PressDoc.bulleted("- salt\n• water\n\n  sun  "))
    }

    @Test fun `paragraph and list are edited and flow beside a picture, a divider does neither`() {
        var lib = PressDoc.Library("p", listOf(PressDoc.Project("p", "Coast", 1, blocks)))
        lib = lib.editText("t1", PressDoc.Field.TEXT, "New text.").editText("l1", PressDoc.Field.TEXT, "a\nb")
        assertEquals("New text.", (lib.current.blocks[0] as Block.Paragraph).text)
        assertEquals("a\nb", (lib.current.blocks[1] as Block.Bullets).text)
        assertTrue(PressLayout.flows(blocks[0]) && PressLayout.flows(blocks[1]) && !PressLayout.flows(blocks[2]))
    }

    @Test fun `in the markdown a paragraph is plain text, a list has dashes, a divider a break, a note stays a quote`() {
        val md = PressReceipt.markdown(PressDoc.Project("p", "Coast", 1, blocks), PressReceipt.Labels(), "13 November 2026", "2.0.0-rc3", "Sources", "Drawing")
        assertTrue(md, md.contains("Plain body text.\n\n- salt\n- water\n- sun\n\n* * *\n\n> An aside."))
    }

    @Test fun `the add toolbar is heading, paragraph, list, divider, photo (adam's order), then drawing and chats`() {
        val src = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt").readText()
        val bar = src.substringAfter("private fun AddBar(").substringBefore("\n}\n")
        val order = listOf("ic_heading", "ic_paragraph", "ic_list", "ic_divider", "ic_photo", "ic_drawing", "ic_chat", "ic_page").map { bar.indexOf(it) }
        assertTrue(order.toString(), order.all { it > 0 } && order == order.sorted())
        // Nov 13: the page breaks switch joins them, last, and the row no longer scrolls.
        assertTrue(!bar.contains("horizontalScroll("))
        val pdf = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PdfCompiler.kt").readText()
        assertTrue(pdf.contains("is Block.Paragraph ->") && pdf.contains("is Block.Bullets ->") && pdf.contains("is Block.Divider ->"))
        for (dir in listOf("values", "values-pt")) {
            val xml = java.io.File("../app/src/labs/plugins/printer/res/$dir/strings_lab_printer.xml").readText()
            for (k in listOf("paragraph", "list", "divider", "paragraphHint", "listHint")) assertTrue("$dir $k", xml.contains("name=\"native_labs_printer_$k\""))
        }
    }
}

/** adam, Nov 13: the document's footer is two small lines, not "How this document was made". */
class PressFooterTest {
    private fun answer(id: String, model: String, vararg src: String) = Block.Answer(id, "Chat", "Q?", "A.", src.map { PressDoc.Source(it, "Wikipedia") }, 1, model, 9.5)

    @Test fun `two lines at most - the sources by number, then the models and BOAR`() {
        val p = PressDoc.Project("p", "Coast", 1, listOf(answer("a", "Qwen3 4B", "Salt", "Sea"), answer("b", "LFM2.5 8B", "Sea", "Tide")))
        assertEquals(listOf("Sources  [1] Salt  ·  [2] Sea  ·  [3] Tide", "Qwen3 4B  ·  LFM2.5 8B  ·  Made offline with BOAR · 13 Nov"),
            PressReceipt.footer(PressReceipt.build(p), PressReceipt.Labels(), "13 Nov"))
    }

    @Test fun `no sources, one line`() {
        val p = PressDoc.Project("p", "Coast", 1, listOf(Block.Paragraph("t", "Mine.")))
        assertEquals(listOf("Made offline with BOAR · 13 Nov"), PressReceipt.footer(PressReceipt.build(p), PressReceipt.Labels(), "13 Nov"))
    }

    @Test fun `the paper and the PDF draw the footer, not the old receipt rows`() {
        val dir = "../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/"
        val pdf = java.io.File(dir + "PdfCompiler.kt").readText().substringAfter("private fun receipt(").substringBefore("\n    }\n")
        val scr = java.io.File(dir + "PressScreen.kt").readText().substringAfter("private fun ReceiptView(").substringBefore("\n}\n")
        for (x in listOf(pdf, scr)) assertTrue(x.contains("footer") && !x.contains("labels.title") && !x.contains("rows"))
        for (d in listOf("values", "values-pt")) assertTrue(java.io.File("../app/src/labs/plugins/printer/res/$d/strings_lab_printer.xml").readText().contains("name=\"native_labs_printer_madeWith\""))
    }
}
