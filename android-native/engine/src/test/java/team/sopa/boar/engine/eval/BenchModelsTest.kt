package team.sopa.boar.engine.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.models.Catalog
import team.sopa.boar.engine.models.SetupModel
import team.sopa.boar.engine.routing.ShortAnswer
import java.io.File

/**
 * t9 0443 / 0452: Qwen3-0.6B as a hidden bench entry. The bench resolves it by "models":["qwen3-0.6b"]; no list a user
 * sees (setup, tiers, onboarding, the Model Catalog, the chat's model) can hold it.
 */
class BenchModelsTest {
    private val assets = File("../app/src/main/assets/boar-data")
    private val catalogText = File(assets, "catalog.json").readText()
    private val catalog = Catalog.parse(catalogText)
    private val benchOnly = BenchModels.parse(File(assets, BenchModels.ASSET).readText())
    private val id = "qwen3-0.6b-q4km"

    @Test fun `the 0_6B is pinned like the catalog's 1_7B - unsloth's Q4_K_M at a commit, SHA-256 and size`() {
        val m = benchOnly.single()
        assertEquals(id, m.id)
        assertEquals(396_705_472L, m.sizeBytes)
        assertEquals("ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a", m.sha256)
        assertEquals("https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/50968a4468ef4233ed78cd7c3de230dd1d61a56b/Qwen3-0.6B-Q4_K_M.gguf", m.sourceUrl)
        val ref = catalog.byId("qwen3-1.7b-q4km")!!
        assertEquals(ref.capabilities, m.capabilities)
        assertEquals(ref.kind, m.kind)
        assertEquals(ref.license, m.license)
        assertEquals(ref.engine, m.engine)
        assertTrue(m.sizeBytes < team.sopa.boar.engine.answer.Personalities.SMALL_MODEL_BYTES)
    }

    @Test fun `no user list has it - catalog, setup models, answer tiers, required, the setup pick`() {
        assertEquals(null, catalog.byId(id))
        assertTrue(catalog.models.none { it.id == id || it.filename == benchOnly.single().filename || it.testOnly })
        assertTrue(catalog.setupModels.none { it.id == id })
        assertTrue(catalog.answerModels.none { it.id == id })
        assertTrue(catalog.requiredModels.none { it.id == id })
        for (ram in listOf(2L, 4L, 6L, 8L, 12L, 16L).map { it * 1024 * 1024 * 1024 }) {
            assertFalse(SetupModel.recommended(catalog.setupModels, ram)?.id == id)
            assertFalse(SetupModel.toInstall(catalog.setupModels, emptySet(), ram)?.id == id)
        }
    }

    @Test fun `a testOnly entry put in catalog_json by mistake is dropped by Catalog_parse`() {
        val entry = File(assets, BenchModels.ASSET).readText().let { t -> t.substring(t.indexOf("{", t.indexOf("\"models\"")), t.lastIndexOf("]")).trim() }
        val leaked = catalogText.replaceFirst("\"models\": [", "\"models\": [\n$entry,")
        assertTrue(leaked.contains(id))
        val c = Catalog.parse(leaked)
        assertEquals(catalog.models.map { it.id }, c.models.map { it.id })
        assertTrue(c.setupModels.none { it.id == id })
    }

