package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam's decisions, Nov 11: the chat header, the menu and the answer footer. Read from the source. */
class HeaderMenuTest {
    private val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
    private val main = java.io.File("../app/src/main/java/team/sopa/boar/MainActivity.kt").readText()
    private val drawer = java.io.File("../app/src/main/java/team/sopa/boar/ui/drawer/BoarDrawer.kt").readText()
    private val header = chat.substringAfter("private fun Header(").substringBefore("\n}\n")

    @Test fun `the header is menu, BOAR, plugins and New chat, Deep Research left it`() {
        assertTrue(!chat.contains("DeepResearchButton"))
        assertTrue(header.contains("LabHooks.HeaderBadges(onOpenRoute)") && header.contains("onNewChat"))
    }

    @Test fun `the connection is the pill beside BOAR, then a quiet dot`() {
        assertTrue(!chat.contains("StatusPill"))
        assertTrue(header.contains("ConnectionDot(state.online)"))
        assertTrue(chat.contains("ConnectionPill.shouldShow(online)"))
    }

    @Test fun `Settings is a gear by the BOAR name, not a row, About is also the last row of Settings`() {
        assertTrue(!main.contains("DrawerItem(\"settings\""))
        assertTrue(main.contains("onSettings = { go { screen = \"settings\" } }") && drawer.contains("onClick = onSettings"))
        val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
        assertTrue(settings.contains("clickable { onOpen(\"about\") }") && settings.contains("Text(t(\"chatScreen.drawerItems.about\")"))
        assertTrue(main.contains("val back = if (screen == \"about:settings\") \"settings\" else \"chat\""))
    }

    @Test fun `a down arrow when the chat isn't at its end, and the rating is hidden`() {
        assertTrue(chat.contains("if (list.canScrollForward) Box("))
        assertTrue(chat.contains("private const val SHOW_RATING = false"))
    }

    @Test fun `strings in English and Portuguese`() {
        for (d in listOf("values", "values-pt")) {
            val a = java.io.File("../app/src/main/res/$d/strings.xml").readText()
            val b = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
            assertTrue(d, b.contains("name=\"native_chat_toBottom\""))
        }
    }
}
