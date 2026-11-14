package team.sopa.boar.engine.canvas

import team.sopa.boar.engine.routing.Calculators

/**
 * Cards, rules first (adam's interview, Nov 14; t9 0207): the cards an answer's own text holds, found with no model.
 * Conservative: when a pairing is not certain, no card. Every label and number is copied from the text. Pure.
 *
 * - Steps: 3 or more consecutive numbered lines; or 3 or more bullet lines when the question is a how-to.
 * - Table: a Markdown table.
 * - Key facts: 3 or more "Label: value" lines.
 * - Comparison: 2 to 4 lines that each start "Name:" with their points, when the question compares.
 * - Chart: 3 or more (name, number, same unit), from list lines or one enumeration ("Everest at 8,849 m, K2 at 8,611 m
 *   and Kangchenjunga at 8,586 m"). Shapes: bars and table always; a pie only for percentages adding up to about 100;
 *   a line only when the labels are years in order.
 */
object CardRules {
    enum class Shape { BARS, TABLE, PIE, LINE, CHECKLIST, FACTS, COMPARE }

    sealed class Card(val shapes: List<Shape>) {
        class Steps(val items: List<String>) : Card(listOf(Shape.CHECKLIST))
        class Table(val header: List<String>, val rows: List<List<String>>) : Card(listOf(Shape.TABLE))
        class Facts(val pairs: List<Pair<String, String>>) : Card(listOf(Shape.FACTS))
        class Compare(val sides: List<Pair<String, List<String>>>) : Card(listOf(Shape.COMPARE))
        class Chart(val points: List<Point>, val unit: String, shapes: List<Shape>) : Card(shapes)
    }

    /** One value of a series: the label and the number as the answer writes them, and the number read. */
    data class Point(val label: String, val written: String, val value: Double)

    private val HOW_TO = Regex("""\b(how (do|can|to|should)|step[- ]by[- ]step|steps to|como (fa[zç]|fazer|purificar|montar|trocar|preparar|acender)|passo a passo)\b""", RegexOption.IGNORE_CASE)
    private val COMPARES = Regex("""\b(compare|comparison|difference|differ|versus|vs\.?|better than|comparar|compara[çc][ãa]o|diferen[çc]a|melhor que)\b""", RegexOption.IGNORE_CASE)

    fun find(question: String, rawAnswer: String): List<Card> {
        // Models write "100\u202f°C" (a narrow no-break space): the rules read it as a space (4B, the F3, Nov 14).
        val answer = rawAnswer.replace('\u202f', ' ').replace('\u00a0', ' ')
        val lines = answer.lines().map { it.trim() }
        val cards = ArrayList<Card>()
        table(lines)?.let { cards += it }
        steps(lines, HOW_TO.containsMatchIn(question))?.let { cards += it }
        val labelled = labelled(lines)
        if (COMPARES.containsMatchIn(question) && labelled.size in 2..4) cards += Card.Compare(labelled.map { (k, v) -> k to splitPoints(v) })
        else if (labelled.size >= 3) cards += Card.Facts(labelled)
        (chart(lines, answer) ?: namedInQuestion(question, answer))?.let { cards += it }
        // Best first: what the question asks for, then the richest.
        return cards.sortedBy { c ->
            when {
                c is Card.Steps && HOW_TO.containsMatchIn(question) -> 0
                c is Card.Compare -> 0
                c is Card.Chart -> 1
                c is Card.Table -> 2
                c is Card.Steps -> 3
                else -> 4
            }
        }
    }

    // ---- Steps ----

    private val NUMBERED = Regex("""^(\d{1,2})[.)]\s+(.+)$""")
    private val BULLET = Regex("""^[-*•]\s+(.+)$""")

    private fun steps(lines: List<String>, howTo: Boolean): Card.Steps? {
        var best = emptyList<String>()
        var run = ArrayList<String>()
        var expect = 1
        for (l in lines) {
            val m = NUMBERED.find(l)
            if (m != null && m.groupValues[1].toInt() == expect) { run += clean(m.groupValues[2]); expect++ }
            else if (l.isEmpty() && run.isNotEmpty()) continue
            else { if (run.size > best.size) best = run; run = ArrayList(); expect = if (m != null && m.groupValues[1] == "1") { run += clean(m.groupValues[2]); 2 } else 1 }
        }
        if (run.size > best.size) best = run
        if (best.size >= 3) return Card.Steps(best)
        if (!howTo) return null
        val bullets = consecutive(lines) { BULLET.find(it)?.groupValues?.get(1)?.let(::clean) }
        return bullets.takeIf { it.size >= 3 }?.let { Card.Steps(it) }
    }

