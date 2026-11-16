package team.sopa.boar.engine.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validator Q-A4 (t9 0115): no citation mark of any shape on a card, from the model or the rules. */
class CiteMarksTest {
    @Test fun `every mark shape leaves`() {
        for (m in listOf("[1]", "[1, 2]", "[1,2]", "[1-3]", "[1–3]", "[12]", "[1][2]", "[ 1 ]", "[S1]", "[^1]", "【1】", "【1, 2】", "[S1, S2]"))
            assertEquals(m, "Paris is the capital.", CiteMarks.strip("Paris is the capital $m.").replace(" .", "."))
    }

    @Test fun `code in backticks keeps its brackets, and other brackets stay`() {
        assertEquals("Read `items[1]` first", CiteMarks.strip("Read `items[1]` first [2]"))
        assertEquals("Mix [ratio a] well", CiteMarks.strip("Mix [ratio a] well"))
        assertEquals("1990 [census]", CiteMarks.strip("1990 [census]"))
    }

    @Test fun `a rules checklist drops a list of marks`() {
        val card = CardRules.find("How do I cool a burn?", "1. Run cool water over it for 20 minutes [1].\n2. Remove rings [2].\n3. Let it cool [1, 2].\n4. Cover it loosely [S1].")
        val steps = card.filterIsInstance<CardRules.Card.Steps>().firstOrNull()?.items ?: error("no steps card: $card")
        assertTrue(steps.toString(), steps.none { it.contains('[') })
    }

    @Test fun `a model card drops them from the title, the columns and the cells`() {
        val v = Visual.Table("Peaks 【1】", listOf("Name [ 1 ]", "Height [^1]"), listOf(listOf("Everest [S1]", "8,849 m [1, 2]")))
        val card = AnswerCard.ofVisual(v, emptyList(), "x", "Everest is 8,849 m.")
        assertEquals("Peaks", card.title)
        val t = card.body as AnswerCard.Body.Table
        assertEquals(listOf("Name", "Height"), t.header)
        assertTrue(t.rows.toString(), t.rows.flatten().none { it.contains('[') })
    }
}
