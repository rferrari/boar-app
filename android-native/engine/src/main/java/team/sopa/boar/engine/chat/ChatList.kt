package team.sopa.boar.engine.chat

import java.util.Calendar
import java.util.TimeZone

/** The menu's chat list (adam, Nov 12): grouped by day, searchable once there are many, deletes undoable for a moment. */
object ChatList {
    enum class Group { TODAY, YESTERDAY, EARLIER }

    /** A search box shows above this many saved chats. */
    const val SEARCH_FROM = 10
    /** How long "Chat deleted · Undo" waits before the chat is really deleted. */
    const val UNDO_MS = 5_000L

    fun groupOf(updatedAt: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): Group {
        fun day(t: Long) = Calendar.getInstance(zone).apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
        val today = Calendar.getInstance(zone).apply { timeInMillis = now }
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when (day(updatedAt)) {
            day(now) -> Group.TODAY
            day(yesterday.timeInMillis) -> Group.YESTERDAY
            else -> Group.EARLIER
        }
    }

    /** Every word of [query] in the title, case and accents ignored; a blank query keeps everything. */
    fun matches(query: String, title: String): Boolean {
        fun fold(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val words = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val t = fold(title)
        return words.all { it in t }
    }
}
