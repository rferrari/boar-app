package team.sopa.boar.engine.rag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.1.0 (BOAR-81) step 7: BOAR answers about itself from its guide. No profiles any more, and "where are the advanced
 * settings?" must lead to Evaluation, from the Model Catalog, in English and Portuguese.
 */
class GuideSimpleSettingsTest {
    private val en = java.io.File("../app/src/main/assets/boar-guide/en.md").readText()
    private val pt = java.io.File("../app/src/main/assets/boar-guide/pt.md").readText()
    private fun section(md: String, title: String) = md.substringAfter("## $title\n").substringBefore("\n## ")

    @Test fun `no Profiles section and no profile, tweaks or Experimental anywhere`() {
        for (w in listOf("## Profiles", "profile", "Starter", "Explorer", "In Pro", "Advanced tweaks", "Experimental", "Settings → How BOAR answers"))
            assertFalse("en: $w", en.contains(w, ignoreCase = w == "profile"))
        for (w in listOf("## Perfis", "perfil", "Iniciante", "Explorador", "No Pro", "Ajustes avançados", "Experimental", "Configurações → Como o BOAR responde"))
            assertFalse("pt: $w", pt.contains(w, ignoreCase = w == "perfil"))
    }

    @Test fun `the advanced settings are in Evaluation, from the Model Catalog`() {
        val e = section(en, "Evaluation and device setup")
        assertTrue(e.contains("advanced settings") && e.contains("Model Catalog") && e.contains("Evaluation (for builders)") && e.contains("Device setup"))
        for (row in listOf("presets", "Max output tokens", "Adaptive routing", "Staged quotes", "Free memory when I leave BOAR", "Show technical details", "Debug log", "Reset to Auto"))
            assertTrue("en: $row", e.contains(row))
        val p = section(pt, "Avaliação e ajustes do aparelho")
        assertTrue(p.contains("configurações avançadas") && p.contains("Catálogo de Modelos") && p.contains("Avaliação (para desenvolvedores)") && p.contains("Ajustes do aparelho"))
        for (row in listOf("predefinições", "Máximo de tokens de saída", "Roteamento adaptativo", "Citações em etapas", "Liberar memória quando eu sair do BOAR", "Mostrar detalhes técnicos", "Log de depuração", "Voltar ao Automático"))
            assertTrue("pt: $row", p.contains(row))
    }

    @Test fun `Settings lists its sections as the app shows them`() {
        val s = section(en, "Settings")
        for (w in listOf("one page for everyone", "Display & theme", "Language and voice", "Chats", "PIN lock", "Plugins", "Start over", "About BOAR", "style button"))
            assertTrue("en: $w", s.contains(w))
        val p = section(pt, "Configurações")
        for (w in listOf("uma página para todos", "Exibição e tema", "Idioma e voz", "Conversas", "Bloqueio por PIN", "Plugins", "Recomeçar", "Sobre o BOAR", "botão de estilo"))
            assertTrue("pt: $w", p.contains(w))
    }

    @Test fun `docs USING has no Experimental path or How BOAR answers in Settings`() {
        val using = java.io.File("../../docs/USING.md").readText()
        assertFalse(using.contains("Settings → Experimental") || using.contains("Settings → How BOAR answers") || using.contains("Advanced tweaks"))
        assertTrue(using.contains("Model Catalog → Evaluation (for builders) → Device setup"))
    }

    @Test fun `asking where the advanced settings are searches the guide, EN and PT`() {
        // On the emulator, "Where are the advanced settings?" didn't count as a question about BOAR, so the guide wasn't
        // searched and the model guessed "the app's settings menu".
        for (q in listOf("Where are the advanced settings?", "where are advanced settings", "Onde ficam as configurações avançadas?",
                "onde estão as configuracoes avancadas", "What is Device setup?", "o que são os ajustes do aparelho?"))
            assertTrue(q, BoarPack.aboutBoar(q))
        // Generic words alone still don't.
        for (q in listOf("what are the settings of a sonata?", "advanced mathematics books")) assertFalse(q, BoarPack.aboutBoar(q))
    }
}
