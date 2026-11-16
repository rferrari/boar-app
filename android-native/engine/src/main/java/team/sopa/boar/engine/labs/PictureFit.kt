package team.sopa.boar.engine.labs

/** Press's pictures keep their shape (adam, Nov 11): the longer side fills [box], the shorter one follows. */
object PictureFit {
    fun fit(width: Int, height: Int, box: Float): Pair<Float, Float> {
        if (width <= 0 || height <= 0) return box to box
        return if (width >= height) box to box * height / width else box * width / height to box
    }

    /**
     * The paper's pictures (Nov 13): as wide as the column, in their own shape, no taller than [maxHeight] (a tall one is
     * narrower then). A broken size gets a square.
     */
    fun within(width: Int, height: Int, maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        if (width <= 0 || height <= 0) return minOf(maxWidth, maxHeight).let { it to it }
        val scale = minOf(maxWidth / width, maxHeight / height)
        return width * scale to height * scale
    }
}
