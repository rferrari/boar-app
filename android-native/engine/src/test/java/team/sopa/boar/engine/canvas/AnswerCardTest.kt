package team.sopa.boar.engine.canvas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.canvas.CardRules.Shape

/** Cards (t9 0207 items 3 and 4): the card shown under an answer, its switch, its place in the meta, and its Markdown. */
class AnswerCardTest {
    private val peaksQ = "What are the three highest mountains, and how tall is each?"
    private val peaks = "The three highest are Everest at 8,849 m, K2 at 8,611 m and Kangchenjunga at 8,586 m."
    private val breadQ = "How do I make a simple no-knead bread?"
    private val bread = "You need:\n- Flour: 500 g\n- Water: 350 g\n- Salt: 10 g\n- Yeast: 3 g\n\n1. Mix everything in a bowl.\n2. Cover it and let it rest for 12 hours.\n3. Shape it into a ball.\n4. Bake it 45 minutes at 230 °C."

    @Test fun `the switch holds every shape of every card found, best first`() {
        val cards = CardRules.find(peaksQ, peaks)
        assertEquals(listOf(AnswerCard.Choice(0, Shape.BARS), AnswerCard.Choice(0, Shape.TABLE)), AnswerCard.choices(cards))
        val both = CardRules.find(breadQ, bread)
        val choices = AnswerCard.choices(both)
        // The how-to's checklist first, then the amounts as bars and a table (no pie: grams are not parts of 100 %).
        assertEquals(Shape.CHECKLIST, choices.first().shape)
        assertTrue(choices.any { it.shape == Shape.BARS } && choices.none { it.shape == Shape.PIE })
    }

    @Test fun `a rule card copies the answer, with a title from what the chat shows`() {
        val chart = CardRules.find(peaksQ, peaks).first()
        val card = AnswerCard.of(chart, Shape.TABLE, AnswerCard.titleFor(chart, peaksQ, listOf("List of highest mountains on Earth")))
        assertEquals(Shape.TABLE, card.shape)
        assertEquals("List of highest mountains on Earth", card.title)
        assertFalse(card.byModel)
        val (header, rows) = AnswerCard.asTable(card)!!
        assertEquals(listOf("", "m"), header)
        assertEquals(listOf(listOf("Everest", "8,849 m"), listOf("K2", "8,611 m"), listOf("Kangchenjunga", "8,586 m")), rows)
        // No source: the question, short; a comparison names its sides.
        assertEquals("What are the three highest mountains, and how tall is each", AnswerCard.titleFor(chart, peaksQ, emptyList()))
        val cmp = CardRules.Card.Compare(listOf("Solar" to listOf("by day"), "Wind" to listOf("day and night")))
        assertEquals("Solar · Wind", AnswerCard.titleFor(cmp, "Compare solar and wind power.", listOf("Solar power")))
        // A shape the card doesn't have falls back to its first.
        assertEquals(Shape.BARS, AnswerCard.of(chart, Shape.PIE, "t").shape)
    }

    @Test fun `what the sheet's line counts`() {
        assertEquals(AnswerCard.Found.CHART to 3, AnswerCard.found(CardRules.find(peaksQ, peaks).first()))
        assertEquals(AnswerCard.Found.STEPS to 4, AnswerCard.found(CardRules.find(breadQ, bread).first()))
    }

    @Test fun `every kind reads back as written, ticks with it for Press`() {
        val cards = listOf(
            AnswerCard(Shape.BARS, "Peaks", AnswerCard.Body.Series("m", listOf(CardRules.Point("Everest", "8,849 m", 8849.0), CardRules.Point("K2", "8,611 m", 8611.0)))),
            AnswerCard(Shape.TABLE, "T", AnswerCard.Body.Table(listOf("A", "B"), listOf(listOf("1", "2")))),
            AnswerCard(Shape.CHECKLIST, "S", AnswerCard.Body.Steps(listOf("one", "two", "three")), ticks = setOf(0, 2)),
            AnswerCard(Shape.FACTS, "F", AnswerCard.Body.Facts(listOf("Born" to "1867"))),
            AnswerCard(Shape.COMPARE, "C", AnswerCard.Body.Compare(listOf("Solar" to listOf("by day"), "Wind" to listOf("at night")))),
            AnswerCard(Shape.PIE, "M", AnswerCard.Body.Series("%", listOf(CardRules.Point("N", "78 %", 78.0))), byModel = true, leftOut = listOf("Argon")),
        )
        for (c in cards) assertEquals(c, AnswerCard.fromJson(AnswerCard.toJson(c, withTicks = true)))
        // In the meta the ticks live beside the card, not in it.
        assertFalse("ticks" in AnswerCard.toJson(cards[2]))
        assertNull(AnswerCard.fromJson(Json.parseToJsonElement("""{"shape":"HEXAGON","kind":"steps","steps":["a"]}""")))
        assertNull(AnswerCard.fromJson(Json.parseToJsonElement("""{"shape":"BARS","kind":"poster"}""")))
    }

