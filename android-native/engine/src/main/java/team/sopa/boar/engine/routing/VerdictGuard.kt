package team.sopa.boar.engine.routing

import team.sopa.boar.engine.rag.Js
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.regex.Pattern

/**
 * Ported from src/routing/verdictGuard.ts in r4to's PR #85 (BOAR R&D, MIT).
 *
 * VG, the verdict guard (cut 3 dev; coordinator 4B-LOSS-ANATOMY, arm 1). The 4B commits to its first-sentence verdict
 * before it states the facts, and the facts are often right: "No, astronauts did not walk on the Moon before 1970. The
 * first human moonwalk occurred on July 20, 1969". A pure check of question + answer after generation. English only; run
 * on model answers only (never health, cards, calculators).
 *  (a) date: a yes/no question with "before / after / by YYYY"; the Yes/No disagrees with the year of the answer's own
 *      "first …" fact (within the two sentences after the verdict).
 *  (b) compare: "Which <is more X>, A or B" / "Is A <more X> than B"; both entities get a number of the compared quantity
 *      (density, people, area, length, mass, age) in the same unit, the ranges don't overlap, and the verdict names the
 *      other one (or says the wrong Yes/No). Ambiguous, mixed units or a missing quantity: no flag.
 *  (c) rank: "Which <things> are bigger than X" answered "No <thing> is bigger…" next to "X … the Nth-largest" (N > 1),
 *      and any list the answer gives has N - 1 items.
 * Repair is a template from the parsed facts (plus the difference and ratio when the question asks "by how much"); the
 * model's self-correction notes and restatements of the old verdict are dropped. No regeneration. v3 abstains when a
 * number may belong to someone else, on a non-world rank scope or a subject inside another name, and on a "first …"
 * sentence with two years. An answer that isn't flagged gives null: nothing about it changes. The regexes are the
 * TypeScript ones verbatim, through JsRegex; the rank subject's unbounded lookbehind is checked by hand (java.util.regex
 * needs a bounded one).
 */
object VerdictGuard {
    /** [kind]: "date", "compare" or "rank". */
    data class Result(val kind: String, val text: String)

    // ---- text segments (sentences and list lines), kept with their separators so a repair only touches what it drops ----

    private class Seg(val text: String, var sep: String)

    /** Inline self-correction notes: "(Note: This response contains a factual error…)", "[Note: …]". */
    private val INLINE_NOTE = JsRegex.re("""\s*[(\[]\s*note:[^)\]]*[)\]]""", "gi")
    private val META = JsRegex.re(
        """^(?:wait\b|correct(?:ed|ion|ing)?\s*:|\(?\[?note\s*:)|\bthis (?:response|answer) contains (?:a |an )?(?:factual )?error\b|\bcontradicts the previous\b""",
        "i",
    )
    private val SPLIT = JsRegex.re("""((?<=[.!?:])[ \t]+(?=["'(\[*A-Z-])|\s*\n+\s*)""")

    private fun segments(text: String): List<Seg> {
        // String.prototype.split with a capturing separator: text, separator, text, …
        val parts = ArrayList<String>()
        var at = 0
        for (m in SPLIT.findAll(text)) {
            parts += text.substring(at, m.range.first)
            parts += m.value
            at = m.range.last + 1
        }
        parts += text.substring(at)
        val out = ArrayList<Seg>()
        var i = 0
        while (i < parts.size) {
            val t = parts[i]
            val sep = parts.getOrElse(i + 1) { "" }
            if (Js.trim(t).isEmpty()) {
                if (out.isNotEmpty()) out.last().sep += sep
            } else out += Seg(t, sep)
            i += 2
        }
        return out
    }

    private fun join(segs: List<Seg>): String = Js.trim(segs.mapIndexed { i, s -> s.text + if (i < segs.size - 1) s.sep else "" }.joinToString(""))

    private fun isMeta(s: String) = META.containsMatchIn(Js.trim(s))

    // ---- numbers ----

