package team.sopa.boar.labs.printer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The research basket: answers picked from any chat ("＋ Basket" under an answer) for the Printer to compile.
 * A plain JSON file at files/labs/basket.json, in the order the Printer shows; it stays until it's cleared.
 */
object Basket {
    data class Source(val title: String, val source: String?)

    data class Item(
        /** The chat message id: the same answer can't go in twice. */
        val id: String,
        val chatTitle: String,
        val question: String,
        val text: String,
        val sources: List<Source>,
        val addedAt: Long,
    )

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items
    private var file: File? = null

    @Synchronized
    fun load(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "labs/basket.json").also { file = it }
        _items.value = runCatching { parse(JSONArray(f.readText())) }.getOrDefault(emptyList())
    }

    fun add(context: Context, item: Item) {
        load(context)
        if (_items.value.any { it.id == item.id }) return
        change { it + item }
    }

    fun remove(id: String) = change { list -> list.filter { it.id != id } }

    fun edit(id: String, text: String) = change { list -> list.map { if (it.id == id) it.copy(text = text) else it } }

    /** Moves an item up (-1) or down (+1). */
    fun move(id: String, by: Int) = change { list ->
        val i = list.indexOfFirst { it.id == id }
        val j = i + by
        if (i < 0 || j !in list.indices) list else list.toMutableList().apply { add(j, removeAt(i)) }
    }

    fun clear() = change { emptyList() }

    private fun change(f: (List<Item>) -> List<Item>) {
        _items.update(f)
        file?.let { out -> out.parentFile?.mkdirs(); out.writeText(toJson(_items.value).toString()) }
    }

    private fun toJson(items: List<Item>) = JSONArray().apply {
        items.forEach { i ->
            put(JSONObject().put("id", i.id).put("chatTitle", i.chatTitle).put("question", i.question).put("text", i.text).put("addedAt", i.addedAt)
                .put("sources", JSONArray().apply { i.sources.forEach { s -> put(JSONObject().put("title", s.title).put("source", s.source ?: JSONObject.NULL)) } }))
        }
    }

    private fun parse(a: JSONArray): List<Item> = (0 until a.length()).map { n ->
        val o = a.getJSONObject(n)
        val src = o.optJSONArray("sources") ?: JSONArray()
        Item(o.getString("id"), o.optString("chatTitle"), o.optString("question"), o.getString("text"),
            (0 until src.length()).map { k -> src.getJSONObject(k).let { Source(it.getString("title"), it.optString("source").takeIf { s -> s.isNotEmpty() && s != "null" }) } },
            o.optLong("addedAt"))
    }
}
