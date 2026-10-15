package team.sopa.boar.engine.chat

import java.text.Normalizer

/**
 * An answer or a whole chat as Markdown (Save as .md): the question, the reasoning when there is one, the answer, its
 * numbered sources and the details line. Written to a file the user picks; nothing leaves the phone.
 */
object ChatExport {
    data class Labels(val reasoning: String, val sources: String)

    data class Turn(val question: String?, val answer: String, val reasoning: String?, val sources: List<Pair<String, String?>>, val details: String?)

    fun turn(t: Turn, l: Labels): String = buildString {
        t.question?.trim()?.takeIf { it.isNotEmpty() }?.let { append("## ").append(it.replace('\n', ' ')).append("\n\n") }
        t.reasoning?.trim()?.takeIf { it.isNotEmpty() }?.let { r ->
            append("> 💭 **").append(l.reasoning).append("**\n>\n")
            r.lines().forEach { append(if (it.isBlank()) ">" else "> $it").append('\n') }
            append('\n')
        }
        append(t.answer.trim()).append('\n')
        if (t.sources.isNotEmpty()) {
            append("\n**").append(l.sources).append("**\n\n")
            t.sources.forEachIndexed { i, (title, source) ->
                append(i + 1).append(". ").append(title)
                source?.takeIf { it.isNotBlank() && it != title }?.let { append(" — ").append(it) }
                append('\n')
            }
        }
        t.details?.takeIf { it.isNotBlank() }?.let { append("\n_").append(it).append("_\n") }
    }

    fun chat(title: String, turns: List<Turn>, l: Labels): String =
        "# ${title.trim().replace('\n', ' ')}\n\n" + turns.joinToString("\n---\n\n") { turn(it, l) }

    /** "boar-2026-10-15-why-is-the-sky-blue.md": the date, then the title in plain lowercase letters and digits. */
    fun fileName(title: String, date: String): String {
        val plain = Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
        val slug = plain.replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).trimEnd('-')
        return if (slug.isEmpty()) "boar-$date.md" else "boar-$date-$slug.md"
    }
}