    private const val NUM = """(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)"""
    private val SCALE = mapOf("thousand" to 1e3, "million" to 1e6, "billion" to 1e9, "trillion" to 1e12)
    private val NUMBER_RE = JsRegex.re("""$NUM(?:\s*(?:–|—|-|to)\s*$NUM)?(?:\s+(thousand|million|billion|trillion)\b)?""", "gi")
    private fun num(s: String) = JsNumber.parse(s.replace(",", ""))

    private class Quantity(val lo: Double, val hi: Double, val unit: String, val index: Int)

    private val UNIT_STOP = setOf("while", "and", "whereas", "compared", "than", "which", "in", "on", "at", "for", "but", "making", "so", "or", "as", "with", "versus", "vs")
    private val UNIT_HEAD = JsRegex.re("""^\s*([^,;.()\[\]]{0,48})""")
    private val UNIT_RULES = listOf(
        JsRegex.re("""\b(?:square|sq\.?)\s+(?:kilomet(?:er|re)s?|km)\b|\bkm²|\bkm2\b""", "g") to "km2",
        JsRegex.re("""\b(?:square|sq\.?)\s+miles?\b|\bmi²""", "g") to "mi2",
        JsRegex.re("""\bkilomet(?:er|re)s?\b""", "g") to "km",
        JsRegex.re("""\bmet(?:er|re)s?\b""", "g") to "m",
        JsRegex.re("""\b(?:pounds?|lbs?)\b""", "g") to "lb",
        JsRegex.re("""\bkilo(?:gram)?s?\b|\bkgs\b""", "g") to "kg",
        JsRegex.re("""\b(?:feet|foot)\b""", "g") to "ft",
        JsRegex.re("""\bpersons\b""", "g") to "people",
    )
    private val SPACES = Regex(Js.WS + "+")

    /** "people per square kilometer, while…" -> "people per km2"; "lb (700 kg)" -> "lb". */
    private fun unitSig(rest: String): String {
        var head = (UNIT_HEAD.find(rest)?.groupValues?.get(1) ?: "").lowercase()
        for ((re, to) in UNIT_RULES) head = re.replace(head, to)
        val words = ArrayList<String>()
        for (w in head.split(SPACES).filter { it.isNotEmpty() }) {
            if (w in UNIT_STOP || words.size >= 4) break
            words += w
        }
        return words.joinToString(" ")
    }

    private val FOUR_DIGITS = JsRegex.re("""^\d{4}$""")
    private val NOT_A_MEASURE = JsRegex.re("""^\s*(?:%|st\b|nd\b|rd\b|th\b)""")

    /** Every number (or range) in [s], with its scale and unit, in order. Bare years (1500-2100, no unit), percentages and
     * ranks ("7th") are skipped. */
    private fun allQuantities(s: String): List<Quantity> {
        val out = ArrayList<Quantity>()
        for (m in NUMBER_RE.findAll(s)) {
            val g1 = m.groups[1]!!.value
            val g2 = m.groups[2]?.value
            val g3 = m.groups[3]?.value
            val scale = if (g3 != null) SCALE.getValue(g3.lowercase()) else 1.0
            val a = num(g1) * scale
            val b = if (g2 != null) num(g2) * scale else a
            val after = s.substring(m.range.last + 1)
            val unit = (if (g3 != null) "${g3.lowercase()} " else "") + unitSig(after)
            val yearLike = g2 == null && g3 == null && FOUR_DIGITS.containsMatchIn(g1) && a >= 1500 && a <= 2100 && unitSig(after).isEmpty()
            if (yearLike) continue
            // A percentage or a rank ("7th") is not a measurement of the entity.
            if (NOT_A_MEASURE.containsMatchIn(after)) continue
            out += Quantity(minOf(a, b), maxOf(a, b), Js.trim(unit), m.range.first)
        }
        return out
    }

    // ---- the compared quantity (VG v2: the number tied to the question's quantity, never just the first) ----

