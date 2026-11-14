package team.sopa.boar.engine.chat

/**
 * "Keep going" (adam, Nov 13-14; t9 0235): under the latest answer, outside its card, up to three faint bubbles on the
 * user's side. One step at most (the next rung of the ladder Quick › Detailed › Research, or "Ask BOAR" after a plugin's
 * instant answer), then follow-up questions from the answer's own sources. No model call: what the answer already has.
 */
object KeepGoing {
    enum class Step(val key: String) { DEEPER("deeper"), RESEARCH("research"), ASK_BOAR("ask") ;
        companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } }
    }

    /** What the answer was, from its receipt and the chat. */
    data class Answer(
        /** Failed, stopped or timed out: nothing to offer but the repair inside the card. */
        val broken: Boolean = false,
        /** A plugin's instant answer (no model ran), and whether the user may ask the model instead (not the CO answer). */
        val instant: Boolean = false,
        val canBypass: Boolean = false,
        /** The style it was written in: "succinct", "detailed", "personal", "custom". */
        val style: String? = null,
        val deepResearch: Boolean = false,
        /** Cut at the token limit: the repair is in the card. */
        val cut: Boolean = false,
        /** The app kept it short on purpose (a health or seed-phrase question): no "deeper". */
        val keptShort: Boolean = false,
        val hasSources: Boolean = false,
    )

    data class Next(val step: Step?, val followUps: List<String>)

    const val MAX = 3

    /**
     * Follow-up questions are off until adam reads the table (t9 0235 item 7: "if they read poorly, the bubbles ship with
     * the steps only"). On the F3's 56 real answers they did: "Purified water" for a question on purifying water,
     * "Cutlet" for bread, "Nature reserve" for "natureza", and a title from the user's own files.
     */
    const val FOLLOW_UPS = false
    const val MAX_FOLLOW_UPS = 2

    /** The table of t9 0235, row by row. */
    fun of(a: Answer, followUps: List<String>): Next {
        if (a.broken) return Next(null, emptyList())
        if (a.instant) return Next(if (a.canBypass) Step.ASK_BOAR else null, emptyList())
        val step = when {
            a.cut || a.deepResearch || a.keptShort -> null
            a.style == "succinct" -> Step.DEEPER
            a.style == "detailed" -> Step.RESEARCH
            else -> null // Personal Files, Custom: no ladder.
        }
        val asks = if (a.hasSources) followUps.distinct().take(MAX_FOLLOW_UPS) else emptyList()
        return Next(step, asks.take(MAX - (if (step != null) 1 else 0)))
    }

    /** The ladder under the step's verb: the rungs done filled ("● Quick › ○ Detailed › ○ Research"). */
    fun rungsDone(step: Step): Int = when (step) { Step.DEEPER -> 1; Step.RESEARCH -> 2; Step.ASK_BOAR -> 1 }
}

/**
 * Follow-up questions with no model call (coder's plan of Nov 12): another source's title ("Tell me more about <title>"),
 * then a named thing in a cited passage that is not in the question ("What is <thing>?"). Never the question's own topic,
 * no duplicates, two at most. Pure: text in, questions out.
 */
object FollowUps {
    /** [fromPack]: a knowledge pack's article; never the user's own files (their names stay theirs). */
    data class Source(val title: String, val body: String, val cited: Boolean, val fromPack: Boolean = true)

    fun pick(question: String, sources: List<Source>, pt: Boolean, max: Int = KeepGoing.MAX_FOLLOW_UPS): List<String> {
        val q = norm(question)
        val out = LinkedHashSet<String>()
        val seen = HashSet<String>()
        // 1. Another source's title, cited ones first; a title the question already names is its topic.
        // Only what the answer cites, only from the packs: an uncited passage was often off-topic ("Cutlet" for bread).
        val usable = sources.filter { it.cited && it.fromPack && !FILE_LIKE.containsMatchIn(it.title) }
        for (s in usable) {
            val t = cleanTitle(s.title)
            val k = norm(t)
            if (t.isEmpty() || k in seen || mentioned(k, q)) continue
            seen += k
            out += if (pt) "Fale mais sobre $t" else "Tell me more about $t"
            break
        }
        // 2. A named thing in a cited passage, not in the question, not a source already offered.
        for (s in usable) {
            for (name in names(s.body)) {
                val k = norm(name)
                if (k in seen || mentioned(k, q) || k.length < 4) continue
                seen += k
                out += if (pt) "O que é $name?" else "What is $name?"
                break
            }
            if (out.size >= max) break
        }
        return out.take(max)
    }

    /** "Mercury (planet)" -> "Mercury"; a pack's "Title — section" -> "Title". */
    fun cleanTitle(t: String): String = t.substringBefore(" — ").replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()

    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** A file's name, not an article's: "eBook_investimentos (part 16/17)". */
    private val FILE_LIKE = Regex("""_|\(part \d+/\d+\)|\.(pdf|txt|md|docx?|csv)\b""", RegexOption.IGNORE_CASE)

    /** Every word of the name is in the question, by its stem ("purified" and "purify"): the question's own topic. */
    private fun mentioned(name: String, q: String): Boolean {
        val qw = q.split(' ').filter { it.length > 2 }.map(::stem).toSet()
        val words = name.split(' ').filter { it.length > 2 }.map(::stem)
        return words.isNotEmpty() && words.all { it in qw }
    }

    private fun stem(w: String) = w.take(5)

    private val NAME = Regex("""\b(\p{Lu}[\p{L}'-]+(?:\s+(?:of|de|da|do|the|von|van)?\s*\p{Lu}[\p{L}'-]+){0,3})""")
    private val STOP = setOf("The", "This", "That", "These", "Those", "It", "In", "On", "At", "A", "An", "He", "She", "They", "We", "I", "His", "Her", "Its",
        "O", "A", "Os", "As", "Um", "Uma", "Ele", "Ela", "Em", "No", "Na", "De", "Da", "Do", "Se", "Por", "Para", "Com", "Mas", "And", "But", "However", "Also", "When", "After", "Before")

    /** Capitalised names in a passage, not at the start of a sentence unless they run over two words. */
    fun names(body: String): List<String> {
        val out = ArrayList<String>()
        for (m in NAME.findAll(body)) {
            val name = m.value.trim()
            val first = name.substringBefore(' ')
            if (first in STOP) continue
            val before = body.substring(0, m.range.first).trimEnd()
            val sentenceStart = before.isEmpty() || before.last() in ".!?:\n"
            if (sentenceStart && !name.contains(' ')) continue
            out += name
        }
        return out
    }
}
