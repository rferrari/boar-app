package team.sopa.boar.engine.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.settings.AppSettings

/** The validator's final gate on 9d22ef1e (Nov 12): queue, merge, Staged. */
class FinalGateTest {
    private val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()

    @Test fun `deleting the active model, saving tweaks and starting an evaluation stop the answer first (Q-5)`() {
        val models = java.io.File("../app/src/main/java/team/sopa/boar/models/ModelsViewModel.kt").readText()
        val remove = models.substringAfter("fun remove(a: CatalogAsset)").substringBefore("\n    fun ")
        assertTrue(remove.indexOf("services.stopAnswersAndWait()") in 0 until remove.indexOf("services.host.unload()"))
        val tweaks = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
        assertTrue(tweaks.contains("if (services.stopAnswersAndWait()) services.host.unload()"))
        val eval = java.io.File("../app/src/main/java/team/sopa/boar/eval/EvaluationViewModel.kt").readText()
        assertTrue(eval.contains("check(services.stopAnswersAndWait()) { \"an answer is still being written\" }\n                services.host.unload()"))
    }

    @Test fun `the queue takes a clear from another thread (Q-1b)`() {
        val q = PromptQueue(limit = 1000)
        var errors = 0
        val t = Thread { repeat(20_000) { runCatching { q.clear() }.onFailure { errors++ } } }
        t.start()
        repeat(20_000) { runCatching { q.add("a"); q.next(); q.waiting }.onFailure { errors++ } }
        t.join()
        assertEquals(0, errors)
    }

    @Test fun `a merged question goes to the model, a safety card is read by it (Q-8)`() {
        val m = PromptMerge.of(listOf("my heater smells, is it carbon monoxide?", "capital of Peru"))
        assertTrue(PromptMerge.isMerged(m))
        assertFalse(PromptMerge.isMerged("capital of Peru"))
        assertTrue(vm.contains("if (r is ToolResult.Instant && team.sopa.boar.engine.chat.PromptMerge.isMerged(q)) (if (r.tool.startsWith(\"safety-\")) ToolResult.Context(r.tool, team.sopa.boar.engine.chat.PromptMerge.safetyContext(r.text)) else null) else r"))
    }

    @Test fun `merging twice keeps one list, Spanish gets a Spanish head (Q-9)`() {
        val once = PromptMerge.of(listOf("a?", "b?"))
        val twice = PromptMerge.of(listOf(once, "c?", "d?"))
        assertEquals(listOf("a?", "b?", "c?", "d?"), PromptMerge.parts(twice))
        assertEquals(1, Regex("Answer these questions").findAll(twice).count())
        assertTrue(PromptMerge.of(listOf("¿Cuál es la capital de Perú?", "¿Dónde está Lima?")).startsWith("Responde a estas preguntas"))
    }

    @Test fun `a send on an idle chat rests too (Q-6)`() {
        // The input bar is shared by the chat and Designer (PromptBar.kt, Nov 12).
        val screen = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/PromptBar.kt").readText()
        assertTrue(screen.contains("onSend(); resting = true") && !screen.contains("if (answering) resting = true"))
    }

    @Test fun `plus N sources and the passages are saved with the answer (S-1)`() {
        assertTrue(vm.contains("if (v.more.isNotEmpty()) \"more\" to factsJson(v.more) else null"))
        assertTrue(vm.contains("more = facts(\"more\")") && vm.contains("withPassages = o[\"withPassages\"]"))
    }

    @Test fun `an unreadable settings file is not replaced by the migration (S-3)`() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val f = java.io.File(dir, "settings.json").apply { writeText("{\"userProfile\":\"pro\", trunc") }
        val s = AppSettings(f)
        assertFalse(s.readable)
        val services = java.io.File("../app/src/main/java/team/sopa/boar/app/BoarServices.kt").readText()
        assertTrue(services.contains("if (labs.contains(\"enabled.staged\") && settings.readable) {"))
        assertTrue(AppSettings(java.io.File(dir, "none.json")).readable)
        dir.deleteRecursively()
    }
}

class InstantTypingEmojiTest {
    @Test fun `no piece ends inside an emoji (T-1)`() {
        for (pad in 0..12) {
            val text = "x".repeat(pad) + "The moon 🌔 is waxing 🌕 soon, 🌑 later." + "y".repeat(pad * 7)
            val ps = InstantTyping.pieces(text)
            assertEquals(text, ps.joinToString(""))
            for (p in ps) assertFalse(p, Character.isHighSurrogate(p.last()) || Character.isLowSurrogate(p.first()))
        }
    }
}

class FinalGatePrivacyTest {
    @Test fun `deleting a chat erases its Designer drawings, recipes included (D-1, D-2)`() {
        val ill = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/Illustrator.kt").readText()
        val erase = ill.substringAfter("fun eraseAnswers(ctx: Context, ids: List<String>) {").substringBefore("\n    }")
        assertTrue(erase.contains("load(ctx)") && erase.contains("jobs.remove(id)?.cancel()") && erase.contains("ImageLab::deleteDrawing") && erase.contains("save()"))
        assertTrue(ill.substringAfter("fun forget(id: String)").substringBefore("\n    }").contains("ImageLab::deleteDrawing"))
        assertTrue(ill.substringAfter("fun eraseAll(ctx: Context)").substringBefore("\n    }").contains("ImageLab::deleteDrawing"))
        val lab = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt").readText()
        assertTrue(lab.contains("override fun eraseAnswers(context: android.content.Context, answerIds: List<String>) = Illustrator.eraseAnswers(context, answerIds)"))
    }

    @Test fun `an import added while one waits joins the end (D-3)`() {
        val docs = java.io.File("../app/src/main/java/team/sopa/boar/documents/DocumentsViewModel.kt").readText()
        assertTrue(docs.contains("if (_state.value.stage != null || !queue.waiting.isEmpty()) {"))
    }

    @Test fun `the connection text names evaluation sharing (C-2)`() {
        for (d in listOf("values", "values-pt")) {
            val s = java.io.File("../app/src/main/res/$d/strings.xml").readText()
            val body = Regex("name=\"chatHeader_onlineBody\"[^>]*>([^<]*)<").find(s)!!.groupValues[1]
            assertTrue(d, body.contains("evaluation results") || body.contains("resultados de avaliação"))
        }
    }
}
