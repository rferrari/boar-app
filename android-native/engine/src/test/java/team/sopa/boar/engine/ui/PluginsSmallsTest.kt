package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam's decisions, Nov 11, for the plugins and Designer. Read from the source. */
class PluginsSmallsTest {
    private val image = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt").readText()
    private val list = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabsScreen.kt").readText()
    private val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()

    @Test fun `Designer's sheet scrolls styles and sizes sideways`() {
        assertTrue(image.contains("style, sideways = true) { style = it }") && image.contains("size, sideways = true) { size = it }"))
    }

    @Test fun `under a drawing only i and x, the rest inside i`() {
        val done = image.substringAfter("is Illustrator.Status.Done ->").substringBefore("if (details) Column(")
        assertTrue(done.contains("Text(\"ⓘ\"") && done.contains("Text(\"✕\""))
        assertTrue(!done.contains("Text(\"🎨 \" + st.subject"))
    }

    @Test fun `the Plugins page has a search, and using a plugin is remembered`() {
        assertTrue(list.contains("RecentPlugins.matches(query"))
        assertTrue(hooks.contains("LaunchedEffect(lab.id) { LabPrefs.used(lab.id) }") && hooks.contains("LabPrefs.used(labId); act.onClick()"))
    }

    @Test fun `strings in English and Portuguese`() {
        for (d in listOf("values", "values-pt")) {
            val s = java.io.File("../app/src/labs/res/$d/strings_labs.xml").readText()
            assertTrue(d, s.contains("name=\"native_labs_search\"") && s.contains("name=\"native_labs_searchNone\""))
        }
    }
}
