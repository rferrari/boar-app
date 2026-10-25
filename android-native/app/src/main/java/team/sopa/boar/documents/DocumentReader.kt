package team.sopa.boar.documents

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import team.sopa.boar.engine.rag.DocumentImport

/**
 * Reads a picked file as text for [DocumentImport]: Knowledge → Personal Files and the setup's files page share it, so
 * both refuse the same files the same way. Failures come out as [Failed], already in the user's language.
 */
class DocumentReader(private val app: Application) {
    /** A failure the user can act on, already in their language. */
    class Failed(message: String) : Exception(message)

    /** The file's name and size as the picker reports them; for a file:// uri, the name on disk. */
    fun nameAndSize(uri: Uri): Pair<String, Long> {
        val resolver = app.contentResolver
        var fileName = uri.lastPathSegment ?: "document"
        var size = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) { fileName = c.getString(0) ?: fileName; size = c.getLong(1) }
        }
        if (size <= 0) size = runCatching { resolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: 0L
        return fileName to size
    }

    /** The file's text, or [Failed]: not a readable kind, too large, unreadable, password-protected, no text. */
    fun read(uri: Uri, name: String? = null): DocumentImport.File {
        val (picked, size) = nameAndSize(uri)
        val fileName = name ?: picked
        // Only what BOAR can read: anything else (an .html page, a .docx) would go in as raw code, found by nothing.
        if (!readable(fileName)) throw Failed(text("native.documents.unsupported", fileName))
        if (DocumentImport.tooLargeToRead(size)) throw Failed(tooLarge(fileName))
        val text = try {
            if (fileName.lowercase().endsWith(".pdf")) pdfText(uri, fileName)
            else app.contentResolver.openInputStream(uri)?.use { readCapped(it) ?: throw Failed(tooLarge(fileName)) }?.toString(Charsets.UTF_8)
                ?: throw Failed(text("native.documents.unreadable", fileName))
        } catch (e: OutOfMemoryError) {
            // Not an Exception: uncaught, it closes the app. The file's text is dropped with the frame.
            throw Failed(tooLarge(fileName))
        }
        return DocumentImport.File(fileName, if (size > 0) size else text.toByteArray().size.toLong(), text)
    }

    /** The line for a failed import: the reader's own message, the importer's too-large, else a plain line. */
    fun messageFor(e: Exception): String = when (e) {
        is Failed -> e.message.orEmpty()
        is DocumentImport.TooLarge -> tooLarge(e.fileName)
        // Storage or embedding failures: a plain line, not the exception text.
        else -> text("native.documents.failed")
    }

    private fun text(key: String, name: String? = null) = team.sopa.boar.ui.I18n.t(app, key, "name" to name)

    private fun tooLarge(fileName: String) =
        team.sopa.boar.ui.I18n.t(app, "native.documents.tooLarge", "name" to fileName, "mb" to DocumentImport.READ_LIMIT / 1_000_000)

    /** An IOException, so PDDocument.load closes and deletes its scratch file before it reaches [pdfText]. */
    private class InputTooLarge : java.io.IOException()

    /**
     * The PDF's selectable text (no OCR); password-protected PDFs aren't supported, as in the Expo app. PDFBox keeps the
     * file in a temp file in the cache, not on the heap, and writes the text in one pass, so a large PDF doesn't fill
     * the memory a loaded model needs. Both the file and its text stop at [DocumentImport.READ_LIMIT], also when the
     * picker didn't say how big the file is.
     */
    private fun pdfText(uri: Uri, fileName: String): String {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(app)
        val input = app.contentResolver.openInputStream(uri) ?: throw Failed(text("native.documents.unreadable", fileName))
        val memory = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly().setTempDir(app.cacheDir)
        val limit = DocumentImport.READ_LIMIT
        return try {
            input.use { raw ->
                val bounded = object : java.io.FilterInputStream(raw) {
                    private var read = 0L
                    private fun count(n: Int): Int { if (n > 0) { read += n; if (read > limit) throw InputTooLarge() }; return n }
                    override fun read(): Int = super.read().also { if (it >= 0) count(1) }
                    override fun read(b: ByteArray, off: Int, len: Int): Int = count(super.read(b, off, len))
                }
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(bounded, memory).use { doc ->
                    val out = StringBuilder()
                    val writer = object : java.io.Writer() {
                        override fun write(cbuf: CharArray, off: Int, len: Int) {
                            out.append(cbuf, off, len)
                            if (out.length > limit) throw Failed(tooLarge(fileName))
                        }
                        override fun flush() {}
                        override fun close() {}
                    }
                    com.tom_roush.pdfbox.text.PDFTextStripper().writeText(doc, writer)
                    out.toString()
                }
            }
        } catch (e: InputTooLarge) {
            throw Failed(tooLarge(fileName))
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw Failed(text("native.documents.passwordProtected", fileName))
        } catch (e: java.io.IOException) {
            throw Failed(text("native.documents.unreadable", fileName))
        }.also { if (it.isBlank()) throw Failed(text("native.documents.noText", fileName)) }
    }

    /** Reads at most [DocumentImport.READ_LIMIT] bytes; null as soon as the file is longer (its size wasn't known up front). */
    private fun readCapped(input: java.io.InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > DocumentImport.READ_LIMIT) return null
            out.write(buf, 0, n)
        }
    }

    companion object {
        /** The extensions the importer reads (the screen's note lists them). */
        val READABLE = setOf("txt", "md", "markdown", "csv", "json", "pdf")

        fun readable(fileName: String) = fileName.substringAfterLast('.', "").lowercase() in READABLE
    }
}
