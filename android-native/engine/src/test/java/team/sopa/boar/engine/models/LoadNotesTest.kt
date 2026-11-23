package team.sopa.boar.engine.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The "couldn't load" note closes when the user picks another model in the Model Catalog. */
class LoadNotesTest {
    @Test fun `a new pick closes the note, the same pick keeps it`() {
        assertFalse(LoadNotes.stillAbout("qwen3-4b", "lfm2.5-8b"))
        assertTrue(LoadNotes.stillAbout("qwen3-4b", "qwen3-4b"))
        assertTrue(LoadNotes.stillAbout("qwen3-4b", null))
    }

    @Test fun `the chat checks the pick when it comes back from the Model Catalog`() {
        val vm = File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
        assertTrue(vm.contains("LoadNotes.stillAbout(note.modelId, services.activeModel()?.id)"))
        val main = File("../app/src/main/java/team/sopa/boar/MainActivity.kt").readText()
        assertTrue(main.contains("ModelsScreen(modelsState, models, onBrowse = { screen = \"browse\" }) { vm.refreshModel()"))
    }
}
