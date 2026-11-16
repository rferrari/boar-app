package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 1 (adam, Nov 11): the name stays Boar in Portuguese; Settings comes back where it was. */
class Wave1Test {
    @Test fun `no Portuguese string translates the name Boar`() {
        // The one allowed: Designer's example drawing, the animal ("um javali"), not the name.
        val allowed = setOf("native_labs_image_placeholder")
        val pt = java.io.File("../app/src/").walkTopDown().filter { it.isFile && it.extension == "xml" && it.parentFile.name == "values-pt" }
        for (f in pt) for (m in Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(f.readText())) {
            if (m.groupValues[1] in allowed) continue
            assertTrue("${f.name} ${m.groupValues[1]}: ${m.groupValues[2]}", !Regex("\\bjavali", RegexOption.IGNORE_CASE).containsMatchIn(m.groupValues[2]))
        }
        assertTrue(!Regex("\\bjavali", RegexOption.IGNORE_CASE).containsMatchIn(java.io.File("../app/src/main/assets/boar-guide/pt.md").readText()))
        val native = java.io.File("../app/src/main/res/values-pt/strings_native.xml").readText()
        assertTrue(native.contains(">Boar Cósmico<"))
    }

    @Test fun `back from a page Settings opened, the scroll and the open sections are kept`() {
        val s = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
        assertTrue(s.contains("val onOpen: (String) -> Unit = { route -> SettingsMemory.keep = true; onOpen(route) }"))
        assertTrue(s.contains("val onPin: (String) -> Unit = { flow -> SettingsMemory.keep = true; onPin(flow) }"))
        assertTrue(s.contains("val scroll = rememberScrollState(SettingsMemory.scroll)") && s.contains("onDispose { SettingsMemory.scroll = scroll.value }"))
        assertTrue(s.contains("mutableStateOf(SettingsMemory.open[title] ?: startOpen)"))
        // From the menu it starts at the top, folded.
        assertTrue(s.contains("if (!keep) { scroll = 0; open.clear() }"))
    }
}
