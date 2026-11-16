package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validator Q-B1 (t9 0115): an off plugin stays one tap away, and Cohazard can't be lost with one tap below Pro. */
class PluginReachTest {
    @Test fun `below Pro, Cohazard's page has no off switch, but an off Cohazard can come back on`() {
        assertFalse(PluginReach.pageSwitchShown("cohazard", on = true, pro = false))
        assertTrue(PluginReach.pageSwitchShown("cohazard", on = false, pro = false))
        assertTrue(PluginReach.pageSwitchShown("cohazard", on = true, pro = true))
        // Every other plugin keeps its page switch in every profile (A-5).
        for (pro in listOf(true, false)) for (on in listOf(true, false)) assertTrue(PluginReach.pageSwitchShown("calc", on, pro))
        assertTrue("cohazard" in LabDefaults.ON)
    }

    @Test fun `turning Cohazard off asks first, turning it on or any other plugin off does not`() {
        assertTrue(PluginReach.asksBeforeOff("cohazard", turningOn = false))
        assertFalse(PluginReach.asksBeforeOff("cohazard", turningOn = true))
        assertFalse(PluginReach.asksBeforeOff("calc", turningOn = false))
    }

    @Test fun `a plugin switched off from its page is listed as off, and stays reachable with nothing on`() {
        val ids = listOf("calc", "facts", "cohazard", "pulse")
        var off = emptySet<String>()
        val on = mutableMapOf("calc" to true, "facts" to false, "cohazard" to true, "pulse" to false)
        // calc off from its page: listed; facts was off by default, not from a page: not listed.
        on["calc"] = false; off = PluginReach.offFromPageAfter(off, "calc", on = false, fromPage = true)
        assertEquals(listOf("calc"), PluginReach.offListed(ids, { on.getValue(it) }, off))
        // Cohazard off (Pro, after the question): listed whatever the path.
        on["cohazard"] = false; off = PluginReach.offFromPageAfter(off, "cohazard", on = false, fromPage = false)
        assertEquals(listOf("calc", "cohazard"), PluginReach.offListed(ids, { on.getValue(it) }, off))
        // Nothing on: the header icon still shows, so the off ones can be found.
        assertTrue(PluginReach.headerIconShown(emptyList(), PluginReach.offListed(ids, { on.getValue(it) }, off)))
        assertFalse(PluginReach.headerIconShown(emptyList(), emptyList()))
        // Back on: out of the Off section.
        on["calc"] = true; off = PluginReach.offFromPageAfter(off, "calc", on = true, fromPage = true)
        assertEquals(listOf("cohazard"), PluginReach.offListed(ids, { on.getValue(it) }, off))
        assertEquals(emptySet<String>(), PluginReach.offFromPageAfter(setOf("calc"), "calc", on = true, fromPage = false))
    }
}
