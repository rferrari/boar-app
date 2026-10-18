package team.sopa.boar.engine.routing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ported from src/routing/travelDistance.ts in r4to's PR #85 (BOAR R&D, MIT).
 *
 * Travel distance between two cities, computed offline (Anvil, 2026-10-03; V-exact "How far is it to walk from Paris to
 * Rome and back?": the 4B answered "~1,200 km… not feasible", judge 0.29). English only.
 *
 * Both places must resolve in the city table (worldCities.v1.json: GeoNames cities15000, the 6,000 most populous cities,
 * CC BY 4.0), passed in as its JSON text; otherwise null and the question takes the normal path. The answer is always
 * labelled an estimate: great-circle distance from coordinates, times a stated route factor for walking, cycling or
 * driving. It never claims a real road or trail distance. The regexes are the TypeScript ones verbatim, through JsRegex.
 */
class TravelDistance(json: String) {
    data class City(val name: String, val country: String, val lat: Double, val lon: Double, val pop: Double)

    /** [mode]: "walk", "cycle", "drive" or null. */
    data class Answer(val text: String, val from: String, val to: String, val straightKm: Double, val mode: String?, val roundTrip: Boolean)

    private val index: Map<String, City> = HashMap<String, City>().also { index ->
        // Rows are sorted by population, so the first city under a name is the most populous one ("Paris" = Paris, France).
        for (row in Json.parseToJsonElement(json).jsonObject["cities"]!!.jsonArray) {
            val r = row.jsonArray
            val name = r[0].jsonPrimitive.content
            val city = City(name, r[2].jsonPrimitive.content, r[3].jsonPrimitive.double, r[4].jsonPrimitive.double, r[5].jsonPrimitive.double)
            for (k in listOf(name, r[1].jsonPrimitive.content).filter { it.isNotEmpty() }) index.putIfAbsent(k.lowercase(), city)
        }
    }

    /** A place phrase as the question writes it -> a city in the table, or null. Accepts a trailing ", Country" or "in Country". */
    fun lookupCity(phrase: String): City? {
        val s = TRAIL.replace(LEAD.replace(phrase.trim(), ""), "")
        val bare = COUNTRY_TAIL.replace(s, "").trim()
        return index[s.lowercase()] ?: index[bare.lowercase()]
    }

    fun answer(query: String): Answer? {
        PATTERNS.forEachIndexed { i, re ->
            val m = re.find(query) ?: return@forEachIndexed
            fun g(n: Int) = m.groups[n]?.value
            var verb: String? = null
            var by: String? = null
            val a: String
            val b: String
            var back: String? = null
            when (i) {
                // As the TypeScript reads it: the second "by" after the round trip is not used.
                0 -> { verb = g(1); by = g(2) ?: g(5); a = g(3)!!; b = g(4)!!; back = g(6) }
                1 -> { verb = g(1); a = g(2)!!; b = g(3)!!; back = g(4) }
                2 -> { b = g(1)!!; a = g(2)!!; by = g(3) } // "how far is X from Y": from Y to X
                else -> { a = g(1)!!; b = g(2)!!; back = g(3) }
            }
            val from = lookupCity(a)
            val to = lookupCity(b)
            if (from == null || to == null || from === to) return null
            val km = greatCircleKm(from.lat, from.lon, to.lat, to.lon)
            if (km < 1) return null
            val modeKey = (verb ?: (if (by == "foot") "walk" else by))?.lowercase()
            val mode = modeKey?.let { VERB_MODE[it] }
            val roundTrip = !back.isNullOrEmpty()
            return Answer(compose(from, to, km, mode, roundTrip, asksTime = i == 1), from.name, to.name, km, mode, roundTrip)
        }
        return null
    }

    companion object {
        /** Route factor (route length / straight line) and pace per mode; stated in the answer, never presented as a measured route. */
        private class Pace(val factor: Double, val perDayKm: Double, val kmh: Double, val verb: String)

        private val MODE = mapOf(
            "walk" to Pace(1.3, 25.0, 5.0, "walking"),
            "cycle" to Pace(1.25, 80.0, 18.0, "cycling"),
            "drive" to Pace(1.25, 0.0, 80.0, "driving"),
        )

        private val VERB_MODE = mapOf(
            "walk" to "walk", "walking" to "walk", "hike" to "walk", "hiking" to "walk", "trek" to "walk", "run" to "walk", "running" to "walk",
            "cycle" to "cycle", "cycling" to "cycle", "bike" to "cycle", "biking" to "cycle", "ride" to "cycle", "bicycle" to "cycle",
            "drive" to "drive", "driving" to "drive", "car" to "drive",
        )

        private val LEAD = JsRegex.re("""^(?:the city of|downtown|central)\s+""", "i")
        private val TRAIL = JsRegex.re("""[\s.?!,;:]+$""", "g")
        private val COUNTRY_TAIL = JsRegex.re("""\s*(?:,|\bin\b)\s*[\p{L} .'-]+$""", "u")

        fun greatCircleKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
            fun r(d: Double) = d * Math.PI / 180
            val dLat = r(bLat - aLat)
            val dLon = r(bLon - aLon)
            val h = sin(dLat / 2).let { it * it } + cos(r(aLat)) * cos(r(bLat)) * sin(dLon / 2).let { it * it }
            return 2 * 6371 * asin(sqrt(h))
        }

        private const val PLACE = """([\p{L}][\p{L} .,'-]{0,40}?)"""
        private val MODE_WORDS = VERB_MODE.keys.joinToString("|")
        private const val BACK = """(\s+and\s+back|\s+and\s+return|\s+round[- ]trip|\s+return\s+trip|\s+there\s+and\s+back)?"""
        private const val BY = """(?:\s+(?:by|on)\s+(car|bike|bicycle|foot))?"""
        private val PATTERNS = listOf(
            // "How far is it to walk from Paris to Rome and back?", "how many km to cycle from Lyon to Nice?"
            """^\s*how\s+(?:far|many\s+(?:km|kilometers|kilometres|miles))\b(?:\s+(?:is\s+it|is|would\s+it\s+be|will\s+it\s+be))?(?:\s+to\s+($MODE_WORDS))?(?:\s+by\s+(car|bike|bicycle|foot))?\s+from\s+$PLACE\s+to\s+$PLACE$BY$BACK$BY\s*\??\s*$""",
            // "How long would it take to walk from Berlin to Prague?", "how many days to bike from Vienna to Budapest and back"
            """^\s*how\s+(?:long|many\s+(?:days|hours|weeks))\b(?:\s+(?:does|would|will)\s+it\s+take(?:\s+me)?)?\s+to\s+($MODE_WORDS)\s+from\s+$PLACE\s+to\s+$PLACE$BACK\s*\??\s*$""",
            // "How far is Rome from Paris?", "how far away is Kyoto from Tokyo by car"
            """^\s*how\s+far(?:\s+away)?\s+is\s+$PLACE\s+from\s+$PLACE$BY\s*\??\s*$""",
            // "What's the distance between Madrid and Barcelona?", "distance from Oslo to Bergen"
            """^\s*(?:what(?:'s|\s+is)\s+the\s+)?distance\s+(?:between|from)\s+$PLACE\s+(?:and|to)\s+$PLACE$BACK\s*\??\s*$""",
        ).map { JsRegex.re(it, "iu") }

        private fun fmt(n: Double) = JsNumber.locale(JsNumber.round(n), false)
        private fun round10(n: Double) = JsNumber.round(n / 10) * 10
        private fun round100(n: Double) = JsNumber.round(n / 100) * 100
        /** Number.prototype.toFixed: the exact binary value, rounded half up. */
        private fun toFixed(n: Double, digits: Int) = BigDecimal(n).setScale(digits, RoundingMode.HALF_UP).toPlainString()
        private fun cap(s: String) = s.substring(0, 1).uppercase() + s.substring(1)

        private fun compose(from: City, to: City, km: Double, mode: String?, roundTrip: Boolean, asksTime: Boolean): String {
            val line = "${from.name} and ${to.name} are about ${fmt(km)} km (${fmt(km * 0.621)} miles) apart in a straight line."
            val caveat = "This is an estimate from the cities' coordinates, not a mapped route; the real route can differ."
            if (mode == null) {
                val road = km * 1.25
                val total = if (roundTrip) " Twice that for the round trip: about ${fmt(round10(2 * km))} km in a straight line." else ""
                return "$line$total Roads and trails are usually longer than the straight line, by about a quarter, so expect roughly " +
                    "${fmt(round10(road))} km${if (roundTrip) " each way (${fmt(round100(2 * road))} km there and back)" else ""} by land. $caveat"
            }
            val p = MODE.getValue(mode)
            val oneWay = km * p.factor
            val hours = oneWay / p.kmh
            val route = "$line ${cap(p.verb)} routes are usually about ${JsNumber.locale(JsNumber.round((p.factor - 1) * 100), false)}% longer than that, " +
                "so plan on roughly ${fmt(round10(oneWay))} km (${fmt(round10(oneWay * 0.621))} miles) each way" +
                (if (roundTrip) ", about ${fmt(round100(2 * oneWay))} km there and back." else ".")
            val time = if (mode == "drive") {
                "about ${if (hours < 10) toFixed(hours, 1) else fmt(hours)} hours of driving each way at an average ${fmt(p.kmh)} km/h" +
                    (if (roundTrip) " (about ${fmt(2 * hours)} hours in total)" else "") + ", before stops"
            } else {
                "about ${fmt(oneWay / p.perDayKm)} days each way at a steady ${fmt(p.perDayKm)} km a day" +
                    (if (roundTrip) ", or around ${toFixed(2 * oneWay / p.perDayKm / 30, 1)} months for the round trip without rest days" else "") +
                    " (about ${fmt(hours)} hours of ${p.verb} each way at ${fmt(p.kmh)} km/h)"
            }
            val head = if (roundTrip) "Roughly ${fmt(round100(2 * oneWay))} km there and back (an estimate)." else "Roughly ${fmt(round10(oneWay))} km (an estimate)."
            return if (asksTime) "${cap(time)} (an estimate). $route $caveat" else "$head $route That is $time. $caveat"
        }
    }
}
