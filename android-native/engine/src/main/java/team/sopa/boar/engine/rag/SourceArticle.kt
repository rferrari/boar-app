package team.sopa.boar.engine.rag

/**
 * A source under an answer, read whole (Oct 24): the article or file the passage came from, as it is on the phone,
 * found by the source's docId. Nothing goes online: a pack that was removed, or a collection deleted since the answer,
 * reads as null.
 */
object SourceArticle {
    /** [markdown]: the text has "#" headings (a pack article, a .md file) and is shown as Markdown. */
    data class Article(val title: String, val text: String, val source: String?, val markdown: Boolean)

    private val PACK_ARTICLE = Regex("^pack:(.+):a(\\d+)$")
    private val PACK_TITLE = Regex("^pack:([^:]+):(.+)$")
    private val PART = Regex("-c(\\d+)$")

    /**
     * The whole of [docId]'s source: a format-2 pack article ("pack:<pack>:a<id>"), a format-1 pack's passages with that
     * title ("pack:<pack>:<title>"), else the knowledge base's parts of that document (the built-in library, Personal
     * Files). [title]: the source's title, shown when the store has none of its own.
     */
    fun read(docId: String, title: String, kb: KnowledgeBase, packs: List<OpenPack>): Article? {
        PACK_ARTICLE.find(docId)?.let { m ->
            val pack = packs.filterIsInstance<OpenPack.Format2>().firstOrNull { it.id == m.groupValues[1] } ?: return null
            val a = runCatching { pack.pack.article(m.groupValues[2].toLong()) }.getOrNull() ?: return null
            val label = Packs.SOURCE_LABEL[a.source] ?: "Source"
            val source = "$label — ${Packs.articleUrl(a.title, a.source, a.url)}${if (!a.license.isNullOrEmpty()) " (${a.license})" else ""}"
            return Article(a.title, a.text, source, markdown = true)
        }
        PACK_TITLE.find(docId)?.let { m ->
            val pack = packs.filterIsInstance<OpenPack.Format1>().firstOrNull { it.id == m.groupValues[1] } ?: return null
            val rows = runCatching {
                pack.db.query("SELECT body, source FROM chunks WHERE title = ? ORDER BY id", listOf(m.groupValues[2])) { it.string(0).orEmpty() to it.string(1) }
            }.getOrDefault(emptyList())
            if (rows.isEmpty()) return null
            return Article(m.groupValues[2], rows.joinToString("\n\n") { it.first }, rows.firstNotNullOfOrNull { it.second }, markdown = false)
        }
        val parts = kb.docParts(docId).sortedBy { PART.find(it.chunkId)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }
        if (parts.isEmpty()) return null
        val source = parts.firstNotNullOfOrNull { it.source }
        val name = parts.first().title.substringBefore(" (part ")
        return Article(name.ifBlank { title }, join(parts.map { it.body }), source, markdown = source.orEmpty().lowercase().let { it.endsWith(".md") || it.endsWith(".markdown") })
    }

    /** Parts cut with an overlap (DocumentImport.chunkText) joined back: each part's start that repeats the text so far goes. */
    fun join(parts: List<String>): String = parts.fold("") { acc, part ->
        if (acc.isEmpty()) return@fold part
        val max = minOf(DocumentImport.OVERLAP_CHARS, acc.length, part.length)
        val k = (max downTo 20).firstOrNull { acc.endsWith(part.substring(0, it)) } ?: 0
        if (k > 0) acc + part.substring(k) else acc + "\n\n" + part
    }

    private val SECTION_PREFIX = Regex("^[^\\n:]{1,120}: ")

    /**
     * Where the passage the model read sits in [text]: as is, else without the pack's "Section: " prefix
     * (Packs.packHitToChunk), else by its first 120 characters. Null when it isn't there (compressed, or edited since).
     */
    fun passageAt(text: String, passage: String): IntRange? {
        val p = passage.trim()
        if (p.isEmpty()) return null
        val tries = listOfNotNull(p, SECTION_PREFIX.find(p)?.let { p.substring(it.range.last + 1) }, p.take(120).takeIf { p.length > 120 })
        for (t in tries) {
            val i = text.indexOf(t)
            if (i >= 0) {
                // A cut passage marks only what was found, not past it.
                val len = if (t.length == 120 && t != p) minOf(p.length, text.length - i) else t.length
                return i until i + len
            }
        }
        return null
    }
}
