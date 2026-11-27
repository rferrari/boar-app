package team.sopa.boar.engine.labs

import team.sopa.boar.engine.labs.PressDoc.Block
import java.util.Locale

/**
 * "How this document was made", at the end of every Press document, always (adam's mock-up, Nov 13): when and where it
 * was made, the answers and each model with its speed, each drawing's Designer recipe, the photos, and ONE numbered list
 * of sources for the whole document. Each answer's "Sources [n]" points into that list: [build] numbers them in the
 * order they first appear, a source two answers share keeps one number. The paper, the PDF and the Markdown all use it.
 */
object PressReceipt {
    enum class Shape { SQUARE, PORTRAIT, WIDE;
        companion object {
            fun of(width: Int, height: Int): Shape? = when {
                width <= 0 || height <= 0 -> null
                width * 10 > height * 11 -> WIDE
                height * 10 > width * 11 -> PORTRAIT
                else -> SQUARE
            }
        }
    }

    /** One model's answers: how many, and its speed (one value, or the slowest to the fastest). */
    data class ModelLine(val model: String, val answers: Int, val minTokPerSec: Double?, val maxTokPerSec: Double?)

    data class Drawing(val style: String?, val shape: Shape?, val width: Int, val height: Int, val seed: Long?)

    data class Receipt(
        val answers: Int,
        val models: List<ModelLine>,
        val drawings: List<Drawing>,
        val photos: Int,
        /** The document's sources, in number order: [n] is sources[n - 1]. */
        val sources: List<PressDoc.Source>,
        /** Each answer block's numbers, in its own sources' order: the ones it cites when its text was changed (G-5). */
        val refs: Map<String, List<Int>>,
        /** Each answer's own [k] → the document's number; a [k] not in it prints as nothing (G-5b). */
        val local: Map<String, Map<Int, Int>> = emptyMap(),
    )

    /**
     * [recipeOf] reads a drawing's recipe (the app reads Designer's files; the block's own copy by default), [sizeOf] its
     * pixels when the block doesn't know them.
     */
    fun build(
        project: PressDoc.Project,
        recipeOf: (Block.Picture) -> PressDoc.Recipe? = { it.recipe },
        sizeOf: (Block.Picture) -> Pair<Int, Int>? = { null },
    ): Receipt {
        val sources = mutableListOf<PressDoc.Source>()
        val numberOf = HashMap<Pair<String, String?>, Int>()
        val refs = LinkedHashMap<String, List<Int>>()
        val local = LinkedHashMap<String, Map<Int, Int>>()
        val models = LinkedHashMap<String, MutableList<Double?>>()
        val drawings = mutableListOf<Drawing>()
        var photos = 0
        var answers = 0
        for (b in project.blocks) when (b) {
            is Block.Answer -> {
                answers++
                // A changed text keeps only the sources it still cites (G-5); the chat's answer as it came keeps them all.
                val cited = if (b.edited) PressRewrite.marks(b.text) else null
                val map = LinkedHashMap<Int, Int>()
                b.sources.forEachIndexed { i, s ->
                    if (cited != null && (i + 1) !in cited) return@forEachIndexed
                    val key = s.title.trim().lowercase(Locale.ROOT) to s.source?.trim()?.lowercase(Locale.ROOT)
                    map[i + 1] = numberOf.getOrPut(key) { sources += s; sources.size }
                }
                refs[b.id] = map.values.distinct()
                local[b.id] = map
                b.model?.takeIf { it.isNotBlank() }?.let { models.getOrPut(it) { mutableListOf() } += b.tokPerSec }
            }
            is Block.Picture -> if (b.kind == PressDoc.Kind.PHOTO) photos++ else {
                val r = recipeOf(b) ?: b.recipe
                val (w, h) = if (b.width > 0 && b.height > 0) b.width to b.height else sizeOf(b) ?: (0 to 0)
                drawings += Drawing(r?.style, Shape.of(w, h), w, h, r?.seed)
            }
            else -> {}
        }
        return Receipt(answers, models.map { (m, speeds) ->
            val known = speeds.filterNotNull().filter { it > 0 }
            ModelLine(m, speeds.size, known.minOrNull(), known.maxOrNull())
        }, drawings, photos, sources, refs, local)
    }

