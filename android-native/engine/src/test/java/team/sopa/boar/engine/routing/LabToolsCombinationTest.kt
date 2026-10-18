package team.sopa.boar.engine.routing

import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.answer.AnswerText
import team.sopa.boar.engine.rag.Facts
import java.io.File
import kotlin.random.Random

/**
 * The Boar Labs answer plugins together: every subset of {calc, distance, facts, cohazard} with Boar Guard on and off
 * (32 combinations), over src/test/resources/labs (EN and PT questions, crazy ones, fake model answers) and 5,000 fuzzed
 * strings. Each invariant names its letter: (a) no exception, (b) one deterministic winner, (c) health, seed, PT and ES
 * kept from the tools that must not take them, (d) Cohazard wins its questions, (e) Guard only on model answers, sound
 * and idempotent, (f) each tool decides fast, (g) no leaked placeholders, class names or non-finite numbers.
 * Failures are collected and printed as a table before the test fails, so one run shows every broken combination.
 */
class LabToolsCombinationTest {
    companion object {
        // The tables ship with the labs (app/src/labs/plugins).
        val distanceTable = TravelDistance(File("../app/src/labs/plugins/distance/res/raw/lab_distance_world_cities_v1.json").readText())
        val factsTable = Facts(File("../app/src/labs/plugins/facts/res/raw/lab_facts_facts_v1.json").readText())

        /** "category | text" lines; \n, \t and \uXXXX decoded. */
        fun load(name: String): List<Pair<String, String>> = File("src/test/resources/labs/$name").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val i = line.indexOf(" |")
                line.substring(0, i).trim() to decode(line.substring(i + 2).removePrefix(" "))
            }

        private val UNICODE = Regex("""\\u([0-9A-Fa-f]{4})""")
        private fun decode(s: String) = UNICODE.replace(s.replace("\\n", "\n").replace("\\t", "\t")) { it.groupValues[1].toInt(16).toChar().toString() }

        val corpus: List<Pair<String, String>> = load("combination-corpus.txt").let { base ->
            // Built here, not written out: three 20,000-character questions.
            val words = base.flatMap { it.second.split(' ') }.filter { it.isNotBlank() }
            val sentence = StringBuilder()
            var k = 0
            while (sentence.length < 20_000) sentence.append(words[k++ % words.size]).append(' ')
            base + listOf(
                "crazy" to sentence.toString().take(20_000),
                "crazy" to "a".repeat(20_000),
                "crazy" to "1+".repeat(10_000),
                // A long run of digits made the calculator's number patterns quadratic (fix(labs) on Calculators.NUM).
                "crazy" to "9".repeat(20_000),
                "crazy" to "9".repeat(19_990) + " L per 100 km in mpg?",
                "crazy" to "I have " + "1".repeat(5_000) + " L of water and 5 L jerrycans. How many jerrycans do I need?",
                "cohazard" to "Can I run my portable generator in the attached garage if I keep the door open? " + "Please answer. ".repeat(1_300),
            )
        }
        val guardAnswers: List<Pair<String, String>> = load("guard-answers.txt") +
            ("Did astronauts walk on the Moon before 1970?" to "No, astronauts did not walk on the Moon before 1970. The first human moonwalk occurred on July 20, 1969. ".repeat(250))

        val TOOLS = listOf("calc", "distance", "facts", "cohazard")

        /** The labs in the order the tool slot tries them: LabTools.slotRank, then the grid's (plugin.properties order; all four are beta). */
        val slotOrder: List<String> = TOOLS.sortedBy { id ->
            File("../app/src/labs/plugins/$id/plugin.properties").readLines().first { it.startsWith("order=") }.removePrefix("order=").toInt()
        }.sortedBy(LabTools::slotRank)

        fun run(id: String, q: String, turns: List<String> = emptyList()): LabTools.Hit? = when (id) {
            "calc" -> LabTools.calc(q, turns)
            "distance" -> LabTools.distance(distanceTable, q, turns)
            "facts" -> LabTools.facts(factsTable, q, turns)
            "cohazard" -> LabTools.coHazard(q)
            else -> error(id)
        }

