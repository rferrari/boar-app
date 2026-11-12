package team.sopa.boar.engine.chat

/**
 * Questions sent while BOAR is answering (adam, Nov 10: "let it send, adding to queue"). They wait in order and run one
 * at a time after the answer in progress, each as the chat's next turn; never two answers at once. At most [limit] wait.
 * In memory only: the app being closed drops them (the chat says so on the next start). Thread-safe: the chat runs it
 * on Main, but a model switch's stopAll clears it from another thread (validator Q-1b).
 */
class PromptQueue(val limit: Int = LIMIT) {
    data class Item(val id: Long, val text: String)

    private val items = ArrayList<Item>()
    private var nextId = 1L

    val waiting: List<Item> @Synchronized get() = items.toList()
    val size: Int @Synchronized get() = items.size
    @Synchronized fun isEmpty() = items.isEmpty()

    /** Adds [text] at the end; false when [limit] questions already wait (or it's blank). */
    @Synchronized fun add(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || items.size >= limit) return false
        items += Item(nextId++, t)
        return true
    }

    /** The next question to run, taken off the queue; null when none waits. */
    @Synchronized fun next(): Item? = if (items.isEmpty()) null else items.removeAt(0)

    @Synchronized fun remove(id: Long): Boolean = items.removeAll { it.id == id }

    /** Puts a question taken by [next] back at the front, as it was. */
    @Synchronized fun pushFront(item: Item) { items.add(0, item) }

    @Synchronized fun clear() = items.clear()

    /** The waiting questions as one (PromptMerge), first in line; false with fewer than two. */
    @Synchronized fun merge(): Boolean {
        if (items.size < 2) return false
        val one = Item(nextId++, PromptMerge.of(items.map { it.text }))
        items.clear(); items += one
        return true
    }

    companion object { const val LIMIT = 5 }
}
