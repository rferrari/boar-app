package team.sopa.boar.engine.canvas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import team.sopa.boar.engine.canvas.CardRules.Shape

/**
 * The card shown under one answer (Cards, adam's interview, Nov 14; t9 0207 items 3 and 4): what was drawn, in which
 * shape, from the rules ([CardRules]) or from the model ([Visual], "made by the model · checked against the answer").
 * Pure data, kept in the answer's own message meta ("card", with the checklist's ticks beside it in "cardTicks", adam: "ticks
 * kept with the chat"), and copied into Press's Answer block when the answer goes to Press with its card shown.
 */
data class AnswerCard(
    val shape: Shape,
    val title: String,
    val body: Body,
    /** Made by the model (the "slower" row), checked against the answer; false: found by the rules in the answer's text. */
    val byModel: Boolean = false,
    /** What the model's card had that no text holds, taken out (Visual's "left out" line). */
    val leftOut: List<String> = emptyList(),
    /** The checklist's ticked steps, by index (only for [Body.Steps]). */
    val ticks: Set<Int> = emptySet(),
) {
    sealed interface Body {
        /** Labelled numbers of one unit: bars, a table, a pie or a line. Each [CardRules.Point.written] as the answer writes it. */
        data class Series(val unit: String, val points: List<CardRules.Point>) : Body
        data class Table(val header: List<String>, val rows: List<List<String>>) : Body
        data class Steps(val items: List<String>) : Body
        data class Facts(val pairs: List<Pair<String, String>>) : Body
        data class Compare(val sides: List<Pair<String, List<String>>>) : Body
    }

    /** One pill of the card's switch: the [card]-th card the rules found, in [shape]. */
    data class Choice(val card: Int, val shape: Shape)

    /** What the rules found, for the plugins sheet's second line ("A chart of 3 numbers"); the app words it. */
    enum class Found { CHART, TABLE, STEPS, FACTS, COMPARE }

    companion object {
        /**
         * The one switch under the card (adam, Nov 14: "the best one, with a switch to the other shapes that fit"): every
         * shape of every card the rules found, best card first. A chart brings its own shapes (bars, table, and a pie or a
         * line only when CardRules allows them).
         */
        fun choices(cards: List<CardRules.Card>): List<Choice> = cards.flatMapIndexed { i, c -> c.shapes.map { Choice(i, it) } }

        /** [card] in [shape] (a shape it doesn't have falls back to its first). */
        fun of(card: CardRules.Card, shape: Shape, title: String): AnswerCard {
            val s = if (shape in card.shapes) shape else card.shapes.first()
            val body: Body = when (card) {
                is CardRules.Card.Chart -> Body.Series(card.unit, card.points)
                is CardRules.Card.Table -> Body.Table(card.header, card.rows)
                is CardRules.Card.Steps -> Body.Steps(card.items)
                is CardRules.Card.Facts -> Body.Facts(card.pairs)
                is CardRules.Card.Compare -> Body.Compare(card.sides)
            }
            return AnswerCard(s, title, body)
        }

        /** Which kind a card is and how many items it holds, for the sheet's line. */
        fun found(card: CardRules.Card): Pair<Found, Int> = when (card) {
            is CardRules.Card.Chart -> Found.CHART to card.points.size
            is CardRules.Card.Table -> Found.TABLE to card.rows.size
            is CardRules.Card.Steps -> Found.STEPS to card.items.size
            is CardRules.Card.Facts -> Found.FACTS to card.pairs.size
            is CardRules.Card.Compare -> Found.COMPARE to card.sides.size
        }

        /**
         * A rule card's title: the things compared ("Solar · Wind"), else the answer's first source, else the question,
         * short. The rules never invent a title, so it comes from what the chat already shows.
         */
        fun titleFor(card: CardRules.Card, question: String, sources: List<String>): String {
            if (card is CardRules.Card.Compare) return card.sides.joinToString(" · ") { it.first }
            sources.firstOrNull { it.isNotBlank() }?.let { return it.trim().take(80) }
            val q = question.trim().trimEnd('?', '.', '!').trim()
            return if (q.length <= 60) q else q.take(57).trimEnd() + "…"
        }

        /** The model's visual (Visual.parse, already checked against the answer) as a card. */
        fun ofVisual(v: Visual, leftOut: List<String>, fallbackTitle: String): AnswerCard {
            val title = v.title.ifBlank { fallbackTitle }
            return when (v) {
                is Visual.Series -> AnswerCard(
                    when (v.kind) { Visual.SeriesKind.BARS -> Shape.BARS; Visual.SeriesKind.LINE -> Shape.LINE; Visual.SeriesKind.SHARE -> Shape.PIE },
                    title, Body.Series(v.unit.orEmpty(), v.items.map { (l, x) -> CardRules.Point(l, (VisualScale.format(x) + " " + v.unit.orEmpty()).trim(), x) }),
                    byModel = true, leftOut = leftOut)
                is Visual.Table -> AnswerCard(Shape.TABLE, title, Body.Table(v.columns, v.rows), true, leftOut)
                is Visual.Steps -> AnswerCard(Shape.CHECKLIST, title, Body.Steps(v.steps), true, leftOut)
                is Visual.Facts -> AnswerCard(Shape.FACTS, title, Body.Facts(v.facts), true, leftOut)
                is Visual.Compare -> AnswerCard(Shape.COMPARE, title, Body.Compare(v.items), true, leftOut)
            }
        }

        /** A pie's parts as whole percents adding up to 100 (VisualScale.percents). */
        fun percents(s: Body.Series): List<Int> = VisualScale.percents(s.points.map { it.value })

        /**
         * The card as a table, for a pie or a line in the PDF (t9 0207: "a pie or a line prints as its table") and for the
         * Markdown: header and rows. A series has two columns, its label and its value as written.
         */
        fun asTable(card: AnswerCard, nameLabel: String = "", valueLabel: String = ""): Pair<List<String>, List<List<String>>>? = when (val b = card.body) {
            is Body.Series -> listOf(nameLabel, valueLabel.ifBlank { b.unit }) to b.points.map { listOf(it.label, it.written) }
            is Body.Table -> b.header to b.rows
            else -> null
        }

        /**
         * The card in Markdown (Press's Save as Markdown, t9 0207 item 4): a table for a series or a table, a numbered list
         * for steps, "- Label: value" lines for facts, a short list under each name for a comparison. Its title first, bold.
         */
        fun markdown(card: AnswerCard, nameLabel: String = "", valueLabel: String = ""): String = buildString {
            if (card.title.isNotBlank()) append("**").append(cell(card.title)).append("**\n\n")
            when (val b = card.body) {
                is Body.Series, is Body.Table -> {
                    val (header, rows) = asTable(card, nameLabel, valueLabel)!!
                    val n = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
                    fun row(r: List<String>) = (r + List(n - r.size) { "" }).joinToString(" | ", "| ", " |") { cell(it) }
                    append(row(header)).append('\n')
                    append((1..n).joinToString("|", "|", "|") { " --- " }).append('\n')
                    rows.forEach { append(row(it)).append('\n') }
                }
                is Body.Steps -> b.items.forEachIndexed { i, s -> append(i + 1).append(". ").append(s.trim()).append('\n') }
                is Body.Facts -> b.pairs.forEach { (l, v) -> append("- ").append(l.trim()).append(": ").append(v.trim()).append('\n') }
                is Body.Compare -> b.sides.forEach { (name, points) ->
                    append("**").append(name.trim()).append("**\n")
                    points.forEach { append("- ").append(it.trim()).append('\n') }
                }
            }
        }.trimEnd()

        private fun cell(s: String) = s.replace('\n', ' ').replace("|", "\\|").trim()

        // ---- JSON ----

        fun toJson(card: AnswerCard, withTicks: Boolean = false): JsonObject {
            val m = linkedMapOf<String, JsonElement>(
                "shape" to JsonPrimitive(card.shape.name), "title" to JsonPrimitive(card.title), "byModel" to JsonPrimitive(card.byModel),
            )
            if (card.leftOut.isNotEmpty()) m["leftOut"] = JsonArray(card.leftOut.map(::JsonPrimitive))
            when (val b = card.body) {
                is Body.Series -> {
                    m["kind"] = JsonPrimitive("series"); m["unit"] = JsonPrimitive(b.unit)
                    m["points"] = JsonArray(b.points.map { JsonObject(linkedMapOf("label" to JsonPrimitive(it.label), "written" to JsonPrimitive(it.written), "value" to JsonPrimitive(it.value))) })
                }
                is Body.Table -> {
                    m["kind"] = JsonPrimitive("table"); m["header"] = JsonArray(b.header.map(::JsonPrimitive))
                    m["rows"] = JsonArray(b.rows.map { r -> JsonArray(r.map(::JsonPrimitive)) })
                }
                is Body.Steps -> { m["kind"] = JsonPrimitive("steps"); m["steps"] = JsonArray(b.items.map(::JsonPrimitive)) }
                is Body.Facts -> {
                    m["kind"] = JsonPrimitive("facts")
                    m["facts"] = JsonArray(b.pairs.map { (l, v) -> JsonObject(linkedMapOf("label" to JsonPrimitive(l), "value" to JsonPrimitive(v))) })
                }
                is Body.Compare -> {
                    m["kind"] = JsonPrimitive("compare")
                    m["sides"] = JsonArray(b.sides.map { (n, ps) -> JsonObject(linkedMapOf("name" to JsonPrimitive(n), "points" to JsonArray(ps.map(::JsonPrimitive)))) })
                }
            }
            if (withTicks && card.ticks.isNotEmpty()) m["ticks"] = JsonArray(card.ticks.sorted().map(::JsonPrimitive))
            return JsonObject(m)
        }

        /** A card written by [toJson]; null for anything else (a newer or damaged one is simply not drawn). */
        fun fromJson(e: JsonElement?): AnswerCard? = runCatching {
            val o = e as? JsonObject ?: return null
            val shape = Shape.valueOf(o.s("shape") ?: return null)
            val body: Body = when (o.s("kind")) {
                "series" -> Body.Series(o.s("unit").orEmpty(), o.arr("points").mapNotNull { p ->
                    val q = p as? JsonObject ?: return@mapNotNull null
                    CardRules.Point(q.s("label") ?: return@mapNotNull null, q.s("written").orEmpty(), (q["value"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null)
                }.takeIf { it.isNotEmpty() } ?: return null)
                "table" -> Body.Table(o.strings("header"), o.arr("rows").mapNotNull { r -> (r as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() } })
                "steps" -> Body.Steps(o.strings("steps").takeIf { it.isNotEmpty() } ?: return null)
                "facts" -> Body.Facts(o.arr("facts").mapNotNull { f -> (f as? JsonObject)?.let { (it.s("label") ?: return@mapNotNull null) to it.s("value").orEmpty() } })
                "compare" -> Body.Compare(o.arr("sides").mapNotNull { f -> (f as? JsonObject)?.let { (it.s("name") ?: return@mapNotNull null) to it.strings("points") } })
                else -> return null
            }
            AnswerCard(shape, o.s("title").orEmpty(), body, (o["byModel"] as? JsonPrimitive)?.booleanOrNull ?: false, o.strings("leftOut"), ticksOf(o["ticks"]))
        }.getOrNull()

        private fun ticksOf(e: JsonElement?): Set<Int> = (e as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.intOrNull }.filter { it >= 0 }.toSet()

        private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun JsonObject.arr(k: String): List<JsonElement> = (this[k] as? JsonArray).orEmpty()
        private fun JsonObject.strings(k: String): List<String> = arr(k).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

        // ---- The message's meta ----

        const val META_CARD = "card"
        const val META_TICKS = "cardTicks"

        private fun metaObject(meta: String?): JsonObject? =
            if (meta.isNullOrBlank()) JsonObject(emptyMap()) else runCatching { Json.parseToJsonElement(meta) as? JsonObject }.getOrNull()

        /** The card kept with an answer, with its ticks; null when none is shown. */
        fun fromMeta(meta: String?): AnswerCard? {
            val o = metaObject(meta) ?: return null
            return fromJson(o[META_CARD])?.copy(ticks = ticksOf(o[META_TICKS]))
        }

        /** The ticks kept with an answer, shown card or not (switching to the amounts and back keeps them). */
        fun ticksFromMeta(meta: String?): Set<Int> = metaObject(meta)?.let { ticksOf(it[META_TICKS]) } ?: emptySet()

        /**
         * [meta] with [card] shown, every other key (sources, receipt, staged…) kept as it is; null [card] removes it and
         * its ticks (✕). Null when [meta] can't be read: then nothing is written over it.
         */
        fun withCard(meta: String?, card: AnswerCard?): String? {
            val o = metaObject(meta) ?: return null
            val m = LinkedHashMap(o)
            if (card == null) { m.remove(META_CARD); m.remove(META_TICKS) } else m[META_CARD] = toJson(card)
            return JsonObject(m).toString()
        }

        /** [meta] with the checklist's [ticks]; null when [meta] can't be read. */
        fun withTicks(meta: String?, ticks: Set<Int>): String? {
            val o = metaObject(meta) ?: return null
            val m = LinkedHashMap(o)
            if (ticks.isEmpty()) m.remove(META_TICKS) else m[META_TICKS] = JsonArray(ticks.sorted().map(::JsonPrimitive))
            return JsonObject(m).toString()
        }
    }
}
