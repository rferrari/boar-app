package team.sopa.boar.engine.answer

/**
 * Results against their reference ranges, decided in code (t9 2330 b; adam: "send blood test fix"). On the F3 the 1.7B
 * summarized a blood test as "Total cholesterol and LDL levels are below the target thresholds" (231 against "below 190",
 * 162 against "below 130") and HDL 48 as "below … above 40". The model is given these marks with the passage, so it
 * doesn't compare numbers itself. A row the code can't read (no number, no range it knows, units that differ) is left
 * unmarked, never guessed. Pure: text in, marks out.
 */
object LabRanges {
    enum class Mark { WITHIN, ABOVE, BELOW }

    /** One result: its name, value and range as the file writes them, and the mark. */
    data class Row(val name: String, val value: String, val range: String, val mark: Mark)

    private const val NUM = """\d+(?:[.,]\d+)?"""
    private const val UNIT = """[\p{L}µ%/]+(?:/[\p{L}]+)?"""

    /** "| Test | Result | Reference |" rows, or "Test: Result (Reference)" lines. */
    private val TABLE_ROW = Regex("""^\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|""")
    private val LINE_ROW = Regex("""^\s*[-*•]?\s*([\p{L}][^:\n]{0,40}?)\s*:\s*($NUM\s*$UNIT?)\s*\(([^)\n]{2,40})\)""")
    private val VALUE = Regex("""^\s*($NUM)\s*($UNIT)?\s*$""")

    // The ranges, EN and PT, each with its unit (optional).
    private val BETWEEN = Regex("""^\s*(?:de\s+|entre\s+|from\s+)?($NUM)\s*($UNIT)?\s*(?:to|-|–|a|até|e|and)\s*($NUM)\s*($UNIT)?\s*$""", RegexOption.IGNORE_CASE)
    // "Up to 190", "até 190", "≤ 190" include 190; "below 190" doesn't (validator P-C2: 190 against "até 190" read ABOVE).
    private val UNDER = Regex("""^\s*(below|under|less than|lower than|up to|no more than|<=|≤|<|abaixo de|menor que|menos de|até|no máximo|inferior a)\s*($NUM)\s*($UNIT)?\s*$""", RegexOption.IGNORE_CASE)
    private val OVER = Regex("""^\s*(above|over|more than|greater than|at least|no less than|>=|≥|>|acima de|maior que|mais de|superior a|pelo menos|no mínimo)\s*($NUM)\s*($UNIT)?\s*$""", RegexOption.IGNORE_CASE)
    private val INCLUSIVE = Regex("""^(?:up to|no more than|<=|≤|até|no máximo|at least|no less than|>=|≥|pelo menos|no mínimo)$""", RegexOption.IGNORE_CASE)

    /**
     * "1.250" or "1,250": a thousands mark or a decimal one, the file doesn't say (validator P-C1: 1.250 mg/dL against
     * "abaixo de 150" read WITHIN, a false comfort). One separator, three digits after it, a non-zero whole part.
     */
    private val AMBIGUOUS = Regex("""(?<![\d.,])[1-9]\d{0,2}[.,]\d{3}(?![\d.,])""")

