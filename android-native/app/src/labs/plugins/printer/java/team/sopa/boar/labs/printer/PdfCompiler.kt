package team.sopa.boar.labs.printer

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * The basket as an A4 PDF with Android's own PdfDocument: the title and date, then each item (its drawing if it has
 * one, its question as the heading, the answer, its sources in small type).
 * Text flows across pages line by line; an illustration that doesn't fit starts a new page. A drawing placed right or
 * left is smaller and the text wraps beside it, so the page has no empty band on either side.
 */
object PdfCompiler {
    private const val W = 595 // A4 in points
    private const val H = 842
    private const val M = 56f // margins
    private val inner = (W - 2 * M).toInt()
    /** A drawing beside the text: 200 pt of the 483 pt column, 14 pt from the text. */
    private const val SIDE_IMAGE = 200f
    private const val GUTTER = 14f

    /** Where the drawings go: centred on their own, or right or left with the text beside them, or alternating. */
    enum class Placement { CENTER, RIGHT, LEFT, ALTERNATE;
        /** The side of the [index]th drawing: ALTERNATE starts on the right. */
        fun sideOf(index: Int): Placement = if (this == ALTERNATE) (if (index % 2 == 0) RIGHT else LEFT) else this
        companion object { fun of(s: String?) = entries.firstOrNull { it.name.equals(s, true) } ?: CENTER }
    }

    /** What prints besides the text: the date under the title, each answer's model and speed, each answer's sources. */
    data class Meta(val date: Boolean = true, val model: Boolean = false, val sources: Boolean = true, val images: Placement = Placement.CENTER, val drawings: Boolean = true)

    /** "Qwen3-4B · 12.3 tok/s", or null when the answer has neither. */
    fun modelLine(i: Basket.Item): String? = listOfNotNull(i.model, i.tokPerSec?.let { String.format(java.util.Locale.US, "%.1f tok/s", it) }).joinToString(" · ").ifEmpty { null }

    fun sourceLines(i: Basket.Item): String = i.sources.mapIndexed { n, s -> "[${n + 1}] ${s.title}" + (s.source?.let { " — $it" } ?: "") }.joinToString("\n")

    /** [drawingOf] is each answer's drawing, or null for none (at most one per answer). */
    fun compile(out: File, title: String, items: List<Basket.Item>, drawingOf: (Basket.Item) -> File?, footer: String, meta: Meta = Meta()) {
        val doc = PdfDocument()
        try {
            val p = Pager(doc)
            p.text(title, paint(24f, bold = true, color = 0xFF111111.toInt()), after = 4f)
            p.text((if (meta.date) DateFormat.getDateInstance(DateFormat.LONG).format(Date()) + " · " else "") + footer, paint(9f, color = 0xFF777777.toInt()), after = 18f)
            p.rule()
            var drawn = 0
            items.forEach { item ->
                // ALTERNATE counts the drawings, not the answers: right, left, right… whichever answers have one.
                if (meta.drawings) drawingOf(item)?.let { p.image(it, meta.images.sideOf(drawn++)) }
                p.text(item.question.ifBlank { item.chatTitle }, paint(14f, bold = true, color = 0xFF1B4D3E.toInt()), after = 6f)
                p.text(plain(item.text), paint(11f, color = 0xFF222222.toInt()), after = 8f, spacing = 1.35f)
                if (meta.model) modelLine(item)?.let { p.text(it, paint(8.5f, color = 0xFF1B4D3E.toInt()), after = 4f) }
                if (meta.sources && item.sources.isNotEmpty()) p.text(sourceLines(item), paint(8.5f, color = 0xFF666666.toInt()), after = 18f)
                else p.gap(12f)
            }
            p.finish()
            out.parentFile?.mkdirs()
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** The answer's markdown as plain text: no ** or #, bullets as •. */
    fun plain(md: String): String = md.lines().joinToString("\n") { line ->
        line.replace(Regex("^#{1,6}\\s+"), "").replace(Regex("^\\s*[-*]\\s+"), "• ").replace("**", "").replace("__", "").replace("`", "")
    }.replace(Regex("\n{3,}"), "\n\n").trim()

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private class Pager(val doc: PdfDocument) {
        private var n = 0
        private var page: PdfDocument.Page? = null
        private var y = M

        private val canvas: Canvas get() = (page ?: newPage()).canvas

        private fun newPage(): PdfDocument.Page {
            page?.let { finishPage(it) }
            n++
            y = M
            float = null
            return doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()).also { page = it }
        }

        private fun finishPage(pg: PdfDocument.Page) {
            val num = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = 0xFF999999.toInt() }
            pg.canvas.drawText("$n", W / 2f, H - M / 2, num)
            doc.finishPage(pg)
        }

        /** A drawing beside the text: its side, width, and the y where the text gets the full width back. */
        private class Beside(val left: Boolean, val width: Float, val bottom: Float)
        private var float: Beside? = null

        fun text(s: String, paint: TextPaint, after: Float, spacing: Float = 1.2f) {
            if (s.isBlank()) return
            var rest = s
            while (rest.isNotEmpty()) {
                canvas // a page exists
                val f = float?.takeIf { y < it.bottom }
                if (f == null) float = null
                // Beside a drawing the column is narrower; lines that start below it get the full width again.
                val width = if (f != null) (inner - f.width - GUTTER).toInt() else inner
                val x = if (f != null && f.left) M + f.width + GUTTER else M
                val layout = StaticLayout.Builder.obtain(rest, 0, rest.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, spacing).build()
                val room = H - M - y
                var last = 0
                while (last < layout.lineCount && layout.getLineBottom(last) <= room && (f == null || layout.getLineTop(last) < f.bottom - y)) last++
                if (last == 0) { newPage(); continue }
                val top = y
                val c = canvas
                c.save()
                c.translate(x, y)
                c.clipRect(0f, 0f, width.toFloat(), layout.getLineBottom(last - 1).toFloat())
                layout.draw(c)
                c.restore()
                y += layout.getLineBottom(last - 1)
                if (last == layout.lineCount) break
                rest = rest.substring(layout.getLineStart(last))
                // Stopped by the page's end, not by reaching the drawing's bottom: the rest goes on the next page.
                if (layout.getLineBottom(last) > room) newPage()
                else if (f != null && layout.getLineTop(last) >= f.bottom - top) float = null
            }
            y += after
        }

        fun image(file: File, side: Placement = Placement.CENTER) {
            val bmp = BitmapFactory.decodeFile(file.path) ?: return
            float?.let { y = maxOf(y, it.bottom) }
            float = null
            val size = if (side == Placement.CENTER) 300f else SIDE_IMAGE
            if (H - M - y < size + 16) newPage()
            val left = when (side) { Placement.RIGHT -> W - M - size; Placement.LEFT -> M; else -> (W - size) / 2 }
            canvas.drawBitmap(bmp, null, RectF(left, y, left + size, y + size), Paint(Paint.FILTER_BITMAP_FLAG))
            if (side == Placement.CENTER) y += size + 20
            else float = Beside(side == Placement.LEFT, size, y + size + 12)
            bmp.recycle()
        }

        fun rule() {
            canvas.drawLine(M, y, W - M, y, Paint().apply { color = 0xFFCCCCCC.toInt(); strokeWidth = 0.6f })
            y += 18
        }

        fun gap(h: Float) { y += h }

        fun finish() { page?.let { finishPage(it) }; page = null }
    }
}
