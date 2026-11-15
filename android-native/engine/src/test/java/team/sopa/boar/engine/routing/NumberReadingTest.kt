package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * t9 0330 (adam: "send number fix"): "27.000 mAh 3,7 V no avião" read as 27 mAh, 1000 times off. A number is read by the
 * question's other numbers first, its language second; a battery's mAh is a whole count everywhere.
 */
class NumberReadingTest {
    private fun calc(q: String) = CalcShortcut.answer(q)?.text

    @Test fun `the three cases of the note`() {
        assertEquals("27.000 mAh × 3,7 V ÷ 1000 = 99,9 Wh." + Calculators.FLIGHT_NOTE_PT, calc("27.000 mAh 3,7 V no avião"))
        assertEquals("27.000 mAh × 3,7 V ÷ 1000 = 99,9 Wh." + Calculators.FLIGHT_NOTE_PT, calc("27,000 mAh 3.7 V no avião, posso levar?"))
        assertEquals("27,000 mAh × 3.7 V ÷ 1000 = 99.9 Wh." + Calculators.FLIGHT_NOTE_EN, calc("27.000 mAh at 3.7 V on a plane"))
    }

    @Test fun `what worked keeps working`() {
        assertEquals("5.000 mAh × 3,85 V ÷ 1000 = 19,25 Wh.", calc("Meu power bank é de 5.000 mAh a 3,85 V. Quantos watt-hora isso dá?"))
        assertEquals("5,000 mAh × 3.85 V ÷ 1000 = 19.25 Wh.", calc("My power bank is 5,000 mAh at 3.85 V. How many watt-hours is that?"))
        assertTrue(calc("Meu carro alugado faz 7,5 litros a cada 100 km. Quanto isso dá em milhas por galão americano?")!!.startsWith("7,5 L/100 km = 31,36"))
        assertTrue(calc("My rental car uses 7.5 litres per 100 km. How much is that in US miles per gallon?")!!.startsWith("7.5 L/100 km = 31.36"))
        // "2.450 km" with no other number: the language decides.
        assertEquals(2.45, Calculators.parseNumber("2.450", Calculators.readsComma("2.450 km in miles", false)), 1e-9)
        assertEquals(2450.0, Calculators.parseNumber("2.450", Calculators.readsComma("2.450 km em milhas", true)), 1e-9)
        for (pt in listOf(false, true)) {
            assertEquals(1200.5, Calculators.parseNumber("1,200.5", pt), 1e-9)
            assertEquals(1200.5, Calculators.parseNumber("1.200,5", pt), 1e-9)
        }
    }

    @Test fun `the conversion reads its number by the question's other numbers too`() {
        // "1,5" says the comma is decimal: "2.450 km" is 2450 km, in an English sentence.
        assertTrue(calc("1,5 kg and 2.450 km in miles")!!.startsWith("2,450 km = "))
        // "1.5" says the dot is decimal: "2,450 km" is 2450 km, in a Portuguese one.
        assertTrue(calc("1.5 kg e 2,450 km em milhas")!!.startsWith("2.450 km = "))
    }

    @Test fun `the decimal mark from the question's own numbers`() {
        val cases = listOf(
            "27.000 mAh 3,7 V" to ',', "27,000 mAh 3.7 V" to '.', "1.200,5 reais" to ',', "1,200.5 dollars" to '.',
            "3,85 and 3.7" to null, "2.450 km" to null, "26 miles in km" to null, "5.000 mAh a 3,85 V" to ',', "5,000 mAh at 3.85 V" to '.',
        )
        for ((q, want) in cases) assertEquals(q, want, Calculators.decimalMark(q))
        assertNull(Calculators.decimalMark("no numbers here"))
    }

    @Test fun `a date never decides the thousands separator (validator B-1)`() {
        // The date is skipped: the language decides "2.450" as before.
        for (q in listOf("2.450 baht no dia 14.11", "2.450 baht no dia 3.12", "2.450 baht em 14.11.2026", "2.450 baht, vencimento 05.01"))
            assertNull(q, Calculators.decimalMark(q))
        assertTrue(Calculators.readsComma("Quanto é 2.450 baht no dia 14.11 em reais?", true))
        assertEquals(2450.0, Calculators.parseNumber("2.450", Calculators.readsComma("2.450 baht no dia 14.11", true)), 0.0)
        // Not dates: a decimal after "on", a voltage, a price with cents.
        assertEquals('.', Calculators.decimalMark("27,000 mAh on 3.7 V"))
        assertEquals('.', Calculators.decimalMark("2,450 items at 12.50 each"))
        assertEquals('.', Calculators.decimalMark("it costs 3.7 on average"))
    }

    @Test fun `a pasted chain of digit groups doesn't freeze the send (validator B-5)`() {
        val chain = "123 ".repeat(8000) + "miles in km"
        val t0 = System.nanoTime()
        assertNull(Calculators.calculate(chain, false))
        assertNull(CalcShortcut.answer(chain))
        val ms = (System.nanoTime() - t0) / 1e6
        assertTrue("took $ms ms", ms < 500)
        // A calculator question under the cap still answers.
        val padded = "x ".repeat(900) + "26 miles in km"
        assertTrue(padded.length < Calculators.MAX_QUERY)
        assertEquals("26 miles = 41.84 km (1 mile = 1.609344 km).", Calculators.calculate(padded, false)?.text)
    }

    @Test fun `a temperature reads its number by the question's mark (validator B-6)`() {
        assertEquals("1,500 °C = 2,732 °F (°F = °C × 9/5 + 32).", Calculators.calculate("What is 1,500 °C in Fahrenheit?", false)?.text)
        // Unchanged: a plain value, a Portuguese decimal comma, a negative.
        assertEquals("30 °C = 86 °F (°F = °C × 9/5 + 32).", Calculators.calculate("What is 30 °C in Fahrenheit?", false)?.text)
        assertEquals("36,6 °C = 97,9 °F (°F = °C × 9/5 + 32).", Calculators.calculate("Quanto é 36,6 °C em Fahrenheit?", true)?.text)
        assertEquals("-40 °F = -40 °C (°C = (°F − 32) × 5/9).", Calculators.calculate("-40 °F to Celsius", false)?.text)
    }
}
