package team.sopa.boar.engine.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** t9 0521 items 1-3 and 8: the fixed set unchanged, the topic files, levels and the expected time. */
class BenchPresetsTest {
    companion object {
        val setFile = File("../app/src/main/assets/boar-data/eval-set.json")
        val sets = EvalSets.parse(setFile.readText())
        val topicDir = File("../app/src/main/assets/boar-data/topics")
        val topics: Map<String, BenchTopic> = BenchTopic.newest(topicDir.list()!!.toList()).map { BenchTopic.parse(File(topicDir, it).readText()) }.associateBy { it.id }
        val small = ModelRef(BenchPresets.SMALL_MODEL, "Qwen3-1.7B")
        val large = ModelRef(BenchPresets.LARGE_MODEL, "Qwen3-4B")
        val phi = ModelRef("phi-3.5-mini-instruct-q4km", "Phi")
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun `the 17 and the 6 stay byte for byte (adam, Nov 14 - keep those as we have since start)`() {
        assertEquals("1f4b8f487bbba8b235701e7138088481c55e4906858cab22343e11d986a16629", sha256(setFile.readBytes()))
        assertEquals(17, sets.evalSet.size)
        assertEquals(6, sets.extraSets.size)
        assertEquals(listOf("greeting-1", "factual-1", "factual-2", "explanation-1", "explanation-2", "comparison-1", "comparison-2",
            "synthesis-1", "synthesis-2", "synthesis-3", "reasoning-1", "reasoning-2", "reasoning-3", "grounded-1", "grounded-2", "no-kb-1", "no-kb-2"),
            sets.evalSet.map { it.id })
        assertTrue(sets.evalSet.all { it.setVersion == "1" })
    }

    @Test fun `the core topic runs the 17 exactly as the set has them`() {
        assertEquals(sets.evalSet, topics.getValue("core").queries(sets))
        assertEquals(sets.extraSets, topics.getValue("vitalik").queries(sets))
    }

    @Test fun `every topic file parses, is reviewed by t9, and its questions resolve`() {
        assertEquals(setOf("core", "vitalik", "plugins", "problems", "howto", "research", "quick", "cards", "portuguese", "health"), topics.keys)
        for (t in topics.values) {
            assertEquals(t.id, "reviewed by t9, 14 Nov", t.status)
            val qs = t.queries(sets)
            assertEquals(t.id, qs.size, qs.map { it.id }.toSet().size)
            for (q in t.questions) if (!q.fromSet) assertEquals("${t.id}-v1", qs.first { it.id == q.id }.setVersion)
        }
    }

    @Test fun `cards has the 14 questions, portuguese the 30 of the language fix`() {
        val cards = topics.getValue("cards").queries(sets)
        assertEquals(14, cards.size)
        assertEquals("How do you purify water in the wild, step by step?", cards.first().query)
        assertEquals("Como purificar água na natureza, passo a passo?", cards.last().query)
        val pt = topics.getValue("portuguese")
        assertEquals(30, pt.questions.size)
        assertTrue(pt.questions.all { it.id.startsWith("pt-") && it.rules?.language == "pt" })
    }

    @Test fun `the newest version of a topic wins`() {
        assertEquals(listOf("a.v2.json", "b.v1.json"), BenchTopic.newest(listOf("a.v1.json", "b.v1.json", "a.v2.json", "README.md")))
        assertNull(BenchTopic.fileName("plugins.json"))
    }

    @Test fun `preset names - a level alone is the 17, a topic after a dash`() {
        assertEquals(BenchPreset(BenchLevel.FAST, listOf("core")), BenchPresets.parse("fast"))
        assertEquals(BenchPreset(BenchLevel.FAST, listOf("plugins")), BenchPresets.parse("fast-plugins"))
        assertEquals(BenchPreset(BenchLevel.MEDIUM, listOf("research")), BenchPresets.parse("medium-research"))
        assertNull(BenchPresets.parse("slow-plugins"))
        assertEquals("complete-research", BenchPreset(BenchLevel.COMPLETE, listOf("research")).name)
        assertEquals("fast", BenchPreset(BenchLevel.FAST, listOf("core")).name)
    }

    @Test fun `fast takes the 1_7B or the only model, medium and complete take both`() {
        assertEquals(listOf(small), BenchPresets.models(BenchLevel.FAST, listOf(phi, large, small)))
        assertEquals(listOf(large), BenchPresets.models(BenchLevel.FAST, listOf(large)))
        assertEquals(listOf(small, large), BenchPresets.models(BenchLevel.MEDIUM, listOf(phi, large, small)))
        assertEquals(listOf(large, phi), BenchPresets.models(BenchLevel.COMPLETE, listOf(phi, large)))
    }

    @Test fun `levels set the styles, Deep Research only at complete and only for the research topic`() {
        fun plan(name: String) = (BenchPresets.plan(BenchPresets.parse(name)!!, listOf(small, large), topics, sets) as BenchPresets.Planned.Ok).plan
        assertEquals(listOf("succinct"), plan("fast").passes.map { it.key })
        assertEquals(listOf("succinct"), plan("medium-plugins").passes.map { it.key })
        assertEquals(listOf("succinct", "detailed"), plan("complete").passes.map { it.key })
        val research = plan("complete-research")
        assertEquals(listOf("succinct", "detailed", "deep"), research.passes.map { it.key })
        assertEquals(research.queries.size, research.passQueries.getValue("deep").size)
        assertEquals(2 * 3 * research.queries.size, research.answers)
        assertEquals(17, plan("fast").answers)
        assertEquals(topics.getValue("plugins").labs, plan("fast-plugins").labs)
        assertTrue(plan("fast-plugins").queries.all { plan("fast-plugins").topicOf[it.id] == "plugins" })
        assertTrue(BenchPresets.plan(BenchPresets.parse("fast-nope")!!, listOf(small), topics, sets) is BenchPresets.Planned.Err)
        assertTrue(BenchPresets.plan(BenchPresets.parse("fast")!!, emptyList(), topics, sets) is BenchPresets.Planned.Err)
    }

    @Test fun `expected time from the F3 medians, then from the last run of the level`() {
        fun plan(name: String) = (BenchPresets.plan(BenchPresets.parse(name)!!, listOf(small, large), topics, sets) as BenchPresets.Planned.Ok).plan
        assertEquals(17 * 8.9, BenchPresets.estimate(plan("fast")).seconds, 1e-6)
        assertEquals(17 * (8.9 + 26.8), BenchPresets.estimate(plan("medium")).seconds, 1e-6)
        // complete: about 3.9 min a question
        assertEquals(232.7, BenchPresets.estimate(plan("complete")).seconds / 17, 1e-6)
        val deep = BenchPresets.estimate(plan("complete-research"))
        assertEquals(2 * plan("complete-research").queries.size, deep.untimed)
        val previous = listOf(
            kotlinx.serialization.json.Json.parseToJsonElement("""{"modelId":"${small.id}","style":"succinct","outcome":"success","totalLatencyMs":4000}""") as kotlinx.serialization.json.JsonObject,
            kotlinx.serialization.json.Json.parseToJsonElement("""{"modelId":"${small.id}","style":"succinct","outcome":"success","totalLatencyMs":6000}""") as kotlinx.serialization.json.JsonObject,
        )
        val e = BenchPresets.estimate(plan("fast"), previous)
        assertEquals(17 * 5.0, e.seconds, 1e-6)
        assertTrue(e.fromPrevious)
    }

    @Test fun `a request can ask for a preset by name or by level and topics`() {
        val a = DeviceEval.parseRequest("""{"requestId":"p1","preset":"fast-plugins"}""")
        assertEquals(BenchPreset(BenchLevel.FAST, listOf("plugins")), a.preset)
        val b = DeviceEval.parseRequest("""{"requestId":"p2","level":"medium","topics":["cards","portuguese"]}""")
        assertEquals(BenchPreset(BenchLevel.MEDIUM, listOf("cards", "portuguese")), b.preset)
        assertNull(DeviceEval.parseRequest("""{"requestId":"p3"}""").preset)
        for (bad in listOf("""{"requestId":"p4","preset":"slow"}""", """{"requestId":"p5","level":"x"}""", """{"requestId":"p6","preset":"fast","queries":["factual-1"]}""")) {
            assertTrue(bad, runCatching { DeviceEval.parseRequest(bad) }.exceptionOrNull() is EvalRequestException)
        }
    }
}
