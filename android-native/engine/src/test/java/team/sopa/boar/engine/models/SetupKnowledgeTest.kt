package team.sopa.boar.engine.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Setup's knowledge step (spec §7): the Encyclopedia without asking when it fits; the real difference when it doesn't. */
class SetupKnowledgeTest {
    private val catalog = Catalog.parse(File("../app/src/main/assets/boar-data/catalog.json").readText())
    private fun packs(tier: String) = catalog.tier(tier)!!.corpusPackIds.mapNotNull(catalog::byId)

    @Test fun `with room for the Encyclopedia there is no choice`() {
        assertTrue(SetupKnowledge.skipChoice(0L))
        assertFalse(SetupKnowledge.skipChoice(1L))
        assertFalse(SetupKnowledge.skipChoice(null))
    }

    @Test fun `the Encyclopedia adds exactly the Wikipedia pack, as the catalog sizes it`() {
        val extra = SetupKnowledge.extraBytes(packs("full"), packs("encyclopedia"))
        assertEquals(catalog.byId("wiki-vital5")!!.sizeBytes, extra)
        assertTrue(extra in 150_000_000L..180_000_000L)
    }

    @Test fun `setup's Continue goes through the rule, and the low-space card says the difference`() {
        val vm = File("../app/src/main/java/team/sopa/boar/setup/SetupViewModel.kt").readText()
        val screen = File("../app/src/main/java/team/sopa/boar/ui/setup/SetupScreen.kt").readText()
        assertTrue(vm.contains("SetupKnowledge.skipChoice("))
        assertTrue(screen.contains("vm.continueFromModel()"))
        assertTrue(screen.contains("native.setup.encyclopediaExtra"))
        for (dir in listOf("values", "values-pt"))
            assertTrue(File("../app/src/main/res/$dir/strings_native.xml").readText().contains("native_setup_encyclopediaExtra\" formatted=\"false\">+{{size}}"))
    }
}
