package team.sopa.boar.engine.answer

import team.sopa.boar.engine.rag.RetrievalParams

/**
 * The Personal Files answer style ("personal"): answers only from the user's own files (Knowledge → Personal Files).
 * The one place the chat (ChatViewModel) and the device runner (DeviceEvalRunner) decide what the style changes, so a
 * benchmark run answers as the chat does. The owner's choices (Oct 21): nothing found means the fixed line, never an
 * answer from memory; Labs tools step aside except the safety cards; no Deep Research, adaptive routing or answer check.
 */
object PersonalFiles {
    const val STYLE = "personal"

    /** The answer when nothing in the user's files matched (the chat shows its own language's line). */
    const val NOT_FOUND = "I couldn't find this in your Personal Files."

    /** The same line in Portuguese (t9 0420), as the chat's own native.personal.notFound says it. */
    const val NOT_FOUND_PT = "Não encontrei isso nos seus Arquivos locais."

    fun isOn(style: String?): Boolean = style == STYLE

    /** The question asks for a summary (t9 2330 a): "summarize", "summary", "overview", "resuma", "resumo", "em resumo". */
    private val SUMMARY = Regex("""(?<![\p{L}])(?:summari[sz]e|summary|overview|sum up|resum[aeo]|resumir|em resumo|sintetize|panorama)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    fun asksSummary(question: String): Boolean = SUMMARY.containsMatchIn(question)

    /**
     * What a summary of the user's file asks of the model (t9 2330 a): its own words, never the file copied (the F3:
     * "summarize" pasted the blood test, table and all), the file's numbers as written, and each result with the mark the
     * code gave it (LabRanges), never its own comparison.
     */
    const val SUMMARY_EN = "This is a request for a summary: write 3 to 5 sentences in your own words; never copy the file or its table. " +
        "Give the file's numbers as it writes them. For each result that has a mark (WITHIN, ABOVE or BELOW its range), say that mark exactly; never compare numbers yourself."
    const val SUMMARY_PT = "Isto é um pedido de resumo: escreva de 3 a 5 frases com suas palavras; nunca copie o arquivo ou a tabela. " +
        "Dê os números como o arquivo escreve. Para cada resultado com marcação (DENTRO, ACIMA ou ABAIXO da faixa), diga essa marcação exatamente; nunca compare números você mesmo."

    /** A Labs tool's answer is kept only from a safety card (`safety-*`), as under Deep Research. */
    fun keepsTool(toolId: String): Boolean = toolId.startsWith("safety-")

    /** [base] (the user's or the run's ranking) kept to the user's own files. */
    fun retrieval(base: RetrievalParams = RetrievalParams.DEFAULT): RetrievalParams = base.copy(ownOnly = true)

    /**
     * [base] as this style sends it: whole parts within [contextSize] (the context the model was opened with), the
     * files-only instruction, [notFound] written without the model when nothing is found, and [filesNote], the list of
     * the user's switched-on files (OwnFiles.note).
     */
    fun request(base: AnswerRequest, contextSize: Int, notFound: String = NOT_FOUND, filesNote: String? = null): AnswerRequest =
        base.copy(filesOnly = true, contextSize = contextSize, notFoundText = notFound, filesNote = filesNote)

    /** An answer this long after the not-found line is a real answer, not a sign-off. */
    const val ANSWER_AFTER_HEDGE_CHARS = 40
    private val LEAD = Regex("^(however|but|that said|still|nevertheless|por[ée]m|mas|no entanto|ainda assim|contudo|entretanto)[,:]?\\s+", RegexOption.IGNORE_CASE)
    private val CITATION = Regex("\\[\\d+]")

    /**
     * A small model sometimes opens with [NOT_FOUND] "to be safe" and then answers from the files anyway ("I couldn't find
     * this in your Personal Files. According to your blood test…", F3, Oct 22): one or the other, never both. The line goes
     * only when what follows cites the user's files ([1], [2]…). Without a citation the rest may come from memory, so the
     * line stays as the honest signal; so does a line on its own or with a short sign-off.
     */
    fun dropHedge(answer: String): String {
        val t = answer.trimStart()
        val hedge = listOf(NOT_FOUND, NOT_FOUND_PT).firstOrNull { t.startsWith(it) } ?: return answer
        val rest = t.removePrefix(hedge).trimStart()
        if (rest.length < ANSWER_AFTER_HEDGE_CHARS || !CITATION.containsMatchIn(rest)) return answer
        val body = rest.replaceFirst(LEAD, "")
        return body.replaceFirstChar { it.uppercaseChar() }
    }
}
