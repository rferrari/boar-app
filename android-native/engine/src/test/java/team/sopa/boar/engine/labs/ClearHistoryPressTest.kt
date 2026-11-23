package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * adam, Nov 15 (t9 0115): "ask user if want to remove press or not. let user choose". Clear chat history offers "Also
 * delete Press projects (N) and their photos", off by default; Forgot PIN still erases Press. Read in the source: the
 * dialog and the plugins need Android.
 */
class ClearHistoryPressTest {
    private fun src(p: String) = java.io.File("../app/src/$p").readText()
    private val settings = src("main/java/team/sopa/boar/ui/settings/SettingsScreen.kt")
    private val printer = src("labs/plugins/printer/java/team/sopa/boar/labs/printer/PrinterLab.kt")

    @Test fun `off keeps every project - Press skips the keep-Press scope, and off is the default`() {
        assertTrue(printer.contains("if (scope == team.sopa.boar.plugins.UserData.Scope.CHATS_KEEP_PRESS) return\n        PressStore.eraseAll(context)"))
        assertTrue(settings.contains("var alsoPress by remember { mutableStateOf(false) }"))
        assertTrue(settings.contains("alsoPress = false; press = "))
        // Golden change (validator R-A6): it fails safe: Press goes only when ticked, an unread count keeps it.
        assertTrue(settings.contains("val scope = if (alsoPress) team.sopa.boar.plugins.UserData.Scope.CHATS else team.sopa.boar.plugins.UserData.Scope.CHATS_KEEP_PRESS"))
    }

    @Test fun `on erases them, as before`() {
        // Ticked: the CHATS scope, which Press erases (every project and its folder).
        assertTrue(printer.contains("PressStore.eraseAll(context)"))
        assertTrue(settings.contains("runCatching { team.sopa.boar.plugins.UserData.erase(scope) }"))
    }

    @Test fun `with no Press projects there is no choice`() {
        assertTrue(settings.contains("text = if (press > 0) {"))
        val store = src("labs/plugins/printer/java/team/sopa/boar/labs/printer/PressStore.kt")
        assertTrue(store.contains("projects?.count { it.blocks.isNotEmpty() }"))
        for (d in listOf("values", "values-pt")) assertTrue(d, src("main/res/$d/strings_native.xml").contains("name=\"native_settings_clearChatsPress\""))
    }

    @Test fun `Forgot PIN still erases Press`() {
        assertTrue(src("main/java/team/sopa/boar/security/PinLock.kt").contains("UserData.erase(team.sopa.boar.plugins.UserData.Scope.EVERYTHING)"))
        // Every lab but Press treats the new scope as CHATS: none of them reads the scope except Press.
        val labs = java.io.File("../app/src/labs/plugins").walkTopDown().filter { it.extension == "kt" }.map { it.readText() }
        assertTrue(labs.count { it.contains("Scope.CHATS_KEEP_PRESS") } == 1)
    }

    @Test fun `with Press kept, its drawings are copied in before Designer erases its files (validator R-A3)`() {
        val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()
        val adopt = hooks.indexOf("PressStore.adoptDrawings(app)")
        assertTrue(adopt > 0 && adopt < hooks.indexOf("lab.eraseUserData(app, scope)"))
        val store = src("labs/plugins/printer/java/team/sopa/boar/labs/printer/PressStore.kt")
        assertTrue(store.contains("src.copyTo(dst, overwrite = true); b.copy(file = dst.path)"))
        // And a drawing copied into its project's folder isn't cleaned up as an orphan.
        val dir = kotlin.io.path.createTempDirectory("press").toFile()
        try {
            val pd = PressFiles.projectDir(dir, "p1").apply { mkdirs() }
            val copied = java.io.File(pd, "drawing-a.png").apply { writeText("x") }
            val stray = java.io.File(pd, "old.png").apply { writeText("x") }
            val lib = PressDoc.Library("p1", listOf(PressDoc.Project("p1", "P", 1, listOf(PressDoc.Block.Picture("pic", PressDoc.Kind.DRAWING, copied.path)))))
            assertEquals(listOf(stray.name), PressFiles.orphans(dir, lib).map { it.name })
        } finally { dir.deleteRecursively() }
    }
}

