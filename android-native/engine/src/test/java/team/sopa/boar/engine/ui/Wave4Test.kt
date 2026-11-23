package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 4 (adam, Nov 12): the chat list. Read from the source. */
class Wave4Test {
    private val drawer = java.io.File("../app/src/main/java/team/sopa/boar/ui/drawer/BoarDrawer.kt").readText()
    private val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()

    @Test fun `no bin on the rows, a long press opens Rename, the plugins' rows and Delete`() {
        assertTrue(!drawer.contains("R.drawable.ic_delete"))
        assertTrue(drawer.contains("onLongClick = { sheetFor = s }") && drawer.contains("ChatActions.actions()"))
        val press = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PrinterLab.kt").readText()
        assertTrue(press.contains("ChatAction(\"native.labs.printer.addChat\")"))
    }

    @Test fun `delete has Undo, the real delete comes after the window and goes through deleteSession`() {
        val del = vm.substringAfter("fun deleteWithUndo(").substringBefore("fun undoDelete()")
        assertTrue(del.contains("delay(team.sopa.boar.engine.chat.ChatList.UNDO_MS)") && del.contains("finishPendingDelete()"))
        assertTrue(vm.substringAfter("private fun finishPendingDelete()").substringBefore("\n    }\n").contains("deleteSession(id)"))
        // deleteSession erases what the plugins made before the chat goes.
        assertTrue(vm.substringAfter("fun deleteSession(id: String)").substringBefore("\n    }\n").contains("UserData.eraseAnswers("))
        assertTrue(drawer.contains("Text(t(\"native.drawer.undo\")"))
    }

    @Test fun `grouped by day, a search box above ten chats`() {
        assertTrue(drawer.contains("ChatList.groupOf(s.updatedAt, now)") && drawer.contains("if (sessions.size > team.sopa.boar.engine.chat.ChatList.SEARCH_FROM)"))
    }
}
