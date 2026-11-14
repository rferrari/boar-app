package team.sopa.boar.engine.routing

import team.sopa.boar.engine.rag.Js

/**
 * Arithmetic questions answered exactly, with the formula, instead of by a model (port of src/routing/calculators.ts;
 * the compact model got fuel economy, Naismith's rule, currency with a given rate, a battery's watt-hours for a flight
 * and a heat warning wrong). Pure: the question in, the answer text out (or null). Numbers are printed as JavaScript's
 * toLocaleString does for en-US / pt-BR.
 */
object Calculators {
    /** kind: "temperature" | "fuel-economy" | "naismith" | "currency" | "battery" | "water" | "conversion". */
    data class Calculation(val kind: String, val text: String)

    // Starts only where a run of digits starts: from every digit inside a long run, \d+ backtracked over the rest of it,
    // so 4,000 digits took 5 s and 20,000 over two minutes (the TypeScript has the same pattern).
    private const val NUM = """(?<!\d)(\d{1,3}(?:[.\s]\d{3})+(?:,\d+)?|\d+(?:[.,]\d+)?)"""

    private val WS_ANY = JsRegex.re("""\s""")
    private val COMMA_3_END = JsRegex.re(""",\d{3}$""")
    private val DOT_3_END = JsRegex.re("""\.\d{3}$""")

    /** "7,5" / "7.5" / "2.450" (PT thousands) / "1,200.5" / "18" -> number (NaN when it isn't one, as Number()). */
    fun parseNumber(raw: String, pt: Boolean): Double {
        val s = WS_ANY.replace(raw, "")
        if (',' in s && '.' in s) {
            return JsNumber.parse(if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replaceFirst(",", ".") else s.replace(",", ""))
        }
        if (',' in s) return if (COMMA_3_END.containsMatchIn(s) && !pt) JsNumber.parse(s.replace(",", "")) else JsNumber.parse(s.replaceFirst(",", "."))
        if ('.' in s) return if (DOT_3_END.containsMatchIn(s) && pt) JsNumber.parse(s.replace(".", "")) else JsNumber.parse(s)
        return JsNumber.parse(s)
    }

    /**
     * The question's decimal mark, read from its own numbers before its language (t9 0330: "27.000 mAh 3,7 V no avião"
     * wasn't detected as Portuguese and 27.000 read as 27). A comma before 1, 2 or 4+ digits ("3,7") or after a dot
     * ("1.200,5") says the comma is decimal; the same for the dot ("3.7", "1,200.5"). Exactly one of the two decides
     * every "N.NNN" and "N,NNN" in the question; both or neither: null, the language decides.
     */
    fun decimalMark(q: String): Char? {
        val marks = HashSet<Char>()
        for (m in NUMBER_TOKEN.findAll(q)) {
            val t = m.value
            val seps = t.filter { it == '.' || it == ',' }
            if (seps.toSet().size == 2) { marks += seps.last(); continue }
            if (seps.length == 1 && t.substringAfter(seps[0]).length != 3) marks += seps[0]
        }
        return marks.singleOrNull()
    }

    /** Read "N,NNN" / "N.NNN" with a decimal comma: the question's own mark first ([decimalMark]), else [pt]. */
    fun readsComma(q: String, pt: Boolean): Boolean = decimalMark(q)?.let { it == ',' } ?: pt

    private val NUMBER_TOKEN = Regex("""(?<![\d.,])\d+(?:[.,]\d+)+""")
    private val THOUSANDS = Regex("""\d{1,3}(?:[.,]\d{3})+""")

    /** A whole count written with thousands marks, "27.000" or "27,000": 27000 in every language (a battery's mAh). */
    private fun wholeNumber(raw: String): Double? = raw.trim().takeIf { THOUSANDS.matches(it) }?.filter(Char::isDigit)?.toDouble()

