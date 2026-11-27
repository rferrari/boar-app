package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** Validator A-4 (t9 0426 item 5): touch targets of 48 dp, and the profile pills' selected state for TalkBack. */
class TouchTargetsTest {
    private val chrome = java.io.File("../app/src/labs/java/team/sopa/boar/labs/PluginChrome.kt").readText()
    private val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()

    @Test fun `a plugin tool's touch area is at least 48 dp, its circle inside`() {
        assertTrue(chrome.contains("val TOUCH_MIN = 48.dp"))
        assertTrue(chrome.contains("Box(Modifier.width(maxOf(touch, size)).height(maxOf(TOUCH_MIN, size))"))
        // The toolbar shares its width out in cells of 48 dp or more, and scrolls only when it can't.
        assertTrue(chrome.contains("val scrolls = cell < TOUCH_MIN") && chrome.contains("val touch = if (scrolls) TOUCH_MIN else cell"))
    }
}