    private val Q_DENSITY = JsRegex.re("""\bdens(?:e|ely|er|est|ity)\b""", "i")
    private val Q_PEOPLE = JsRegex.re("""\b(?:more|fewer|less) (?:people|inhabitants|residents)\b|\bpopul(?:ous|ation)\b""", "i")
    private val Q_AGE = JsRegex.re("""\b(?:older|younger)\b""", "i")
    private val Q_AREA = JsRegex.re("""\barea\b|\bsquare\b""", "i")
    private val Q_LENGTH = JsRegex.re("""\b(?:taller|higher|longer|deeper|shallower)\b""", "i")
    private val Q_MASS = JsRegex.re("""\b(?:heavier|lighter|weighs?|weight)\b""", "i")
    private val Q_SIZE = JsRegex.re("""\b(?:bigger|larger|smaller|greater)\b""", "i")

    /** The quantity a comparison question is about; null = unclear (no flag). Generic size words allow area, mass or length. */
    private fun questionClasses(q: String): List<String>? = when {
        Q_DENSITY.containsMatchIn(q) -> listOf("density")
        Q_PEOPLE.containsMatchIn(q) -> listOf("people")
        Q_AGE.containsMatchIn(q) -> listOf("age")
        Q_AREA.containsMatchIn(q) -> listOf("area")
        Q_LENGTH.containsMatchIn(q) -> listOf("length")
        Q_MASS.containsMatchIn(q) -> listOf("mass")
        Q_SIZE.containsMatchIn(q) -> listOf("area", "mass", "length")
        else -> null
    }

    private val U_DENSITY = JsRegex.re("""\bper (?:km2|mi2)\b|\/ ?(?:km2|mi2)\b""")
    private val U_AGE = JsRegex.re("""\b(?:years? old|year-old|years of age)\b""")
    private val U_AREA = JsRegex.re("""\b(?:km2|mi2|hectares?|acres?)\b""")
    private val U_SCALE = JsRegex.re("""^(?:thousand|million|billion|trillion) """)
    private val U_MASS = JsRegex.re("""^(?:kg|lb|g|tons?|tonnes?|t|grams?|ounces?|oz)$""")
    private val U_LENGTH = JsRegex.re("""^(?:m|km|ft|mi|miles?|cm|mm|inch(?:es)?|in|yards?|yd)$""")
    private val U_PEOPLE = JsRegex.re("""\b(?:people|inhabitants|residents|citizens)\b""")
    private val U_PEOPLE_SCALE = JsRegex.re("""^(?:thousand|million|billion)$""")

    private fun unitClass(unit: String): String? {
        if (U_DENSITY.containsMatchIn(unit)) return "density"
        if (U_AGE.containsMatchIn(unit)) return "age"
        if (U_AREA.containsMatchIn(unit)) return "area"
        val first = U_SCALE.replaceFirst(unit, "").split(" ")[0]
        if (U_MASS.containsMatchIn(first)) return "mass"
        if (U_LENGTH.containsMatchIn(first)) return "length"
        if (U_PEOPLE.containsMatchIn(unit) || U_PEOPLE_SCALE.containsMatchIn(unit)) return "people"
        return null
    }

    // ---- comparatives ----

    private const val MORE_WORDS =
        "more|bigger|larger|greater|higher|denser|older|taller|longer|heavier|deeper|hotter|warmer|faster|richer|wider|farther|further|stronger|wealthier|busier|louder|brighter"
    private const val LESS_WORDS = "less|fewer|smaller|lower|younger|shorter|lighter|shallower|colder|cooler|slower|poorer|narrower|weaker|cheaper"
    private val MORE = JsRegex.re("""\b(?:$MORE_WORDS)\b""", "i")
    private val LESS = JsRegex.re("""\b(?:$LESS_WORDS)\b""", "i")
    private val LESS_ALL = JsRegex.re("""\b(?:$LESS_WORDS)\b""", "gi")
    private val MORE_WORD = JsRegex.re("""\bmore\b""", "i")
    private val LESS_OR_FEWER = JsRegex.re("""\b(?:less|fewer)\b""", "i")
    private const val COMPARATIVE = "(?:$MORE_WORDS|$LESS_WORDS)"

