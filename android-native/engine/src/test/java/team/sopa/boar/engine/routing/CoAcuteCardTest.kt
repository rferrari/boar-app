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

    /** t9's review table (14 Nov) with adam's decisions 2 and 3 (15 Nov, t9 0110 item 1): true = the acute card. */
    private val review = listOf(
        "my carbon monoxide alarm is going off and I feel dizzy" to true,
        "My CO alarm keeps chirping every minute" to false,
        "My CO alarm chirps, how do I change the battery?" to false,
        "My CO detector reads 0 ppm, is that normal?" to false,
        "my CO detector says error" to false,
        "Is 50 ppm of carbon monoxide dangerous?" to true,
        "Can carbon monoxide cause headaches?" to true,
        "What are carbon monoxide poisoning symptoms like headache and nausea?" to true,
        "What are the symptoms of carbon monoxide poisoning?" to false,
        "does a carbon monoxide detector need to be on every floor?" to false,
        "my smoke alarm is beeping" to false,
        "CO2 levels are high and I feel dizzy" to false,
        "I ran the generator in the garage and now I have a headache" to true,
    )

    @Test fun `the review's thirteen questions, as adam decided`() {
        for ((q, card) in review) assertEquals(q, card, hit(q)?.tool == LabTools.CO_ACUTE)
        // The ones without the card don't get the planning card either: the model answers them.
        for ((q, card) in review) if (!card) assertTrue(q, hit(q) == null)
    }

    @Test fun `the same decisions in Portuguese and Spanish`() {
        val pt = listOf(
            "Meu alarme de monóxido de carbono está tocando e estou tonto" to "pt",
            "O alarme de CO fica dando um bipe curto a cada minuto" to null,
            "Meu detector de CO marca 0 ppm, é normal?" to null,
            "O detector de monóxido mostra erro" to null,
            "50 ppm de monóxido de carbono é perigoso?" to "pt",
            "Monóxido de carbono causa dor de cabeça?" to "pt",
            "Liguei o gerador na garagem e agora estou com dor de cabeça" to "pt",
            "Acendi a churrasqueira dentro de casa e estou com tontura" to "pt",
        )
        val es = listOf(
            "Mi alarma de monóxido de carbono está sonando y estoy mareado" to "es",
            "Mi detector de CO marca 0 ppm, ¿es normal?" to null,
            "¿50 ppm de monóxido de carbono es peligroso?" to "es",
            "Encendí el generador en el garaje y ahora me duele la cabeza" to "es",
        )
        for ((q, lang) in pt + es) {
            val h = hit(q)
            if (lang == null) assertTrue(q, h?.tool != LabTools.CO_ACUTE)
            else {
                assertEquals(q, LabTools.CO_ACUTE, h?.tool)
                val want = if (lang == "pt") CoHazard.CO_ACUTE_PT else CoHazard.CO_ACUTE_ES
                assertTrue(q + " | " + h!!.text, h.text.startsWith(want))
            }
        }
    }

    @Test fun `electric, solar and battery generators stay out, with a symptom too`() {
        for (q in listOf("I used my solar generator in the tent and now I have a headache", "My battery generator is in the garage and I feel dizzy",
            "Jackery generator indoors, I have a headache", "Meu gerador solar está na garagem e estou com dor de cabeça"))
            assertTrue(q, hit(q)?.tool != LabTools.CO_ACUTE)
    }

    /** The validator's gate of eaa0161c, part C (t9 2226 item 1): the acute card's false negatives. */
    @Test fun `home fuel appliances count with a symptom, vented or not (R-C1)`() {
        for (q in listOf("We all have headaches, the gas furnace was running all night",
            "my head hurts and we have a gas water heater in the closet, the alarm keeps going off",
            "headache and nausea, the whole house, the boiler was serviced yesterday",
            "everyone in the house has a headache since the furnace was repaired",
            "we lit the wood stove and now we feel dizzy", "headache after sitting by the fireplace all evening",
            "the pellet stove is on and my kids are vomiting", "usamos a lareira e estamos com dor de cabeça e tontura"))
            assertEquals(q, LabTools.CO_ACUTE, hit(q)?.tool)
        // Electric ones stay out.
        for (q in listOf("I have a headache, the electric heater is on", "my electric furnace is on and I have a headache"))
            assertTrue(q, hit(q)?.tool != LabTools.CO_ACUTE)
    }

    @Test fun `an alarm going off and a symptom fire with nothing named, not with smoke (R-C1b)`() {
        for (q in listOf("the alarm is going off and I feel dizzy", "alarm beeping, headache, nausea, whole family"))
            assertEquals(q, LabTools.CO_ACUTE, hit(q)?.tool)
        assertTrue(hit("the smoke alarm is going off and I feel dizzy")?.tool != LabTools.CO_ACUTE)
        assertTrue(hit("fire alarm ringing and I have a headache")?.tool != LabTools.CO_ACUTE)
    }

    @Test fun `past tense, more alarm verbs, typos and Portuguese past forms (R-C2, R-C3)`() {
        for (q in listOf("I felt sick after the generator ran", "I felt weak after the propane heater",
            "I felt sick after the pressure washer ran in the basement",
            "my CO alarm is ringing", "the carbon monoxide detector is buzzing", "co alarm just triggered and I'm dizzy",
            "carbon monixide alarm going off", "carbon monoxid detector beeeping", "monoxide alarm going off", "c.o. alarm going off",
            "o alarme de monóxido não para de apitar", "la alarma de monóxido no para de sonar",
            "liguei o carro na garagem e estou com dor de cabeça"))
            assertEquals(q, LabTools.CO_ACUTE, hit(q)?.tool)
    }

    @Test fun `a Spanish reading gets the Spanish card (R-C4)`() {
        for (q in listOf("el detector de monóxido marca 60 ppm", "mi alarma de monóxido de carbono indica 40",
            "el monóxido de carbono está en 50 ppm en el sótano"))
            assertTrue(q, hit(q)!!.text.startsWith(CoHazard.CO_ACUTE_ES))
        assertTrue(hit("o detector de monóxido marca 60 ppm")!!.text.startsWith(CoHazard.CO_ACUTE_PT))
    }

    @Test fun `adam's no-card cases stay no-card`() {
        for ((q, card) in review) assertEquals(q, card, hit(q)?.tool == LabTools.CO_ACUTE)
        for (q in listOf("my smoke alarm is beeping", "CO2 levels are high and I feel dizzy", "My CO alarm keeps chirping every minute",
            "I used my solar generator in the tent and now I have a headache", "I have a headache and feel dizzy"))
            assertTrue(q, hit(q)?.tool != LabTools.CO_ACUTE)
    }
}
