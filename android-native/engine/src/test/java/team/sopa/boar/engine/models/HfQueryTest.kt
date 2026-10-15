package team.sopa.boar.engine.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HfQueryTest {
    @Test fun `search asks for text-generation GGUF repos in the bucket, with their GGUF facts`() {
        val url = HfQuery.searchUrl(" qwen 3 ", HfQuery.Size.B1_3, HfQuery.Sort.TRENDING)
        assertTrue(url.startsWith("https://huggingface.co/api/models?search=qwen+3&filter=gguf&pipeline_tag=text-generation"))
        assertTrue("num_parameters=min:1B,max:3B" in url)
        assertTrue("sort=trendingScore" in url && "expand[]=gguf" in url)
    }

    @Test fun `browsing without a query has no search term, and Any stops at 20B`() {
        val url = HfQuery.searchUrl("", HfQuery.Size.ANY, HfQuery.Sort.DOWNLOADS)
        assertFalse("search=" in url)
        assertTrue("num_parameters=min:0,max:20B" in url)
    }

    @Test fun `the next page comes from the Link header and stays on the listing`() {
        val next = "https://huggingface.co/api/models?filter=gguf&cursor=abc"
        assertEquals(next, HfQuery.nextUrl("<$next>; rel=\"next\""))
        assertNull(HfQuery.nextUrl("<https://evil.example/api/models?x>; rel=\"next\""))
        assertNull(HfQuery.nextUrl(null))
    }

    @Test fun `keeps chat architectures, drops drafts, embedders and diffusion`() {
        assertTrue(HfQuery.chatArchitecture("qwen3"))
        assertTrue(HfQuery.chatArchitecture("lfm2"))
        for (a in listOf("dflash", "bert", "llada", "clip", null)) assertFalse("$a", HfQuery.chatArchitecture(a))
    }

    @Test fun `only whole GGUF files under 12 GB`() {
        assertTrue(HfQuery.addableFile("Qwen3-4B-Q4_K_M.gguf", 2_500_000_000))
        assertFalse(HfQuery.addableFile("Big-Q4_K_M-00001-of-00003.gguf", 5_000_000_000))
        assertFalse(HfQuery.addableFile("mmproj-model-f16.gguf", 800_000_000))
        assertFalse(HfQuery.addableFile("Huge-Q8_0.gguf", 13L * 1024 * 1024 * 1024))
        assertFalse(HfQuery.addableFile("README.md", 10))
    }

    @Test fun `suggests Q4_K_M and prints parameter counts`() {
        assertEquals("m-Q4_K_M.gguf", HfQuery.recommended(listOf("m-Q8_0.gguf", "m-Q4_K_M.gguf")))
        assertEquals("2.7B", HfQuery.formatParams(2_697_198_592))
        assertEquals("4B", HfQuery.formatParams(4_000_000_000))
        assertEquals("500M", HfQuery.formatParams(500_000_000))
    }

    @Test
    fun uncensoredModelsAreRecognized() {
        assertTrue(HfQuery.uncensored("Orenguteng/Llama-3-8B-Lexi-Uncensored-GGUF"))
        assertTrue(HfQuery.uncensored("mlabonne/gemma-3-4b-it-abliterated-GGUF"))
        assertTrue(HfQuery.uncensored("someone/plain-name-GGUF", listOf("text-generation", "not-for-all-audiences")))
        assertTrue(HfQuery.uncensored("someone/model-GGUF", listOf("nsfw")))
        assertTrue(HfQuery.uncensored("someone/Adult-Chat-7B-GGUF"))
        assertFalse(HfQuery.uncensored("Qwen/Qwen3-4B-Instruct-2507-GGUF", listOf("text-generation", "gguf")))
        // Whole words only: "therapy" isn't "erp", "superpower" isn't "erp" either.
        assertFalse(HfQuery.uncensored("someone/therapy-superpower-GGUF"))
    }
}
