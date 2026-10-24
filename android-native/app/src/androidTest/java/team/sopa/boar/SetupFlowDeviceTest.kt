package team.sopa.boar

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import team.sopa.boar.app.BoarServices
import team.sopa.boar.ui.I18n

/**
 * The whole first run, on the throwaway devtest install (its files are emptied first): hardware, the 1.5B model, the
 * Essential package, then the guide's pages while the real download and indexing run (a style, a file added before
 * the search model is in, a first question, a PIN set on its page), the summary, and the chat with the question
 * waiting and no second PIN offer. Needs Wi-Fi and about 1 GB; it only runs on the devtest install.
 */
@RunWith(AndroidJUnit4::class)
class SetupFlowDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    /** Screens launched by a test, closed after it whatever happened. */
    private val opened = mutableListOf<ActivityScenario<*>>()

    @org.junit.After fun closeScreens() { opened.forEach { runCatching { it.close() } } }
    private fun t(key: String) = I18n.t(ctx, key)

    private fun click(text: String, substring: Boolean = false) {
        val node = compose.onNode(hasText(text, substring = substring) and hasClickAction())
        runCatching { node.performScrollTo() } // screens that don't scroll (the PIN keypad) have nothing to scroll
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Checks every 2 s: waitUntil's 10 ms polling keeps the main thread busy for the whole half-hour setup. */
    private fun waitSlowly(timeoutMs: Long, condition: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + timeoutMs
        while (!condition()) {
            check(android.os.SystemClock.uptimeMillis() < end) { "condition not met in ${timeoutMs / 1000} s" }
            Thread.sleep(2_000)
        }
    }

    private fun shows(text: String, substring: Boolean = false) = runCatching { compose.onNodeWithText(text, substring = substring).assertExists() }.isSuccess // false until a screen exists

    /** The guide's page title is on screen. */
    private fun onPage(key: String) = compose.waitUntil(10_000) { shows(t("native.setupGuide.$key.title")) }

    @Test fun firstRunWalksTheGuideWhileItDownloads() {
        // A first run: this throwaway install's files are emptied before the app starts (nothing else in this process
        // has opened them yet), so setup begins from step 1.
        assumeTrue("only on the throwaway devtest install", ctx.packageName.endsWith(".devtest"))
        ctx.filesDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
        val scenario = ActivityScenario.launch(MainActivity::class.java).also { opened += it }
        val services = BoarServices.get(ctx)
        // The engine and the screen take a moment on a first start.
        compose.waitUntil(60_000) { shows(t("flows.onboarding.start"), substring = true) }

        click(t("flows.onboarding.start"), substring = true)
        val compact = services.catalog.answerModels.first { it.id == "qwen2.5-1.5b-instruct-q4km" }
        click(compact.shortName, substring = true) // the name the model step shows
        click(t("flows.onboarding.continue"), substring = true)
        click(t("flows.onboarding.package.essential.name"))
        click(t("flows.onboarding.install").substringBefore("{{"), substring = true) // "Download 1.0 GB  →"

        onPage("look")
        click(t("native.settings.large"))
        assertEquals("large", services.settings.fontScale)
        click(t("native.setupGuide.next"), substring = true)

        onPage("answers")
        click(t("personalities.succinct.label"), substring = true)
        click(t("native.setupGuide.next"), substring = true)

        // A file picked now, before the search model is in: copied at once, imported when it can be. The system
        // picker can't be driven from here, so the file goes to the same call the picker's result does.
        onPage("files")
        val note = java.io.File(ctx.cacheDir, "boar-notes.txt").apply { writeText("The boar festival is on the third Saturday of May, in the village square.") }
        scenario.onActivity { androidx.lifecycle.ViewModelProvider(it)[team.sopa.boar.setup.SetupViewModel::class.java].addFiles(listOf(android.net.Uri.fromFile(note))) }
        compose.waitUntil(10_000) { shows("boar-notes.txt") }
        click(t("native.setupGuide.next"), substring = true)

        onPage("ask")
        val question = t("chat.suggestions.q4")
        click(question)
        assertEquals(question, services.settings.pendingFirstQuestion)
        click(t("native.setupGuide.next"), substring = true)

        // The PIN page: set it here, while the download runs; done goes on to the next page.
        onPage("pin")
        click(t("native.pin.offerSet"), substring = true)
        repeat(2) {
            "2580".forEach { d -> click(d.toString()) }
            click("✓")
        }
        compose.waitUntil(20_000) { services.pinLock.isSet }
        onPage("privacy")
        click(t("native.setupGuide.next"), substring = true)
        if (shows(t("native.setupGuide.labs.title"))) click(t("native.setupGuide.next"), substring = true)

        // The last page waits for the downloads (verified) and the index, then shows the summary.
        onPage("ready")
        waitSlowly(30 * 60_000L) { shows(t("flows.onboarding.doneBody")) }
        click(t("flows.onboarding.open"))

        // The chat, with the question in the input and no second PIN offer; the file is in Personal Files.
        compose.waitUntil(20_000) { shows(question) }
        assertFalse(shows(t("native.pin.offerTitle")))
        assertEquals(null, services.settings.pendingFirstQuestion)
        assertEquals("succinct", services.settings.personalityId)
        assertTrue(services.activeModel()?.id == compact.id)
        waitSlowly(60_000) { services.knowledgeBase.collections().any { it.name == t("native.setupGuide.files.collection") } }
    }
}
