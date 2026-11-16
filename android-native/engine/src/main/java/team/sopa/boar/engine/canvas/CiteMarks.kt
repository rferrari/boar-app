package team.sopa.boar.engine.canvas

/**
 * The answer's citation marks, taken off a card (validator R-E2, Q-A4; t9 0115): Press renumbers the answer, not the
 * card, so a mark on a card would point at the wrong source. One strip for the model's cards and the rules' cards:
 * [1], [1, 2], [1–3], [ 1 ], [S1], [^1], 【1】. Code in backticks keeps its brackets (`items[1]`); a bare items[1] is
 * read as a mark, like "capital[1]": the two can't be told apart.
 */
object CiteMarks {
    private const val N = """(?:S|\^)?\d{1,3}"""
    private val MARK = Regex("""\s*(?:\[\s*$N(?:\s*[,–-]\s*$N)*\s*]|【\s*$N(?:\s*[,–-]\s*$N)*\s*】)""")
    private val CODE = Regex("`[^`\n]*`")

    fun strip(s: String): String {
        if (!s.contains('`')) return s.replace(MARK, "")
        val out = StringBuilder(); var at = 0
        for (m in CODE.findAll(s)) { out.append(s.substring(at, m.range.first).replace(MARK, "")).append(m.value); at = m.range.last + 1 }
        return out.append(s.substring(at).replace(MARK, "")).toString()
    }
}