    /**
     * Portuguese in the rules' own words (t9 0330), for the answer's language when the detector misses a short question:
     * "27.000 mAh 3,7 V no avião" has no question word. QueryContext is not touched.
     */
    private val PT_WORDS = JsRegex.re("""\b(?:avi[ãa]o|voo|voar|bagagem|companhia|litros?|milhas?|gal[ãa]o|gal[õo]es|quil[ôo]metros?|quanto|quantos|quantas|posso|pode|levar|meu|minha|tenho|preciso|gorjeta|c[âa]mbio)\b""", "i")

    private fun fmt(n: Double, pt: Boolean, digits: Int = 2) = JsNumber.locale(n, pt, digits)

    // (a) Fuel economy: L/100 km <-> miles per gallon (US 235.215, UK 282.481).
    private val UK = JsRegex.re("""\b(uk|imperial|brit[âa]nic[oa]|ingl[eê]s)\b""", "i")
    private val L100 = JsRegex.re("""$NUM\s*(?:l|litros?|liters?|litres?)\s*(?:/|a cada|por|per|every|each)\s*100\s*km""", "i")
    private val ASKS_MPG = JsRegex.re("""\b(mpg|milhas? por gal[ãa]o|miles? per (?:us |uk |imperial )?gallon|mi\/gal)\b""", "i")
    private val MPG = JsRegex.re("""$NUM\s*(?:mpg|milhas? por gal[ãa]o|miles? per (?:us |uk |imperial )?gallon)""", "i")
    private val ASKS_L100 = JsRegex.re("""l\s*\/\s*100|litros? (a cada|por) 100|liters? per 100""", "i")

    private fun fuelEconomy(q: String, pt: Boolean, rd: Boolean): String? {
        val uk = UK.containsMatchIn(q)
        val k = if (uk) 282.481 else 235.215
        val gal = if (pt) (if (uk) "galão imperial" else "galão americano") else if (uk) "UK gallon" else "US gallon"
        val l100 = L100.find(q)
        if (l100 != null && ASKS_MPG.containsMatchIn(q)) {
            val x = parseNumber(l100.groupValues[1], rd)
            if (!(x > 0)) return null
            val mpg = k / x
            return if (pt) {
                "${fmt(x, pt)} L/100 km = ${fmt(mpg, pt)} milhas por galão ($gal). Conta: mpg = ${fmt(k, pt, 3)} ÷ L/100 km."
            } else {
                "${fmt(x, pt)} L/100 km = ${fmt(mpg, pt)} miles per gallon ($gal). Formula: mpg = ${fmt(k, pt, 3)} ÷ L/100 km."
            }
        }
        val mpgM = MPG.find(q)
        if (mpgM != null && ASKS_L100.containsMatchIn(q)) {
            val x = parseNumber(mpgM.groupValues[1], rd)
            if (!(x > 0)) return null
            return if (pt) {
                "${fmt(x, pt)} milhas por galão ($gal) = ${fmt(k / x, pt)} L/100 km. Conta: L/100 km = ${fmt(k, pt, 3)} ÷ mpg."
            } else {
                "${fmt(x, pt)} miles per gallon ($gal) = ${fmt(k / x, pt)} L/100 km. Formula: L/100 km = ${fmt(k, pt, 3)} ÷ mpg."
            }
        }
        return null
    }

    // (b) Naismith's rule: 1 h per 5 km, plus 1 h per 600 m of ascent.
    private val NAISMITH = JsRegex.re("naismith", "i")
    private val KM = JsRegex.re("""$NUM\s*km\b""", "i")
    private val METERS = JsRegex.re("""$NUM\s*m\b(?![a-z])""", "i")

