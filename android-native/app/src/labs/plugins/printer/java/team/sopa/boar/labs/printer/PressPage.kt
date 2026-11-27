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

    // A 12 pt body since t9 0735 (adam, 27 Nov: a page read thin, about 29 lines at 16 pt; he picked 12 pt), the rest
    // scaled with it (about three quarters), margins and page as they were.
    const val TITLE = 23f
    const val DATE = 9f
    const val HEADING = 15.5f
    const val QUESTION = 11.5f
    const val BODY = 12f
    /**
     * Line height over font size, on the screen; the PDF's StaticLayout gets the same height through [PDF_SPACING]:
     * measured 16 pt per line for the 12 pt body (emulator, t9 0735), so 16 / 12.
     */
    const val LINE = 1.333f
    const val PDF_SPACING = 1.15f
    /** Between two paragraphs of a text: a small gap, not a whole empty line (PressSpacing). */
    const val PARA_GAP = 6f
    const val REFS = 9f
    const val NOTE = 11.5f
    const val CAPTION = 10f
    const val RECEIPT = 9f
    const val RECEIPT_TITLE = 9.5f
    const val RECEIPT_LABEL_W = 80f
    const val FOOT = 8.5f
    /** The document's footer: small, light, centered (adam, Nov 13: it read as part of the document). */
    const val FOOTER = 7.5f
    /** Each PDF page's foot (page number, and the footer line when on): 7.5 pt, as adam's sample PDF. */
    const val PAGE_FOOT = 7.5f
    const val GAP = 10f
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
    /** A card's bars on the paper (Cards, t9 0207 item 4): the ink's grey, so a black-and-white print still reads. */
    const val BAR = 0xFF6B7080.toInt()
    const val BAR_TRACK = 0xFFE8EAF0.toInt()
    /** A card's text: a little smaller than the answer's. */
    const val CARD = 10f
    const val CARD_TITLE = 10.5f
}

/** One project as it prints: the date, the receipt and the words, the same for the paper, the PDF and the Markdown. */
class PressPage(
    val project: PressDoc.Project,
    val date: String,
    val version: String,
    val receipt: PressReceipt.Receipt,
    val labels: PressReceipt.Labels,
    val sourcesLabel: String,
    /** What prints besides the blocks (Press settings, adam Nov 14). */
    val options: PressReceipt.Options = PressReceipt.Options(),
    /** The line under a card the model made ("made by the model · checked against the answer"). */
    val cardByModel: String = "",
    /** "Page {{n}}" at the foot of each PDF page (when on). */
    val pageLabel: String = "Page {{n}}",
) {
    val rows: List<PressReceipt.Row> get() = PressReceipt.rows(receipt, labels, date, version)

    /** The Sources section at the end, one line per source with its pack; empty when off or there are none. */
    val sourceLines: List<String> get() = PressReceipt.sourceLines(receipt, options, labels.ownFile)

    /** The footer: the models' line when on, and "Made offline with BOAR", always (PressReceipt.footer). */
    val footer: List<String> get() = PressReceipt.footer(receipt, labels, date, options)

    /** An answer's text as it prints: plain, its [k] marks as the document's numbers (none when sources are off). */
    fun answerText(a: PressDoc.Block.Answer) =
        PressReceipt.plain(PressReceipt.renumber(a.text, if (options.sources) receipt.local[a.id].orEmpty() else emptyMap()))

    fun refsLine(a: PressDoc.Block.Answer) = if (options.sources) PressReceipt.refsLine(receipt.refs[a.id], sourcesLabel) else null

    fun markdown(drawingLabel: String, photoLabel: String = drawingLabel) =
        PressReceipt.markdown(project, labels, date, version, sourcesLabel, drawingLabel, receipt, photoLabel, options)

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
            madeWith = I18n.t(ctx, "native.labs.printer.madeWith", "date" to "{{date}}"),
            designer = I18n.t(ctx, "native.labs.printer.rDesigner"),
            seed = I18n.t(ctx, "native.labs.printer.rSeed", "seed" to "{{seed}}"),
            square = I18n.t(ctx, "native.labs.printer.rSquare"),
            portrait = I18n.t(ctx, "native.labs.printer.rPortrait"),
            wide = I18n.t(ctx, "native.labs.printer.rWide"),
            low = I18n.t(ctx, "native.labs.printer.rLow"),
            ownFile = I18n.t(ctx, "native.labs.printer.rOwnFile"),
            medium = I18n.t(ctx, "native.labs.printer.rMedium"),
            high = I18n.t(ctx, "native.labs.printer.rHigh"),
            // Designer's own name for the style when this build has it; else the key, capitalised.
            styleName = { k ->
                val key = "native.labs.image.style.$k"
                I18n.t(ctx, key).takeIf { it != key && it.isNotBlank() } ?: k.replaceFirstChar { it.titlecase(Locale.ROOT) }
            },
        )

        /** Press settings (adam, Nov 14): the date, the Sources section, the models and their speed; global, like page breaks. */
        const val DATE_PREF = "printer.showDate"
        const val SOURCES_PREF = "printer.showSources"
        const val MODELS_PREF = "printer.showModels"
        const val SPEED_PREF = "printer.showSpeed"
        /** The document's font, and each page's foot (adam, 27 Nov). */
        const val FONT_PREF = "printer.font"
        const val PAGE_NUMBERS_PREF = "printer.pageNumbers"
        const val FOOTER_PREF = "printer.pageFooter"

        private fun pref(key: String, default: Boolean) = when (team.sopa.boar.labs.LabPrefs.string(key)) { "on" -> true; "off" -> false; else -> default }

        fun options(): PressReceipt.Options = PressReceipt.Options().let { d ->
            PressReceipt.Options(pref(DATE_PREF, d.date), pref(SOURCES_PREF, d.sources), pref(MODELS_PREF, d.models), pref(SPEED_PREF, d.speed),
                team.sopa.boar.engine.labs.PressFont.of(team.sopa.boar.labs.LabPrefs.string(FONT_PREF)), pref(PAGE_NUMBERS_PREF, d.pageNumbers), pref(FOOTER_PREF, d.pageFooter))
        }

        fun saveOptions(o: PressReceipt.Options) {
            for ((k, v) in listOf(DATE_PREF to o.date, SOURCES_PREF to o.sources, MODELS_PREF to o.models, SPEED_PREF to o.speed,
                    PAGE_NUMBERS_PREF to o.pageNumbers, FOOTER_PREF to o.pageFooter))
                team.sopa.boar.labs.LabPrefs.putString(k, if (v) "on" else "off")
            team.sopa.boar.labs.LabPrefs.putString(FONT_PREF, o.font.key)
        }

        /** Reads Designer's recipes and the pictures' sizes: off the main thread. */
        fun of(ctx: Context, project: PressDoc.Project, date: String = dateText(), options: PressReceipt.Options = options()): PressPage {
            val receipt = PressReceipt.build(project,
                recipeOf = { p -> if (p.kind == PressDoc.Kind.DRAWING) PressPictures.recipeOf(ctx, p.file, p.answerId) ?: p.recipe else p.recipe },
                sizeOf = { p -> PressPictures.fileOf(ctx, project.id, p)?.let(PressPictures::sizeOf) })
            return PressPage(project, date, team.sopa.boar.BuildConfig.VERSION_NAME, receipt, labels(ctx), I18n.t(ctx, "native.labs.printer.sourcesRefs"), options,
                I18n.t(ctx, "native.labs.printer.cardByModel"), I18n.t(ctx, "native.labs.printer.pageFoot", "n" to "{{n}}"))
        }
    }
}
