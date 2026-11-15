package team.sopa.boar.engine.routing

import java.math.BigDecimal
import java.math.MathContext

/**
 * A plain unit conversion, answered exactly (adam, Nov 13: "send converter"). On the F3 "26 miles in km" went to the
 * 1.7B model: 35.8 s for a long text that said 41.8 km and then flagged its own numbers as not in the sources. Calculators
 * runs this last, after the temperature rule; the receipt says "calculator-conversion".
 *
 * Length (mile, km, m, cm, foot, inch, yard), mass (kg, lb, g, oz) and volume (US gallon, L), exact factors, any two
 * units of one family, EN and PT. Two shapes, both with the question's own number: "26 miles in km" (also "to", "into",
 * "em", "para") and "how many km is 26 miles" / "quantos km são 26 milhas".
 *
 * Left alone, so the rules that work today keep their questions: a rate (mph, km/h, "per hour", "por hora", "/h": those
 * are UnitBridge + Arithmetic), a fuel economy ("/100" or mpg: the fuel rule), a UK or imperial gallon (the fuel rule
 * tells them apart, this one only knows the US gallon), two names for the same unit, and a unit with no number before
 * it. The health lexicon is CalcShortcut's (NumGuard). Pure: text in, answer out.
 */
object UnitConversion {
    private data class Unit(
        val family: String,
        /** The size in the family's base: metres, grams or litres. */
        val base: Double,
        /** Miles, feet, pounds: the factor is said from this side ("1 lb = 0.45359237 kg"). */
        val imperial: Boolean,
        val en: Pair<String, String>,
        val pt: Pair<String, String>,
        /** The names a question uses, as a regex alternation. */
        val names: String,
    )

    private val UNITS = listOf(
        Unit("length", 1609.344, true, "mile" to "miles", "milha" to "milhas", "mi|miles?|milhas?"),
        Unit("length", 1000.0, false, "km" to "km", "km" to "km", "km|kms|kilomet(?:er|re)s?|quil[ôo]metros?"),
        // "m" is a metre only as a whole word (after a number, or after "in"/"em"): never the start of "my" or "min".
        Unit("length", 1.0, false, "m" to "m", "m" to "m", "m|met(?:er|re)s?|metros?"),
        Unit("length", 0.01, false, "cm" to "cm", "cm" to "cm", "cm|centimet(?:er|re)s?|cent[íi]metros?"),
        Unit("length", 0.3048, true, "foot" to "feet", "pé" to "pés", "ft|feet|foot|pés?|pes"),
        // "in" alone is an inch only in the source slot and only when a "to"/"in"/"em" follows ("5 in to cm"): see SOURCE.
        Unit("length", 0.0254, true, "inch" to "inches", "polegada" to "polegadas", "inch(?:es)?|polegadas?"),
        Unit("length", 0.9144, true, "yard" to "yards", "jarda" to "jardas", "yds?|yards?|jardas?"),
        Unit("mass", 1000.0, false, "kg" to "kg", "kg" to "kg", "kgs?|kilos?|kilogram(?:me)?s?|quilos?|quilogramas?"),
        Unit("mass", 453.59237, true, "lb" to "lb", "lb" to "lb", "lbs?|pounds?|libras?"),
        Unit("mass", 1.0, false, "g" to "g", "g" to "g", "g|gram(?:me)?s?|gramas?"),
        Unit("mass", 28.349523125, true, "oz" to "oz", "oz" to "oz", "oz|ounces?|on[çc]as?"),
        Unit("volume", 3.785411784, true, "US gallon" to "US gallons", "galão americano" to "galões americanos",
            "(?:us\\s+)?gallons?|gal|gal[ãa]o(?:\\s+americano)?|gal[õo]es(?:\\s+americanos)?"),
        Unit("volume", 1.0, false, "L" to "L", "L" to "L", "l|lit(?:er|re)s?|litros?"),
    )

    private val UNIT_RES = UNITS.map { Regex("(?:${it.names})", RegexOption.IGNORE_CASE) }
    private val ALL_NAMES = UNITS.joinToString("|") { it.names }

    // As Calculators.NUM: "1.5", PT "1,5", "1,200.5", PT "2.450"; starts only where a run of digits starts.
    private const val NUM = """(?<![\d\p{L}])(\d{1,3}(?:[.\s]\d{3})+(?:,\d+)?|\d+(?:[.,]\d+)?)"""
    private const val END = """(?![\p{L}/])"""
    private const val TO = """(?:in|into|to|em|para|in terms of)"""

    /** The number and the unit it's in: "in" counts as inches only when a "to"/"in"/"em" follows ("5 in to cm"). */
    private val SOURCE_TAIL = """\s*((?:$ALL_NAMES)$END|in(?=\s+(?:to|into|in|em|para)\s))"""

    /** "26 miles in km", "convert 26 miles into kilometres", "26 milhas para quilômetros". */
    private val FORWARD = Regex("""$NUM$SOURCE_TAIL\s+$TO\s+(?:(?:the|o|a|os|as)\s+)?((?:$ALL_NAMES))$END""", RegexOption.IGNORE_CASE)

    /** A quantity right before the converted number: "5 feet " in "5 feet 10 in to cm", "1 lb and " in "1 lb and 8 oz". */
    private val PART_BEFORE = Regex("""\d\s*((?:$ALL_NAMES))$END\s*(?:,|and|e|&)?\s*$""", RegexOption.IGNORE_CASE)

    /** A quantity right after the converted one: " 10 inches" in "how many cm is 5 feet 10 inches". */
    private val PART_AFTER = Regex("""^\s*(?:,|and|e|&)?\s*\d+(?:[.,]\d+)?\s*((?:$ALL_NAMES))$END""", RegexOption.IGNORE_CASE)

