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
 * "Sources [n]"; note; picture with its caption if it has one), then "How this document was made", always, and the footer.
 * Text flows across pages line by line; a heading that would end a page alone starts the next one, a picture that
 * doesn't fit starts a new page.
 */
object PdfCompiler {
    /** [fileOf] is a picture's file, or null when it is gone (it is left out). */
    fun compile(out: File, page: PressPage, fileOf: (Block.Picture) -> File?) {
        val doc = PdfDocument()
        try {
            val p = Pager(doc)
            p.text(page.project.name.trim().ifEmpty { " " }, serif(Paper.TITLE, bold = true), after = 4f)
            p.text(page.date.uppercase(), sans(Paper.DATE, color = Paper.DATE_INK), after = Paper.GAP + 4f)
            val caption = { b: Block.Picture -> b.caption.trim().takeIf { it.isNotEmpty() }?.let { it to serif(Paper.CAPTION, italic = true, color = Paper.MUTED) } }
            // The same plan as the paper on screen: a side picture with the next text block beside it (PressLayout).
            for (item in PressLayout.plan(page.project.blocks)) when (item) {
                is PressLayout.Item.Single -> {
                    val b = item.block
                    if (b is Block.Picture) fileOf(b)?.let { f ->
                        // Empty captions don't print.
                        p.picture(f, caption(b))
                        p.gap(Paper.GAP)
                    } else block(p, page, b)
                }
                is PressLayout.Item.Beside -> {
                    val f = fileOf(item.picture)
                    if (f != null) p.sidePicture(f, item.left, caption(item.picture))
                    item.text?.let { block(p, page, it) }
                    // What follows (a heading above all) starts below the picture.
                    p.endBeside()
                    p.gap(if (item.text == null) Paper.GAP else 0f)
                }
            }
            receipt(p, page)
            p.finish()
            out.parentFile?.mkdirs()
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
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
                p.text(page.answerText(b), serif(Paper.BODY), after = 4f, spacing = Paper.PDF_SPACING)
                page.refsLine(b)?.let { p.text(it, sans(Paper.REFS, color = Paper.META), after = 0f) }
                p.gap(Paper.GAP)
            }
            is Block.Note -> if (b.text.isNotBlank()) p.text(b.text.trim(), serif(Paper.NOTE, italic = true, color = Paper.MUTED), after = Paper.GAP,
                spacing = Paper.PDF_SPACING, indent = 12f)
            is Block.Picture, is Block.Unknown -> {}
        }
    }

    /** "How this document was made", always at the end. */
    private fun receipt(p: Pager, page: PressPage) {
        val rows = page.rows
        // Kept together (t9 1721: a page break ran through it): the whole receipt's height, measured as it will be drawn;
        // a receipt taller than a page starts on a fresh one and continues.
        val label0 = sans(Paper.RECEIPT, bold = true, color = Paper.LABEL_INK)
        val value0 = sans(Paper.RECEIPT, color = Paper.RECEIPT_INK)
        val w = (Paper.INNER - Paper.RECEIPT_LABEL_W).toInt()
        var total = 6f + 2f + Paper.RECEIPT_TITLE * 1.4f + 6f + 10f + Paper.FOOT * 1.6f
        for (row in rows) {
            row.lines.forEachIndexed { i, line ->
                val text = if (row.numbered) "${i + 1}. $line" else line
                total += maxOf(StaticLayout.Builder.obtain(text, 0, text.length, value0, w).setLineSpacing(0f, 1.15f).build().height,
                    if (i == 0) StaticLayout.Builder.obtain(row.label, 0, row.label.length, label0, (Paper.RECEIPT_LABEL_W - 6).toInt()).build().height else 0).toFloat()
            }
            total += 4f
        }
        p.keepRoom(minOf(total, Paper.H - 2 * Paper.M))
        p.gap(6f)
        p.rule()
        p.text(page.labels.title, sans(Paper.RECEIPT_TITLE, bold = true, color = Paper.INK).apply { letterSpacing = 0.06f }, after = 6f)
        val label = sans(Paper.RECEIPT, bold = true, color = Paper.LABEL_INK)
        val value = sans(Paper.RECEIPT, color = Paper.RECEIPT_INK)
        for (row in rows) {
            row.lines.forEachIndexed { i, line ->
                p.row(if (i == 0) row.label else "", if (row.numbered) "${i + 1}. $line" else line, label, value)
            }
            p.gap(4f)
        }
        p.gap(10f)
        p.text(page.labels.footer, sans(Paper.FOOT, color = Paper.FOOT_INK), after = 0f, center = true)
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

    private class Pager(val doc: PdfDocument) {
        private var n = 0
        private var page: PdfDocument.Page? = null
        private var y = Paper.M
        private val bottom = Paper.H - Paper.M

        private val canvas: Canvas get() = (page ?: newPage()).canvas

        private fun newPage(): PdfDocument.Page {
            page?.let { finishPage(it) }
            n++
            y = Paper.M
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
            val bmp = BitmapFactory.decodeFile(file.path) ?: return
            try {
                val box = PressLayout.sideWidth(Paper.INNER)
                val (w, h) = team.sopa.boar.engine.labs.PictureFit.within(bmp.width, bmp.height, box, Paper.PICTURE_MAX_H)
                val cap = caption?.let { (s, paint) -> StaticLayout.Builder.obtain(s, 0, s.length, paint, box.toInt()).setLineSpacing(0f, 1.1f).build() }
                val total = h + (cap?.let { it.height + 4f } ?: 0f)
                canvas
                if (bottom - y < total) newPage()
                val x = if (left) Paper.M else Paper.W - Paper.M - box
                // The picture to the outer edge of its box; a narrow (tall) one leaves no gap by the text.
                val px = if (left) x else x + box - w
                canvas.drawBitmap(bmp, null, RectF(px, y, px + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                cap?.let { c -> canvas.save(); canvas.translate(x, y + h + 4f); c.draw(canvas); canvas.restore() }
                float = Beside(left, y + total + 8f)
            } finally {
                bmp.recycle()
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
            val bmp = BitmapFactory.decodeFile(file.path) ?: return
            try {
                val (w, h) = team.sopa.boar.engine.labs.PictureFit.within(bmp.width, bmp.height, Paper.INNER, Paper.PICTURE_MAX_H)
                canvas
                if (bottom - y < h + (if (caption != null) 24f else 0f)) newPage()
                val left = (Paper.W - w) / 2
                canvas.drawBitmap(bmp, null, RectF(left, y, left + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                y += h + 4f
            } finally {
                bmp.recycle()
            }
            caption?.let { (s, paint) -> text(s, paint, after = 0f, center = true) }
        }

        fun rule() {
            canvas.drawLine(Paper.M, y, Paper.W - Paper.M, y, Paint().apply { color = Paper.RULE; strokeWidth = 0.8f })
            y += 10f
        }

        fun gap(h: Float) { y += h }

        fun finish() { canvas; page?.let { finishPage(it) }; page = null }
    }
}

/** Small text helpers the paper and the PDF share. */
object PressPageText {
    fun oneLine(s: String) = s.trim().replace(Regex("\\s*\n\\s*"), " ")
}
