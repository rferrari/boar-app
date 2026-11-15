package team.sopa.boar.engine.rag

import java.text.Normalizer

/** JavaScript regex building blocks for java.util.regex: JS's \b is ASCII-only and its \s is Unicode. */
internal object Js {
    const val WS = "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]"
    /** \b before a word character, and after one. */
    const val B0 = "(?<![A-Za-z0-9_])"
    const val B1 = "(?![A-Za-z0-9_])"
    val COMBINING = Regex("[̀-ͯ]")
    fun nfd(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
    fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)
    /** JavaScript String.prototype.trim whitespace. */
    fun isSpace(c: Char) = c in "\t\n\u000B\u000C\r \u00A0\u1680\u2028\u2029\u202F\u205F\u3000\uFEFF" || c in '\u2000'..'\u200A'
    fun trim(s: String) = s.trim(::isSpace)
}

/** "Why"/"what causes" questions: the passage with the question's other words first (port of src/rag/explain.ts). */
object Explain {
    val EXPLAIN_INTENT = Regex(
        "(?i)${Js.B0}(why|what (?:causes?|makes|drives|triggers|produces)|how (?:does|do|did|is|are|can)|reason|por ?que|o que (?:causa|faz|provoca|gera)|como (?:funciona|acontece|se forma|ocorre))${Js.B1}",
    )

    fun termsBeyondTitle(terms: List<LexicalTerm>, title: String): List<LexicalTerm> = terms.filter { RagPure.countMatchedTerms(title, listOf(it)) == 0 }

    /** Reorders each title's chunks among the slots it already holds; a moved chunk takes its slot's score. */
    fun explainingFirst(items: List<Chunk>, terms: List<LexicalTerm>): List<Chunk> {
        val out = items.toMutableList()
        val byTitle = LinkedHashMap<String, MutableList<Int>>()
        items.forEachIndexed { i, c -> byTitle.getOrPut(c.title) { mutableListOf() } += i }
        for ((title, slots) in byTitle) {
            if (slots.size < 2) continue
            val beyond = termsBeyondTitle(terms, title)
            if (beyond.isEmpty()) continue
            val ranked = slots.mapIndexed { order, i -> Triple(items[i], order, RagPure.countMatchedTerms(items[i].body, beyond)) }
                .sortedWith(compareByDescending<Triple<Chunk, Int, Int>> { it.third }.thenBy { it.second })
            slots.forEachIndexed { k, slot -> out[slot] = ranked[k].first.copy(score = items[slot].score) }
        }
        return out
    }
}

/** One copy of an article's passage per answer (port of src/rag/dedupe.ts). */
object Dedupe {
    const val COPY_MIN_SHARE = 0.6
    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private fun words(t: String) = WORD.findAll(t.lowercase()).map { it.value }.toList()
    private fun pairs(t: String): List<String> { val w = words(t); return (1 until w.size).map { "${w[it - 1]} ${w[it]}" } }

    fun containedShare(a: String, b: String): Double {
        val inA = pairs(a).toHashSet()
        val pb = pairs(b)
        if (pb.isEmpty()) { val wa = words(a); return if (words(b).all { it in wa }) 1.0 else 0.0 }
        return pb.count { it in inA }.toDouble() / pb.size
    }

    fun dedupeArticleCopies(chunks: List<Chunk>): List<Chunk> {
        val kept = ArrayList<Chunk>()
        for (c in chunks) {
            val copy = kept.any { k ->
                k.title.trim().lowercase() == c.title.trim().lowercase() &&
                    (containedShare(k.body, c.body) >= COPY_MIN_SHARE || containedShare(c.body, k.body) >= COPY_MIN_SHARE)
            }
            if (!copy) kept += c
        }
        return kept
    }
}

/** Standards named by number: EIP-1559, ERC-20, BIP-32 (port of src/rag/identifiers.ts). */
object Identifiers {
    private val ID = Regex("(?i)${Js.B0}(EIP|ERC|BIP)(?:-|${Js.WS})?(\\d{1,5})${Js.B1}")

    fun identifiersIn(query: String): List<String> =
        ID.findAll(query).map { "${it.groupValues[1].uppercase()}-${it.groupValues[2].toInt()}" }.distinct().toList()

