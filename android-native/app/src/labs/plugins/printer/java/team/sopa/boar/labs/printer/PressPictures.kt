package team.sopa.boar.labs.printer

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONObject
import team.sopa.boar.engine.labs.PressDoc
import team.sopa.boar.engine.labs.PressFiles
import java.io.File

/**
 * Pictures on the paper. Designer's files are read, never changed, and read defensively (Designer is being rebuilt; any
 * field may be missing): files/labs/illustrations.json (answer id to its drawing, with style, size and seed) and each
 * drawing's <name>.png.json recipe (subject, style, size, seed).
 */
object PressPictures {
    /** Each answer's own illustration that is still on the phone: answer id to file path. */
    fun illustrations(ctx: Context): Map<String, String> = entries(ctx).mapValues { it.value.first }

    private fun entries(ctx: Context): Map<String, Pair<String, PressDoc.Recipe?>> = runCatching {
        val o = JSONObject(File(ctx.filesDir, "labs/illustrations.json").readText())
        o.keys().asSequence().mapNotNull { id ->
            val e = o.optJSONObject(id) ?: return@mapNotNull null
            val f = e.optString("file").takeIf { it.isNotEmpty() && File(it).isFile } ?: return@mapNotNull null
            id to (f to recipe(e))
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun recipe(o: JSONObject): PressDoc.Recipe? {
        fun s(k: String) = o.optString(k).takeIf { it.isNotEmpty() && it != "null" }
        val seed = o.opt("seed").let { (it as? Number)?.toLong() ?: (it as? String)?.toLongOrNull() }?.takeIf { it != 0L }
        val r = PressDoc.Recipe(s("subject"), s("style"), s("size"), seed)
        return r.takeIf { it != PressDoc.Recipe() }
    }

    /** A drawing's recipe: its .png.json beside it, else what illustrations.json says for [answerId]. */
    fun recipeOf(ctx: Context, file: String, answerId: String?): PressDoc.Recipe? =
        runCatching { recipe(JSONObject(File("$file.json").readText())) }.getOrNull()
            ?: answerId?.let { entries(ctx)[it]?.second }

    fun sizeOf(file: File): Pair<Int, Int>? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        (o.outWidth to o.outHeight).takeIf { it.first > 0 && it.second > 0 }
    }.getOrNull()

    /** A picture block for a Designer drawing, with its recipe and size as they are now. */
    fun drawingBlock(ctx: Context, file: File, answerId: String? = null): PressDoc.Block.Picture {
        val (w, h) = sizeOf(file) ?: (0 to 0)
        return PressDoc.Block.Picture(if (answerId != null) "pic-$answerId" else PressStore.newId("pic"), PressDoc.Kind.DRAWING, file.path,
            answerId = answerId, recipe = recipeOf(ctx, file.path, answerId), width = w, height = h)
    }

    /** The drawing an answer already has (🎨 under it in the chat), as a block to follow it; null if it has none. */
    fun ofAnswer(ctx: Context, answerId: String): PressDoc.Block.Picture? = entries(ctx)[answerId]?.let { drawingBlock(ctx, File(it.first), answerId) }

    /** Designer's drawings on this phone, newest first. */
    fun gallery(ctx: Context): List<File> =
        File(ctx.filesDir, "labs/images").listFiles { f -> f.extension.equals("png", true) }.orEmpty().sortedByDescending { it.lastModified() }

    /**
     * A photo picked from the phone (Android's photo picker, no permission), COPIED into the project's folder: turned
     * upright, its long side at most PressFiles.PHOTO_MAX, as a JPEG. The original stays in the gallery; the copy goes
     * with its project. Off the main thread.
     */
    fun copyPhoto(ctx: Context, projectId: String, uri: android.net.Uri): PressDoc.Block.Picture {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: error("can't open the photo")
        val sample = PressFiles.sampleSize(bounds.outWidth, bounds.outHeight)
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("not a picture")
        val (w, h) = PressFiles.scaled(bmp.width, bmp.height)
        if (w != bmp.width || h != bmp.height) bmp = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
        val degrees = runCatching {
            cr.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        if (degrees != 0) bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }, true)
        val f = PressFiles.copyInto(PressStore.dir(ctx), projectId, PressStore.newId("photo") + ".jpg") { out ->
            if (!bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)) error("not saved")
        }
        return PressDoc.Block.Picture(PressStore.newId("pic"), PressDoc.Kind.PHOTO, f.name, width = bmp.width, height = bmp.height).also { bmp.recycle() }
    }

    /** The file behind a picture: a drawing's own path; a photo (phase 2) lives in its project's folder. */
    fun fileOf(ctx: Context, projectId: String, p: PressDoc.Block.Picture): File? = when (p.kind) {
        PressDoc.Kind.DRAWING -> File(p.file)
        PressDoc.Kind.PHOTO -> PressFiles.photoFile(PressStore.dir(ctx), projectId, p.file)
    }.takeIf { it.isFile }
}
