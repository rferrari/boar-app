package team.sopa.boar.engine.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, 23 Nov: Boar Avatars merged into Display & theme, a few avatars and a last box that opens them all. */
class AvatarStripTest {
    private val known = listOf("a", "b", "c", "d", "e", "f")

    @Test fun `the plain BOAR, the one worn, then the latest, four in all`() {
        val unlocked = mapOf("a" to 1L, "b" to 5L, "c" to 3L, "d" to 9L, "e" to 7L)
        assertEquals(listOf(null, "d", "e", "b"), AvatarStrip.pick(unlocked, known, equipped = null))
        assertEquals(listOf(null, "a", "d", "e"), AvatarStrip.pick(unlocked, known, equipped = "a"))
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
        for (d in listOf("values", "values-pt")) assertTrue(d, java.io.File("../app/src/main/res/$d/strings_native.xml").readText().contains("name=\"native_vault_allTile\""))
    }
}
