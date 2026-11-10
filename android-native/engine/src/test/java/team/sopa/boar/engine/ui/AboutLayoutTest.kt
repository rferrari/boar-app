package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** About (adam, Nov 10): the hold-to-copy line is the footer; the version block lists the plugins and copies them. */
class AboutLayoutTest {
    private val about = java.io.File("../app/src/main/java/team/sopa/boar/ui/about/AboutScreen.kt").readText()
    private val debug = java.io.File("../app/src/main/java/team/sopa/boar/ui/about/AboutDebug.kt").readText()

    @Test fun `press and hold to copy is the last line, after the version`() {
        val screen = about.substringAfter("fun AboutScreen(").substringBefore("private fun Profile(")
        assertTrue(screen.indexOf("VersionInfo(info, debug)") in 0 until screen.indexOf("native.about.holdToCopy"))
        assertTrue(screen.substringAfter("native.about.holdToCopy").contains("Text(").not())
    }

    @Test fun `the version shows the enabled plugins and press and hold copies them with the device`() {
        assertTrue(about.contains("LabHooks.enabledLabs()"))
        assertTrue(about.contains("native.about.plugins") && about.contains("native.about.noPlugins"))
        assertTrue(about.contains(".setPrimaryClip(android.content.ClipData.newPlainText(\"BOAR\", report))"))
        assertTrue(debug.contains("Plugins (") && debug.contains("Device: "))
    }
}
