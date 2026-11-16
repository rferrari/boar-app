package team.sopa.boar.engine.labs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * adam chose (a), Nov 12: deleting a chat takes its answers out of Press; since the projects (Nov 13), out of every
 * project. Forgot PIN and Clear all erase every project. The app's wiring, read from the source (the store and the sheet
 * are Android classes); the logic itself is PressDocTest's. The run on a phone is in the report.
 */
class PressEraseTest {
    private fun read(p: String) = java.io.File("../app/src/$p").readText()
    private val dir = "labs/plugins/printer/java/team/sopa/boar/labs/printer"
    private val press = read("$dir/PrinterLab.kt")
    private val store = read("$dir/PressStore.kt")
    private val vm = read("main/java/team/sopa/boar/chat/ChatViewModel.kt")

    @Test fun `a deleted chat's answers leave every project, whether Press is on or off`() {
        assertTrue(press.contains("override fun eraseAnswers(context: android.content.Context, answerIds: List<String>) = PressStore.eraseAnswers(context, answerIds)"))
        val erase = store.substringAfter("fun eraseAnswers(context: Context, ids: Collection<String>) {").substringBefore("\n    }")
        // Loaded first (a file not read yet is never written over), then one write without those answers, in every project.
        assertTrue(erase.indexOf("load(context)") in 0 until erase.indexOf("change { it.eraseAnswers(ids) }"))
        // Every lab's eraser runs, on or off (LabRegistry.ALL).
        assertTrue(read("labs/java/team/sopa/boar/labs/LabHooks.kt").contains("UserData.eraseAnswers = { ids -> LabRegistry.ALL.forEach { lab -> runCatching { lab.eraseAnswers(app, ids) } } }"))
        // And the logic does reach every project.
        val lib = PressDoc.emptyLibrary("p1", "A", 0).addAnswer(PressDoc.Block.Answer("x", "", "q", "t"))
            .newProject("p2", "B", 1).addAnswer(PressDoc.Block.Answer("x", "", "q", "t"))
        assertTrue(lib.eraseAnswers(listOf("x")).projects.all { it.blocks.isEmpty() })
    }

    @Test fun `only after the Undo window, and on prune`() {
        // deleteSession (the erase) runs from the pending job; Undo cancels that job first.
        assertTrue(vm.substringAfter("fun deleteSession(id: String)").substringBefore("\n    }").contains("UserData.eraseAnswers("))
        assertTrue(vm.substringAfter("fun undoDelete()").substringBefore("\n    }").contains("pendingDeleteJob?.cancel()"))
        assertTrue(vm.contains("team.sopa.boar.plugins.UserData.eraseAnswers(chats.messages(id).filter { it.role == \"assistant\" }.map { it.id })"))
    }

    @Test fun `the sheet's line shows only with Press on and answers in a project`() {
        assertTrue(press.contains("if (PressStore.countOf(context, answerIds) > 0) team.sopa.boar.ui.I18n.t(context, \"native.labs.printer.deleteNote\") else null"))
        // Asked of the labs switched on only.
        assertTrue(read("labs/java/team/sopa/boar/labs/LabHooks.kt").contains("UserData.deleteNotes = { ids -> LabRegistry.active.mapNotNull"))
        val drawer = read("main/java/team/sopa/boar/ui/drawer/BoarDrawer.kt")
        assertTrue(drawer.contains("for (n in deleteNotes) Text(n,"))
        for (d in listOf("values", "values-pt")) assertTrue(d, read("labs/plugins/printer/res/$d/strings_lab_printer.xml").contains("name=\"native_labs_printer_deleteNote\""))
    }

    @Test fun `Forgot PIN and Clear all erase every project, its folder and the old basket`() {
        assertTrue(press.substringAfter("override fun eraseUserData(").substringBefore("\n    }").contains("PressStore.eraseAll(context)"))
        val all = store.substringAfter("fun eraseAll(context: Context)").substringBefore("\n    }")
        assertTrue(all.contains("PressFiles.eraseAll(dir(context))"))
        assertTrue(all.contains("basketFile(context).delete()"))
        // A deleted project takes its folder (phase 2's photos) with it.
        assertTrue(store.substringAfter("fun deleteProject(context: Context, id: String)").substringBefore("\n    }").contains("PressFiles.deleteProject(dir(context), id)"))
    }

    @Test fun `the header count follows the open project`() {
        assertTrue(press.contains("return PressStore.library.collectAsState().value?.current?.answers?.size ?: 0"))
        assertTrue(store.substringAfter("fun change(").substringBefore("\n    }").contains("_library.value = next"))
    }

    @Test fun `the app migrates only without a projects file, and keeps an unreadable one`() {
        // The basket file goes only after the projects file was written.
        val migrated = store.substringAfter("is PressDoc.Loaded.Migrated ->").substringBefore("is PressDoc.Loaded.Fresh")
        assertTrue(migrated.indexOf("write(") in 0 until migrated.indexOf("basketFile(context).delete()"))
        // Unreadable: not loaded, so change() writes nothing over it.
        assertTrue(store.substringAfter("is PressDoc.Loaded.Unreadable ->").substringBefore("}").contains("loaded = false"))
        assertTrue(store.substringAfter("fun change(").substringBefore("\n    }").contains("if (!loaded) return"))
        // The old basket's code is gone; nothing else reads basket.json.
        assertFalse(java.io.File("../app/src/$dir/Basket.kt").exists())
    }

    @Test fun `Press fills the page, keeps About in its project sheet, and its icon buttons speak`() {
        assertTrue(press.contains("override val fullPage = true"))
        val screen = read("$dir/PressScreen.kt")
        assertTrue(screen.contains("team.sopa.boar.labs.AboutPluginFold(lab)"))
        // Icon-only actions: each has its name for screen readers (and a long press shows it).
        // Drawn by the plugin pattern's shared round tool (Nov 14), which Press's IconAction calls.
        val chrome = java.io.File("../app/src/labs/java/team/sopa/boar/labs/PluginChrome.kt").readText()
        assertTrue(chrome.contains("Icon(painterResource(icon), label, Modifier.size(20.dp), tint = tint)") && screen.contains("team.sopa.boar.labs.PluginTool(icon, label,"))
        assertTrue(chrome.contains("onLongClick = { Toasts.show(label) }"))
        for (k in listOf("moveUp", "moveDown", "edit", "doneEditing", "remove", "close")) assertTrue(k, screen.contains("\"native.labs.printer.$k\""))
        // The receipt is drawn under the blocks on every page, empty or not; the PDF ends with it too.
        assertTrue(screen.substringAfter("private fun PaperView(").substringBefore("\n}").contains("ReceiptView(page, s)"))
        val pdf = read("$dir/PdfCompiler.kt")
        assertTrue(pdf.substringAfter("fun compile(").substringBefore("p.finish()").contains("receipt(p, page)"))
        // No emoji in the new strings (the chat's own 📎 action stays as it was).
        for (d in listOf("values", "values-pt")) {
            val s = read("labs/plugins/printer/res/$d/strings_lab_printer.xml")
            assertFalse(d, Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]").containsMatchIn(s))
        }
    }
}
