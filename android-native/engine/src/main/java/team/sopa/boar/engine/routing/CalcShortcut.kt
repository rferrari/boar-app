package team.sopa.boar.engine.routing

/**
 * The calculator shortcuts of r4to's src/routing/answer.ts (PR #85, BOAR R&D, MIT), in its order: the exact calculators
 * (Calculators: battery, fuel economy, Naismith, currency, water, temperature; EN and PT), then the word problems
 * (Arithmetic: rate × quantity -> time, ceil-division; EN only). Travel distance is its own lab, Boar Distance (TravelDistance).
 *
 * answer.ts skips them when its learned emergency detector fires, over this turn or the earlier user turns. BOAR native has
 * no such detector, so NumGuard's health lexicon stands in for it: a turn, or an earlier user turn, naming a health,
 * injury, danger or person word never gets an instant answer, and the model answers it as before. Pure: text in, answer out.
 */
object CalcShortcut {
    /** [code]: the receipt code answer.ts gives ("calculator-battery", "temperature-conversion", "calculator-rate-time"). */
    data class Hit(val code: String, val text: String)

    fun answer(query: String, userTurns: List<String> = emptyList()): Hit? {
        if ((listOf(query) + userTurns).any(NumGuard::healthLexicon)) return null
        val pt = QueryContext.isPortugueseQuestion(query)
        Calculators.calculate(query, pt)?.let { return Hit(if (it.kind == "temperature") "temperature-conversion" else "calculator-${it.kind}", it.text) }
        if (pt || QueryContext.isSpanishQuestion(query)) return null
        return Arithmetic.answer(query)?.let { Hit("calculator-${it.kind}", it.text) }
    }
}
