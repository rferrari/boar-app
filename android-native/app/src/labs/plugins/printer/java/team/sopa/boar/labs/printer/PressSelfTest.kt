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
        // A photo as Add puts one: copied into its (self-test) project's folder, which goes at the end.
        val photoDir = PressStore.projectDir(ctx, "selftest-Sides")
        team.sopa.boar.engine.labs.PressFiles.copyInto(PressStore.dir(ctx), "selftest-Sides", PHOTO) { out -> wide.inputStream().use { it.copyTo(out) } }
        var ok = 0; var fail = 0
        for ((name, project) in projects(square, tall, wide)) {
            // adb args "font=sans;footer=on" try the Press settings without changing the user's (adam, 27 Nov).
            val opts = PressPage.options().let { d -> d.copy(font = team.sopa.boar.engine.labs.PressFont.of(args["font"] ?: d.font.key),
                pageFooter = args["footer"]?.let { it == "on" } ?: d.pageFooter) }
            val page = PressPage.of(ctx, project, options = opts)
            val out = File(dir, "$name.pdf")
            runCatching {
                PdfCompiler.compile(out, page) { PressPictures.fileOf(ctx, project.id, it) }
                pages(out)
            }.onSuccess { n ->
                if (n > 0) { ok++; say("ok press $name: $n pages, ${out.length() / 1024} KB") } else { fail++; say("FAIL press $name: no pages") }
            }.onFailure { fail++; say("FAIL press $name: ${it.javaClass.simpleName} ${it.message}") }
            runCatching {
                val md = page.markdown(drawingLabel, team.sopa.boar.ui.I18n.t(ctx, "native.labs.printer.mdPhoto"))
                File(dir, "$name.md").writeText(md)
                val missing = project.answers.filterNot { PressPageText.oneLine(it.shownHeading) in md }.map { it.id }
                missing + listOfNotNull("footer".takeIf { page.footer.any { it !in md } })
            }.onSuccess { missing -> if (missing.isEmpty()) { ok++; say("ok press $name.md") } else { fail++; say("FAIL press $name.md: missing $missing") } }
                .onFailure { fail++; say("FAIL press $name.md: ${it.javaClass.simpleName} ${it.message}") }
        }
        photoDir.deleteRecursively()
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

    private const val PHOTO = "selftest-photo.png"

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
            Block.Paragraph("t1", "A paragraph of plain body text, not a note."),
            Block.Divider("d1"),
            Block.Bullets("l1", "Salt\nWater\nSun"),
        )),
        // Phase 2: pictures left and right with the next text beside them, a heading after a side picture, a photo.
        "sides" to project("Sides", listOf(
            picture(tall, 10, "On the left, a caption that wraps over two lines at least").copy(place = PressDoc.Place.LEFT),
            answer(10, "Why is the sea salty?", ANSWER + "\n\n" + ANSWER),
            picture(wide, 11).copy(place = PressDoc.Place.RIGHT),
            Block.Note("n10", "A note beside a wide picture on the right."),
            picture(square, 12, "Before a heading").copy(place = PressDoc.Place.RIGHT),
            Block.Heading("h10", "A heading never starts beside a picture"),
            answer(11, "Short?", "Yes."),
            Block.Picture("photo", PressDoc.Kind.PHOTO, PHOTO, caption = "A photo copied into the project", place = PressDoc.Place.LEFT, width = 640, height = 320),
            answer(12, "Tide pools", ANSWER),
            picture(tall, 13).copy(place = PressDoc.Place.LEFT),
        )),
        // Pictures small, medium and large (adam, 27 Nov), centred and beside text.
        "sizes" to project("Picture sizes", listOf(
            picture(square, 31, "Small").copy(size = PressDoc.PicSize.SMALL),
            picture(square, 32, "Medium").copy(size = PressDoc.PicSize.MEDIUM),
            picture(square, 33, "Large").copy(size = PressDoc.PicSize.LARGE),
            picture(tall, 34, "Small, left").copy(place = PressDoc.Place.LEFT, size = PressDoc.PicSize.SMALL),
            answer(31, "Beside a small picture", ANSWER + "\n\n" + ANSWER),
            picture(tall, 35, "Medium, right").copy(place = PressDoc.Place.RIGHT, size = PressDoc.PicSize.MEDIUM),
            answer(32, "Beside a medium picture", ANSWER + "\n\n" + ANSWER),
        )),
        // The same document before and after a layout change (t9 0735: lines per page, pages): four long answers in
        // paragraphs, one with a list, one followed by a drawing.
        "spacing" to project("Spacing basket", listOf(
            answer(21, "How did the Roman aqueducts carry water?", PARAS),
            answer(22, "What should a hiking first-aid kit hold?", PARAS.substringBefore("\n\n") + "\n\n" + KIT + "\n\n" + PARAS.substringAfterLast("\n\n")),
            answer(23, "Why do leaves change colour in autumn?", PARAS),
            picture(square, 23, "A drawing after the third answer"),
            answer(24, "How does a lighthouse lens work?", PARAS + "\n\n" + PARAS.substringBefore("\n\n")),
        )),
    )

    /** Five paragraphs of about 70 words each: the long answer of the spacing basket. */
    private val PARAS = (1..5).joinToString("\n\n") { p ->
        "Paragraph $p. " + (1..9).joinToString(" ") { "The engineers measured each slope with care, and the water moved slowly downhill over long distances." }.take(430) + "."
    }

    /** A list with blank lines between its items, as models often write them. */
    private val KIT = (1..8).joinToString("\n\n") { "- Item $it: a short line about what it is for" }
}