    fun titleHasIdentifier(title: String, id: String): Boolean {
        val (kind, n) = id.split("-")
        return Regex("(?i)${Js.B0}$kind(?:-|${Js.WS})?0*$n${Js.B1}").containsMatchIn(title)
    }
}

/** Portuguese questions against English sources (port of src/rag/ptLexicon.ts). */
object PtLexicon {
    private val QUALIFIER = Regex("${Js.WS}*\\([^)]*\\)${Js.WS}*$")
    private val SPACES = Regex("${Js.WS}+")

    fun normalizeKey(s: String): String =
        Js.trim(Js.nfd(s).replace(Js.COMBINING, "").lowercase().replace(QUALIFIER, "").replace(SPACES, " "))

    private fun foldText(s: String) = Js.nfd(s).replace(Js.COMBINING, "").lowercase()

    private val PT_MARKERS = setOf(
        "o", "os", "que", "como", "porque", "qual", "quais", "uma", "um", "sao", "nao", "e", "das", "dos", "nas", "nos", "numa", "num",
        "pelo", "pela", "onde", "quando", "quanto", "quantos", "existem", "fazer", "faco", "devo", "entre", "diferenca", "depois", "antes",
        "em", "para", "com", "se", "posso", "pode", "tem", "funciona",
    ) // "por que" can't match a single word, as in the TS regex
    private val ASCII_WORD = Regex("[a-z0-9]+")
    private val ACCENTED = Regex("(^|[^\\p{L}])[a-zà-ÿ]*[ãõçâêôáéíóú]\\p{L}*")

    fun looksPortuguese(query: String): Boolean {
        val words = ASCII_WORD.findAll(foldText(query)).map { it.value }.toList()
        val hits = words.count { it in PT_MARKERS }
        val accented = ACCENTED.containsMatchIn(query)
        return hits >= 2 || (hits >= 1 && (accented || words.size <= 4))
    }

    private val STOP = ("a o as os um uma uns umas de do da dos das em no na nos nas num numa por para com sem que como qual quais quando onde porque se ao aos " +
        "e ou é nao mais menos muito entre sobre ate apos depois antes existem existe fazer faco devo pode posso quanto quantos quanta quantas isso isto esse essa este esta the of and").split(" ").toSet()

    private val SINGULAR_RULES = listOf(
        Regex("oes$") to "ao", Regex("aes$") to "ao", Regex("ais$") to "al", Regex("eis$") to "el", Regex("ois$") to "ol",
        Regex("is$") to "il", Regex("ns$") to "m", Regex("res$") to "r", Regex("zes$") to "z", Regex("s$") to "",
    )

    fun singularsPt(word: String): List<String> = SINGULAR_RULES.filter { (re, _) -> re.containsMatchIn(word) && word.length > 3 }.map { (re, to) -> re.replaceFirst(word, to) }

    private fun variants(words: List<String>): List<String> {
        val out = mutableListOf(words.joinToString(" "))
        for (i in words.indices) for (s in singularsPt(words[i])) out += (words.subList(0, i) + s + words.subList(i + 1, words.size)).joinToString(" ")
        // "oferta e a demanda" is the lexicon's "oferta e demanda": an article inside a phrase doesn't change it.
        if (words.size >= 3) {
            val bare = listOf(words.first()) + words.subList(1, words.size - 1).filter { it !in INNER_ARTICLES } + words.last()
            if (bare.size != words.size) out += bare.joinToString(" ")
        }
        return out
    }
    private val INNER_ARTICLES = setOf("a", "o", "as", "os")

    /**
     * Readings the lexicon gets wrong for a question (t9 0226, measured on the 30 Portuguese questions, 15 Nov), checked
     * before it; "" reads as no name at all (the words are used up, so no shorter name is looked up for them either).
     * Keys are folded. "aparelho" is a device, not Brickwork (the masonry bond); a time unit names no article to search ("15h40… 35 minutos" found Minute, Inverse second,
     * Sexagesimal); "inclinação da Terra" is the axial tilt, not an orbit's inclination; "céu azul" is why the sky is blue.
     */
    private val READINGS: Map<String, String> = mapOf(
        "aparelho" to "", "aparelhos" to "",
        "minuto" to "", "minutos" to "", "hora" to "", "horas" to "", "segundo" to "", "segundos" to "",
        "inclinacao da terra" to "Axial tilt", "inclinacao do eixo" to "Axial tilt", "inclinacao do eixo da terra" to "Axial tilt", "eixo da terra" to "Axial tilt",
        "ceu azul" to "Diffuse sky radiation", "ceu e azul" to "Diffuse sky radiation",
    )