    private fun naismith(q: String, pt: Boolean, rd: Boolean): String? {
        if (!NAISMITH.containsMatchIn(q)) return null
        val km = KM.find(q)
        val up = METERS.find(q.replaceFirst(km?.value ?: "", ""))
        if (km == null) return null
        val d = parseNumber(km.groupValues[1], rd)
        val a = if (up != null) parseNumber(up.groupValues[1], rd) else 0.0
        val h = d / 5 + a / 600
        val hh = kotlin.math.floor(h)
        val mm = JsNumber.round((h - hh) * 60)
        val hhs = JsNumber.toString(hh)
        val mms = JsNumber.toString(mm)
        return if (pt) {
            "Pela regra de Naismith: ${fmt(d, pt)} km ÷ 5 km/h + ${fmt(a, pt, 0)} m ÷ 600 m/h = ${fmt(h, pt, 1)} h (cerca de $hhs h $mms min), sem contar paradas."
        } else {
            "By Naismith's rule: ${fmt(d, pt)} km ÷ 5 km/h + ${fmt(a, pt, 0)} m ÷ 600 m/h = ${fmt(h, pt, 1)} h (about $hhs h $mms min), not counting stops."
        }
    }

    // (c) Currency with the rate the question gives, and an optional tip.
    private val RATE = JsRegex.re(
        """\b1\s*(?:u\.?s\.?\s+)?(d[óo]lar(?:es)?(?: americanos?)?|us\${'$'}|usd|euros?|eur|€|libras?|gbp|reais|real|brl|r\${'$'}|dollars?(?: us)?)\s*(?:vale|valem|=|equals|is worth|is|custa)\s*$NUM\s*([\p{L}${'$'}€£]+(?: [\p{L}]+)?)?""",
        "iu",
    )
    private val AMOUNT = JsRegex.re("""$NUM\s*(?!%)(?!\s*%)([\p{L}${'$'}€£]+)?""", "iu")
    private val PERCENT = JsRegex.re("""$NUM\s*%""")
    private val TIP = JsRegex.re("""gorjeta|tip|servi[çc]o|gratuity""", "i")
    private val ES_END = JsRegex.re("""es$""", "i")
    private val SPACE_DOT = JsRegex.re("""\s+\.""")
    private val SPACES_2 = JsRegex.re("""\s{2,}""")

    private fun currency(q: String, pt: Boolean, rd: Boolean): String? {
        val rate = RATE.find(q) ?: return null
        val r = parseNumber(rate.groupValues[2], rd)
        val rest = q.replaceFirst(rate.value, " ")
        val amountM = AMOUNT.find(PERCENT.replace(rest, " "))
        if (amountM == null || !(r > 0)) return null
        val amount = parseNumber(amountM.groupValues[1], rd)
        val unitFrom = Js.trim(amountM.groups[2]?.value ?: rate.groups[3]?.value ?: "")
        val cur = rate.groupValues[1]
        val unitTo = if (ES_END.replaceFirst(cur, "").lowercase().startsWith("d")) (if (pt) "dólares" else "US dollars") else cur
        val converted = amount / r
        var text = "${fmt(amount, pt)} $unitFrom ÷ ${fmt(r, pt)} = ${fmt(converted, pt)} $unitTo."
        val tipM = PERCENT.find(q)
        if (tipM != null && TIP.containsMatchIn(q)) {
            val t = parseNumber(tipM.groupValues[1], rd)
            val total = converted * (1 + t / 100)
            text += if (pt) {
                " Com ${fmt(t, pt)}% de gorjeta: ${fmt(total, pt)} $unitTo (gorjeta de ${fmt(converted * (t / 100), pt)})."
            } else {
                " With a ${fmt(t, pt)}% tip: ${fmt(total, pt)} $unitTo (tip ${fmt(converted * (t / 100), pt)})."
            }
        }
        return SPACES_2.replace(SPACE_DOT.replace(text, "."), " ")
    }

    // (d) Battery energy: Wh = mAh × V / 1000. With flight words, one neutral line instead of a verdict (adam, Nov 14; t9
    // 0143): the limits are set by airlines and IATA and they change (a power bank above 100 Wh is "Forbidden" in IATA's
    // 2026 guidance, and the 100-160 Wh approval goes from the DGR in 2027); an offline app can't know the rule of the day.
    private val MAH = JsRegex.re("""$NUM\s*mah\b""", "i")
    private val VOLTS = JsRegex.re("""$NUM\s*v\b""", "i")
    private val FLIGHT = JsRegex.re("""avi[ãa]o|voo|voar|bagagem|companhia a[ée]rea|plane|flight|fly|airline|carry[- ]on|luggage""", "i")

