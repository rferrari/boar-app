package team.sopa.boar.engine.labs

/**
 * The chat header's plugins sheet (adam, Nov 11): the last [n] plugins used, newest first, among those switched on;
 * when fewer were used, the others switched on fill it in their list order.
 */
object RecentPlugins {
    /** Three (adam, Nov 13; it was five). */
    const val SHOWN = 3
    const val KEPT = 10

    fun pick(active: List<String>, recent: List<String>, n: Int = SHOWN): List<String> =
        (recent.filter { it in active } + active.filter { it !in recent }).distinct().take(n)

    /** [recent] with [id] moved to the front, at most [KEPT]. */
    fun used(recent: List<String>, id: String): List<String> = (listOf(id) + recent.filter { it != id }).take(KEPT)

    /** A search on the Plugins page: every word of [query] in the name or the line under it, accents and case ignored. */
    fun matches(query: String, vararg texts: String): Boolean {
        val words = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val hay = fold(texts.joinToString(" "))
        return words.all { it in hay }
    }

    private fun fold(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
