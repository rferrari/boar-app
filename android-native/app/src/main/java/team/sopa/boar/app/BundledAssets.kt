package team.sopa.boar.app

import android.content.Context
import android.os.StatFs
import java.io.File
import java.security.MessageDigest
import team.sopa.boar.engine.models.CatalogAsset

/**
 * The offline APK (scripts/build-offline-apk.sh): models and packs that ship inside the APK under assets/bundled/, at
 * their catalog paths (bundled/models/…gguf, bundled/corpus/…sqlite), stored uncompressed. On first launch they're
 * copied to the files directory and checked against the catalog's SHA-256, so the app needs no network from its first
 * second. The engine needs a real file, so the copy can't be avoided; the normal APK has no bundled/ folder and this
 * finds nothing to do.
 */
class BundledAssets(private val services: BoarServices, private val context: Context) {
    class NotEnoughSpace(val neededBytes: Long) : Exception("not enough space: $neededBytes bytes needed")
    class Corrupt(val asset: CatalogAsset) : Exception("${asset.filename} doesn't match the catalog")

    /** Shipped in this APK, and not yet (or not fully) on disk. */
    fun pending(): List<CatalogAsset> = (services.catalog.models + team.sopa.boar.labs.LabHooks.bundledAssets()).filter { a ->
        inApk(a) && services.fileOf(a).let { !it.isFile || it.length() != a.sizeBytes }
    }

    // openFd only opens an uncompressed asset, the way the offline APK stores them; the size must match the catalog.
    private fun inApk(a: CatalogAsset): Boolean =
        runCatching { pieces(a).sumOf { name -> context.assets.openFd(name).use { it.length } } == a.sizeBytes }.getOrDefault(false)

    /**
     * The asset names holding [a]: the file itself, or its pieces (`<file>.00.piece`, `.01.piece`…). The build splits files
     * over 1 GiB, because reading one asset past 2 GiB failed on the phone (Qwen3 4B, 2.5 GB, Lab device 8 GB, Oct 22).
     */
    private fun pieces(a: CatalogAsset): List<String> {
        val whole = "$DIR/${a.filename}"
        if (runCatching { context.assets.openFd(whole).close() }.isSuccess) return listOf(whole)
        return generateSequence(0) { it + 1 }.map { "$whole.%02d.piece".format(it) }
            .takeWhile { name -> runCatching { context.assets.openFd(name).close() }.isSuccess }.toList()
    }

    /**
     * Copies every pending asset, verifying each one; [onProgress] gets bytes done and total. One run at a time in the
     * process: a rotation rebuilds the screen and starts another while the first copy is still writing, and both would
     * write the same .part file. The second waits, then finds nothing left to do.
     */
    fun unpack(onProgress: (Long, Long) -> Unit) = synchronized(LOCK) { unpackLocked(onProgress) }

    private fun unpackLocked(onProgress: (Long, Long) -> Unit) {
        val todo = pending()
        val total = todo.sumOf { it.sizeBytes }
        val free = StatFs(context.filesDir.path).availableBytes
        if (free < total + SPACE_MARGIN_BYTES) throw NotEnoughSpace(total + SPACE_MARGIN_BYTES)
        var done = 0L
        val buf = ByteArray(1 shl 20)
        for (a in todo) {
            val dest = services.fileOf(a)
            dest.parentFile?.mkdirs()
            val part = File(dest.path + ".part")
            val sha = MessageDigest.getInstance("SHA-256")
            try {
                part.outputStream().use { out ->
                    var lastReport = 0L
                    for (name in pieces(a)) context.assets.openFd(name).use { fd ->
                        fd.createInputStream().use { input ->
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                sha.update(buf, 0, n)
                                done += n
                                if (done - lastReport >= REPORT_EVERY_BYTES) { onProgress(done, total); lastReport = done }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                part.delete()
                throw e
            }
            val hex = sha.digest().joinToString("") { "%02x".format(it) }
            if (!hex.equals(a.sha256, ignoreCase = true)) { part.delete(); throw Corrupt(a) }
            if (!part.renameTo(dest)) { part.delete(); throw java.io.IOException("can't move ${a.filename} in place") }
        }
        onProgress(total, total)
        services.reloadPacks()
    }

    companion object {
        const val DIR = "bundled"
        private val LOCK = Any()
        /** Room left after unpacking, for chats, the index and Android itself. */
        const val SPACE_MARGIN_BYTES = 500L * 1024 * 1024
        private const val REPORT_EVERY_BYTES = 16L * 1024 * 1024
    }
}
