package team.sopa.boar.engine.rag

/**
 * The sources block above the answer (layout "B+", adam, Nov 10): one row per article, its passages grouped ("2
 * passages"), and the one sentence that answers best, with the question's words marked. Code only, no model call, from
 * the same scoring Staged Answers uses (Compress.scoreSentences).
 */
object SourceDigest {
    /** A passage as the answer numbers it ([n] = [number]), and its key sentence. */
    data class Passage(val number: Int, val body: String, val sentence: String, val lead: Boolean, val marks: List<IntRange>)

    /** One article: its passages, best first; [sentence] and [marks] are the best passage's. */
    data class Row(val title: String, val docId: String, val passages: List<Passage>) {
        val best: Passage get() = passages.first()
    }

    /** About 25 words: enough to read at a glance, short enough for three rows on a phone. */
    const val MAX_WORDS = 25

    /**
     * The rows for an answer's [sources] (title, docId, passage text, in the answer's [n] order). A passage's sentence is
     * its best-scoring one; when none of the question's words is in any of its sentences, the passage's first sentence
     * stands in, marked as a lead ([Passage.lead]) and with nothing highlighted, so a row never looks like a match it
     * isn't. Rows keep the order of their first passage.
     */
    fun rows(query: String, sources: List<Triple<String, String, String>>): List<Row> {
        val chunks = sources.mapIndexed { i, (title, docId, body) -> Chunk("p$i", docId, title, body) }
        val scored = Compress.scoreSentences(query, chunks).groupBy { it.chunkIndex }
        val qTerms = Compress.tokenizeTerms(query).toSet()
        val passages = chunks.mapIndexed { i, c ->
            val best = scored[i]?.filter { it.score > 0 }?.maxByOrNull { it.score }
            val text = best?.text ?: Compress.splitSentences(c.body).firstOrNull() ?: c.body
            val cut = cut(text.trim())
            Passage(i + 1, c.body, cut, lead = best == null, marks = if (best == null) emptyList() else marks(cut, qTerms))
        }
        val order = LinkedHashMap<String, MutableList<Pair<Passage, Double>>>()
        chunks.forEachIndexed { i, c ->
            val score = scored[i]?.maxOfOrNull { it.score } ?: 0.0
            order.getOrPut(c.docId.ifBlank { c.title }) { ArrayList() } += passages[i] to score
        }
        return order.map { (key, list) ->
            val first = chunks[list.first().first.number - 1]
            Row(first.title, key, list.sortedByDescending { it.second }.map { it.first })
        }
    }

    /** The row that holds the answer's [n], for a tap on [n]. */
    fun rowOf(rows: List<Row>, number: Int): Row? = rows.firstOrNull { r -> r.passages.any { it.number == number } }

    /** At most [MAX_WORDS] words, whole words, "…" when cut. */
    fun cut(sentence: String): String {
        val words = sentence.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (words.size <= MAX_WORDS) sentence else words.take(MAX_WORDS).joinToString(" ").trimEnd(',', ';', ':') + "…"
    }

    /** The character ranges of the words in [sentence] whose term is one of the question's ([qTerms], stemmed and folded). */
    fun marks(sentence: String, qTerms: Set<String>): List<IntRange> =
        Regex("[\\p{L}\\p{N}]+").findAll(sentence).filter { m -> Compress.tokenizeTerms(m.value).any { it in qTerms } }.map { it.range }.toList()

    /**
     * What the passage sheet shows under the row's key sentence (t9, Nov 10: never the card's text again): the passage
     * after that sentence. When the sentence ends the passage, [before] holds what came before it instead, so the
     * sheet is never empty. A sentence not found in the passage leaves the whole passage in [after].
     */
    data class Sheet(val after: String, val before: String?)

    fun sheet(p: Passage): Sheet {
        val key = p.sentence.removeSuffix("…").trim()
        // Found by its words, whatever the spaces between them: the card's sentence has single spaces, the passage may
        // have a newline, a double space or a no-break space inside it (validator S-1).
        val find = keyPattern(key) ?: return Sheet(p.body.trim(), null)
        val m = find.find(p.body) ?: return Sheet(p.body.trim(), null)
        // The same sentence written twice in the passage: both go, the card already shows it.
        val after = find.replace(p.body.substring(m.range.last + 1), " ").replace(Regex("[ \t]{2,}"), " ").trim()
        return if (after.isNotEmpty()) Sheet(after, null) else Sheet("", p.body.substring(0, m.range.first).trim().ifEmpty { null })
    }

    /** [key]'s words in order, any run of spaces, newlines or no-break spaces between them. */
    private fun keyPattern(key: String): Regex? {
        val words = key.split(Regex("[\\s\u00A0\u202F]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return Regex(words.joinToString("[\\s\u00A0\u202F]+") { Regex.escape(it) })
    }

    /** The same article's other passages for the sheet of passage [number], best first; empty when it's the only one. */
    fun others(rows: List<Row>, number: Int): List<Passage> = rowOf(rows, number)?.passages?.filter { it.number != number }.orEmpty()
}
