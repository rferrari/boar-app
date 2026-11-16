package team.sopa.boar.engine.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.canvas.CardRules.Card
import team.sopa.boar.engine.canvas.CardRules.Shape

/** t9 0207 item 2: the rules, conservative, every label and number copied from the answer. */
class CardRulesTest {
    @Test fun `numbered steps become a checklist`() {
        val a = "To purify water:\n1. Collect clear water.\n2. Filter it through cloth [1].\n3. Boil it for one minute.\n4. Let it cool."
        val c = CardRules.find("How do you purify water in the wild, step by step?", a).first() as Card.Steps
        // The [1] mark goes; the words stay as written.
        assertEquals(listOf("Collect clear water.", "Filter it through cloth.", "Boil it for one minute.", "Let it cool."), c.items)
        assertEquals(listOf(Shape.CHECKLIST), c.shapes)
    }

    @Test fun `bullets are steps only for a how-to`() {
        val a = "- Remove the wheel\n- Take out the tube\n- Patch the hole\n- Pump it up"
        assertTrue(CardRules.find("How do I change a flat bicycle tyre?", a).first() is Card.Steps)
        assertTrue(CardRules.find("Tell me about bicycles", a).none { it is Card.Steps })
    }

    @Test fun `an enumeration of three heights is a chart, copied as written`() {
        val a = "The three highest are Everest at 8,849 m, K2 at 8,611 m and Kangchenjunga at 8,586 m."
        val c = CardRules.find("What are the three highest mountains, and how tall is each?", a).first() as Card.Chart
        assertEquals(listOf("Everest", "K2", "Kangchenjunga"), c.points.map { it.label })
        assertEquals(listOf("8,849 m", "8,611 m", "8,586 m"), c.points.map { it.written })
        assertEquals(8849.0, c.points[0].value, 0.0)
        assertEquals(listOf(Shape.BARS, Shape.TABLE), c.shapes)
    }

    @Test fun `a list of values in one unit is a chart, mixed units are not`() {
        val a = "- Water: 100 °C\n- Ethanol: 78 °C\n- Mercury: 357 °C"
        val c = CardRules.find("What are the boiling points of water, ethanol and mercury?", a).first() as Card.Chart
        assertEquals(listOf("Water", "Ethanol", "Mercury"), c.points.map { it.label })
        val mixed = "- Moon: 384,400 km\n- Mars: 225 million km\n- Sun: 1 AU"
        assertTrue(CardRules.find("How far?", mixed).none { it is Card.Chart })
    }

    @Test fun `a pie only for percentages that add up, a line only for years in order`() {
        val pie = CardRules.find("What is air made of?", "- Nitrogen: 78 %\n- Oxygen: 21 %\n- Argon: 1 %").filterIsInstance<Card.Chart>().first()
        assertTrue(Shape.PIE in pie.shapes)
        val noPie = CardRules.find("Shares?", "- A: 30 %\n- B: 20 %\n- C: 10 %").filterIsInstance<Card.Chart>().first()
        assertTrue(Shape.PIE !in noPie.shapes)
        val line = CardRules.find("Population over time?", "- 1990: 5 million\n- 2000: 6 million\n- 2010: 7 million").filterIsInstance<Card.Chart>().first()
        assertTrue(Shape.LINE in line.shapes)
    }

    @Test fun `a markdown table`() {
        val a = "| Planet | Distance |\n|---|---|\n| Mars | 225 million km |\n| Venus | 41 million km |"
        val t = CardRules.find("Distances?", a).filterIsInstance<Card.Table>().first()
        assertEquals(listOf("Planet", "Distance"), t.header); assertEquals(2, t.rows.size)
    }

