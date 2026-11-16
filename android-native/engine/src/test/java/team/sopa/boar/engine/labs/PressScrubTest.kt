package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.labs.PressDoc.Block

/** Validator Q-B3 (t9 0115): a user's own file name doesn't reach a shared PDF or .md through the chat's words. */
class PressScrubTest {
    private val own = PressDoc.Source("imposto-2025-joao-silva", "imposto-2025-joao-silva.pdf")
    private val pack = PressDoc.Source("Income tax", "Wikipedia — https://en.wikipedia.org/wiki/Income_tax")
    private fun project(vararg b: Block) = PressDoc.Project("p", "Taxes", 0, b.toList())

    @Test fun `the question, the chat title, the answer's text and the card's title say Your own file`() {
        val a = Block.Answer("a1", chatTitle = "imposto-2025-joao-silva", question = "what does imposto-2025-joao-silva.pdf say about tax?",
            text = "Imposto-2025-Joao-Silva.pdf lists a refund [1]. Income tax is progressive [2].", sources = listOf(own, pack),
            card = team.sopa.boar.engine.canvas.AnswerCard(team.sopa.boar.engine.canvas.CardRules.Shape.FACTS, "imposto-2025-joao-silva",
                team.sopa.boar.engine.canvas.AnswerCard.Body.Facts(listOf("Refund" to "120")), true))
        val out = PressReceipt.scrubOwnFiles(project(a), "Your own file").answers.single()
        assertEquals("what does Your own file say about tax?", out.question)
        assertEquals("Your own file", out.chatTitle)
        assertEquals("Your own file lists a refund [1]. Income tax is progressive [2].", out.text)
        assertEquals("Your own file", out.card!!.title)
        // Through the Markdown export too: the name appears nowhere.
        val md = PressReceipt.markdown(PressReceipt.scrubOwnFiles(project(a), "Your own file"), PressReceipt.Labels(), "16 Nov 2026", "x", "Sources", "Drawing")
        assertTrue(md, !md.contains("joao", ignoreCase = true))
    }

    @Test fun `what the user typed in Press stays, and a pack's names stay`() {
        val a = Block.Answer("a1", "Taxes", "How does income tax work?", "Income tax is progressive [1].", listOf(pack, own))
        val typed = Block.Note("n1", "Check imposto-2025-joao-silva again")
        val out = PressReceipt.scrubOwnFiles(project(a, typed), "Your own file")
        assertEquals(typed, out.blocks[1])
        assertEquals("Income tax is progressive [1].", out.answers.single().text)
        // No own file: the project is unchanged.
        val p = project(Block.Answer("a2", "T", "Q", "Text", listOf(pack)))
        assertEquals(p, PressReceipt.scrubOwnFiles(p, "Your own file"))
    }
}
