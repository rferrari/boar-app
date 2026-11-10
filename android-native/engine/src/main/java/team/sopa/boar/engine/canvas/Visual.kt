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
        data class Ok(val visual: Visual) : Parsed
        /** [detail]: what failed, for the eval and the log (a number, a label); never shown as the answer. */
        data class Rejected(val reason: Reason, val detail: String? = null) : Parsed
    }

    /**
     * NOTHING: the model said a visual wouldn't help. RESERVED: an interactive kind not built yet. INVENTED_NUMBERS and
     * UNGROUNDED_LABELS: something not in the answer (or the user's own message). BAD_SHARE: parts that can't make a whole.
     */
    enum class Reason { NO_JSON, UNKNOWN_TYPE, RESERVED, EMPTY, INVENTED_NUMBERS, UNGROUNDED_LABELS, BAD_SHARE, NOTHING }

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
                        val v = (i["value"] as? JsonPrimitive)?.let { it.doubleOrNull ?: VisualCheck.valuesOf(it.contentOrNull ?: "").firstOrNull() }
                            ?: return@mapNotNull null
                        (i.text("label") ?: return@mapNotNull null) to v
                    }.filter { it.second.isFinite() }.take(if (kind == SeriesKind.SHARE) MAX_SHARE_PARTS else MAX_ITEMS)
                    if (items.size < 2) return Parsed.Rejected(Reason.EMPTY)
                    if (kind == SeriesKind.SHARE && !VisualScale.canShare(items.map { it.second })) return Parsed.Rejected(Reason.BAD_SHARE)
                    Series(title, kind, o.text("unit")?.takeIf { it.isNotBlank() }, items)
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
            return VisualCheck.check(visual, answer, userText)?.let { Parsed.Rejected(it.first, it.second) } ?: Parsed.Ok(visual)
        }

        /**
         * The prompt: the model extracts and picks, the app computes and draws. "none" is a fine reply, better than a
         * forced visual. Short texts keep the JSON small (a 1.7B model writes 150 tokens in 11 to 15 s on the X6).
         */
        const val PROMPT = "You turn an encyclopedia answer into ONE visual for a phone screen. Reply with ONE JSON object and nothing else.\n" +
            "Pick the type that fits the ANSWER best:\n" +
            "- numbers to compare across things: {\"type\":\"bars\",\"title\":\"...\",\"unit\":\"...\",\"items\":[{\"label\":\"...\",\"value\":123}]}\n" +
            "- numbers that change over time, in order: {\"type\":\"line\",\"title\":\"...\",\"unit\":\"...\",\"items\":[{\"label\":\"1990\",\"value\":5}]}\n" +
            "- parts of one whole: {\"type\":\"share\",\"title\":\"...\",\"unit\":\"...\",\"items\":[{\"label\":\"...\",\"value\":40}]}\n" +
            "- several things with the same properties: {\"type\":\"table\",\"title\":\"...\",\"columns\":[\"...\",\"...\"],\"rows\":[[\"...\",\"...\"]]}\n" +
            "- a process or how-to: {\"type\":\"steps\",\"title\":\"...\",\"steps\":[\"...\",\"...\"]}\n" +
            "- two to four options side by side: {\"type\":\"compare\",\"title\":\"...\",\"items\":[{\"name\":\"...\",\"points\":[\"...\",\"...\"]}]}\n" +
            "- key facts about one thing: {\"type\":\"facts\",\"title\":\"...\",\"facts\":[{\"label\":\"...\",\"value\":\"...\"}]}\n" +
            "Copy labels and numbers exactly as the ANSWER writes them; \"value\" is a plain number. Never add, guess, round or " +
            "compute a number (no totals, no percentages: the app does that). At most 8 items or rows. Keep every text short " +
            "(under 12 words). If no visual would help, reply {\"type\":\"none\"}."

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
     * A number as written: thousands in groups of three ("1,234", "1.234", "1 234") or plain digits, a decimal part, a
     * minus only where it can't be a dash ("1913-03-12" is 1913, 3 and 12), and a word for thousands or more. A list
     * ("1913, 1914") stays two numbers.
     */
    private val TOKEN = Regex(
        "(?<![\\p{N}.,])(?:(?<![\\p{L}\\p{N}])[-−])?(?:\\d{1,3}(?:[.,\\u00a0\\u202f ]\\d{3})+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?)" +
            "(?:\\s*(?:%|million|millions|billion|billions|thousand|trillion|mil|milhão|milhões|bilhão|bilhões|bi|mi|k)(?![\\p{L}]))?",
        RegexOption.IGNORE_CASE,
    )

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
        val digits = (if (word != null) t.removeSuffix(Regex("[\\p{L}%]+$").find(t)!!.value) else t).trim().replace(" ", " ").replace(" ", " ")
        val compact = digits.replace(" ", "")
        val reads = LinkedHashSet<Double>()
        fun add(s: String) = s.toDoubleOrNull()?.let { reads += it }
        // EN: , thousands and . decimal.
        add(compact.replace(",", ""))
        // PT: . thousands and , decimal.
        add(compact.replace(".", "").replace(",", "."))
        // A plain decimal comma with no thousands ("3,5").
        if (compact.count { it == ',' } == 1 && '.' !in compact) add(compact.replace(",", "."))
        val mult = when (word) {
            "thousand", "mil", "k" -> 1e3
            "million", "millions", "milhão", "milhões", "mi" -> 1e6
            "billion", "billions", "bilhão", "bilhões", "bi" -> 1e9
            "trillion" -> 1e12
            else -> null
        }
        if (mult != null) reads.toList().forEach { reads += it * mult }
        return reads.toList()
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

    /** The views a series can switch to with no new model call: bars and a table always, a line from 3 points, a share for parts of a whole. */
    fun kinds(s: Visual.Series): List<String> = listOfNotNull(
        "bars",
        "line".takeIf { s.items.size >= 3 },
        "share".takeIf { canShare(s.items.map { it.second }) },
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
