package team.sopa.boar.engine.canvas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.text.Normalizer

/**
 * Boar Canvas's merged visual (cards and widgets in one, adam, Nov 10): the model reads a finished answer and returns ONE
 * JSON object, the data and a suggested type; the app checks every number and label against the answer and draws it
 * with its own components. The model never computes: percentages, totals and axes are the app's ([VisualScale]).
 *
 *     {"type":"table","title":"…","columns":["…","…"],"rows":[["…","…"]]}
 *     {"type":"bars"|"line"|"share","title":"…","unit":"…","items":[{"label":"…","value":12.5}]}
 *     {"type":"steps","title":"…","steps":["…","…"]}
 *     {"type":"compare","title":"…","items":[{"name":"…","points":["…","…"]}]}
 *     {"type":"facts","title":"…","facts":[{"label":"…","value":"…"}]}
 *     {"type":"none"}
 *
 * A superset of [CardSpec]'s cards, so a card saved before reads the same. "pie" is read as "share". The interactive
 * kinds (timeline, slider, quiz, converter) are reserved for after the eval: read and refused.
 */
sealed interface Visual {
    val title: String

    data class Table(override val title: String, val columns: List<String>, val rows: List<List<String>>) : Visual
    /** One list of labelled numbers, drawn as [kind]; the same data can be shown as any kind [VisualScale.kinds] allows. */
    data class Series(override val title: String, val kind: SeriesKind, val unit: String?, val items: List<Pair<String, Double>>) : Visual
    data class Steps(override val title: String, val steps: List<String>) : Visual
    data class Compare(override val title: String, val items: List<Pair<String, List<String>>>) : Visual
    data class Facts(override val title: String, val facts: List<Pair<String, String>>) : Visual

    enum class SeriesKind { BARS, LINE, SHARE }

    sealed interface Parsed {
        /** [leftOut]: what was taken out because none of the texts has it (a number, a name), shown under the visual. */
        data class Ok(val visual: Visual, val leftOut: List<String> = emptyList()) : Parsed
        /** [detail]: what failed, for the eval and the log (a number, a label); never shown as the answer. */
        data class Rejected(val reason: Reason, val detail: String? = null) : Parsed
    }

    /**
     * NOTHING: the model said a visual wouldn't help. RESERVED: an interactive kind not built yet. INVENTED_NUMBERS and
     * UNGROUNDED_LABELS: something not in the answer (or the user's own message). BAD_SHARE: parts that can't make a whole.
     */
    enum class Reason { NO_JSON, UNKNOWN_TYPE, RESERVED, EMPTY, INVENTED_NUMBERS, UNGROUNDED_LABELS, UNPAIRED, BAD_SHARE, NOTHING }

