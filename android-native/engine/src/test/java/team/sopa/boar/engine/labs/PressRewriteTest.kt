package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Press phase 3, "Ask BOAR to rewrite it" (t9 0625 item 4): prompts, clean-up, source marks, and the app's wiring. */
class PressRewriteTest {
    private val en = "Where the sun evaporates a lot of water and few rivers flow in, salt is left behind [1]. The Red Sea is one of the saltiest [2]."
    private val pt = "Onde o sol evapora muita água e poucos rios chegam, o sal fica para trás [1]. O Mar Vermelho é um dos mais salgados [2]."

    @Test fun `each option asks for its change, in the text's language, and keeps the marks`() {
        for (o in PressRewrite.Option.entries.filter { it != PressRewrite.Option.OWN }) {
            val p = PressRewrite.prompt(o, en, own = "for a 10-year-old")
            assertTrue(o.name, p.system.contains("Keep every source mark such as [1] or [2] exactly as written"))
            assertTrue(o.name, p.user.startsWith("PASSAGE:\n$en\n\nTASK: "))
            assertTrue(o.name, p.nPredict in 96..1024)
            val q = PressRewrite.prompt(o, pt, own = "para uma criança")
            assertTrue(o.name, q.system.contains("Mantenha cada marca de fonte"))
            assertTrue(o.name, q.user.startsWith("TRECHO:\n$pt\n\nTAREFA: "))
        }
        assertTrue(PressRewrite.prompt(PressRewrite.Option.SHORTER, en).user.substringAfter("TASK: ").startsWith("Rewrite it in at most"))
        assertTrue(PressRewrite.prompt(PressRewrite.Option.SIMPLER, en).user.substringAfter("TASK: ").startsWith("Rewrite it with short, everyday words"))
        assertTrue(PressRewrite.prompt(PressRewrite.Option.FIX, en).user.substringAfter("TASK: ").startsWith("Fix only the grammar"))
        assertTrue(PressRewrite.prompt(PressRewrite.Option.LIST, pt).user.substringAfter("TAREFA: ").startsWith("Transforme em uma lista"))
        assertTrue(PressRewrite.prompt(PressRewrite.Option.OWN, en, own = "  for a 10-year-old ").user.endsWith("INSTRUCTION: for a 10-year-old"))
        // The language can come from the answer's question when the text alone is too short to tell.
        assertTrue(PressRewrite.prompt(PressRewrite.Option.FIX, "Sim [1].", languageOf = "Por que o mar é salgado? Sim [1].").system.startsWith("Você"))
        // A list gets more room than a shorter version.
        assertTrue(PressRewrite.prompt(PressRewrite.Option.LIST, en.repeat(10)).nPredict > PressRewrite.prompt(PressRewrite.Option.SHORTER, en.repeat(10)).nPredict)
    }

    @Test fun `the reasoning and labels are stripped`() {
        assertEquals("Salt stays behind [1].", PressRewrite.clean("<think>The user wants it shorter.</think>\n\nSalt stays behind [1]."))
        assertEquals("Salt stays behind [1].", PressRewrite.clean("Rewritten: \"Salt stays behind [1].\""))
        assertEquals("O sal fica [1].", PressRewrite.clean("Texto reescrito: O sal fica [1]."))
        // While it streams, the reasoning doesn't show.
        assertEquals("", PressRewrite.visible("<think>still thinking"))
        assertEquals("Salt", PressRewrite.visible("<think>x</think>Salt"))
    }

    @Test fun `a source mark lost or added is caught`() {
        assertEquals(setOf(1, 2), PressRewrite.marks(en))
        assertFalse(PressRewrite.marksChanged(en, "Salt stays [1]; the Red Sea is salty [2]."))
        // Order or repeats don't matter, the set does.
        assertFalse(PressRewrite.marksChanged(en, "The Red Sea [2] is salty [1] [1]."))
        assertTrue(PressRewrite.marksChanged(en, "Salt stays behind [1]."))
        assertTrue(PressRewrite.marksChanged(en, "Salt [1] stays [2] behind [3]."))
        assertFalse(PressRewrite.marksChanged("No marks.", "Still none."))
    }

    private val dir = "../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer"
    private val screen = File("$dir/PressScreen.kt").readText()
    private val rewriter = File("$dir/PressRewriter.kt").readText()