    /** "how many km is 26 miles", "how many cm in 6 feet", "quantos km são 26 milhas", "quantas libras tem 80 kg". */
    private val ASKED = Regex(
        """(?<![\p{L}])(?:how\s+many|quant[oa]s)\s+((?:$ALL_NAMES))$END\s+(?:is|are|in|make|equals?|são|sao|é|e|tem|têm|dá|da|dão|em|há\s+em|cabem\s+em)\s+(?:there\s+in\s+)?$NUM$SOURCE_TAIL""",
        RegexOption.IGNORE_CASE,
    )

    /** A rate or a fuel economy: UnitBridge + Arithmetic, or the fuel rule. */
    private val RATE = Regex(
        """\b(?:mph|kph|kmh|mpg)\b|km\s*/\s*h|/\s*100|/\s*(?:h|hr|hour|s|sec|min)\b|\b(?:per|an?|each|every)\s+(?:hour|minute|second|hr)\b|\bpor\s+(?:hora|minuto|segundo)\b|\b(?:miles?|kilomet(?:er|re)s?)\s+per\b""",
        RegexOption.IGNORE_CASE,
    )
    private val UK_GALLON = Regex("""\b(?:uk|imperial|brit[âa]nic[oa]|ingl[eê]s)\b""", RegexOption.IGNORE_CASE)

    /** PT words in the conversion itself: "26 milhas em km" has no PT_QUESTION word and only one PT_ONLY one. */
    private val PT_WORDS = Regex(
        """(?<![\p{L}])(?:em|para|quantos|quantas|quanto|são|milhas?|quil[ôo]metros?|metros?|cent[íi]metros?|pés?|polegadas?|jardas?|quilos?|quilogramas?|libras?|gramas?|on[çc]as?|gal[ãa]o|gal[õo]es|litros?)(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )

    private fun unitOf(raw: String): Unit? {
        val name = raw.trim()
        if (name.equals("in", ignoreCase = true)) return UNITS[5]
        val i = UNIT_RES.indexOfFirst { it.matches(name) }
        return if (i < 0) null else UNITS[i]
    }

    /** "26 miles = 41.84 km (1 mile = 1.609344 km)." for a plain conversion, or null. */
    fun convert(query: String, pt: Boolean): String? {
        if (RATE.containsMatchIn(query)) return null
        val fwd = FORWARD.find(query)
        val asked = if (fwd == null) ASKED.find(query) else null
        val (rawNum, from, to, match) = fwd?.let { Quad(it.groupValues[1], it.groupValues[2], it.groupValues[3], it.value) }
            ?: asked?.let { Quad(it.groupValues[2], it.groupValues[3], it.groupValues[1], it.value) }
            ?: return null
        val numAt = fwd?.groups?.get(1)?.range?.first ?: asked!!.groups[2]!!.range.first
        val unitEnd = (fwd?.groups?.get(2) ?: asked!!.groups[3]!!).range.last + 1
        val src = unitOf(from) ?: return null
        // "5 feet 10 in to cm", "1 lb 8 oz to kg": the number is the last part of a compound quantity. Converting it alone
        // looks exact and is wrong (validator B-2): the model takes the question.
        PART_BEFORE.find(query.substring(0, numAt))?.let { m -> if (unitOf(m.groupValues[1])?.family == src.family) return null }
        PART_AFTER.find(query.substring(unitEnd))?.let { m -> if (unitOf(m.groupValues[1])?.family == src.family) return null }
        val dst = unitOf(to) ?: return null
        if (src === dst || src.family != dst.family) return null
        if (src.family == "volume" && UK_GALLON.containsMatchIn(query)) return null
        val ptHere = pt || PT_WORDS.containsMatchIn(match)
        val x = Calculators.parseNumber(rawNum, Calculators.readsComma(query, ptHere))
        if (!x.isFinite() || x < 0) return null
        val y = x * src.base / dst.base
        if (!y.isFinite()) return null

        // The factor, said from the imperial side ("1 lb = 0.45359237 kg"), or from the bigger unit ("1 km = 1,000 m").
        val ref = when {
            src.imperial != dst.imperial -> if (src.imperial) src else dst
            src.base >= dst.base -> src
            else -> dst
        }
        val other = if (ref === src) dst else src
        val factor = ref.base / other.base
        val xs = JsNumber.locale(x, ptHere, 6)
        val ys = value(y, ptHere)
        val fs = JsNumber.locale(factor, ptHere, 9)
        return "$xs ${name(src, xs, ptHere)} = $ys ${name(dst, ys, ptHere)} (1 ${name(ref, "1", ptHere)} = $fs ${name(other, fs, ptHere)})."
    }

    /** Two decimals at most; under 1, three significant digits, so 0.02 miles isn't "0 km". */
    private fun value(y: Double, pt: Boolean): String =
        if (y != 0.0 && kotlin.math.abs(y) < 1) JsNumber.locale(BigDecimal(y).round(MathContext(3)).toDouble(), pt, 12)
        else JsNumber.locale(y, pt, 2)

    /** Singular for "1" (EN) or under 2 (PT, "1,5 milha"); the printed number decides, so "1.00" rounds count. */
    private fun name(u: Unit, printed: String, pt: Boolean): String {
        val n = Calculators.parseNumber(printed, pt)
        return if (pt) (if (n < 2) u.pt.first else u.pt.second) else if (n == 1.0) u.en.first else u.en.second
    }

    private data class Quad(val num: String, val from: String, val to: String, val match: String)
}