    const val FLIGHT_NOTE_EN = " Airlines set their battery limits in Wh, and the rules change: check your airline before you fly."
    const val FLIGHT_NOTE_PT = " As companhias aéreas definem os limites de bateria em Wh, e as regras mudam: confira com a sua antes de voar."

    private fun battery(q: String, pt: Boolean, rd: Boolean): String? {
        val mah = MAH.find(q) ?: return null
        val v = VOLTS.find(q) ?: return null
        val capacity = wholeNumber(mah.groupValues[1]) ?: parseNumber(mah.groupValues[1], rd)
        val wh = (capacity * parseNumber(v.groupValues[1], rd)) / 1000
        var text = "${fmt(capacity, pt, 0)} mAh × ${fmt(parseNumber(v.groupValues[1], rd), pt)} V ÷ 1000 = ${fmt(wh, pt)} Wh."
        if (FLIGHT.containsMatchIn(q)) text += if (pt) FLIGHT_NOTE_PT else FLIGHT_NOTE_EN
        return text
    }

    private val WORD_NUMBERS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12,
        "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "três" to 3, "tres" to 3, "quatro" to 4, "cinco" to 5, "seis" to 6, "sete" to 7,
        "oito" to 8, "nove" to 9, "dez" to 10, "onze" to 11, "doze" to 12,
    )
    private const val COUNT =
        """(\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|um|uma|dois|duas|três|tres|quatro|cinco|seis|sete|oito|nove|dez|onze|doze)"""

    private fun count(w: String, rd: Boolean): Double = WORD_NUMBERS[w.lowercase()]?.toDouble() ?: parseNumber(w, rd)

    // (f) Water for a trip: people × days × litres per person per day; 1 L of water weighs about 1 kg.
    private val WATER = JsRegex.re("""(?<![\p{L}])(water|[áa]gua)(?![\p{L}])""", "iu")
    private val PER_DAY = JsRegex.re("""$NUM\s*(?:l|litros?|liters?|litres?)\b[^.?!]{0,30}?(?:por dia|per day|a day|/dia|/day)""", "i")
    private val PEOPLE = JsRegex.re("""\b(?:somos|we are|we're)\s+$COUNT\b|\b$COUNT\s+(?:of us|people|persons|pessoas|adultos|adults|hikers)\b""", "i")
    private val DAYS = JsRegex.re("""\b$COUNT[\s-]+(?:days?|dias?)\b""", "i")

    private fun water(q: String, pt: Boolean, rd: Boolean): String? {
        if (!WATER.containsMatchIn(q)) return null
        val perDay = PER_DAY.find(q)
        val people = PEOPLE.find(q)
        val days = DAYS.find(q)
        if (perDay == null || people == null || days == null) return null
        val l = parseNumber(perDay.groupValues[1], rd)
        val p = count(people.groups[1]?.value ?: people.groups[2]!!.value, rd)
        val d = count(days.groupValues[1], rd)
        if (!(l > 0 && p > 0 && d > 0)) return null
        val total = l * p * d
        return if (pt) {
            "${fmt(p, pt, 0)} pessoas × ${fmt(d, pt, 0)} dias × ${fmt(l, pt)} L = ${fmt(total, pt)} litros de água, que pesam cerca de ${fmt(total, pt)} kg (1 L de água ≈ 1 kg)."
        } else {
            "${fmt(p, pt, 0)} people × ${fmt(d, pt, 0)} days × ${fmt(l, pt)} L = ${fmt(total, pt)} liters of water, weighing about ${fmt(total, pt)} kg (1 L of water ≈ 1 kg)."
        }
    }

    // from context.ts; replace with the shared port when context.ts lands
    private val TEMPERATURE = JsRegex.re(
        """(?<!\d)(-?\d+(?:[.,]\d+)?)\s*(?:°|º|degrees?|graus?)?\s*(c|celsius|centigrade|f|fahrenheit)\b[^?]*?\b(?:in|to|into|em|para)\s+(?:degrees?\s+|graus?\s+)?(celsius|centigrade|fahrenheit|c|f)\b""",
        "i",
    )

    // from context.ts; replace with the shared port when context.ts lands
    /** "What is 30 °C in Fahrenheit?": the conversion with its formula, or null. */
    private fun temperatureConversion(query: String, pt: Boolean): String? {
        val m = TEMPERATURE.find(query) ?: return null
        val value = JsNumber.parse(m.groupValues[1].replaceFirst(",", "."))
        val from = if (m.groupValues[2][0].lowercaseChar() == 'f') "F" else "C"
        val to = if (m.groupValues[3][0].lowercaseChar() == 'f') "F" else "C"
        if (from == to || !value.isFinite()) return null
        val result = if (from == "C") (value * 9) / 5 + 32 else ((value - 32) * 5) / 9
        fun f(n: Double) = JsNumber.locale(JsNumber.round(n * 10) / 10, pt)
        val formula = if (from == "C") "°F = °C × 9/5 + 32" else "°C = (°F − 32) × 5/9"
        return "${f(value)} °$from = ${f(result)} °$to ($formula)."
    }

    // (e) A temperature, plus the heat warning when the question asks if it's dangerous (or it clearly is).
    private val CELSIUS = JsRegex.re("""(-?[\d.,]+)\s*°C""")
    private val HAS_C = JsRegex.re("°C")
    private val ASKS_SAFE = JsRegex.re("""perig|segur|caminh|trilha|correr|exerc|danger|safe|hike|hiking|walk|run|exercise""", "i")

    private fun temperature(q: String, pt: Boolean, rd: Boolean): String? {
        val base = temperatureConversion(q, pt) ?: return null
        val c = CELSIUS.find(base.split("=").map(Js::trim).firstOrNull { HAS_C.containsMatchIn(it) } ?: "")
        val celsius = if (c != null) parseNumber(c.groupValues[1], rd) else Double.NaN
        val asks = ASKS_SAFE.containsMatchIn(q)
        if (!celsius.isFinite() || !(asks && celsius >= 32)) return base
        val note = if (celsius >= 40) {
            if (pt) {
                " Sim, é perigoso: a 40 °C ou mais o esforço físico traz alto risco de exaustão pelo calor e insolação. Se precisar sair, evite as horas mais quentes, beba água com frequência e descanse na sombra."
            } else {
                " Yes, it is dangerous: at 40 °C or more, physical effort carries a high risk of heat exhaustion and heatstroke. If you must go, avoid the hottest hours, drink water often and rest in the shade."
            }
        } else if (pt) {
            " Calor forte: acima de 32-35 °C o risco de exaustão pelo calor sobe com esforço físico. Evite as horas mais quentes, beba água e faça pausas na sombra."
        } else {
            " Strong heat: above 32-35 °C the risk of heat exhaustion rises with effort. Avoid the hottest hours, drink water and take breaks in the shade."
        }
        return base + note
    }

    /**
     * The exact answer to an arithmetic question, or null. Order: the most specific first. A plain unit conversion
     * ("26 miles in km", UnitConversion) is the last try, after the temperature rule (adam, Nov 13).
     */
    fun calculate(query: String, pt: Boolean): Calculation? {
        // The answer's language: the detector, or the rules' own Portuguese words. The numbers: the question's own mark first.
        val ptText = pt || PT_WORDS.containsMatchIn(query)
        val rd = readsComma(query, ptText)
        val tries: List<Pair<String, (String, Boolean, Boolean) -> String?>> = listOf(
            "battery" to ::battery,
            "fuel-economy" to ::fuelEconomy,
            "naismith" to ::naismith,
            "currency" to ::currency,
            "water" to ::water,
            "temperature" to ::temperature,
            "conversion" to { q, p, _ -> UnitConversion.convert(q, p) },
        )
        for ((kind, fn) in tries) {
            val text = fn(query, ptText, rd)
            if (!text.isNullOrEmpty()) return Calculation(kind, text)
        }
        return null
    }
}
