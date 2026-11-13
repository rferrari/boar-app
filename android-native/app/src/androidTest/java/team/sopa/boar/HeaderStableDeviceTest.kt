package team.sopa.boar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import team.sopa.boar.chat.ChatState
import team.sopa.boar.engine.chat.ConnectionPill
import team.sopa.boar.ui.I18n
import team.sopa.boar.ui.PageHeader
import team.sopa.boar.ui.chat.ChatScreen
import team.sopa.boar.ui.theme.BoarTheme

/**
 * The headers on a phone (t9 1950, adam on all17d): the connection pill beside BOAR moves nothing in the chat header,
 * before, during and after it shows; a page header's subtitle is laid out inside the 62 dp bar, up to the 1.3 font scale.
 */
@RunWith(AndroidJUnit4::class)
class HeaderStableDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun t(key: String) = I18n.t(ctx, key)

    @Test fun thePillMovesNothingInTheChatHeader() {
        ConnectionPill.lastShown = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoarTheme { ChatScreen(ChatState(online = true), onSend = {}, onStop = {}, onNewChat = {}, onMenu = {}, onSetTone = {}, onSetDeepResearch = {}, onRate = { _, _ -> }) }
        }
        fun bounds() = listOf(
            // The header's BOAR: the empty chat shows a big BOAR too, lower down.
            compose.onAllNodesWithText("BOAR", useUnmergedTree = true).fetchSemanticsNodes().minBy { it.boundsInRoot.top }.boundsInRoot,
            compose.onNodeWithContentDescription(t("chatHeader.newChatSession"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithContentDescription(t("native.a11y.menu"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot,
        )
        compose.mainClock.advanceTimeByFrame()
        val before = bounds()
        compose.mainClock.advanceTimeBy(400)   // the pill fully shown
        val during = bounds()
        compose.mainClock.advanceTimeBy(ConnectionPill.SHOW_MS + 600)   // back to the dot
        val after = bounds()
        assertEquals("during", before, during)
        assertEquals("after", before, after)
    }

    @Test fun theFirstTapShowsThePillTheSecondOpensTheDialogAndNothingMoves() {
        ConnectionPill.lastShown = true   // no automatic pill: start from the dot
        compose.setContent {
            BoarTheme { ChatScreen(ChatState(online = true), onSend = {}, onStop = {}, onNewChat = {}, onMenu = {}, onSetTone = {}, onSetDeepResearch = {}, onRate = { _, _ -> }) }
        }
        val dot = compose.onNode(androidx.compose.ui.test.hasContentDescription(t("chatHeader.online") + ". ", substring = true))
        fun newChat() = compose.onNodeWithContentDescription(t("chatHeader.newChatSession"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val before = newChat()
        dot.performClick()
        compose.waitForIdle()
        compose.onNodeWithText(t("chatHeader.onlineTitle")).assertDoesNotExist()   // first tap: only the pill
        assertEquals(before, newChat())
        dot.performClick()
        compose.waitForIdle()
        compose.onNodeWithText(t("chatHeader.onlineTitle")).assertExists()         // second tap, on the pill: the dialog
    }

    @Test fun theSubtitleIsInsideTheBarAtTheLargestFontScale() {
        compose.setContent {
            BoarTheme {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, 1.3f)) {
                    PageHeader("Model Catalog", onBack = {}, subtitle = "Models on this phone and to download")
                }
            }
        }
        val density = ctx.resources.displayMetrics.density
        val sub = compose.onNodeWithText("Models on this phone and to download", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Model Catalog", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("title $title", title.top >= 0f)
        assertTrue("subtitle bottom ${sub.bottom / density} dp", sub.bottom / density <= 62f)
        assertTrue("subtitle under the title", sub.top >= title.bottom - 1f)
    }
}
