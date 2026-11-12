package team.sopa.boar.engine.routing

import java.util.Locale

/**
 * Miles and kilometres in one rate-and-time question (adam, Nov 12): "120 miles at 60 mph, answer in km" or "200 km at
 * 50 mph". The distance is converted to the rate's unit before the rate × time rule reads it, and the answer says the
 * conversion. Without a mix, nothing changes.
 */
object UnitBridge {
    const val KM_PER_MILE = 1.609344

    data class Prepared(val query: String, val note: String?)

    private val KM = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:km|kms|kilomet(?:er|re)s?)\\b", RegexOption.IGNORE_CASE)
    private val MILES = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:mi|miles?)\\b(?!\\s*(?:per|an?)\\s*hour)", RegexOption.IGNORE_CASE)
    private val MPH = Regex("\\b(?:mph|miles?\\s+(?:per|an?)\\s+hour)\\b", RegexOption.IGNORE_CASE)
    private val KMH = Regex("\\b(?:km/h|kmh|kph|kilomet(?:er|re)s?\\s+(?:per|an?)\\s+hour)\\b", RegexOption.IGNORE_CASE)
    private val WANTS_KM = Regex("\\b(?:in|into|to)\\s+(?:km|kilomet(?:er|re)s)\\b|\\banswer\\s+in\\s+km\\b", RegexOption.IGNORE_CASE)
    private val WANTS_MILES = Regex("\\b(?:in|into|to)\\s+miles\\b", RegexOption.IGNORE_CASE)

    fun prepare(query: String): Prepared {
        val mph = MPH.containsMatchIn(query)
        val kmh = KMH.containsMatchIn(query)
        if (mph == kmh) return Prepared(query, null)
        // The words asking for the other unit aren't a distance: "in km" left in the question would read as one.
        var q = query.replace(WANTS_KM, " ").replace(WANTS_MILES, " ")
        val notes = ArrayList<String>()
        if (mph) {
            KM.find(q)?.let { m ->
                val km = m.groupValues[1].toDouble(); val mi = km / KM_PER_MILE
                q = q.replaceRange(m.range, "${exact(mi)} miles"); notes += "${fmt(km)} km = ${fmt(mi)} miles"
            }
            if (notes.isEmpty() && WANTS_KM.containsMatchIn(query)) MILES.find(q)?.let { m ->
                val mi = m.groupValues[1].toDouble(); notes += "${fmt(mi)} miles = ${fmt(mi * KM_PER_MILE)} km"
            }
        } else {
            MILES.find(q)?.let { m ->
                val mi = m.groupValues[1].toDouble(); val km = mi * KM_PER_MILE
                q = q.replaceRange(m.range, "${exact(km)} km"); notes += "${fmt(mi)} miles = ${fmt(km)} km"
            }
        }
        return Prepared(q.replace(Regex("\\s{2,}"), " ").trim(), notes.firstOrNull())
    }

    /** For the sum itself: full precision, so 0.02 miles isn't "0 km" (CodeRabbit); [fmt] is only for the note. */
    private fun exact(x: Double): String = if (kotlin.math.abs(x - Math.rint(x)) < 1e-9) Math.rint(x).toLong().toString()
        else java.math.BigDecimal(x).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString()

    private fun fmt(x: Double): String = if (kotlin.math.abs(x - Math.rint(x)) < 0.05) Math.rint(x).toLong().toString() else String.format(Locale.US, "%.1f", x)
}