    @Test fun `label lines are facts, or a comparison when the question compares`() {
        val facts = "Born: 7 November 1867\nField: Physics and chemistry\nKnown for: Radioactivity"
        assertTrue(CardRules.find("Who was Marie Curie?", facts).first() is Card.Facts)
        val cmp = "Solar: works by day; quiet, no moving parts\nWind: works day and night; needs steady wind"
        val c = CardRules.find("Compare solar and wind power.", cmp).first() as Card.Compare
        assertEquals(listOf("Solar", "Wind"), c.sides.map { it.first })
    }

    @Test fun `paragraphs, the way answers are written today, hold no card`() {
        val a = "Marie Curie was a physicist and chemist who did pioneering research on radioactivity [1]. She was the first woman to win a Nobel Prize, " +
            "and the only person to win it in two sciences [2]. Born in Warsaw in 1867, she moved to Paris in 1891."
        assertTrue(CardRules.find("Who was Marie Curie?", a).isEmpty())
    }

    @Test fun `the items the question names, one per sentence, from the F3's real answers`() {
        val boil = CardRules.find("What are the boiling points of water, ethanol and mercury?",
            "Water boils at 100\u202f°C (212\u202f°F) at sea level [2]. Ethanol boils at approximately 78\u202f°C (173\u202f°F) [3]. Mercury boils at 357\u202f°C (675\u202f°F) [3].").first() as Card.Chart
        assertEquals(listOf("Water", "Ethanol", "Mercury"), boil.points.map { it.label })
        assertEquals(listOf("100 °C", "78 °C", "357 °C"), boil.points.map { it.written })
        val far = CardRules.find("How far are the Moon, Mars and the Sun from Earth?",
            "The Moon is about 384,400 km from Earth. Mars varies from about 54.6 million to 401 million km away. The Sun is about 149.6 million km from Earth, on average. [1]")
        // Mars gives two numbers (a range): not certain, so no card.
        assertTrue(far.isEmpty())
        // A question that names nothing: no pairing across sentences.
        assertTrue(CardRules.find("What are the three highest mountains, and how tall is each?", "Mount Everest is 8,850 m. K2 is 8,611 m. Kilimanjaro is 5,895 m.").isEmpty())
    }

    @Test fun `a list label may hold a digit (K2), the number after it is still the value`() {
        val c = CardRules.find("What are the three highest mountains, and how tall is each?", "- Everest: 8,849 m\n- K2: 8,611 m\n- Kangchenjunga: 8,586 m").first() as Card.Chart
        assertEquals(listOf("Everest", "K2", "Kangchenjunga"), c.points.map { it.label })
        assertEquals(listOf(8849.0, 8611.0, 8586.0), c.points.map { it.value })
    }

    @Test fun `a name never takes another item's number (validator B-8)`() {
        val q = "What are the boiling points of water, ethanol and mercury?"
        // Ethanol's sentence names water after the number: it is ethanol's alone, and water's comes from its own sentence.
        val c = CardRules.find(q, "Ethanol boils at 78 °C, well below water. Water boils at 100 °C. Mercury boils at 357 °C.").firstOrNull() as Card.Chart?
        assertEquals(listOf("Water" to 100.0, "Ethanol" to 78.0, "Mercury" to 357.0), c!!.points.map { it.label to it.value })
        // A sentence where the comparison comes first is no one's: no card rather than a wrong bar.
        assertTrue(CardRules.find(q, "Compared with water, ethanol boils at 78 °C. Mercury boils at 357 °C.").none { it is Card.Chart })
    }

    @Test fun `negatives keep their sign, and a chart with one is a table (validator B-9)`() {
        val a = "The coldest inhabited places:\n- Oymyakon is -67 °C\n- Verkhoyansk is −68 °C\n- Yakutsk is -64 °C"
        val c = CardRules.find("What are the coldest inhabited places?", a).first { it is Card.Chart } as Card.Chart
        assertEquals(listOf(-67.0, -68.0, -64.0), c.points.map { it.value })
        assertEquals(listOf("Oymyakon", "Verkhoyansk", "Yakutsk"), c.points.map { it.label })
        assertEquals(listOf(Shape.TABLE), c.shapes)
        // A dash between name and number is still a separator.
        val peaks = CardRules.find("Tallest peaks?", "- Everest - 8,849 m\n- K2 - 8,611 m\n- Kangchenjunga - 8,586 m").first { it is Card.Chart } as Card.Chart
        assertEquals(listOf(8849.0, 8611.0, 8586.0), peaks.points.map { it.value })
    }

