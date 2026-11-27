package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * t9 0735 (adam, 27 Nov): a Press PDF page read thin, about 29 lines. Paragraph breaks no longer cost a whole empty line
 * (a small gap instead), list items with blank lines between them close up, the body is 12 pt (adam's pick: 16 pt
 * could only reach about 31 lines) at 1.15 spacing; the paper on screen follows the same measures.
 */
class PressSpacingTest {
    private val app = "../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer"
    private fun read(f: String) = java.io.File("$app/$f").readText()

    @Test fun `paragraphs split on blank lines, with no empty line left in any`() {
        assertEquals(listOf("One.", "Two, line a\nline b.", "Three."), PressSpacing.paragraphs("One.\n\nTwo, line a\nline b.\n\n\n  \nThree.\n"))
        assertEquals(listOf("Alone."), PressSpacing.paragraphs("  Alone.  "))
        assertEquals(emptyList<String>(), PressSpacing.paragraphs("\n\n  \n"))
        for (p in PressSpacing.paragraphs("a\n\nb\n\n\nc")) assertTrue(p, "\n\n" !in p && p.isNotBlank())
    }

    @Test fun `a list with blank lines between its items stays one block, no gap between items`() {
        val text = "Pack these:\n\n- Water\n\n- Map\n\n• Torch\n\n1. Boil\n\n2) Cool\n\nThen go."
        assertEquals(listOf("Pack these:", "- Water\n- Map\n• Torch\n1. Boil\n2) Cool", "Then go."), PressSpacing.paragraphs(text))
    }

    @Test fun `the PDF's measures - 12 pt body, 1_15 spacing, a 6 pt gap between paragraphs, margins and page kept`() {
        val paper = read("PressPage.kt").substringAfter("object Paper {").substringBefore("\n}\n")
        for (c in listOf("const val BODY = 12f", "const val PDF_SPACING = 1.15f", "const val PARA_GAP = 6f", "const val LINE = 1.333f", "const val W = 595f", "const val H = 842f", "const val M = 40f"))
            assertTrue(c, paper.contains(c))
    }

    @Test fun `the PDF and the paper on screen both lay text out paragraph by paragraph`() {
        val pdf = read("PdfCompiler.kt")
        assertTrue(pdf.contains("fun paragraphs(s: String, paint: TextPaint, after: Float, spacing: Float)"))
        for (w in listOf("p.paragraphs(page.answerText(b), serif(Paper.BODY), after = 4f, spacing = Paper.PDF_SPACING)",
                "p.paragraphs(b.text.trim(), serif(Paper.BODY), after = Paper.GAP, spacing = Paper.PDF_SPACING)",
                "p.paragraphs(team.sopa.boar.engine.labs.PressDoc.bulleted(b.text), serif(Paper.BODY), after = Paper.GAP, spacing = Paper.PDF_SPACING)"))
            assertTrue(w, pdf.contains(w))
        val screen = read("PressScreen.kt")
        assertTrue(screen.contains("PressSpacing.paragraphs(page.answerText(b))") && screen.contains("Paper.PARA_GAP"))
        assertTrue(!screen.contains("else Text(page.answerText(b), style = s.body)"))
    }
}
