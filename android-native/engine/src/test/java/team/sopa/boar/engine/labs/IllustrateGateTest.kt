package team.sopa.boar.engine.labs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IllustrateGateTest {
    @Test fun `the model's answers, the Calculator's and Facts' can be illustrated`() {
        assertTrue(IllustrateGate.allowed(null, "Canberra was chosen in 1908."))
        assertTrue(IllustrateGate.allowed("calculator-rate-time", "About 2 hours: 120 miles ÷ 60 miles per hour = 2 hours."))
        assertTrue(IllustrateGate.allowed("temperature-conversion", "30 °F is about −1.1 °C."))
        assertTrue(IllustrateGate.allowed("facts-population", "Brazil has about 212 million people (Wikidata)."))
        assertTrue(IllustrateGate.allowed("lunar-phase", "The moon is a waxing gibbous now, 78% lit."))
        assertTrue(IllustrateGate.allowed("compass-heading", "The top of your phone points north (349°)."))
    }

    @Test fun `other tool answers stay out`() {
        for (t in listOf("safety-fact-co-hazard", "calculator-travel-distance", "language-gate", "vitals-phone", "verdict-guard-date"))
            assertFalse(t, IllustrateGate.allowed(t, "Some answer."))
    }

    @Test fun `a warning-style answer stays refused even from Facts or the Calculator`() {
        assertFalse(IllustrateGate.allowed("facts-x", "⚠ Never run a generator indoors: carbon monoxide is deadly."))
        assertFalse(IllustrateGate.allowed("calculator-fuel", "Warning: do not refuel a hot engine."))
        assertFalse(IllustrateGate.allowed("facts-x", "Nunca use um gerador em lugar fechado: perigo de monóxido."))
    }
}
