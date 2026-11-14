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

    @Test fun `the answer's notes sit under the separator, above the receipt row`() {
        // adam, Nov 14: "Every number in this answer is in its sources" read as part of the answer.
        val footer = chat.substringAfter("if (m.text.isNotEmpty() && !streaming) {\n                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(0.5.dp)").substringBefore("Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart)")
        assertTrue(footer.contains("m.receipt?.styleNote?.let {") && footer.contains("StagedCheck(it)"))
        assertTrue(Regex("StagedCheck\\(it\\)").findAll(chat).count() == 1 && Regex("native.chat.styleNote").findAll(chat).count() == 1)
    }
}
