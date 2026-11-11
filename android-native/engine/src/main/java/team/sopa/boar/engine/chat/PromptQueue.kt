package team.sopa.boar.engine.chat

/**
 * Questions sent while BOAR is answering (adam, Nov 10: "let it send, adding to queue"). They wait in order and run one
 * at a time after the answer in progress, each as the chat's next turn; never two answers at once. At most [limit] wait.
 * In memory only: the app being closed drops them (the chat says so on the next start). Not thread-safe: the chat's
 * main thread owns it.
 */
class PromptQueue(val limit: Int = LIMIT) {
    data class Item(val id: Long, val text: String)

    private val items = ArrayList<Item>()
    private var nextId = 1L

    val waiting: List<Item> get() = items.toList()
    val size: Int get() = items.size
    fun isEmpty() = items.isEmpty()

    /** Adds [text] at the end; false when [limit] questions already wait (or it's blank). */
    fun add(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || items.size >= limit) return false
        items += Item(nextId++, t)
        return true
    }

    /** The next question to run, taken off the queue; null when none waits. */
    fun next(): Item? = if (items.isEmpty()) null else items.removeAt(0)

    fun remove(id: Long): Boolean = items.removeAll { it.id == id }

    /** Puts a question taken by [next] back at the front, as it was. */
    fun pushFront(item: Item) { items.add(0, item) }

    fun clear() = items.clear()

    /** The waiting questions as one (PromptMerge), first in line; false with fewer than two. */
    fun merge(): Boolean {
        if (items.size < 2) return false
        val one = Item(nextId++, PromptMerge.of(items.map { it.text }))
        items.clear(); items += one
        return true
    }

    companion object { const val LIMIT = 5 }
}
