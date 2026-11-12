package team.sopa.boar.engine.labs

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * adam chose (a), Nov 12: deleting a chat takes its answers out of the Press basket. Read from the source (the basket
 * and the sheet are Android classes); the run on a phone is in the report.
 */
class BasketEraseTest {
    private fun read(p: String) = java.io.File("../app/src/$p").readText()
    private val press = read("labs/plugins/printer/java/team/sopa/boar/labs/printer/PrinterLab.kt")
    private val basket = read("labs/plugins/printer/java/team/sopa/boar/labs/printer/Basket.kt")
    private val vm = read("main/java/team/sopa/boar/chat/ChatViewModel.kt")

    @Test fun `a deleted chat's answers leave the basket, whether Press is on or off`() {
        assertTrue(press.contains("override fun eraseAnswers(context: android.content.Context, answerIds: List<String>) = Basket.removeAll(context, answerIds)"))
        val remove = basket.substringAfter("fun removeAll(context: Context, ids: Collection<String>) {").substringBefore("\n    }")
        // Loaded first (a basket not read yet is never written over), then one write without those answers.
        assertTrue(remove.indexOf("load(context)") in 0 until remove.indexOf("change { list -> list.filter { it.id !in gone } }"))
        // Every lab's eraser runs, on or off (LabRegistry.ALL).
        assertTrue(read("labs/java/team/sopa/boar/labs/LabHooks.kt").contains("UserData.eraseAnswers = { ids -> LabRegistry.ALL.forEach { lab -> runCatching { lab.eraseAnswers(app, ids) } } }"))
    }

    @Test fun `only after the Undo window, and on prune`() {
        // deleteSession (the erase) runs from the pending job; Undo cancels that job first.
        assertTrue(vm.substringAfter("fun deleteSession(id: String)").substringBefore("\n    }").contains("UserData.eraseAnswers("))
        assertTrue(vm.substringAfter("fun undoDelete()").substringBefore("\n    }").contains("pendingDeleteJob?.cancel()"))
        assertTrue(vm.contains("team.sopa.boar.plugins.UserData.eraseAnswers(chats.messages(id).filter { it.role == \"assistant\" }.map { it.id })"))
    }

    @Test fun `the sheet's line shows only with Press on and answers in the basket`() {
        assertTrue(press.contains("if (Basket.countOf(context, answerIds) > 0) team.sopa.boar.ui.I18n.t(context, \"native.labs.printer.deleteNote\") else null"))
        // Asked of the labs switched on only.
        assertTrue(read("labs/java/team/sopa/boar/labs/LabHooks.kt").contains("UserData.deleteNotes = { ids -> LabRegistry.active.mapNotNull"))
        val drawer = read("main/java/team/sopa/boar/ui/drawer/BoarDrawer.kt")
        assertTrue(drawer.contains("for (n in deleteNotes) Text(n,"))
        for (d in listOf("values", "values-pt")) assertTrue(d, read("labs/plugins/printer/res/$d/strings_lab_printer.xml").contains("name=\"native_labs_printer_deleteNote\""))
    }

    @Test fun `the header count follows the basket`() {
        assertTrue(press.contains("return Basket.items.collectAsState().value.size"))
        assertTrue(basket.substringAfter("private fun change(").substringBefore("\n    }").contains("_items.value = next"))
    }
}
