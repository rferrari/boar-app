package team.sopa.boar.engine.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.models.SetupGuide.Page

class SetupGuideTest {
    @Test fun `every page in a Labs build without a PIN, ending on Ready`() =
        assertEquals(listOf(Page.LOOK, Page.FILES, Page.ANSWERS, Page.ASK, Page.FIRST, Page.PIN, Page.PRIVACY, Page.LABS, Page.READY),
            SetupGuide.pages(experimental = true, pinSetBefore = false))

    @Test fun `no Labs page in a normal build`() =
        assertFalse(Page.LABS in SetupGuide.pages(experimental = false, pinSetBefore = false))

    @Test fun `no PIN page when a PIN was set before setup started`() {
        val pages = SetupGuide.pages(experimental = false, pinSetBefore = true)
        assertFalse(Page.PIN in pages)
        assertEquals(Page.READY, pages.last())
    }

    @Test fun `a PIN set on its page keeps the pages when setup resumes in a new process`() {
        // Saved on Privacy, with the PIN page in the list; the PIN is set now. Same pages, same page.
        val (pages, i) = SetupGuide.resume(experimental = true, pinPage = true, pinSetNow = true, page = "PRIVACY")
        assertTrue(Page.PIN in pages)
        assertEquals(Page.PRIVACY, pages[i])
    }

    @Test fun `resuming without a saved page starts on the first one`() {
        val (pages, i) = SetupGuide.resume(experimental = false, pinPage = null, pinSetNow = true, page = null)
        assertFalse(Page.PIN in pages)
        assertEquals(0, i)
    }

    @Test fun `files wait for the search model`() =
        assertFalse(SetupGuide.canImport(searchModelPresent = false, indexBuilding = false, importing = false))

    @Test fun `files wait while the index builds`() =
        assertFalse(SetupGuide.canImport(searchModelPresent = true, indexBuilding = true, importing = false))

    @Test fun `one import at a time`() =
        assertFalse(SetupGuide.canImport(searchModelPresent = true, indexBuilding = false, importing = true))

    @Test fun `files go in once the search model is there and the index isn't building`() =
        assertTrue(SetupGuide.canImport(searchModelPresent = true, indexBuilding = false, importing = false))

    @Test fun `pending is what the last finished import didn't include`() {
        assertEquals(listOf("b.pdf"), SetupGuide.pending(listOf("a.txt", "b.pdf"), listOf("a.txt")))
        assertEquals(emptyList<String>(), SetupGuide.pending(listOf("a.txt"), listOf("a.txt")))
        // An import cut short (process killed) recorded nothing: everything is pending again.
        assertEquals(listOf("a.txt", "b.pdf"), SetupGuide.pending(listOf("a.txt", "b.pdf"), emptyList()))
    }

    @Test fun `a second file with the same name gets a number`() {
        assertEquals("notes.txt", SetupGuide.freeName("notes.txt", setOf("a.md")))
        assertEquals("notes (2).txt", SetupGuide.freeName("notes.txt", setOf("notes.txt")))
        assertEquals("notes (3).txt", SetupGuide.freeName("notes.txt", setOf("notes.txt", "notes (2).txt")))
        assertEquals("README (2)", SetupGuide.freeName("README", setOf("README")))
    }
}
