package team.sopa.boar.engine.labs

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, 23 Nov: the Plugins list's rows look the same on or off (the switch says which); Polyglot is deprecated. */
class PluginsListPolishTest {
    private val labs = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabsScreen.kt").readText()
    private val registry = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabPlugin.kt").readText()

    @Test fun `no on-state highlight on a list row`() {
        assertTrue(labs.contains(".background(c.bgCard)\n        .border(1.dp, c.borderSubtle, RoundedCornerShape(10.dp)).clickable { onOpen(\"lab:\${lab.id}\") }"))
        assertTrue(!labs.contains("background(if (on) c.emeraldBgSubtle else c.bgCard)"))
    }

    @Test fun `Polyglot is out of the list and off, its code kept`() {
        assertTrue("polyglot" in LabDefaults.DEPRECATED && LabDefaults.DEPRECATED.none { it in LabDefaults.ON })
        // visible feeds the list, the pages (byId) and the active slots: a deprecated plugin is in none of them.
        assertTrue(registry.contains("ALL.filter { it.id !in hidden && it.id !in team.sopa.boar.engine.labs.LabDefaults.DEPRECATED }"))
        assertTrue(registry.contains("fun byId(id: String): LabPlugin? = visible.") && registry.contains("val active: List<LabPlugin> get() = if (!LabPrefs.labsOn) emptyList() else visible."))
        assertTrue(java.io.File("../app/src/labs/plugins/polyglot/java/team/sopa/boar/labs/polyglot/PolyglotLab.kt").isFile)
        // BOAR answers about itself from the guide: no Polyglot there.
        for (g in listOf("en", "pt")) assertTrue(g, !java.io.File("../app/src/main/assets/boar-guide/$g.md").readText().contains("Polyglot"))
    }
}
