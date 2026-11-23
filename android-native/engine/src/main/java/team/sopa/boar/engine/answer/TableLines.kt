package team.sopa.boar.engine.answer

/**
 * A Markdown table in an answer, as lines the chat can show (t9 2200 d: a copied blood-test table showed its raw "|"
 * pipes; the chat's Markdown draws no tables). Each data row becomes "First: second (third, fourth)", the header and the
 * separator row go. Text that isn't a table is left exactly as it was. Pure.
 */
object TableLines {
    private val ROW = Regex("""^\s*\|.*\|\s*$""")
    private val SEP = Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$""")

    fun of(text: String): String {
        if ('|' !in text) return text
        val lines = text.lines()
        val out = ArrayList<String>()
        var i = 0
        while (i < lines.size) {
            // A table: a row, a separator row, then rows.
            if (ROW.matches(lines[i]) && i + 1 < lines.size && SEP.matches(lines[i + 1])) {
                var j = i + 2
                while (j < lines.size && ROW.matches(lines[j])) {
                    // An empty cell keeps its place as "—" (validator P-C5: a blank result read as the range).
                    val raw = lines[j].trim().trim('|').split('|').map { it.trim() }
                    val cells = if (raw.all { it.isEmpty() }) emptyList() else raw.map { it.ifEmpty { "—" } }
                    if (cells.isNotEmpty()) out += when (cells.size) {
                        1 -> cells[0]
                        2 -> "${cells[0]}: ${cells[1]}"
                        else -> "${cells[0]}: ${cells[1]} (${cells.drop(2).joinToString(", ")})"
                    }
                    j++
                }
                i = j
                continue
            }
            out += lines[i]
            i++
        }
        return out.joinToString("\n")
    }
}
