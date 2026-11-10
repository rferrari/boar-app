package team.sopa.boar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import team.sopa.boar.chat.ChatState
import team.sopa.boar.chat.Source
import team.sopa.boar.chat.UiMessage
import team.sopa.boar.ui.I18n
import team.sopa.boar.ui.chat.ChatScreen
import team.sopa.boar.ui.theme.BoarTheme

/**
 * Sources layout B+ on the phone (t9, Nov 10): the block above the answer stays when the answer starts, and never takes
 * more than about 35% of the screen however many sources there are. Draws ChatScreen from a state, no model.
 */
@RunWith(AndroidJUnit4::class)
class SourcesAboveDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun t(key: String, vararg args: Pair<String, Any?>) = I18n.t(ctx, key, *args)

    private fun source(i: Int, long: Boolean = false) = Source("Article $i", "corpus", 0.8, docId = "doc$i",
        body = "Canberra was chosen in 1908 as a compromise between Sydney and Melbourne. " + if (long) "More text here. ".repeat(40) else "")

    private fun chat(sources: List<Source>, text: String, answering: Boolean) = ChatState(
        sessionId = "s",
        messages = listOf(UiMessage("q", "user", "Why was Canberra built?"), UiMessage("a", "assistant", text, sources)),
        answeringId = if (answering) "a" else null,
        sourcesAbove = true,
    )

    private fun draw(state: () -> ChatState) = compose.setContent {
        BoarTheme { ChatScreen(state(), onSend = {}, onStop = {}, onNewChat = {}, onMenu = {}, onSetTone = {}, onSetDeepResearch = {}, onRate = { _, _ -> }) }
    }

    @Test fun theBlockStaysWhenTheAnswerStarts() {
        var state by mutableStateOf(chat(listOf(source(1), source(2)), "", answering = true))
        draw { state }
        val title = t("native.sources.title", "count" to 2)
        compose.onNodeWithText(title).assertExists()
        // The answer starts streaming, then finishes: the block is still there each time.
        state = chat(listOf(source(1), source(2)), "Canberra was a compromise [1].", answering = true)
        compose.waitForIdle()
        compose.onNodeWithText(title).assertExists()
        state = chat(listOf(source(1), source(2)), "Canberra was a compromise between Sydney and Melbourne [1].", answering = false)
        compose.waitForIdle()
        compose.onNodeWithText(title).assertExists()
        // And the list under the answer isn't drawn with B+.
        compose.onNodeWithText(t("sourceFootnotes.title")).assertDoesNotExist()
    }

    @Test fun theBlockIsCappedAtAboutAThirdOfTheScreen() {
        val many = (1..12).map { source(it, long = true) }
        draw { chat(many, "An answer [1].", answering = false) }
        compose.waitForIdle()
        val screen = ctx.resources.configuration.screenHeightDp
        // The block's own scrolling column (the innermost scrollable node holding the rows), not the chat's list.
        val scrollers = compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText("Article 1")), useUnmergedTree = true).fetchSemanticsNodes()
        val inner = scrollers.minBy { it.boundsInRoot.height }
        val heightDp = inner.boundsInRoot.height / ctx.resources.displayMetrics.density
        assertTrue("block is $heightDp dp, screen $screen dp", heightDp <= screen * 0.35f + 1)
        assertTrue("the block scrolls inside: twelve long rows don't fit", heightDp >= screen * 0.2f)
    }

    @Test fun theChevronFoldsItAndItStaysFolded() {
        draw { chat(listOf(source(1)), "An answer [1].", answering = false) }
        compose.onNodeWithText("Article 1", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(t("native.sources.title", "count" to 1)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Article 1", useUnmergedTree = true).assertDoesNotExist()
    }
}