    @Test fun `only the bench reads bench-models_json`() {
        val src = File("../app/src/main/java")
        val readers = src.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.readText().let { "BenchModels" in it || BenchModels.ASSET in it } }.map { it.name }.toSet()
        assertEquals(setOf("DeviceEvalRunner.kt"), readers)
    }

    @Test fun `the bench lists it after the catalog's models and resolves qwen3-0_6b to it, in either order`() {
        val llms = BenchModels.llms(catalog, benchOnly)
        assertEquals(id, llms.last().id)
        assertEquals(catalog.models.filter { it.kind == team.sopa.boar.engine.models.AssetKind.LLM }.map { it.id }, llms.dropLast(1).map { it.id })
        val installed = llms.map { ModelRef(it.id, it.label) }
        assertEquals(listOf(id), DeviceEval.matchModels("qwen3-0.6b", installed).map { it.id })
        assertEquals(listOf("qwen3-1.7b-q4km"), DeviceEval.matchModels("qwen3-1.7b", installed).map { it.id })
        val sets = EvalSets.parse(File(assets, "eval-set.json").readText())
        for (order in listOf(listOf("qwen3-0.6b", "qwen3-1.7b"), listOf("qwen3-1.7b", "qwen3-0.6b"))) {
            val r = DeviceEval.resolve(EvalRequest(requestId = "t", models = order), installed, sets.evalSet, "Adaptive routing", sets.extraSets)
            r as Resolved.Ok
            assertEquals(order.map { if (it == "qwen3-0.6b") id else "qwen3-1.7b-q4km" }, r.configs.map { (it as EvalConfig.Model).modelId })
            assertEquals(17, r.queries.size)
        }
    }

    @Test fun `the phone requests parse - presets with both model orders, the probe on the 0_6B`() {
        val dir = File("../../scripts/bench-requests/qwen3-0.6b")
        val reqs = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.associate { it.name to DeviceEval.parseRequest(it.readText()) }
        assertEquals(5, reqs.size)
        assertEquals(listOf("qwen3-0.6b"), reqs.getValue("0-probe-greeting.json").models)
        for ((name, r) in reqs) if (r.preset != null) {
            assertEquals(name, listOf("core", "portuguese", "health"), r.preset!!.topics)
            assertEquals(name, if ("0.6b-first" in name) listOf("qwen3-0.6b", "qwen3-1.7b") else listOf("qwen3-1.7b", "qwen3-0.6b"), r.models)
        }
        assertEquals(setOf(BenchLevel.COMPLETE, BenchLevel.MEDIUM), reqs.values.mapNotNull { it.preset?.level }.toSet())
    }

    @Test fun `an entry is in the catalog's own id or file - the catalog's wins`() {
        val dup = benchOnly.single().copy(id = "qwen3-1.7b-q4km")
        assertEquals(1, BenchModels.llms(catalog, listOf(dup)).count { it.id == "qwen3-1.7b-q4km" })
    }

    @Test fun `a bench entry that could reach a user list does not parse`() {
        val ok = File(assets, BenchModels.ASSET).readText()
        assertThrows(IllegalArgumentException::class.java) { BenchModels.parse(ok.replace("\"testOnly\": true", "\"testOnly\": false")) }
        assertThrows(IllegalArgumentException::class.java) { BenchModels.parse(ok.replace("\"hidden\": true", "\"hidden\": false")) }
        assertThrows(IllegalArgumentException::class.java) { BenchModels.parse(ok.replace("\"required\": false", "\"required\": false, \"answerTier\": \"compact\"")) }
    }

    @Test fun `the health topic - five questions, the CO one answered by its acute card, the rest by the model kept short`() {
        val topicDir = File(assets, "topics")
        val health = BenchTopic.parse(File(topicDir, "health.v1.json").readText())
        assertEquals(listOf("health-co", "health-burn", "health-sting", "health-fever", "health-dose"), health.questions.map { it.id })
        // Every rule says what right looks like (t9 1058). The model's four are kept short; where the chat doesn't keep it
        // short today (the paracetamol dose: not a health question to QueryContext, found 14 Nov), the question carries its
        // known issue, so the scorecard shows an expected failure, not a regression.
        for (q in health.questions.filter { it.id != "health-co" }) {
            assertTrue(q.id, q.rules!!.keptShort)
            if (ShortAnswer.reason("detailed", q.query!!, null) != "health") assertTrue(q.id, q.knownIssue != null)
        }
        // health-co: right is the acute CO card first and alone (t9 1535), no known issue left.
        val co = health.questions.first { it.id == "health-co" }
        assertTrue(!co.rules!!.noTool && co.knownIssue == null)
        // The preset the requests use: both models' order comes from the request.
        val sets = EvalSets.parse(File(assets, "eval-set.json").readText())
        val topics = BenchTopic.newest(topicDir.list()!!.toList()).map { BenchTopic.parse(File(topicDir, it).readText()) }.associateBy { it.id }
        val preset = BenchPresets.parse("complete-core+portuguese+health")!!
        val small = ModelRef(id, "Qwen3-0.6B (Q4_K_M)")
        val compact = ModelRef("qwen3-1.7b-q4km", "Qwen3-1.7B (Q4_K_M)")
        val plan = (BenchPresets.plan(preset, listOf(compact, small), topics, sets, listOf(small, compact)) as BenchPresets.Planned.Ok).plan
        assertEquals(listOf(small, compact), plan.models)
        assertEquals(17 + 30 + 5, plan.queries.size)
        assertEquals(listOf("succinct", "detailed"), plan.passes.map { it.key })
    }
}
