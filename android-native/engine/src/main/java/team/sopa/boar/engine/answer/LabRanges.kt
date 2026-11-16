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
    private val UNDER = Regex("""^\s*(?:below|under|less than|lower than|up to|<|≤|<=|abaixo de|menor que|menos de|até|inferior a)\s*($NUM)\s*($UNIT)?\s*$""", RegexOption.IGNORE_CASE)
    private val OVER = Regex("""^\s*(?:above|over|more than|greater than|at least|>|≥|>=|acima de|maior que|mais de|superior a|pelo menos)\s*($NUM)\s*($UNIT)?\s*$""", RegexOption.IGNORE_CASE)

    private fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    private fun sameUnit(a: String?, b: String?) = a.isNullOrBlank() || b.isNullOrBlank() || a.equals(b, ignoreCase = true)

    /** The mark for a value against a range written as the file writes it; null when it can't tell. */
    fun mark(value: String, range: String): Mark? {
        val v = VALUE.matchEntire(value.trim()) ?: return null
        val x = num(v.groupValues[1]) ?: return null
        val unit = v.groupValues[2].ifBlank { null }
        BETWEEN.matchEntire(range.trim())?.let { m ->
            val lo = num(m.groupValues[1]) ?: return null; val hi = num(m.groupValues[3]) ?: return null
            if (lo > hi || !sameUnit(unit, m.groupValues[4].ifBlank { m.groupValues[2] })) return null
            return when { x < lo -> Mark.BELOW; x > hi -> Mark.ABOVE; else -> Mark.WITHIN }
        }
        UNDER.matchEntire(range.trim())?.let { m ->
            val lim = num(m.groupValues[1]) ?: return null
            if (!sameUnit(unit, m.groupValues[2])) return null
            return if (x < lim) Mark.WITHIN else Mark.ABOVE
        }
        OVER.matchEntire(range.trim())?.let { m ->
            val lim = num(m.groupValues[1]) ?: return null
            if (!sameUnit(unit, m.groupValues[2])) return null
            return if (x > lim) Mark.WITHIN else Mark.BELOW
        }
        return null
    }

    /** Every result row in [text] the code can mark. */
    fun rows(text: String): List<Row> = text.lines().mapNotNull { l ->
        val m = TABLE_ROW.find(l) ?: LINE_ROW.find(l) ?: return@mapNotNull null
        val (name, value, range) = Triple(m.groupValues[1].trim(), m.groupValues[2].trim(), m.groupValues[3].trim())
        mark(value, range)?.let { Row(name, value, range, it) }
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
        return head + "\n" + rs.joinToString("\n") { "- ${it.name}: ${it.value}, ${word(it.mark)} (${it.range})" }
    }
}
