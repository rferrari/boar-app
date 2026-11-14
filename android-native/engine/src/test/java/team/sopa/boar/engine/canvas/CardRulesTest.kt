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
}
