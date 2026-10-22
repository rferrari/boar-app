package team.sopa.boar.engine.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEvalEngineTest {
    private val installed = listOf(
        ModelRef("qwen3-4b-instruct-2507-q4km", "Qwen3 4B"),
        ModelRef("lfm2.5-8b-a1b-q4km", "LFM2.5 8B"),
    )

    private fun configs(json: String) =
        (DeviceEval.resolve(DeviceEval.parseRequest(json), installed, emptyList(), "Adaptive") as Resolved.Ok).configs.map { it as EvalConfig.Model }

    @Test fun `without an engine every model runs on BOAR's own, with the usual config id`() {
        val c = configs("""{"requestId":"r","models":["qwen3-4b","lfm2.5"]}""")
        assertEquals(listOf(EvalConfig.BUILTIN, EvalConfig.BUILTIN), c.map { it.engine })
        assertEquals(listOf("model:qwen3-4b-instruct-2507-q4km", "model:lfm2.5-8b-a1b-q4km"), c.map { it.id })
    }

    @Test fun `one engine for all, and per model over it`() {
        val c = configs("""{"requestId":"r","models":["qwen3-4b","lfm2.5"],"engine":"mapped","engines":{"lfm2.5":"bigboar"}}""")
        assertEquals(listOf(EvalConfig.MAPPED, EvalConfig.BIG_BOAR), c.map { it.engine })
        assertEquals("model:lfm2.5-8b-a1b-q4km@bigboar", c[1].id)
        // By model id too.
        val byId = configs("""{"requestId":"r","models":["lfm2.5"],"engines":{"lfm2.5-8b-a1b-q4km":"bigboar"}}""")
        assertEquals(EvalConfig.BIG_BOAR, byId.single().engine)
    }

    @Test fun `an unknown engine is refused`() {
        val e = runCatching { DeviceEval.parseRequest("""{"requestId":"r","engine":"turbo"}""") }.exceptionOrNull()
        assertTrue(e is EvalRequestException && e.message.orEmpty().contains("\"engine\""))
        val e2 = runCatching { DeviceEval.parseRequest("""{"requestId":"r","engines":{"lfm2.5":"fast"}}""") }.exceptionOrNull()
        assertTrue(e2 is EvalRequestException && e2.message.orEmpty().contains("\"engines\""))
    }
}
