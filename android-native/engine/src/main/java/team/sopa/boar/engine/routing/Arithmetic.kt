package team.sopa.boar.engine.routing

/**
 * Ported from r4to's src/routing/arithmetic.ts (PR #85, BOAR R&D, MIT).
 *
 * Two word-problem shapes a small model sets up wrong, answered exactly from the question's own numbers (v0.1
 * vit-sci-003, "10 tokens per second… 30 tokens per second… 1,300 tokens of sources and a 400-token answer, how long?"):
 *  (1) rate × quantity -> time: "N units at R units per second/minute/hour", one phase or two (prompt then answer,
 *      download then install), each quantity paired with its rate by adjacency ("600 MB at 20 MB/s") or by phase words;
 *  (2) ceil-division: "how many X of size S for T" ("23 L of water, 5 L jerrycans, how many jerrycans?").
 * English only; a message with a symptom, injury, medication or danger word is never answered here. The regexes are
 * the TypeScript ones verbatim (JsRegex keeps their JS meaning), so both engines answer the same questions.
 */
object Arithmetic {
    /** kind: "rate-time" | "ceil-division"; value: seconds (rate-time) or a count (ceil-division). */
    data class Answer(val kind: String, val text: String, val value: Double)

    private const val NUM = """(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)"""
    private fun num(s: String) = JsNumber.parse(s.replace(",", ""))

    /** Health, injury, medication or body words: never a calculator answer (NumGuard shares them). */
    internal val HEALTH = JsRegex.re(
        """\b(?:heart|pulse|bpm|breath\w*|blood|dos(?:e|es|age|ing)|mg|mcg|ml|iv|drip|infusion|insulin|tablets?|pills?|fever|contractions?|labou?r|pain\w*|bleed\w*|injur\w*|symptoms?|medic\w*|drugs?|overdos\w*|poison\w*|bab(?:y|ies)|toddlers?|infants?|calories?|kcal|glucose|oxygen|sp[o0]2)\b""",
        "i",
    )

    /** A situation, not a word problem ("water rising 10 cm per minute, how long do we have?"). */
    internal val DANGER = JsRegex.re(
        """\b(?:trapped|stuck|rising|flood\w*|fire|smoke|drown\w*|sinking|emergency|leak\w*|gas|danger\w*|unsafe|collaps\w*|avalanche|hurricane|tornado|lost|dying|dead|unconscious|attack\w*|bitten|bite|stung|hypotherm\w*|frostbite|choking|suicid\w*|kill\w*)\b""",
        "i",
    )

    // ---- units ----

    private val TIME_UNITS: Map<String, Pair<Double, String>> = mapOf(
        "s" to (1.0 to "second"), "sec" to (1.0 to "second"), "secs" to (1.0 to "second"), "second" to (1.0 to "second"), "seconds" to (1.0 to "second"),
        "min" to (60.0 to "minute"), "mins" to (60.0 to "minute"), "minute" to (60.0 to "minute"), "minutes" to (60.0 to "minute"),
        "h" to (3600.0 to "hour"), "hr" to (3600.0 to "hour"), "hrs" to (3600.0 to "hour"), "hour" to (3600.0 to "hour"), "hours" to (3600.0 to "hour"),
        "day" to (86400.0 to "day"), "days" to (86400.0 to "day"),
    )
    private const val TIME = "seconds?|secs?|s|minutes?|mins?|min|hours?|hrs?|hr|h|days?"

    private val U_TOKEN = JsRegex.re("^(?:tok|tokens?)$")
    private val U_LITRE = JsRegex.re("^(?:l|lt|litres?|liters?)$")
    private val U_MB = JsRegex.re("^(?:mb|megabytes?)$")
    private val U_GB = JsRegex.re("^(?:gb|gigabytes?)$")
    private val U_KB = JsRegex.re("^(?:kb|kilobytes?)$")
    private val U_KM = JsRegex.re("^(?:km|kms|kilomet(?:er|re)s?)$")
    private val U_MILE = JsRegex.re("^(?:mi|miles?)$")
    private val U_GALLON = JsRegex.re("^(?:gal|gallons?)$")
    private val IES_END = JsRegex.re("ies$")
    private val S_END = JsRegex.re("(?<=[^s])s$")

    /** "tokens" -> "token", "litres" / "L" -> "litre", "MB" -> "mb". */
    internal fun unitKey(raw: String): String {
        val u = raw.lowercase()
        return when {
            U_TOKEN.containsMatchIn(u) -> "token"
            U_LITRE.containsMatchIn(u) -> "litre"
            U_MB.containsMatchIn(u) -> "mb"
            U_GB.containsMatchIn(u) -> "gb"
            U_KB.containsMatchIn(u) -> "kb"
            U_KM.containsMatchIn(u) -> "km"
            U_MILE.containsMatchIn(u) -> "mile"
            U_GALLON.containsMatchIn(u) -> "gallon"
            else -> S_END.replaceFirst(IES_END.replaceFirst(u, "y"), "")
        }
    }

