package team.sopa.boar.labs.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import team.sopa.boar.engine.labs.PressDoc
import team.sopa.boar.engine.labs.PressDoc.Block
import java.io.File

/**
 * Boar Press's self-test (adb: run=labtest, lab=printer): compiles made-up projects (empty, one answer, 30 answers with
 * drawings, a very long answer, Arabic, Chinese and emoji, an empty title, every block type with a missing drawing) as PDF
 * and Markdown, then opens each PDF with Android's renderer and checks the receipt is in the Markdown. The files stay in
 * Android/data/<app>/files/labtest/press for the PC. The user's projects aren't touched.
 */
internal object PressSelfTest {
    suspend fun run(ctx: Context, args: Map<String, String>, say: (String) -> Unit) {
        val dir = File(ctx.getExternalFilesDir(null), "labtest/press").apply { deleteRecursively(); mkdirs() }
        val square = File(dir, "square.png").also { f -> f.outputStream().use { sample(384, 384).compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val tall = File(dir, "tall.png").also { f -> f.outputStream().use { sample(384, 512).compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val wide = File(dir, "wide.png").also { f -> f.outputStream().use { sample(640, 320).compress(Bitmap.CompressFormat.PNG, 100, it) } }
        File(tall.path + ".json").writeText("""{"subject":"a boar","style":"engraving","size":"384x512","seed":48213}""")
        val drawingLabel = team.sopa.boar.ui.I18n.t(ctx, "native.labs.printer.mdDrawing")
        var ok = 0; var fail = 0
        for ((name, project) in projects(square, tall, wide)) {
            val page = PressPage.of(ctx, project)
            val out = File(dir, "$name.pdf")
            runCatching {
                PdfCompiler.compile(out, page) { PressPictures.fileOf(ctx, project.id, it) }
                pages(out)
            }.onSuccess { n ->
                if (n > 0) { ok++; say("ok press $name: $n pages, ${out.length() / 1024} KB") } else { fail++; say("FAIL press $name: no pages") }
            }.onFailure { fail++; say("FAIL press $name: ${it.javaClass.simpleName} ${it.message}") }
            runCatching {
                val md = page.markdown(drawingLabel)
                File(dir, "$name.md").writeText(md)
                val missing = project.answers.filterNot { PressPageText.oneLine(it.shownHeading) in md }.map { it.id }
                missing + listOfNotNull("receipt".takeIf { page.labels.title !in md || page.labels.footer !in md })
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

    private fun sample(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); c.drawColor(Color.rgb(34, 25, 19))
        c.drawCircle(w / 2f, h / 2f, minOf(w, h) / 3f, Paint().apply { color = Color.rgb(255, 122, 61) })
    }

    private fun answer(n: Int, question: String, text: String, sources: Int = 2) = Block.Answer(
        "selftest-$n", "Self-test", question, text, (1..sources).map { PressDoc.Source("Article ${(n + it) % 7}", "Wikipedia") },
        1_760_000_000_000L + n, if (n % 2 == 0) "Qwen3-4B" else "Qwen3-1.7B", 4.0 + n % 5,
    )

    private fun picture(f: File, n: Int, caption: String = "") = Block.Picture("pic-$n", PressDoc.Kind.DRAWING, f.path, caption = caption,
        width = PressPictures.sizeOf(f)?.first ?: 0, height = PressPictures.sizeOf(f)?.second ?: 0)

    private val ANSWER = "Boil water for at least one minute (three minutes above 2,000 m) [1], let it cool covered, and store it in a clean container [2]. " +
        "**Filtering** first through a clean cloth removes sediment.\n\n- Use a lid\n- Keep it shaded\n\n1. Boil\n2. Cool\n3. Store"

    private fun project(name: String, blocks: List<Block>) = PressDoc.Project("selftest-$name", name, 0, blocks)

    private fun projects(square: File, tall: File, wide: File): List<Pair<String, PressDoc.Project>> = listOf(
        "empty" to project("Empty project", emptyList()),
        "one" to project("Field notes", listOf(answer(1, "How do I make water safe to drink?", ANSWER))),
        "thirty" to project("Thirty answers", (1..30).flatMap { n ->
            listOfNotNull(answer(n, "Question $n: how do I make water safe?", ANSWER), when (n % 4) { 0 -> picture(square, n, "Drawing $n"); 1 -> picture(tall, n); 2 -> picture(wide, n); else -> null })
        }),
        "long" to project("A very long answer", listOf(answer(1, "Tell me everything about knots.", (1..400).joinToString(" ") { "Knot $it holds under load when dressed and set." }))),
        "scripts" to project("Scripts", listOf(
            answer(1, "ما هي عاصمة مصر؟", "عاصمة مصر هي القاهرة، وهي أكبر مدينة في العالم العربي."),
            answer(2, "中国的首都是哪里？", "中国的首都是北京。"),
            answer(3, "Emoji 🐗🔥⛺", "The boar 🐗 sits by the fire 🔥 near the tent ⛺. Flags: 🇧🇷🇯🇵"),
        )),
        "notitle" to project("", listOf(answer(1, "Untitled project?", ANSWER, sources = 0))),
        "blocks" to project("Every block", listOf(
            Block.Heading("h1", "Day 1: the coast"),
            answer(1, "Why is the sea salty?", ANSWER).copy(heading = "Salty seas"),
            picture(tall, 1, "A boar, engraved"),
            Block.Note("n1", "Ask about the lighthouse tomorrow."),
            Block.Picture("gone", PressDoc.Kind.DRAWING, File(square.parentFile, "deleted.png").path),
            Block.Heading("h2", (1..40).joinToString(" ") { "long" }),
        )),
    )
}
