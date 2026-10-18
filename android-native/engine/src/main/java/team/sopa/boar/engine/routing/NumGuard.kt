package team.sopa.boar.engine.routing

/**
 * Ported from r4to's src/routing/numguard.ts (PR #85, BOAR R&D, MIT).
 *
 * NUMGUARD: an English, single-topic numeric question that is a pure unit conversion, a clock calculation or an exact
 * calculator match may be answered by a deterministic calculator; anything that names a body, person/age, drug, dose,
 * symptom, injury, danger, alcohol or ingestion word, a body-range temperature, or that is not English, in this message
 * or (for the lexicon) in any earlier user turn, is not. The regexes are the TypeScript ones verbatim, through JsRegex.
 *
 * Not ported: conditionCards.ts isCardEmergency (one first-aid condition + what-to-do + a symptom; it needs the
 * condition terms and coHazard.ts). Every probe in numguard.test.ts gives the same result without it (NumGuardTest).
 */
object NumGuard {
    // A short explicit list on top of Arithmetic's HEALTH and DANGER: people, care settings and symptoms they don't name.
    private val PERSON_CARE = JsRegex.re(
        """\b(?:pregnan\w*|child(?:ren)?|kids?|son|daughter|sick|ill|illness|hospital|doctor|nurse|vomit\w*|diarrh\w*|rash|cough\w*|headaches?|ache\w*|dizz\w*|nause\w*|thermometer|body temperature|my temperature|weigh(?:t|s)? loss|bmi|alcohol|drunk|safe(?:ly)?|hurt\w*|wound\w*|burn\w*|cpr|(?:my|his|her|their|your|our) (?:temp|temperature)|\d+[\s-]*(?:year|month|week|day)s?[\s-]*old|paracetamol|acetaminophen|ibuprofen|aspirin|tylenol|advil|benadryl|antibiotic\w*|amoxicillin|units? of|urin\w*|pee\w*|poop\w*|stool|toxic\w*|bleach|ingest\w*|swallow\w*|drank|drink\w*|beers?|wine|vodka|bac|sober|bp|blood pressure|saturation|unconscious|wake up|survive|surviv\w*|without (?:food|water)|fell|fall(?:s|en)?|hit (?:his|her|my|their)? ?head|faint\w*|\w+'s (?:core )?temp\w*|core temp\w*|boy|girl|husband|wife|partner|mom|mum|mother|dad|father|grand\w*|friend|patient|newborn|stranded|chest|passed out|water broke)\b""",
        "i",
    )
    // A body temperature (30-45 °C, 86-113 °F) is a person's, whatever the wording.
    private val BODY_TEMP = JsRegex.re(
        """\b(?:3\d|4[0-5])(?:[.,]\d+)?\s?(?:°\s?|degrees? )?c(?:elsius)?\b|\b(?:8[6-9]|9\d|10\d|11[0-3])(?:[.,]\d+)?\s?(?:°\s?|degrees? )?f(?:ahrenheit)?\b""",
        "i",
    )
    // Accented Latin letters (not × ÷): not an English message.
    private val NON_EN = JsRegex.re("[À-ÖØ-öø-ɏ]")

