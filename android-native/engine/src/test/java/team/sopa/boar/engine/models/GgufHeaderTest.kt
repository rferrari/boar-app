package team.sopa.boar.engine.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GgufHeaderTest {
    /** A synthetic GGUF v3 file: the header, then [kvs] written by their own writers (no tensors). */
    private class Gguf(val version: Int = 3) {
        private val kvs = mutableListOf<(ByteArrayOutputStream) -> Unit>()

        fun string(key: String, value: String) = apply { kvs.add { o -> wstr(o, key); w32(o, 8); wstr(o, value) } }
        fun u32(key: String, value: Long) = apply { kvs.add { o -> wstr(o, key); w32(o, 4); w32(o, value) } }
        fun i32(key: String, value: Int) = apply { kvs.add { o -> wstr(o, key); w32(o, 5); w32(o, value.toLong()) } }
        fun f32(key: String, value: Float) = apply { kvs.add { o -> wstr(o, key); w32(o, 6); w32(o, java.lang.Float.floatToIntBits(value).toLong()) } }
        fun bool(key: String, value: Boolean) = apply { kvs.add { o -> wstr(o, key); w32(o, 7); o.write(if (value) 1 else 0) } }
        fun strings(key: String, values: List<String>) = apply {
            kvs.add { o -> wstr(o, key); w32(o, 9); w32(o, 8); w64(o, values.size.toLong()); values.forEach { wstr(o, it) } }
        }
        fun ints(key: String, values: List<Int>) = apply {
            kvs.add { o -> wstr(o, key); w32(o, 9); w32(o, 5); w64(o, values.size.toLong()); values.forEach { w32(o, it.toLong()) } }
        }

        fun bytes(): ByteArray {
            val o = ByteArrayOutputStream()
            o.write("GGUF".toByteArray())
            w32(o, version.toLong())
            w64(o, 0) // tensors
            w64(o, kvs.size.toLong())
            kvs.forEach { it(o) }
            return o.toByteArray()
        }

        companion object {
            /** The low 32 bits, little-endian. */
            fun w32(o: ByteArrayOutputStream, v: Long) = o.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array())
            fun w64(o: ByteArrayOutputStream, v: Long) = o.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array())
            fun wstr(o: ByteArrayOutputStream, s: String) { val b = s.toByteArray(); w64(o, b.size.toLong()); o.write(b) }
        }
    }

    private val tokens = (0 until 500).map { "tok$it" }

    private fun dense() = Gguf().string("general.architecture", "llama").string("general.name", "Llama 3.2 1B")
        .u32("llama.block_count", 16).u32("llama.context_length", 131072).f32("llama.rope.freq_base", 500000f)
        .strings("tokenizer.ggml.tokens", tokens).ints("tokenizer.ggml.token_type", tokens.map { 1 }).bytes()

    private fun lfm2moe() = Gguf().string("general.architecture", "lfm2moe").string("general.name", "LFM2 8B A1B")
        .u32("lfm2moe.block_count", 24).u32("lfm2moe.expert_count", 32).u32("lfm2moe.expert_used_count", 4)
        .bool("lfm2moe.expert_weights_norm", true).strings("tokenizer.ggml.tokens", tokens).bytes()

    private fun qwen3moe() = Gguf().string("general.architecture", "qwen3moe").strings("general.tags", listOf("moe", "text-generation"))
        .u32("qwen3moe.block_count", 48).i32("qwen3moe.expert_count", 128).u32("qwen3moe.expert_used_count", 8)
        .strings("tokenizer.ggml.tokens", tokens).bytes()

    @Test fun `a dense model is not a mixture of experts`() {
        assertNull(GgufHeader.readMoe(dense()))
    }

    @Test fun `lfm2moe reads its expert counts`() {
        assertEquals(MoeInfo("lfm2moe", 32, 4), GgufHeader.readMoe(lfm2moe()))
    }

    @Test fun `qwen3moe reads its expert counts past an array and from an int32`() {
        assertEquals(MoeInfo("qwen3moe", 128, 8), GgufHeader.readMoe(qwen3moe()))
    }

    @Test fun `counts written before the architecture still count`() {
        val b = Gguf().u32("qwen35moe.expert_count", 256).u32("qwen35moe.expert_used_count", 8).string("general.architecture", "qwen35moe").bytes()
        assertEquals(MoeInfo("qwen35moe", 256, 8), GgufHeader.readMoe(b))
    }

    @Test fun `another architecture's counts don't make a model a mixture of experts`() {
        val b = Gguf().string("general.architecture", "llama").u32("qwen3moe.expert_count", 128).bytes()
        assertNull(GgufHeader.readMoe(b))
    }

    @Test fun `without expert_used_count the count is null, and zero experts is dense`() {
        assertEquals(MoeInfo("lfm2moe", 32, null), GgufHeader.readMoe(Gguf().string("general.architecture", "lfm2moe").u32("lfm2moe.expert_count", 32).bytes()))
        assertNull(GgufHeader.readMoe(Gguf().string("general.architecture", "llama").u32("llama.expert_count", 0).bytes()))
    }

    @Test fun `a file cut after the expert count keeps what was read`() {
        val full = Gguf().string("general.architecture", "lfm2moe").u32("lfm2moe.expert_count", 32).strings("tokenizer.ggml.tokens", tokens).u32("lfm2moe.expert_used_count", 4).bytes()
        assertEquals(MoeInfo("lfm2moe", 32, null), GgufHeader.readMoe(full.copyOf(full.size - 600)))
    }

    @Test fun `garbage, foreign and truncated input give null without throwing`() {
        assertNull(GgufHeader.readMoe(ByteArray(0)))
        assertNull(GgufHeader.readMoe("GGU".toByteArray()))
        assertNull(GgufHeader.readMoe("PK\u0003\u0004 not a model at all".toByteArray()))
        val rnd = java.util.Random(7)
        repeat(200) { assertNull(GgufHeader.readMoe(ByteArray(rnd.nextInt(4096)).also(rnd::nextBytes))) }
        // A real header with random bytes after it, and every prefix of a real file.
        val moe = lfm2moe()
        repeat(50) { val b = moe.copyOf(); for (i in 24 until b.size) if (rnd.nextInt(8) == 0) b[i] = rnd.nextInt(256).toByte(); GgufHeader.readMoe(b) }
        for (n in 0 until 120) GgufHeader.readMoe(moe.copyOf(n))
        // Absurd lengths and counts: refused, not allocated.
        val huge = ByteArrayOutputStream().apply {
            write("GGUF".toByteArray()); Gguf.w32(this, 3); Gguf.w64(this, 0); Gguf.w64(this, 1); Gguf.w64(this, Long.MAX_VALUE)
        }.toByteArray()
        assertNull(GgufHeader.readMoe(huge))
        val manyKvs = ByteArrayOutputStream().apply { write("GGUF".toByteArray()); Gguf.w32(this, 3); Gguf.w64(this, 0); Gguf.w64(this, -1) }.toByteArray()
        assertNull(GgufHeader.readMoe(manyKvs))
        assertNull(GgufHeader.readMoe(Gguf(version = 9).string("general.architecture", "lfm2moe").u32("lfm2moe.expert_count", 32).bytes()))
    }

    @Test fun `a file is read once per path, size and mtime`() {
        val f = File.createTempFile("gguf", ".gguf")
        try {
            f.writeBytes(lfm2moe())
            assertEquals(MoeInfo("lfm2moe", 32, 4), GgufHeader.moe(f))
            assertEquals(MoeInfo("lfm2moe", 32, 4), GgufHeader.moe(f))
            // Replaced by a dense file of another size: read again.
            f.writeBytes(dense())
            f.setLastModified(f.lastModified() + 5_000)
            assertNull(GgufHeader.moe(f))
            assertNull(GgufHeader.moe(File(f.path + ".missing")))
        } finally {
            f.delete()
        }
    }
}
