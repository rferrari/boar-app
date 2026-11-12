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

    /** Words in [text], [n] marks not counted. */
    fun words(text: String): Int = text.replace(MARK, " ").split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }

    /** "Shorter": at most half the words (and never under 8), a number the model can aim at (t9 1721: "about half" came back unchanged). */
    fun shorterTarget(text: String): Int = maxOf(8, words(text) / 2)

    /**
     * Each quick action with a concrete target (t9 1721, after "Shorter" returned the same text on the F3): Shorter = at most
     * half the words; Simpler words = short everyday words, the same facts; Fix the wording = the same length.
     */
    fun instruction(option: Option, own: String?, pt: Boolean, text: String = ""): String = when (option) {
        Option.SHORTER -> shorterTarget(text).let { n -> if (pt) "Reescreva em no máximo $n palavras (hoje são ${words(text)}). Fique só com os pontos principais e corte o resto."
            else "Rewrite it in at most $n words (it has ${words(text)} now). Keep only the main points and cut the rest." }
        Option.SIMPLER -> if (pt) "Reescreva com palavras curtas do dia a dia e frases curtas, como para uma criança de 10 anos. Os mesmos fatos, nenhum fato novo. Troque toda palavra difícil."
            else "Rewrite it with short, everyday words and short sentences, as for a 10-year-old. The same facts, no new ones. Replace every hard word."
        Option.FIX -> words(text).let { n -> if (pt) "Corrija só a gramática, a ortografia e a redação. Mesmo tamanho (cerca de $n palavras) e os mesmos fatos."
            else "Fix only the grammar, spelling and wording. The same length (about $n words) and the same facts." }
        Option.LIST -> if (pt) "Transforme em uma lista, um ponto por linha, cada linha começando com \"- \"." else "Turn it into a list, one point per line, each line starting with \"- \"."
        Option.OWN -> (if (pt) "Reescreva assim: " else "Rewrite it this way: ") + own.orEmpty().trim().take(200)
    }

    /**
     * The model's prompt for [text] (the language from [languageOf], the text by default). Room to write: about the text's
     * length again, more for a list, between 96 and 1024 tokens.
     */
    /**
     * The user's own words (the input bar, or the sheet's field) may ask for new text, not a rewording: "replace this with
     * something about the document" (adam on the F3, Nov 12: the model kept returning "this is a test"). So that option gets
     * the rest of the document to read, and may write new text from it.
     */
    private const val OWN_EN = "You edit one passage of a document, doing exactly what the writer's INSTRUCTION says. The instruction may ask " +
        "for new text in place of the passage: then write it, using the DOCUMENT to know what it is about. Reply with the new passage " +
        "only: no preface, no title, no quotes, no notes. Write in English. Use only facts from the document or the passage. Keep a " +
        "source mark such as [1] only if the passage already has it; never add a new one."
    private const val OWN_PT = "Você edita um trecho de um documento, fazendo exatamente o que a INSTRUÇÃO de quem escreve pede. A instrução pode " +
        "pedir um texto novo no lugar do trecho: então escreva, usando o DOCUMENTO para saber do que se trata. Responda só com o novo " +
        "trecho: sem introdução, sem título, sem aspas, sem notas. Escreva em português. Use só fatos do documento ou do trecho. " +
        "Mantenha uma marca de fonte como [1] só se o trecho já a tiver; nunca acrescente uma nova."

    /** The rest of the document for the model to read: its title and the other blocks' text, [max] characters at most. */
    fun documentContext(title: String, others: List<String>, max: Int = 1500): String =
        (listOf(title.trim()) + others.map { it.trim() }).filter { it.isNotEmpty() }.joinToString("\n\n").take(max).trim()

    fun prompt(option: Option, text: String, own: String? = null, languageOf: String = text, document: String = ""): Prompt {
        val pt = portuguese(languageOf)
        if (option == Option.OWN) {
            val doc = document.trim().takeIf { it.isNotEmpty() }?.let { (if (pt) "DOCUMENTO (só para contexto, não copie):" else "DOCUMENT (for context only, do not copy it):") + "\n" + it + "\n\n" }.orEmpty()
            val user = doc + (if (pt) "TRECHO:" else "PASSAGE:") + "\n" + text.trim() + "\n\n" + (if (pt) "INSTRUÇÃO: " else "INSTRUCTION: ") + own.orEmpty().trim().take(200)
            // Room for new text, not only the passage's length again.
            return Prompt(if (pt) OWN_PT else OWN_EN, user, (text.length / 3 * 3 / 2 + 64).coerceIn(320, 1024))
        }
        // The passage first, the task last: small models copied the passage back when the task came first (the F3, Nov 12:
        // "Shorter" returned the same text on Qwen3 1.7B).
        val user = (if (pt) "TRECHO:" else "PASSAGE:") + "\n" + text.trim() + "\n\n" + (if (pt) "TAREFA: " else "TASK: ") + instruction(option, own, pt, text)
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

    /** The rewrite says the same as the text (spaces and case aside): the screen says "No change" instead of NOW / REWRITTEN. */
    fun unchanged(before: String, after: String): Boolean {
        fun norm(x: String) = x.trim().replace(Regex("\\s+"), " ").lowercase()
        return norm(before) == norm(after)
    }

    /** The source marks in a text, each once. */
    fun marks(text: String): Set<Int> = MARK.findAll(text).map { it.groupValues[1].toInt() }.toSet()

    /**
     * New text written from the user's own words must not cite what the passage didn't: a [n] that wasn't there before is
     * taken out (the model added "[1]" to a plain note on the F3, Nov 12). Marks the passage had stay.
     */
    fun dropNewMarks(before: String, after: String): String {
        val had = marks(before)
        return MARK.replace(after) { m -> if (m.groupValues[1].toInt() in had) m.value else "" }.replace(Regex(" +([.,;:!?])"), "$1").replace(Regex(" {2,}"), " ").trim()
    }

    /** A mark that was there is missing, or a new one appeared: the screen says "Some source marks changed". */
    fun marksChanged(before: String, after: String): Boolean = marks(before) != marks(after)
}