    /**
     * The words, from the app's strings. [madeValue] has {{date}} and {{version}}; [seed] has {{seed}}; [styleName] turns a
     * Designer style key into its name.
     */
    data class Labels(
        val title: String = "HOW THIS DOCUMENT WAS MADE",
        val made: String = "Made",
        val madeValue: String = "{{date}}, offline, on this phone · BOAR {{version}}",
        val answers: String = "Answers",
        val drawings: String = "Drawings",
        val photos: String = "Photos",
        val photosValue: String = "{{count}} from this phone",
        val sources: String = "Sources",
        val footer: String = "compiled offline with BOAR",
        /** The footer's last line, after the models; {{date}}. */
        val madeWith: String = "Made offline with BOAR · {{date}}",
        val designer: String = "Designer",
        val seed: String = "seed {{seed}}",
        val square: String = "square",
        val portrait: String = "portrait",
        val wide: String = "wide",
        /** A user's own file in the Sources section: its name never prints (validator R-A1). */
        val ownFile: String = "Your own file",
        val styleName: (String) -> String = { k -> k.replaceFirstChar { it.titlecase(Locale.ROOT) } },
    )

    /** One row of the receipt: its label, its lines; [numbered] for the sources (1., 2., …). */
    data class Row(val label: String, val lines: List<String>, val numbered: Boolean = false)

    fun speed(v: Double) = String.format(Locale.US, "%.1f tok/s", v)

    fun modelText(m: ModelLine): String = when {
        m.minTokPerSec == null || m.maxTokPerSec == null -> m.model
        String.format(Locale.US, "%.1f", m.minTokPerSec) == String.format(Locale.US, "%.1f", m.maxTokPerSec) -> "${m.model} · ${speed(m.minTokPerSec)}"
        else -> "${m.model} · " + String.format(Locale.US, "%.1f–%.1f tok/s", m.minTokPerSec, m.maxTokPerSec)
    }

    fun drawingText(d: Drawing, l: Labels): String = listOfNotNull(
        l.designer,
        d.style?.takeIf { it.isNotBlank() }?.let(l.styleName),
        // The shape in words, no pixel numbers (t9 0620/0625). No quality since BOAR-88: Designer draws at 512 only, and
        // a wide or tall drawing is a crop of it.
        d.shape?.let { s -> when (s) { Shape.SQUARE -> l.square; Shape.PORTRAIT -> l.portrait; Shape.WIDE -> l.wide } },
        d.seed?.let { l.seed.replace("{{seed}}", it.toString()) },
    ).joinToString(" · ")

    /** The receipt's rows; "Made" is always there, the others when the document has them. */
    fun rows(r: Receipt, l: Labels, date: String, version: String): List<Row> = buildList {
        add(Row(l.made, listOf(l.madeValue.replace("{{date}}", date).replace("{{version}}", version))))
        if (r.answers > 0) add(Row(l.answers, listOf(r.answers.toString()) + r.models.map(::modelText)))
        if (r.drawings.isNotEmpty()) add(Row(l.drawings, r.drawings.map { drawingText(it, l) }))
        if (r.photos > 0) add(Row(l.photos, listOf(l.photosValue.replace("{{count}}", r.photos.toString()))))
        if (r.sources.isNotEmpty()) add(Row(l.sources, r.sources.map { sourceText(it, l.ownFile) }, numbered = true))
    }

    /**
     * What a document prints besides its blocks, from Press settings (adam, Nov 14: "the toggle pages button become a
     * settings… the date below title, sources on end of doc, models, speed… made with boar keep always on").
     */
    /**
     * [font]: the document's font; [pageNumbers]: "Page N" at the foot of each PDF page (on, as Press always printed a
     * number); [pageFooter]: a light rule and the project's name at the foot of each page (off), as adam's sample PDF.
     */
    data class Options(val date: Boolean = true, val sources: Boolean = true, val models: Boolean = true, val speed: Boolean = false,
                       val font: PressFont = PressFont.SERIF, val pageNumbers: Boolean = true, val pageFooter: Boolean = false)

    /** The Sources section at the end: one line per source, by its [n], with the pack it came from ("Wikipedia"). */
    fun sourceLines(r: Receipt, o: Options, ownFile: String = Labels().ownFile): List<String> =
        if (!o.sources) emptyList() else r.sources.mapIndexed { i, s -> "[${i + 1}] " + sourceText(s, ownFile).trim() }

    /** A model with its speed in brackets: "Qwen3 4B (9.5 tok/s)", or the slowest to the fastest. */
    fun modelWithSpeed(m: ModelLine): String = when {
        m.minTokPerSec == null || m.maxTokPerSec == null -> m.model
        String.format(Locale.US, "%.1f", m.minTokPerSec) == String.format(Locale.US, "%.1f", m.maxTokPerSec) -> "${m.model} (${speed(m.minTokPerSec)})"
        else -> "${m.model} (" + String.format(Locale.US, "%.1f–%.1f tok/s", m.minTokPerSec, m.maxTokPerSec) + ")"
    }

