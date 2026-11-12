package team.sopa.boar.engine.labs

/**
 * Boar Designer's canvas, the parts that are plain logic (adam's mock-up, Nov 13): a picture's shape and speed instead
 * of its pixel size, the saved default and its migration, the caption under the picture, the two rail tools, and the
 * four variations drawn one after another. The app (plugins/designer) draws the screen; this is what it decides with.
 *
 * Pixel sizes stay inside: sd-cli takes W×H and the recipe files keep it. The screen only ever says Square, Portrait or
 * Wide, and Fast or Slow.
 */
object DesignerCanvas {
    /** [key]: the shape's word key (strings native.labs.image.shape.<key>); "banner" is the old key of Wide, kept. */
    enum class Shape(val key: String) { SQUARE("square"), PORTRAIT("portrait"), WIDE("banner") }

    /** Fast is the middle size of a shape, Slow the largest. The smallest sizes left the screen (adam, Nov 13). */
    enum class Tier(val key: String) { FAST("fast"), SLOW("slow") }

    /** A drawing's size in pixels. [key]: "512" for a square (the key older saves and times used), "384x512" otherwise. */
    data class Size(val w: Int, val h: Int) {
        val key: String get() = if (w == h) "$w" else "${w}x$h"
        val shape: Shape get() = when { w == h -> Shape.SQUARE; h > w -> Shape.PORTRAIT; else -> Shape.WIDE }
        /** Slow when it is its shape's largest size; anything else (the middle size, or a removed small one) reads as Fast. */
        val tier: Tier get() = if (this == size(shape, Tier.SLOW)) Tier.SLOW else Tier.FAST
    }

    /** The size [shape] draws at in [tier]. */
    fun size(shape: Shape, tier: Tier): Size = when (shape) {
        Shape.SQUARE -> if (tier == Tier.FAST) Size(512, 512) else Size(640, 640)
        Shape.PORTRAIT -> if (tier == Tier.FAST) Size(384, 512) else Size(448, 640)
        Shape.WIDE -> if (tier == Tier.FAST) Size(640, 320) else Size(768, 320)
    }

    /** The six sizes the screen offers, shape by shape, Fast first. */
    val OFFERED: List<Size> = Shape.entries.flatMap { s -> Tier.entries.map { t -> size(s, t) } }

    /** The smallest size of each shape, gone from the screen (Nov 13). A saved default of one becomes its shape's Fast. */
    val REMOVED: List<Size> = listOf(Size(384, 384), Size(320, 448), Size(576, 256))

    /** Square and Fast: a fresh install, and Reset. */
    val DEFAULT: Size = size(Shape.SQUARE, Tier.FAST)

    /** "512" or "384x512" (a recipe's or a saved default's key) to a size; null for anything else. */
    fun parse(key: String?): Size? {
        val k = key?.trim().orEmpty()
        k.toIntOrNull()?.takeIf { it > 0 }?.let { return Size(it, it) }
        val m = Regex("^(\\d+)[x×](\\d+)$").find(k) ?: return null
        val w = m.groupValues[1].toInt(); val h = m.groupValues[2].toInt()
        return if (w > 0 && h > 0) Size(w, h) else null
    }

    /**
     * A saved size key to one the screen offers: an offered size stays, a removed one (or any other) becomes Fast of the
     * same shape. Null when [key] is no size at all, so the caller falls back to [DEFAULT].
     */
    fun migrate(key: String?): Size? = parse(key)?.let { s -> size(s.shape, s.tier) }

    /** Where the default is kept (LabPrefs): the style's key and the size's key, saved together by "★ Default". */
    const val STYLE_PREF = "image.style"
    const val SIZE_PREF = "image.size"

    /** "★ Default": style, shape and speed together, as two keys. */
    fun saveDefault(put: (String, String) -> Unit, styleKey: String, picked: Size) {
        put(STYLE_PREF, styleKey)
        put(SIZE_PREF, size(picked.shape, picked.tier).key)
    }

    /** The saved default: the style key (null when never saved) and the size, migrated; [DEFAULT] when none. */
    fun loadDefault(get: (String) -> String?): Pair<String?, Size> = get(STYLE_PREF) to (migrate(get(SIZE_PREF)) ?: DEFAULT)

    /**
     * The line under the picture: "Engraving · Square · 9 s", with this phone's own average for that size, or the speed
     * word ("Fast") until it was drawn once at that size. Words come translated from the app.
     */
    fun caption(style: String, shape: String, seconds: Int?, speed: String): String =
        "$style · $shape · " + (seconds?.let { "$it s" } ?: speed)

    /** The rail under the prompt bar: Style and Shape, nothing else (adam, Nov 13: no Seed, Edit or More). */
    enum class Tool(val key: String) { STYLE("style"), SHAPE("shape") }

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