    /** "marés" -> "maré" (Tide) before the folded "mares" -> "mar" (Sea): an accented word's own singular first. */
    private fun accentedSingulars(word: String): List<String> = listOf(Regex("s$") to "").filter { (re, _) -> re.containsMatchIn(word) && word.length > 3 }.map { (re, to) -> re.replaceFirst(word, to) }

    private val titleCache = java.util.WeakHashMap<Map<String, String>, Map<String, String>>()
    private fun englishTitles(lexicon: Map<String, String>): Map<String, String> =
        synchronized(titleCache) { titleCache.getOrPut(lexicon) { lexicon.values.associateBy { normalizeKey(it) } } }

    private val FOLDED_WORD = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private val ORIGINAL_WORD = Regex("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*")
    private val UPPER_START = Regex("^\\p{Lu}")

    /**
     * "na natureza", "em meio à natureza", "em plena natureza", "no meio da natureza": "in the wild", a place, not the
     * article Nature (t9 0110 item 6: "Como purificar água na natureza, passo a passo?" got Nature, Nature (journal) and
     * Nature reserve as its sources). "a natureza", "da natureza" still name it.
     */
    private val IN_THE_WILD = Regex("""(?<![\p{L}])(?:na|em plena|em meio [àa]|no meio da|em contato com a|ao ar livre na) natureza(?![\p{L}])""", RegexOption.IGNORE_CASE)

    /** English article names a Portuguese question mentions, longest names first, each word used once. */
    fun englishNamesIn(query: String, lexicon: Map<String, String>, max: Int = 4, maxLen: Int = 5): List<String> {
        @Suppress("NAME_SHADOWING") val query = IN_THE_WILD.replace(query, " ")
        val words = FOLDED_WORD.findAll(foldText(query)).map { it.value }.toList()
        val original = ORIGINAL_WORD.findAll(query).map { it.value }.toList()
        val accented = if (original.size == words.size) original.map { Js.nfc(it).lowercase() } else null
        val contentWords = words.count { it !in STOP && it !in PT_MARKERS }
        fun capitalizedMid(i: Int) = original.size == words.size && i > 0 && UPPER_START.containsMatchIn(original[i])
        fun specific(i: Int) = words[i].length >= 7 || contentWords <= 2 || capitalizedMid(i)
        val used = BooleanArray(words.size)
        val names = ArrayList<String>()
        for (len in minOf(maxLen, words.size) downTo 1) {
            for (i in 0..words.size - len) {
                if (names.size >= max) return names
                val gram = words.subList(i, i + len)
                if ((i until i + len).any { used[it] }) continue
                if (gram[0] in STOP || gram[len - 1] in STOP) continue
                if (len == 1 && (gram[0].length < 4 || !specific(i))) continue
                val reading = variants(gram).firstNotNullOfOrNull { READINGS[it] }
                if (reading != null) {
                    if (reading.isEmpty()) { for (j in i until i + len) used[j] = true; continue }
                    for (j in i until i + len) used[j] = true
                    if (reading !in names) names += reading
                    continue
                }
                val hit = accented?.let { a -> lexicon[a.subList(i, i + len).joinToString(" ")]
                    ?: if (len == 1) accentedSingulars(a[i]).firstNotNullOfOrNull { lexicon[it]?.takeIf { v -> v.isNotEmpty() } } else null }
                    ?: variants(gram).firstNotNullOfOrNull { lexicon[it]?.takeIf { v -> v.isNotEmpty() } }
                    ?: (if (len == 1) englishTitles(lexicon)[gram[0]] else null)
                    ?: continue
                if (hit.isEmpty()) continue // as in the TS: an empty lexicon value ends the search for this gram
                if (len == 1 && hit.contains('(') && !capitalizedMid(i)) continue
                for (j in i until i + len) used[j] = true
                if (hit !in names) names += hit
            }
        }
        return names
    }
}