    /**
     * The footer under the Sources section (adam, Nov 14: "split what is sources, what is model, and made with boar"): the
     * models on their own line when [Options.models] (each with its speed when [Options.speed]), then "Made offline with
     * BOAR" and the date, always.
     */
    fun footer(r: Receipt, l: Labels, date: String, o: Options): List<String> = listOfNotNull(
        r.models.takeIf { o.models && it.isNotEmpty() }?.joinToString("  ·  ") { if (o.speed) modelWithSpeed(it) else it.model },
        l.madeWith.replace("{{date}}", date),
    )

    /**
     * A source as the Sources section prints it. A pack's prints its title and "Label — https://…"; a user's own file
     * (its source is the file name, and its title the file name without the extension, DocumentImport.parse) prints
     * [ownFile] only, under its own [n] (validator R-A1: "[1] imposto-2025").
     */
    fun sourceText(s: PressDoc.Source, ownFile: String = Labels().ownFile): String = when {
        s.source.isNullOrBlank() -> s.title
        shareable(s.source) -> s.title + " — " + s.source
        else -> ownFile
    }

    /**
     * A source that may print in a shared PDF or .md (validator A-1): a pack's or the corpus's "Wikipedia — https://…", or
     * a pack's bare label. For the user's own files the source is the file name ("imposto-2025.pdf"): never.
     */
    fun shareable(source: String): Boolean {
        val s = source.trim()
        if (s.isEmpty()) return false
        if (PACK_STYLE.matches(s)) return true
        // BOAR's own guide and FAQ (BoarPack): public text, never a user's file.
        if (team.sopa.boar.engine.rag.BoarPack.isBoarSource(s)) return true
        return s in team.sopa.boar.engine.rag.Packs.SOURCE_LABEL.values
    }

