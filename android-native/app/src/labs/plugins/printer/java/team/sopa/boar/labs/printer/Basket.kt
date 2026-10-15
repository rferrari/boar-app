package team.sopa.boar.labs.printer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        val model: String? = null,
        val tokPerSec: Double? = null,
    )

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items
    private var file: android.util.AtomicFile? = null
    /** Read successfully (or nothing saved yet). Until then nothing is written, so a file that failed to read is never replaced by an empty basket. */
    private var loaded = false
    private val lock = Any()

    fun load(context: Context) = synchronized(lock) {
        if (loaded) return@synchronized
        val f = android.util.AtomicFile(File(context.filesDir, "labs/basket.json")).also { file = it }
        if (!f.baseFile.exists()) { _items.value = emptyList(); loaded = true; return@synchronized }
        runCatching { parse(JSONArray(String(f.readFully(), Charsets.UTF_8))) }
            .onSuccess { _items.value = it; loaded = true }
            .onFailure { android.util.Log.w("BoarLabs", "basket unreadable, kept as is; will retry", it) }
    }

    fun add(context: Context, item: Item) {
        load(context)
        // The duplicate check and the write under one lock: two adds of the same answer can't both get in.
        change { list -> if (list.any { it.id == item.id }) list else list + item }
    }

    fun remove(id: String) = change { list -> list.filter { it.id != id } }

    fun edit(id: String, text: String) = change { list -> list.map { if (it.id == id) it.copy(text = text) else it } }

    /** The heading printed above an answer (its question, at first). */
    fun editHeading(id: String, heading: String) = change { list -> list.map { if (it.id == id) it.copy(question = heading) else it } }

    /** Moves an item up (-1) or down (+1). */
    fun move(id: String, by: Int) = change { list ->
        val i = list.indexOfFirst { it.id == id }
        val j = i + by
        if (i < 0 || j !in list.indices) list else list.toMutableList().apply { add(j, removeAt(i)) }
    }

    fun clear() = change { emptyList() }

    /** Erased with the chats: the file goes even if it couldn't be read, and the basket starts empty. */
    fun eraseAll(context: Context) = synchronized(lock) {
        val f = android.util.AtomicFile(File(context.filesDir, "labs/basket.json")).also { file = it }
        f.delete()
        _items.value = emptyList()
        loaded = true
    }

    /** One change at a time, state and file together; written whole and swapped in (AtomicFile), so a crash can't leave half a basket. */
    private fun change(f: (List<Item>) -> List<Item>) = synchronized(lock) {
        val out = file ?: return@synchronized
        if (!loaded) return@synchronized
        val next = f(_items.value)
        if (next == _items.value) return@synchronized
        out.baseFile.parentFile?.mkdirs()
        val stream = out.startWrite()
        try {
            stream.write(toJson(next).toString().toByteArray(Charsets.UTF_8))
            out.finishWrite(stream)
            _items.value = next
        } catch (e: java.io.IOException) {
            out.failWrite(stream)
            android.util.Log.w("BoarLabs", "basket not saved", e)
        }
    }

    private fun toJson(items: List<Item>) = JSONArray().apply {
        items.forEach { i ->
            put(JSONObject().put("id", i.id).put("chatTitle", i.chatTitle).put("question", i.question).put("text", i.text).put("addedAt", i.addedAt).put("model", i.model ?: JSONObject.NULL).put("tokPerSec", i.tokPerSec ?: JSONObject.NULL)
                .put("sources", JSONArray().apply { i.sources.forEach { s -> put(JSONObject().put("title", s.title).put("source", s.source ?: JSONObject.NULL)) } }))
        }
    }

    private fun parse(a: JSONArray): List<Item> = (0 until a.length()).map { n ->
        val o = a.getJSONObject(n)
        val src = o.optJSONArray("sources") ?: JSONArray()
        Item(o.getString("id"), o.optString("chatTitle"), o.optString("question"), o.getString("text"),
            (0 until src.length()).map { k -> src.getJSONObject(k).let { Source(it.getString("title"), it.optString("source").takeIf { s -> s.isNotEmpty() && s != "null" }) } },
            o.optLong("addedAt"), o.optString("model").takeIf { it.isNotEmpty() && it != "null" }, o.optDouble("tokPerSec").takeIf { !it.isNaN() })
    }
}