        val TOOL_PREFIX = mapOf("calc" to listOf("calculator-", "temperature-"), "distance" to listOf("calculator-travel-distance"),
            "facts" to listOf("facts-"), "cohazard" to listOf("safety-fact-co-hazard"))
        fun owner(hit: LabTools.Hit): String = when {
            hit.tool == "calculator-travel-distance" -> "distance"
            hit.tool.startsWith("facts-") -> "facts"
            hit.tool == "safety-fact-co-hazard" -> "cohazard"
            else -> "calc"
        }

        /** Every combination: a subset of the tools (in slot order) and Guard on or off. */
        val combos: List<Pair<List<String>, Boolean>> = (0 until 16).flatMap { mask ->
            val on = slotOrder.filter { (mask shr TOOLS.indexOf(it)) and 1 == 1 }
            listOf(on to false, on to true)
        }
        fun name(c: Pair<List<String>, Boolean>) = (c.first.joinToString("+").ifEmpty { "none" }) + if (c.second) " +guard" else ""

        private val LEAKS = listOf("null", "NaN", "Infinity", "{{", "}}", "Exception", "undefined")
        private val CLASS_NAME = Regex("""team\.sopa|kotlin\.|java\.(?:lang|util)|\b(?:Hit|Answer|City|Entity|Result|Quantity|Seg|SplitThinking|Repair|ToolResult)\(|@[0-9a-f]{6,}\b""")
        // A raw Kotlin double ("1.0E308") or a non-finite number.
        private val RAW_NUMBER = Regex("""\d\.\d+E[+-]?\d|∞""")

