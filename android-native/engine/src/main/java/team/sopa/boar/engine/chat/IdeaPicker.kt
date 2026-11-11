package team.sopa.boar.engine.chat

/**
 * The empty chat's three ideas (adam, Nov 12: "the same ones every time"): never the set shown last (kept across
 * launches by the caller), and ideas whose source is a knowledge pack the user downloaded come up twice as often.
 */
object IdeaPicker {
    fun pick(pool: List<String>, fromPacks: Set<String>, lastShown: Set<String>, n: Int, random: java.util.Random = java.util.Random()): List<String> {
        val fresh = pool.filter { it !in lastShown }
        // Too few left without the last set: fall back to the whole pool, still shuffled.
        val candidates = (if (fresh.size >= n) fresh else pool).toMutableList()
        val out = ArrayList<String>(n)
        while (out.size < n && candidates.isNotEmpty()) {
            val weights = candidates.map { if (it in fromPacks) 2.0 else 1.0 }
            var r = random.nextDouble() * weights.sum()
            var i = 0
            while (i < candidates.size - 1 && r >= weights[i]) { r -= weights[i]; i++ }
            out += candidates.removeAt(i)
        }
        return out
    }
}
