package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.labs.PressDoc.Block

/** The end of every Press document: how it was made, and one numbered list of sources matching the answers' [n]. */
class PressReceiptTest {
    private fun src(t: String, s: String? = "Wikipedia") = PressDoc.Source(t, s)

    private val project = PressDoc.Project("p1", "Field notes", 0, listOf(
        Block.Heading("h1", "Day 1: the coast"),
        Block.Answer("a1", "Coast", "Why is the sea salty?", "Evaporation leaves salt [2], rivers bring it [1].", listOf(src("Seawater"), src("Red Sea")), 1, "Qwen3-4B", 4.6),
        Block.Picture("pic", PressDoc.Kind.DRAWING, "/d.png", recipe = PressDoc.Recipe("a crab", "engraving", "384", 48213), width = 384, height = 512),
        Block.Answer("a2", "Coast", "What is a tide pool?", "Rock basins [1].", listOf(src("Tide pool"), src("Seawater")), 2, "Qwen3-4B", 4.8, heading = "Tide pools"),
        Block.Note("n1", "Ask about the lighthouse."),
        Block.Answer("a3", "Water", "What is evaporation?", "Water turns into vapour.", listOf(src("Evaporation", null)), 3, "Qwen3-1.7B", 9.1),
        Block.Answer("a4", "Calc", "2+2", "4", emptyList(), 4, null, null),
    ))

    @Test fun `the answers' refs are the receipt's list, one number per source`() {
        val r = PressReceipt.build(project)
        assertEquals(listOf("Seawater", "Red Sea", "Tide pool", "Evaporation"), r.sources.map { it.title })
        assertEquals(listOf(1, 2), r.refs["a1"])
        // Seawater is cited again: the same number, not a new line.
        assertEquals(listOf(3, 1), r.refs["a2"])
        assertEquals(listOf(4), r.refs["a3"])
        assertEquals(emptyList<Int>(), r.refs["a4"])
        // Every [n] under an answer is a line of the list, and every line is cited.
        val cited = r.refs.values.flatten().toSet()
        assertEquals((1..r.sources.size).toSet(), cited)
        val rows = PressReceipt.rows(r, PressReceipt.Labels(), "13 November 2026", "2.0.0-rc3")
        val list = rows.single { it.numbered }
        assertEquals(r.sources.map(PressReceipt::sourceText), list.lines)
        assertEquals("Seawater — Wikipedia", list.lines[0])
        assertEquals("Evaporation", list.lines[3])
        assertEquals("Sources [3] [1]", PressReceipt.refsLine(r.refs["a2"], "Sources"))
        assertNull(PressReceipt.refsLine(r.refs["a4"], "Sources"))
    }

    @Test fun `an answer's own marks become the document's numbers`() {
        val r = PressReceipt.build(project)
        assertEquals("Evaporation leaves salt [2], rivers bring it [1].", PressReceipt.renumber("Evaporation leaves salt [2], rivers bring it [1].", r.refs.getValue("a1")))
        assertEquals("Rock basins [3].", PressReceipt.renumber("Rock basins [1].", r.refs.getValue("a2")))
        // A mark with no source behind it is taken out: it would read as another answer's source (validator G-5b).
        assertEquals("See.", PressReceipt.renumber("See [7].", r.refs.getValue("a2")))
    }

    @Test fun `the receipt is always there, and names models, speeds and drawings`() {
        // Even an empty document ends with how it was made.
        val empty = PressReceipt.rows(PressReceipt.build(PressDoc.Project("e", "Empty", 0)), PressReceipt.Labels(), "13 November 2026", "2.0.0-rc3")
        assertEquals(listOf("Made"), empty.map { it.label })
        assertEquals("13 November 2026, offline, on this phone · BOAR 2.0.0-rc3", empty[0].lines.single())

        val r = PressReceipt.build(project)
        assertEquals(4, r.answers)
        val rows = PressReceipt.rows(r, PressReceipt.Labels(), "13 November 2026", "2.0.0-rc3")
        assertEquals(listOf("Made", "Answers", "Drawings", "Sources"), rows.map { it.label })
        assertEquals(listOf("4", "Qwen3-4B · 4.6–4.8 tok/s", "Qwen3-1.7B · 9.1 tok/s"), rows[1].lines)
        assertEquals(listOf("Designer · Engraving · portrait, medium quality · seed 48213"), rows[2].lines)
        // Photos (phase 2) count on their own line.
        val withPhoto = project.copy(blocks = project.blocks + Block.Picture("ph", PressDoc.Kind.PHOTO, "photos/1.jpg"))
        assertEquals(listOf("1 from this phone"), PressReceipt.rows(PressReceipt.build(withPhoto), PressReceipt.Labels(), "d", "v").single { it.label == "Photos" }.lines)
    }

