package team.sopa.boar.engine.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, 23 Nov: Boar Avatars merged into Display & theme, a few avatars and a last box that opens them all. */
class AvatarStripTest {
    private val known = listOf("a", "b", "c", "d", "e", "f")

    @Test fun `the plain BOAR, the one worn, then the latest, up to max`() {
        val unlocked = mapOf("a" to 1L, "b" to 5L, "c" to 3L, "d" to 9L, "e" to 7L)
        assertEquals(listOf(null, "d", "e", "b"), AvatarStrip.pick(unlocked, known, equipped = null, max = 4))
        assertEquals(listOf(null, "a", "d", "e"), AvatarStrip.pick(unlocked, known, equipped = "a", max = 4))
        // Nothing unlocked: only BOAR (the last box still opens the page).
        assertEquals(listOf(null), AvatarStrip.pick(emptyMap(), known, equipped = null))
        // An id without art, or a worn one no longer unlocked, isn't shown.
        assertEquals(listOf(null, "b"), AvatarStrip.pick(mapOf("zz" to 9L, "b" to 1L), known, equipped = "c"))
    }

    @Test fun `Settings has no Boar Avatars section, Display and theme has the picker with the way to the page`() {
        val settings = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
        assertTrue(!settings.contains("Section(t(\"native.vault.title\")") && !settings.contains("VaultEntry"))
        assertTrue(settings.contains("team.sopa.boar.ui.appearance.AvatarPicker(services.vault) { onOpen(\"vault\") }"))
        val vault = java.io.File("../app/src/main/java/team/sopa/boar/ui/appearance/VaultScreen.kt").readText()
        val picker = vault.substringAfter("fun AvatarPicker(").substringBefore("\n}\n")
        assertTrue(picker.contains("AvatarStrip.pick(state.unlocked, TruffleArt.byId.keys, icon)") && picker.contains("onClick = onOpenVault"))
        // adam, 23 Nov: "list it as Boar Avatar", and "All" without a count (some are locked).
        for ((d, label, all) in listOf(Triple("values", "Boar Avatar", "All ›"), Triple("values-pt", "Avatar do Boar", "Todos ›"))) {
            val x = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
            assertTrue(d, x.contains("name=\"native_vault_boar\" formatted=\"false\">$label<") && x.contains("name=\"native_vault_allTile\" formatted=\"false\">$all<"))
        }
        assertTrue(picker.contains("t(\"native.vault.allTile\")"))
    }

    @Test fun `ten avatars before All, in a row that scrolls sideways (adam, 27 Nov)`() {
        assertEquals(10, AvatarStrip.MAX)
        val many = (1..15).associate { "x$it" to it.toLong() }
        val strip = AvatarStrip.pick(many, many.keys, equipped = null)
        assertEquals(10, strip.size)
        assertEquals(listOf(null, "x15", "x14"), strip.take(3))
        val vault = java.io.File("../app/src/main/java/team/sopa/boar/ui/appearance/VaultScreen.kt").readText()
        val picker = vault.substringAfter("fun AvatarPicker(").substringBefore("\n}\n")
        assertTrue(picker.contains("Row(Modifier.horizontalScroll(rememberScrollState())"))
    }
}