    private fun consecutive(lines: List<String>, item: (String) -> String?): List<String> {
        var best = emptyList<String>(); var run = ArrayList<String>()
        for (l in lines) {
            val v = item(l)
            if (v != null) run += v else if (l.isNotEmpty()) { if (run.size > best.size) best = run; run = ArrayList() }
        }
        return if (run.size > best.size) run else best
    }

    // ---- Table ----

    private val SEP = Regex("""^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$""")

    private fun table(lines: List<String>): Card.Table? {
        for (i in 0 until lines.size - 2) {
            if (!lines[i].contains('|') || !SEP.matches(lines[i + 1])) continue
            val header = cells(lines[i])
            val rows = lines.drop(i + 2).takeWhile { it.contains('|') }.map(::cells).filter { it.size == header.size }
            if (header.size >= 2 && rows.isNotEmpty()) return Card.Table(header, rows)
        }
        return null
    }

    private fun cells(l: String) = l.trim().trim('|').split('|').map { clean(it.trim()) }

    // ---- Key facts and comparison ----

    private val LABEL = Regex("""^(?:[-*•]\s+)?\**([\p{L}][\p{L}\d '’()/-]{0,38}?)\**\s*:\s*\**\s*(.+)$""")

    private fun labelled(lines: List<String>): List<Pair<String, String>> =
        consecutive(lines) { l -> LABEL.find(l)?.takeIf { !it.groupValues[1].endsWith(" ") }?.let { it.groupValues[1].trim() + "\u0000" + clean(it.groupValues[2]) } }
            .map { it.substringBefore('\u0000') to it.substringAfter('\u0000') }
            .takeIf { ps -> ps.map { it.first.lowercase() }.toSet().size == ps.size } ?: emptyList()

