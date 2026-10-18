package team.sopa.boar.engine.routing

/**
 * Boar Polyglot's decision, pure: is this question clearly a full sentence in a language BOAR doesn't answer in? Then the
 * app replies at once ("Sorry, BOAR doesn't speak Russian yet.") instead of running the model on it. It blocks only
 * when every check agrees, and passes everything it isn't sure about:
 * 1. what is left after cleaning (URLs, emails, numbers, code, emoji, quoted text, capitalized names) is long enough:
 *    [MIN_WORDS] words, or [MIN_CHARS_NO_SPACE] letters in a script written without spaces;
 * 2. the message has no English or Portuguese function words ("What does 'je ne sais quoi' mean?" passes);
 * 3. it isn't a greeting, a thanks, ok, yes or no in any of about 25 languages;
 * 4. the language is sure: a non-Latin script by its letters ([SCRIPT_SHARE] of them in one script), a Latin one by the
 *    phone's detector (confidence [MIN_CONFIDENCE], [MIN_MARGIN] ahead of the next). No detector: Latin text passes.
 * A short follow-up ([FOLLOW_UP_WORDS] words) in a chat whose last question was answered passes too.
 * The detector is Android's (the app's adapter); it is passed in so this stays testable on the JVM.
 */
object LanguageGate {
    /** The receipt's tool code for this reply. */
    const val TOOL = "language"

    /** Languages BOAR answers in. Spanish is allowed for now; take it out here to reply to it too. */
    val ALLOWED: Set<String> = setOf("en", "pt", "es")

    const val MIN_WORDS = 4
    const val MIN_CHARS_NO_SPACE = 8
    const val MIN_SCRIPT_LETTERS = 6
    const val SCRIPT_SHARE = 0.70
    const val MIN_CONFIDENCE = 0.90f
    const val MIN_MARGIN = 0.25f
    const val FOLLOW_UP_WORDS = 6

    /** Only this much of a long message is measured; the function-word check reads all of it. */
    private const val MAX_SCAN = 4_000

    /** A quoted span longer than this isn't a quote (an unmatched mark); the mark is kept as punctuation. */
    private const val MAX_QUOTE = 300

    sealed interface Decision {
        data object Allowed : Decision
        /** [language]: ISO 639-1 code ("ru", "fr", "zh"). */
        data class Unsupported(val language: String) : Decision
    }

    /** [detected]: the phone's guesses for the cleaned text, best first (code to confidence); null when there is no detector. */
    fun decide(text: String, detected: List<Pair<String, Float>>?, followUp: Boolean = false): Decision =
        decide(text, followUp) { detected }

