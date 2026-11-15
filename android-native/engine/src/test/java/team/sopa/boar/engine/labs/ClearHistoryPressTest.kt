package team.sopa.boar.engine.labs

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
        assertTrue(settings.contains("if (press > 0 && !alsoPress) team.sopa.boar.plugins.UserData.Scope.CHATS_KEEP_PRESS else team.sopa.boar.plugins.UserData.Scope.CHATS"))
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
}
