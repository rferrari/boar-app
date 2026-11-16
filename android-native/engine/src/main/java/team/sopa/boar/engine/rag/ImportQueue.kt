package team.sopa.boar.engine.rag

/**
 * Collections waiting to be added in Knowledge while one is being added (adam, Nov 12): in order, at most [limit], each
 * removable. The runner takes the next one whether the last one worked or failed. In memory only: closing BOAR drops
 * them (the screen says so). Not thread-safe: the screen's view model owns it on the main thread.
 */
class ImportQueue<T>(val limit: Int = LIMIT) {
    data class Item<T>(val id: Long, val name: String, val files: Int, val payload: T)

    private val items = ArrayList<Item<T>>()
    private var nextId = 1L

    val waiting: List<Item<T>> get() = items.toList()
    fun isEmpty() = items.isEmpty()

    fun add(name: String, files: Int, payload: T): Boolean {
        if (files <= 0 || items.size >= limit) return false
        items += Item(nextId++, name, files, payload)
        return true
    }

    fun next(): Item<T>? = if (items.isEmpty()) null else items.removeAt(0)
    fun remove(id: Long): Boolean = items.removeAll { it.id == id }

    companion object { const val LIMIT = 5 }
}
