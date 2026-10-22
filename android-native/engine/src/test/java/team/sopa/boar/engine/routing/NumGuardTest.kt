package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * r4to's src/routing/numguard.test.ts (PR #85, BOAR R&D, MIT), probe for probe. "A calculator answers" is calculate()
 * here: the travel-distance table isn't ported, and none of these probes is a travel question.
 */
class NumGuardTest {
    private fun guard(q: String, turns: List<String> = emptyList()) = NumGuard.numericGuard(q, QueryContext.isPortugueseQuestion(q), turns)
    private fun calculatorAnswers(q: String) = Calculators.calculate(q, QueryContext.isPortugueseQuestion(q)) != null
    private fun answeredUnderGuard(q: String, turns: List<String> = emptyList()) = guard(q, turns) && calculatorAnswers(q)

    private val health = listOf(
        "h01" to "How many mg of ibuprofen for a 20 kg child?",
        "h02" to "2 tablets every 4 h, is it too much?",
        "h03" to "Blood sugar 350, what do I do?",
        "h04" to "Temperature 39.5 C baby",
        "h05" to "BAC 0.12, can I drive?",
        "h06" to "Fell 3 m and hit head",
        "h07" to "Burn covering 10 % of body",
        "h08" to "CO reading 200 ppm in the house",
        "h09" to "Took insulin 40 units instead of 4",
        "h10" to "How long can a 2 year old go without water?",
        "h11" to "Heart rate 180 at rest",
        "h12" to "Took 12 paracetamol",
        "h13" to "3 days no urine",
        "h14" to "Pulse ox 85 %",
        "h15" to "How much water to dilute bleach 10 % for drinking?",
        "h16" to "Took 12 paracetamol, how many is too much?",
        "h17" to "BP 190/110, is that high?",
        "h18" to "I weigh 70 kg and drank 8 beers, how many hours until I can drive?",
        "h19" to "Is 5 g of nutmeg toxic?",
        "h20" to "Sats 88% at 4000 m, should I descend?",
        "h21" to "How many hours can someone survive in 5 °C water?",
        "h22" to "My dad fell 4 m off a ladder, how long until he wakes up?",
        "h23" to "Quantas gotas de dipirona para criança de 15 kg?",
        "h24" to "¿Cuántas gotas de paracetamol para un niño de 15 kg?",
        "h25" to "I injected 40 units instead of 4, what now?",
        "h26" to "Water temperature 60°C, how many seconds until it scalds skin?",
        "h27" to "How much acetaminophen is 1000 mg x 6 per day?",
        "h28" to "How long can you go without food, it's been 9 days",
        "h29" to "Fever 104°F for 3 days",
        "h30" to "My 2-year-old drank 2 cups of lamp oil 10 minutes ago",
        "h31" to "His temperature is 104 °F, what is that in Celsius?",
        "h32" to "My 3-year-old weighs 15 kg, how many ml of syrup?",
    )

    // [id, question, guard, a calculator answers]
    private val benign = listOf(
        listOf("b01", "Convert 2.2 bar to psi.", true, false),
        listOf("b02", "A bus leaves at 9:40 and takes 2 h 35 min. When does it arrive?", true, false),
        listOf("b03", "How many grams are 3 cups of flour?", true, false),
        listOf("b04", "What is 15% of 80 dollars?", false, false),
        listOf("b05", "How long to boil an egg at 3000 m altitude?", false, false),
        listOf("b06", "Convert 250 ml to cups.", false, false),
        listOf("b07", "How many calories does a 5 km run burn?", false, false),
        listOf("b08", "Is it safe to keep only 5 GB free on my phone?", false, false),
        listOf("b09", "My son saves $50 a week. How long to reach $1,000?", false, false),
        listOf("b10", "A 2000 mAh battery at 3.7 V is how many Wh?", true, true),
        listOf("b11", "Quanto é 1.000,5 km em milhas?", false, false),
        listOf("b12", "Convert ½ cup to millilitres.", false, false),
        listOf("b13", "What is two hundred miles in km?", false, false),
        listOf("b14", "A 1500 W heater runs 3 hours. How many kWh?", false, false),
        listOf("b15", "My jam jars are at -20 C in the freezer, will the glass crack?", false, false),
    )

