package team.sopa.boar.engine.routing

import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.answer.DeepResearch
import team.sopa.boar.engine.routing.LabToolsCombinationTest.Companion.combos
import team.sopa.boar.engine.routing.LabToolsCombinationTest.Companion.distanceTable
import team.sopa.boar.engine.routing.LabToolsCombinationTest.Companion.factsTable
import team.sopa.boar.engine.routing.LabToolsCombinationTest.Companion.name
import team.sopa.boar.engine.routing.LabToolsCombinationTest.Companion.owner
import team.sopa.boar.engine.routing.LabToolsCombinationTest.Companion.run

/**
 * The Boar Labs answer plugins on harder input than LabToolsCombinationTest's corpus: compound questions that pull three
 * to five plugins and a seed phrase at once (EN, PT, ES), questions that carry a real-looking seed, and adversarial
 * strings through every regex entry point (linear time). Failures are collected and printed before the test fails.
 */
class LabToolsCompoundTest {
    /** A CO hazard among the asks: Cohazard answers alone whenever it is on. */
    private val withCo = listOf(
        "How far is Lisbon to Porto, what is 15 * 8, how do I back up my 12-word seed phrase, and can I run a propane heater in the car?",
        "Can I run the generator in the garage overnight, how far is Paris to Rome, how many 5 L jerrycans for 23 L, and where should I store my seed phrase?",
        "What is the capital of Kenya, how do I back up my wallet, is 10 ft from the back door far enough for the generator, and how long to drive 240 miles at 60 mph?",
        "My seed phrase is on paper; how far is it from Madrid to Barcelona; can we use a charcoal grill inside the tent; and what is 20 °C in Fahrenheit?",
        "How long to drive 240 miles at 60 mph, does Brazil have more people than Russia, and can I warm up my car in the garage with the door closed? Also where do I keep my private key?",
        "Where do I store my recovery phrase? Can I use a camping stove inside the tent? How far is Vienna to Budapest?",
        "Qual a distância de Lisboa ao Porto, quanto é 15 * 8, como faço backup da minha frase semente, e posso usar aquecedor a gás dentro da barraca?",
        "Posso ligar o gerador na garagem, onde guardo minhas palavras de recuperação, qual a capital do Quênia e quanto é 2+2?",
        "Onde guardo minha frase de recuperação? E posso usar a churrasqueira dentro de casa? Qual a distância de São Paulo ao Rio de Janeiro?",
        "¿Qué distancia hay de Madrid a Barcelona, cuánto es 15 * 8, dónde guardo mi seed phrase y puedo usar un generador en el garaje?",
        "¿Puedo usar un brasero dentro de la tienda? Y también, ¿cuál es la capital de Kenia y cómo guardo mi seed phrase?",
    )

    /** A seed phrase and other plugins' asks, no CO hazard: Facts and Distance never answer. */
    private val seedNoCo = listOf(
        "How far is Lisbon to Porto, what is 15 * 8, how do I back up my 12-word seed phrase, and is a polar bear bigger than a grizzly bear?",
        "What is the capital of Kenya, how far is it from Paris to Rome, and how should I store my seed phrase?",
        "Where should I store my recovery phrase, how far is Madrid to Barcelona, and does Brazil have more people than Russia?",
        "I have 23 L of water and 5 L jerrycans. How many jerrycans do I need? Also how far is it from Paris to Rome and where do I keep my private key?",
        "How long to drive 240 miles at 60 mph, what plug type does Brazil use, and how do I back up my wallet?",
        "Is it safe to keep my private key in a photo? And what is 20 °C in Fahrenheit, and how far is Zürich to Geneva?",
        "Qual a distância de Lisboa ao Porto, quanto é 15 * 8 e onde guardo minha frase semente?",
        "Qual a capital do Quênia, quanto é 2+2, e como guardo minhas 24 palavras da carteira de bitcoin?",
        "¿Qué distancia hay de Madrid a Barcelona, cuál es la capital de Kenia y dónde guardo mi seed phrase?",
    )

    private val all = listOf("calc", "distance", "facts", "cohazard")

    @Test fun `compound with a CO hazard - Cohazard answers alone, the same in every combination with it on`() {
        val bad = ArrayList<String>()
        for (q in withCo) {
            val co = LabTools.coHazard(q)
            if (co == null) { bad += "Cohazard missed: $q"; continue }
            for (c in combos) {
                val win = LabTools.first(c.first) { run(it, q) }
                if ("cohazard" in c.first && win != co) bad += "${name(c)}: ${win?.tool} instead of Cohazard: $q"
                if (win != null && owner(win) in listOf("facts", "distance")) bad += "${name(c)}: ${win.tool} on a seed-phrase compound: $q"
            }
        }
        bad.forEach(::println)
        assertTrue(bad.firstOrNull() ?: "", bad.isEmpty())
    }

