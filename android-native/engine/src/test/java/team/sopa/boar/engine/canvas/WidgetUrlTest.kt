package team.sopa.boar.engine.canvas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The widget's lock: its own inline page loads, nothing else does. */
class WidgetUrlTest {
    @Test fun `the widget's own page loads`() {
        assertTrue(WidgetHtml.allowedUrl("data:text/html;charset=utf-8;base64,PGgxPmhpPC9oMT4="))
        assertTrue(WidgetHtml.allowedUrl("DATA:text/html,<b>x</b>"))
        assertTrue(WidgetHtml.allowedUrl("about:blank"))
    }

    @Test fun `everything else is blocked`() {
        for (u in listOf("https://example.com/", "http://10.0.0.1/x.js", "file:///sdcard/Download/a.html", "content://media/external/1",
                "intent://scan/#Intent;scheme=zxing;end", "javascript:alert(1)", "about:config", "blob:https://x/1", "ws://x", "", null))
            assertFalse(u ?: "null", WidgetHtml.allowedUrl(u))
    }
}
