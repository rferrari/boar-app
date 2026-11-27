package team.sopa.boar.labs.printer

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import team.sopa.boar.engine.labs.PressDoc.Block
import team.sopa.boar.engine.labs.PressLayout
import java.io.File

/**
 * A project as an A4 PDF with Android's own PdfDocument, offline: what the white page in Press shows, at [Paper]'s
 * measures. The title and date, the blocks in order (heading, answer: its heading, the question in italics, the text,
 * "Sources [n]"; note; picture with its caption if it has one), then the Sources section and the footer (Press settings).
 * Text flows across pages line by line; a heading that would end a page alone starts the next one, a picture that
 * doesn't fit starts a new page.
 */
object PdfCompiler {
    /**
     * Where a PDF page starts, for the paper on screen (adam, Nov 12: the screen's estimated "page 2" line crossed a
     * picture that the PDF kept whole on page 1): the block it starts in ([unit], a block id, [TITLE] or [RECEIPT]) and how
     * many points of that block came before it. 0 means the block itself starts the page.
     */
    data class PageStart(val unit: String, val intoPt: Float)
    const val TITLE = "title"
    const val RECEIPT = "receipt"
    const val SOURCES = "sources"

    /** [fileOf] is a picture's file, or null when it is gone (it is left out). */
    fun compile(out: File, page: PressPage, fileOf: (Block.Picture) -> File?) {
        val doc = PdfDocument()
        try {
            layout(Pager(doc, dry = false), page, fileOf)
            out.parentFile?.mkdirs()
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** The same layout as [compile], nothing drawn and no picture decoded (sizes only): where each page after the first starts. */
    fun pageStarts(page: PressPage, fileOf: (Block.Picture) -> File?): List<PageStart> {
        val doc = PdfDocument()
        return try { Pager(doc, dry = true).also { layout(it, page, fileOf) }.starts } finally { doc.close() }
    }

    private fun layout(p: Pager, page: PressPage, fileOf: (Block.Picture) -> File?) {
            p.begin(TITLE)
            p.text(page.project.name.trim().ifEmpty { " " }, serif(Paper.TITLE, bold = true), after = 4f)
            if (page.options.date) p.text(page.date.uppercase(), sans(Paper.DATE, color = Paper.DATE_INK), after = Paper.GAP + 4f)
            else p.gap(Paper.GAP)
            val caption = { b: Block.Picture -> b.caption.trim().takeIf { it.isNotEmpty() }?.let { it to serif(Paper.CAPTION, italic = true, color = Paper.MUTED) } }
            // The same plan as the paper on screen: a side picture with the next text block beside it (PressLayout).
            for (item in PressLayout.plan(page.project.blocks)) when (item) {
                is PressLayout.Item.Single -> {
                    val b = item.block
                    p.begin(b.id)
                    if (b is Block.Picture) fileOf(b)?.let { f ->
                        // Empty captions don't print.
                        p.picture(f, caption(b))
                        p.gap(Paper.GAP)
                    } else block(p, page, b)
                }
                is PressLayout.Item.Beside -> {
                    val f = fileOf(item.picture)
                    p.begin(item.picture.id)
                    if (f != null) p.sidePicture(f, item.left, caption(item.picture))
                    item.text?.let { p.begin(it.id); block(p, page, it) }
                    // What follows (a heading above all) starts below the picture.
                    p.endBeside()
                    p.gap(if (item.text == null) Paper.GAP else 0f)
                }
            }
            sources(p, page)
            p.begin(RECEIPT)
            receipt(p, page)
            p.finish()
    }

    /** A text block (or nothing for the others): beside a picture, the Pager narrows its lines. */
    private fun block(p: Pager, page: PressPage, b: Block) {
        when (b) {
            is Block.Heading -> if (b.text.isNotBlank()) {
                p.keepRoom(Paper.HEADING * 1.4f + Paper.BODY * 3)
                p.text(b.text.trim(), serif(Paper.HEADING, bold = true), after = Paper.GAP)
            }
            is Block.Answer -> {
                p.keepRoom(Paper.HEADING * 1.4f + Paper.BODY * 3)
                p.text(PressPageText.oneLine(b.shownHeading), serif(Paper.HEADING, bold = true), after = 3f)
                b.shownQuestion?.let { p.text(it.trim(), serif(Paper.QUESTION, italic = true, color = Paper.MUTED), after = 3f) }
                p.paragraphs(page.answerText(b), serif(Paper.BODY), after = 4f, spacing = Paper.PDF_SPACING)
                page.refsLine(b)?.let { p.text(it, sans(Paper.REFS, color = Paper.META), after = 0f) }
                // The card after the answer's own lines, as on the paper (beside a picture too, where it goes below it).
                b.card?.let { card(p, page, it) }
                p.gap(Paper.GAP)
            }
            is Block.Note -> if (b.text.isNotBlank()) p.text(b.text.trim(), serif(Paper.NOTE, italic = true, color = Paper.MUTED), after = Paper.GAP,
                spacing = Paper.PDF_SPACING, indent = 12f)
            is Block.Paragraph -> if (b.text.isNotBlank()) p.paragraphs(b.text.trim(), serif(Paper.BODY), after = Paper.GAP, spacing = Paper.PDF_SPACING)
            is Block.Bullets -> if (b.items.isNotEmpty()) p.paragraphs(team.sopa.boar.engine.labs.PressDoc.bulleted(b.text), serif(Paper.BODY), after = Paper.GAP, spacing = Paper.PDF_SPACING)
            is Block.Divider -> { p.gap(4f); p.rule(); p.gap(Paper.GAP - 10f) }
            is Block.Picture, is Block.Unknown -> {}
        }
    }

    /**
     * An answer's card under its text (Cards, t9 0207 item 4), drawn as on screen: bars, a table, the steps (✓ for a
     * ticked one), the things compared side by side, the key facts. A pie or a line prints as its table. Full width:
     * below a side picture, never beside it.
     */
    private fun card(p: Pager, page: PressPage, card: team.sopa.boar.engine.canvas.AnswerCard) {
        val body = card.body
        val text = sans(Paper.CARD)
        val muted = sans(Paper.CARD, color = Paper.MUTED)
        p.endBeside()
        p.keepRoom(Paper.CARD_TITLE * 1.4f + Paper.CARD * 4)
        p.gap(4f)
        p.rule()
        if (card.title.isNotBlank()) p.text(card.title.trim(), sans(Paper.CARD_TITLE, bold = true), after = 2f)
        if (card.byModel && page.cardByModel.isNotBlank()) p.text(page.cardByModel, sans(Paper.REFS, color = Paper.META), after = 4f) else p.gap(2f)
        val shape = card.shape
        when {
            body is team.sopa.boar.engine.canvas.AnswerCard.Body.Series && shape == team.sopa.boar.engine.canvas.CardRules.Shape.BARS ->
                p.bars(body.points.map { it.label to it.written }, body.points.map { kotlin.math.abs(it.value) }, text, muted)
            body is team.sopa.boar.engine.canvas.AnswerCard.Body.Series || body is team.sopa.boar.engine.canvas.AnswerCard.Body.Table -> {
                val (header, rows) = team.sopa.boar.engine.canvas.AnswerCard.asTable(card)!!
                if (header.any { it.isNotBlank() }) p.cells(header.map { it.uppercase() }, sans(Paper.REFS, bold = true, color = Paper.META), rule = false)
                rows.forEach { p.cells(it, text, rule = true) }
            }
            body is team.sopa.boar.engine.canvas.AnswerCard.Body.Steps -> body.items.forEachIndexed { i, step ->
                p.cells(listOf(if (i in card.ticks) "✓" else "${i + 1}", step), text, rule = false, widths = listOf(0.07f, 0.93f))
            }
            body is team.sopa.boar.engine.canvas.AnswerCard.Body.Facts -> body.pairs.forEach { (l, v) ->
                p.cells(listOf(l, v), text, rule = true, widths = listOf(0.38f, 0.62f), firstPaint = muted)
            }
            body is team.sopa.boar.engine.canvas.AnswerCard.Body.Compare ->
                p.cells(body.sides.map { (name, points) -> name + "\n" + points.joinToString("\n") { "•  $it" } }, text, rule = false)
        }
        p.gap(4f)
    }

    /**
     * The Sources section at the end of the document (adam, Nov 14: "a sources session on end of document"): a small heading,
     * then each source by its [n] with its pack. Its heading never ends a page alone.
     */
    private fun sources(p: Pager, page: PressPage) {
        val lines = page.sourceLines.takeIf { it.isNotEmpty() } ?: return
        p.begin(SOURCES)
        val head = sans(Paper.RECEIPT_TITLE, bold = true)
        val line = sans(Paper.REFS, color = Paper.RECEIPT_INK)
        p.keepRoom(Paper.GAP + Paper.RECEIPT_TITLE * 1.6f + Paper.REFS * 2.6f)
        p.gap(Paper.GAP)
        p.text(page.sourcesLabel, head, after = 6f)
        for (l in lines) p.text(l, line, after = 3f, spacing = 1.25f)
    }

    /**
     * The footer, always at the end (adam, Nov 13): a short centered hairline, then at most two small light lines, the
     * sources by number and "models · Made offline with BOAR · date". Kept together on one page (t9 1721).
     */
    private fun receipt(p: Pager, page: PressPage) {
        val lines = page.footer
        val paint = sans(Paper.FOOTER, color = Paper.FOOT_INK)
        var total = 8f
        for (line in lines) total += StaticLayout.Builder.obtain(line, 0, line.length, paint, Paper.INNER.toInt()).setLineSpacing(0f, 1.3f).build().height + 2f
        // 18 pt above it when there is room; on a nearly full last page only 6, so it isn't alone on a new page (all41 on the F3).
        val above = if (p.room() >= total + 18f) 18f else 6f
        p.keepRoom(minOf(total + above, Paper.H - 2 * Paper.M))
        p.gap(above)
        p.shortRule()
        for (line in lines) p.text(line, paint, after = 2f, spacing = 1.3f, center = true)
    }

    private fun serif(size: Float, bold: Boolean = false, italic: Boolean = false, color: Int = Paper.INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SERIF, when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
    }

    private fun sans(size: Float, bold: Boolean = false, color: Int = Paper.INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** [dry]: lay out only (pageStarts): pictures are measured from their size, nothing is decoded. */
    private class Pager(val doc: PdfDocument, val dry: Boolean) {
        /** Where each page after the first starts (PageStart). */
        val starts = ArrayList<PageStart>()
        private var unit = TITLE
        private var unitTop = Paper.M
        private var unitDone = 0f

        /** The block being laid out now; a page that starts inside it is recorded with how far into it. */
        fun begin(id: String) { unit = id; unitTop = y; unitDone = 0f }

        /** A picture's pixel size: decoded for the PDF, read from the file's header in a dry run. */
        fun bitmapOrSize(file: File): Pair<android.graphics.Bitmap?, Pair<Int, Int>>? {
            if (!dry) return BitmapFactory.decodeFile(file.path)?.let { it to (it.width to it.height) }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, o)
            return if (o.outWidth > 0 && o.outHeight > 0) null to (o.outWidth to o.outHeight) else null
        }

        private var n = 0
        private var page: PdfDocument.Page? = null
        private var y = Paper.M
        private val bottom = Paper.H - Paper.M

        private val canvas: Canvas get() = (page ?: newPage()).canvas

        private fun newPage(): PdfDocument.Page {
            if (page != null) {
                val into = unitDone + (y - unitTop).coerceAtLeast(0f)
                starts += PageStart(unit, into)
                unitDone = into
            }
            page?.let { finishPage(it) }
            n++
            y = Paper.M
            unitTop = Paper.M
            float = null
            return doc.startPage(PdfDocument.PageInfo.Builder(Paper.W.toInt(), Paper.H.toInt(), n).create()).also { page = it }
        }

        private fun finishPage(pg: PdfDocument.Page) {
            val num = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Paper.FOOT_INK; textAlign = Paint.Align.CENTER }
            pg.canvas.drawText("$n", Paper.W / 2f, Paper.H - Paper.M / 2, num)
            doc.finishPage(pg)
        }

        /** A side picture's region: the text beside it is narrower until [bottom]; [left] puts the picture on the left. */
        private class Beside(val left: Boolean, val bottom: Float)
        private var float: Beside? = null

        /** Starts a new page unless [h] points still fit on this one (a heading never ends a page alone); not beside a picture. */
        /** What is left on this page, in points. */
        fun room(): Float = bottom - y

        fun keepRoom(h: Float) {
            canvas
            if (float == null && y > Paper.M && bottom - y < h) newPage()
        }

        /**
         * Text that flows across pages line by line; [indent] with a bar on its left (a note). Beside a side picture the lines
         * whose top is above the picture's bottom are narrower (PressLayout.linesBeside), the rest get the full width back.
         */
        fun text(s: String, paint: TextPaint, after: Float, spacing: Float = 1.15f, indent: Float = 0f, center: Boolean = false) {
            if (s.isBlank()) { y += after; return }
            var rest = s
            while (rest.isNotEmpty()) {
                canvas // a page exists
                val f = float?.takeIf { y < it.bottom }
                val column = if (f != null) PressLayout.besideWidth(Paper.INNER) else Paper.INNER
                val x0 = if (f != null && f.left) Paper.M + PressLayout.sideWidth(Paper.INNER) + PressLayout.GUTTER else Paper.M
                val width = (column - indent).toInt()
                val layout = StaticLayout.Builder.obtain(rest, 0, rest.length, paint, width)
                    .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, spacing).build()
                val room = bottom - y
                var last = 0
                while (last < layout.lineCount && layout.getLineBottom(last) <= room) last++
                if (f != null) last = minOf(last, PressLayout.linesBeside((0 until layout.lineCount).map { layout.getLineTop(it).toFloat() }, f.bottom - y))
                if (last == 0) {
                    // Nothing more beside the picture: below it at the full width; or nothing fits this page: the next one.
                    if (f != null && y + layout.getLineBottom(0) <= bottom) { y = f.bottom; float = null } else newPage()
                    continue
                }
                val c = canvas
                val h = layout.getLineBottom(last - 1).toFloat()
                if (indent > 0) c.drawLine(x0 + 1f, y + 2f, x0 + 1f, y + h - 2f, Paint().apply { color = Paper.NOTE_BAR; strokeWidth = 2f })
                c.save()
                c.translate(x0 + indent, y)
                c.clipRect(0f, 0f, width.toFloat(), h)
                layout.draw(c)
                c.restore()
                y += h
                if (last == layout.lineCount) break
                rest = rest.substring(layout.getLineStart(last))
                // Stopped by the picture's bottom: the rest at the full width; by the page's end: the next page.
                if (f != null && layout.getLineBottom(last) <= room) { y = maxOf(y, f.bottom); float = null } else newPage()
            }
            y += after
        }

        /** [s] paragraph by paragraph (PressSpacing): a small gap between them, not an empty line; [after] after the last. */
        fun paragraphs(s: String, paint: TextPaint, after: Float, spacing: Float) {
            val ps = team.sopa.boar.engine.labs.PressSpacing.paragraphs(s)
            if (ps.isEmpty()) { y += after; return }
            ps.forEachIndexed { i, para -> text(para, paint, after = if (i == ps.lastIndex) after else Paper.PARA_GAP, spacing = spacing) }
        }

        /** Ends the text beside a picture: what follows starts below it, at the full width. */
        fun endBeside() {
            float?.let { y = maxOf(y, it.bottom) }
            float = null
        }

        /**
         * A picture on the left or right, PressLayout.sideWidth of the column, its caption under it in the same width; the
         * text that follows goes beside it ([text]).
         */
        fun sidePicture(file: File, left: Boolean, caption: Pair<String, TextPaint>?) {
            endBeside()
            val (bmp, size) = bitmapOrSize(file) ?: return
            try {
                val box = PressLayout.sideWidth(Paper.INNER)
                val (w, h) = team.sopa.boar.engine.labs.PictureFit.within(size.first, size.second, box, Paper.PICTURE_MAX_H)
                val cap = caption?.let { (s, paint) -> StaticLayout.Builder.obtain(s, 0, s.length, paint, box.toInt()).setLineSpacing(0f, 1.1f).build() }
                val total = h + (cap?.let { it.height + 4f } ?: 0f)
                canvas
                if (bottom - y < total) newPage()
                val x = if (left) Paper.M else Paper.W - Paper.M - box
                // The picture to the outer edge of its box; a narrow (tall) one leaves no gap by the text.
                val px = if (left) x else x + box - w
                bmp?.let { canvas.drawBitmap(it, null, RectF(px, y, px + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG)) }
                cap?.let { c -> canvas.save(); canvas.translate(x, y + h + 4f); c.draw(canvas); canvas.restore() }
                float = Beside(left, y + total + 8f)
            } finally {
                bmp?.recycle()
            }
        }

        /** One receipt line: the label in its column, the value beside it. */
        fun row(label: String, value: String, labelPaint: TextPaint, valuePaint: TextPaint) {
            val w = (Paper.INNER - Paper.RECEIPT_LABEL_W).toInt()
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, valuePaint, w).setLineSpacing(0f, 1.15f).build()
            if (bottom - y < layout.height) newPage()
            val c = canvas
            if (label.isNotEmpty()) {
                c.save(); c.translate(Paper.M, y)
                StaticLayout.Builder.obtain(label, 0, label.length, labelPaint, (Paper.RECEIPT_LABEL_W - 6).toInt()).build().draw(c)
                c.restore()
            }
            c.save(); c.translate(Paper.M + Paper.RECEIPT_LABEL_W, y)
            layout.draw(c)
            c.restore()
            y += layout.height + 1f
        }

