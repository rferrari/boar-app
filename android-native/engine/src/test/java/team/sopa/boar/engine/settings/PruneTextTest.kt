package team.sopa.boar.engine.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validator Q-B2 (t9 0115): both delete dialogs say what goes with the chats' answers, in EN and PT. */
class PruneTextTest {
    private fun str(d: String) = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
    private fun value(x: String, k: String) = Regex("name=\"$k\" formatted=\"false\">([^<]*)<").find(x)!!.groupValues[1]

    @Test fun `with Boar Labs on, the dialog adds the plugins' line after the chats`() {
        assertEquals("Keeping 5 chats deletes “A”.\n\nTheir answers…", PruneText.of("Keeping 5 chats deletes “A”.", "Their answers…", pluginsOn = true))
        assertEquals("Keeping 5 chats deletes “A”.", PruneText.of("Keeping 5 chats deletes “A”.", "Their answers…", pluginsOn = false))
    }

    @Test fun `the line names Press, cards and Designer pictures, in EN and PT`() {
        val en = value(str("values"), "native_chat_prune_plugins")
        val pt = value(str("values-pt"), "native_chat_prune_plugins")
        for (w in listOf("Press", "cards", "Designer pictures")) assertTrue(w, en.contains(w))
        for (w in listOf("Press", "cartões", "imagens do Designer")) assertTrue(w, pt.contains(w))
    }

    @Test fun `both dialogs use it, and Boar Labs says when it applies`() {
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText().substringAfter("private fun PruneNoteDialog(").substringBefore("\n}\n")
        val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText().substringAfter("lowering?.let {").substringBefore("ClearChats(services)")
        for (s in listOf(chat, settings)) assertTrue(s.contains("UserData.withPluginsLine(") && s.contains("t(\"native.chat.prune.plugins\")"))
        assertTrue(java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText().contains("UserData.pluginsKeepAnswers = { LabPrefs.labsOn }"))
    }
}