    /** "max" when the bigger number wins ("more densely populated"), "min" for "smaller / fewer", null when unclear. */
    private fun polarity(s: String): String? {
        val less = LESS.containsMatchIn(s)
        val more = MORE.containsMatchIn(s)
        if (less && !MORE_WORD.containsMatchIn(LESS_ALL.replace(s, ""))) return "min"
        if (LESS_OR_FEWER.containsMatchIn(s)) return "min"
        if (more) return "max"
        return null
    }

    private val ENTITY_STOP = setOf("the", "a", "an", "of", "adult", "male", "female", "average", "on", "in", "typical", "common", "wild")
    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private fun words(s: String) = WORD.findAll(s.lowercase()).map { it.value }.toList()

    /** Words that tell the two entities apart ("saltwater" vs "nile"; "moose" vs "american", "bison"). */
    private fun entityKeys(a: String, b: String): Pair<List<String>, List<String>> {
        val wa = words(a).filter { it !in ENTITY_STOP }
        val wb = words(b).filter { it !in ENTITY_STOP }
        return wa.filter { it !in wb } to wb.filter { it !in wa }
    }

    private fun mentionRe(keys: List<String>) =
        Regex("(?iu)(?<![\\p{L}])(?:${keys.joinToString("|") { Pattern.quote(it) }})(?:'s|’s|s|es)?(?![\\p{L}])")

    private class Mention(val who: Int, val index: Int, val end: Int)

    private fun mentions(s: String, res: List<Regex>): List<Mention> {
        val out = ArrayList<Mention>()
        for (who in 0..1) for (m in res[who].findAll(s)) out += Mention(who, m.range.first, m.range.last + 1)
        return out.sortedBy { it.index }
    }

    /** After a possessive mention ("Germany's …"), the words before the number must name the compared quantity itself. */
    private val OWN_MEASURE = JsRegex.re(
        """^\s*(?:(?:total|land|average|overall|population|surface|estimated|current|mean|peak|summit|official)\s+)*(?:population density|density|population|area|size|height|elevation|altitude|length|weight|mass|diameter|width|age)\b|^\s*(?:is|was|are|were|of|stands|sits|reaches|averages|measures|weighs|covers)\b""",
        "i",
    )
    private val CAPITALISED = Regex("(?<![\\p{L}\\p{N}])\\p{Lu}[\\p{L}-]*")
    private val POSSESSIVE_END = JsRegex.re("""['’]s$""", "i")

    /** A capitalised word (not the entities' own key words) or "in <Name>" between an entity's mention and its number. */
    private fun otherOwner(between: String, keys: List<String>): Boolean {
        val own = keys.map { it.lowercase() }.toSet()
        return CAPITALISED.findAll(between).any { POSSESSIVE_END.replace(it.value.lowercase(), "") !in own }
    }

    /** Each entity's stated quantity of the compared kind: from its mention to the other entity's next mention, within one
     * segment, the numbers whose unit fits the question's quantity. Two different same-unit values in one window: ambiguous. */
    private fun quantities(segs: List<Seg>, res: List<Regex>, classes: List<String>, keys: List<String>): Array<Quantity?>? {
        val found = arrayOfNulls<Quantity>(2)
        for (seg in segs) {
            val text = seg.text
            val ms = mentions(text, res)
            for (i in ms.indices) {
                val m = ms[i]
                if (found[m.who] != null) continue
                val nextOther = ms.drop(i + 1).firstOrNull { it.who != m.who }
                val window = text.substring(m.index, nextOther?.index ?: text.length)
                val fit = allQuantities(window).filter { q -> unitClass(q.unit)?.let { it in classes } == true }
                if (fit.isEmpty()) continue
                // VG v3 F1: the number must be the entity's own. Abstain when a capitalised name or an "in <Name>" phrase
                // sits between the mention and the number, or the mention is a possessive of something other than the
                // compared quantity ("Saturn's rings are 282,000 km wide").
                val from = m.end - m.index
                val between = if (from <= fit[0].index) window.substring(from, fit[0].index) else ""
                if (otherOwner(between, keys)) return null
                if (POSSESSIVE_END.containsMatchIn(text.substring(m.index, m.end)) && !OWN_MEASURE.containsMatchIn(between)) return null
                if (fit.any { q -> q.unit == fit[0].unit && (q.lo != fit[0].lo || q.hi != fit[0].hi) }) return null
                found[m.who] = fit[0]
            }
            if (found[0] != null && found[1] != null) break
        }
        return found
    }

