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

    @Test fun `the add toolbar has the text kinds first and scrolls sideways`() {
        val src = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt").readText()
        val bar = src.substringAfter("private fun AddBar(").substringBefore("\n}\n")
        val order = listOf("ic_paragraph", "ic_heading", "ic_list", "ic_divider", "ic_photo", "ic_drawing", "ic_chat").map { bar.indexOf(it) }
        assertTrue(order.toString(), order.all { it > 0 } && order == order.sorted())
        assertTrue(bar.contains("horizontalScroll(rememberScrollState())"))
        val pdf = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PdfCompiler.kt").readText()
        assertTrue(pdf.contains("is Block.Paragraph ->") && pdf.contains("is Block.Bullets ->") && pdf.contains("is Block.Divider ->"))
        for (dir in listOf("values", "values-pt")) {
            val xml = java.io.File("../app/src/labs/plugins/printer/res/$dir/strings_lab_printer.xml").readText()
            for (k in listOf("paragraph", "list", "divider", "paragraphHint", "listHint")) assertTrue("$dir $k", xml.contains("name=\"native_labs_printer_$k\""))
        }
    }
}
