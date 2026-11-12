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
            for (b in page.project.blocks) when (b) {
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
                is Block.Picture -> fileOf(b)?.let { f ->
                    // Empty captions don't print.
                    p.picture(f, b.caption.trim().takeIf { it.isNotEmpty() }?.let { it to serif(Paper.CAPTION, italic = true, color = Paper.MUTED) })
                    p.gap(Paper.GAP)
                }
                is Block.Unknown -> {}
            }
            receipt(p, page)
            p.finish()
            out.parentFile?.mkdirs()
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** "How this document was made", always at the end. */
    private fun receipt(p: Pager, page: PressPage) {
        val rows = page.rows
        p.keepRoom(Paper.RECEIPT_TITLE * 2 + Paper.RECEIPT * 4)
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
            return doc.startPage(PdfDocument.PageInfo.Builder(Paper.W.toInt(), Paper.H.toInt(), n).create()).also { page = it }
        }

        private fun finishPage(pg: PdfDocument.Page) {
            val num = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Paper.FOOT_INK; textAlign = Paint.Align.CENTER }
            pg.canvas.drawText("$n", Paper.W / 2f, Paper.H - Paper.M / 2, num)
            doc.finishPage(pg)
        }

        /** Starts a new page unless [h] points still fit on this one (a heading never ends a page alone). */
        fun keepRoom(h: Float) {
            canvas
            if (y > Paper.M && bottom - y < h) newPage()
        }

        /** Text that flows across pages line by line; [indent] with a bar on its left (a note). */
        fun text(s: String, paint: TextPaint, after: Float, spacing: Float = 1.15f, indent: Float = 0f, center: Boolean = false) {
            if (s.isBlank()) { y += after; return }
            var rest = s
            val width = (Paper.INNER - indent).toInt()
            while (rest.isNotEmpty()) {
                canvas // a page exists
                val layout = StaticLayout.Builder.obtain(rest, 0, rest.length, paint, width)
                    .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, spacing).build()
                val room = bottom - y
                var last = 0
                while (last < layout.lineCount && layout.getLineBottom(last) <= room) last++
                if (last == 0) { newPage(); continue }
                val c = canvas
                val h = layout.getLineBottom(last - 1).toFloat()
                if (indent > 0) c.drawLine(Paper.M + 1f, y + 2f, Paper.M + 1f, y + h - 2f, Paint().apply { color = Paper.NOTE_BAR; strokeWidth = 2f })
                c.save()
                c.translate(Paper.M + indent, y)
                c.clipRect(0f, 0f, width.toFloat(), h)
                layout.draw(c)
                c.restore()
                y += h
                if (last == layout.lineCount) break
                rest = rest.substring(layout.getLineStart(last))
                newPage()
            }
            y += after
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