    companion object {
        const val MAX_ROWS = 20
        const val MAX_COLUMNS = 6
        const val MAX_ITEMS = 12
        const val MAX_SHARE_PARTS = 8
        const val MAX_TEXT = 160
        val RESERVED = setOf("timeline", "slider", "quiz", "converter")

        /**
         * The first JSON object in the model's [raw] reply (after any reasoning, inside a fence or a sentence), checked
         * against [answer] and, for numbers the user typed, [userText].
         */
        fun parse(raw: String, answer: String, userText: String = ""): Parsed {
            val o = firstObject(raw.substringAfterLast("</think>")) ?: return Parsed.Rejected(Reason.NO_JSON)
            val title = o.text("title") ?: ""
            val type = o.text("type")?.lowercase()?.trim() ?: return Parsed.Rejected(Reason.UNKNOWN_TYPE)
            val visual: Visual = when (type) {
                "none" -> return Parsed.Rejected(Reason.NOTHING)
                in RESERVED -> return Parsed.Rejected(Reason.RESERVED, type)
                "table" -> {
                    val columns = o.strings("columns").take(MAX_COLUMNS)
                    val rows = (o["rows"] as? JsonArray).orEmpty().mapNotNull { r -> (r as? JsonArray)?.map { cell(it) } }
                        .filter { r -> r.any { it.isNotBlank() } }.take(MAX_ROWS)
                        .map { r -> (r + List(columns.size) { "" }).take(columns.size) }
                    if (columns.isEmpty() || rows.isEmpty()) return Parsed.Rejected(Reason.EMPTY)
                    Table(title, columns, rows)
                }
                "bars", "bar", "line", "share", "pie" -> {
                    val kind = when (type) { "line" -> SeriesKind.LINE; "share", "pie" -> SeriesKind.SHARE; else -> SeriesKind.BARS }
                    val items = (o["items"] as? JsonArray ?: o["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { i ->
                        // A value written as text ("3,5") is read by its form (VisualCheck.readings), never the English way first.
                        val v = (i["value"] as? JsonPrimitive)?.let { if (!it.isString) it.doubleOrNull else VisualCheck.valuesOf(it.content).singleOrNull() }
                            ?: return@mapNotNull null
                        (i.text("label") ?: return@mapNotNull null) to v
                    }.filter { it.second.isFinite() }
                    // More parts than a pie can show is refused, not cut and drawn as if the rest were the whole (V-7).
                    if (kind == SeriesKind.SHARE && items.size > MAX_SHARE_PARTS) return Parsed.Rejected(Reason.BAD_SHARE, "${items.size}")
                    val kept = items.take(MAX_ITEMS)
                    if (kept.size < 2) return Parsed.Rejected(Reason.EMPTY)
                    if (kind == SeriesKind.SHARE && !VisualScale.canShare(kept.map { it.second })) return Parsed.Rejected(Reason.BAD_SHARE)
                    Series(title, kind, o.text("unit")?.takeIf { it.isNotBlank() }, kept)
                }
                "steps" -> o.strings("steps").take(MAX_ITEMS).takeIf { it.size >= 2 }?.let { Steps(title, it) } ?: return Parsed.Rejected(Reason.EMPTY)
                "compare" -> {
                    val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { i ->
                        val name = i.text("name") ?: return@mapNotNull null
                        name to i.strings("points").take(MAX_ITEMS)
                    }.filter { it.second.isNotEmpty() }.take(4)
                    if (items.size < 2) return Parsed.Rejected(Reason.EMPTY)
                    Compare(title, items)
                }
                "facts" -> {
                    val facts = (o["facts"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { f ->
                        (f.text("label") ?: return@mapNotNull null) to (f.text("value") ?: return@mapNotNull null)
                    }.take(MAX_ITEMS)
                    if (facts.isEmpty()) return Parsed.Rejected(Reason.EMPTY)
                    Facts(title, facts)
                }
                else -> return Parsed.Rejected(Reason.UNKNOWN_TYPE, type)
            }
            // What isn't in the texts comes out (an item, a row or column, a line); refused only when too little is left.
            val pruned = VisualCheck.prune(visual, answer, userText)
            return pruned.visual?.let { Parsed.Ok(it, pruned.leftOut) } ?: Parsed.Rejected(pruned.reason ?: Reason.EMPTY, pruned.leftOut.firstOrNull())
        }

        /**
         * The prompt: the model extracts and picks, the app computes and draws. "none" is a fine reply, better than a
         * forced visual. Short texts keep the JSON small (a 1.7B model writes 150 tokens in 11 to 15 s on the X6).
         */
        const val PROMPT = "You extract structured data from an encyclopedia ANSWER into ONE visual for a phone screen. " +
            "Reply with ONE JSON object and nothing else.\n" +
            // Rules before the examples: a 1.7B model weighs what it reads first (X6, Nov 10: a 6-column table filled from
            // memory, and an area of 8.5 under "km²" that meant 8.5 million km²).
            "STRICT RULES:\n" +
            "1. Use ONLY facts and numbers written in the ANSWER (or EARLIER IN THIS CHAT or FROM THE SOURCES), copied exactly as written there.\n" +
            "2. NEVER add, invent, estimate, round, convert or compute a number, and never fill missing data from memory " +
            "(no totals, no percentages: the app does that).\n" +
            "3. Units: write the unit exactly as the ANSWER does, with its scale word, so the number and unit read as in the " +
            "ANSWER (\"2.642\" with \"trillion US$\", \"210\" with \"million\", \"8,515,767\" with \"km²\"). Never convert units. " +
            "In one chart or column, every value has the same unit.\n" +
            "4. If a value or cell is missing from the ANSWER, use an empty string (\"\") or leave the item out.\n" +
            "5. Keep text short (under 8 words per label). Tables: at most 3 columns. At most 6 rows or items.\n" +
            "6. Facts under EARLIER IN THIS CHAT or FROM THE SOURCES may join only when the ANSWER's visual measures the same thing.\n" +
            "7. If fewer than 2 numbers, steps or facts come from the ANSWER, reply {\"type\":\"none\"}.\n" +
            "Pick ONE type that fits:\n" +
            "- numbers to compare across things: {\"type\":\"bars\",\"title\":\"...\",\"unit\":\"...\",\"items\":[{\"label\":\"...\",\"value\":123}]}\n" +
            "- numbers that change over time, in order: {\"type\":\"line\",\"title\":\"...\",\"unit\":\"...\",\"items\":[{\"label\":\"1990\",\"value\":5}]}\n" +
            "- parts of one whole: {\"type\":\"share\",\"title\":\"...\",\"unit\":\"...\",\"items\":[{\"label\":\"...\",\"value\":40}]}\n" +
            "- a table (at most 3 columns): {\"type\":\"table\",\"title\":\"...\",\"columns\":[\"...\",\"...\",\"...\"],\"rows\":[[\"...\",\"...\",\"...\"]]}\n" +
            "- a process or how-to: {\"type\":\"steps\",\"title\":\"...\",\"steps\":[\"...\",\"...\"]}\n" +
            "- two to four options side by side: {\"type\":\"compare\",\"title\":\"...\",\"items\":[{\"name\":\"...\",\"points\":[\"...\",\"...\"]}]}\n" +
            "- key facts about one thing: {\"type\":\"facts\",\"title\":\"...\",\"facts\":[{\"label\":\"...\",\"value\":\"...\"}]}\n" +
            "\"value\" in bars, line and share is a plain number. If no visual would help, reply {\"type\":\"none\"}."

        private fun firstObject(s: String): JsonObject? {
            var start = s.indexOf('{')
            while (start >= 0) {
                var depth = 0
                var inString = false
                var escaped = false
                for (i in start until s.length) {
                    val ch = s[i]
                    if (inString) {
                        if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') inString = false
                        continue
                    }
                    when (ch) {
                        '"' -> inString = true
                        '{' -> depth++
                        '}' -> if (--depth == 0) {
                            runCatching { Json.parseToJsonElement(s.substring(start, i + 1)) as? JsonObject }.getOrNull()?.let { return it }
                            break
                        }
                    }
                }
                start = s.indexOf('{', start + 1)
            }
            return null
        }

        private fun cell(e: JsonElement): String = ((e as? JsonPrimitive)?.contentOrNull ?: "").trim().take(MAX_TEXT)
        private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.take(MAX_TEXT)
        private fun JsonObject.strings(k: String): List<String> = (this[k] as? JsonArray).orEmpty().map(::cell).filter { it.isNotEmpty() }
    }
}

/**
 * The visual's grounding: every number it shows is written in the answer (or the user's own message), however it's
 * written there, and every label shares its words with the answer. Dates count as their numbers: "1913" is grounded by
 * "12 March 1913" or "1913-03-12"; "12 March 1913" needs the 12 too.
 */
object VisualCheck {
    /**
     * The first failure, or null when everything is grounded. Numbers are checked everywhere. Words are checked in the
     * content (series labels, table cells, names, points, steps, fact values); headings (the title, a table's columns, a
     * fact's name like "Height") are the model's own words for what it shows, and aren't.
     */
    fun check(v: Visual, answer: String, userText: String = ""): Pair<Visual.Reason, String>? {
        val source = "$answer\n$userText"
        val written = numbersIn(source)
        val words = wordsOf(source)
        fun grounded(x: Double) = written.any { kotlin.math.abs(it - x) <= 1e-9 * maxOf(1.0, kotlin.math.abs(x)) }
        fun numbersOk(text: String): String? = tokens(text).firstOrNull { t -> valuesOf(t).none(::grounded) }
        fun labelOk(text: String): Boolean = contentWords(text).let { it.isEmpty() || it.any { w -> w in words } }
        val texts = ArrayList<String>()
        val labels = ArrayList<String>()
        when (v) {
            is Visual.Table -> { texts += v.columns; v.rows.forEach { r -> r.forEach { c -> texts += c; labels += c } } }
            is Visual.Series -> {
                v.items.firstOrNull { (_, x) -> !grounded(x) }?.let { return Visual.Reason.INVENTED_NUMBERS to VisualScale.format(it.second) }
                v.items.forEach { (l, _) -> texts += l; labels += l }
                // The unit is a claim too: its words come from the answer ("million", "km²"), not from the model's memory.
                v.unit?.let { u -> texts += u; if (!unitOk(u, source)) return Visual.Reason.UNGROUNDED_LABELS to u }
            }
            is Visual.Steps -> { texts += v.steps; labels += v.steps }
            is Visual.Compare -> v.items.forEach { (n, ps) -> texts += n; labels += n; texts += ps; labels += ps }
            is Visual.Facts -> v.facts.forEach { (l, x) -> texts += l; texts += x; labels += x }
        }
        texts.forEach { t -> numbersOk(t)?.let { return Visual.Reason.INVENTED_NUMBERS to it } }
        labels.firstOrNull { !labelOk(it) }?.let { return Visual.Reason.UNGROUNDED_LABELS to it }
        return null
    }

    /**
     * [visual]: what's left once everything not in the texts is out; null when too little is left to draw (fewer than two
     * items, rows of numbers, steps or options; no fact). [leftOut]: what came out, in order; [reason]: why the first did.
     */
    data class Pruned(val visual: Visual?, val leftOut: List<String>, val reason: Visual.Reason?)

    /**
     * Takes out what none of the texts has (the answer, the user's message, and whatever the caller added: earlier
     * answers, the answer's sources), instead of refusing the whole visual (adam, Nov 10):
     * - a chart: the items with a number or a label not in the texts; a unit not in them refuses the chart;
     * - a table: the row or the column with the most cells not in the texts or empty, again and again until every cell
     *   is backed (the first column, the names, stays: a bad name takes its row out); a column whose heading holds a
     *   number not in the texts goes too;
     * - steps, a comparison's points and options, facts: the lines not in the texts.
     */
    fun prune(v: Visual, answer: String, userText: String = ""): Pruned {
        val g = Ground("$answer\n$userText")
        val out = ArrayList<String>()
        var reason: Visual.Reason? = null
        fun note(what: String, r: Visual.Reason) { if (what !in out) out += what; if (reason == null) reason = r }
        /** Why [text] isn't backed: the number not in the texts, or the text itself when its words aren't; null when it is. */
        fun fault(text: String, checkWords: Boolean = true): Pair<String, Visual.Reason>? =
            g.badNumber(text)?.let { it to Visual.Reason.INVENTED_NUMBERS }
                ?: if (checkWords && !g.wordsOk(text)) text to Visual.Reason.UNGROUNDED_LABELS else null
        // The title and the unit are the model's words, but a number in them is a claim too (validator V-4).
        g.badNumber(v.title)?.let { return Pruned(null, listOf(it), Visual.Reason.INVENTED_NUMBERS) }
        val left: Visual? = when (v) {
            is Visual.Series -> {
                v.unit?.let { u -> g.badNumber(u)?.let { return Pruned(null, listOf(it), Visual.Reason.INVENTED_NUMBERS) } }
                v.unit?.let { u -> if (!unitOk(u, g.source)) return Pruned(null, listOf(u), Visual.Reason.UNGROUNDED_LABELS) }
                val labels = v.items.map { it.first }
                val kept = v.items.filter { (l, x) ->
                    val f = if (!g.grounded(x)) VisualScale.format(x) to Visual.Reason.INVENTED_NUMBERS
                        else fault(l) ?: if (!g.paired(l, x, labels, v.items)) "$l ${VisualScale.format(x)}" to Visual.Reason.UNPAIRED else null
                    f?.let { note(it.first, it.second) }
                    f == null
                }
                if (kept.size >= 2 && (v.kind != Visual.SeriesKind.SHARE || VisualScale.canShare(kept.map { it.second }))) v.copy(items = kept) else null
            }
            is Visual.Table -> {
                var cols = v.columns.indices.filter { c ->
                    if (c == 0) true else g.badNumber(v.columns[c]).also { b -> b?.let { note(it, Visual.Reason.INVENTED_NUMBERS) } } == null
                }
                var rows = v.rows.filter { r -> fault(r.getOrElse(0) { "" })?.also { note(it.first, it.second) } == null && r.getOrElse(0) { "" }.isNotBlank() }
                // A cell is bad when it's empty, holds something not in the texts, or a number the answer gives another
                // row's name (V-3); each round removes the worst line.
                val names = v.rows.map { it.getOrElse(0) { "" } }
                // Every (row name, number) of the table, for which side of a number its name is written on.
                val cellItems = v.rows.flatMap { r -> r.drop(1).flatMap { c -> tokens(c).mapNotNull { t -> valuesOf(t).firstOrNull()?.let { r.getOrElse(0) { "" } to it } } } }
                fun unpaired(r: List<String>, cell: String): Boolean = tokens(cell).any { t -> valuesOf(t).none { x -> g.paired(r.getOrElse(0) { "" }, x, names, cellItems) } }
                fun bad(r: List<String>, c: Int): Boolean = r.getOrElse(c) { "" }.let { it.isBlank() || fault(it) != null || unpaired(r, it) }
                /** Why a cell that comes out came out: its own fault, or a number that belongs to another row. */
                fun cellFault(r: List<String>, cell: String): Pair<String, Visual.Reason>? =
                    fault(cell) ?: if (unpaired(r, cell)) "${r.getOrElse(0) { "" }} $cell" to Visual.Reason.UNPAIRED else null
                while (true) {
                    val dataCols = cols.filter { it > 0 }
                    if (rows.isEmpty() || dataCols.isEmpty()) break
                    // Each round takes out the line that removes the most bad cells for the fewest good ones (bad − good):
                    // with Brazil backed and Russia invented, Russia's row goes, then the columns Brazil has no value for.
                    fun score(bad: Int, total: Int) = bad - (total - bad)
                    val worstRow = rows.map { r -> r to dataCols.count { bad(r, it) } }.maxByOrNull { (_, b) -> score(b, dataCols.size) }!!
                    val worstCol = dataCols.map { c -> c to rows.count { bad(it, c) } }.maxByOrNull { (_, b) -> score(b, rows.size) }!!
                    if (worstRow.second == 0 && worstCol.second == 0) break
                    val rowScore = if (worstRow.second == 0) Int.MIN_VALUE else score(worstRow.second, dataCols.size)
                    val colScore = if (worstCol.second == 0) Int.MIN_VALUE else score(worstCol.second, rows.size)
                    // On a tie, a column goes: the rows are the things compared.
                    val dropCol = colScore >= rowScore
                    if (dropCol) {
                        rows.forEach { r -> r.getOrElse(worstCol.first) { "" }.takeIf { it.isNotBlank() }?.let { cellFault(r, it) }?.let { note(it.first, it.second) } }
                        cols = cols - worstCol.first
                    } else {
                        dataCols.forEach { c -> worstRow.first.getOrElse(c) { "" }.takeIf { it.isNotBlank() }?.let { cellFault(worstRow.first, it) }?.let { note(it.first, it.second) } }
                        rows = rows.filter { it !== worstRow.first }
                    }
                }
                val dataCols = cols.filter { it > 0 }
                val cells = rows.sumOf { r -> dataCols.count { !bad(r, it) } }
                if (rows.isNotEmpty() && dataCols.isNotEmpty() && cells >= 2)
                    Visual.Table(v.title, cols.map { v.columns[it] }, rows.map { r -> cols.map { r.getOrElse(it) { "" } } }) else null
            }
            is Visual.Steps -> v.steps.filter { s -> fault(s)?.also { note(it.first, it.second) } == null }.takeIf { it.size >= 2 }?.let { Visual.Steps(v.title, it) }
            is Visual.Compare -> v.items.mapNotNull { (n, ps) ->
                if (fault(n)?.also { note(it.first, it.second) } != null) return@mapNotNull null
                val kept = ps.filter { p -> fault(p)?.also { note(it.first, it.second) } == null }
                if (kept.isEmpty()) null else n to kept
            }.takeIf { it.size >= 2 }?.let { Visual.Compare(v.title, it) }
            is Visual.Facts -> v.facts.filter { (l, x) ->
                (g.badNumber(l)?.let { it to Visual.Reason.INVENTED_NUMBERS } ?: fault(x))?.also { note(it.first, it.second) } == null
            }.takeIf { it.isNotEmpty() }?.let { Visual.Facts(v.title, it) }
        }
        return Pruned(left, out, if (left == null) reason ?: Visual.Reason.EMPTY else reason)
    }

    /** The texts a visual is checked against, read once. */
    private class Ground(val source: String) {
        private val written = numbersIn(source)
        private val words = wordsOf(source)
        fun grounded(x: Double) = written.any { kotlin.math.abs(it - x) <= 1e-9 * maxOf(1.0, kotlin.math.abs(x)) }

        /** Where each number is written in the source (its start), by value. */
        private val at: List<Pair<Int, List<Double>>> = TOKEN.findAll(source).map { it.range.first to valuesOf(it.value.trim()) }.toList()
        private val foldedSource = fold(source)

        /** Where [label]'s words (2 characters or more, letters or digits: "K2" too) are written in the source. */
        private fun placesOf(label: String): List<Int> =
            Regex("[\\p{L}\\p{N}]{2,}").findAll(fold(label)).map { it.value }.filter { it !in STOP && it.any(Char::isLetter) }.flatMap { w ->
                // A word with a digit ends there: "Part1" isn't "Part10" (validator V-3).
                val end = if (w.any(Char::isDigit)) "(?![\\p{N}])" else ""
                Regex("(?<![\\p{L}\\p{N}])${Regex.escape(stem(w))}$end").findAll(foldedSource).map { it.range.first }.toList()
            }.toList()

        /**
         * [label]'s places, without those inside a longer label of the same visual that contains it: "Sudan" isn't
         * where "South Sudan" is written (validator V-3).
         */
        private fun placesAmong(label: String, labels: List<String>): List<Int> {
            val own = fold(label).trim()
            val covered = labels.map { fold(it).trim() }.filter { it != own && it.length > own.length && Regex("(?<![\\p{L}\\p{N}])${Regex.escape(own)}(?![\\p{L}\\p{N}])").containsMatchIn(it) }
                .flatMap { longer -> Regex(Regex.escape(longer)).findAll(foldedSource).map { it.range }.toList() }
            return placesOf(label).filter { p -> covered.none { p in it } }
        }

        private val orderCache = HashMap<List<Pair<String, Double>>, Boolean>()

        /**
         * The value [x] stands next to [label] in the source, not next to another of the visual's [labels] (validator V-3:
         * "Paris 2.1 million, Lyon 0.5 million" drawn swapped passed): for some place [x] is written, the nearest label
         * (one before it counts at half the distance of one after) is [label]. True when no label is near any place
         * [x] is written, so a number the answer gives without naming its item isn't refused for that.
         */
        fun paired(label: String, x: Double, labels: List<String>, items: List<Pair<String, Double>> = emptyList()): Boolean {
            // Which side of its number a name is written on, read from this answer and this visual (validator V-3): the
            // reading under which more of the visual's items stand by their own name. "Paris has 2.1 million" puts the
            // name first; "2.1 million in Paris" puts it after. A swap fits neither reading, so it's still refused.
            // The answer's own sentences decide first (validator Q-A3): a partial swap could pick the reading that suited
            // it ("Paris 2.1, Lyon 0.9" passed against "…in Paris. 0.5 million… in Lyon. 0.9 million… in Marseille.").
            val numbersFirst = items.size >= 2 && (writtenNumbersFirst(labels) ?: orderCache.getOrPut(items) {
                items.count { (l, v) -> pairedAs(l, v, labels, true) } > items.count { (l, v) -> pairedAs(l, v, labels, false) }
            })
            return pairedAs(label, x, labels, numbersFirst)
        }

        /**
         * Which side of their numbers the answer writes these [labels] on, from its sentences alone: a label with numbers
         * before it in its sentence and none after votes numbers-first, the opposite votes names-first. Null on a tie.
         */
        private fun writtenNumbersFirst(labels: List<String>): Boolean? {
            val ends = listOf(0) + SENTENCE_END.findAll(source).map { it.range.last + 1 }.toList() + source.length
            var first = 0; var after = 0
            for (l in labels.distinct()) for (w in placesAmong(l, labels)) {
                val i = ends.indexOfLast { it <= w }.coerceAtLeast(0)
                val (from, to) = ends[i] to ends.getOrElse(i + 1) { source.length }
                val before = at.any { (p, _) -> p in from until w }
                val later = at.any { (p, _) -> p in (w + l.length) until to }
                if (before && !later) first++ else if (later && !before) after++
            }
            return if (first == after) null else first > after
        }

        /**
         * [numbersFirst] false: the name before the number counts, at half the distance of one after (the original
         * rule). True: the name after the number counts, at half the distance of one before.
         */
        private fun pairedAs(label: String, x: Double, labels: List<String>, numbersFirst: Boolean): Boolean {
            val near = 400
            val places = at.filter { (_, vs) -> vs.any { kotlin.math.abs(it - x) <= 1e-9 * maxOf(1.0, kotlin.math.abs(x)) } }.map { it.first }
            if (places.isEmpty()) return false
            val byLabel = labels.distinct().associateWith { placesAmong(it, labels) }
            var anyNear = false
            for (p in places) {
                fun dist(w: Int) = if (numbersFirst) (if (w >= p) (w - p) / 2 else (p - w)) else (if (w <= p) (p - w) / 2 else (w - p))
                fun nearestOf(keep: (Int) -> Boolean) = byLabel.mapValues { (_, ws) -> ws.filter(keep).minOfOrNull(::dist) ?: Int.MAX_VALUE }
                    .filterValues { it <= near }
                // The next sentence's subject doesn't take this number, but only while another name stays near: emptying
                // the field made every label pair by default, and a swapped card passed again (validator R-B1).
                val nearest = (nearestOf { w -> !acrossSentence(w, p, numbersFirst) }.takeIf { it.isNotEmpty() } ?: nearestOf { true })
                    .minByOrNull { it.value } ?: continue
                anyNear = true
                if (nearest.key == label) return true
            }
            return !anyNear
        }
        /**
         * A name on the far side of its number (after it when names come first) and past a sentence's end is the next
         * sentence's subject, not this number's: "…com uma área de 8.515.814 km². A Índia é…" is Brasil's area, not
         * India's (coder's F3 check, 15 Nov: the PT card dropped Brasil).
         */
        private fun acrossSentence(w: Int, p: Int, numbersFirst: Boolean): Boolean {
            val far = if (numbersFirst) w < p else w > p
            if (!far) return false
            // One character past the span, so a sentence that starts with the number itself ends the one before it
            // ("…in Paris. 0.5 million people live in Lyon": validator Q-A3).
            val between = source.substring(minOf(w, p), minOf(source.length, maxOf(w, p) + 1))
            // A clause break on the far side too: "…in Paris, 0.5 million in Lyon" is Lyon's 0.5 (Q-A3).
            return SENTENCE_END.containsMatchIn(between) || CLAUSE_END.containsMatchIn(between)
        }

        fun badNumber(text: String): String? = tokens(text).firstOrNull { t -> valuesOf(t).none(::grounded) }
        fun wordsOk(text: String): Boolean = contentWords(text).let { it.isEmpty() || it.any { w -> w in words } }
    }

    /**
     * A unit the answer uses: each of its words (3 letters or more: "million", "trillion", "milhões") is in the answer,
     * and each symbol or short unit (km², %, US$, °C, m) appears in it as written.
     */
    fun unitOk(unit: String, source: String): Boolean {
        val src = fold(source)
        val have = contentWords(source).toSet()
        val words = Regex("\\p{L}{3,}").findAll(fold(unit)).map { it.value }.filter { it !in STOP }.toList()
        if (words.any { w -> stem(w) !in have }) return false
        val symbols = Regex("[^\\p{L}\\p{N}\\s()/.,-]+|(?<!\\p{L})\\p{L}{1,2}[²³]?(?!\\p{L})").findAll(fold(unit)).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
        return symbols.all { it in src }
    }

    /**
     * A number as written: thousands in groups of three ("1,234", "1.234", "1 234") or plain digits, a decimal part, a
     * minus only where it can't be a dash ("1913-03-12" is 1913, 3 and 12), and a word for thousands or more. A list
     * ("1913, 1914") stays two numbers.
     */
    private val TOKEN = Regex(
        "(?<![\\p{N}.,])(?:(?<![\\p{L}\\p{N}])[-−])?(?:\\d{1,3}(?:[.,\\u00a0\\u202f]\\d{3})+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?)" +
            "(?:\\s*(?:%|million|millions|billion|billions|thousand|trillion|mil|milhão|milhões|bilhão|bilhões|bi|mi|k)(?![\\p{L}]))?",
        RegexOption.IGNORE_CASE,
    )

    private val CLAUSE_END = Regex("""[,;]\s|\s(?:and|e|y)\s""", RegexOption.IGNORE_CASE)
    private val SENTENCE_END = Regex("""[.!?](?:\s*\[\d{1,3}])?\s+(?=\p{Lu}|\d)""")

    /**
     * [text] with each number written as [source] writes the same value ("3,287,263" in a card under a Portuguese answer
     * that wrote "3.287.263"); a number [source] doesn't hold, or holds in two spellings, stays as it is.
     */
    fun asWritten(text: String, source: String): String {
        if (source.isBlank()) return text
        val spelled = HashMap<Double, MutableSet<String>>()
        for (t in tokens(source)) { val vs = valuesOf(t); if (vs.size == 1) spelled.getOrPut(vs[0]) { HashSet() } += t }
        return TOKEN.replace(text) { m ->
            val t = m.value.trim()
            val pick = valuesOf(t).mapNotNull { v -> spelled[v]?.singleOrNull() }.distinct().singleOrNull()
            if (pick == null || pick == t) m.value else m.value.replace(t, pick)
        }
    }

    private fun tokens(text: String): List<String> = TOKEN.findAll(text).map { it.value.trim() }.toList()

    /** Every number in [text], each in every way it could be read (EN and PT separators, with and without its multiplier word). */
    fun numbersIn(text: String): Set<Double> = tokens(text).flatMap(::valuesOf).toSet()

    /**
     * The values one written number can mean: "1,234" is 1234 (or 1.234 in PT); "1.234" is 1.234 (or 1234 in PT);
     * "3,5" is 3.5; "1 234" is 1234; "12%" is 12; "1.5 million" and "1,5 milhão" are 1.5 and 1,500,000.
     */
    fun valuesOf(token: String): List<Double> {
        val t = token.trim().replace("−", "-")
        val word = Regex("[\\p{L}%]+$").find(t)?.value?.lowercase()
        val digits = (if (word != null) t.removeSuffix(Regex("[\\p{L}%]+$").find(t)!!.value) else t).trim()
        val reads = readings(digits.replace("\u00a0", "").replace("\u202f", "").replace(" ", ""))
        val mult = when (word) {
            "thousand", "mil", "k" -> 1e3
            "million", "millions", "milhão", "milhões", "mi" -> 1e6
            "billion", "billions", "bilhão", "bilhões", "bi" -> 1e9
            "trillion" -> 1e12
            else -> null
        }
        return if (mult != null) reads + reads.map { it * mult } else reads
    }

    /**
     * How a number is read from the way it's written, one reading where the form says which (validator V-2: "3,5" was
     * also 35, "1,5 milhão" 15): both separators, the last one is the decimal; several of one, thousands; one comma
     * before exactly three digits ("1,234"), thousands, else a decimal ("3,5"); one dot before anything but exactly three
     * digits ("1.5", "8,849.0"), a decimal. Only one dot before exactly three digits ("1.234": EN 1.234, PT 1234) keeps
     * both readings.
     */
    fun readings(n: String): List<Double> {
        val neg = n.startsWith("-")
        val d = n.removePrefix("-")
        val commas = d.count { it == ',' }
        val dots = d.count { it == '.' }
        fun num(x: String) = x.toDoubleOrNull()?.let { if (neg) -it else it }
        val three = Regex("^\\d{1,3}[.,]\\d{3}$").matches(d)
        return when {
            commas > 0 && dots > 0 -> listOfNotNull(if (d.lastIndexOf(',') > d.lastIndexOf('.')) num(d.replace(".", "").replace(",", ".")) else num(d.replace(",", "")))
            commas > 1 -> listOfNotNull(num(d.replace(",", "")))
            dots > 1 -> listOfNotNull(num(d.replace(".", "")))
            commas == 1 -> listOfNotNull(if (three) num(d.replace(",", "")) else num(d.replace(",", ".")))
            dots == 1 && three -> listOfNotNull(num(d), num(d.replace(".", "")))
            else -> listOfNotNull(num(d))
        }
    }


    private val STOP = setOf(
        "the", "and", "for", "with", "from", "that", "this", "are", "was", "were", "has", "have", "its", "into", "than", "per", "about",
        "de", "da", "do", "das", "dos", "em", "para", "com", "por", "que", "uma", "um", "os", "as", "na", "no", "nas", "nos", "mais", "como",
    )

    private fun fold(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** A word reduced to a rough stem, so "volcanoes" meets "volcano" and "eruptions" "eruption". */
    private fun stem(w: String): String = when {
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 4 && w.endsWith("oes") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    private fun contentWords(text: String): List<String> =
        Regex("\\p{L}{3,}").findAll(fold(text)).map { it.value }.filter { it !in STOP }.map(::stem).toList()

    private fun wordsOf(text: String): Set<String> = contentWords(text).toSet()
}

/** What the app computes for a visual: shares that add up, axes, which views the same data allows, and a text copy. */
object VisualScale {
    /** Parts that can make a whole: at least two, none negative, the total above zero, at most [Visual.MAX_SHARE_PARTS]. */
    fun canShare(values: List<Double>): Boolean =
        values.size in 2..Visual.MAX_SHARE_PARTS && values.all { it >= 0 && it.isFinite() } && values.sum() > 0

    /** Each part's whole percent, adding up to exactly 100 (largest remainder): 1/3 each is 34, 33, 33. */
    fun percents(values: List<Double>): List<Int> {
        val total = values.sum()
        if (total <= 0) return values.map { 0 }
        val raw = values.map { it * 100.0 / total }
        val floors = raw.map { kotlin.math.floor(it).toInt() }.toMutableList()
        var left = 100 - floors.sum()
        raw.indices.sortedByDescending { raw[it] - floors[it] }.forEach { i -> if (left > 0) { floors[i]++; left-- } }
        return floors
    }

    /** An axis: ticks at a round step (1, 2, 2.5 or 5 times a power of ten), from zero for bars, around the data for a line. */
    data class Axis(val min: Double, val max: Double, val step: Double) {
        val ticks: List<Double> get() = generateSequence(min) { it + step }.takeWhile { it <= max + step * 1e-9 }.toList()
    }

    fun axis(values: List<Double>, fromZero: Boolean, ticks: Int = 4): Axis {
        var lo = values.minOrNull() ?: 0.0
        var hi = values.maxOrNull() ?: 1.0
        if (fromZero) { lo = minOf(0.0, lo); hi = maxOf(0.0, hi) }
        if (hi == lo) { hi = lo + 1; if (!fromZero) lo -= 1 }
        val step = niceStep((hi - lo) / ticks)
        return Axis(kotlin.math.floor(lo / step) * step, kotlin.math.ceil(hi / step) * step, step)
    }

    private fun niceStep(raw: Double): Double {
        val p = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(raw)))
        val f = raw / p
        return p * when { f <= 1 -> 1.0; f <= 2 -> 2.0; f <= 2.5 -> 2.5; f <= 5 -> 5.0; else -> 10.0 }
    }

    /**
     * The views a series can switch to with no new model call: bars and a table always, a line from 3 points, and a pie
     * only when the model said the numbers are parts of one whole (validator V-6: a pie of unrelated heights).
     */
    fun kinds(s: Visual.Series): List<String> = listOfNotNull(
        "bars",
        "line".takeIf { s.items.size >= 3 },
        "share".takeIf { s.kind == Visual.SeriesKind.SHARE && canShare(s.items.map { it.second }) },
        "table",
    )

    /** The same series as another kind (bars, line, share); a share only when the parts can make a whole. */
    fun asKind(s: Visual.Series, kind: Visual.SeriesKind): Visual.Series? =
        if (kind == Visual.SeriesKind.SHARE && !canShare(s.items.map { it.second })) null else s.copy(kind = kind)

    /** A series as a two-column table: the label, then the value with its unit. */
    fun asTable(s: Visual.Series): Visual.Table =
        Visual.Table(s.title, listOf("", s.unit ?: ""), s.items.map { (l, v) -> listOf(l, format(v)) })

    /** 1500.0 → "1500", 3.25 → "3.25": no trailing zeros, no exponent. */
    fun format(v: Double): String =
        if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString()
        else java.math.BigDecimal(v).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString()

    /** The visual as Markdown, for Copy: a table, "label: value unit" lines, numbered steps, or name: value lines. */
    fun markdown(v: Visual): String = buildString {
        if (v.title.isNotBlank()) append("**").append(v.title).append("**\n\n")
        when (v) {
            is Visual.Table -> {
                append("| ").append(v.columns.joinToString(" | ")).append(" |\n")
                append("|").append(v.columns.joinToString("|") { " --- " }).append("|\n")
                v.rows.forEach { r -> append("| ").append(r.joinToString(" | ")).append(" |\n") }
            }
            is Visual.Series -> {
                val pct = if (v.kind == Visual.SeriesKind.SHARE) percents(v.items.map { it.second }) else null
                v.items.forEachIndexed { i, (l, x) ->
                    append("- ").append(l).append(": ").append(format(x))
                    v.unit?.let { append(' ').append(it) }
                    pct?.let { append(" (").append(it[i]).append("%)") }
                    append('\n')
                }
            }
            is Visual.Steps -> v.steps.forEachIndexed { i, s -> append(i + 1).append(". ").append(s).append('\n') }
            is Visual.Compare -> v.items.forEach { (n, ps) -> append("**").append(n).append("**\n"); ps.forEach { append("- ").append(it).append('\n') } }
            is Visual.Facts -> v.facts.forEach { (l, x) -> append("- ").append(l).append(": ").append(x).append('\n') }
        }
    }.trimEnd()
}
