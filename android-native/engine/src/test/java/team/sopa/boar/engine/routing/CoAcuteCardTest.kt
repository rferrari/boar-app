package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * adam: "send co safety fix" (Nov 14; t9 1535, coder 1046): a carbon monoxide alarm going off or symptoms got no fixed
 * text. The acute card answers first, alone, in EN, PT and ES, with the planning card's own advice.
 */
class CoAcuteCardTest {
    private fun hit(q: String) = LabTools.coHazard(q)

    @Test fun `the acute phrasings fire the acute card, in the question's language`() {
        val en = listOf(
            "My carbon monoxide alarm keeps going off and I have a headache and feel dizzy. What should I do?",
            "My CO alarm is beeping and I feel dizzy",
            "carbon monoxide alarm going off",
            "My carbon monoxide detector went off, what do I do?",
            "The CO detector reads 120 ppm",
            "I think I have carbon monoxide poisoning, I have a headache and feel sick",
        )
        for (q in en) {
            val h = hit(q)
            assertEquals(q, LabTools.CO_ACUTE, h?.tool)
            assertTrue(q, h!!.text.startsWith(CoHazard.CO_ACUTE_EN))
        }
        val pt = listOf("Meu alarme de monóxido de carbono está tocando e estou com dor de cabeça e tontura. O que faço?", "O detector de CO está apitando")
        for (q in pt) assertTrue(q, hit(q)?.let { it.tool == LabTools.CO_ACUTE && it.text.startsWith(CoHazard.CO_ACUTE_PT) } == true)
        val es = "Mi alarma de monóxido de carbono está sonando y me duele la cabeza"
        assertTrue(es, hit(es)?.let { it.tool == LabTools.CO_ACUTE && it.text.startsWith(CoHazard.CO_ACUTE_ES) } == true)
    }

    @Test fun `the card is the planning card's own advice, then the emergency line, nothing new`() {
        assertTrue(CoHazard.CO_HAZARD_EN.contains("get everyone out into fresh air right away, call emergency services, and don't go back inside until you're told it's safe."))
        assertTrue(CoHazard.CO_HAZARD_PT.contains("leve todos para o ar livre imediatamente, ligue para o serviço de emergência e não volte para dentro até que digam que é seguro."))
        assertTrue(CoHazard.CO_HAZARD_ES.contains("saca a todos al aire libre de inmediato, llama a los servicios de emergencia y no vuelvas a entrar hasta que te digan que es seguro."))
        val a = hit("carbon monoxide alarm going off")!!.text
        assertTrue(a, a.contains("\n\n") && a.substringAfter("\n\n").contains("emergency"))
    }

    @Test fun `the planning control still gets the planning card`() {
        assertEquals("safety-fact-co-hazard", hit("Can I run a generator in my garage?")?.tool)
    }

    @Test fun `false positives stay out`() {
        for (q in listOf("My smoke alarm keeps going off", "CO2 levels in my office are high and I have a headache", "I'm moving to Colorado, I get headaches at altitude",
                "I have a headache and feel dizzy", "Our CO-founder keeps beeping me", "The company CO is sick")) assertNull(q, hit(q))
    }

    @Test fun `never merged, first in order, no bypass`() {
        val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
        assertTrue(vm.contains("queue.merge { team.sopa.boar.engine.routing.LabTools.coHazard(it) != null }"))
        assertEquals(0, LabTools.slotRank("cohazard"))
        val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()
        assertTrue(hooks.contains("tool == \"safety-fact-co-hazard\" || tool == team.sopa.boar.engine.routing.LabTools.CO_ACUTE -> \"cohazard\""))
        assertTrue(hooks.contains("canBypass = it.id != \"cohazard\""))
    }

    @Test fun `the bench's CO questions get the card and the text their rules expect`() {
        val dir = java.io.File("../app/src/main/assets/boar-data/topics")
        val qs = listOf("plugins.v1.json", "health.v1.json").flatMap { team.sopa.boar.engine.eval.BenchTopic.parse(java.io.File(dir, it).readText()).questions }
            .filter { it.rules?.tool == LabTools.CO_ACUTE }
        assertEquals(listOf("cohazard-acute", "cohazard-acute-pt", "health-co"), qs.map { it.id }.sorted())
        for (q in qs) {
            val h = hit(q.query!!)
            assertEquals(q.id, LabTools.CO_ACUTE, h?.tool)
            q.rules!!.answerStartsWith?.let { assertTrue(q.id, h!!.text.startsWith(it)) }
            for (w in q.rules!!.answerHas) assertTrue(q.id + " " + w, h!!.text.contains(w, ignoreCase = true))
        }
    }
}