        /** A picture centered at the column's width in its own shape, no taller than half a page; its caption under it. */
        fun picture(file: File, caption: Pair<String, TextPaint>?) {
            endBeside()
            val (bmp, size) = bitmapOrSize(file) ?: return
            try {
                val (w, h) = team.sopa.boar.engine.labs.PictureFit.within(size.first, size.second, Paper.INNER, Paper.PICTURE_MAX_H)
                canvas
                if (bottom - y < h + (if (caption != null) 24f else 0f)) newPage()
                val left = (Paper.W - w) / 2
                bmp?.let { canvas.drawBitmap(it, null, RectF(left, y, left + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG)) }
                y += h + 4f
            } finally {
                bmp?.recycle()
            }
            caption?.let { (s, paint) -> text(s, paint, after = 0f, center = true) }
        }

        /** A short hairline in the middle of the page, above the footer. */
        fun shortRule() {
            canvas.drawLine(Paper.W / 2 - 24f, y, Paper.W / 2 + 24f, y, Paint().apply { color = Paper.RULE; strokeWidth = 0.6f })
            y += 8f
        }

        fun rule() {
            canvas.drawLine(Paper.M, y, Paper.W - Paper.M, y, Paint().apply { color = Paper.RULE; strokeWidth = 0.8f })
            y += 10f
        }

