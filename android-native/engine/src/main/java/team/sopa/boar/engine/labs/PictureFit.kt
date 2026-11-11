package team.sopa.boar.engine.labs

/** Press's pictures keep their shape (adam, Nov 11): the longer side fills [box], the shorter one follows. */
object PictureFit {
    fun fit(width: Int, height: Int, box: Float): Pair<Float, Float> {
        if (width <= 0 || height <= 0) return box to box
        return if (width >= height) box to box * height / width else box * width / height to box
    }
}
