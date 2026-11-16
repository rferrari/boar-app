package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * adam, Nov 13: "on vitals plugins, move the example to how it works. leave the try it as is", then "all example on plugin
 * page, move to how it works when it fit". The example sits inside the fold above its note; Try is unchanged; a page
 * without a fold keeps the example in its own card. And no "this plugin is off" banner on the pages.
 */
class PluginExampleInHowTest {
    private val page = File("../app/src/labs/java/team/sopa/boar/labs/PluginPage.kt").readText()

    @Test fun `the example goes inside How it works when the page has one`() {
        assertTrue(page.contains("if (hasExample && howItWorks == null) Column("))
        assertTrue(page.contains("Fold(t(\"native.labs.page.how\"), it, if (hasExample) exampleBody else null)"))
        val fold = page.substringAfter("private fun Fold(").substringBefore("\n}\n")
        assertTrue(fold.indexOf("top?.invoke()") in 0 until fold.indexOf("Text(body,"))
        // Try is drawn before the fold, as it was.
        assertTrue(page.indexOf("Label(t(\"native.labs.page.try\"))") in 0 until page.indexOf("howItWorks?.let"))
    }

    @Test fun `every plugin page with an example also has a How it works text, so every example moves`() {
        val calls = File("../app/src/labs/plugins").walk().filter { it.extension == "kt" }
            .flatMap { f -> Regex("labs\\.(AnswerPage|PluginPage)\\(([^\\n]*\\n){0,3}").findAll(f.readText()).map { f.name to it.value } }.toList()
        assertTrue(calls.size >= 10)
        for ((name, call) in calls) assertTrue(name, call.contains(".how\")"))
    }

    @Test fun `no off banner on a plugin page`() {
        val screen = File("../app/src/labs/java/team/sopa/boar/labs/LabsScreen.kt").readText()
        assertTrue(!screen.contains("OffAlert") && !screen.contains("native.labs.offInList"))
    }
}