    // ---- formatting ----

    private fun fmt(n: Double) = JsNumber.locale(n, false, if (n >= 100) 0 else if (n >= 10) 1 else 2)
    private fun capitalize(s: String) = if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)
    private val CARDINALS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty")
    private val ORDINALS = mapOf(
        "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10,
        "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13, "fourteenth" to 14, "fifteenth" to 15, "sixteenth" to 16, "seventeenth" to 17, "eighteenth" to 18, "nineteenth" to 19, "twentieth" to 20,
    )

    /** A shown entity name: the question's own words, article kept. */
    private fun display(s: String) = SPACES.replace(Js.trim(s), " ")

    /** Number.prototype.toFixed: the exact binary value, rounded half up. */
    private fun toFixed(n: Double, digits: Int) = BigDecimal(n).setScale(digits, RoundingMode.HALF_UP).toPlainString()

    // ---- (a) date threshold ----

    private val YESNO_Q = JsRegex.re("""^\s*(?:did|was|were|has|have|had|is|are|do|does)\b""", "i")
    private val THRESHOLD = JsRegex.re("""\b(before|after|by)\s+(?:the\s+(?:year\s+)?)?(1\d{3}|20\d{2})\b""", "i")
    private val VERDICT = JsRegex.re("""^\s*(yes|no)\b[,.!:;—–-]?\s*""", "i")
    private val NEGATION = JsRegex.re("""\b(did|was|were|had|has|have|is|are|could|would)(?:\s+not\b|n't\b)""", "i")
    private val NEGATION_ALL = JsRegex.re("""\b(did|was|were|had|has|have|is|are|could|would)(?:\s+not\b|n't\b)""", "gi")
    private val FIRST = JsRegex.re("""\bfirst\b""", "i")
    private val YEAR = JsRegex.re("""\b(?:1\d{3}|20\d{2})\b""")
    private val YEAR_ALL = JsRegex.re("""\b(?:1\d{3}|20\d{2})\b""", "g")
    private val YEAR_GROUP = JsRegex.re("""\b(1\d{3}|20\d{2})\b""")
    private val ABSOLUTE = JsRegex.re("""\b(?:never|no|none|nobody)\b""", "i")
    private val DENIES = JsRegex.re("""\b(?:no|not|never|none|n't)\b""", "i")

    private fun checkDate(question: String, segs: List<Seg>): Result? {
        if (!YESNO_Q.containsMatchIn(question)) return null
        val t = THRESHOLD.find(question) ?: return null
        val rel = t.groupValues[1].lowercase()
        val year = t.groupValues[2].toInt()
        val v = VERDICT.find(segs[0].text) ?: return null
        val said = v.groupValues[1].lowercase() == "yes"
        val factAt = segs.indices.firstOrNull { i -> i in 1..2 && FIRST.containsMatchIn(segs[i].text) && YEAR.containsMatchIn(segs[i].text) } ?: return null
        // VG v3 F3: a "first …" sentence with more than one year ("first plans from 1802, but it opened in 1994") can't
        // say which year is the event's: abstain.
        if (YEAR_ALL.findAll(segs[factAt].text).map { it.value }.toSet().size > 1) return null
        val first = YEAR_GROUP.find(segs[factAt].text)!!.groupValues[1].toInt()
        // The first time it happened decides "before / by" both ways; "after" only when the first time is already after.
        val truth: Boolean? = when (rel) {
            "before" -> first < year
            "by" -> first <= year
            else -> if (first > year) true else null
        }
        if (truth == null || truth == said) return null

        val rest = segs[0].text.substring(v.value.length)
        val verdict = if (truth) {
            if (NEGATION_ALL.findAll(rest).count() == 1 && !ABSOLUTE.containsMatchIn(rest)) "Yes, " + NEGATION.replaceFirst(rest, "$1") else "Yes."
        } else "No."
        val alt = when (rel) { "before" -> "pre-"; "after" -> "post-"; else -> "x^" }
        val phrase = JsRegex.re("""\b(?:${t.groupValues[1]}\s+(?:the\s+(?:year\s+)?)?|$alt)${t.groupValues[2]}\b""", "i")
        val kept = segs.drop(1).filter { !isMeta(it.text) && !(phrase.containsMatchIn(it.text) && DENIES.containsMatchIn(it.text)) }
        return Result("date", join(listOf(Seg(verdict, segs[0].sep)) + kept))
    }

    // ---- (b) two-entity numeric comparison ----

    private val WHICH_Q = JsRegex.re(
        """^\s*(?:which|who|what)\s+(.*?\b$COMPARATIVE\b.*?)\s*[,:]\s*(.+?)\s+or\s+(.+?)(?:\s*,\s*and\b.*?|\s+and\s+by\s+how\s+much.*?)?\s*\?\s*$""",
        "i",
    )
    private val YESNO_CMP_Q = JsRegex.re(
        """^\s*(?:is|are|was|were|does|do|did|has|have)\s+(.+?)\s+((?:have\s+|has\s+)?(?:a\s+)?$COMPARATIVE\b.*?)\s+than\s+(.+?)\s*\?\s*$""",
        "i",
    )
    private val BY_HOW_MUCH = JsRegex.re("""\bby how much\b|\bhow much (?:more|less|bigger|larger|denser)\b|\bhow many more\b""", "i")
    private val THAN = JsRegex.re("""\bthan\b""", "i")
    private val WHILE = JsRegex.re("""\bwhile\b|\bwhereas\b""", "i")
    private val DIGIT = Regex("\\d")
    private val KM2 = JsRegex.re("""\bkm2\b""")
    private val MI2 = JsRegex.re("""\bmi2\b""")

    private fun checkCompare(question: String, segs: List<Seg>): Result? {
        val which = WHICH_Q.find(question)
        val yesno = if (which != null) null else YESNO_CMP_Q.find(question)
        if (which == null && yesno == null) return null
        val vp = if (which != null) which.groupValues[1] else yesno!!.groupValues[2]
        val a = if (which != null) which.groupValues[2] else yesno!!.groupValues[1]
        val b = if (which != null) which.groupValues[3] else yesno!!.groupValues[3]
        val pol = polarity(vp) ?: return null
        val (ka, kb) = entityKeys(a, b)
        if (ka.isEmpty() || kb.isEmpty()) return null
        val res = listOf(mentionRe(ka), mentionRe(kb))
        val classes = questionClasses(question) ?: return null
        val content = segs.filter { !isMeta(it.text) }
        val qs = quantities(content, res, classes, ka + kb) ?: return null
        val qa = qs[0] ?: return null
        val qb = qs[1] ?: return null
        if (qa.unit != qb.unit) return null
        if (qa.lo <= qb.hi && qb.lo <= qa.hi) return null // overlapping ranges: no decision
        val aWins = if (pol == "max") qa.lo > qb.hi else qa.hi < qb.lo

        fun both(s: String) = mentions(s, res).let { ms -> ms.any { it.who == 0 } && ms.any { it.who == 1 } }
        val v0 = segs[0].text
        if (yesno != null) {
            val v = VERDICT.find(v0) ?: return null
            if ((v.groupValues[1].lowercase() == "yes") == aWins) return null
            val verdict = if (aWins) "Yes." else "No."
            val kept = segs.drop(1).filter { !isMeta(it.text) && !(both(it.text) && polarity(it.text) != null && !DIGIT.containsMatchIn(it.text)) }
            return Result("compare", join(listOf(Seg(verdict, segs[0].sep)) + kept))
        }

        // Which-form: whom does the verdict sentence name as the winner?
        val ms = mentions(v0, res)
        if (ms.isEmpty()) return null
        val vpol = polarity(v0) ?: return null
        val than = THAN.find(v0)
        val named = if (than != null && ms.any { it.index < than.range.first } && ms.any { it.index > than.range.first }) ms.first { it.index < than.range.first }.who
        else if (ms.map { it.who }.toSet().size == 1) ms[0].who
        else return null
        val claimedA = if (vpol == pol) named == 0 else named == 1
        if (claimedA == aWins) return null

        val (w, l) = if (aWins) a to b else b to a
        val (qw, ql) = if (aWins) qa to qb else qb to qa
        val verb = SPACES.replace(Js.trim(vp), " ")
        var verdict = "${capitalize(display(w))} $verb than ${display(l)}"
        // Difference and ratio only for two single positive values (VG v2: never for ranges).
        if (BY_HOW_MUCH.containsMatchIn(question) && qw.lo == qw.hi && ql.lo == ql.hi && ql.lo > 0) {
            val diff = Math.abs(qw.lo - ql.lo)
            val ratio = maxOf(qw.lo, ql.lo) / minOf(qw.lo, ql.lo)
            val unit = MI2.replaceFirst(KM2.replaceFirst(qw.unit, "km²"), "mi²")
            verdict += ", by about ${fmt(diff)}${if (unit.isNotEmpty()) " $unit" else ""} (${fmt(qw.lo)} versus ${fmt(ql.lo)}, about ${toFixed(ratio, 1)} times as much)"
        }
        verdict += "."
        // Restatements of the verdict (both entities and a comparative, no figures) go; the figures stay.
        val kept = segs.drop(1).filter { !isMeta(it.text) && !(both(it.text) && polarity(it.text) != null && !WHILE.containsMatchIn(it.text)) }
        return Result("compare", join(listOf(Seg(verdict, segs[0].sep)) + kept))
    }

    // ---- (c) rank ----

    private val RANK_Q = JsRegex.re("""^\s*which\s+([\p{L} ]+?)\s+(are|is)\s+((?:bigger|larger|greater)\b.*?)\s*\?\s*$""", "iu")
    private val DENIAL = JsRegex.re(
        """\b(?:no|none of the)\s+[\p{L}]+(?:\s+[\p{L}]+)?\s+(?:is|are|has|have)\s+(?:any\s+)?(?:bigger|larger|greater)\b|\bthere (?:is|are) no\s+[\p{L}]+(?:\s+[\p{L}]+)?\s+(?:bigger|larger|greater)\b""",
        "iu",
    )
    private val RANK = JsRegex.re(
        """\b(second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|(\d{1,2})(?:st|nd|rd|th))[- ](?:largest|biggest)\b""",
        "i",
    )
    private val LIST = JsRegex.re(
        """\b(?:after|following|behind)\s+((?:the\s+)?[A-Z][\p{L}.]*(?:\s+[A-Z][\p{L}.]*)*(?:\s*,\s*(?:and\s+)?(?:the\s+)?[A-Z][\p{L}.]*(?:\s+[A-Z][\p{L}.]*)*)+)""",
        "u",
    )
    private val INCLUDE_LIST = JsRegex.re(
        """\b(?:include|includes|including)\s*:?\s+((?:the\s+)?[A-Z][\p{L}.]*(?:\s+[A-Z][\p{L}.]*)*(?:\s*,\s*(?:and\s+)?(?:the\s+)?[A-Z][\p{L}.]*(?:\s+[A-Z][\p{L}.]*)*)+)""",
        "u",
    )
    private val BULLET = JsRegex.re("""^\s*(?:[-*•]|\d{1,2}[.)])\s+\S""")
    private val SUBJECT = JsRegex.re("""\bthan\s+(?:the\s+)?(.+?)(?:\s+(?:by|in|when|on)\b.*)?$""", "i")
    private val OTHER_SCOPE = JsRegex.re("""\b(?:in|of|within)\s+(?:the\s+)?(?!world\b)(?:\p{Lu}[\p{L}-]*|EU|continent|region|hemisphere)""", "u")
    /** The TypeScript's lookbehind (?<![\p{Lu}][\p{L}.-]*\s+), as a test of the text before the subject. */
    private val AFTER_NAME = Regex("\\p{Lu}[\\p{L}.-]*" + Js.WS + "+\\z")
    private val LETTER_OR_NUMBER = Regex("[\\p{L}\\p{N}]")
    private val ITEM_SPLIT = JsRegex.re("""\s*,\s*(?:and\s+)?|\s+and\s+""")
    private val TRAILING_DOTS = Regex("[.]+$")
    private val IES = JsRegex.re("ies$", "i")
    private val PLURAL_S = JsRegex.re("(?<!s)s$", "i")

    /** [subject] on word boundaries and not right after another capitalised word ("South Sudan" is not "Sudan"). */
    private fun namesSubject(text: String, subject: String): Boolean {
        var from = 0
        while (true) {
            val i = text.indexOf(subject, from)
            if (i < 0) return false
            from = i + 1
            val before = text.getOrNull(i - 1)
            val after = text.getOrNull(i + subject.length)
            if (before != null && LETTER_OR_NUMBER.matches(before.toString())) continue
            if (after != null && LETTER_OR_NUMBER.matches(after.toString())) continue
            if (AFTER_NAME.containsMatchIn(text.substring(0, i))) continue
            return true
        }
    }

    private fun checkRank(question: String, segs: List<Seg>): Result? {
        val q = RANK_Q.find(question) ?: return null
        if (segs.none { DENIAL.containsMatchIn(it.text) }) return null
        // VG v2: the rank must be the subject's own ("India is the seventh-largest").
        val subject = SUBJECT.find(q.groupValues[3])?.groupValues?.get(1)?.let(Js::trim) ?: ""
        if (subject.isEmpty()) return null
        // VG v3 F2: and it must be a world rank: any other scope ("in Africa", "in the EU") abstains.
        val rankSeg = segs.firstOrNull { RANK.containsMatchIn(it.text) && namesSubject(it.text, subject) } ?: return null
        val r = RANK.find(rankSeg.text)!!
        if (OTHER_SCOPE.containsMatchIn(rankSeg.text.substring(r.range.first))) return null
        val n = r.groups[2]?.value?.toInt() ?: ORDINALS[r.groupValues[1].lowercase()] ?: return null
        if (!(n > 1 && n <= 50)) return null
        val count = n - 1
        var noun = Js.trim(q.groupValues[1])
        val be: String
        if (count == 1) {
            noun = PLURAL_S.replaceFirst(IES.replaceFirst(noun, "y"), "")
            be = "is"
        } else be = "are"
        var verdict = "${capitalize(if (count <= 20) CARDINALS[count] else count.toString())} $noun $be ${Js.trim(q.groupValues[3])}"
        // VG v2: the answer's own list must agree with the new count, else no flag. A bulleted or numbered list, or a prose
        // list ("after A, B and C", "include A, B and C").
        val bullets = segs.count { BULLET.containsMatchIn(it.text) }
        if (bullets > 0 && bullets != count) return null
        for (s in segs) {
            val l = LIST.find(s.text) ?: INCLUDE_LIST.find(s.text) ?: continue
            val items = l.groupValues[1].split(ITEM_SPLIT).map { TRAILING_DOTS.replace(Js.trim(it), "") }.filter { it.isNotEmpty() }
            if (items.size != count) return null
            verdict += ": ${items.dropLast(1).joinToString(", ")} and ${items.last()}"
            break
        }
        verdict += "."
        val kept = segs.filterIndexed { i, s -> i != 0 && !isMeta(s.text) && !DENIAL.containsMatchIn(s.text) }
        return Result("rank", join(listOf(Seg(verdict, segs[0].sep)) + kept))
    }

    /**
     * The verdict guard: null when the answer is not flagged (it stays byte-identical), else the repaired text and which
     * check fired. English questions only (the caller checks the language).
     */
    fun check(question: String, answer: String): Result? {
        if (Js.trim(question).isEmpty() || Js.trim(answer).isEmpty()) return null
        val segs = segments(INLINE_NOTE.replace(answer, ""))
        if (segs.isEmpty()) return null
        return checkDate(question, segs) ?: checkCompare(question, segs) ?: checkRank(question, segs)
    }
}
