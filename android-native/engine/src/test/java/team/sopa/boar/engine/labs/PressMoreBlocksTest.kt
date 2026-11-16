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
        val order = listOf("ic_heading", "ic_paragraph", "ic_list", "ic_divider", "ic_photo", "ic_drawing", "ic_chat").map { bar.indexOf(it) }
        assertTrue(order.toString(), order.all { it > 0 } && order == order.sorted())
        // The row never scrolls (Nov 13); Press settings are the header's gear (Nov 14).
        assertTrue(!bar.contains("horizontalScroll("))
        val pdf = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PdfCompiler.kt").readText()
        assertTrue(pdf.contains("is Block.Paragraph ->") && pdf.contains("is Block.Bullets ->") && pdf.contains("is Block.Divider ->"))
        for (dir in listOf("values", "values-pt")) {
            val xml = java.io.File("../app/src/labs/plugins/printer/res/$dir/strings_lab_printer.xml").readText()
            for (k in listOf("paragraph", "list", "divider", "paragraphHint", "listHint")) assertTrue("$dir $k", xml.contains("name=\"native_labs_printer_$k\""))
        }
    }
}

/**
 * adam, Nov 14: "split what is sources, what is model, and made with boar… a sources session on end of document", with
 * switches in Press settings for the date, the sources, the models and their speed; "Made with BOAR" always prints.
 */
class PressFooterTest {
    private fun answer(id: String, model: String, tps: Double, vararg src: String) =
        Block.Answer(id, "Chat", "Q?", "A [1].", src.map { PressDoc.Source(it, "Wikipedia") }, 1, model, tps)
    private val p = PressDoc.Project("p", "Coast", 1, listOf(answer("a", "Qwen3 4B", 9.5, "Salt", "Sea"), answer("b", "LFM2.5 8B", 12.0, "Sea", "Tide")))
    private val r = PressReceipt.build(p)

    @Test fun `the sources are their own section, each with its pack`() {
        assertEquals(listOf("[1] Salt — Wikipedia", "[2] Sea — Wikipedia", "[3] Tide — Wikipedia"), PressReceipt.sourceLines(r, PressReceipt.Options()))
        assertTrue(PressReceipt.sourceLines(r, PressReceipt.Options(sources = false)).isEmpty())
    }

    @Test fun `the user's own file names never print, a pack's source and link do (validator A-1)`() {
        val mine = Block.Answer("m", "Chat", "Q?", "A [1] [2] [3].", listOf(
            PressDoc.Source("Imposto 2025", "imposto-2025-joao-silva.pdf"),
            PressDoc.Source("Nosebleed", "Wikipedia — https://en.wikipedia.org/wiki/Nosebleed (CC BY-SA 4.0)"),
            PressDoc.Source("Notes", "  "),
        ), 1, "Qwen3 4B", 9.5)
        val lines = PressReceipt.sourceLines(PressReceipt.build(PressDoc.Project("q", "Mine", 1, listOf(mine))), PressReceipt.Options())
        // Golden change (validator R-A1): a user's file prints a neutral line, not its title (the file name, often).
        assertEquals(listOf("[1] Your own file", "[2] Nosebleed — Wikipedia — https://en.wikipedia.org/wiki/Nosebleed (CC BY-SA 4.0)", "[3] Notes"), lines)
        // The shapes a file name can take never pass.
        for (f in listOf("notes.txt", "Relatório final.docx", "a — b.pdf", "IMG_2041", "Wikipedia notes.pdf")) assertTrue(f, !PressReceipt.shareable(f))
        for (s in listOf("Wikipedia", "Wikivoyage — https://en.wikivoyage.org/wiki/Lisbon")) assertTrue(s, PressReceipt.shareable(s))
    }

    @Test fun `the footer is the models on one line and Made with BOAR on the next, always`() {
        val l = PressReceipt.Labels()
        assertEquals(listOf("Qwen3 4B  ·  LFM2.5 8B", "Made offline with BOAR · 13 Nov"), PressReceipt.footer(r, l, "13 Nov", PressReceipt.Options()))
        assertEquals(listOf("Qwen3 4B (9.5 tok/s)  ·  LFM2.5 8B (12.0 tok/s)", "Made offline with BOAR · 13 Nov"), PressReceipt.footer(r, l, "13 Nov", PressReceipt.Options(speed = true)))
        assertEquals(listOf("Made offline with BOAR · 13 Nov"), PressReceipt.footer(r, l, "13 Nov", PressReceipt.Options(models = false, speed = true)))
        val none = PressDoc.Project("p", "Coast", 1, listOf(Block.Paragraph("t", "Mine.")))
        assertEquals(listOf("Made offline with BOAR · 13 Nov"), PressReceipt.footer(PressReceipt.build(none), l, "13 Nov", PressReceipt.Options()))
    }

