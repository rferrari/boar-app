package team.sopa.boar.engine.vault

/**
 * The avatars Settings → Display & theme shows (adam, 23 Nov: Boar Avatars merged into it, "limit it to some avatars
 * and on the last box we open the page", as Designer's strip ends on "All N"): the plain BOAR first, the one being worn,
 * then the latest unlocked, [max] in all (10 since adam, 27 Nov; the row scrolls). null stands for the plain BOAR. Pure.
 */
object AvatarStrip {
    const val MAX = 10

    /** [unlocked]: id → when it was unlocked; [known]: the ids that have art; [equipped]: the one worn, null for BOAR. */
    fun pick(unlocked: Map<String, Long>, known: Collection<String>, equipped: String?, max: Int = MAX): List<String?> {
        val have = unlocked.filterKeys { it in known }
        val worn = equipped?.takeIf { it in have }
        val latest = have.entries.sortedByDescending { it.value }.map { it.key }.filter { it != worn }
        return (listOf<String?>(null) + listOfNotNull(worn) + latest).take(max.coerceAtLeast(1))
    }
}
