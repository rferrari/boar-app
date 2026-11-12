package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** t9's 0510, 0540, 0550, 0555 (adam, Nov 12): plugin pages. Read from the source. */
class PluginPagesTest {
    private val labs = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabsScreen.kt").readText()
    private val scaffold = labs.substringAfter("fun LabScaffold(").substringBefore("\n}\n")

    @Test fun `one switch, in the list, and off the page shows everything under an alert`() {
        assertTrue(!scaffold.contains("material3.Switch("))
        assertTrue(scaffold.contains("if (!LabPrefs.enabled(lab.id)) OffAlert(") && scaffold.contains("        lab.Content()"))
        // An off plugin fills no slot: only enabled labs are active.
        val plugin = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabPlugin.kt").readText()
        assertTrue(plugin.contains("visible.mapNotNull(::opened).filter { LabPrefs.enabled(it.id) }"))
    }

    @Test fun `plugins in testing and the tile view are hidden behind switches`() {
        assertTrue(labs.contains("const val SHOW_UPCOMING = false") && labs.contains("const val SHOW_TILES = false"))
        assertTrue(labs.contains("if (PluginsUi.SHOW_UPCOMING && classified.isNotEmpty()") && labs.contains("if (PluginsUi.SHOW_TILES) Text(t(\"native.labs.showTiles\")"))
    }

    @Test fun `examples are Try rows that ask, edit or copy, on every plugin that has them`() {
        // On the page template (Nov 12) the Try rows are PluginPage's: each plugin hands it its examples.
        assertTrue(java.io.File("../app/src/labs/java/team/sopa/boar/labs/PluginPage.kt").readText().contains("for (q in tries) TryRow(q)"))
        for (id in listOf("cohazard", "calc", "polyglot", "distance", "facts"))
            assertTrue(id, java.io.File("../app/src/labs/plugins/$id").walkTopDown().first { it.name.endsWith("Lab.kt") }.readText().contains("(1..3).map { t(\"native.labs.$id.example\$it\") }"))
        for (id in listOf("compass", "lunar", "pulse"))
            assertTrue(id, java.io.File("../app/src/labs/plugins/$id").walkTopDown().first { it.name.endsWith("Lab.kt") }.readText().contains("(1..2).map { t(\"native.labs.$id.example\$it\") }"))
        val row = java.io.File("../app/src/labs/java/team/sopa/boar/labs/TryRow.kt").readText()
        assertTrue(row.contains("AskFromPlugin.ask(question, send = true)") && row.contains("AskFromPlugin.ask(question, send = false)") && row.contains("clipboard.setText(AnnotatedString(question))"))
        val main = java.io.File("../app/src/main/java/team/sopa/boar/MainActivity.kt").readText()
        assertTrue(main.contains("AskFromPlugin.handler = { q, send -> vm.newChat(); if (send) vm.send(q) else vm.prefillWith(q); screen = \"chat\" }"))
    }

    @Test fun `Guard's page follows the template, in English and Portuguese`() {
        val guard = java.io.File("../app/src/labs/plugins/guard/java/team/sopa/boar/labs/guard/GuardLab.kt").readText()
        assertTrue(guard.contains("team.sopa.boar.labs.PluginPage(t(\"native.labs.guard.summary\")"))
        for (d in listOf("values", "values-pt")) {
            val s = java.io.File("../app/src/labs/plugins/guard/res/$d").listFiles()!!.joinToString("") { it.readText() }
            for (k in listOf("summary", "before", "after")) assertTrue("$d $k", s.contains("name=\"native_labs_guard_$k\""))
        }
    }
}
