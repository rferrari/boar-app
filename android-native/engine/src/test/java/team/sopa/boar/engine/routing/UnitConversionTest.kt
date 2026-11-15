package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Calculator's plain unit conversion (adam, Nov 13: "26 miles in km" went to the model, 35.8 s). */
class UnitConversionTest {
    private fun en(q: String) = Calculators.calculate(q, false)
    private fun conv(q: String) = CalcShortcut.answer(q)?.takeIf { it.code == "calculator-conversion" }?.text

    @Test fun `miles to km in every English shape`() {
        val want = "26 miles = 41.84 km (1 mile = 1.609344 km)."
        for (q in listOf(
            "26 miles in km", "26 mi to km", "convert 26 miles into kilometres", "Convert 26 miles to kilometers?",
            "how many km is 26 miles", "How many kilometres are 26 miles?", "how much is 26 miles in km?", "26mi in km",
        )) assertEquals(q, want, conv(q))
    }

    @Test fun `miles to km in every Portuguese shape`() {
        val want = "26 milhas = 41,84 km (1 milha = 1,609344 km)."
        for (q in listOf("26 milhas em km", "26 milhas para quilômetros", "quantos km são 26 milhas", "Quantos quilômetros são 26 milhas?", "quanto é 26 milhas em km?")) {
            assertEquals(q, want, conv(q))
        }
    }

    @Test fun `the reverse directions`() {
        assertEquals("41.8 km = 25.97 miles (1 mile = 1.609344 km).", conv("41.8 km in miles"))
        assertEquals("80 kg = 176.37 lb (1 lb = 0.45359237 kg).", conv("80 kg in lb"))
        assertEquals("6 feet = 182.88 cm (1 foot = 30.48 cm).", conv("6 feet in cm"))
        assertEquals("41,8 km = 25,97 milhas (1 milha = 1,609344 km).", conv("41,8 km em milhas"))
        assertEquals("80 kg = 176,37 lb (1 lb = 0,45359237 kg).", conv("quantas libras são 80 kg"))
        assertEquals("6 pés = 182,88 cm (1 pé = 30,48 cm).", conv("6 pés em cm"))
    }

    @Test fun `every unit pair the brief names, both ways`() {
        assertEquals("180 cm = 5.91 feet (1 foot = 30.48 cm).", conv("180 cm in feet"))
        assertEquals("2 m = 6.56 feet (1 foot = 0.3048 m).", conv("2 m in ft"))
        assertEquals("10 inches = 25.4 cm (1 inch = 2.54 cm).", conv("10 inches in cm"))
        assertEquals("30 cm = 11.81 inches (1 inch = 2.54 cm).", conv("30 cm to inches"))
        assertEquals("100 yards = 91.44 m (1 yard = 0.9144 m).", conv("100 yards in meters"))
        assertEquals("100 m = 109.36 yards (1 yard = 0.9144 m).", conv("100 m in yards"))
        assertEquals("150 lb = 68.04 kg (1 lb = 0.45359237 kg).", conv("150 lbs to kg"))
        assertEquals("100 g = 3.53 oz (1 oz = 28.349523125 g).", conv("100 g in oz"))
        assertEquals("8 oz = 226.8 g (1 oz = 28.349523125 g).", conv("8 ounces in grams"))
        assertEquals("10 US gallons = 37.85 L (1 US gallon = 3.785411784 L).", conv("10 gallons in liters"))
        assertEquals("20 L = 5.28 US gallons (1 US gallon = 3.785411784 L).", conv("20 litres to US gallons"))
        assertEquals("2 km = 2,000 m (1 km = 1,000 m).", conv("2 km in m"))
        assertEquals("1.5 m = 150 cm (1 m = 100 cm).", conv("1.5 m to cm"))
        assertEquals("5 polegadas = 12,7 cm (1 polegada = 2,54 cm).", conv("5 polegadas em cm"))
        assertEquals("100 jardas = 91,44 m (1 jarda = 0,9144 m).", conv("100 jardas em metros"))
        assertEquals("100 g = 3,53 oz (1 oz = 28,349523125 g).", conv("100 gramas em onças"))
        assertEquals("10 galões americanos = 37,85 L (1 galão americano = 3,785411784 L).", conv("10 galões em litros"))
    }

    @Test fun `decimals in both locales, and a small value keeps three significant digits`() {
        assertEquals("1.5 miles = 2.41 km (1 mile = 1.609344 km).", conv("1.5 miles in km"))
        assertEquals("1,5 milha = 2,41 km (1 milha = 1,609344 km).", conv("1,5 milhas em km"))
        assertEquals("0.02 miles = 0.0322 km (1 mile = 1.609344 km).", conv("0.02 miles in km"))
        assertEquals("0,02 milha = 0,0322 km (1 milha = 1,609344 km).", conv("0,02 milhas em km"))
        assertEquals("1 mile = 1.61 km (1 mile = 1.609344 km).", conv("1 mile in km"))
        assertEquals("1,200 miles = 1,931.21 km (1 mile = 1.609344 km).", conv("1,200 miles in km"))
    }

