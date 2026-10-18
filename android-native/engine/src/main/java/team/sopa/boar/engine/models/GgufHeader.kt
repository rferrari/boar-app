package team.sopa.boar.engine.models

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * A mixture of experts, from its GGUF header: [arch] is general.architecture, [experts] <arch>.expert_count and [used]
 * <arch>.expert_used_count (null when the file doesn't say).
 */
data class MoeInfo(val arch: String, val experts: Int, val used: Int? = null)

/**
 * Reads a GGUF file's metadata keys (the key/value section right after the header) for what Boar Labs' Big Boar needs:
 * whether the model is a mixture of experts and of which architecture. Only the first [MAX_READ_BYTES] are read, and
 * the reading stops as soon as the architecture and both expert counts are known (they come before the tokenizer's
 * long arrays in llama.cpp's files). Never throws: a dense model, a truncated or foreign file gives null.
 */
object GgufHeader {
    /** The metadata of a big vocabulary (150k tokens, merges) takes a few MB; past this it isn't a file worth reading. */
    const val MAX_READ_BYTES: Long = 64L * 1024 * 1024

    /** "GGUF" read as a little-endian uint32. */
    private const val MAGIC = 0x46554747L
    private const val MAX_KEY_BYTES = 65_536
    private const val MAX_ARCH_BYTES = 256
    private const val MAX_KV = 1_000_000L
    private const val MAX_EXPERTS = 1_000_000L

    private const val T_UINT8 = 0L
    private const val T_INT8 = 1L
    private const val T_UINT16 = 2L
    private const val T_INT16 = 3L
    private const val T_UINT32 = 4L
    private const val T_INT32 = 5L
    private const val T_FLOAT32 = 6L
    private const val T_BOOL = 7L
    private const val T_STRING = 8L
    private const val T_ARRAY = 9L
    private const val T_UINT64 = 10L
    private const val T_INT64 = 11L
    private const val T_FLOAT64 = 12L

    private class Cached(val info: MoeInfo?)

    /** Per file: path, size and modification time, so a replaced file is read again and the catalog never re-reads. */
    private val cache = ConcurrentHashMap<String, Cached>()

    /** [file]'s mixture-of-experts facts, read once per path + size + mtime; null for dense, missing or unreadable. */
    fun moe(file: File): MoeInfo? {
        val (size, mtime) = runCatching { if (file.isFile) file.length() to file.lastModified() else null }.getOrNull() ?: return null
        if (size <= 0) return null
        val key = "${file.path}|$size|$mtime"
        cache[key]?.let { return it.info }
        val info = runCatching { file.inputStream().use { readMoe(it) } }.getOrNull()
        cache[key] = Cached(info)
        return info
    }

    fun readMoe(bytes: ByteArray): MoeInfo? = readMoe(bytes.inputStream())

    /** The facts from a GGUF stream positioned at its first byte; null for dense or garbage. Never throws. */
    fun readMoe(input: InputStream): MoeInfo? = try {
        parse(Reader(BufferedInputStream(input, 1 shl 16), MAX_READ_BYTES))
    } catch (e: Exception) {
        null
    }

    private class Garbage : Exception()

    private fun parse(r: Reader): MoeInfo? {
        if (r.u32() != MAGIC) return null
        val version = r.u32()
        if (version !in 1L..3L) return null
        val v1 = version == 1L
        if (v1) r.u32() else r.u64() // tensor count
        val kvCount = if (v1) r.u32() else r.u64()
        if (kvCount !in 0L..MAX_KV) return null
        var arch: String? = null
        // Expert counts can come before general.architecture: kept by key until the architecture is known.
        val counts = HashMap<String, Long>()
        fun done(): Boolean = arch?.let { a -> "$a.expert_count" in counts && "$a.expert_used_count" in counts } == true
        try {
            var i = 0L
            while (i < kvCount && !done()) {
                val key = String(r.bytes(r.len(v1, MAX_KEY_BYTES.toLong()).toInt()), Charsets.UTF_8)
                val type = r.u32()
                when {
                    key == "general.architecture" && type == T_STRING ->
                        arch = String(r.bytes(r.len(v1, MAX_ARCH_BYTES.toLong()).toInt()), Charsets.UTF_8).trim()
                    (key.endsWith(".expert_count") || key.endsWith(".expert_used_count")) && isInt(type) ->
                        counts[key] = r.int(type)
                    else -> skipValue(r, type, v1, depth = 0)
                }
                i++
            }
        } catch (e: Exception) {
            // Truncated, over the read budget or garbage past this point: what was read before still counts.
        }
        val a = arch?.takeIf { it.isNotEmpty() } ?: return null
        val experts = counts["$a.expert_count"]?.takeIf { it in 1L..MAX_EXPERTS } ?: return null
        val used = counts["$a.expert_used_count"]?.takeIf { it in 1L..experts }?.toInt()
        return MoeInfo(a, experts.toInt(), used)
    }

    private fun isInt(type: Long) = type == T_UINT8 || type == T_INT8 || type == T_UINT16 || type == T_INT16 ||
        type == T_UINT32 || type == T_INT32 || type == T_UINT64 || type == T_INT64

    private fun fixedSize(type: Long): Long = when (type) {
        T_UINT8, T_INT8, T_BOOL -> 1
        T_UINT16, T_INT16 -> 2
        T_UINT32, T_INT32, T_FLOAT32 -> 4
        T_UINT64, T_INT64, T_FLOAT64 -> 8
        else -> -1
    }

    private fun skipValue(r: Reader, type: Long, v1: Boolean, depth: Int) {
        val fixed = fixedSize(type)
        when {
            fixed > 0 -> r.skip(fixed)
            type == T_STRING -> r.skip(r.len(v1, MAX_READ_BYTES))
            type == T_ARRAY -> {
                if (depth > 3) throw Garbage()
                val elem = r.u32()
                val count = r.len(v1, MAX_READ_BYTES)
                val size = fixedSize(elem)
                when {
                    size > 0 -> { if (count > MAX_READ_BYTES / size) throw Garbage(); r.skip(count * size) }
                    // Each element takes at least its length field, so the read budget ends a bogus count.
                    elem == T_STRING || elem == T_ARRAY -> { var k = 0L; while (k < count) { skipValue(r, elem, v1, depth + 1); k++ } }
                    else -> throw Garbage()
                }
            }
            else -> throw Garbage()
        }
    }

    /** Little-endian reads over at most [limit] bytes; past it, or at the end of the stream, an exception. */
    private class Reader(private val input: InputStream, private val limit: Long) {
        private var pos = 0L

        private fun need(n: Long) { if (n < 0 || n > limit - pos) throw Garbage() }

        fun bytes(n: Int): ByteArray {
            need(n.toLong())
            val out = ByteArray(n)
            var off = 0
            while (off < n) {
                val k = input.read(out, off, n - off)
                if (k < 0) throw EOFException()
                off += k
            }
            pos += n
            return out
        }

        private fun le(n: Int): Long {
            val b = bytes(n)
            var v = 0L
            for (i in n - 1 downTo 0) v = (v shl 8) or (b[i].toLong() and 0xff)
            return v
        }

        fun u32(): Long = le(4)
        /** A uint64; one above Long.MAX_VALUE comes back negative and fails every range check. */
        fun u64(): Long = le(8)

        /** A string or array length (uint32 in GGUF v1, uint64 after), refused above [max]. */
        fun len(v1: Boolean, max: Long): Long {
            val n = if (v1) u32() else u64()
            if (n < 0 || n > max) throw Garbage()
            return n
        }

        fun int(type: Long): Long = when (type) {
            T_UINT8 -> le(1)
            T_INT8 -> le(1).toByte().toLong()
            T_UINT16 -> le(2)
            T_INT16 -> le(2).toShort().toLong()
            T_UINT32 -> le(4)
            T_INT32 -> le(4).toInt().toLong()
            else -> le(8)
        }

        fun skip(n: Long) {
            need(n)
            var left = n
            while (left > 0) {
                val s = input.skip(left)
                if (s > 0) left -= s
                else { if (input.read() < 0) throw EOFException(); left-- }
            }
            pos += n
        }
    }
}
