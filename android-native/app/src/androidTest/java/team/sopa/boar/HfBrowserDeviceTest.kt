package team.sopa.boar

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import team.sopa.boar.models.HfBrowser

/** The Hugging Face search from the phone (needs the internet): finds GGUF repos, lists their files, makes a catalog entry. */
@RunWith(AndroidJUnit4::class)
class HfBrowserDeviceTest {
    @Test fun searchesListsAndAddsAGgufModel() {
        val repos = HfBrowser.search("qwen2.5 0.5b instruct")
        assertTrue("no results", repos.isNotEmpty())
        val repo = repos.first { r -> HfBrowser.files(r.id).isNotEmpty() }
        val f = HfBrowser.files(repo.id).first()
        assertTrue(f.filename.lowercase().endsWith(".gguf"))
        assertTrue(f.sizeBytes > 0)
        val a = HfBrowser.toAsset(repo.id, f)
        assertTrue(a.id.startsWith("hf-"))
        assertEquals("models/${a.id}.gguf", a.filename)
        assertTrue(a.sourceUrl.startsWith("https://huggingface.co/${repo.id}/resolve/"))
    }
}
