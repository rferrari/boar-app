package team.sopa.boar.engine.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.settings.FreeMemory.Decision
import java.nio.file.Files

class FreeMemoryTest {
    @Test fun `Auto waits 5 minutes, a pick waits its own, Immediately none`() {
        assertEquals(5 * 60_000L, FreeMemory.delayMs(null))
        assertEquals(0L, FreeMemory.delayMs(0))
        assertEquals(5 * 60_000L, FreeMemory.delayMs(5))
        assertEquals(30 * 60_000L, FreeMemory.delayMs(30))
        assertEquals(0L, FreeMemory.delayMs(-3))
    }

    @Test fun `unloads only when on, away, loaded and idle`() {
        assertEquals(Decision.UNLOAD, FreeMemory.decide(enabled = true, inForeground = false, loaded = true, busy = false))
        assertEquals(Decision.WAIT, FreeMemory.decide(enabled = true, inForeground = false, loaded = true, busy = true))
        assertEquals(Decision.SKIP, FreeMemory.decide(enabled = false, inForeground = false, loaded = true, busy = false))
        assertEquals(Decision.SKIP, FreeMemory.decide(enabled = true, inForeground = true, loaded = true, busy = false))
        // A model still loading counts as busy: wait for it rather than leave it in memory once loaded.
        assertEquals(Decision.WAIT, FreeMemory.decide(enabled = true, inForeground = false, loaded = false, busy = true))
        assertEquals(Decision.SKIP, FreeMemory.decide(enabled = true, inForeground = false, loaded = false, busy = false))
    }

    @Test fun `on and Auto by default, and Reset settings restores both`() {
        val f = Files.createTempDirectory("boar-settings").toFile().resolve("settings.json")
        val s = AppSettings(f)
        assertTrue(s.freeMemoryInBackground)
        assertNull(s.freeMemoryAfterMinutes)
        s.freeMemoryInBackground = false
        s.freeMemoryAfterMinutes = 30
        assertFalse(AppSettings(f).freeMemoryInBackground)
        assertEquals(30, AppSettings(f).freeMemoryAfterMinutes)
        s.freeMemoryAfterMinutes = 0
        assertEquals(0, AppSettings(f).freeMemoryAfterMinutes)
        s.resetPreferences()
        val again = AppSettings(f)
        assertTrue(again.freeMemoryInBackground)
        assertNull(again.freeMemoryAfterMinutes)
    }
}