    @Test fun `in the markdown, the date and the sources follow the switches, and with sources off no mark is left`() {
        fun md(o: PressReceipt.Options) = PressReceipt.markdown(p, PressReceipt.Labels(), "13 November 2026", "v", "Sources", "Drawing", r, o = o)
        val on = md(PressReceipt.Options())
        assertTrue(on, on.contains("_13 November 2026_") && on.contains("## Sources\n\n[1] Salt — Wikipedia  \n[2] Sea — Wikipedia") && on.contains("A [1]."))
        val off = md(PressReceipt.Options(date = false, sources = false))
        assertTrue(off, !off.contains("_13 November 2026_") && !off.contains("## Sources") && !off.contains("[1]") && off.contains("_Made offline with BOAR · 13 November 2026_"))
    }

    @Test fun `the paper and the PDF draw the footer, not the old receipt rows`() {
        val dir = "../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/"
        val pdf = java.io.File(dir + "PdfCompiler.kt").readText().substringAfter("private fun receipt(").substringBefore("\n    }\n")
        val scr = java.io.File(dir + "PressScreen.kt").readText().substringAfter("private fun ReceiptView(").substringBefore("\n}\n")
        for (x in listOf(pdf, scr)) assertTrue(x.contains("footer") && !x.contains("labels.title") && !x.contains("rows"))
        // The Sources section before the footer, on the paper and in the PDF, and the date only when on.
        val compiler = java.io.File(dir + "PdfCompiler.kt").readText()
        assertTrue(compiler.contains("sources(p, page)\n            p.begin(RECEIPT)") && compiler.contains("if (page.options.date) p.text(page.date.uppercase()"))
        val screen = java.io.File(dir + "PressScreen.kt").readText()
        assertTrue(screen.contains("if (page.sourceLines.isNotEmpty()) Column(Modifier.unit(PdfCompiler.SOURCES)") && screen.contains("if (page.options.date) Text(page.date.uppercase()"))
        val sheet = screen.substringAfter("private fun SettingsSheet(").substringBefore("\n}\n")
        for (k in listOf("pageBreaks", "showDate", "showSources", "showModels", "showSpeed", "madeAlways")) assertTrue(k, sheet.contains("native.labs.printer.$k"))
        for (d in listOf("values", "values-pt")) for (k in listOf("settings", "showDate", "showSources", "showModels", "showSpeed", "madeAlways"))
            assertTrue("$d $k", java.io.File("../app/src/labs/plugins/printer/res/$d/strings_lab_printer.xml").readText().contains("name=\"native_labs_printer_$k\""))
        for (d in listOf("values", "values-pt")) assertTrue(java.io.File("../app/src/labs/plugins/printer/res/$d/strings_lab_printer.xml").readText().contains("name=\"native_labs_printer_madeWith\""))
    }
}

/** t9 0110 item 4: a note added from Press's bar stayed selected (and open, all its text selected): the next key replaced it. */
class PressBarNoteTest {
    private val screen = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt").readText()

    @Test fun `a note from the bar is placed but neither selected nor opened, and the next one follows it`() {
        assertTrue(screen.contains("else addNote(Block.Note(PressStore.newId(\"n\"), typed))"))
        val add = screen.substringAfter("fun addNote(b: Block.Note) {").substringBefore("\n    }")
        for (w in listOf("addBlock(b)", "selected = null", "editing = null", "fresh = null", "barNote = b.id")) assertTrue(w, add.contains(w))
        assertTrue(screen.contains("val anchor = selected ?: barNote"))
    }
}

/** Validator R-A1: a user's file name never prints in a shared PDF or .md, through its title either. */
class PressOwnFileNameTest {
    @Test fun `the Sources section, the receipt and the Markdown never print a user's file title`() {
        val mine = Block.Answer("m", "Chat", "Q?", "A [1] [2].", listOf(
            PressDoc.Source("imposto-2025-joao-silva", "imposto-2025-joao-silva.pdf"),
            PressDoc.Source("Tax", "Wikipedia — https://en.wikipedia.org/wiki/Tax"),
        ), 1, "Qwen3 4B", 9.5)
        val p = PressDoc.Project("q", "Mine", 1, listOf(mine))
        val r = PressReceipt.build(p)
        val lines = PressReceipt.sourceLines(r, PressReceipt.Options(), "Arquivo seu")
        assertEquals(listOf("[1] Arquivo seu", "[2] Tax — Wikipedia — https://en.wikipedia.org/wiki/Tax"), lines)
        val md = PressReceipt.markdown(p, PressReceipt.Labels(), "today", "x", "Sources", "Drawing", r, "Photo", PressReceipt.Options())
        assertTrue(md, "joao-silva" !in md)
        val canvas = java.io.File("../app/src/labs/plugins/canvas/java/team/sopa/boar/labs/canvas/CanvasLab.kt").readText()
        assertTrue(!canvas.contains("a.sources.map { it.first }") && canvas.contains("PressReceipt.shareable(src)"))
    }
}