    private val LABELS = mapOf("token" to "token", "litre" to "litre", "mb" to "MB", "gb" to "GB", "kb" to "KB", "km" to "km", "mile" to "mile", "gallon" to "gallon")
    private val CONSONANT_Y = JsRegex.re("[^aeiou]y$")

    /** How a unit is written back: the singular or plural word. */
    private fun unitLabel(key: String, n: Double): String {
        val one = LABELS[key] ?: key
        if (one == "MB" || one == "GB" || one == "KB" || one == "km" || n == 1.0) return one
        return if (CONSONANT_Y.containsMatchIn(one)) "${one.dropLast(1)}ies" else "${one}s"
    }

    // Compact rate spellings: "mph", "kph", "wpm", "ppm".
    private val COMPACT = mapOf("mph" to ("mile" to "h"), "kph" to ("km" to "h"), "wpm" to ("word" to "min"), "ppm" to ("page" to "min"))

    private class Rate(val start: Int, val end: Int, val value: Double, val per: Double, val unit: String, val time: String)
    private class Quantity(val start: Int, val end: Int, val value: Double, val unit: String)

    private val RATE_RE = JsRegex.re("""$NUM\s*-?\s*([a-zA-Z]+)\s*(?:per|an?|each|every|\/)\s*($TIME)\b|$NUM\s*(mph|kph|wpm|ppm)\b""", "gi")

    private fun rates(q: String): List<Rate> {
        val out = ArrayList<Rate>()
        for (m in RATE_RE.findAll(q)) {
            val g = m.groups
            val (n, unitRaw, timeRaw) = if (g[1] != null) Triple(g[1]!!.value, g[2]!!.value, g[3]!!.value)
            else COMPACT.getValue(g[5]!!.value.lowercase()).let { Triple(g[4]!!.value, it.first, it.second) }
            val t = TIME_UNITS[timeRaw.lowercase()] ?: continue
            // "a minute" after a bare count is not a rate unit ("5 minutes a day"): the unit must not be a time word itself.
            if (TIME_UNITS[unitRaw.lowercase()] != null) continue
            val per = num(n)
            if (!(per > 0)) continue
            out += Rate(m.range.first, m.range.last + 1, per / t.first, per, unitKey(unitRaw), t.second)
        }
        return out
    }

    private val QTY_RE = JsRegex.re("""$NUM\s*-?\s*([a-zA-Z]+)""", "gi")

    private fun quantities(q: String, rs: List<Rate>): List<Quantity> {
        val units = rs.map { it.unit }.toSet()
        val out = ArrayList<Quantity>()
        for (m in QTY_RE.findAll(q)) {
            val start = m.range.first
            if (rs.any { start >= it.start && start < it.end }) continue
            val unit = unitKey(m.groupValues[2])
            if (unit !in units) continue
            val value = num(m.groupValues[1])
            if (!(value > 0)) continue
            out += Quantity(start, m.range.last + 1, value, unit)
        }
        return out
    }

    private val INPUT = JsRegex.re("""\b(?:prompts?|prefill\w*|process\w*|reads?|reading|ingest\w*|sources?|input|context|download\w*|upload\w*|scan\w*|proofread\w*)\b""", "i")
    private val OUTPUT = JsRegex.re("""\b(?:answers?|generat\w*|writ\w*|wrote|decod\w*|output\w*|repl(?:y|ies)|responses?|install\w*|print\w*|format\w*)\b""", "i")

    private fun phaseOf(s: String): String? {
        val i = INPUT.containsMatchIn(s)
        val o = OUTPUT.containsMatchIn(s)
        return if (i && !o) "in" else if (o && !i) "out" else null
    }

    private val ASK_TIME = JsRegex.re(
        """\bhow long\b|\bhow (?:many|much) (?:time|seconds?|minutes?|hours?|days?)\b|\btime (?:needed|required)\b|\btotal (?:time|wait|seconds?|minutes?|hours?)\b|\btotal\s*\?|\bwhen will (?:it|this) (?:be )?(?:done|finish)""",
        "i",
    )

    // ---- formatting ----

    private fun fmt(n: Double, digits: Int = 1) = JsNumber.locale(n, false, digits)
    private fun str(n: Double) = JsNumber.toString(n)
    private fun count(n: Double) = JsNumber.locale(n, false)

