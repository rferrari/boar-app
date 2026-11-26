package team.sopa.boar.engine.labs

/**
 * Boar Designer's canvas, the parts that are plain logic (adam's mock-up, Nov 13): the one size it draws at, the saved
 * default and its migration, the caption under the picture, the toolbar's tools, the crop, and the four variations drawn
 * one after another. The app (plugins/designer) draws the screen; this is what it decides with.
 *
 * One size since BOAR-88 (adam, Nov 26: "keep only squared draw and add crop as a tool"): SDXS-512 is trained at 512x512,
 * and t9 measured on the F3 (same seeds) that 640 drew two mouths and a three-eared mouse and 448x640 a distorted face,
 * while 512 was clean. Other shapes come from cropping a copy. Pixel sizes stay inside: the screen never shows them.
 */
object DesignerCanvas {
    /** An old drawing's shape, or a crop's (from its size); [key]: strings native.labs.image.shape.<key>. */
    enum class Shape(val key: String) { SQUARE("square"), PORTRAIT("portrait"), WIDE("banner") }

    /** A drawing's size in pixels. [key]: "512" for a square (the key older saves and times used), "384x512" otherwise. */
    data class Size(val w: Int, val h: Int) {
        val key: String get() = if (w == h) "$w" else "${w}x$h"
        val shape: Shape get() = when { w == h -> Shape.SQUARE; h > w -> Shape.PORTRAIT; else -> Shape.WIDE }
    }

    /** The model's own size, the only one Designer draws at. */
    val SIZE: Size = Size(512, 512)

    /** What the screen offers: [SIZE] alone. */
    val OFFERED: List<Size> = listOf(SIZE)

    /** A fresh install, and Reset. */
    val DEFAULT: Size = SIZE

    /** "512" or "384x512" (a recipe's, a saved default's or a file name's key) to a size; null for anything else. */
    fun parse(key: String?): Size? {
        val k = key?.trim().orEmpty()
        k.toIntOrNull()?.takeIf { it > 0 }?.let { return Size(it, it) }
        val m = Regex("^(\\d+)[x×](\\d+)$").find(k) ?: return null
        val w = m.groupValues[1].toInt(); val h = m.groupValues[2].toInt()
        return if (w > 0 && h > 0) Size(w, h) else null
    }

    /**
     * A saved size key (a default, a recipe's size for Draw again) to the one Designer draws at: any size becomes [SIZE],
     * silently (BOAR-88). Null when [key] is no size at all, so the caller falls back to [DEFAULT]. An old drawing keeps
     * its own pixels and file name; only what is drawn next is 512.
     */
    fun migrate(key: String?): Size? = parse(key)?.let { SIZE }

    /** Where the default is kept (LabPrefs): the style's key and the size's key, saved together by "★ Default". */
    const val STYLE_PREF = "image.style"
    const val SIZE_PREF = "image.size"

    /** The style picked, saved with the one size (an older 640 or 384x512 is written over on the next pick). */
    fun saveDefault(put: (String, String) -> Unit, styleKey: String) {
        put(STYLE_PREF, styleKey)
        put(SIZE_PREF, SIZE.key)
    }

    /** The saved default: the style key (null when never saved) and the size, migrated; [DEFAULT] when none. */
    fun loadDefault(get: (String) -> String?): Pair<String?, Size> = get(STYLE_PREF) to (migrate(get(SIZE_PREF)) ?: DEFAULT)

    /** The line under the picture: "Engraving · 9 s", with this phone's own average at 512; the style alone until timed. */
    fun caption(style: String, seconds: Int?): String = style + (seconds?.let { " · $it s" } ?: "")

    /** Designer's toolbar, above the input bar (adam, Nov 14; Shape and Quality gone with BOAR-88). */
    enum class Section(val key: String) { STYLE("style") }

    /** How many "4 variations" draws. */
    const val VARIATIONS = 4

    /** Why a run of variations ended: all drawn, one failed, or too little free memory before the next. */
    enum class Stop { DONE, FAILED, LOW_MEMORY }

    data class Outcome<T>(val drawn: List<T>, val stop: Stop)

    /**
     * Draws [count] variations one after another, never two at once. Before each it checks [enoughMemory] (the same gate
     * as Illustrate); the first failure ends the run, and what was drawn before it is kept (each reaches [onDrawn] as it
     * lands). Cancelling the coroutine stops it too, with the same pictures kept: cancellation is rethrown, not a failure.
     */
    suspend fun <T> variations(count: Int = VARIATIONS, enoughMemory: () -> Boolean, onDrawn: (T) -> Unit = {}, draw: suspend (index: Int) -> T): Outcome<T> {
        val drawn = ArrayList<T>()
        for (i in 0 until count) {
            if (!enoughMemory()) return Outcome(drawn, Stop.LOW_MEMORY)
            val r = try { draw(i) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) { return Outcome(drawn, Stop.FAILED) }
            drawn += r
            onDrawn(r)
        }
        return Outcome(drawn, Stop.DONE)
    }

    /** "~N s" for the variations row: four times this phone's average, or null before it has one. */
    fun variationsSeconds(average: Int?): Int? = average?.let { it * VARIATIONS }
}
