package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.canvas.AnswerCard
import team.sopa.boar.engine.canvas.CardRules
import team.sopa.boar.engine.labs.PressDoc.Block

/** Cards in Press (t9 0207 item 4): the answer's Add to Press takes its card along; old projects still read. */
class PressCardTest {
    private val steps = AnswerCard(CardRules.Shape.CHECKLIST, "Purify water", AnswerCard.Body.Steps(listOf("Collect it.", "Boil it.", "Let it cool.")), ticks = setOf(1))

    private fun lib(card: AnswerCard?) = PressDoc.emptyLibrary("p1", "Field notes", 1).addAnswer(
        Block.Answer("m1", "Chat", "How do you purify water?", "Collect, boil, cool [1].", listOf(PressDoc.Source("Water purification", "Wikipedia")), 5, card = card))

    @Test fun `an answer's card and its ticks are kept in the project`() {
        val back = PressDoc.parse(PressDoc.toJson(lib(steps)))
        assertEquals(steps, back.current.answers.single().card)
        // Golden change (validator R-E3): an edit of the text drops the card, which showed the old text's figures.
        assertNull(back.editText("m1", PressDoc.Field.TEXT, "Boil it.").current.answers.single().card)
        // Its heading edited, the card stays.
        assertEquals(steps, back.editText("m1", PressDoc.Field.HEADING, "Water").current.answers.single().card)
    }

    @Test fun `a projects file from before Cards still reads, with no card`() {
        val old = """{"version":1,"open":"p1","projects":[{"id":"p1","name":"Field notes","createdAt":1,"seenAt":1,"blocks":[
            {"type":"answer","id":"m1","chatTitle":"Chat","question":"Q","heading":"","text":"T","addedAt":5,"model":null,"tokPerSec":null,"edited":false,"sources":[]}]}]}"""
        val a = PressDoc.parse(old).current.answers.single()
        assertEquals("T", a.text)
        assertNull(a.card)
        // Written again with no card, it has no "card" key.
        assertTrue("\"card\"" !in PressDoc.toJson(PressDoc.parse(old)))
        // A card a newer BOAR wrote in a form this one doesn't know is left out, the answer kept.
        val newer = old.replace("\"sources\":[]}", "\"sources\":[],\"card\":{\"shape\":\"HOLOGRAM\"}}")
        assertNull(PressDoc.parse(newer).current.answers.single().card)
    }

    @Test fun `the Markdown has the card after the answer's lines, as a numbered list for steps`() {
        val md = PressReceipt.markdown(lib(steps).current, PressReceipt.Labels(), "14 Nov 2026", "2.0.0", "Sources", "Drawing")
        val text = md.indexOf("Collect, boil, cool [1].")
        val refs = md.indexOf("Sources [1]")
        val card = md.indexOf("**Purify water**\n\n1. Collect it.\n2. Boil it.\n3. Let it cool.")
        assertTrue(md, text in 0 until refs && refs < card)
        assertTrue("1. Collect" !in PressReceipt.markdown(lib(null).current, PressReceipt.Labels(), "d", "v", "Sources", "Drawing"))
    }

    @Test fun `a chart goes to the Markdown as a table`() {
        val chart = AnswerCard(CardRules.Shape.PIE, "Air", AnswerCard.Body.Series("%", listOf(CardRules.Point("Nitrogen", "78 %", 78.0), CardRules.Point("Oxygen", "21 %", 21.0))))
        val md = PressReceipt.markdown(lib(chart).current, PressReceipt.Labels(), "d", "v", "Sources", "Drawing")
        assertTrue(md, md.contains("| Nitrogen | 78 % |\n| Oxygen | 21 % |"))
    }
}
