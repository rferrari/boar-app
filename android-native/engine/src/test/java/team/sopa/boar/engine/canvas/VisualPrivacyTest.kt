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
        // The card and the widget kept beside it (Canvas.keyOf) both go.
        assertTrue(read("labs/plugins/canvas/java/team/sopa/boar/labs/canvas/CanvasLab.kt").contains(
            "override fun eraseAnswers(context: Context, answerIds: List<String>) = answerIds.forEach {\n        Canvas.forget(context, it); Canvas.forget(context, Canvas.keyOf(it, Canvas.Kind.WIDGET))"))
    }

    @Test fun `a visual's file holds the model's reply only`() {
        val canvas = read("labs/plugins/canvas/java/team/sopa/boar/labs/canvas/Canvas.kt")
        val save = canvas.substring(canvas.indexOf("private fun save(")).let { it.substring(0, it.indexOf("\n    }\n")) }
        assertFalse(save.contains("\"context\""))
        assertTrue(canvas.contains("fun contextFor("))
    }

    @Test fun `the other erase paths still reach the visuals`() {
        val lab = read("labs/plugins/canvas/java/team/sopa/boar/labs/canvas/CanvasLab.kt")
        assertTrue(lab.contains("override fun eraseUserData(context: Context, scope: UserData.Scope) = Canvas.eraseAll(context)"))
    }

    @Test fun `old files with the context text are rewritten without it, and auto-pruned chats are erased too`() {
        val canvas = read("labs/plugins/canvas/java/team/sopa/boar/labs/canvas/Canvas.kt")
        assertTrue(canvas.contains("if (\"context\" in o) save(ctx, id, done)"))
        val vm = read("main/java/team/sopa/boar/chat/ChatViewModel.kt")
        assertTrue(vm.contains("chats.prune(settings.maxSavedSessions) { id ->\n                    team.sopa.boar.plugins.UserData.eraseAnswers("))
    }
}
