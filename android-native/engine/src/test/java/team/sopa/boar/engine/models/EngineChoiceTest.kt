package team.sopa.boar.engine.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.models.EngineChoice.Choice
import team.sopa.boar.engine.models.EngineChoice.Engine

class EngineChoiceTest {
    private val fits = listOf(Compatibility.GREEN, Compatibility.YELLOW, Compatibility.RED)

    /** The whole table: choice × fit × eligible. */
    private fun expected(choice: Choice, fit: Compatibility, eligible: Boolean): Engine = when {
        !eligible -> Engine.BOAR
        choice == Choice.BOAR -> Engine.BOAR
        choice == Choice.BIG_BOAR -> Engine.BIG_BOAR
        else -> if (fit == Compatibility.RED) Engine.BIG_BOAR else Engine.BOAR
    }

    @Test fun `every choice, fit and eligibility`() {
        for (choice in Choice.entries) for (fit in fits) for (eligible in listOf(true, false))
            assertEquals("$choice $fit eligible=$eligible", expected(choice, fit, eligible), EngineChoice.resolve(choice, fit, eligible))
    }

    @Test fun `auto picks Big Boar only for a model too big for the phone`() {
        assertEquals(Engine.BOAR, EngineChoice.resolve(Choice.AUTO, Compatibility.GREEN, true))
        assertEquals(Engine.BOAR, EngineChoice.resolve(Choice.AUTO, Compatibility.YELLOW, true))
        assertEquals(Engine.BIG_BOAR, EngineChoice.resolve(Choice.AUTO, Compatibility.RED, true))
        assertEquals(Engine.BOAR, EngineChoice.resolve(Choice.AUTO, Compatibility.UNKNOWN, true))
    }

    @Test fun `a model Big Boar can't run stays on BOAR whatever the choice`() {
        for (choice in Choice.entries) for (fit in fits) assertEquals(Engine.BOAR, EngineChoice.resolve(choice, fit, false))
    }

    @Test fun `the header is only needed when the choice could pick Big Boar`() {
        assertFalse(EngineChoice.prefersBigBoar(Choice.BOAR, Compatibility.RED))
        assertFalse(EngineChoice.prefersBigBoar(Choice.AUTO, Compatibility.YELLOW))
        assertTrue(EngineChoice.prefersBigBoar(Choice.AUTO, Compatibility.RED))
        assertTrue(EngineChoice.prefersBigBoar(Choice.BIG_BOAR, Compatibility.GREEN))
    }

    @Test fun `stored values read back, anything else is auto`() {
        assertEquals(Choice.BIG_BOAR, Choice.of("bigboar"))
        assertEquals(Choice.BOAR, Choice.of("boar"))
        assertEquals(Choice.AUTO, Choice.of("auto"))
        assertEquals(Choice.AUTO, Choice.of(null))
        assertEquals(Choice.AUTO, Choice.of("llama"))
    }

    @Test fun `the page lists what's too big for the phone first, then by size`() {
        val gb = 1_000_000_000L
        val models = listOf(
            "olmoe-7b" to (4 * gb to Compatibility.GREEN), "lfm2-24b" to (14 * gb to Compatibility.RED),
            "qwen3-30b" to (6 * gb to Compatibility.YELLOW), "qwen3.6-35b" to (12 * gb to Compatibility.RED),
            "unknown" to (20 * gb to Compatibility.UNKNOWN),
        )
        val order = EngineChoice.bigModelOrder(models, { it.second.second }, { it.second.first }).map { it.first }
        assertEquals(listOf("lfm2-24b", "qwen3.6-35b", "qwen3-30b", "olmoe-7b", "unknown"), order)
    }

    @Test fun `big boar gets room to reason before the answer`() {
        assertEquals(2048, EngineChoice.bigBoarTokens(1024, 4096))
        // A small context: only what fits beside the prompt, never below the answer's own budget.
        assertEquals(1536, EngineChoice.bigBoarTokens(1024, 3072))
        assertEquals(1024, EngineChoice.bigBoarTokens(1024, 2048))
        assertEquals(512, EngineChoice.bigBoarTokens(512, 1024))
        assertEquals(-1, EngineChoice.bigBoarTokens(-1, 4096))
    }
}
