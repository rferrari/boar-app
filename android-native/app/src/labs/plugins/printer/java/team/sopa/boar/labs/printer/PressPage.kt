package team.sopa.boar.labs.printer

import android.content.Context
import team.sopa.boar.engine.labs.PressDoc
import team.sopa.boar.engine.labs.PressReceipt
import team.sopa.boar.ui.I18n
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * The paper's measures, in PDF points (A4: 595 × 842). The screen draws the same page scaled to its width, so the white
 * page in the app is what the PDF prints (adam's mock-up, Nov 13); its line breaks are close, its page breaks approximate.
 */
object Paper {
    const val W = 595f
    const val H = 842f
    const val M = 40f
    val INNER = W - 2 * M

    const val TITLE = 30f
    const val DATE = 11.5f
    const val HEADING = 21f
    const val QUESTION = 15f
    const val BODY = 16f
    /** Line height over font size, on the screen; the PDF's StaticLayout gets the same height through [PDF_SPACING]. */
    const val LINE = 1.4f
    const val PDF_SPACING = 1.2f
    const val REFS = 12f
    const val NOTE = 15f
    const val CAPTION = 13f
    const val RECEIPT = 11.5f
    const val RECEIPT_TITLE = 12.5f
    const val RECEIPT_LABEL_W = 80f
    const val FOOT = 11f
    const val GAP = 14f
    /** The tallest a picture gets: about half a page. */
    const val PICTURE_MAX_H = 380f

    const val INK = 0xFF1D1D1F.toInt()
    const val MUTED = 0xFF55596A.toInt()
    const val META = 0xFF7A7F90.toInt()
    const val DATE_INK = 0xFF6B7080.toInt()
    const val RULE = 0xFFD5D8E2.toInt()
    const val RECEIPT_INK = 0xFF5D6273.toInt()
    const val LABEL_INK = 0xFF3A3E4C.toInt()
    const val NOTE_BAR = 0xFFD0D4E0.toInt()
    const val FOOT_INK = 0xFF9A9EAC.toInt()
}

/** One project as it prints: the date, the receipt and the words, the same for the paper, the PDF and the Markdown. */
class PressPage(
    val project: PressDoc.Project,
    val date: String,
    val version: String,
    val receipt: PressReceipt.Receipt,
    val labels: PressReceipt.Labels,
    val sourcesLabel: String,
) {
    val rows: List<PressReceipt.Row> get() = PressReceipt.rows(receipt, labels, date, version)

    /** An answer's text as it prints: plain, its [k] marks as the document's numbers. */
    fun answerText(a: PressDoc.Block.Answer) = PressReceipt.plain(PressReceipt.renumber(a.text, receipt.refs[a.id].orEmpty()))

    fun refsLine(a: PressDoc.Block.Answer) = PressReceipt.refsLine(receipt.refs[a.id], sourcesLabel)

    fun markdown(drawingLabel: String) = PressReceipt.markdown(project, labels, date, version, sourcesLabel, drawingLabel, receipt)

    companion object {
        fun dateText(): String = DateFormat.getDateInstance(DateFormat.LONG).format(Date())

        fun labels(ctx: Context) = PressReceipt.Labels(
            title = I18n.t(ctx, "native.labs.printer.receipt"),
            made = I18n.t(ctx, "native.labs.printer.rMade"),
            madeValue = I18n.t(ctx, "native.labs.printer.rMadeValue", "date" to "{{date}}", "version" to "{{version}}"),
            answers = I18n.t(ctx, "native.labs.printer.rAnswers"),
            drawings = I18n.t(ctx, "native.labs.printer.rDrawings"),
            photos = I18n.t(ctx, "native.labs.printer.rPhotos"),
            photosValue = I18n.t(ctx, "native.labs.printer.rPhotosValue", "count" to "{{count}}"),
            sources = I18n.t(ctx, "native.labs.printer.rSources"),
            footer = I18n.t(ctx, "native.labs.printer.footer"),
            designer = I18n.t(ctx, "native.labs.printer.rDesigner"),
            seed = I18n.t(ctx, "native.labs.printer.rSeed", "seed" to "{{seed}}"),
            square = I18n.t(ctx, "native.labs.printer.rSquare"),
            portrait = I18n.t(ctx, "native.labs.printer.rPortrait"),
            wide = I18n.t(ctx, "native.labs.printer.rWide"),
            // Designer's own name for the style when this build has it; else the key, capitalised.
            styleName = { k ->
                val key = "native.labs.image.style.$k"
                I18n.t(ctx, key).takeIf { it != key && it.isNotBlank() } ?: k.replaceFirstChar { it.titlecase(Locale.ROOT) }
            },
        )

        /** Reads Designer's recipes and the pictures' sizes: off the main thread. */
        fun of(ctx: Context, project: PressDoc.Project, date: String = dateText()): PressPage {
            val receipt = PressReceipt.build(project,
                recipeOf = { p -> if (p.kind == PressDoc.Kind.DRAWING) PressPictures.recipeOf(ctx, p.file, p.answerId) ?: p.recipe else p.recipe },
                sizeOf = { p -> PressPictures.fileOf(ctx, project.id, p)?.let(PressPictures::sizeOf) })
            return PressPage(project, date, team.sopa.boar.BuildConfig.VERSION_NAME, receipt, labels(ctx), I18n.t(ctx, "native.labs.printer.sourcesRefs"))
        }
    }
}