    @Test fun `a drawing without a recipe still counts, with what is known`() {
        val p = PressDoc.Project("p", "P", 0, listOf(Block.Picture("x", PressDoc.Kind.DRAWING, "/x.png")))
        val r = PressReceipt.build(p, recipeOf = { null }, sizeOf = { 640 to 320 })
        assertEquals("Designer · wide, medium quality", PressReceipt.drawingText(r.drawings.single(), PressReceipt.Labels()))
        // The app's reader wins over the block's copy when it finds one.
        val r2 = PressReceipt.build(p, recipeOf = { PressDoc.Recipe(style = "watercolor", seed = 7) }, sizeOf = { 384 to 384 })
        assertEquals("Designer · Watercolor · square, low quality · seed 7", PressReceipt.drawingText(r2.drawings.single(), PressReceipt.Labels()))
    }

    @Test fun `the Markdown has the blocks, the refs and the same receipt`() {
        val md = PressReceipt.markdown(project, PressReceipt.Labels(), "13 November 2026", "2.0.0-rc3", "Sources", "Drawing")
        assertTrue(md.startsWith("# Field notes\n\n_13 November 2026_"))
        assertTrue(md.contains("## Day 1: the coast"))
        assertTrue(md.contains("### Tide pools\n\n_What is a tide pool?_\n\nRock basins [3]."))
        // The heading is the question: no italic repeat.
        assertFalse(md.contains("_Why is the sea salty?_"))
        assertTrue(md.contains("Sources [3] [1]"))
        assertTrue(md.contains("> Ask about the lighthouse."))
        // Nov 13: no "How this document was made"; a two-line footer, the sources by number, then the models and BOAR.
        assertFalse(md.contains("HOW THIS DOCUMENT WAS MADE") || md.contains("tok/s"))
        assertTrue(md, md.trimEnd().endsWith("---\n\n_Sources  [1] Seawater  ·  [2] Red Sea  ·  [3] Tide pool  ·  [4] Evaporation_  \n_Qwen3-4B  ·  Qwen3-1.7B  ·  Made offline with BOAR · 13 November 2026_"))
        // No model line under the answers: models are in the footer only.
        assertEquals(1, Regex("Qwen3-4B").findAll(md).count())
    }

    @Test fun `shapes`() {
        assertEquals(PressReceipt.Shape.SQUARE, PressReceipt.Shape.of(384, 384))
        assertEquals(PressReceipt.Shape.PORTRAIT, PressReceipt.Shape.of(384, 512))
        assertEquals(PressReceipt.Shape.WIDE, PressReceipt.Shape.of(640, 320))
        assertNull(PressReceipt.Shape.of(0, 10))
    }
}

class PressReceiptWordsTest {
    @Test fun `a drawing's line says its shape and quality, never pixels`() {
        val l = PressReceipt.Labels()
        val fast = PressReceipt.drawingText(PressReceipt.Drawing("photo", PressReceipt.Shape.SQUARE, 512, 512, 7), l)
        val slow = PressReceipt.drawingText(PressReceipt.Drawing("photo", PressReceipt.Shape.WIDE, 768, 320, null), l)
        org.junit.Assert.assertTrue(fast, fast.contains("square, medium quality") && !Regex("\\d+\\s*[x×]\\s*\\d+").containsMatchIn(fast))
        org.junit.Assert.assertTrue(slow, slow.contains("wide, high quality"))
    }
}