    private fun duration(sec: Double): String {
        if (sec < 120) {
            val s = "${fmt(sec)} seconds"
            val r = JsNumber.round(sec % 60)
            return if (sec >= 60) "$s (${str(kotlin.math.floor(sec / 60))} min${if (r != 0.0) " ${str(r)} s" else ""})" else s
        }
        if (sec < 2 * 3600) {
            val m = kotlin.math.floor(sec / 60)
            val s = JsNumber.round(sec - m * 60)
            return "${fmt(sec / 60)} minutes${if (s != 0.0) " (${str(m)} min ${str(s)} s)" else ""}"
        }
        if (sec < 2 * 86400) {
            val h = kotlin.math.floor(sec / 3600)
            val m = JsNumber.round((sec - h * 3600) / 60)
            return "${fmt(sec / 3600)} hours${if (m != 0.0) " (${str(h)} h ${str(m)} min)" else ""}"
        }
        return "${fmt(sec / 86400)} days"
    }

    /** One step: "1,300 tokens ÷ 30 tokens per second ≈ 43.3 seconds". */
    private fun step(q: Quantity, r: Rate): String {
        val t = q.value / r.per
        val exact = kotlin.math.abs(t - JsNumber.round(t * 10) / 10) < 1e-9
        return "${fmt(q.value, 2)} ${unitLabel(q.unit, q.value)} ÷ ${fmt(r.per, 2)} ${unitLabel(r.unit, r.per)} per ${r.time} ${if (exact) "=" else "≈"} ${fmt(t)} ${if (t == 1.0) r.time else "${r.time}s"}"
    }

    // ---- (1) rate × quantity -> time ----

    private val ADJACENT = JsRegex.re("""^\s*(?:[a-z]+\s+){0,2}(?:at|@)\s*$""", "i")

    private fun rateTime(q: String): Answer? {
        if (!ASK_TIME.containsMatchIn(q)) return null
        val rs = rates(q)
        if (rs.isEmpty() || rs.size > 2) return null
        val qs = quantities(q, rs)
        if (qs.size != rs.size) return null
        val pairs = ArrayList<Pair<Quantity, Rate>>()
        // Insertion-ordered, as the TypeScript Sets.
        val freeQ = LinkedHashSet(qs)
        val freeR = LinkedHashSet(rs)
        // Adjacency: "600 MB at 20 MB/s", "a 3,000 token prompt at 60 tokens per second", "400 tokens generated at 8 tok/s".
        for (x in qs) for (r in rs) {
            if (x in freeQ && r in freeR && r.start > x.end && ADJACENT.containsMatchIn(q.substring(x.end, r.start))) {
                pairs += x to r
                freeQ.remove(x)
                freeR.remove(r)
            }
        }
        // Phase words: a rate's are just before it, a quantity's just after it (then just before).
        if (freeQ.size == 2 && freeR.size == 2) {
            val marks = (rs.map { it.start to it.end } + qs.map { it.start to it.end }).sortedBy { it.first }
            fun before(s: Int) = q.substring(maxOf(0, marks.filter { it.second <= s }.maxOfOrNull { it.second } ?: 0), s)
            fun after(e: Int) = q.substring(e, minOf(q.length, marks.filter { it.first >= e }.minOfOrNull { it.first } ?: q.length))
            val rp = freeR.map { it to phaseOf(before(it.start)) }
            val qp = freeQ.map { it to (phaseOf(after(it.end)) ?: phaseOf(before(it.start))) }
            for (phase in listOf("in", "out")) {
                val r = rp.filter { it.second == phase }
                val x = qp.filter { it.second == phase }
                if (r.size == 1 && x.size == 1) {
                    pairs += x[0].first to r[0].first
                    freeQ.remove(x[0].first)
                    freeR.remove(r[0].first)
                }
            }
        }
        if (freeQ.size == 1 && freeR.size == 1) {
            pairs += freeQ.first() to freeR.first()
            freeQ.clear()
            freeR.clear()
        }
        if (freeQ.isNotEmpty() || freeR.isNotEmpty() || pairs.any { (x, r) -> x.unit != r.unit }) return null
        pairs.sortBy { it.first.start }
        val total = pairs.sumOf { (x, r) -> x.value / r.value }
        val steps = pairs.map { (x, r) -> step(x, r) }
        val text = if (pairs.size == 1) "About ${duration(total)}: ${steps[0]}. Computed from the numbers in your question."
        else "About ${duration(total)} in total: ${steps[0]}, plus ${steps[1]}. Computed from the numbers in your question."
        return Answer("rate-time", text, total)
    }

    // ---- (2) ceil-division ----

