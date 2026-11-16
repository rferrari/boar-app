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

    /**
     * Longer than any answer (2,048 tokens is about 10,000 characters): ENUM_POINT is quadratic on a long capitalised
     * token (validator B-7: 2.5 s at 20,000 characters, only from degenerate model output). Past it, no card.
     */
    const val MAX_ANSWER = 12_000
    private const val MAX_SENTENCE = 600

    fun find(question: String, rawAnswer: String): List<Card> {
        if (rawAnswer.length > MAX_ANSWER) return emptyList()
        // Models write "100\u202f°C" (a narrow no-break space): the rules read it as a space (4B, the F3, Nov 14).
        val answer = rawAnswer.replace('\u202f', ' ').replace('\u00a0', ' ')
        val lines = answer.lines().map { it.trim() }
        val cards = ArrayList<Card>()
        table(lines)?.let { cards += it }
        steps(lines, HOW_TO.containsMatchIn(question))?.let { cards += it }
        val labelled = labelled(lines)
        if (COMPARES.containsMatchIn(question) && labelled.size in 2..4) cards += Card.Compare(labelled.map { (k, v) -> k to splitPoints(v) })
        else if (labelled.size >= 3) cards += Card.Facts(labelled)
        (chart(lines, answer, question) ?: namedInQuestion(question, answer))?.let { cards += it }
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
        consecutive(lines) { l -> LABEL.find(l)?.takeIf { !it.groupValues[1].endsWith(" ") && !clock(it) }?.let { it.groupValues[1].trim() + "\u0000" + clean(it.groupValues[2]) } }
            .map { it.substringBefore('\u0000') to it.substringAfter('\u0000') }
            .takeIf { ps -> ps.map { it.first.lowercase() }.toSet().size == ps.size } ?: emptyList()

    /**
     * The colon is a clock time's ("Breakfast 08:00" read as Breakfast 08 -> 00), not a label's (validator B-10). A label
     * whose value holds a time stays a fact: "Opening hours: 09:00 to 17:00".
     */
    private fun clock(m: MatchResult) = Regex("""\d$""").containsMatchIn(m.groupValues[1].trim('*')) && CLOCK_VALUE.matches(m.groupValues[2].trim())
    /** What's left of a time after the colon: the minutes alone, with h/am/pm, or a range ("00", "30h", "00 - 13:00"); not "10 points" (R-B5). */
    private val CLOCK_VALUE = Regex("""^\**\s*\d{2}(?:\s*(?:h|am|pm|a\.m\.|p\.m\.))?\**(?:\s*(?:-|–|to|até|a)\s*\d{1,2}(?::\d{2})?(?:\s*(?:h|am|pm))?)?\s*$""", RegexOption.IGNORE_CASE)

    private fun splitPoints(v: String): List<String> = v.split(Regex("""[;•]|,\s(?=[\p{Ll}])""")).map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }

    // ---- Chart ----

    private const val NUM = """\d{1,3}(?:[.,\s]\d{3})+(?:[.,]\d+)?|\d+(?:[.,]\d+)?"""
    /** A minus only right against the digits ("-67 °C", "−67 °C"); "Everest - 8,849 m" keeps the dash as a separator (validator B-9). */
    private const val SIGNED = """[-−](?=\d)(?:$NUM)|(?:$NUM)"""
    private const val UNITS = """%|°C|°F|º|km²|km2|sq km|m²|km|m|cm|mm|kg|g|mg|L|ml|mL|ft|feet|metres|meters|metros|miles|milhas|years|anos|million|millions|milhões|billion|bilhões|AU|K"""
    private val LIST_POINT = Regex("""^(?:[-*•]|\d{1,2}[.)])\s+\**(\d{4}(?=\**\s*:)|[\p{L}](?:[^:\d]|(?<=\p{L})\d(?!\d*\s*(?:$UNITS)\b)){0,40}?)\**\s*(?::|—|-|–|,|at|is|em|com|tem)?\s*(?:about|around|approximately|roughly|cerca de|aproximadamente)?\s*($SIGNED)\s*($UNITS)(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
    private val ENUM_POINT = Regex("""(\p{Lu}[\p{L}\d'’-]*(?:\s(?:\p{Lu}[\p{L}\d'’-]*|\d+))*)\s(?:at|is|was|with|of|em|com|tem|has)?\s*(?:about|around|approximately|roughly|cerca de)?\s*\(?($SIGNED)\s*($UNITS)(?![\p{L}\d])""")

    private fun chart(lines: List<String>, answer: String, question: String = ""): Card.Chart? {
        val mark = markOf(answer, question)
        fun point(label: String, num: String, unit: String) = Point(clean(label).trim().trimEnd(':', ',', '-', '—'), "$num $unit".trim(), signed(num, mark))
        val fromList = lines.mapNotNull { l -> LIST_POINT.find(l)?.let { m -> point(m.groupValues[1], m.groupValues[2], m.groupValues[3]) to m.groupValues[3].lowercase() } }
        val series = sameUnit(fromList) ?: answer.split(Regex("""(?<=[.!?])\s+""")).asSequence()
            // A "sentence" past 600 characters is a degenerate run, not prose: ENUM_POINT is quadratic on one (R-E4).
            .filter { it.length <= MAX_SENTENCE }
            .map { s -> ENUM_POINT.findAll(s).map { m -> point(m.groupValues[1], m.groupValues[2], m.groupValues[3]) to m.groupValues[3].lowercase() }.toList() }
            .mapNotNull(::sameUnit).firstOrNull() ?: return null
        val (points, unit) = series
        // Bars draw a length: with a value below zero they would show the coldest place as the hottest. The table keeps the sign.
        if (points.any { it.value < 0 }) return Card.Chart(points, unit, listOf(Shape.TABLE))
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
        val mark = markOf(answer, question)
        val first = answer.split(Regex("""\n\s*\n""")).first()
        val sentences = first.split(Regex("""(?<=[.!?])\s+"""))
        val points = ArrayList<Pair<Point, String>>()
        fun at(name: String, s: String) = Regex("""\b${Regex.escape(name)}\b""", RegexOption.IGNORE_CASE).find(s)?.range?.first
        // A sentence's number belongs to the first item it names, named before the number: "Ethanol boils at 78 °C, well
        // below water" is ethanol's, never water's (validator B-8).
        fun owner(s: String): String? {
            val firstNum = Regex(NUM).find(s)?.range?.first ?: return null
            return names.mapNotNull { n -> at(n, s)?.let { n to it } }.filter { it.second < firstNum }.minByOrNull { it.second }?.first
        }
        for (name in names) {
            val hits = sentences.filter { owner(it.replace(Regex("""\([^)]*\)"""), "").replace(Regex("""\[\d{1,3}]"""), "")) == name }
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

    /** The number with its sign: "-67" or "−67" reads -67.0. */
    private fun signed(num: String, mark: Boolean): Double =
        if (num.startsWith('-') || num.startsWith('−')) -Calculators.parseNumber(num.substring(1), mark) else Calculators.parseNumber(num, mark)

    /**
     * The decimal mark for a card's numbers: the answer's own numbers first ([Calculators.readsComma]), else its language,
     * else the question's (a list of names and numbers says little).
     * A Portuguese answer's "8.849 m" is 8849 (validator B-11: "Everest: 8.849 m … Morro: 300 m" drew Everest under the
     * hill).
     */
    private fun markOf(answer: String, question: String): Boolean {
        // The answer's language, else the question's, else the app's (AnswerLanguage.of ends on the app setting; R-B2:
        // "Montanhas mais altas?" says too little).
        val lang = team.sopa.boar.engine.answer.AnswerLanguage.clear(answer) ?: team.sopa.boar.engine.answer.AnswerLanguage.of(question)
        return Calculators.readsComma(answer, lang == team.sopa.boar.engine.answer.AnswerLanguage.Lang.PT)
    }

    // Every mark shape, the model's cards' too (CiteMarks; validator Q-A4: a rules card kept "[1, 2]" in a step).
    private fun clean(s: String) = CiteMarks.strip(s.replace("**", "").replace("__", "")).trim()
}
