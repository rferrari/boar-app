package team.sopa.boar.engine.canvas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Validator P-1: a deleted chat takes its visuals with it, and no earlier-answer text is copied to disk. */
class VisualPrivacyTest {
    private val app = File("../app/src")
    private fun read(p: String) = File(app, p).readText()

    @Test fun `deleting a chat erases what the labs made for its answers`() {
        val vm = read("main/java/team/sopa/boar/chat/ChatViewModel.kt")
        val del = vm.substring(vm.indexOf("fun deleteSession(id: String)")).let { it.substring(0, it.indexOf("\n    }\n")) }
        assertTrue(del.contains("UserData.eraseAnswers("))
        assertTrue(del.indexOf("eraseAnswers") < del.indexOf("services.chats.deleteSession(id)"))
        assertTrue(read("labs/java/team/sopa/boar/labs/LabHooks.kt").contains("UserData.eraseAnswers = { ids -> LabRegistry.ALL.forEach"))
        // Cards keeps a card in the answer's meta, which goes with the chat; a file from before Cards, and a widget's beside
        // it, go here.
        assertTrue(read("labs/plugins/canvas/java/team/sopa/boar/labs/canvas/CanvasLab.kt").contains(
            "override fun eraseAnswers(context: Context, answerIds: List<String>) = answerIds.forEach { Canvas.forget(context, it) }"))
        val forget = read("labs/plugins/canvas/java/team/sopa/boar/labs/canvas/Canvas.kt").substringAfter("fun forget(").substringBefore("\n    }\n")
        assertTrue(forget.contains("file(ctx, id).delete(); file(ctx, \"\$id\$WIDGET_SUFFIX\").delete()"))
    }

    @Test fun `no earlier-answer text is kept - only the card, in the answer's own meta`() {
        val canvas = read("labs/plugins/canvas/java/team/sopa/boar/labs/canvas/Canvas.kt")
        // Nothing is written to files any more; the context is read again from the chat when needed.
        assertFalse(canvas.contains("writeText("))
        assertTrue(canvas.contains("fun contextFor("))
        assertTrue(canvas.contains("AnswerCard.withCard(it, card)"))
    }

    @Test fun `the other erase paths still reach the visuals`() {
        val lab = read("labs/plugins/canvas/java/team/sopa/boar/labs/canvas/CanvasLab.kt")
        assertTrue(lab.contains("override fun eraseUserData(context: Context, scope: UserData.Scope) = Canvas.eraseAll(context)"))
    }

    @Test fun `old files move into the meta and are removed, and auto-pruned chats are erased too`() {
        val canvas = read("labs/plugins/canvas/java/team/sopa/boar/labs/canvas/Canvas.kt")
        val old = canvas.substringAfter("private fun fromOldFile(").substringBefore("\n    }\n")
        assertTrue(old.contains("f.delete()") && old.contains("file(ctx, \"\${a.id}\$WIDGET_SUFFIX\").delete()"))
        val vm = read("main/java/team/sopa/boar/chat/ChatViewModel.kt")
        assertTrue(vm.contains("chats.prune(settings.maxSavedSessions) { id ->\n                    team.sopa.boar.plugins.UserData.eraseAnswers("))
    }
}
