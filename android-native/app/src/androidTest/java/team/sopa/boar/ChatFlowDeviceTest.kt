package team.sopa.boar

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import team.sopa.boar.app.BoarServices
import team.sopa.boar.ui.I18n

/**
 * A question typed and sent on the real chat screen, and its answer read back from the screen while it streams and
 * after it ends. Needs an installed model and no PIN (the devtest install after a setup run).
 */
@RunWith(AndroidJUnit4::class)
class ChatFlowDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    /** Screens launched by a test, closed after it whatever happened. */
    private val opened = mutableListOf<ActivityScenario<*>>()

    @org.junit.After fun closeScreens() { opened.forEach { runCatching { it.close() } } }

    /** Every text on screen, joined (the answer bubble's text among them). */
    private fun screenText(): String = compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().joinToString("\n") { root ->
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode): List<String> =
            (n.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + n.children.flatMap(::walk)
        walk(root).joinToString("\n")
    }

    @Test fun aSentQuestionShowsItsAnswerOnScreen() {
        val services = BoarServices.get(ctx)
        assumeTrue("needs a model and no PIN", services.activeModel() != null && !services.pinLock.isSet)
        val scenario = ActivityScenario.launch(MainActivity::class.java).also { opened += it }
        compose.waitUntil(60_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        val before = screenText()
        compose.onNode(hasSetTextAction()).performTextInput("What is a monsoon?")
        compose.onNode(hasText("➤")).performSemanticsAction(SemanticsActions.OnClick)
        // The answer appears and grows on screen (not only in the database).
        compose.waitUntil(180_000) { compose.onAllNodesWithText("monsoon", substring = true, ignoreCase = true).fetchSemanticsNodes().size >= 2 }
        compose.waitUntil(300_000) { runCatching { compose.onNode(hasText("➤")).assertExists() }.isSuccess && compose.onAllNodesWithText("■").fetchSemanticsNodes().isEmpty() }
        val after = screenText()
        android.util.Log.i("ChatFlowDeviceTest", "screen after answer:\n$after")
        assertTrue("no answer text appeared on screen", after.length > before.length + 80)
    }

    /** The same with a PIN: unlock on the lock screen first, then the answer must still show. The PIN is removed after. */
    @Test fun withAPinTheAnswerStillShowsOnScreen() {
        val services = BoarServices.get(ctx)
        assumeTrue("needs a model and no PIN yet", services.activeModel() != null && !services.pinLock.isSet)
        services.pinLock.enable("2580")
        try {
            services.chats.cipher = team.sopa.boar.engine.security.PlainCipher // as a fresh process: locked until the PIN
            val scenario = ActivityScenario.launch(MainActivity::class.java).also { opened += it }
            compose.waitUntil(60_000) { compose.onAllNodesWithText(I18n.t(ctx, "native.pin.enterTitle")).fetchSemanticsNodes().isNotEmpty() }
            "2580".forEach { d -> compose.onNode(hasText(d.toString())).performSemanticsAction(SemanticsActions.OnClick) }
            compose.onNode(hasText("✓")).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(30_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            val before = screenText()
            compose.onNode(hasSetTextAction()).performTextInput("What is a monsoon?")
            compose.onNode(hasText("➤")).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(180_000) { compose.onAllNodesWithText("monsoon", substring = true, ignoreCase = true).fetchSemanticsNodes().size >= 2 }
            compose.waitUntil(300_000) { runCatching { compose.onNode(hasText("➤")).assertExists() }.isSuccess && compose.onAllNodesWithText("■").fetchSemanticsNodes().isEmpty() }
            val after = screenText()
            android.util.Log.i("ChatFlowDeviceTest", "PIN screen after answer:\n$after")
            assertTrue("no answer text appeared on screen with a PIN", after.length > before.length + 80)
            } finally {
            runCatching { services.pinLock.disable("2580") }
        }
    }
}
