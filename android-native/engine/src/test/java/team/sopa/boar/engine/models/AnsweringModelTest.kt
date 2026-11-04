package team.sopa.boar.engine.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.chat.HeaderModel
import team.sopa.boar.engine.models.StartLoad.Model

/**
 * The model the chat answers with and its header names, one rule for both (X6, Nov 4: the header said 35B, then "llm
 * core", then Gemma while it downloaded, the catalog said Qwen3 4B, and a question got "no model").
 */
class AnsweringModelTest {
    private val qwen4b = Model("qwen3-4b", 2_500_000_000)
    private val gemma = Model("gemma-4-e4b", 5_000_000_000)
    private val bigBoar = Model("qwen3.6-35b-a3b", 20_000_000_000, streamed = true)
    private val blocked = setOf("qwen3-4b")

    /** What BoarServices lists: a model whose file is whole. */
    private fun installed(vararg files: Pair<Model, Long>) = files.filter { (m, len) -> StartLoad.complete(true, len, m.sizeBytes) }.map { it.first }

    @Test fun `a download in progress isn't installed`() {
        assertFalse(StartLoad.complete(true, 1_200_000_000, gemma.sizeBytes))
        assertTrue(StartLoad.complete(true, gemma.sizeBytes, gemma.sizeBytes))
        assertFalse(StartLoad.complete(false, 0, gemma.sizeBytes))
        // A model of unknown size (copied by hand) counts once its file is there.
        assertTrue(StartLoad.complete(true, 123, 0))
    }

    @Test fun `the pick answers while another model downloads`() {
        val phone = installed(qwen4b to qwen4b.sizeBytes, gemma to 1_200_000_000)
        assertEquals(qwen4b, StartLoad.answering(qwen4b, phone, { false }, null))
    }

    @Test fun `a held-back pick never falls back to a half-downloaded model`() {
        val phone = installed(qwen4b to qwen4b.sizeBytes, gemma to 1_200_000_000)
        assertNull(StartLoad.answering(qwen4b, phone, { it in blocked }, null))
    }

    @Test fun `a held-back pick never falls back to Big Boar on its own, even as the last one loaded`() {
        val small = Model("qwen3-1.7b", 1_100_000_000)
        val phone = listOf(qwen4b, small, bigBoar)
        assertEquals(small, StartLoad.answering(qwen4b, phone, { it in blocked }, bigBoar.id))
        assertNull(StartLoad.answering(qwen4b, listOf(qwen4b, bigBoar), { it in blocked }, bigBoar.id))
    }

    @Test fun `Big Boar answers when the user picks it, and not once its lab is off`() {
        assertEquals(bigBoar, StartLoad.answering(bigBoar, listOf(qwen4b, bigBoar), { false }, null))
        // Lab off: its model leaves the catalog, so the pick is the catalog's again.
        assertEquals(qwen4b, StartLoad.answering(qwen4b, listOf(qwen4b), { false }, bigBoar.id))
    }

    @Test fun `the header names the phone's model, an arrow while another one writes`() {
        assertEquals("Qwen3-4B", HeaderModel.label(false, null, "Qwen3-4B"))
        assertEquals("→ Qwen3-1.7B", HeaderModel.label(true, "Qwen3-1.7B", "Qwen3-4B"))
        // The arrow only while answering: after it, back to the source.
        assertEquals("Qwen3-4B", HeaderModel.label(false, "Gemma", "Qwen3-4B"))
        assertNull(HeaderModel.label(false, null, null))
    }
}
