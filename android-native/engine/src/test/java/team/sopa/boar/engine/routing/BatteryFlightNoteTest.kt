package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * adam, Nov 14 (t9 0143): "maybe is accurate today, and change in future… better change that?" The battery answer keeps
 * its sum; with flight words it adds one neutral line, no yes or no, no 100 or 160, no carry-on rule.
 */
class BatteryFlightNoteTest {
    private fun calc(q: String) = LabTools.calc(q, emptyList())?.text

    @Test fun `the sum, then the neutral line, in English and Portuguese`() {
        assertEquals("5,000 mAh × 3.85 V ÷ 1000 = 19.25 Wh." + Calculators.FLIGHT_NOTE_EN, calc("My power bank is 5,000 mAh at 3.85 V. Can I take it on a plane?"))
        assertEquals("5.000 mAh × 3,85 V ÷ 1000 = 19,25 Wh." + Calculators.FLIGHT_NOTE_PT, calc("Meu power bank é de 5.000 mAh a 3,85 V. Posso levar no avião?"))
        // Without flight words, the sum alone.
        assertEquals("2,000 mAh × 3.7 V ÷ 1000 = 7.4 Wh.", calc("A 2000 mAh battery at 3.7 V is how many Wh?"))
    }

    @Test fun `no verdict at any size, and no number that is not in the question or the result`() {
        for (q in listOf("10000 mAh 3.7 V on a plane?", "40000 mAh 3.7 V flight", "60000 mAh 3.7 V airline carry-on", "Meu power bank de 30000 mAh a 3,7 V pode ir no avião?")) {
            val a = calc(q)!!
            for (gone in listOf("Yes", "Sim,", "carry-on baggage", "bagagem de mão", "not allowed", "não é permitido")) assertTrue("$q: $gone", !a.contains(gone))
            assertTrue("$q: a 100 or 160 limit", !Regex("(?<![\\d.,])(100|160)(?![\\d.,])").containsMatchIn(a))
            // Digits only, so "10,000" in the answer matches "10000" in the question.
            fun digits(x: String) = x.filter(Char::isDigit)
            val numbers = Regex("\\d[\\d.,]*").findAll(a).map { digits(it.value) }.toSet()
            val result = digits(a.substringBefore(" Wh.").substringAfterLast("= "))
            val asked = Regex("\\d[\\d.,]*").findAll(q).map { digits(it.value) }.toSet()
            for (n in numbers) assertTrue("$q: $n", n in asked || n == result || n == "1000")
        }
    }
}
