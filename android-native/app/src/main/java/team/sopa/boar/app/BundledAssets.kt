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
        runCatching { context.assets.openFd("$DIR/${a.filename}").use { it.length == a.sizeBytes } }.getOrDefault(false)

    /** Copies every pending asset, verifying each one; [onProgress] gets bytes done and total. */
    fun unpack(onProgress: (Long, Long) -> Unit) {
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
            context.assets.openFd("$DIR/${a.filename}").use { fd ->
                fd.createInputStream().use { input ->
                    part.outputStream().use { out ->
                        var lastReport = 0L
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
            val hex = sha.digest().joinToString("") { "%02x".format(it) }
            if (!hex.equals(a.sha256, ignoreCase = true)) { part.delete(); throw Corrupt(a) }
            if (!part.renameTo(dest)) { part.delete(); throw java.io.IOException("can't move ${a.filename} in place") }
        }
        onProgress(total, total)
        services.reloadPacks()
    }

    companion object {
        const val DIR = "bundled"
        /** Room left after unpacking, for chats, the index and Android itself. */
        const val SPACE_MARGIN_BYTES = 500L * 1024 * 1024
        private const val REPORT_EVERY_BYTES = 16L * 1024 * 1024
    }
}
