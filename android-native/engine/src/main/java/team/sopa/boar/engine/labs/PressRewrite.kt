package team.sopa.boar.engine.labs

import team.sopa.boar.engine.answer.AnswerText
import team.sopa.boar.engine.routing.QueryContext

/**
 * Press phase 3, "Ask BOAR to rewrite it" (adam's mock-up, Nov 13): the prompt for each option, in the text's language,
 * the clean-up of what the model wrote (its reasoning out), and the check that the answer's [n] source marks came back
 * as they were. The app runs it on the loaded chat model and changes nothing until the user picks "Use the new text".
 */
object PressRewrite {
    enum class Option { SHORTER, SIMPLER, FIX, LIST, OWN }

    data class Prompt(val system: String, val user: String, val nPredict: Int)

    /** The text's language: Portuguese when it reads as Portuguese, else English. */
    fun portuguese(text: String): Boolean = QueryContext.isPortugueseQuestion(text)

    private const val SYSTEM_EN = "You rewrite a passage for a document. Reply with the rewritten passage only: no preface, no title, no quotes, " +
        "no notes. Write in English, as the passage is. Keep every source mark such as [1] or [2] exactly as written, after the same " +
        "statement; do not add, drop or renumber any. Do not add facts that are not in the passage."
    private const val SYSTEM_PT = "Você reescreve um trecho para um documento. Responda só com o trecho reescrito: sem introdução, sem título, " +
        "sem aspas, sem notas. Escreva em português, como o trecho. Mantenha cada marca de fonte como [1] ou [2] exatamente como está, " +
        "depois da mesma afirmação; não acrescente, não tire e não renumere nenhuma. Não acrescente fatos que não estejam no trecho."

    fun instruction(option: Option, own: String?, pt: Boolean): String = when (option) {
        Option.SHORTER -> if (pt) "Deixe mais curto: mais ou menos metade do tamanho, com os pontos principais." else "Make it shorter: about half the length, keeping the main points."
        Option.SIMPLER -> if (pt) "Use palavras mais simples e frases mais curtas, para um leitor jovem. Mantenha o sentido." else "Use simpler words and shorter sentences, for a young reader. Keep the meaning."
        Option.FIX -> if (pt) "Corrija a gramática, a ortografia e a redação. Mude o mínimo possível." else "Fix the grammar, spelling and wording. Change as little as possible."
        Option.LIST -> if (pt) "Transforme em uma lista, um ponto por linha, cada linha começando com \"- \"." else "Turn it into a list, one point per line, each line starting with \"- \"."
        Option.OWN -> (if (pt) "Reescreva assim: " else "Rewrite it this way: ") + own.orEmpty().trim().take(200)
    }

    /**
     * The model's prompt for [text] (the language from [languageOf], the text by default). Room to write: about the text's
     * length again, more for a list, between 96 and 1024 tokens.
     */
    fun prompt(option: Option, text: String, own: String? = null, languageOf: String = text): Prompt {
        val pt = portuguese(languageOf)
        val user = instruction(option, own, pt) + "\n\n" + (if (pt) "TRECHO:" else "PASSAGE:") + "\n" + text.trim()
        val tokens = text.length / 3
        val n = when (option) { Option.SHORTER -> tokens; Option.LIST -> tokens * 2; else -> tokens * 3 / 2 } + 64
        return Prompt(if (pt) SYSTEM_PT else SYSTEM_EN, user, n.coerceIn(96, 1024))
    }

    /** What has been written so far, for the screen: the reasoning (while it lasts) hidden. */
    fun visible(raw: String): String = AnswerText.splitThinking(raw).answer.trim()

    private val LABEL = Regex("^(rewritten( passage| text)?|passage|texto reescrito|trecho( reescrito)?|reescrito)\\s*:\\s*", RegexOption.IGNORE_CASE)

    /** The rewrite as it goes in the block: no reasoning, no "Rewritten:" label, no quotes around it all. */
    fun clean(raw: String): String {
        var s = AnswerText.stripThinking(raw).trim()
        s = s.replace(LABEL, "").trim()
        if (s.length >= 2 && ((s.first() == '"' && s.last() == '"') || (s.first() == '“' && s.last() == '”'))) s = s.substring(1, s.length - 1).trim()
        return s
    }

    private val MARK = Regex("\\[(\\d{1,3})]")

    /** The source marks in a text, each once. */
    fun marks(text: String): Set<Int> = MARK.findAll(text).map { it.groupValues[1].toInt() }.toSet()

    /** A mark that was there is missing, or a new one appeared: the screen says "Some source marks changed". */
    fun marksChanged(before: String, after: String): Boolean = marks(before) != marks(after)
}
