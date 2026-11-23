package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, Nov 14 (t9 0025): the app's one toast is a light card with dark text and a shadow; an action is an accent pill. */
class ToastInverseTest {
    private val host = java.io.File("../app/src/main/java/team/sopa/boar/ui/Toasts.kt").readText().substringAfter("fun ToastHost(")

    @Test fun `light card, dark text, a shadow and no border`() {
        assertTrue(host.contains(".shadow(8.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(c.textHeading)"))
        assertTrue(host.contains("Text(t.text, style = Boar.type.body, color = c.textInverse, fontWeight = FontWeight.SemiBold"))
        assertTrue(!host.contains(".border(") && !host.contains("bgCardElevated"))
    }

    @Test fun `the action is an accent pill, the same tap, and the timings are unchanged`() {
        assertTrue(host.contains("Modifier.clip(RoundedCornerShape(50)).background(c.emerald500)") && host.contains(".padding(vertical = 6.dp, horizontal = 12.dp)"))
        assertTrue(host.contains("t.onAction?.invoke(); Toasts.hide(t)"))
        val all = java.io.File("../app/src/main/java/team/sopa/boar/ui/Toasts.kt").readText()
        assertTrue(all.contains("millis: Long = if (action != null) 6_000 else 1_800") && all.contains("top = 84.dp"))
    }
}