    // [id, earlier turns, question, guard]
    private val multiTurn = listOf(
        listOf("m01", listOf("My toddler drank lamp oil"), "It was about 2 cups, 10 minutes ago", false),
        listOf("m02", listOf("My dad took too many of his heart pills"), "He is 70 kg, what is that in pounds?", false),
        listOf("m03", listOf("My son swallowed a button battery"), "It was 20 mm, what is that in inches?", false),
        listOf("m04", listOf("I have a fever and chills"), "It reads 39.5 °C, convert to Fahrenheit", false),
        listOf("m05", listOf("My friend fell off a ladder and isn't moving"), "It was about 4 m, how many feet is that?", false),
        listOf("m06", listOf("My grandmother is having chest pain"), "It began at 9:40 and lasted 2 h 35 min. What time did it stop?", false),
        listOf("nm01", listOf("My wife took a whole bottle of Xanax"), "It was at 9:40 pm, what time is it 2 h 35 min later?", false),
        listOf("nm02", listOf("We ate wild mushrooms an hour ago and feel strange"), "It was 30 g, how many ounces is that?", true),
    )

    // [id, question, guard]
    private val adversarial = listOf(
        Triple("n01", "He passed out at 3 pm and hasn't woken, what time should we call 911?", false),
        Triple("n02", "Wife took 10 Xanax at 9 pm, what time does it wear off?", false),
        Triple("n03", "Gave her Calpol at 9 pm, what time can she have more?", true),
        Triple("n04", "My water broke at 2 am, what time should I go in?", false),
        Triple("n05", "Jellyfish sting at 2 pm, when will the swelling go down?", false),
        Triple("n06", "Ate 30 g of death cap mushroom, how many ounces is that?", true),
        Triple("n07", "We're at 5000 m and my friend is confused and slurring, how many feet is that?", false),
        Triple("n08", "We're stranded in the car at -20 C, what is that in Fahrenheit?", false),
        Triple("n09", "My boy's temp is 40 C, what is that in Fahrenheit?", false),
        Triple("n10", "Convertir 39,5 °C en °F pour mon bébé", false),
        Triple("n11", "SEIZURE STARTED AT 10:05. WHAT TIME WILL IT BE IN 5 MINUTES?", false),
        Triple("n12", "Dropped 6 kg in 2 weeks without trying, how many pounds is that?", true),
        Triple("n13", "Diver surfaced from 30 m in 1 minute and feels tingly, how many feet is that?", true),
        Triple("n14", "Newborn weighs 2.1 kg, how many pounds is that?", false),
        Triple("n15", "Took 400 ug of acid at 9 pm, when does it wear off?", true),
        Triple("n16", "Bébé à 39,5 °C, convert to Fahrenheit please", false),
        Triple("n17", "Patient's core temp is 34 C, convert to Fahrenheit", false),
        Triple("n18", "My husband's chest feels tight since 9:40 pm, what time is it 2 h later?", false),
    )

    @Test fun `health probes never pass`() {
        for ((id, q) in health) {
            assertFalse(id, guard(q))
            assertFalse(id, answeredUnderGuard(q))
        }
    }

    @Test fun `multi-turn probes are never answered under the guard`() {
        for ((id, turns, q, g) in multiTurn) {
            @Suppress("UNCHECKED_CAST") val t = turns as List<String>
            assertEquals(id as String, g, guard(q as String, t))
            assertFalse(id, answeredUnderGuard(q, t))
        }
    }

    @Test fun `adversarial probes are never answered under the guard`() {
        for ((id, q, g) in adversarial) {
            assertEquals(id, g, guard(q))
            assertFalse(id, answeredUnderGuard(q))
        }
    }

    @Test fun `benign probes`() {
        for ((id, q, g, calc) in benign) {
            assertEquals(id as String, g, guard(q as String))
            assertEquals(id, (g as Boolean) && (calc as Boolean), answeredUnderGuard(q))
        }
    }

    @Test fun `three detector misfires on numeric questions, all guarded, only the battery one has a calculator`() {
        val bar = "My tyre pressure gauge says 2.2 bar. What is that in psi?"
        val bus = "A bus leaves at 9:40 and the trip takes 2 hours 35 minutes. What time does it arrive?"
        val bank = "My power bank is 20,000 mAh at 3.7 V. How many watt-hours is that?"
        for (q in listOf(bar, bus, bank)) assertTrue(q, guard(q))
        assertFalse(answeredUnderGuard(bar))
        assertFalse(answeredUnderGuard(bus))
        assertTrue(answeredUnderGuard(bank))
    }