    /** As [decide], calling [detect] (with the cleaned text) only when the answer depends on it. */
    fun decide(text: String, followUp: Boolean, detect: (String) -> List<Pair<String, Float>>?): Decision {
        if (text.isBlank()) return Decision.Allowed
        if (hasEnglishOrPortuguese(text)) return Decision.Allowed
        if ("es" in ALLOWED && QueryContext.isSpanishQuestion(text)) return Decision.Allowed
        val cleaned = clean(text)
        val words = cleaned.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || isSkip(words)) return Decision.Allowed
        val stats = scriptStats(cleaned)
        val noSpace = stats.dominant in NO_SPACE_SCRIPTS
        if (followUp) {
            val size = if (noSpace) (stats.letters + 1) / 2 else text.trim().split(WHITESPACE).size
            if (size <= FOLLOW_UP_WORDS) return Decision.Allowed
        }
        val long = words.size >= MIN_WORDS || (noSpace && stats.dominantLetters >= MIN_CHARS_NO_SPACE)
        if (!long || stats.dominant == null) return Decision.Allowed
        if (stats.dominant == Character.UnicodeScript.LATIN) {
            val guesses = runCatching { detect(cleaned) }.getOrNull()?.map { normalize(it.first) to it.second }?.sortedByDescending { it.second }
            val top = guesses?.firstOrNull() ?: return Decision.Allowed
            val second = guesses.getOrNull(1)?.second ?: 0f
            if (top.first.isEmpty() || top.first == "und" || top.first in ALLOWED) return Decision.Allowed
            if (top.second < MIN_CONFIDENCE || top.second - second < MIN_MARGIN) return Decision.Allowed
            return Decision.Unsupported(top.first)
        }
        if (stats.dominantLetters < MIN_SCRIPT_LETTERS) return Decision.Allowed
        val language = scriptLanguage(stats, cleaned) { runCatching { detect(cleaned) }.getOrNull() } ?: return Decision.Allowed
        return if (language in ALLOWED) Decision.Allowed else Decision.Unsupported(language)
    }

    // ---- cleaning ----

    /** Lowercase-free: the words left to measure, separated by single spaces. */
    fun clean(text: String): String {
        val src = if (text.length > MAX_SCAN) text.substring(0, MAX_SCAN) else text
        val noCode = dropCodeLines(dropBackticks(src))
        val noQuotes = dropQuotes(noCode)
        val out = StringBuilder(noQuotes.length)
        for (line in noQuotes.split('\n')) {
            var sentenceStart = true
            for (raw in line.split(WHITESPACE)) {
                if (raw.isEmpty()) continue
                val endsSentence = endsSentence(raw)
                if (keepToken(raw, sentenceStart)) {
                    val word = lettersOnly(raw)
                    if (word.isNotEmpty()) out.append(word).append(' ')
                }
                sentenceStart = endsSentence
            }
        }
        return out.toString().trim().replace(MULTI_SPACE, " ")
    }

    private val WHITESPACE = Regex("\\s+")
    private val MULTI_SPACE = Regex(" {2,}")

    /** Text between backticks (`x`, ```block```) is code; an unmatched last backtick is kept as it is. */
    private fun dropBackticks(s: String): String {
        if ('`' !in s) return s
        val parts = s.split('`')
        val sb = StringBuilder()
        for (i in parts.indices) if (i % 2 == 0 || i == parts.lastIndex && parts.size % 2 == 0) sb.append(parts[i]).append(' ')
        return sb.toString()
    }

    /** A line with braces, or a semicolon beside code marks (=, brackets, $), is code. A lone ';' is Greek's question mark. */
    private fun dropCodeLines(s: String): String {
        if ('{' !in s && '}' !in s && ';' !in s) return s
        return s.split('\n').filterNot { line ->
            '{' in line || '}' in line || (';' in line && line.any { it in "=()[]<>$" })
        }.joinToString("\n")
    }

    private val QUOTE_PAIRS = mapOf('«' to "»", '»' to "«", '“' to "”„", '„' to "“”", '"' to "\"", '‘' to "’", '‚' to "‘’", '「' to "」", '『' to "』", '\'' to "'")

    /** Removes quoted spans. A straight ' quotes only from outside a word to outside a word (don't, l'eau stay). */
    private fun dropQuotes(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val closers = QUOTE_PAIRS[c]
            if (closers != null && (c != '\'' || i == 0 || !s[i - 1].isLetterOrDigit())) {
                val end = findCloser(s, i + 1, closers, c == '\'')
                if (end >= 0) { sb.append(' '); i = end + 1; continue }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun findCloser(s: String, from: Int, closers: String, straight: Boolean): Int {
        val limit = minOf(s.length, from + MAX_QUOTE)
        var j = from
        while (j < limit) {
            val c = s[j]
            if (c == '\n') return -1
            if (c in closers && (!straight || j + 1 >= s.length || !s[j + 1].isLetterOrDigit())) return j
            j++
        }
        return -1
    }

    private fun endsSentence(token: String): Boolean {
        var k = token.length - 1
        while (k >= 0 && token[k] in "\"'”’»)]」』") k--
        return k >= 0 && token[k] in ".!?…:;。！？"
    }

    /** Drops URLs, emails, anything with a digit, and a capitalized word that doesn't start a sentence (a name, a place). */
    private fun keepToken(token: String, sentenceStart: Boolean): Boolean {
        if ("://" in token || token.startsWith("www.", ignoreCase = true)) return false
        val at = token.indexOf('@')
        if (at > 0 && token.indexOf('.', at) > at) return false
        var firstLetter = -1
        var i = 0
        while (i < token.length) {
            val cp = token.codePointAt(i)
            if (Character.isDigit(cp)) return false
            if (firstLetter < 0 && Character.isLetter(cp)) firstLetter = cp
            i += Character.charCount(cp)
        }
        if (firstLetter < 0) return false
        return sentenceStart || !(Character.isUpperCase(firstLetter) || Character.isTitleCase(firstLetter))
    }

    /** Letters and combining marks; an apostrophe or hyphen inside a word is kept; emoji and punctuation go. */
    private fun lettersOnly(token: String): String {
        val sb = StringBuilder(token.length)
        var i = 0
        while (i < token.length) {
            val cp = token.codePointAt(i)
            val type = Character.getType(cp)
            if (Character.isLetter(cp) || type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()) {
                sb.appendCodePoint(cp)
            } else if ((cp == '\''.code || cp == '’'.code || cp == '-'.code) && sb.isNotEmpty() && i + 1 < token.length && Character.isLetter(token.codePointAt(i + 1))) {
                sb.appendCodePoint(cp)
            } else if (sb.isNotEmpty() && sb.last() != ' ') {
                sb.append(' ')
            }
            i += Character.charCount(cp)
        }
        return sb.toString().trim()
    }

    // ---- function words and skip list ----

    /** English and Portuguese words one of them is enough for. */
    private val STRONG = setOf(
        "the", "what", "what's", "whats", "how", "how's", "why", "where", "where's", "which", "who", "whom", "whose", "when",
        "does", "doesn't", "did", "didn't", "isn't", "aren't", "can't", "don't", "mean", "means", "meaning", "meant", "say",
        "said", "words", "english", "portuguese", "please", "you", "your", "this", "that", "these", "those", "it",
        "it's", "its", "would", "could", "should", "have", "there", "they", "i'm", "and", "with", "about", "from",
        "como", "qual", "quais", "é", "são", "onde", "porque", "porquê", "você", "vocês", "não", "isso", "isto", "significa",
        "significado", "quem", "português", "portugues", "inglês", "ingles", "palavra", "tradução", "traducao",
    )

    /** Shared with other languages (Dutch "is", Polish "do", Turkish "can", Romanian "are"): two different ones are needed. */
    private val WEAK = setOf("is", "do", "can", "are")

    private val STRONG_PAIRS = setOf("o que", "por que", "quer dizer", "o quê")

    /** Any English or Portuguese function word: the message is in an allowed language, maybe quoting another one. */
    fun hasEnglishOrPortuguese(text: String): Boolean {
        val tokens = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() { if (sb.isNotEmpty()) { tokens.add(sb.toString().lowercase()); sb.setLength(0) } }
        for (c in text) {
            if (c.isLetter() || ((c == '\'' || c == '’') && sb.isNotEmpty())) sb.append(if (c == '’') '\'' else c) else flush()
        }
        flush()
        var weak: String? = null
        for ((i, t) in tokens.withIndex()) {
            val w = t.trimEnd('\'')
            if (w in STRONG || w.startsWith("translat") || w.startsWith("traduz") || w.startsWith("traduç")) return true
            if (i > 0 && "${tokens[i - 1]} $w" in STRONG_PAIRS) return true
            if (w in WEAK) { if (weak != null && weak != w) return true; weak = w }
        }
        return false
    }

    private val SKIP_WORDS = setOf(
        // English, Portuguese, Spanish
        "ok", "okay", "okey", "k", "kk", "yes", "yeah", "yep", "no", "nope", "hi", "hello", "hey", "bye", "goodbye", "thanks",
        "thank", "thx", "ty", "good", "morning", "night", "evening", "afternoon", "very", "much", "so", "lot", "sim", "não",
        "nao", "olá", "ola", "oi", "tchau", "obrigado", "obrigada", "valeu", "bom", "dia", "boa", "noite", "tarde", "muito",
        "gracias", "hola", "adiós", "adios", "sí", "si", "buenos", "días", "buenas", "noches", "mucho", "muchas",
        // French, German, Italian, Dutch
        "merci", "beaucoup", "bonjour", "bonsoir", "salut", "oui", "non", "au", "revoir", "très", "bien", "d'accord",
        "danke", "schön", "schon", "sehr", "vielen", "dank", "ja", "nein", "hallo", "tschüss", "guten", "morgen", "tag",
        "abend", "gut", "grazie", "mille", "ciao", "buongiorno", "buonasera", "prego", "sì", "molto", "bene", "dankjewel",
        "bedankt", "dankje", "goedemorgen", "doei", "nee",
        // Nordic, Polish, Czech, Hungarian, Romanian, Turkish, Indonesian, Swahili, Finnish
        "tak", "tack", "takk", "hej", "hei", "kiitos", "dzięki", "dziękuję", "cześć", "dobry", "dzień", "děkuji", "ahoj",
        "köszönöm", "szia", "mulțumesc", "multumesc", "merhaba", "teşekkürler", "teşekkür", "ederim", "sağol", "evet",
        "hayır", "terima", "kasih", "halo", "asante", "sana", "jambo",
        // Romanized
        "arigato", "arigatou", "xiexie", "xièxie", "nihao", "shukran", "salam", "toda", "spasibo", "efharisto", "namaste",
        "dhanyavaad", "gamsahamnida", "kamsahamnida", "annyeong", "konnichiwa", "aloha", "mahalo", "hvala",
        // Russian, Ukrainian, Greek, Arabic, Hebrew, Hindi, Thai
        "спасибо", "большое", "привет", "пока", "да", "нет", "здравствуйте", "доброе", "утро", "добрый", "день", "вечер",
        "дякую", "привіт", "так", "ні", "ευχαριστώ", "πολύ", "γεια", "σας", "ναι", "όχι", "شكرا", "شكرًا", "شكراً", "مرحبا",
        "نعم", "لا", "سلام", "תודה", "רבה", "שלום", "כן", "לא", "धन्यवाद", "नमस्ते", "शुक्रिया", "हाँ", "हां", "नहीं", "ขอบคุณ",
        "สวัสดี", "ครับ", "ค่ะ",
    )

    /** Greetings and thanks in scripts written without spaces, letters only. */
    private val SKIP_PHRASES = setOf(
        "谢谢", "謝謝", "多谢", "你好", "您好", "再见", "再見", "好的", "是", "不是", "对", "不", "谢谢你", "非常感谢",
        "ありがとう", "ありがとうございます", "ありがとうございました", "どうもありがとう", "こんにちは", "こんばんは", "おはよう",
        "おはようございます", "さようなら", "はい", "いいえ", "どうも",
        "감사합니다", "고맙습니다", "안녕하세요", "안녕", "네", "아니요", "아니오", "예",
        "ขอบคุณครับ", "ขอบคุณค่ะ", "ขอบคุณ", "สวัสดีครับ", "สวัสดีค่ะ", "สวัสดี", "ขอบคุณมากครับ", "ขอบคุณมากค่ะ",
    )

    /** All of it is a greeting, a thanks, yes or no; or half of it is and it starts with one ("merci beaucoup mon ami"). */
    private fun isSkip(words: List<String>): Boolean {
        val lower = words.map { it.lowercase() }
        if (lower.joinToString("") in SKIP_PHRASES) return true
        val hits = lower.count { it in SKIP_WORDS || it in SKIP_PHRASES }
        return hits == lower.size || (lower.first() in SKIP_WORDS && hits * 2 >= lower.size)
    }

    // ---- scripts ----

    private val NO_SPACE_SCRIPTS = setOf(
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL, Character.UnicodeScript.THAI, Character.UnicodeScript.LAO,
        Character.UnicodeScript.KHMER, Character.UnicodeScript.MYANMAR,
    )

    class ScriptStats(val letters: Int, val dominant: Character.UnicodeScript?, val dominantLetters: Int, val kana: Int, val han: Int)

    /** Han, Hiragana and Katakana count as one script (Japanese mixes them); [ScriptStats.dominant] is null under [SCRIPT_SHARE]. */
    fun scriptStats(s: String): ScriptStats {
        val counts = HashMap<Character.UnicodeScript, Int>()
        var letters = 0
        var kana = 0
        var han = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            if (!Character.isLetter(cp)) continue
            letters++
            var script = runCatching { Character.UnicodeScript.of(cp) }.getOrNull() ?: continue
            when (script) {
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> { kana++; script = Character.UnicodeScript.HAN }
                Character.UnicodeScript.HAN -> han++
                else -> {}
            }
            counts[script] = (counts[script] ?: 0) + 1
        }
        val best = counts.maxByOrNull { it.value }
        if (best == null || letters == 0 || best.value < letters * SCRIPT_SHARE) return ScriptStats(letters, null, best?.value ?: 0, kana, han)
        val dominant = if (best.key == Character.UnicodeScript.HAN && kana > 0) Character.UnicodeScript.HIRAGANA else best.key
        return ScriptStats(letters, dominant, best.value, kana, han)
    }

    /** The languages each script is written in, the most spoken first (the answer when the detector can't tell). */
    private val SCRIPT_LANGUAGES: Map<Character.UnicodeScript, List<String>> = mapOf(
        Character.UnicodeScript.CYRILLIC to listOf("ru", "uk", "bg", "sr", "mk", "be", "kk", "ky", "mn", "tg", "tt", "ba", "cv"),
        Character.UnicodeScript.ARABIC to listOf("ar", "fa", "ur", "ps", "ku", "sd", "ug", "ckb"),
        Character.UnicodeScript.HEBREW to listOf("he", "yi"),
        Character.UnicodeScript.GREEK to listOf("el"),
        Character.UnicodeScript.DEVANAGARI to listOf("hi", "mr", "ne", "sa"),
        Character.UnicodeScript.BENGALI to listOf("bn", "as"),
        Character.UnicodeScript.GURMUKHI to listOf("pa"),
        Character.UnicodeScript.GUJARATI to listOf("gu"),
        Character.UnicodeScript.ORIYA to listOf("or"),
        Character.UnicodeScript.TAMIL to listOf("ta"),
        Character.UnicodeScript.TELUGU to listOf("te"),
        Character.UnicodeScript.KANNADA to listOf("kn"),
        Character.UnicodeScript.MALAYALAM to listOf("ml"),
        Character.UnicodeScript.SINHALA to listOf("si"),
        Character.UnicodeScript.THAI to listOf("th"),
        Character.UnicodeScript.LAO to listOf("lo"),
        Character.UnicodeScript.KHMER to listOf("km"),
        Character.UnicodeScript.MYANMAR to listOf("my"),
        Character.UnicodeScript.TIBETAN to listOf("bo"),
        Character.UnicodeScript.GEORGIAN to listOf("ka"),
        Character.UnicodeScript.ARMENIAN to listOf("hy"),
        Character.UnicodeScript.ETHIOPIC to listOf("am", "ti"),
        Character.UnicodeScript.HAN to listOf("zh"),
        Character.UnicodeScript.HIRAGANA to listOf("ja"),
        Character.UnicodeScript.HANGUL to listOf("ko"),
    )

    /** The detector's pick when it names a language of this script, else the letters' own clues, else the script's main language. */
    private fun scriptLanguage(stats: ScriptStats, cleaned: String, detect: () -> List<Pair<String, Float>>?): String? {
        val dominant = stats.dominant ?: return null
        val options = SCRIPT_LANGUAGES[dominant] ?: return null
        if (options.size > 1) {
            val top = detect()?.map { normalize(it.first) to it.second }?.maxByOrNull { it.second }
            if (top != null && top.second >= 0.5f && top.first in options) return top.first
        }
        if (dominant == Character.UnicodeScript.CYRILLIC) {
            if (cleaned.any { it in "їєґЇЄҐ" } || (cleaned.any { it in "іІ" } && cleaned.none { it in "ыэъЫЭЪ" })) return "uk"
            if (cleaned.any { it in "ўЎ" }) return "be"
        }
        return options.first()
    }

    /** "zh-Hant" → "zh", "iw" → "he", "in" → "id"; lowercase. */
    fun normalize(code: String): String {
        val base = code.substringBefore('-').substringBefore('_').lowercase()
        return when (base) { "iw" -> "he"; "in" -> "id"; "ji" -> "yi"; "jw" -> "jv"; else -> base }
    }
}