    private fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    /**
     * The range's unit agrees with the value's. A value with a unit against a range without one is compared only when the
     * table's header names that unit (validator P-C3: 6,5 mmol/L against "abaixo de 190" read WITHIN).
     */
    private fun sameUnit(value: String?, range: String?, context: String) = when {
        value.isNullOrBlank() -> true
        range.isNullOrBlank() -> Regex("(?<![\\p{L}])${Regex.escape(value)}(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(context)
        else -> value.equals(range, ignoreCase = true)
    }

    /**
     * The mark for a value against a range written as the file writes it; null when it can't tell. [context]: the table's
     * header row, where a unit for the whole column may be written.
     */
    fun mark(value: String, range: String, context: String = ""): Mark? {
        // Units or magnitudes that don't line up: never guess a direction (t9 0435).
        if (AMBIGUOUS.containsMatchIn(value) || AMBIGUOUS.containsMatchIn(range)) return null
        val v = VALUE.matchEntire(value.trim()) ?: return null
        val x = num(v.groupValues[1]) ?: return null
        val unit = v.groupValues[2].ifBlank { null }
        BETWEEN.matchEntire(range.trim())?.let { m ->
            val lo = num(m.groupValues[1]) ?: return null; val hi = num(m.groupValues[3]) ?: return null
            if (lo > hi || !sameUnit(unit, m.groupValues[4].ifBlank { m.groupValues[2] }, context)) return null
            return when { x < lo -> Mark.BELOW; x > hi -> Mark.ABOVE; else -> Mark.WITHIN }
        }
        UNDER.matchEntire(range.trim())?.let { m ->
            val lim = num(m.groupValues[2]) ?: return null
            if (!sameUnit(unit, m.groupValues[3], context)) return null
            val within = if (INCLUSIVE.matches(m.groupValues[1])) x <= lim else x < lim
            return if (within) Mark.WITHIN else Mark.ABOVE
        }
        OVER.matchEntire(range.trim())?.let { m ->
            val lim = num(m.groupValues[2]) ?: return null
            if (!sameUnit(unit, m.groupValues[3], context)) return null
            val within = if (INCLUSIVE.matches(m.groupValues[1])) x >= lim else x > lim
            return if (within) Mark.WITHIN else Mark.BELOW
        }
        return null
    }

    /** Every result row in [text] the code can mark. */
    fun rows(text: String): List<Row> {
        val lines = text.lines()
        // A table's header: the row right above its "|---|" line; its words can give a column's unit.
        val sep = Regex("""^\s*\|?\s*:?-{3,}""")
        return lines.withIndex().mapNotNull { (i, l) ->
            if (sep.containsMatchIn(l)) return@mapNotNull null
            val m = TABLE_ROW.find(l) ?: LINE_ROW.find(l) ?: return@mapNotNull null
            val header = if (m.value.trimStart().startsWith("|")) lines.subList(0, i).withIndex().lastOrNull { (k, _) -> k + 1 < lines.size && sep.containsMatchIn(lines[k + 1]) }?.value.orEmpty() else ""
            val (name, value, range) = Triple(m.groupValues[1].trim(), m.groupValues[2].trim(), m.groupValues[3].trim())
            mark(value, range, header)?.let { Row(name, value, range, it) }
        }
    }

    /**
     * The marks as one block for the model, in [pt] Portuguese or English; null when no row could be marked. Each row
     * names the result, its value and range as written, and the mark in capitals.
     */
    fun note(text: String, pt: Boolean): String? {
        val rs = rows(text).takeIf { it.isNotEmpty() } ?: return null
        fun word(m: Mark) = when (m) {
            Mark.WITHIN -> if (pt) "DENTRO da faixa" else "WITHIN its range"
            Mark.ABOVE -> if (pt) "ACIMA da faixa" else "ABOVE its range"
            Mark.BELOW -> if (pt) "ABAIXO da faixa" else "BELOW its range"
        }
        // Said as BOAR's, not the file's: it shows in the Sources view with the passage.
        val head = if (pt) "Calculado pelo BOAR com os números deste arquivo (não está escrito no arquivo); cada resultado e a sua faixa:" else
            "Computed by BOAR from this file's own numbers (not written in the file); each result against its range:"
        // The range as a reference, "(ref. below 190 mg/dL)": "ACIMA da faixa (abaixo de 190)" came back from the model as
        // "ACIMA da faixa de abaixo de 190" (t9 0115 item 5).
        return head + "\n" + rs.joinToString("\n") { "- ${it.name}: ${it.value}, ${word(it.mark)} (ref. ${it.range})" }
    }
}
