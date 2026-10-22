package team.sopa.boar.labs.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Boar Press's self-test (adb: run=labtest, lab=printer): compiles made-up baskets (empty, one answer, 30 answers, a very
 * long answer, Arabic, Chinese and emoji, an empty title) in every drawing placement, with and without drawings, as PDF and
 * Markdown, then opens each PDF with Android's renderer. The files stay in Android/data/<app>/files/labtest/press for the PC.
 * The user's basket isn't touched.
 */
internal object PressSelfTest {
    suspend fun run(ctx: Context, args: Map<String, String>, say: (String) -> Unit) {
        val dir = File(ctx.getExternalFilesDir(null), "labtest/press").apply { deleteRecursively(); mkdirs() }
        val drawing = File(dir, "drawing.png").also { f -> f.outputStream().use { sample().compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val footer = team.sopa.boar.ui.I18n.t(ctx, "native.labs.printer.footer")
        val labels = team.sopa.boar.engine.chat.ChatExport.Labels(team.sopa.boar.ui.I18n.t(ctx, "native.labs.printer.mdReasoning"), team.sopa.boar.ui.I18n.t(ctx, "sourceFootnotes.title"))
        var ok = 0; var fail = 0
        for ((name, title, items) in baskets()) {
            for (placement in PdfCompiler.Placement.entries) for (drawings in listOf(true, false)) for (first in listOf(false, true)) {
                if (!drawings && (placement != PdfCompiler.Placement.CENTER || first)) continue
                val meta = PdfCompiler.Meta(model = true, images = placement, drawings = drawings, drawingFirst = first)
                val tag = "$name-${placement.name.lowercase()}-${if (drawings) "drawings" else "nodrawings"}${if (first) "-first" else ""}"
                val out = File(dir, "$tag.pdf")
                runCatching {
                    PdfCompiler.compile(out, title, items, { if (drawings) drawing else null }, footer, meta)
                    pages(out)
                }.onSuccess { n ->
                    if (n > 0) { ok++; say("ok press $tag: $n pages, ${out.length() / 1024} KB") } else { fail++; say("FAIL press $tag: no pages") }
                }.onFailure { fail++; say("FAIL press $tag: ${it.javaClass.simpleName} ${it.message}") }
            }
            runCatching {
                val md = MarkdownCompiler.compile(title, items, footer, PdfCompiler.Meta(model = true), labels)
                File(dir, "$name.md").writeText(md)
                items.filterNot { it.question in md }.map { it.id }
            }.onSuccess { missing -> if (missing.isEmpty()) { ok++; say("ok press $name.md") } else { fail++; say("FAIL press $name.md: missing $missing") } }
                .onFailure { fail++; say("FAIL press $name.md: ${it.javaClass.simpleName} ${it.message}") }
        }
        say("press done: $ok ok, $fail failed; files in ${dir.path}")
    }

    private fun pages(f: File): Int = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { r ->
            // Render every page, so a page the renderer can't draw fails here and not on the user's phone.
            for (i in 0 until r.pageCount) r.openPage(i).use { p -> p.render(Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888), null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            r.pageCount
        }
    }

    private fun sample(): Bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); c.drawColor(Color.rgb(34, 25, 19))
        c.drawCircle(192f, 192f, 120f, Paint().apply { color = Color.rgb(255, 122, 61) })
    }

    private fun item(n: Int, question: String, text: String, sources: Int = 2) = Basket.Item(
        id = "selftest-$n", chatTitle = "Self-test", question = question, text = text,
        sources = (1..sources).map { Basket.Source("Article $it", "Wikipedia") }, addedAt = 1_760_000_000_000L + n, model = "Qwen3-4B", tokPerSec = 9.4,
    )

    private val ANSWER = "Boil water for at least one minute (three minutes above 2,000 m), let it cool covered, and store it in a clean container. " +
        "**Filtering** first through a clean cloth removes sediment.\n\n- Use a lid\n- Keep it shaded\n\n1. Boil\n2. Cool\n3. Store"

    private fun baskets(): List<Triple<String, String, List<Basket.Item>>> = listOf(
        Triple("empty", "Empty basket", emptyList()),
        Triple("one", "Field notes", listOf(item(1, "How do I make water safe to drink?", ANSWER))),
        Triple("thirty", "Thirty answers", (1..30).map { item(it, "Question $it: how do I make water safe?", ANSWER) }),
        Triple("long", "A very long answer", listOf(item(1, "Tell me everything about knots.", (1..400).joinToString(" ") { "Knot $it holds under load when dressed and set." }))),
        Triple("scripts", "Scripts", listOf(
            item(1, "ما هي عاصمة مصر؟", "عاصمة مصر هي القاهرة، وهي أكبر مدينة في العالم العربي."),
            item(2, "中国的首都是哪里？", "中国的首都是北京。"),
            item(3, "Emoji 🐗🔥⛺", "The boar 🐗 sits by the fire 🔥 near the tent ⛺. Flags: 🇧🇷🇯🇵"),
        )),
        Triple("notitle", "", listOf(item(1, "Untitled basket?", ANSWER, sources = 0))),
    )
}