    private val HOW_MANY = JsRegex.re("""\bhow many\s+(?:more\s+)?([a-z][a-z-]*)""", "i")
    private const val CLAUSE = """[,.;:?!()]|\b(?:and|with|if|then|into|but|so)\b"""
    private val CLAUSE_RE = JsRegex.re("($CLAUSE)", "i")
    private val STEM_ES = JsRegex.re("(?:ches|shes|xes|sses|uses)$")
    private val NUMS = JsRegex.re("""(?<![\p{L}-])$NUM(?!\s*%)(?![\p{L}\d-]*\d)""", "gu")
    private val UNIT_AFTER = JsRegex.re("""^\s*-?\s*([a-zA-Z]+)""")
    private val MEASURE = JsRegex.re("^(?:litre|mb|gb|kb|km|mile|gallon|kg|g|lb|eth|btc|m|cm|ft|day|week|hour|minute|second|tb)$")
    private val SHORT_UNIT = JsRegex.re("^[A-Z]|^(?:kg|g|lb|m|cm|ft|km|mb|gb|kb|tb)$", "i")

    private fun stem(w: String): String {
        val l = w.lowercase()
        if (STEM_ES.containsMatchIn(l)) return l.dropLast(2)
        if (IES_END.containsMatchIn(l)) return "${l.dropLast(3)}y"
        return S_END.replaceFirst(l, "")
    }

    private class Num(val value: Double, val index: Int, val raw: String)

    private fun ceilDivision(q: String): Answer? {
        val hm = HOW_MANY.find(q) ?: return null
        val x = stem(hm.groupValues[1])
        if (x.length < 3) return null
        val rest = q.substring(0, hm.range.first) + " " + q.substring(hm.range.last + 1)
        // Numbers, minus identifiers ("EIP-7251", "ERC-20") and percentages.
        val nums = NUMS.findAll(rest).map { Num(num(it.groupValues[1]), it.range.first, it.groupValues[1]) }.toList()
        if (nums.size != 2) return null
        // The clauses, separators included (JS split with a capturing group), so their offsets cover all of rest.
        val clauses = ArrayList<Triple<Int, Int, String>>()
        var at = 0
        for (m in CLAUSE_RE.findAll(rest)) {
            clauses += Triple(at, m.range.first, rest.substring(at, m.range.first))
            clauses += Triple(m.range.first, m.range.last + 1, m.value)
            at = m.range.last + 1
        }
        clauses += Triple(at, rest.length, rest.substring(at))
        fun clauseOf(i: Int) = clauses.first { i >= it.first && i < it.second }.third
        val xRe = JsRegex.re("""\b${jsEscape(x)}(?:e?s)?\b|\bper\b|\beach\b|\bmax\b|\bcap\b""", "i")
        val sizeCands = nums.filter { xRe.containsMatchIn(clauseOf(it.index)) }
        if (sizeCands.size != 1) return null
        val size = sizeCands[0]
        val total = nums.first { it !== size }
        if (!(size.value > 0 && total.value > size.value)) return null
        // Units next to the numbers must agree when both are written ("23 L … 5 L", "10,000 ETH … 32 ETH").
        fun unitAfter(n: Num) = UNIT_AFTER.find(rest.substring(n.index + n.raw.length))?.groupValues?.get(1) ?: ""
        val us = unitKey(unitAfter(size))
        val ut = unitKey(unitAfter(total))
        if (MEASURE.containsMatchIn(us) && MEASURE.containsMatchIn(ut) && us != ut) return null
        if (rates(q).isNotEmpty()) return null
        val quotient = total.value / size.value
        val n = kotlin.math.ceil(quotient - 1e-9)
        val full = kotlin.math.floor(quotient + 1e-9)
        val raw = unitAfter(size)
        fun unitFor(v: Double) = if (!MEASURE.containsMatchIn(us)) "" else if (SHORT_UNIT.containsMatchIn(raw) && raw.length <= 3) " $raw" else " ${unitLabel(us, v)}"
        val noun = hm.groupValues[1].lowercase()
        val text = if (n == full) "${count(n)} $noun: ${fmt(total.value, 2)} ÷ ${fmt(size.value, 2)} = ${count(n)} exactly."
        else "${count(n)} $noun: ${fmt(total.value, 2)} ÷ ${fmt(size.value, 2)} = ${fmt(quotient, 2)}, rounded up. ${count(full)} full ones hold " +
            "${fmt(full * size.value, 2)}${unitFor(full * size.value)}; the remaining ${fmt(total.value - full * size.value, 2)}${unitFor(total.value - full * size.value)} needs one more."
        return Answer("ceil-division", text, n)
    }

    /** x.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"): a word as a literal inside a JS regex. */
    private fun jsEscape(s: String) = s.replace(Regex("""[.*+?^${'$'}{}()|\[\]\\]""")) { "\\" + it.value }

    /** The exact answer to a rate or ceil-division word problem (EN), or null. Never on health/medication messages. */
    fun answer(query: String): Answer? {
        if (HEALTH.containsMatchIn(query) || DANGER.containsMatchIn(query)) return null
        return rateTime(query) ?: ceilDivision(query)
    }
}
