package team.sopa.boar.engine.chat

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, Nov 14: a plugin's error under an answer goes after the separator with BOAR's notes, the newest last. */
class PluginNotesFooterTest {
    private fun read(p: String) = java.io.File("../app/src/$p").readText()

    @Test fun `plugin notes sit under the separator after BOAR's own, before the receipt row`() {
        val chat = read("main/java/team/sopa/boar/ui/chat/ChatScreen.kt")
        val footer = chat.substringAfter("Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(0.5.dp)").substringBefore("Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart)")
        assertTrue(footer.indexOf("StagedCheck(it)") in 0 until footer.indexOf("LabHooks.AnswerNotes(ref)"))
    }

    @Test fun `oldest first, so a new note is the last line`() {
        val hooks = read("labs/java/team/sopa/boar/labs/LabHooks.kt").substringAfter("fun AnswerNotes(").substringBefore("\n    }\n")
        assertTrue(hooks.contains(".sortedBy { it.at }"))
    }

    @Test fun `Designer's failure is a note, not a line under the answer text`() {
        val d = read("labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt")
        assertTrue(d.contains("is Illustrator.Status.Failed -> {}") && d.contains("override fun answerNotes("))
        assertTrue(read("labs/plugins/designer/java/team/sopa/boar/labs/designer/Illustrator.kt").contains("data class Failed(val message: String, val at: Long"))
    }
}
