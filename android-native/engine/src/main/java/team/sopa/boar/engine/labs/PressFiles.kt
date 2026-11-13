package team.sopa.boar.engine.labs

import java.io.File
import java.io.OutputStream

/**
 * Press's files on the phone (phase 2): files/labs/press/ holds projects.json and one folder per project, where the
 * photos picked from the phone are COPIED (adam's open question; t9's default, Nov 13): the project keeps working if the
 * photo leaves the gallery, and the copy goes with its project, with Clear all and with Forgot PIN.
 */
object PressFiles {
    /** The long side of a copied photo: sharp in an A4 PDF, small on the phone. */
    const val PHOTO_MAX = 1600

    /**
     * A project's folder, always one level under the Press folder: a character that isn't a letter, digit, - or _ becomes
     * _, so "." and ".." can't name a parent, and an empty id is the folder "_", never the Press folder itself (validator
     * G-9: "delete project" on it would have wiped every project).
     */
    fun projectDir(pressDir: File, projectId: String): File =
        File(pressDir, projectId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "_" })

    /** A photo block's file: a plain name in its project's folder; "", "." and ".." become "_", never a folder (G-9). */
    fun photoFile(pressDir: File, projectId: String, name: String): File {
        val plain = File(name).name.takeUnless { it.isBlank() || it == "." || it == ".." } ?: "_"
        return File(projectDir(pressDir, projectId), plain)
    }

    /** True when no project uses the photo [name] of [projectId] any more (its block removed past the Undo, G-1). */
    fun unused(lib: PressDoc.Library, projectId: String, name: String): Boolean =
        lib.projects.none { p -> p.id == projectId && p.blocks.any { it is PressDoc.Block.Picture && it.kind == PressDoc.Kind.PHOTO && File(it.file).name == name } }

    /**
     * How to stand a photo upright from its EXIF orientation (1–8): the degrees to rotate, then whether to mirror it
     * left-right (validator G-6: the mirrored ones, 2, 4, 5 and 7, were left as taken).
     */
    fun upright(orientation: Int): Pair<Int, Boolean> = when (orientation) {
        2 -> 0 to true
        3 -> 180 to false
        4 -> 180 to true
        5 -> 90 to true
        6 -> 90 to false
        7 -> 270 to true
        8 -> 270 to false
        else -> 0 to false
    }

    /**
     * Writes a photo into its project's folder as [name]: to a temporary file first, renamed when complete, so a half copy
     * never shows. Returns the file.
     */
    fun copyInto(pressDir: File, projectId: String, name: String, write: (OutputStream) -> Unit): File {
        val dir = projectDir(pressDir, projectId).apply { mkdirs() }
        val out = File(dir, File(name).name)
        val tmp = File(dir, out.name + ".tmp")
        try {
            tmp.outputStream().use(write)
            if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
        return out
    }

    /** A project deleted: its folder, photos and all. */
    fun deleteProject(pressDir: File, projectId: String) = projectDir(pressDir, projectId).deleteRecursively()

    /** Clear all, Forgot PIN: every project's folder and the projects file. */
    fun eraseAll(pressDir: File) = pressDir.deleteRecursively()

    /**
     * What the folders hold that no project uses any more (a photo removed past its Undo, a project gone, a copy cut short):
     * files and folders to delete. Only ever run on projects read from their file.
     */
    fun orphans(pressDir: File, lib: PressDoc.Library): List<File> {
        val dirs = lib.projects.associateBy { projectDir(pressDir, it.id).name }
        return pressDir.listFiles().orEmpty().filter { it.isDirectory }.flatMap { d ->
            val p = dirs[d.name] ?: return@flatMap listOf(d)
            val used = p.blocks.filterIsInstance<PressDoc.Block.Picture>().filter { it.kind == PressDoc.Kind.PHOTO }.map { File(it.file).name }.toSet()
            d.listFiles().orEmpty().filter { it.name !in used }
        }
    }

    /** A photo's size once copied: its long side at most [max], its shape kept. */
    fun scaled(width: Int, height: Int, max: Int = PHOTO_MAX): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val long = maxOf(width, height)
        if (long <= max) return width to height
        return maxOf(1, Math.round(width.toDouble() * max / long).toInt()) to maxOf(1, Math.round(height.toDouble() * max / long).toInt())
    }

    /** The power of two to decode with: the largest that still leaves the long side at least [max]. */
    fun sampleSize(width: Int, height: Int, max: Int = PHOTO_MAX): Int {
        var s = 1
        val long = maxOf(width, height)
        while (long / (s * 2) >= max) s *= 2
        return s
    }
}