    @Test fun `clock times are not facts, a label whose value holds a time is (validator B-10)`() {
        val day = CardRules.find("What does a typical day look like?", "- Breakfast 08:00\n- Lunch 12:30\n- Dinner 19:00")
        assertTrue(day.none { it is Card.Facts })
        val shop = CardRules.find("When is the museum open?", "- Opening hours: 09:00 to 17:00\n- Closed: Mondays\n- Entry: free")
        assertEquals(listOf("Opening hours", "Closed", "Entry"), (shop.first { it is Card.Facts } as Card.Facts).pairs.map { it.first })
    }

    @Test fun `a Portuguese answer's dotted thousands are thousands in the chart (validator B-11)`() {
        val a = "As montanhas mais altas do mundo e do Brasil:\n- Everest: 8.849 m\n- K2: 8.611 m\n- Pico da Neblina: 2.995 m\n- Morro do Elefante: 300 m"
        val c = CardRules.find("Quais são as montanhas mais altas?", a).first { it is Card.Chart } as Card.Chart
        assertEquals(listOf(8849.0, 8611.0, 2995.0, 300.0), c.points.map { it.value })
        // English keeps reading "8.849" as a decimal when nothing says otherwise, and its "8,849" as thousands.
        val en = CardRules.find("Tallest peaks?", "- Everest: 8,849 m\n- K2: 8,611 m\n- Kangchenjunga: 8,586 m").first { it is Card.Chart } as Card.Chart
        assertEquals(listOf(8849.0, 8611.0, 8586.0), en.points.map { it.value })
    }

    @Test fun `a degenerate long answer can't stall the cards (validator B-7)`() {
        val junk = "A".repeat(40_000) + " is 5 m"
        val t0 = System.nanoTime()
        assertTrue(CardRules.find("How tall?", junk).isEmpty())
        assertTrue("took ${(System.nanoTime() - t0) / 1e6} ms", (System.nanoTime() - t0) / 1e6 < 200)
        // Under the cap too (validator R-E4: 11,900 "A" took 1.18 s).
        for (junk2 in listOf("A".repeat(11_900), "É".repeat(11_900), "Mount " + "A".repeat(11_000) + " is 5 m. K2 is 8,611 m.")) {
            val t1 = System.nanoTime()
            CardRules.find("How tall?", junk2)
            assertTrue("took ${(System.nanoTime() - t1) / 1e6} ms", (System.nanoTime() - t1) / 1e6 < 200)
        }
        // A real long answer under the cap still gets its card.
        val long = "x ".repeat(4_000) + "\n- Everest: 8,849 m\n- K2: 8,611 m\n- Kangchenjunga: 8,586 m"
        assertTrue(long.length < CardRules.MAX_ANSWER)
        assertTrue(CardRules.find("Tallest peaks?", long).any { it is Card.Chart })
    }

    @Test fun `a short Portuguese question in a Portuguese app reads dotted thousands as thousands (validator R-B2)`() {
        val was = team.sopa.boar.engine.answer.AnswerLanguage.appLanguage
        try {
            team.sopa.boar.engine.answer.AnswerLanguage.appLanguage = "pt"
            val c = CardRules.find("Montanhas mais altas?", "- Everest: 8.849 m\n- K2: 8.611 m\n- Morro: 300 m").first { it is Card.Chart } as Card.Chart
            assertEquals(listOf(8849.0, 8611.0, 300.0), c.points.map { it.value })
        } finally { team.sopa.boar.engine.answer.AnswerLanguage.appLanguage = was }
    }
}