    @Test fun `compound with a seed phrase and no CO hazard - Facts, Distance and Cohazard never answer, the calculator takes its own part when it finds one, the same in every combination with it on`() {
        val bad = ArrayList<String>()
        val table = StringBuilder("\n== seed-phrase compounds, no CO hazard: what each tool decides ==\n")
        for (q in seedNoCo) {
            val hits = all.associateWith { run(it, q) }
            table.append(all.joinToString("  ") { "$it=${hits[it]?.tool ?: "-"}" }).append("  | ").append(q.take(70)).append('\n')
            for (id in listOf("facts", "distance", "cohazard")) if (hits[id] != null) bad += "$id answered: $q"
            for (c in combos) {
                val win = LabTools.first(c.first) { run(it, q) }
                val expected = if ("calc" in c.first) hits["calc"] else null
                if (win != expected) bad += "${name(c)}: ${win?.tool} instead of ${expected?.tool}: $q"
            }
        }
        println(table)
        bad.forEach(::println)
        assertTrue(bad.firstOrNull() ?: "", bad.isEmpty())
    }

    // BIP39-looking words (the first 24 of the English list): a seed someone pasted.
    private val seed12 = "abandon ability able about above absent absorb abstract absurd abuse access accident"
    private val seed24 = "$seed12 account accuse achieve acid acoustic acquire across act action actor actress actual"
    private val seedWords = seed24.split(' ')

    private val withSeed = listOf(
        "Summarize our chat and keep my seed phrase safe: $seed12",
        "What is 2+2, my seed is $seed12",
        "Guarde minha frase-semente: $seed12",
        "Guarde minha frase semente $seed24 e me diga quanto é 2+2",
        "How far is it from Paris to Rome? my seed is $seed24",
        "What is the capital of Kenya? $seed12 is my wallet seed",
        "Does Brazil have more people than Russia? Recovery words: $seed24",
        "I have 23 L of water and 5 L jerrycans. How many jerrycans do I need? seed: $seed12",
        "Can I run the generator in the garage? My seed: $seed12",
        "How far is it from Madrid to Barcelona, here are my 12 words for the wallet: $seed12",
        "$seed12 — that's my bitcoin seed. Is a polar bear bigger than a grizzly bear?",
        "Did astronauts walk on the Moon before 1970? my seed is $seed12",
        "¿Cuál es la capital de Kenia? Mi frase semilla es $seed12",
    )

    /** Seed words in [text], each as often as it appears. */
    private fun seedCounts(text: String): Map<String, Int> = seedWords.associateWith { w -> Regex("(?i)(?<![\\p{L}])$w(?![\\p{L}])").findAll(text).count() }

    @Test fun `a pasted seed is never echoed, never taken by Facts or Distance, and Guard adds none of its words`() {
        val bad = ArrayList<String>()
        for (q in withSeed) {
            // The same question without the seed: a fixed answer text may hold a common word ("about") on its own.
            val bare = q.replace(seed24, "XXXX").replace(seed12, "XXXX")
            for (id in all) {
                val h = run(id, q) ?: continue
                if (id == "facts" || id == "distance") bad += "$id answered a question with a seed: $q"
                val base = run(id, bare)?.text?.let(::seedCounts) ?: emptyMap()
                seedCounts(h.text).filter { (w, n) -> n > (base[w] ?: 0) }.takeIf { it.isNotEmpty() }?.let { bad += "$id echoes ${it.keys}: $q" }
            }
            for (a in listOf(
                "No, astronauts did not walk on the Moon before 1970. The first human moonwalk occurred on July 20, 1969. Your words $seed12 are noted.",
                "Yes, astronauts walked on the Moon before 1970. Keep $seed24 offline.",
                "Your seed phrase is $seed12. Never share it.",
                "Yes, a polar bear is bigger. $seed12",
                "No, Brazil does not have more people than Russia. Brazil has about 203 million people and Russia about 146 million. $seed12",
            )) {
                val r = LabTools.guard(q, a) ?: continue
                val before = seedCounts(a)
                seedCounts(r.text).filter { (w, n) -> n > (before[w] ?: 0) }.takeIf { it.isNotEmpty() }?.let { bad += "guard adds ${it.keys}: $q / $a" }
            }
        }
        bad.forEach(::println)
        assertTrue(bad.firstOrNull() ?: "", bad.isEmpty())
    }

    /** Adversarial strings, about [n] characters each. */
    private val attacks: Map<String, (Int) -> String> = linkedMapOf(
        "aaaa" to { n -> "a".repeat(n) },
        "1,1,1" to { n -> "1,".repeat(n / 2) },
        "((((" to { n -> "(".repeat(n) },
        "unclosed quote" to { n -> "\"" + "how far is ".repeat(n / 11) },
        "1.2.3.4" to { n -> (0 until n / 2).joinToString("") { "${it % 10}." } },
        "spaces" to { n -> " ".repeat(n) },
        "the the" to { n -> "the ".repeat(n / 4) },
        "mixed scripts" to { n -> "aé日ا🐗1 ש,".repeat(n / 11) },
        "generator garage" to { n -> "generator garage ".repeat(n / 17) },
        "how far a to b" to { n -> "how far is a to b ".repeat(n / 18) },
        "write,report" to { n -> "write,".repeat(n / 6) },
        "gerador garagem" to { n -> "gerador na garagem ".repeat(n / 19) },
        "digits L km" to { n -> "1 L 2 km 3 mph ".repeat(n / 15) },
        "seed words" to { n -> "seed phrase 12 words wallet ".repeat(n / 28) },
    )

