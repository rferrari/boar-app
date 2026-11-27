package team.sopa.boar.engine.labs

/**
 * How Press lays a text out in paragraphs (t9 0735, adam 27 Nov: a PDF page read thin). A blank line between paragraphs
 * used to print as a whole empty line; now each paragraph is laid out on its own with a small gap (Paper.PARA_GAP) after
 * it. A list whose items have blank lines between them, as models often write, closes up into one block.
 */
object PressSpacing {
    private val LIST_ITEM = Regex("^\\s*(?:[-*•]|\\d+[.)])\\s+")

    /** [text]'s paragraphs, trimmed, none empty and none with an empty line in it; list items run together. */
    fun paragraphs(text: String): List<String> {
        val groups = text.split(Regex("\\n\\s*\\n")).map { it.trim('\n', ' ', '\t', '\r') }.filter { it.isNotBlank() }
        val out = ArrayList<String>()
        for (g in groups) {
            val prev = out.lastOrNull()
            if (prev != null && isList(prev) && isList(g)) out[out.size - 1] = prev + "\n" + g else out += g
        }
        return out.map { it.trim() }
    }

    /** Every line of [block] is a list item ("- ", "• ", "1. ", "2) "). */
    private fun isList(block: String): Boolean = block.lines().filter { it.isNotBlank() }.let { ls -> ls.isNotEmpty() && ls.all { LIST_ITEM.containsMatchIn(it) } }
}