        fun gap(h: Float) { y += h }

        /**
         * One row of cells across the column ([widths] as parts of it, equal by default), each wrapped in its own width; the
         * row starts a new page when it doesn't fit; [rule]: a hairline above it. A card's table, steps, facts and comparison.
         */
        fun cells(cells: List<String>, paint: TextPaint, rule: Boolean, widths: List<Float>? = null, firstPaint: TextPaint? = null) {
            if (cells.isEmpty()) return
            val parts = widths ?: List(cells.size) { 1f / cells.size }
            val gutter = 8f
            val layouts = cells.mapIndexed { i, s ->
                val w = (Paper.INNER * parts[i] - gutter).toInt().coerceAtLeast(20)
                val t = s.ifEmpty { " " }
                StaticLayout.Builder.obtain(t, 0, t.length, if (i == 0 && firstPaint != null) firstPaint else paint, w).setLineSpacing(0f, 1.15f).build()
            }
            val h = layouts.maxOf { it.height } + (if (rule) 6f else 3f)
            canvas
            if (bottom - y < h) newPage()
            val c = canvas
            if (rule) { c.drawLine(Paper.M, y, Paper.W - Paper.M, y, Paint().apply { color = Paper.RULE; strokeWidth = 0.6f }); y += 3f }
            var x = Paper.M
            layouts.forEachIndexed { i, l -> c.save(); c.translate(x, y); l.draw(c); c.restore(); x += Paper.INNER * parts[i] }
            y += h - (if (rule) 3f else 0f)
        }

