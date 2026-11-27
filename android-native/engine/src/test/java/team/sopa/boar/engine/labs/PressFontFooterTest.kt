package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * adam, 27 Nov: "can we add 3 fonts to be selected by user?" and, after a sample PDF (a light rule and 7.5 pt grey text at
 * the foot of each page, "Page N" on the right), "its possible to add footer and page on pdf settings?".
 */
class PressFontFooterTest {
    private val app = "../app/src/labs/plugins/printer"
    private fun src(f: String) = java.io.File("$app/java/team/sopa/boar/labs/printer/$f").readText()
    private fun strings(d: String) = java.io.File("$app/res/$d/strings_lab_printer.xml").readText()

    @Test fun `three fonts, Serif by default, read back from their keys`() {
        assertEquals(listOf("serif", "sans", "mono"), PressFont.entries.map { it.key })
        assertEquals(PressFont.SANS, PressFont.of("sans")); assertEquals(PressFont.SERIF, PressFont.of(null)); assertEquals(PressFont.SERIF, PressFont.of("comic"))
        val o = PressReceipt.Options()
        assertEquals(PressFont.SERIF, o.font); assertTrue(o.pageNumbers); assertTrue(!o.pageFooter)
    }

    @Test fun `the settings are kept with the other Press settings`() {
        val page = src("PressPage.kt")
        for (k in listOf("const val FONT_PREF = \"printer.font\"", "const val PAGE_NUMBERS_PREF = \"printer.pageNumbers\"", "const val FOOTER_PREF = \"printer.pageFooter\""))
            assertTrue(k, page.contains(k))
        val save = page.substringAfter("fun saveOptions(o: PressReceipt.Options) {").substringBefore("\n        }\n")
        assertTrue(save.contains("FONT_PREF, o.font.key") && save.contains("PAGE_NUMBERS_PREF to o.pageNumbers") && save.contains("FOOTER_PREF to o.pageFooter"))
    }

    @Test fun `the PDF and the paper on screen use the picked font`() {
        val pdf = src("PdfCompiler.kt")
        assertTrue(pdf.contains("private val face = ThreadLocal<Typeface>()") && pdf.contains("face.set(typefaceOf(page.options.font))"))
        val serif = pdf.substringAfter("private fun serif(").substringBefore("\n    }\n")
        assertTrue(serif.contains("Typeface.create(face.get() ?: Typeface.SERIF,"))
        val screen = src("PressScreen.kt")
        assertTrue(screen.contains("PaperScale(with(density) { maxWidth.toPx() } / Paper.W, density, familyOf(page.options.font))"))
    }

    @Test fun `each page's foot - a light rule, the project's name and Page N, as the sample`() {
        val pdf = src("PdfCompiler.kt")
        val finish = pdf.substringAfter("private fun finishPage(pg: PdfDocument.Page) {").substringBefore("\n        }\n")
        assertTrue(finish.contains("if (foot.line)") && finish.contains("if (foot.numbers)") && finish.contains("Paper.PAGE_FOOT"))
        assertTrue(src("PressPage.kt").contains("const val PAGE_FOOT = 7.5f"))
    }

    @Test fun `the settings sheet has the font and the two switches, English and Portuguese`() {
        val sheet = src("PressScreen.kt").substringAfter("private fun SettingsSheet(").substringBefore("\n}\n")
        assertTrue(sheet.contains("PressFont.entries") && sheet.contains("t(\"native.labs.printer.pageNumbers\")") && sheet.contains("t(\"native.labs.printer.pageFooter\")"))
        for (d in listOf("values", "values-pt")) for (k in listOf("font", "font_serif", "font_sans", "font_mono", "pageNumbers", "pageFooter", "pageFooterNote"))
            assertTrue("$d $k", strings(d).contains("name=\"native_labs_printer_$k\""))
    }
}
