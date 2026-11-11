package team.sopa.boar.engine.labs

/**
 * Which plugin answers Designer may illustrate (adam, Nov 12): the model's own answers as before, and now the
 * Calculator's and Facts' instant answers. Every other tool answer stays out: safety (Cohazard), Distance, Polyglot,
 * and the plugins that read the phone. A Facts answer comes from its table's templates (population, area, capital,
 * height…), which hold no warning; a text that reads like one is refused anyway, as a second lock.
 */
object IllustrateGate {
    private val DRAWABLE_TOOLS = listOf("calculator-", "temperature-conversion", "facts-")
    private const val NOT_DRAWABLE = "calculator-travel-distance"
    private val WARNING = Regex(
        "⚠|\\b(warning|danger|dangerous|hazard|deadly|poison|toxic|never|do not|don't|unsafe|emergency|" +
            "aviso|perigo|perigoso|t[oó]xico|veneno|nunca|n[aã]o use|n[aã]o fa[cç]a|emerg[eê]ncia|mon[oó]xido|carbon monoxide)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun allowed(tool: String?, text: String): Boolean {
        if (tool == null) return true
        // A repaired answer carries "+guard…": the model's answer with a fix, not a tool's; the tool part decides.
        val first = tool.substringBefore('+')
        if (first.startsWith(NOT_DRAWABLE) || first.startsWith("safety-")) return false
        if (DRAWABLE_TOOLS.none { first.startsWith(it) }) return false
        return !WARNING.containsMatchIn(text)
    }
}