    @Test fun `in is a preposition first, an inch only before to or in`() {
        assertEquals("5 inches = 12.7 cm (1 inch = 2.54 cm).", conv("5 in to cm"))
        assertEquals("5 inches = 12.7 cm (1 inch = 2.54 cm).", conv("5 in in cm"))
        assertEquals("5 inches = 12.7 cm (1 inch = 2.54 cm).", conv("5 inches in cm"))
        assertEquals("26 miles = 41.84 km (1 mile = 1.609344 km).", conv("26 miles in km"))
        assertNull(en("5 in km"))
    }

    @Test fun `m is a metre only as a whole word`() {
        assertNull(en("I drove 26 miles in my car"))
        assertNull(en("5 min to km"))
        assertEquals("5 m = 500 cm (1 m = 100 cm).", conv("5 m to cm"))
    }

    @Test fun `same unit, another family, or no number - no answer`() {
        for (q in listOf(
            "26 km in kilometres", "5 miles in miles", "5 miles in kg", "80 kg in km", "miles in km", "how many feet in a mile",
            "how many km in a marathon", "convert miles to km", "10 imperial gallons in liters", "10 UK gallons to litres",
            "I walked 5 km to the station", "What is the capital of France?",
        )) assertNull(q, en(q))
    }

    @Test fun `a rate question stays with Arithmetic and the unit bridge`() {
        val a = CalcShortcut.answer("how long does it take to drive 120 miles at 60 mph, answer in km")!!
        assertEquals("calculator-rate-time", a.code)
        assertTrue(a.text, a.text.contains("2 hours") && a.text.contains("120 miles = 193.1 km"))
        val b = CalcShortcut.answer("how long to drive 62 miles at 100 km/h?")!!
        assertEquals("calculator-rate-time", b.code)
        assertTrue(b.text, b.text.contains("62 miles = 99.8 km"))
        for (q in listOf("26 miles in km at 60 mph", "10 km in miles per hour", "100 km/h in mph", "5 km per hour in miles", "20 km por hora em milhas")) {
            assertNull(q, en(q)?.takeIf { it.kind == "conversion" })
        }
    }

    @Test fun `the temperature and fuel rules keep their questions`() {
        assertEquals("temperature-conversion", CalcShortcut.answer("What is 30 °C in Fahrenheit?")!!.code)
        assertEquals("calculator-fuel-economy", CalcShortcut.answer("30 mpg in l/100km")!!.code)
        assertEquals("calculator-fuel-economy", CalcShortcut.answer("7.5 L/100 km in mpg")!!.code)
    }

    @Test fun `a health turn gets no conversion`() {
        assertEquals("calculator-conversion", CalcShortcut.answer("20 kg in lb")!!.code)
        assertNull(CalcShortcut.answer("20 kg in lb for my child"))
        assertNull(CalcShortcut.answer("20 kg in lb", listOf("My daughter is sick and has a fever")))
        assertNull(CalcShortcut.answer("the patient lost 5 kg in lb?"))
    }

    @Test fun `the receipt code and the kind`() {
        assertEquals("conversion", en("26 miles in km")!!.kind)
        val hit = CalcShortcut.answer("26 miles in km")!!
        assertEquals("calculator-conversion", hit.code)
        assertEquals(LabTools.Hit("calculator-conversion", "26 miles = 41.84 km (1 mile = 1.609344 km)."), LabTools.calc("26 miles in km", emptyList()))
        assertEquals(CalcShortcut.Hit("calculator-conversion", "26 milhas = 41,84 km (1 milha = 1,609344 km)."), CalcShortcut.answer("quantos km são 26 milhas"))
    }

    @Test fun `a compound quantity goes to the model, never its last part alone (validator B-2)`() {
        for (q in listOf("5 feet 10 in to cm", "5 ft 10 inches in cm", "1 lb 8 oz to kg", "1 lb and 8 oz in grams", "how many cm is 5 feet 10 inches",
            "2 libras e 3 onças em gramas")) {
            assertEquals(q, null, conv(q))
            assertEquals(q, null, CalcShortcut.answer(q))
        }
        // Two quantities of different kinds are not one compound: the conversion still answers.
        assertEquals("10 inches = 25.4 cm (1 inch = 2.54 cm).", conv("A 5 kg box, 10 inches to cm"))
    }
}
