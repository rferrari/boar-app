package team.sopa.boar.engine.canvas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Boar Canvas's native cards (the Open Intelligent UI idea, offline): the chat model turns a finished answer into one
 * small JSON spec, and the app draws it with its own components. Five kinds, each small enough for a 4B model to write:
 *
 *     {"type":"table","title":"…","columns":["…","…"],"rows":[["…","…"]]}
 *     {"type":"bars","title":"…","unit":"…","items":[{"label":"…","value":12.5}]}
 *     {"type":"steps","title":"…","steps":["…","…"]}
 *     {"type":"compare","title":"…","items":[{"name":"…","points":["…","…"]}]}
 *     {"type":"facts","title":"…","facts":[{"label":"…","value":"…"}]}
 *
 * The answer stays the source of truth: a bar chart is drawn only when every value appears in the answer's own text,
 * so a chart never shows a number BOAR didn't write.
 */
sealed interface Card {
    val title: String

    data class Table(override val title: String, val columns: List<String>, val rows: List<List<String>>) : Card
    data class Bars(override val title: String, val unit: String?, val items: List<Pair<String, Double>>) : Card
    data class Steps(override val title: String, val steps: List<String>) : Card
    data class Compare(override val title: String, val items: List<Pair<String, List<String>>>) : Card
    data class Facts(override val title: String, val facts: List<Pair<String, String>>) : Card
}

object CardSpec {
    sealed interface Parsed {
        data class Ok(val card: Card) : Parsed
        data class Rejected(val reason: Reason) : Parsed
    }

    /** NOTHING: the model said a card wouldn't help ({"type":"none"}). */
    enum class Reason { NO_JSON, UNKNOWN_TYPE, EMPTY, INVENTED_NUMBERS, NOTHING }

    private const val MAX_ROWS = 20
    private const val MAX_COLUMNS = 6
    private const val MAX_ITEMS = 12
    private const val MAX_TEXT = 160

    /** The first JSON object in the model's reply (it may wrap it in a ```json fence or a sentence), checked against [answer]. */
    fun parse(raw: String, answer: String): Parsed {
        val o = firstObject(raw.substringAfter("</think>")) ?: return Parsed.Rejected(Reason.NO_JSON)
        val title = o.text("title") ?: ""
        val card: Card = when (o.text("type")?.lowercase()) {
            "none" -> return Parsed.Rejected(Reason.NOTHING)
            "table" -> {
                val columns = o.strings("columns").take(MAX_COLUMNS)
                val rows = (o["rows"] as? JsonArray).orEmpty().mapNotNull { r -> (r as? JsonArray)?.map { cell(it) } }
                    .filter { r -> r.any { it.isNotBlank() } }.take(MAX_ROWS)
                    .map { r -> (r + List(columns.size) { "" }).take(columns.size) }
                if (columns.isEmpty() || rows.isEmpty()) return Parsed.Rejected(Reason.EMPTY)
                Card.Table(title, columns, rows)
            }
            "bars" -> {
                val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { i ->
                    val v = (i["value"] as? JsonPrimitive)?.let { it.doubleOrNull ?: number(it.contentOrNull ?: "") } ?: return@mapNotNull null
                    (i.text("label") ?: return@mapNotNull null) to v
                }.filter { it.second.isFinite() }.take(MAX_ITEMS)
                if (items.size < 2) return Parsed.Rejected(Reason.EMPTY)
                val written = numbersIn(answer)
                if (items.any { (_, v) -> written.none { kotlin.math.abs(it - v) <= 1e-9 * maxOf(1.0, kotlin.math.abs(v)) } }) return Parsed.Rejected(Reason.INVENTED_NUMBERS)
                Card.Bars(title, o.text("unit")?.takeIf { it.isNotBlank() }, items)
            }
            "steps" -> o.strings("steps").take(MAX_ITEMS).takeIf { it.size >= 2 }?.let { Card.Steps(title, it) } ?: return Parsed.Rejected(Reason.EMPTY)
            "compare" -> {
                val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { i ->
                    val name = i.text("name") ?: return@mapNotNull null
                    name to i.strings("points").take(MAX_ITEMS)
                }.filter { it.second.isNotEmpty() }.take(4)
                if (items.size < 2) return Parsed.Rejected(Reason.EMPTY)
                Card.Compare(title, items)
            }
            "facts" -> {
                val facts = (o["facts"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { f ->
                    (f.text("label") ?: return@mapNotNull null) to (f.text("value") ?: return@mapNotNull null)
                }.take(MAX_ITEMS)
                if (facts.isEmpty()) return Parsed.Rejected(Reason.EMPTY)
                Card.Facts(title, facts)
            }
            else -> return Parsed.Rejected(Reason.UNKNOWN_TYPE)
        }
        return Parsed.Ok(card)
    }

    /** Every number written in [text]: "1,200", "3.5", "−4", "12%" → 1200, 3.5, -4, 12. */
    fun numbersIn(text: String): List<Double> =
        Regex("[-−]?\\d[\\d,]*(?:\\.\\d+)?").findAll(text).mapNotNull { number(it.value) }.toList()

    private fun number(s: String): Double? = s.replace("−", "-").replace(",", "").trim().toDoubleOrNull()

    private fun firstObject(s: String): JsonObject? {
        var start = s.indexOf('{')
        while (start >= 0) {
            // The shortest balanced { … } from here that parses; braces inside strings are rare enough in a card to retry.
            var depth = 0
            for (i in start until s.length) {
                when (s[i]) { '{' -> depth++; '}' -> if (--depth == 0) {
                    runCatching { Json.parseToJsonElement(s.substring(start, i + 1)) as? JsonObject }.getOrNull()?.let { return it }
                    break
                } }
            }
            start = s.indexOf('{', start + 1)
        }
        return null
    }

    private fun cell(e: JsonElement): String = ((e as? JsonPrimitive)?.contentOrNull ?: "").trim().take(MAX_TEXT)
    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.take(MAX_TEXT)
    private fun JsonObject.strings(k: String): List<String> = (this[k] as? JsonArray).orEmpty().map(::cell).filter { it.isNotEmpty() }
}