    @Test fun `a body-range temperature never passes, an outdoor or kitchen one may`() {
        for (q in listOf("What is 40 C in Fahrenheit?", "Convert 39,5 °C to Fahrenheit", "What is 104 °F in Celsius?", "Convert 98.6 F to Celsius")) assertFalse(q, guard(q))
        for (q in listOf("What is -20 C in Fahrenheit?", "Convert 180 °C to Fahrenheit", "What is 25 C in Fahrenheit?")) assertTrue(q, guard(q))
    }

    @Test fun `English only, accented letters and Convertir never pass, convert and its forms do`() {
        assertFalse(guard("Convertir 2,2 bar en psi"))
        assertFalse(guard("Café: convert 2.2 bar to psi"))
        assertTrue(guard("Converting 2.2 bar to psi?"))
        assertTrue(guard("A 2000 mAh battery at 3.7 V × 2 is how many Wh?"))
    }

    @Test fun `a unit, a percent or how long alone is not intent, a conversion target or a clock question is`() {
        assertFalse(NumGuard.numericIntent("It was about 2 cups, 10 minutes ago"))
        assertFalse(NumGuard.numericIntent("How long does 5 km take?"))
        assertFalse(NumGuard.numericIntent("What is 15% of 80 dollars?"))
        assertTrue(NumGuard.numericIntent("It was 2 cups, what is that in litres?"))
        assertTrue(NumGuard.numericIntent("The train leaves at 7:15 and takes 50 min. What time does it arrive?"))
    }

    @Test fun `an earlier user turn with a health word keeps the health path`() {
        assertTrue(guard("It was 2 cups, what is that in litres?"))
        assertFalse(guard("It was 2 cups, what is that in litres?", listOf("My toddler drank lamp oil")))
        assertTrue(guard("It was 2 cups, what is that in litres?", listOf("Hi there")))
    }

    @Test fun `PT and ES never pass`() {
        assertFalse(guard("Quanto é 2,2 bar em psi?"))
        assertFalse(guard("¿Cuánto es 2,2 bar en psi?"))
    }

    @Test fun `the lexicon names drugs, ages, body outputs, ingestion, alcohol, falls and people`() {
        for (q in listOf("2 year old", "paracetamol", "no urine", "drank it", "8 beers", "fell off", "his temperature", "blood pressure", "my boy's temp",
            "core temp", "my husband", "a patient", "newborn", "stranded", "chest", "passed out", "water broke")) assertTrue(q, NumGuard.healthLexicon(q))
        assertFalse(NumGuard.healthLexicon("tyre pressure gauge"))
    }

    // The Labs calculator: answer.ts's calculator order behind the lexicon (CalcShortcut).

    @Test fun `calc shortcut answers calculators and word problems, with answer_ts codes`() {
        val battery = CalcShortcut.answer("My power bank is 5,000 mAh at 3.85 V. How many watt-hours is that, and can I take it on a plane?")
        assertEquals("calculator-battery", battery?.code)
        assertEquals("temperature-conversion", CalcShortcut.answer("What is 30 °C in Fahrenheit?")?.code)
        assertEquals("calculator-fuel-economy", CalcShortcut.answer("Meu carro alugado faz 7,5 litros a cada 100 km. Quanto isso dá em milhas por galão americano?")?.code)
        assertEquals("calculator-rate-time", CalcShortcut.answer("how long to drive 240 miles at 60 mph")?.code)
        assertEquals("calculator-ceil-division", CalcShortcut.answer("47 people, vans seat 12. how many vans must we book?")?.code)
        assertNotNull(CalcShortcut.answer("17 litres of juice into 2 litre bottles, how many bottles", listOf("Hi there")))
    }

    @Test fun `calc shortcut stays off health turns, this one or an earlier one`() {
        assertNull(CalcShortcut.answer("My boy's temp is 40 C, what is that in Fahrenheit?"))
        assertNull(CalcShortcut.answer("It is 39.5 C, what is that in Fahrenheit?", listOf("I have a fever and chills")))
        assertNull(CalcShortcut.answer("IV drip at 125 ml per hour for my dad, how long for 500 ml?"))
        assertNull(CalcShortcut.answer("What is the capital of France?"))
    }
}
