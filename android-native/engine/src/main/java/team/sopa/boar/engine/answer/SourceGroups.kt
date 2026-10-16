package team.sopa.boar.engine.answer

/**
 * The sources under an answer, one per article: passages of the same article become one entry with the best score,
 * listed best first. The answer's [n] citations are renumbered to match, so a number still points at its passage's
 * article.
 */
object SourceGroups {
    /** [items] in their new order, and each old 1-based number's new one. */
    data class Grouped<T>(val items: List<T>, val renumber: Map<Int, Int>)

    fun <T> group(passages: List<T>, key: (T) -> String, score: (T) -> Double?, merge: (List<T>) -> T): Grouped<T> {
        val byKey = LinkedHashMap<String, MutableList<IndexedValue<T>>>()
        passages.withIndex().forEach { byKey.getOrPut(key(it.value)) { mutableListOf() }.add(it) }
        fun best(g: List<IndexedValue<T>>) = g.maxOf { score(it.value) ?: Double.NEGATIVE_INFINITY }
        // Stable: equal scores keep the order the model read them in.
        val ordered = byKey.values.sortedByDescending(::best)
        val renumber = HashMap<Int, Int>()
        val items = ordered.mapIndexed { i, g ->
            g.forEach { renumber[it.index + 1] = i + 1 }
            merge(g.sortedByDescending { score(it.value) ?: Double.NEGATIVE_INFINITY }.map { it.value })
        }
        return Grouped(items, renumber)
    }

    private val URL = Regex("https?://[^\\s)]+")

    /**
     * Which article a passage belongs to: its page URL when the source names one, so the same article from two packs is
     * one source; else its document id; else its title.
     */
    fun articleKey(source: String?, docId: String?, title: String): String = source?.let { URL.find(it)?.value } ?: docId ?: title

    private val CITE = Regex("\\[(\\d{1,4})\\]")
    private val REPEATED = Regex("\\[(\\d{1,4})\\]((?:[ \\t]*\\[\\1\\])+)")

    /** "[3] … [1][2]" with 1 and 2 merged: "[1] … [2]". Numbers it doesn't know are left as written. */
    fun renumber(text: String, map: Map<Int, Int>): String {
        if (map.all { (k, v) -> k == v }) return text
        val out = text.replace(CITE) { m -> m.groupValues[1].toInt().let { map[it] }?.let { "[$it]" } ?: m.value }
        return out.replace(REPEATED) { "[${it.groupValues[1]}]" }
    }
}