        /** Bars: each label and its value as the answer writes it, a bar under them, the longest the column's width. */
        fun bars(items: List<Pair<String, String>>, values: List<Double>, label: TextPaint, value: TextPaint) {
            val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val line = label.textSize * 1.3f
            val barH = 7f
            items.forEachIndexed { i, (l, v) ->
                val h = line + barH + 6f
                canvas
                if (bottom - y < h) newPage()
                val c = canvas
                val vw = value.measureText(v)
                c.drawText(android.text.TextUtils.ellipsize(l, label, Paper.INNER - vw - 12f, android.text.TextUtils.TruncateAt.END).toString(), Paper.M, y + label.textSize, label)
                c.drawText(v, Paper.W - Paper.M - vw, y + label.textSize, value)
                val top = y + line
                c.drawRoundRect(RectF(Paper.M, top, Paper.W - Paper.M, top + barH), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Paper.BAR_TRACK })
                val w = (Paper.INNER * (values[i] / max)).toFloat().coerceIn(4f, Paper.INNER)
                c.drawRoundRect(RectF(Paper.M, top, Paper.M + w, top + barH), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Paper.BAR })
                y += h
            }
        }

        fun finish() { canvas; page?.let { finishPage(it) }; page = null }
    }
}

/** Small text helpers the paper and the PDF share. */
object PressPageText {
    fun oneLine(s: String) = s.trim().replace(Regex("\\s*\n\\s*"), " ")
}
