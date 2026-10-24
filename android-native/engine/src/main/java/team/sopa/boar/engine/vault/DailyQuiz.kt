package team.sopa.boar.engine.vault

import java.text.Normalizer
import java.time.LocalDate
import kotlin.random.Random

/**
 * Boar Labs' Daily Quiz (Oct 24): a few questions a day from BOAR's own library, offline and without the model. Each
 * shows an article's opening sentence with the title's words hidden, and four titles to pick from. The date picks the
 * questions, so the same day gives the same quiz on every open. Right answers unlock Boar Avatars (Vault.Rule.QUIZ_*).
 */
object DailyQuiz {
    data class Question(val clue: String, val options: List<String>, val answer: Int)

    const val MASK = "▢▢▢"
    private const val MIN_CLUE = 40
    private const val MAX_CLUE = 260

    private val MARKS = Regex("\\p{M}+")
    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private val SENTENCE_END = Regex("(?<=[.!?])\\s+(?=[\\p{Lu}\\d\"“(])")

    private fun fold(s: String) = MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")
    private fun stem(w: String) = if (w.length > 3 && w.endsWith("s")) w.dropLast(1) else w

    /** The title's words a clue must not show: three letters or more ("of", "a" stay). */
    private fun titleWords(title: String) =
        WORD.findAll(fold(title.substringBefore(" ("))).map { stem(it.value) }.filter { it.length >= 3 }.toSet()

    /** [sentence] with every word of [title] (any case, accent or plural) replaced by [MASK]. */
    fun mask(sentence: String, title: String): String {
        val hidden = titleWords(title)
        if (hidden.isEmpty()) return sentence
        return WORD.replace(sentence) { m -> if (stem(fold(m.value)) in hidden) MASK else m.value }
    }

    /** The article's first sentence, or null when it's too short to be a clue. */
    fun opening(body: String): String? {
        val text = body.trim().substringBefore("\n\n").replace(Regex("\\s+"), " ")
        val first = SENTENCE_END.split(text, 2).first().trim()
        return first.takeIf { it.length >= MIN_CLUE }?.let { if (it.length > MAX_CLUE) it.take(MAX_CLUE).substringBeforeLast(' ') + "…" else it }
    }

    /**
     * Today's questions from [articles] (title, body), [count] at most. An article whose opening is too short, or that
     * hides nothing of its title (the clue would be a guess), is skipped; a small library gives fewer questions.
     */
    fun daily(articles: List<Pair<String, String>>, date: LocalDate, count: Int = 5): List<Question> {
        val pool = articles.distinctBy { fold(it.first) }
        if (pool.size < 4) return emptyList()
        val rnd = Random(date.toEpochDay())
        val order = pool.shuffled(rnd)
        val out = ArrayList<Question>()
        for ((title, body) in order) {
            if (out.size >= count) break
            val clue = opening(body)?.let { mask(it, title) } ?: continue
            if (!clue.contains(MASK)) continue
            val others = order.map { it.first }.filter { fold(it) != fold(title) }.shuffled(rnd).take(3)
            if (others.size < 3) continue
            val at = rnd.nextInt(4)
            val options = others.toMutableList().apply { add(at, title) }
            out += Question(clue, options, at)
        }
        return out
    }
}
