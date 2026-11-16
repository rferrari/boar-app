package team.sopa.boar.engine.chat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** adam, Nov 13: "click on avatar+name on chat response and go to the plugin page". */
class PluginSpeakerOpensPageTest {
    private val chat = File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()

    @Test fun `a plugin answer's picture and name are one button that opens that plugin's page`() {
        assertTrue(File("../app/src/main/java/team/sopa/boar/plugins/AnswerTools.kt").readText().contains("val canBypass: Boolean, val id: String? = null)"))
        assertTrue(File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText().contains("canBypass = it.id != \"cohazard\", id = it.id)"))
        val bubble = chat.substringAfter("private fun Bubble(").substringBefore("if (user) {")
        // Not while it is still typing, and only when Labs is on (the route exists).
        assertTrue(bubble.contains("val speakerPage = speaker?.id?.takeIf { !streaming && team.sopa.boar.labs.LabPrefs.labsOn }"))
        assertTrue(bubble.contains("onClickLabel = t(\"native.chat.openPlugin\", \"name\" to pluginName.orEmpty())) { onOpenPlugin(speakerPage) }"))
        // In every profile, the plugin's own page (not the list).
        assertTrue(chat.contains("onOpenPlugin = { id -> onOpenRoute(\"lab:\$id\") }"))
        for (d in listOf("values", "values-pt")) assertTrue(d, File("../app/src/main/res/$d/strings_native.xml").readText().contains("name=\"native_chat_openPlugin\""))
    }
}