    private const val NUM = """\d[\d,]*(?:\.\d+)?"""
    // A number followed by a unit, a currency or a percent.
    private val UNIT = JsRegex.re(
        """(?:[${'$'}€£¥]\s?$NUM|$NUM\s?(?:%|°\s?[cf]?\b|degrees?\b|(?:k?m|cm|mm|km\/h|kph|mph|mi|miles?|ft|feet|foot|inch(?:es)?|yards?|metres?|meters?|kilomet(?:re|er)s?|kg|g|lbs?|pounds?|ounces?|oz|tons?|tonnes?|l|litres?|liters?|gallons?|gal|pints?|cups?|bar|psi|kpa|hpa|mah|wh|kwh|w|watts?|v|volts?|amps?|hz|ghz|mhz|gb|mb|kb|tb|tokens?|bytes?|bits?|sec(?:ond)?s?|min(?:ute)?s?|h|hrs?|hours?|days?|weeks?|dollars?|euros?|usd|eur|gbp|cents?|btc|eth|sats?|gwei|coins?|million|billion|thousand)\b))""",
        "i",
    )
    private val CLOCK = JsRegex.re("""\b\d{1,2}(?::\d{2}\s?(?:am|pm)?|\s?(?:am|pm))\b""", "i")
    // A conversion target ("in psi", "to Celsius", "convert") or a clock question: the shape of a misfire, not of a situation.
    private val CONVERT = JsRegex.re(
        """\b(?:convert(?:s|ed|ing)?|(?:in|to|into) (?:fahrenheit|celsius|kelvin|psi|bar|kpa|hpa|mph|km\/h|kph|feet|ft|inches|cm|mm|metres|meters|miles|kilomet(?:re|er)s|yards|pounds|lbs|kg|kilograms|grams|ounces|oz|litres|liters|gallons|pints|cups|ml|watt-hours|wh|kwh|euros?|dollars?|usd|eur|gbp))\b""",
        "i",
    )
    private val HOW_MANY_UNIT = JsRegex.re(
        """\bhow many (?:kilomet(?:re|er)s|km|miles|feet|ft|metres|meters|inches|cm|yards|pounds|lbs|kg|kilograms|grams|ounces|oz|litres|liters|gallons|pints|cups|psi|watt-hours|wh|kwh) (?:is|are|in|make)\b""",
        "i",
    )
    private val WHEN = JsRegex.re("""\b(?:what time|when (?:does|do|will|did)|arrive|arrives)\b""", "i")
    private val IDENTIFIER = JsRegex.re("""\b[A-Za-z]+-\d+\b""", "g")
    private val YEAR = JsRegex.re("""\b(?:1[5-9]\d\d|20\d\d)s?\b""", "g")
    private val DIGIT = JsRegex.re("""\d""")

    /** True when the question names a health, injury, danger or person-care word: the health path's call stands. */
    fun healthLexicon(query: String): Boolean =
        Arithmetic.HEALTH.containsMatchIn(query) || Arithmetic.DANGER.containsMatchIn(query) || PERSON_CARE.containsMatchIn(query) ||
            QueryContext.isHealthQuestion(query)

    /** A body-range temperature (30-45 °C, 86-113 °F): a person's. */
    fun bodyTemperature(query: String): Boolean = BODY_TEMP.containsMatchIn(query)

    /** A number with a unit plus a conversion target ("in psi", "to Celsius", "how many feet is"), or a clock time plus a clock question. */
    fun numericIntent(query: String): Boolean {
        // Identifiers ("EIP-4444", "DS-160") and years or decades ("in 1900", "the 1600s", "mid-2012") are not quantities.
        val q = YEAR.replace(IDENTIFIER.replace(query, " "), " ")
        if (!DIGIT.containsMatchIn(q)) return false
        return (UNIT.containsMatchIn(q) && (CONVERT.containsMatchIn(q) || HOW_MANY_UNIT.containsMatchIn(q))) || (CLOCK.containsMatchIn(q) && WHEN.containsMatchIn(q))
    }

    /**
     * The guard: English (not PT/ES, no accented letters), no body-range temperature, no health lexicon in this or any
     * earlier user turn, AND (a calculator match OR numeric intent).
     */
    fun numericGuard(query: String, pt: Boolean, userTurns: List<String> = emptyList()): Boolean {
        if (pt || QueryContext.isSpanishQuestion(query) || NON_EN.containsMatchIn(query) || bodyTemperature(query)) return false
        if ((listOf(query) + userTurns).any(::healthLexicon)) return false
        return Calculators.calculate(query, pt) != null || numericIntent(query)
    }
}
