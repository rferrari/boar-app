package team.sopa.boar.engine.chat

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, Nov 14: "now that we dropped the thumbs up and down, bring the receipt time and tokens to the left and leave button on the right". */
class ReceiptLeftTest {
    private val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()

    @Test fun `the receipt starts the footer row, the buttons after it on the right`() {
        val row = chat.substringAfter("Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {").substringBefore("clipboard.setText(")
        assertTrue(row.contains("ReceiptToggle(r, receiptOpen)") && row.contains("ReceiptShort(r)"))
        assertTrue(row.indexOf("ReceiptShort(r)") < row.indexOf("LabHooks.AnswerActions(ref)") && row.indexOf("LabHooks.AnswerActions(ref)") < row.indexOf("FooterButton(\"⧉\""))
        for (f in listOf("private fun ReceiptShort(", "private fun ReceiptToggle(")) {
            val body = chat.substringAfter(f).substringBefore("\n}\n")
            assertTrue(f, body.contains("textAlign = TextAlign.Start") && !body.contains("TextAlign.End"))
        }
    }
}
