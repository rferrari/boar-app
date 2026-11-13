package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 1721, after the F3 check of all28/all29: the Designer drawer, the rewrite targets, the caption icon, the receipt. */
class All29FollowupsTest {
    private fun app(p: String) = java.io.File("../app/src/labs/plugins/$p").readText()

    @Test fun `the drawer is fully open or closed, its chips take no taps while it closes, and it never reopens by itself`() {
        val s = app("designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt")
        assertTrue(s.contains("var drawer by remember { mutableStateOf(false) }") && !s.contains("var drawer by rememberSaveable"))
        val d = s.substringAfter("private fun DesignerDrawer(").substringBefore("\n/** Style:")
        assertTrue(d.contains("rememberModalBottomSheetState(skipPartiallyExpanded = true)") && d.contains("sheetState = sheet"))
        assertTrue(d.contains("val live = sheet.targetValue != androidx.compose.material3.SheetValue.Hidden"))
        assertTrue(d.contains("if (!live) Box(Modifier.matchParentSize().pointerInput(Unit)") && d.contains("it.consume()"))
        // Opened from the left button only; the hint is plain text.
        assertEquals(1, Regex("drawer = true").findAll(s).count())
        assertTrue(s.contains("onLeft = { if (ready && !busy) drawer = true }"))
    }

    @Test fun `each quick action has a concrete target`() {
        val text = "The sea is salty because seawater contains minerals and salts that accumulate over time [1]. These salts come from rocks [2]."
        assertEquals(19, PressRewrite.words(text))
        assertEquals(9, PressRewrite.shorterTarget(text))
        assertEquals(8, PressRewrite.shorterTarget("One two three."))
        val en = PressRewrite.instruction(PressRewrite.Option.SHORTER, null, false, text)
        assertTrue(en, en.contains("at most 9 words") && en.contains("it has 19 now"))
        val pt = PressRewrite.instruction(PressRewrite.Option.SHORTER, null, true, text)
        assertTrue(pt, pt.contains("no máximo 9 palavras"))
        assertTrue(PressRewrite.instruction(PressRewrite.Option.SIMPLER, null, false, text).contains("everyday words") &&
            PressRewrite.instruction(PressRewrite.Option.SIMPLER, null, false, text).contains("The same facts"))
        assertTrue(PressRewrite.instruction(PressRewrite.Option.FIX, null, false, text).contains("The same length (about 19 words)"))
        val user = PressRewrite.prompt(PressRewrite.Option.SHORTER, text).user
        assertTrue(user, user.startsWith("PASSAGE:\n") && user.trimEnd().endsWith("cut the rest.") && user.contains("TASK: Rewrite it in at most 9 words"))
    }

    @Test fun `the same text back says No change instead of NOW and REWRITTEN`() {
        assertTrue(PressRewrite.unchanged("The sea  is salty [1].", " the sea is salty [1]. "))
        assertFalse(PressRewrite.unchanged("The sea is salty [1].", "Seawater is salty [1]."))
        val runner = app("printer/java/team/sopa/boar/labs/printer/PressRewriter.kt")
        assertTrue(runner.indexOf("if (PressRewrite.unchanged(text, out)) return Outcome.Unchanged") in 0 until runner.indexOf("return Outcome.Done("))
        assertTrue(app("printer/java/team/sopa/boar/labs/printer/PressScreen.kt").contains("PressRewriter.Outcome.Unchanged -> line = I18n.t(ctx, \"native.labs.printer.rwNoChange\")"))
        for (d in listOf("values", "values-pt")) assertTrue(d, app("printer/res/$d/strings_lab_printer.xml").contains("name=\"native_labs_printer_rwNoChange\""))
    }

    @Test fun `the caption has its own glyph and shows open with a dot, the gold ring is the placement's alone`() {
        val icon = app("printer/res/drawable/lab_printer_ic_caption.xml")
        val center = app("printer/res/drawable/lab_printer_ic_center.xml")
        assertTrue(icon.contains("M4,5h10M9,5v10") && !icon.contains(center.substringAfter("pathData=\"").substringBefore("\"")))
        val s = app("printer/java/team/sopa/boar/labs/printer/PressScreen.kt")
        assertTrue(s.contains("lab_printer_ic_caption, t(if (editing) \"native.labs.printer.doneCaption\" else \"native.labs.printer.caption\"), dot = editing, onClick = onCaption)"))
        assertTrue(s.contains("if (dot) Box(Modifier.align(Alignment.TopEnd)"))
    }

    @Test fun `the receipt is kept together in the PDF, and no page marker runs through it on screen`() {
        val pdf = app("printer/java/team/sopa/boar/labs/printer/PdfCompiler.kt")
        val r = pdf.substringAfter("private fun receipt(").substringBefore("p.shortRule()")
        assertTrue(r.contains("p.keepRoom(minOf(total, Paper.H - 2 * Paper.M))") && r.contains("for (line in lines)"))
        val s = app("printer/java/team/sopa/boar/labs/printer/PressScreen.kt")
        assertTrue(s.contains("Box(Modifier.unit(PdfCompiler.RECEIPT))"))
    }

    @Test fun `the page lines on the paper are the PDF's own breaks, and they can be switched off`() {
        val pdf = app("printer/java/team/sopa/boar/labs/printer/PdfCompiler.kt")
        // One layout for both: compile draws, pageStarts only measures (no picture decoded).
        assertTrue(pdf.contains("layout(Pager(doc, dry = false), page, fileOf)") && pdf.contains("Pager(doc, dry = true).also { layout(it, page, fileOf) }.starts"))
        assertTrue(pdf.contains("if (!dry) return BitmapFactory.decodeFile(file.path)") && pdf.contains("inJustDecodeBounds = true"))
        val newPage = pdf.substringAfter("private fun newPage()").substringBefore("return doc.startPage(")
        assertTrue(newPage.contains("starts += PageStart(unit, into)"))
        for (b in listOf("p.begin(TITLE)", "p.begin(b.id)", "p.begin(item.picture.id)", "item.text?.let { p.begin(it.id)", "p.begin(RECEIPT)")) assertTrue(b, pdf.contains(b))
        val s = app("printer/java/team/sopa/boar/labs/printer/PressScreen.kt")
        assertTrue(s.contains("PdfCompiler.pageStarts(page) { b -> PressPictures.fileOf(ctx, page.project.id, b) }"))
        assertTrue(s.contains("PaperView(page, if (showBreaks) pageStarts else emptyList(),"))
        assertTrue(s.contains("val y = if (st.intoPt <= 1f) top - 4.dp.toPx() else top + minOf(st.intoPt * s.pxPerPt, height)"))
        assertTrue(s.contains("val top = inner + Paper.M * s.pxPerPt"))
        assertTrue(!s.contains("val step = (Paper.H - 2 * Paper.M) * s.pxPerPt"))
        assertTrue(s.contains("team.sopa.boar.labs.LabPrefs.putString(BREAKS_PREF, if (on) \"on\" else \"off\")"))
        for (d in listOf("values", "values-pt")) assertTrue(d, app("printer/res/$d/strings_lab_printer.xml").contains("name=\"native_labs_printer_pageBreaks\""))
    }
}
