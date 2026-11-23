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
    /** OTHER: clearly Spanish, French or German; no Portuguese line, the model answers as it would (validator C-1). */
    enum class Lang { EN, PT, OTHER }

    /**
     * The language fix's arm (t9 0420 item 5), bench only until adam picks: 0 no line (the "before"); 1 the line next to the question (what users
     * get); 2 also the style reminder in Portuguese; 3 also the style prompt in Portuguese (StylePt). English never moves.
     */
    @Volatile var arm: Int = 1

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

    // Words a Portuguese question doesn't use; one is enough once the Portuguese checks above said no (40 short questions,
    // Nov 14: two missed "What causes tides?" and "is it dangerous?").
    private val EN_WORDS = Regex("""\b(the|is|are|was|were|what|how|why|who|which|when|where|does|did|of|and|to|in|can|should|would|tell|about|between|my|your|it|there|this|that|explain|thanks|thank|please|much|many|vs|height|best)\b""", RegexOption.IGNORE_CASE)

    // Spanish, French and German: words none of Portuguese or English uses ("es", "una", "pourquoi", "nicht"), two or
    // more, or a greeting alone (validator C-1: "hola" got "Responda em Português" in a Portuguese app).
    private val ES_WORDS = Regex("""(?<![\p{L}])(qué|cómo|cuándo|dónde|por qué|es|una|un|mi|muy|tiene|puedo|hacer|están|el|los|las|del|al|y|pero|también|hola|gracias|cuál|cuánto|cuántos|hay)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val FR_WORDS = Regex("""(?<![\p{L}])(je|vous|nous|ce|le|est-ce|qu'est|pourquoi|quelle|quel|quels|quelles|les|avec|dans|sont|c'est|bonjour|merci|combien|très|ça|où|est|il|elle|pour|sur|une|des)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val DE_WORDS = Regex("""(?<![\p{L}])(ich|du|warum|wie|ist|sind|und|nicht|mit|eine|ein|der|den|dem|hallo|danke|bitte|welche|wieviel|wo|wer|was ist|kann|ich bin)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val GREETING_OTHER = Regex("""^\s*(?:¡?hola|bonjour|salut|hallo|guten (?:tag|morgen|abend)|buenos (?:días|dias)|buenas(?: tardes| noches)?)\b[\s!.,?¿¡]*\p{L}*[\s!.?]*$""", RegexOption.IGNORE_CASE)

    /** Quoted words are what the question is about, not the language it's asked in: "Translate 'bom dia' to English" (validator C-3). */
    private val QUOTED = Regex("""'[^'\n]{1,80}'|"[^"\n]{1,80}"|“[^”\n]{1,80}”|‘[^’\n]{1,80}’|«[^»\n]{1,80}»""")

    /** An English request about another language's words: "translate … to English", "how do you say …", "what does … mean". */
    private val EN_FRAME = Regex("""^\s*(?:please\s+)?(?:translate|how do (?:you|i) say|what does .{1,60} mean|what is the meaning of|meaning of)\b""", RegexOption.IGNORE_CASE)

    private fun distinct(re: Regex, text: String) = re.findAll(text).map { it.value.lowercase() }.toSet().size

    /** The language [text] is clearly in, or null when it can't be told (a few words, numbers, a name). */
    fun clear(text: String): Lang? {
        if (EN_FRAME.containsMatchIn(text)) return Lang.EN
        val t = QUOTED.replace(text, " ").takeIf { it.isNotBlank() } ?: text
        if (GREETING_OTHER.matches(t) || other(t)) return Lang.OTHER
        if (QueryContext.isPortugueseQuestion(t) || PtLexicon.looksPortuguese(t)) return Lang.PT
        // One English word beside Portuguese ones is a title ("Resumo de The Matrix", "Letra de Let It Be"): can't
        // tell, the conversation or the app decides (validator R-D1).
        if (distinct(EN_WORDS, t) == 1 && PT_SMALL.containsMatchIn(t)) return null
        return if (EN_WORDS.containsMatchIn(t)) Lang.EN else null
    }

    /**
     * Clearly Spanish, French or German: ¿ or ¡, or two of one language's own words and more of them than of
     * Portuguese's or English's. Shared words ("de", "a", "que") don't count either way.
     */
    private fun other(t: String): Boolean {
        if (QueryContext.isSpanishQuestion(t)) return true
        val es = distinct(ES_WORDS, t); val fr = distinct(FR_WORDS, t); val de = distinct(DE_WORDS, t)
        val best = maxOf(es, fr, de)
        val pt = distinct(PT_WORDS, t); val en = distinct(EN_WORDS, t)
        // One marker word is enough in a sentence of three words or more with no Portuguese or English word at all
        // ("Wie viele Einwohner hat Berlin", "combien de temps dure un vol"; validator R-D5). A lone "gracias" stays
        // ambiguous.
        if (best == 1 && pt == 0 && en == 0 && WORD.findAll(t).count() >= 3) return true
        if (best < 2) return false
        return best > pt && best > en
    }

    private val WORD = Regex("""\p{L}+""")
    /** Portuguese small words a title in English sits among: "Resumo de The Matrix". */
    private val PT_SMALL = Regex("""(?<![\p{L}])(de|do|da|dos|das|em|no|na|para|sobre|resumo|letra|significado|tradução)(?![\p{L}])""", RegexOption.IGNORE_CASE)

    private val PT_WORDS = Regex("""(?<![\p{L}])(não|é|são|está|você|como|qual|quais|quanto|quantos|porque|por que|uma|um|do|da|dos|das|no|na|nos|nas|em|para|com|isso|meu|minha|tem|têm|faz|fazer|posso|olá|oi|obrigado|obrigada)(?![\p{L}])""", RegexOption.IGNORE_CASE)

    /** The phone's own language when the app follows the system: "System" is not English (validator C-2). */
    fun setting(app: String?): String = app ?: java.util.Locale.getDefault().language

    /** [earlier]: the user's earlier turns, oldest first. */
    fun of(question: String, earlier: List<String> = emptyList(), app: String? = appLanguage): Lang =
        clear(question) ?: earlier.asReversed().firstNotNullOfOrNull(::clear) ?: if (setting(app) == "pt") Lang.PT else Lang.EN

    /** The line next to the question, or null for English (its prompt stays as it was). */
    fun line(lang: Lang, withSources: Boolean, filesOnly: Boolean = false): String? = when {
        // Arm 0 (bench only): the app as it was, no line, for the "before" run.
        lang != Lang.PT || arm == 0 -> null
        else -> if (filesOnly && withSources) PT_FILES_ONLY else if (withSources) PT_WITH_SOURCES else PT_NO_SOURCES
    }
}
