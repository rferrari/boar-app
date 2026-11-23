package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * adam, 23 Nov: "remove the 'See the tour' from settings - start over" and "review the onboarding plugins descriptions.
 * it mention experimental, alpha, beta."
 */
class OnboardingPluginsTest {
    private fun v(d: String, k: String) = Regex("name=\"$k\" formatted=\"false\">([^<]*)<").find(java.io.File("../app/src/main/res/$d/strings_native.xml").readText())!!.groupValues[1]

    @Test fun `no See the tour in Start over`() {
        val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
        assertTrue(!settings.contains("native.tour.seeTour") && !settings.contains("TourScreen("))
        assertTrue(!java.io.File("../app/src/main/assets/boar-guide/en.md").readText().contains("See the tour"))
    }

    @Test fun `the setup page and the tour's plugin card say nothing of experiments, alpha, beta, or plugins a release leaves out`() {
        val words = Regex("\\b(?:experiments?|experimentos?|experimental|alpha|beta)\\b|Big Boar|Wisp", RegexOption.IGNORE_CASE) // not "Experimente" (PT "try")
        for (d in listOf("values", "values-pt")) for (k in listOf("native_setupGuide_labs_sub", "native_setupGuide_labs_note", "native_tour_labs_body"))
            assertTrue("$d $k: ${v(d, k)}", !words.containsMatchIn(v(d, k).replace("Settings → Experimental", "").replace("Configurações → Experimental", "")))
        // One plain list on the setup page: no Beta / Alpha groups, no status tag.
        val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText().substringAfter("fun SetupList()").substringBefore("\n    }\n")
        assertTrue(!hooks.contains("setupGuide.labs.beta") && !hooks.contains("setupGuide.labs.alpha") && !hooks.contains("native.labs.status."))
    }
}
