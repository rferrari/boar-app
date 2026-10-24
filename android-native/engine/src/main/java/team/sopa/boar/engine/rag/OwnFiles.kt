package team.sopa.boar.engine.rag

import java.text.Normalizer

/**
 * What BOAR knows about the user's files as files (Oct 23), for the Personal Files style: which files are switched on,
 * a question that asks about them as a whole ("summarize my files") or names one ("what's in my blood test?"), and a
 * card per long file that a search can match by its outline. All of it comes from the files themselves: names,
 * headings and opening lines, never text a model wrote.
 */
object OwnFiles {
    /** One imported document of a switched-on collection: its file's [name] and how many parts it was cut into. */
    data class File(val docId: String, val name: String, val parts: Int)

    /** A card's chunk id ends with this; its document's own parts end with "-c<n>". */
    const val CARD_SUFFIX = "-card"

    /** The files the prompt lists by name; past it, "and N more". */
    const val NOTE_MAX_FILES = 20

    /** Files whose opening part a question about all of them brings (WholePassages then fits them to the context). */
    const val OVERVIEW_MAX_FILES = 12

    fun isCard(c: Chunk) = c.chunkId.endsWith(CARD_SUFFIX)

    private val MARKS = Regex("\\p{M}+")
    private val WORD = Regex("[a-z0-9]+")

    /** Lower case without accents ("Resumo dos meus Arquivos" → "resumo dos meus arquivos"). */
    private fun fold(s: String) = MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

    private const val FILE_NOUNS = "files?|documents?|docs?|notes?|pdfs?|uploads?|arquivos?|documentos?|notas?|anotacoes|anotacao"

    /** The user's files as a thing ("my files", "meus arquivos", "the documents I imported"). */
    private val FILES = Regex("\\b($FILE_NOUNS)\\b")

    /** Asking about them as a whole: a summary, an overview, a list, whether one exists, how many. */
    private val WHOLE = Regex(
        "\\b(summari[sz]e|summary|overview|main points|key points|main ideas|what('s| is| are)? in|what do|which|list|" +
            "do i have|how many|tell me about|resum[aoei]\\w*|visao geral|pontos principais|principais pontos|ideias principais|" +
            "o que (tem|ha|esta|dizem|diz)|quais|liste|listar|tenho (algum|alguma|um|uma)|quantos|quantas|fale sobre)\\b",
    )

    /**
     * A question about the user's files as a whole, which no single passage is close to: "What are the main points of my
     * files?", "Do I have a medical file?", "Resuma meus arquivos". Its passages are then each file's opening part.
     */
    fun aboutTheFiles(question: String): Boolean {
        val q = fold(question)
        return FILES.containsMatchIn(q) && WHOLE.containsMatchIn(q)
    }

    /** Words a file name shares with any question: they never name a file by themselves. */
    private val GENERIC = setOf(
        "the", "and", "for", "file", "files", "note", "notes", "doc", "docs", "document", "documents", "my", "new", "copy", "final", "draft",
        "txt", "pdf", "csv", "json", "md", "markdown", "arquivo", "arquivos", "nota", "notas", "documento", "documentos", "meu", "minha",
    )

    private fun stem(w: String) = if (w.length > 3 && w.endsWith("s")) w.dropLast(1) else w

    /** A file name's words that can name it: letters, three or more, not [GENERIC]. */
    private fun nameWords(name: String): Set<String> =
        WORD.findAll(fold(name.substringBeforeLast('.'))).map { it.value }.filter { it.length >= 3 && it.any(Char::isLetter) && it !in GENERIC }.map(::stem).toSet()

    /**
     * The one file the question names: every naming word of a file name with one or two of them ("blood-test-2026.txt"
     * by "blood test"), at least two of a longer name's. None when no file or two equally do; then the search runs on
     * all the files as before.
     */
    fun named(question: String, files: List<File>): File? {
        val words = WORD.findAll(fold(question)).map { stem(it.value) }.toSet()
        val scored = files.distinctBy { it.name }.mapNotNull { f ->
            val name = nameWords(f.name)
            val hit = name.count { it in words }
            val enough = if (name.size <= 2) name.isNotEmpty() && hit == name.size else hit >= 2
            if (enough) f to hit else null
        }
        val best = scored.maxOfOrNull { it.second } ?: return null
        return scored.filter { it.second == best }.singleOrNull()?.first
    }

    /** The prompt's line naming the switched-on files, or null without any. */
    fun note(files: List<File>): String? {
        val names = files.map { it.name }.distinct()
        if (names.isEmpty()) return null
        val shown = names.take(NOTE_MAX_FILES).joinToString("; ")
        val more = if (names.size > NOTE_MAX_FILES) "; and ${names.size - NOTE_MAX_FILES} more" else ""
        return "The user's Personal Files (${names.size}): $shown$more."
    }

    private val HEADING = Regex("^#{1,3} +(.+)$")
    private val SPACE = Regex("\\s+")

    /** How the card says the file's kind. */
    private fun kind(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "md", "markdown" -> "Markdown"
        "pdf" -> "PDF"
        "csv" -> "spreadsheet (CSV)"
        "json" -> "JSON"
        else -> "text"
    }

    /**
     * A card for one long document (cut into more than one part): its file, kind, size and part count, its Markdown
     * headings as an outline, and how it starts. A search matches the file as a whole by it, where one part each is
     * only a slice. A one-part document has no card: its part already is the whole file. [size] is formatted by the caller.
     */
    fun card(docId: String, collectionId: String, title: String, fileName: String, body: String, parts: Int, size: String): Chunk? {
        if (parts <= 1) return null
        val headings = body.lineSequence().mapNotNull { HEADING.find(it.trim())?.groupValues?.get(1)?.trim() }.distinct().take(15).toList()
        val start = SPACE.replace(body.trim(), " ").take(300)
        val text = buildString {
            append("File: ").append(fileName).append(" (").append(kind(fileName)).append(", ").append(size).append(", ").append(parts).append(" parts).")
            if (headings.isNotEmpty()) append("\nSections: ").append(headings.joinToString("; ")).append('.')
            append("\nStarts: ").append(start).append(if (body.trim().length > 300) "…" else "")
        }
        return Chunk("$docId$CARD_SUFFIX", docId, "$title (about this file)", text, source = fileName, collectionId = collectionId)
    }

    /**
     * [found] first, then [more] (each file's opening part) for files [found] doesn't already have, up to [limit]; a
     * card goes when a real part of its file is there.
     */
    fun withOpenings(found: List<Chunk>, more: List<Chunk>, limit: Int): List<Chunk> {
        val docs = found.map { it.docId }.toHashSet()
        return dropCoveredCards((found + more.filter { it.docId !in docs }).distinctBy { it.chunkId }).take(limit)
    }

    /** A card is there to find its file: once a part of the same file is among the passages, it only repeats it. */
    fun dropCoveredCards(chunks: List<Chunk>): List<Chunk> {
        val withPart = chunks.filter { !isCard(it) }.map { it.docId }.toHashSet()
        return chunks.filter { !isCard(it) || it.docId !in withPart }
    }
}