    private fun splitPoints(v: String): List<String> = v.split(Regex("""[;•]|,\s(?=[\p{Ll}])""")).map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }

    // ---- Chart ----

    private const val NUM = """\d{1,3}(?:[.,\s]\d{3})+(?:[.,]\d+)?|\d+(?:[.,]\d+)?"""
    private const val UNITS = """%|°C|°F|º|km²|km2|sq km|m²|km|m|cm|mm|kg|g|mg|L|ml|mL|ft|feet|metres|meters|metros|miles|milhas|years|anos|million|millions|milhões|billion|bilhões|AU|K"""
    private val LIST_POINT = Regex("""^(?:[-*•]|\d{1,2}[.)])\s+\**(\d{4}(?=\**\s*:)|[\p{L}][^:\d]{0,40}?)\**\s*(?::|—|-|–|,|at|is|em|com|tem)?\s*(?:about|around|approximately|roughly|cerca de|aproximadamente)?\s*($NUM)\s*($UNITS)(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
    private val ENUM_POINT = Regex("""(\p{Lu}[\p{L}\d'’-]*(?:\s(?:\p{Lu}[\p{L}\d'’-]*|\d+))*)\s(?:at|is|was|with|of|em|com|tem|has)?\s*(?:about|around|approximately|roughly|cerca de)?\s*\(?($NUM)\s*($UNITS)(?![\p{L}\d])""")

    private fun chart(lines: List<String>, answer: String): Card.Chart? {
        val mark = Calculators.readsComma(answer, false)
        fun point(label: String, num: String, unit: String) = Point(clean(label).trim().trimEnd(':', ',', '-', '—'), "$num $unit".trim(), Calculators.parseNumber(num, mark))
        val fromList = lines.mapNotNull { l -> LIST_POINT.find(l)?.let { m -> point(m.groupValues[1], m.groupValues[2], m.groupValues[3]) to m.groupValues[3].lowercase() } }
        val series = sameUnit(fromList) ?: answer.split(Regex("""(?<=[.!?])\s+""")).asSequence()
            .map { s -> ENUM_POINT.findAll(s).map { m -> point(m.groupValues[1], m.groupValues[2], m.groupValues[3]) to m.groupValues[3].lowercase() }.toList() }
            .mapNotNull(::sameUnit).firstOrNull() ?: return null
        val (points, unit) = series
        val shapes = mutableListOf(Shape.BARS, Shape.TABLE)
        if (unit == "%" && points.sumOf { it.value } in 95.0..105.0) shapes += Shape.PIE
        val years = points.map { it.label.toIntOrNull() }
        if (years.all { it != null && it in 1000..2999 } && years.zipWithNext().all { (a, b) -> a!! < b!! }) shapes += Shape.LINE
        return Card.Chart(points, unit, shapes)
    }

    /**
     * The items the question names, one per sentence (the F3 measurement, Nov 14: "Water boils at 100 °C… Ethanol boils at
     * approximately 78 °C… Mercury boils at 357 °C", never a list). Certain only because the question named them: each
     * named item in the answer's first paragraph, in a sentence with exactly one number of the shared unit ("million"
     * and "billion" read as part of the number). Three or more, or none.
     */
    private val SCALED = Regex("""($NUM)\s*(million|billion|milhões|bilhões)?\s*(%|°C|°F|km|m|kg|g|L|ft|feet|miles|metres|meters|metros|quilômetros|kilometers|kilometres)(?![\p{L}\d])""", RegexOption.IGNORE_CASE)

    private fun namedInQuestion(question: String, answer: String): Card.Chart? {
        // The list after the last "of" / "de" / "are" that holds one ("the boiling points of water, ethanol and mercury").
        val list = Regex("""\b(?:of|de|do|da|dos|das|are|is|between|entre)\s""", RegexOption.IGNORE_CASE).findAll(question).toList().asReversed()
            .map { question.substring(it.range.last + 1).substringBefore('?').trim() }
            .firstOrNull { it.contains(',') || Regex("""\s(and|e)\s""").containsMatchIn(it) } ?: return null
        val names = list.split(Regex(""",\s*|\s+(?:and|e)\s+""")).map { it.trim().removePrefix("the ").removePrefix("The ").removePrefix("o ").removePrefix("a ") }
            .map { it.substringBefore(" from ").substringBefore(",").trim() }.filter { it.length >= 3 }
        if (names.size < 3) return null
        val mark = Calculators.readsComma(answer, false)
        val first = answer.split(Regex("""\n\s*\n""")).first()
        val sentences = first.split(Regex("""(?<=[.!?])\s+"""))
        val points = ArrayList<Pair<Point, String>>()
        for (name in names) {
            val hits = sentences.filter { Regex("""\b${Regex.escape(name)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            val s = hits.firstOrNull() ?: return null
            val bare = s.replace(Regex("""\([^)]*\)"""), "").replace(Regex("""\[\d{1,3}]"""), "")
            // Exactly one number in the sentence: a range ("54.6 million to 401 million km") is not one value.
            if (Regex(NUM).findAll(bare).count() != 1) return null
            val nums = SCALED.findAll(bare).toList()
            val unitsHere = nums.map { it.groupValues[3].lowercase() }.toSet()
            if (nums.isEmpty() || unitsHere.size != 1) return null
            val m = nums.first()
            val scale = when (m.groupValues[2].lowercase()) { "million", "milhões" -> 1e6; "billion", "bilhões" -> 1e9; else -> 1.0 }
            val label = Regex("""\b${Regex.escape(name)}\b""", RegexOption.IGNORE_CASE).find(s)!!.value
            val written = listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3]).filter { it.isNotEmpty() }.joinToString(" ")
            points += Point(label.replaceFirstChar { it.uppercase() }, written, Calculators.parseNumber(m.groupValues[1], mark) * scale) to m.groupValues[3].lowercase()
        }
        val (ps, unit) = sameUnit(points) ?: return null
        return Card.Chart(ps, points.first().second.let { u -> if (u == "%") "%" else u }, listOf(Shape.BARS, Shape.TABLE))
    }

    /** Three or more points with one unit and distinct labels; else none. */
    private fun sameUnit(ps: List<Pair<Point, String>>): Pair<List<Point>, String>? {
        if (ps.size < 3) return null
        val unit = ps.first().second
        if (ps.any { it.second != unit } || ps.any { !(it.first.value.isFinite()) || it.first.label.isBlank() }) return null
        if (ps.map { it.first.label.lowercase() }.toSet().size != ps.size) return null
        return ps.map { it.first } to ps.first().first.written.substringAfter(' ', unit)
    }

    private fun clean(s: String) = s.replace("**", "").replace("__", "").replace(Regex("""\s*\[\d{1,3}]"""), "").trim()
}
