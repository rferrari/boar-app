package team.sopa.boar.engine.labs

/**
 * Boar Designer's canvas, the parts that are plain logic (adam's mock-up, Nov 13): a picture's shape and quality
 * instead of its pixel size, the saved default and its migration, the caption under the picture, the drawer's three sections,
 * and the four variations drawn one after another. The app (plugins/designer) draws the screen; this is what it decides
 * with.
 *
 * Pixel sizes stay inside: sd-cli takes W×H and the recipe files keep it. The screen only ever says Square, Portrait or
 * Wide, and Low, Medium or High quality.
 */
object DesignerCanvas {
    /** [key]: the shape's word key (strings native.labs.image.shape.<key>); "banner" is the old key of Wide, kept. */
    enum class Shape(val key: String) { SQUARE("square"), PORTRAIT("portrait"), WIDE("banner") }

    /**
     * The three sizes of a shape, smallest to largest (adam, Nov 12: "bring back 3 quality image modes"). Replaces Fast
     * and Slow: Fast was Medium, Slow was High, and the smallest sizes are back as Low.
     */
    enum class Quality(val key: String) { LOW("low"), MEDIUM("medium"), HIGH("high") }

    /** A drawing's size in pixels. [key]: "512" for a square (the key older saves and times used), "384x512" otherwise. */
    data class Size(val w: Int, val h: Int) {
        val key: String get() = if (w == h) "$w" else "${w}x$h"
        val shape: Shape get() = when { w == h -> Shape.SQUARE; h > w -> Shape.PORTRAIT; else -> Shape.WIDE }
        /** Its own level among its shape's three sizes; a size that is none of them reads as Medium. */
        val quality: Quality get() = Quality.entries.firstOrNull { size(shape, it) == this } ?: Quality.MEDIUM
    }

    /** The size [shape] draws at in [quality]. */
    fun size(shape: Shape, quality: Quality): Size = when (shape) {
        Shape.SQUARE -> when (quality) { Quality.LOW -> Size(384, 384); Quality.MEDIUM -> Size(512, 512); Quality.HIGH -> Size(640, 640) }
        Shape.PORTRAIT -> when (quality) { Quality.LOW -> Size(320, 448); Quality.MEDIUM -> Size(384, 512); Quality.HIGH -> Size(448, 640) }
        Shape.WIDE -> when (quality) { Quality.LOW -> Size(576, 256); Quality.MEDIUM -> Size(640, 320); Quality.HIGH -> Size(768, 320) }
    }

    /** The nine sizes the screen offers, shape by shape, Low first. */
    val OFFERED: List<Size> = Shape.entries.flatMap { s -> Quality.entries.map { q -> size(s, q) } }

    /** Square, Medium: a fresh install, and Reset (the Fast square of Nov 13). */
    val DEFAULT: Size = size(Shape.SQUARE, Quality.MEDIUM)

    /** The words the Nov 13 build showed, as levels now: Fast was the middle size, Slow the largest. */
    fun fromSpeed(word: String?): Quality? = when (word?.trim()?.lowercase()) { "fast" -> Quality.MEDIUM; "slow" -> Quality.HIGH; else -> null }

    /** "512" or "384x512" (a recipe's or a saved default's key) to a size; null for anything else. */
    fun parse(key: String?): Size? {
        val k = key?.trim().orEmpty()
        k.toIntOrNull()?.takeIf { it > 0 }?.let { return Size(it, it) }
        val m = Regex("^(\\d+)[x×](\\d+)$").find(k) ?: return null
        val w = m.groupValues[1].toInt(); val h = m.groupValues[2].toInt()
        return if (w > 0 && h > 0) Size(w, h) else null
    }

    /**
     * A saved size key to one the screen offers: each of the nine keeps its own level (what Nov 13 saved as Fast is the
     * middle size, so Medium; Slow the largest, so High); any other size becomes Medium of its shape. Null when [key] is
     * no size at all, so the caller falls back to [DEFAULT].
     */
    fun migrate(key: String?): Size? = parse(key)?.let { s -> size(s.shape, s.quality) }

    /** Where the default is kept (LabPrefs): the style's key and the size's key, saved together by "★ Default". */
    const val STYLE_PREF = "image.style"
    const val SIZE_PREF = "image.size"

    /** "★ Default": style, shape and quality together, as two keys. */
    fun saveDefault(put: (String, String) -> Unit, styleKey: String, picked: Size) {
        put(STYLE_PREF, styleKey)
        put(SIZE_PREF, size(picked.shape, picked.quality).key)
    }

    /** The saved default: the style key (null when never saved) and the size, migrated; [DEFAULT] when none. */
    fun loadDefault(get: (String) -> String?): Pair<String?, Size> = get(STYLE_PREF) to (migrate(get(SIZE_PREF)) ?: DEFAULT)

    /**
     * The line under the picture: "Engraving · Square · 9 s", with this phone's own average for that size, or the
     * quality ("Low quality") until it was drawn once at that size. Words come translated from the app.
     */
    fun caption(style: String, shape: String, seconds: Int?, quality: String): String =
        "$style · $shape · " + (seconds?.let { "$it s" } ?: quality)

    /**
     * Designer's drawer, opened by the input bar's left button (adam, Nov 12: no tool rail): Style, Shape and Quality,
     * nothing else (no Seed, Edit or More).
     */
    enum class Section(val key: String) { STYLE("style"), SHAPE("shape"), QUALITY("quality") }

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
