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
 * The basket as an A4 PDF with Android's own PdfDocument: the title and date, the first illustration, then each item
 * (its question as the heading, the answer, its sources in small type), the second illustration halfway through.
 * Text flows across pages line by line; an illustration that doesn't fit starts a new page.
 */
object PdfCompiler {
    private const val W = 595 // A4 in points
    private const val H = 842
    private const val M = 56f // margins
    private val inner = (W - 2 * M).toInt()

    /** What prints besides the text: the date under the title, each answer's model and speed, each answer's sources. */
    data class Meta(val date: Boolean = true, val model: Boolean = false, val sources: Boolean = true)

    /** "Qwen3-4B · 12.3 tok/s", or null when the answer has neither. */
    fun modelLine(i: Basket.Item): String? = listOfNotNull(i.model, i.tokPerSec?.let { String.format(java.util.Locale.US, "%.1f tok/s", it) }).joinToString(" · ").ifEmpty { null }

    fun sourceLines(i: Basket.Item): String = i.sources.mapIndexed { n, s -> "[${n + 1}] ${s.title}" + (s.source?.let { " — $it" } ?: "") }.joinToString("\n")

    fun compile(out: File, title: String, items: List<Basket.Item>, images: List<File>, footer: String, meta: Meta = Meta()) {
        val doc = PdfDocument()
        try {
            val p = Pager(doc)
            p.text(title, paint(24f, bold = true, color = 0xFF111111.toInt()), after = 4f)
            p.text((if (meta.date) DateFormat.getDateInstance(DateFormat.LONG).format(Date()) + " · " else "") + footer, paint(9f, color = 0xFF777777.toInt()), after = 18f)
            p.rule()
            images.getOrNull(0)?.let { p.image(it) }
            val half = (items.size + 1) / 2
            items.forEachIndexed { i, item ->
                if (i == half && i > 0) images.getOrNull(1)?.let { p.image(it) }
                p.text(item.question.ifBlank { item.chatTitle }, paint(14f, bold = true, color = 0xFF1B4D3E.toInt()), after = 6f)
                p.text(plain(item.text), paint(11f, color = 0xFF222222.toInt()), after = 8f, spacing = 1.35f)
                if (meta.model) modelLine(item)?.let { p.text(it, paint(8.5f, color = 0xFF1B4D3E.toInt()), after = 4f) }
                if (meta.sources && item.sources.isNotEmpty()) p.text(sourceLines(item), paint(8.5f, color = 0xFF666666.toInt()), after = 18f)
                else p.gap(12f)
            }
            if (items.size == 1) images.getOrNull(1)?.let { p.image(it) }
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
            return doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()).also { page = it }
        }

        private fun finishPage(pg: PdfDocument.Page) {
            val num = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = 0xFF999999.toInt() }
            pg.canvas.drawText("$n", W / 2f, H - M / 2, num)
            doc.finishPage(pg)
        }

        fun text(s: String, paint: TextPaint, after: Float, spacing: Float = 1.2f) {
            if (s.isBlank()) return
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, inner).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, spacing).build()
            var line = 0
            while (line < layout.lineCount) {
                canvas // a page exists
                val room = H - M - y
                var last = line
                while (last < layout.lineCount && layout.getLineBottom(last) - layout.getLineTop(line) <= room) last++
                if (last == line) { newPage(); continue }
                val top = layout.getLineTop(line).toFloat()
                val c = canvas
                c.save()
                c.translate(M, y - top)
                c.clipRect(0f, top, inner.toFloat(), layout.getLineBottom(last - 1).toFloat())
                layout.draw(c)
                c.restore()
                y += layout.getLineBottom(last - 1) - top
                line = last
                if (line < layout.lineCount) newPage()
            }
            y += after
        }

        fun image(f: File) {
            val bmp = BitmapFactory.decodeFile(f.path) ?: return
            val side = 300f
            if (H - M - y < side + 16) newPage()
            val left = (W - side) / 2
            canvas.drawBitmap(bmp, null, RectF(left, y, left + side, y + side), Paint(Paint.FILTER_BITMAP_FLAG))
            y += side + 20
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
