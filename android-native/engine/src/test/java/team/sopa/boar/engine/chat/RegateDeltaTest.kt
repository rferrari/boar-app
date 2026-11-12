package team.sopa.boar.engine.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.labs.PluginAnswers
import team.sopa.boar.engine.routing.LabTools
import team.sopa.boar.engine.routing.UnitBridge

/** The validator's re-gate of the rc3 delta (Nov 13). */
class RegateDeltaTest {
    private val co = "Can I run my portable generator in the attached garage if I keep the door open?"
    private val coPt = "Posso usar o gerador na garagem fechada?"

    @Test fun `a safety question is never merged, in English or mixed with Portuguese (M-1, M-2)`() {
        assertTrue(LabTools.coHazard(co) != null)
        for (others in listOf(listOf("What is the capital of Peru?", "Quantos habitantes tem Portugal?"), listOf("Qual a capital do Brasil?", "Who was Napoleon?"))) {
            val q = PromptQueue()
            q.add(co); others.forEach { q.add(it) }
            assertTrue(q.merge { LabTools.coHazard(it) != null })
            val left = q.waiting.map { it.text }
            assertEquals(co, left[0])                       // its own turn, first, unchanged: the card answers it
            assertEquals(2, left.size)
            assertTrue(PromptMerge.isMerged(left[1]) && LabTools.coHazard(left[1]) == null)
        }
        // A safety question and one other: nothing to merge.
        val q = PromptQueue(); q.add(co); q.add("Who was Napoleon?")
        assertFalse(q.merge { LabTools.coHazard(it) != null })
        assertEquals(2, q.waiting.size)
        if (LabTools.coHazard(coPt) != null) { val p = PromptQueue(); p.add("a?"); p.add(coPt); p.add("b?"); assertTrue(p.merge { LabTools.coHazard(it) != null }); assertEquals(coPt, p.waiting[0].text) }
    }

    @Test fun `please, here, today, on my phone and a dash still ask for a heading (N-1)`() {
        for (q in listOf("which way is north please", "which way is north on my phone", "which way is north today", "where is north here",
            "which way is north on this map", "where is north - i am lost", "onde fica o norte por favor"))
            assertTrue(q, PluginAnswers.isDirectionQuestion(q))
        for (q in listOf("Where is North Carolina?", "what's North Korea's capital", "onde fica o Sul do Brasil")) assertFalse(q, PluginAnswers.isDirectionQuestion(q))
    }

    @Test fun `facing it already says straight ahead (M-5)`() {
        assertTrue(PluginAnswers.directionAnswer("which way is north?", 0f, 0f).contains("North is straight ahead"))
        assertTrue(PluginAnswers.directionAnswer("onde fica o norte?", 0f, 0f).contains("O norte fica bem à sua frente"))
    }

    @Test fun `a Portuguese pair gets the Portuguese head (Q-9 tie)`() {
        assertTrue(PromptMerge.of(listOf("Quantos habitantes tem Portugal?", "Qual a capital do Brasil?")).startsWith("Responda a estas perguntas"))
        assertTrue(PromptMerge.of(listOf("What is the capital of Peru?", "Who was Napoleon?")).startsWith("Answer these questions"))
    }

    @Test fun `huge and tiny distances never throw and the note isn't zero (N-4)`() {
        val huge = UnitBridge.prepare("how long to travel " + "9".repeat(400) + " miles at 3 km/h")
        assertTrue(huge.query.isNotBlank())
        val tiny = UnitBridge.prepare("how long to travel 0.02 miles at 3 km/h")
        assertTrue(tiny.note.toString(), tiny.note != null && !tiny.note!!.startsWith("0 miles"))
    }
}

class IllustratorCancelTest {
    @Test fun `a cancel while the subject is picked draws nothing, and a late picture goes with its recipe (N-3)`() {
        val ill = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/Illustrator.kt").readText()
        val start = ill.substringAfter("val fromModel = runCatching")
        assertTrue(start.indexOf("currentCoroutineContext().let { it[kotlinx.coroutines.Job]?.isActive ?: true } || status[a.id] !is Status.Choosing) return@launch") in 0 until start.indexOf("SdRunner.draw("))
        assertTrue(ill.contains("if (status[a.id] !is Status.Drawing) { ImageLab.deleteDrawing(d.file); return@onSuccess }"))
    }
}

class CoMergeOrdersTest {
    private val coEn = "Can I run my portable generator in the attached garage if I keep the door open?"
    private val harmlessEn = listOf("What is the capital of Peru?", "Who was Napoleon?")
    private val harmlessPt = listOf("Qual a capital do Brasil?", "Quantos habitantes tem Portugal?")

    private fun mergeOf(vararg qs: String): List<String> {
        val q = PromptQueue(); qs.forEach { q.add(it) }
        q.merge { team.sopa.boar.engine.routing.LabTools.coHazard(it) != null }
        return q.waiting.map { it.text }
    }

    @Test fun `the validator's mixed cases and every order keep the CO question as its own turn (M-1, M-2)`() {
        val cases = listOf(
            listOf(coEn, harmlessPt[0]), listOf(harmlessPt[0], coEn, harmlessPt[1]), listOf(coEn, harmlessEn[0], harmlessPt[0]),
            listOf(coEn) + harmlessEn, harmlessEn + coEn, listOf(harmlessEn[0], coEn, harmlessEn[1]),
            listOf(coEn) + harmlessPt, harmlessPt + coEn, listOf(harmlessPt[0], coEn, harmlessPt[1]),
        )
        for (c in cases) {
            val left = mergeOf(*c.toTypedArray())
            assertEquals(c.toString(), coEn, left.first())
            assertTrue(c.toString(), left.drop(1).none { team.sopa.boar.engine.routing.LabTools.coHazard(it) != null && it == coEn })
            val others = c.filter { it != coEn }
            assertEquals(c.toString(), if (others.size >= 2) 2 else c.size, left.size)
        }
    }

    @Test fun `a merged text that still matches is told to state the card first`() {
        val ctx = PromptMerge.safetyContext("Carbon monoxide can kill.")
        assertTrue(ctx.startsWith("SAFETY WARNING. Start your answer with this warning, word for word") && ctx.endsWith("Carbon monoxide can kill."))
    }
}
