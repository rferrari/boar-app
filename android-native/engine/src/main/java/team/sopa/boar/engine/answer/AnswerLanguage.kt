package team.sopa.boar.engine.answer

import team.sopa.boar.engine.rag.PtLexicon
import team.sopa.boar.engine.routing.QueryContext

/**
 * The language an answer is written in (adam, Nov 14: "the system prompt is all time in english, even if user select
 * portugueser… send language fix. all prompts"; t9 0420). r4to's fix of 29 Sep (gate bc7db6d: the 4B answered 8 of 29
 * Portuguese questions in English with English sources), lost when the Expo engine left: a line in Portuguese, next to
 * the question, word for word, with the citation request because "with the language line alone the models stopped
 * writing [n]" (gate 1724fd5). English prompts are unchanged.
 *
 * Which language: the question's, when it's clear; else the conversation's last clear one; else the app's setting.
 */
object AnswerLanguage {
    enum class Lang { EN, PT }

    /** The app's language setting ("pt", "en"), set by the app; the last word when nothing else tells. */
    @Volatile var appLanguage: String? = null

    /**
     * r4to's lines (src/routing/context.ts:823-829), with adam's wording of the language (Nov 14: "use \"Responda em
     * Português.\" instead \"Responda em português do Brasil.\""); the citation request stays word for word.
     */
    const val PT_WITH_SOURCES = "Responda em Português, mesmo que as fontes estejam em inglês, e cite cada afirmação com o número da fonte, como [1]."
    const val PT_NO_SOURCES = "Responda em Português."

    /** Personal Files' fixed line, in Portuguese (PersonalFiles.NOT_FOUND_PT), said exactly when the files don't hold it. */
    const val PT_FILES_ONLY = "Responda em Português e cite o arquivo pelo número, como [1]. Se o contexto não tiver a resposta, diga exatamente: " + PersonalFiles.NOT_FOUND_PT

    private val EN_WORDS = Regex("""\b(the|is|are|was|were|what|how|why|who|which|when|where|does|do|did|of|and|to|in|can|should|would|tell|me|about|between|my|your)\b""", RegexOption.IGNORE_CASE)

    /** The language [text] is clearly in, or null when it can't be told (a few words, numbers, a name). */
    fun clear(text: String): Lang? {
        if (QueryContext.isPortugueseQuestion(text) || PtLexicon.looksPortuguese(text)) return Lang.PT
        return if (EN_WORDS.findAll(text).count() >= 2) Lang.EN else null
    }

    /** [earlier]: the user's earlier turns, oldest first. */
    fun of(question: String, earlier: List<String> = emptyList(), app: String? = appLanguage): Lang =
        clear(question) ?: earlier.asReversed().firstNotNullOfOrNull(::clear) ?: if (app == "pt") Lang.PT else Lang.EN

    /** The line next to the question, or null for English (its prompt stays as it was). */
    fun line(lang: Lang, withSources: Boolean, filesOnly: Boolean = false): String? = when (lang) {
        Lang.EN -> null
        Lang.PT -> if (filesOnly && withSources) PT_FILES_ONLY else if (withSources) PT_WITH_SOURCES else PT_NO_SOURCES
    }
}