    @Test fun `the card and its ticks join the answer's meta, the rest of it kept`() {
        val meta = """{"sources":[{"title":"Everest","body":"…"}],"receipt":{"model":"Qwen3 4B"}}"""
        val card = AnswerCard(Shape.CHECKLIST, "S", AnswerCard.Body.Steps(listOf("a", "b", "c")))
        val with = AnswerCard.withCard(meta, card)!!
        val ticked = AnswerCard.withTicks(with, setOf(1))!!
        val o = Json.parseToJsonElement(ticked).jsonObject
        assertTrue("sources" in o && "receipt" in o && "card" in o && "cardTicks" in o)
        assertEquals(card.copy(ticks = setOf(1)), AnswerCard.fromMeta(ticked))
        assertEquals(setOf(1), AnswerCard.ticksFromMeta(ticked))
        // ✕ takes the card and its ticks; the receipt stays.
        val removed = Json.parseToJsonElement(AnswerCard.withCard(ticked, null)!!).jsonObject
        assertTrue("receipt" in removed && "card" !in removed && "cardTicks" !in removed)
        // No meta yet starts one; a meta that can't be read is never written over.
        assertEquals(card, AnswerCard.fromMeta(AnswerCard.withCard(null, card)))
        assertNull(AnswerCard.withCard("not json", card))
        assertNull(AnswerCard.withTicks("[1,2]", setOf(1)))
        assertNull(AnswerCard.fromMeta(meta))
    }

    @Test fun `the model's card is marked as made by the model, a share as a pie`() {
        val v = Visual.Series("Air", Visual.SeriesKind.SHARE, "%", listOf("Nitrogen" to 78.0, "Oxygen" to 21.0, "Argon" to 1.0))
        val c = AnswerCard.ofVisual(v, listOf("CO2"), "fallback")
        assertEquals(Shape.PIE, c.shape)
        assertTrue(c.byModel)
        assertEquals(listOf("CO2"), c.leftOut)
        assertEquals(listOf("78 %", "21 %", "1 %"), (c.body as AnswerCard.Body.Series).points.map { it.written })
        assertEquals(listOf(78, 21, 1), AnswerCard.percents(c.body as AnswerCard.Body.Series))
        assertEquals("fallback", AnswerCard.ofVisual(Visual.Steps("", listOf("a", "b")), emptyList(), "fallback").title)
    }

    @Test fun `in Markdown - a table for a series, numbered steps, label lines, a list under each name`() {
        val chart = AnswerCard.of(CardRules.find(peaksQ, peaks).first(), Shape.BARS, "Peaks")
        assertEquals("**Peaks**\n\n| Name | m |\n| --- | --- |\n| Everest | 8,849 m |\n| K2 | 8,611 m |\n| Kangchenjunga | 8,586 m |",
            AnswerCard.markdown(chart, nameLabel = "Name"))
        assertEquals("**S**\n\n1. one\n2. two", AnswerCard.markdown(AnswerCard(Shape.CHECKLIST, "S", AnswerCard.Body.Steps(listOf("one", "two")), ticks = setOf(0))))
        assertEquals("- Born: 1867\n- Field: Physics", AnswerCard.markdown(AnswerCard(Shape.FACTS, "", AnswerCard.Body.Facts(listOf("Born" to "1867", "Field" to "Physics")))))
        assertEquals("**Solar**\n- by day\n**Wind**\n- at night",
            AnswerCard.markdown(AnswerCard(Shape.COMPARE, "", AnswerCard.Body.Compare(listOf("Solar" to listOf("by day"), "Wind" to listOf("at night"))))))
        // A cell's | can't break the table.
        assertTrue(AnswerCard.markdown(AnswerCard(Shape.TABLE, "", AnswerCard.Body.Table(listOf("a|b"), listOf(listOf("x"))))).startsWith("| a\\|b |"))
    }

    @Test fun `a model's card - negatives as a table, no answer marks, and gone when Press edits the text (R-E1, R-E2, R-E3)`() {
        val cold = Visual.Series("Coldest", Visual.SeriesKind.BARS, "°C", listOf("Oymyakon [1]" to -67.0, "Yakutsk" to 5.0))
        val c = AnswerCard.ofVisual(cold, emptyList(), "t")
        assertEquals(Shape.TABLE, c.shape)
        assertEquals(listOf("Oymyakon", "Yakutsk"), (c.body as AnswerCard.Body.Series).points.map { it.label })
        val steps = AnswerCard.ofVisual(Visual.Steps("Boil [2]", listOf("Boil the water [1]", "Let it cool [1, 2]")), emptyList(), "t")
        assertEquals(listOf("Boil the water", "Let it cool"), (steps.body as AnswerCard.Body.Steps).items)
        assertEquals("Boil", steps.title)
        // Press: "Use the new text" drops the card (it showed the old text's figures).
        val a = team.sopa.boar.engine.labs.PressDoc.Block.Answer("a", "Chat", "Q?", "Everest 8,849 m, K2 8,611 m.", emptyList(), 1, null, null, card = c)
        val lib = team.sopa.boar.engine.labs.PressDoc.Library("p", listOf(team.sopa.boar.engine.labs.PressDoc.Project("p", "P", 1, listOf(a))))
        val edited = lib.editText("a", team.sopa.boar.engine.labs.PressDoc.Field.TEXT, "Mountains are tall.").current.blocks.single() as team.sopa.boar.engine.labs.PressDoc.Block.Answer
        assertEquals(null, edited.card)
        assertTrue(edited.edited)
    }
}
