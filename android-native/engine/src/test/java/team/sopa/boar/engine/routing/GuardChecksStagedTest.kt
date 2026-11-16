package team.sopa.boar.engine.routing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, Nov 14: Staged is the main answer style (on by default), so the check slot (Guard) reads Staged answers too. */
class GuardChecksStagedTest {
    @Test fun `normal, adaptive and staged answers are checked, deep research and recaps are not`() {
        for (path in listOf(null, "adaptive", "staged")) assertTrue("$path", LabTools.checksAnswer(false, false, false, false, path, false))
        for (path in listOf("deep", "chat")) assertFalse(path, LabTools.checksAnswer(false, false, false, false, path, false))
        assertFalse(LabTools.checksAnswer(false, true, false, false, "staged", false))
        assertFalse(LabTools.checksAnswer(true, false, false, false, "staged", false))
    }

    @Test fun `the India answer from adam's Staged chat is corrected`() {
        val fixed = LabTools.guard("Which countries are bigger than India?",
            "No countries are bigger than India by area. India is the world's seventh-largest country by area.")
        assertTrue(fixed?.text.orEmpty(), fixed?.text.orEmpty().startsWith("Six countries are bigger than India"))
    }
}
