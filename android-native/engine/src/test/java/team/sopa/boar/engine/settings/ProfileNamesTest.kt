package team.sopa.boar.engine.settings

import org.junit.Assert.assertTrue
import org.junit.Test

/** adam, Nov 15 (t9 2345, 2350, 2355): the Simple profile is called Starter (Iniciante); the id SIMPLE stays. */
class ProfileNamesTest {
    private fun str(d: String) = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()

    @Test fun `Starter and Iniciante, with adam's short lines`() {
        val en = str("values"); val pt = str("values-pt")
        for (line in listOf(">Starter</string>", ">Starter: set up the essentials.</string>", ">Explorer: discover more features.</string>",
            ">Pro: advanced settings and tweaks.</string>", ">Starter, plus how BOAR answers")) assertTrue(line, en.contains(line))
        for (line in listOf(">Iniciante</string>", ">Iniciante: configure o essencial.</string>", ">Explorador: descubra mais recursos.</string>",
            ">Pro: ajustes avançados e afine tudo.</string>", ">O Iniciante, mais como o BOAR responde")) assertTrue(line, pt.contains(line))
        // No "Simple"/"Simples" left as the profile's name, in the strings or the guides.
        assertTrue(!Regex("profile_\\w+\" formatted=\"false\">(?:O )?Simples?\\b").containsMatchIn(en + pt))
        for (g in listOf("en", "pt")) assertTrue(g, !java.io.File("../app/src/main/assets/boar-guide/$g.md").readText().contains(Regex("Settings → Profile.*Simple:|Perfil escolhe.*Simples:")))
        // The id doesn't move: stored settings stay.
        assertTrue(UserProfile.entries.any { it.name == "SIMPLE" })
    }
}