    /**
     * The project as it is shared (PDF, .md): each user's own file named in what came from the chat (the question, the
     * chat title, the answer's text, its card's title) reads [ownFile] (validator Q-B3, t9 0115: "### what does
     * imposto-2025.pdf say about tax?"). What the user typed in Press (headings, notes, the project's name) is
     * theirs and stays. The sources aren't touched: they already print as [ownFile].
     */
    fun scrubOwnFiles(project: PressDoc.Project, ownFile: String): PressDoc.Project {
        val names = project.answers.flatMap { it.sources }.mapNotNull { s -> s.source?.trim()?.takeIf { it.isNotEmpty() && !shareable(it) }?.let { it to s.title.trim() } }
            .flatMap { (file, title) -> listOf(file, file.substringBeforeLast('.'), title) }.filter { it.length >= 4 }.distinct().sortedByDescending { it.length }
        if (names.isEmpty()) return project
        val re = Regex(names.joinToString("|", "(?<![\\p{L}\\p{N}_])(?:", ")(?![\\p{L}\\p{N}_])") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
        fun x(t: String) = t.replace(re, ownFile)
        return project.copy(blocks = project.blocks.map { b ->
            if (b !is PressDoc.Block.Answer) b
            else b.copy(question = x(b.question), chatTitle = x(b.chatTitle), text = x(b.text), card = b.card?.let { it.copy(title = x(it.title), body = scrubBody(it.body, ::x)) })
        })
    }

    /** Every text a card's body holds, through [x] (CodeRabbit on all73: a Facts value could still hold the file's name). */
    private fun scrubBody(b: team.sopa.boar.engine.canvas.AnswerCard.Body, x: (String) -> String): team.sopa.boar.engine.canvas.AnswerCard.Body = when (b) {
        is team.sopa.boar.engine.canvas.AnswerCard.Body.Series -> b.copy(unit = x(b.unit), points = b.points.map { it.copy(label = x(it.label), written = x(it.written)) })
        is team.sopa.boar.engine.canvas.AnswerCard.Body.Table -> b.copy(header = b.header.map(x), rows = b.rows.map { r -> r.map(x) })
        is team.sopa.boar.engine.canvas.AnswerCard.Body.Steps -> b.copy(items = b.items.map(x))
        is team.sopa.boar.engine.canvas.AnswerCard.Body.Facts -> b.copy(pairs = b.pairs.map { (k, v) -> x(k) to x(v) })
        is team.sopa.boar.engine.canvas.AnswerCard.Body.Compare -> b.copy(sides = b.sides.map { (n, ps) -> x(n) to ps.map(x) })
    }

    private val PACK_STYLE = Regex("""[^—\n]{1,60} — https?://\S+(?: \([^)\n]{1,60}\))?""")

    /** "Sources [1] [3]" under an answer; null when it has none. */
    fun refsLine(numbers: List<Int>?, label: String): String? = numbers?.takeIf { it.isNotEmpty() }?.joinToString(" ", prefix = "$label ") { "[$it]" }

    /** An answer's own [k] marks, if its text kept any, become the document's numbers ([refs] in its sources' order). */
    fun renumber(text: String, refs: List<Int>): String = renumber(text, refs.withIndex().associate { (i, n) -> i + 1 to n })

    /**
     * An answer's own [k] marks become the document's numbers ([map]); a [k] the answer has no source for is taken out,
     * so it never reads as another answer's source (validator G-5b).
     */
    fun renumber(text: String, map: Map<Int, Int>): String =
        Regex("\\[(\\d{1,3})]").replace(text) { m -> map[m.groupValues[1].toInt()]?.let { "[$it]" } ?: "" }
            .replace(Regex("(?<=\\S) +([.,;:!?])"), "$1").replace(Regex("(?<=\\S) {2,}(?=\\S)"), " ")

    /** An answer's markdown as plain text for the paper: no ** or #, bullets as •. */
    fun plain(md: String): String = md.lines().joinToString("\n") { line ->
        line.replace(Regex("^#{1,6}\\s+"), "").replace(Regex("^\\s*[-*]\\s+"), "• ").replace("**", "").replace("__", "").replace("`", "")
    }.replace(Regex("\n{3,}"), "\n\n").trim()

    /**
     * The document as Markdown (Save as Markdown, in the project sheet): the same title, date, blocks and receipt as the
     * paper. Pictures stay out (a line says where one was, with its caption if it has one).
     */
    fun markdown(project: PressDoc.Project, l: Labels, date: String, version: String, sourcesLabel: String, drawingLabel: String,
                 r: Receipt = build(project), photoLabel: String = drawingLabel, o: Options = Options()): String = buildString {
        append("# ").append(project.name.trim().replace('\n', ' ')).append("\n\n")
        if (o.date) append('_').append(date).append("_\n\n")
        for (b in project.blocks) when (b) {
            is Block.Heading -> if (b.text.isNotBlank()) append("## ").append(b.text.trim().replace('\n', ' ')).append("\n\n")
            is Block.Note -> if (b.text.isNotBlank()) append(b.text.trim().lines().joinToString("\n") { "> $it" }).append("\n\n")
            is Block.Paragraph -> if (b.text.isNotBlank()) append(b.text.trim()).append("\n\n")
            is Block.Bullets -> if (b.items.isNotEmpty()) append(b.items.joinToString("\n") { "- $it" }).append("\n\n")
            is Block.Divider -> append("* * *\n\n")
            is Block.Answer -> {
                // Sources off: no "Sources [n]" line and no [n] in the text, which would point at nothing.
                val refs = if (o.sources) r.refs[b.id].orEmpty() else emptyList()
                val local = if (o.sources) r.local[b.id].orEmpty() else emptyMap()
                append("### ").append(b.shownHeading.trim().replace('\n', ' ')).append("\n\n")
                b.shownQuestion?.let { append('_').append(it.trim().replace('\n', ' ')).append("_\n\n") }
                append(renumber(b.text.trim(), local).trim()).append("\n\n")
                refsLine(refs, sourcesLabel)?.let { append(it).append("\n\n") }
                // Its card, when it came with one (Cards, t9 0207 item 4): in the form Markdown has for it, after the
                // answer's own lines as on the paper and in the PDF.
                b.card?.let { append(team.sopa.boar.engine.canvas.AnswerCard.markdown(it)).append("\n\n") }
            }
            is Block.Picture -> append("_").append(if (b.kind == PressDoc.Kind.PHOTO) photoLabel else drawingLabel).append(b.caption.trim().takeIf { it.isNotEmpty() }?.let { ": $it" } ?: "").append("_\n\n")
            is Block.Unknown -> {}
        }
        // The Sources section, then the footer, as on the paper (adam, Nov 14), the footer in small italics under a rule.
        sourceLines(r, o, l.ownFile).takeIf { it.isNotEmpty() }?.let { append("## ").append(sourcesLabel).append("\n\n").append(it.joinToString("  \n")).append("\n\n") }
        append("---\n\n").append(footer(r, l, date, o).joinToString("  \n") { "_${it}_" }).append('\n')
    }
}
