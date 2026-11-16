package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam's decisions and the UX review's small batch, Nov 11. Read from the source. */
class SmallsWordsTest {
    private fun res(d: String, f: String) = java.io.File("../app/src/main/res/$d/$f").readText()
    private val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
    private val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()

    @Test fun `Personal Files is Local Files, in English and Portuguese`() {
        for (d in listOf("values", "values-pt")) for (f in listOf("strings.xml", "strings_native.xml")) {
            val s = res(d, f)
            assertTrue("$d/$f", !s.contains("Personal Files", ignoreCase = true) && !s.contains("Arquivos pessoais", ignoreCase = true))
        }
        assertTrue(res("values", "strings.xml").contains(">Local Files<") && res("values-pt", "strings.xml").contains(">Arquivos locais<"))
    }

    @Test fun `one name for a new chat`() {
        assertTrue(res("values", "strings.xml").contains("name=\"drawer_newChat\" formatted=\"false\">New chat<"))
        assertTrue(res("values-pt", "strings.xml").contains("name=\"drawer_newChat\" formatted=\"false\">Nova conversa<"))
    }

    @Test fun `the answer's symbol buttons have spoken labels`() {
        for (k in listOf("native.a11y.reasoning", "native.a11y.copyAnswer")) assertTrue(k, chat.contains("t(\"$k\")"))
        assertTrue(hooks.contains("t(\"native.a11y.pluginActions\")"))
    }

    @Test fun `both plugin sheets use one row, status chips behind a switch (off since Nov 12)`() {
        assertTrue(hooks.contains("private fun SheetRow(") && Regex("SheetRow\\(").findAll(hooks).count() == 4) // Golden change (Q-B1): the header sheet's Off rows use it too
        assertTrue(hooks.contains("if (PluginsUi.SHOW_STATUS && (status == LabStatus.ALPHA || status == LabStatus.BETA)) StatusChip(status)"))
    }

    @Test fun `the plugin switches use the Settings switch row`() {
        assertTrue(!hooks.contains("material3.Switch(LabPrefs.labsOn") && hooks.contains("SwitchRow(team.sopa.boar.ui.t(\"native.labs.settingsTitle\")"))
    }

    @Test fun `the chat list's time follows Text size`() {
        val drawer = java.io.File("../app/src/main/java/team/sopa/boar/ui/drawer/BoarDrawer.kt").readText()
        assertTrue(drawer.contains("Text(relativeTime(s.updatedAt), style = Boar.type.caption"))
    }
}
