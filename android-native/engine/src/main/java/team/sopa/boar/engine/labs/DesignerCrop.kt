package team.sopa.boar.engine.labs

/**
 * Crop, a Designer tool (BOAR-88, adam Nov 26: "keep only squared draw and add crop as a tool"): Designer draws 512x512
 * only, and a wide or tall picture is a crop of it. 16:9, 4:3, 9:16 or free; the crop is saved as a COPY beside the
 * original (same folder, so it is in the gallery and can go to Press like any drawing), never over it. All in the
 * picture's own pixels; the screen maps its touches to them.
 */
object DesignerCrop {
    /** [w]:[h], or 0:0 for free. */
    enum class Ratio(val key: String, val w: Int, val h: Int) { WIDE("16:9", 16, 9), STANDARD("4:3", 4, 3), TALL("9:16", 9, 16), FREE("free", 0, 0) }

    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    /** The smallest side a crop keeps, in pixels: smaller is a sliver nobody meant. */
    const val MIN_SIDE = 64

    /** The largest rectangle of [ratio], centred; free starts as the whole picture. */
    fun initial(ratio: Ratio, imgW: Int, imgH: Int): Rect {
        if (ratio == Ratio.FREE) return Rect(0, 0, imgW, imgH)
        var w = imgW
        var h = imgW * ratio.h / ratio.w
        if (h > imgH) { h = imgH; w = imgH * ratio.w / ratio.h }
        // Never empty, even for a tiny picture (a 3x1 crop of a crop, CodeRabbit).
        w = w.coerceIn(1, imgW.coerceAtLeast(1)); h = h.coerceIn(1, imgH.coerceAtLeast(1))
        return Rect((imgW - w) / 2, (imgH - h) / 2, w, h)
    }

    /** Dragged by [dx], [dy] pixels: the same size, kept inside the picture. */
    fun move(r: Rect, dx: Int, dy: Int, imgW: Int, imgH: Int): Rect =
        r.copy(x = (r.x + dx).coerceIn(0, (imgW - r.w).coerceAtLeast(0)), y = (r.y + dy).coerceIn(0, (imgH - r.h).coerceAtLeast(0)))

    /**
     * The bottom-right corner dragged by [dw], [dh]: the top-left stays. A ratio follows the larger of the two moves;
     * free takes each on its own. Never past the picture, never below [MIN_SIDE] (or the picture, if it is smaller).
     */
    fun resize(r: Rect, dw: Int, dh: Int, ratio: Ratio, imgW: Int, imgH: Int): Rect {
        val maxW = imgW - r.x
        val maxH = imgH - r.y
        if (ratio == Ratio.FREE) return r.copy(w = (r.w + dw).coerceIn(minOf(MIN_SIDE, maxW), maxW), h = (r.h + dh).coerceIn(minOf(MIN_SIDE, maxH), maxH))
        // From the height: rounded away from the current size, or a 1 px drag rounds back to it and a tall frame never grows
        // under the finger (CodeRabbit on PR 125).
        val wanted = if (kotlin.math.abs(dw) >= kotlin.math.abs(dh)) r.w + dw else {
            val num = (r.h + dh) * ratio.w
            if (dh > 0) (num + ratio.h - 1) / ratio.h else num / ratio.h
        }
        // Both sides at least MIN_SIDE: the short one decides how narrow it can get.
        val minW = maxOf(MIN_SIDE, (MIN_SIDE * ratio.w + ratio.h - 1) / ratio.h)
        var w = wanted.coerceIn(minOf(minW, maxW), maxW)
        var h = w * ratio.h / ratio.w
        if (h > maxH) { h = maxH; w = h * ratio.w / ratio.h }
        return r.copy(w = w, h = h)
    }

    /** "<time>-<style>-crop-<w>x<h>.png": ImageLab.canvasOf reads its size from the name, as for any drawing. */
    fun fileName(now: Long, styleKey: String?, r: Rect): String = "$now-${styleKey ?: "none"}-crop-${r.w}x${r.h}.png"

    /** A crop's copy, not a drawing sd-cli made: Draw again draws a new 512 picture and keeps it. */
    fun isCrop(name: String): Boolean = "-crop-" in name
}