    @Test fun `the edit sheet has both choices and every option`() {
        val sheet = screen.substringAfter("private fun EditSheet(").substringBefore("\n}\n")
        assertTrue(sheet.contains("t(\"native.labs.printer.editSelf\")"))
        assertTrue(sheet.contains("t(\"native.labs.printer.askTitle\")"))
        for (k in listOf("rwShorter", "rwSimpler", "rwFix", "rwList", "rwOwnHint", "rwAsk", "rwNow", "rwRewritten", "rwUse", "rwKeep", "rwStop", "rwMarks"))
            assertTrue(k, sheet.contains("\"native.labs.printer.$k\""))
        for (d in listOf("values", "values-pt")) {
            val s = File("../app/src/labs/plugins/printer/res/$d/strings_lab_printer.xml").readText()
            for (k in listOf("editSelf", "askTitle", "rwShorter", "rwSimpler", "rwFix", "rwList", "rwBusy", "rwNoModel", "rwMarks", "rwUse", "rwKeep"))
                assertTrue("$d $k", s.contains("name=\"native_labs_printer_$k\""))
        }
        // The pencil opens the sheet; a block being typed in closes with it.
        assertTrue(screen.contains("onEdit = { if (editing == selected) editing = null else sheet = Sheet.EDIT }"))
    }

    @Test fun `nothing is written before Use, and Keep, Stop and leaving change nothing`() {
        val sheet = screen.substringAfter("private fun EditSheet(").substringBefore("\n}\n")
        // The only write in the sheet is Use's.
        assertEquals(1, Regex("PressStore\\.change").findAll(sheet).count())
        val use = sheet.substringAfter("t(\"native.labs.printer.rwUse\")").substringBefore("t(\"native.labs.printer.rwKeep\")")
        assertTrue(use.contains("PressStore.change { it.editText(block.id, PressDoc.Field.TEXT, done.text) }"))
        // The runner never writes to the projects.
        assertFalse(rewriter.contains("PressStore.change"))
        // Leaving the sheet stops the rewrite; so does Stop.
        assertTrue(sheet.contains("DisposableEffect(block.id) { onDispose { job?.cancel() } }"))
        assertTrue(sheet.contains("job?.cancel()"))
    }

    @Test fun `it refuses while the chat uses the model, and without a model, and never cancels the chat's generation`() {
        val run = rewriter.substringAfter("suspend fun run(").substringBefore("\n    }\n")
        assertTrue(run.indexOf("services.isModelBusy") in 0 until run.indexOf("activeSession()"))
        assertTrue(run.contains("?: return Outcome.NoModel"))
        // Its own timeout, as a coroutine: SessionEngine's timeoutMs would cancel the session, whoever runs on it.
        assertTrue(run.contains("timeoutMs = null"))
        assertTrue(run.contains("withTimeoutOrNull("))
        assertFalse(run.contains(".cancel()"))
        // While it writes, the app counts the model as busy (no unload in the background).
        assertTrue(run.contains("services.addBusyCheck(busy)") && run.contains("services.removeBusyCheck(busy)"))
        val services = File("../app/src/main/java/team/sopa/boar/app/BoarServices.kt").readText()
        assertTrue(services.contains("val isModelBusy: Boolean get() = modelBusy()"))
        // The clean-up and the marks check are the engine's.
        assertTrue(rewriter.contains("PressRewrite.clean(") && rewriter.contains("PressRewrite.marksChanged("))
    }
}

class PressRewriteOwnWordsTest {
    @Test fun `the user's own words get the document to read and may write new text`() {
        val doc = PressRewrite.documentContext("Field notes", listOf("Why is the sea salty?\nSeawater contains minerals and salts [1].", "", "A test sea"))
        org.junit.Assert.assertEquals("Field notes\n\nWhy is the sea salty?\nSeawater contains minerals and salts [1].\n\nA test sea", doc)
        val p = PressRewrite.prompt(PressRewrite.Option.OWN, "this is a test", own = "replace this with a sentence about the document", document = doc)
        org.junit.Assert.assertTrue(p.system.contains("may ask for new text") && p.system.contains("never add a new one"))
        org.junit.Assert.assertTrue(p.user, p.user.startsWith("DOCUMENT (for context only, do not copy it):\nField notes") &&
            p.user.contains("\n\nPASSAGE:\nthis is a test\n\nINSTRUCTION: replace this with a sentence about the document"))
        org.junit.Assert.assertTrue(p.nPredict >= 320)
        // No document: the passage and the instruction alone; Portuguese follows the text.
        org.junit.Assert.assertTrue(PressRewrite.prompt(PressRewrite.Option.OWN, "x", own = "y").user.startsWith("PASSAGE:"))
        org.junit.Assert.assertTrue(PressRewrite.prompt(PressRewrite.Option.OWN, "Por que o mar é salgado? Não sei.", own = "mais curto").user.contains("INSTRUÇÃO: mais curto"))
        org.junit.Assert.assertTrue(PressRewrite.documentContext("T", listOf("a".repeat(3000))).length <= 1500)
        val screen = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt").readText()
        org.junit.Assert.assertTrue(screen.contains("document = PressRewrite.documentContext(project.name,") && screen.contains("PressRewriter.run(ctx, current, option, ownText, languageOf, document)"))
    }
}