    private val shortAnswer = "No, astronauts did not walk on the Moon before 1970. The first human moonwalk occurred on July 20, 1969."

    private val entries: Map<String, (String) -> Any?> = linkedMapOf(
        "LabTools.calc" to { s -> LabTools.calc(s, emptyList()) },
        "LabTools.distance" to { s -> LabTools.distance(distanceTable, s, emptyList()) },
        "LabTools.facts" to { s -> LabTools.facts(factsTable, s, emptyList()) },
        "LabTools.coHazard" to { s -> LabTools.coHazard(s) },
        "LabTools.guard(q)" to { s -> LabTools.guard(s, shortAnswer) },
        "LabTools.guard(answer)" to { s -> LabTools.guard("Did astronauts walk on the Moon before 1970?", s) },
        "VerdictGuard.check(q)" to { s -> VerdictGuard.check(s, shortAnswer) },
        "VerdictGuard.check(answer)" to { s -> VerdictGuard.check("Is a polar bear bigger than a grizzly bear?", s) },
        "CoHazard.isCoHazardQuestion" to { s -> CoHazard.isCoHazardQuestion(s) },
        "CoHazard.coHazardAnswer" to { s -> CoHazard.coHazardAnswer(s) },
        "CoHazard.coHazardLanguage" to { s -> CoHazard.coHazardLanguage(s) },
        "QueryContext.isHealthQuestion" to { s -> QueryContext.isHealthQuestion(s) },
        "QueryContext.isSeedPhraseQuestion" to { s -> QueryContext.isSeedPhraseQuestion(s) },
        "QueryContext.isPortugueseQuestion" to { s -> QueryContext.isPortugueseQuestion(s) },
        "QueryContext.isSpanishQuestion" to { s -> QueryContext.isSpanishQuestion(s) },
        "NumGuard.healthLexicon" to { s -> NumGuard.healthLexicon(s) },
        "DeepResearch.isConversationRecap" to { s -> DeepResearch.isConversationRecap(s) },
    )

    private fun time(f: (String) -> Any?, s: String): Double {
        repeat(2) { runCatching { f(s) } }
        return (1..5).minOf { val t = System.nanoTime(); runCatching { f(s) }; (System.nanoTime() - t) / 1e6 }
    }

    @Test fun `every regex entry point stays linear on adversarial strings`() {
        val bad = ArrayList<String>()
        val out = StringBuilder("\n== adversarial strings: worst attack per entry point, ms (min of 5, warm) ==\n")
        out.append("entry point".padEnd(36)).append("attack".padEnd(18)).append("2k".padStart(9)).append("20k".padStart(9)).append("ratio".padStart(8)).append('\n')
        for ((entry, f) in entries) {
            var worst: Triple<String, Double, Double>? = null
            for ((attack, make) in attacks) {
                val small = make(2_000)
                val large = make(20_000)
                val t2 = time(f, small)
                val t20 = time(f, large)
                // Below 0.05 ms the clock, not the regex, sets the ratio.
                val ratio = t20 / maxOf(t2, 0.05)
                if (ratio >= 25 || t20 >= 300) bad += "$entry on $attack: 2k ${"%.3f".format(t2)} ms, 20k ${"%.3f".format(t20)} ms, ratio ${"%.1f".format(ratio)}"
                if (worst == null || t20 > worst.third) worst = Triple(attack, t2, t20)
            }
            val (attack, t2, t20) = worst!!
            out.append(entry.padEnd(36)).append(attack.padEnd(18)).append("%9.3f".format(t2)).append("%9.3f".format(t20))
                .append("%8.1f".format(t20 / maxOf(t2, 0.05))).append('\n')
        }
        // The sizes the plan names, once each.
        out.append("\n== large inputs, ms ==\n")
        for ((label, s) in listOf(
            "aaaa x 50k" to "a".repeat(50_000),
            "generator garage x 5k" to "generator garage ".repeat(5_000),
            "how far is a to b x 2k" to "how far is a to b ".repeat(2_000),
            "the x 20k" to "the ".repeat(20_000),
            "spaces x 20k" to " ".repeat(20_000),
        )) for ((entry, f) in entries) {
            val t = time(f, s)
            if (t >= 1_000) bad += "$entry on $label: ${"%.1f".format(t)} ms"
            if (t >= 10) out.append("${label.padEnd(24)} ${entry.padEnd(36)} ${"%.1f".format(t)}\n")
        }
        println(out)
        bad.forEach(::println)
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