        /** (g): what in [text] is a leak, ignoring what [input] already says (a question that says "null" may be echoed). */
        fun leaks(text: String, input: String): List<String> =
            LEAKS.filter { it in text && it !in input } +
                listOfNotNull(CLASS_NAME.find(text)?.value?.takeIf { it !in input }, RAW_NUMBER.find(text)?.value?.takeIf { it !in input }) +
                listOfNotNull("blank".takeIf { text.isBlank() })
    }

    private class Failures {
        val byCombo = LinkedHashMap<String, MutableMap<Char, Int>>()
        val lines = ArrayList<String>()
        fun add(combo: String, inv: Char, detail: String) {
            byCombo.getOrPut(combo) { HashMap() }.merge(inv, 1, Int::plus)
            if (lines.size < 200) lines += "[$inv] $combo: $detail"
        }
        fun report(title: String, invariants: String) {
            val sb = StringBuilder("\n== $title ==\ncombination".padEnd(42) + invariants.map { " $it  " }.joinToString("") + "\n")
            for (c in combos) {
                val f = byCombo[name(c)]
                sb.append(name(c).padEnd(30)).append(invariants.map { inv -> (f?.get(inv)?.let { "F$it" } ?: "ok").padStart(4) }.joinToString("")).append('\n')
            }
            byCombo.keys.filter { k -> combos.none { name(it) == k } }.forEach { k -> sb.append(k.padEnd(30)).append(byCombo[k]).append('\n') }
            println(sb)
            lines.forEach(::println)
        }
    }

    private fun short(q: String) = q.replace("\n", "\\n").replace("\t", "\\t").let { if (it.length > 90) it.take(87) + "…" else it }

    /** Each tool on its own, outside the slot (which would swallow a throw): (a). */
    private fun direct(q: String, turns: List<String>, f: Failures): Map<String, LabTools.Hit?> = TOOLS.associateWith { id ->
        try { run(id, q, turns) } catch (e: Throwable) { f.add("tool $id", 'a', "${short(q)} -> $e"); null }
    }

    private val modelAnswers = listOf(
        "Yes.", "No, it is not. The first one was in 1969, before 1970.", "<think>2+2 is 4</think>The answer is 4.", "",
        "Não sei. Pergunte de novo.",
    )

    @Test fun `every combination over the corpus keeps invariants a b c d e g`() {
        val f = Failures()
        for ((cat, q) in corpus) {
            val hits = direct(q, emptyList(), f)
            // (a)/(b): a tool gives the same answer twice in a row.
            for (id in TOOLS) runCatching { run(id, q) }.getOrNull().let { if (it != hits[id]) f.add("tool $id", 'b', "not repeatable: ${short(q)}") }
            // (g) on every tool's own answer, and its code belongs to it.
            for ((id, h) in hits) if (h != null) {
                leaks(h.text, q).takeIf { it.isNotEmpty() }?.let { f.add("tool $id", 'g', "${short(q)} -> $it") }
                if (TOOL_PREFIX[id]!!.none { h.tool.startsWith(it) }) f.add("tool $id", 'g', "code ${h.tool} for ${short(q)}")
            }
            // Each tool's own questions: with only it switched on, it answers (the corpus really exercises it).
            if (cat in TOOLS && hits[cat] == null) f.add("tool $cat", 'x', "own question not answered: ${short(q)}")

            for (c in combos) {
                val combo = name(c)
                // (b): the slot's pick, three times, equals the first enabled tool with an answer.
                val picks = (1..3).map { LabTools.first(c.first) { id -> run(id, q) } }
                val expected = c.first.firstNotNullOfOrNull { hits[it] }
                if (picks.any { it != picks[0] } || picks[0] != expected) f.add(combo, 'b', "${short(q)} -> $picks, expected $expected")
                val win = picks[0]
                val by = win?.let(::owner)
                if (by != null && by !in c.first) f.add(combo, 'b', "${short(q)} answered by switched-off $by")
                // (c)
                when (cat) {
                    "health" -> if (by in listOf("facts", "distance", "calc")) f.add(combo, 'c', "health taken by $by: ${short(q)}")
                    "seed" -> if (by in listOf("facts", "distance")) f.add(combo, 'c', "seed taken by $by: ${short(q)}")
                    "pt", "es", "cohazard-pt" -> if (by in listOf("facts", "distance", "cohazard")) f.add(combo, 'c', "$cat taken by $by: ${short(q)}")
                }
                // (d)
                if (cat.startsWith("cohazard") && cat != "cohazard-pt" && "cohazard" in c.first && by != "cohazard")
                    f.add(combo, 'd', "CO question taken by ${by ?: "the model"}: ${short(q)}")
                // (e): the check slot never reads an instant answer; with Guard off a model answer stays as written.
                if (win != null) {
                    if (LabTools.checksAnswer(true, false, false, false, null, false)) f.add(combo, 'e', "check slot reads an instant answer")
                } else for (a in modelAnswers + guardAnswers.filter { it.first == q }.map { it.second }) {
                    val checks = if (c.second) listOf("guard") else emptyList()
                    val applies = LabTools.checksAnswer(false, false, false, false, null, a.isBlank())
                    val repair = if (applies) LabTools.first(checks) { LabTools.guard(q, a) } else null
                    if (!c.second && repair != null) f.add(combo, 'e', "repair with Guard off: ${short(q)}")
                    if (repair != null) leaks(repair.text, q + a).takeIf { it.isNotEmpty() }?.let { f.add(combo, 'g', "guard ${short(q)} -> $it") }
                }
            }
        }
        // (c) over history: an earlier user turn about health keeps every tool but Cohazard off a later question.
        val healthTurns = listOf("My son was bitten by a snake, what do I do?")
        for ((cat, q) in corpus.filter { it.first in listOf("calc", "distance", "facts") }) {
            direct(q, healthTurns, f).forEach { (id, h) -> if (h != null && id != "cohazard") f.add("tool $id", 'c', "after a health turn: ${short(q)}") }
        }
        f.report("corpus: ${corpus.size} questions x ${combos.size} combinations", "abcdegx")
        assertTrue("${f.lines.size} failures, first: ${f.lines.firstOrNull()}", f.lines.isEmpty())
    }

    @Test fun `the winner depends only on the switched-on set, in any question order`() {
        val f = Failures()
        val first = HashMap<Pair<String, String>, LabTools.Hit?>()
        for (c in combos) for ((_, q) in corpus) first[name(c) to q] = LabTools.first(c.first) { run(it, q) }
        // Again, combinations and questions in another order, after the fuzz-style mix of everything else.
        for (c in combos.reversed()) for ((_, q) in corpus.shuffled(Random(7))) {
            val again = LabTools.first(c.first) { run(it, q) }
            if (again != first[name(c) to q]) f.add(name(c), 'b', "order-dependent: ${short(q)}")
        }
        f.report("order independence", "b")
        assertTrue(f.lines.firstOrNull() ?: "", f.lines.isEmpty())
    }

    @Test fun `Guard is sound, keeps thinking blocks and is idempotent (e)`() {
        val f = Failures()
        val questions = (guardAnswers.map { it.first } + corpus.map { it.second }).distinct()
        val answers = guardAnswers.map { it.second }.distinct()
        var repaired = 0
        for (q in questions) for (a in answers) {
            val r = try { LabTools.guard(q, a) } catch (e: Throwable) { f.add("guard", 'a', "${short(q)} / ${short(a)} -> $e"); null } ?: continue
            repaired++
            if (r.text.isBlank()) f.add("guard", 'e', "empty repair: ${short(q)} / ${short(a)}")
            if (!r.tool.startsWith("verdict-guard-")) f.add("guard", 'e', "code ${r.tool}")
            val before = AnswerText.splitThinking(a)
            val after = AnswerText.splitThinking(r.text)
            if (before.thinking != after.thinking || before.thinkingInProgress != after.thinkingInProgress ||
                listOf("<think>", "</think>", "<|channel>thought", "<channel|>").any { m -> a.split(m).size != r.text.split(m).size })
                f.add("guard", 'e', "thinking block changed: ${short(a)} -> ${short(r.text)}")
            if (QueryContext.isHealthQuestion(q) || QueryContext.isPortugueseQuestion(q) || QueryContext.isSpanishQuestion(q))
                f.add("guard", 'e', "repaired a health/PT/ES question: ${short(q)}")
            val again = LabTools.guard(q, r.text)
            if (again != null && again.text != r.text) f.add("guard", 'e', "not idempotent: ${short(q)} / ${short(a)}\n  1: ${short(r.text)}\n  2: ${short(again.text)}")
            leaks(r.text, q + a).takeIf { it.isNotEmpty() }?.let { f.add("guard", 'g', "${short(q)} / ${short(a)} -> $it") }
        }
        // Not reachable through the slot (the check slot skips instant answers), counted for the record.
        val wouldTouch = corpus.flatMap { (_, q) -> TOOLS.mapNotNull { run(it, q) }.map { q to it } }.count { (q, h) -> LabTools.guard(q, h.text) != null }
        println("guard: ${questions.size} questions x ${answers.size} answers, $repaired repaired; tool answers it would change if it read them: $wouldTouch")
        f.lines.forEach(::println)
        assertTrue(f.lines.firstOrNull() ?: "", f.lines.isEmpty())
    }

    @Test fun `each tool decides fast, warm, even on 20,000 characters (f)`() {
        val deciders: Map<String, (String) -> Any?> = TOOLS.associateWith { id -> { q: String -> run(id, q) } } +
            ("guard" to { q: String -> LabTools.guard(q, guardAnswers[1].second) }) +
            ("guard (20k answer)" to { q: String -> LabTools.guard(q.take(200), guardAnswers.last().second) })
        val qs = corpus.map { it.second }
        val report = StringBuilder("\n== decision time per question, ms (min of 3, warm) ==\n")
        val slow = ArrayList<String>()
        for ((id, d) in deciders) {
            repeat(2) { qs.forEach { runCatching { d(it) } } }
            val times = qs.map { q -> q to (1..3).minOf { val t = System.nanoTime(); runCatching { d(q) }; (System.nanoTime() - t) / 1e6 } }
            val sorted = times.map { it.second }.sorted()
            val worst = times.maxBy { it.second }
            report.append("${id.padEnd(20)} median ${"%.3f".format(sorted[sorted.size / 2])}  p95 ${"%.3f".format(sorted[sorted.size * 95 / 100])}  " +
                "max ${"%.2f".format(worst.second)} (${worst.first.length} chars: ${short(worst.first).take(50)})\n")
            times.filter { it.second > 200 }.forEach { slow += "$id ${"%.1f".format(it.second)} ms on ${it.first.length} chars: ${short(it.first)}" }
        }
        println(report)
        slow.forEach(::println)
        assertTrue(slow.joinToString("\n"), slow.isEmpty())
    }

    @Test fun `5,000 fuzzed strings through all 32 combinations keep a b g`() {
        val rnd = Random(20261018)
        val words = corpus.filter { it.second.length < 400 }.flatMap { it.second.split(' ', '\n', '\t') }.filter { it.isNotBlank() }.distinct()
        val bits = listOf(
            "+", "-", "*", "/", "^", "!", "%", "(", ")", "=", ".", ",", "?", "0", "1e308", "-1", "0.0", "999999999999", "1/0", "0/0",
            "km", "L", "mph", "°C", "°F", "ft", "m", "mAh", "V", "Wh", "kg", "from", "to", "and back", "how far", "how long", "how many",
            "generator", "garage", "tent", "charcoal", "stove", "indoors", "Paris", "Rome", "São Paulo", "Zürich", "Brazil", "population",
            "é", "ã", "ç", "ü", "🐗", "\u200B", "\u200D", "\u202E", "ا", "ש", "日本", "\n", "\t", " ", "  ", "\"", "'", "{{", "}}", "<think>", "</think>",
        )
        val f = Failures()
        repeat(5_000) { n ->
            val q = (1..rnd.nextInt(1, 40)).joinToString(if (rnd.nextInt(4) == 0) "" else " ") {
                if (rnd.nextInt(3) == 0) bits[rnd.nextInt(bits.size)] else words[rnd.nextInt(words.size)]
            }
            val hits = direct(q, emptyList(), f)
            for (id in TOOLS) if (runCatching { run(id, q) }.getOrNull() != hits[id]) f.add("tool $id", 'b', "fuzz #$n not repeatable: ${short(q)}")
            for ((id, h) in hits) if (h != null) leaks(h.text, q).takeIf { it.isNotEmpty() }?.let { f.add("tool $id", 'g', "fuzz #$n ${short(q)} -> $it") }
            try { LabTools.guard(q, q)?.let { r -> leaks(r.text, q).takeIf { it.isNotEmpty() }?.let { f.add("guard", 'g', "fuzz #$n -> $it") } } }
            catch (e: Throwable) { f.add("guard", 'a', "fuzz #$n ${short(q)} -> $e") }
            // The slot over the answers just computed: its pick is the first enabled tool's, and only an enabled one's.
            for (c in combos) {
                val win = LabTools.first(c.first) { hits[it] }
                if (win != c.first.firstNotNullOfOrNull { hits[it] } || (win != null && owner(win) !in c.first)) f.add(name(c), 'b', "fuzz #$n ${short(q)}")
            }
        }
        f.report("fuzz: 5,000 strings x ${combos.size} combinations", "abg")
        assertTrue("${f.lines.size} failures, first: ${f.lines.firstOrNull()}", f.lines.isEmpty())
    }

    @Test fun `the slot skips a tool that throws and keeps going`() {
        val hit = LabTools.Hit("t", "x")
        assertTrue(LabTools.first(listOf<() -> LabTools.Hit?>({ error("boom") }, { null }, { hit })) { it() } == hit)
    }
}
