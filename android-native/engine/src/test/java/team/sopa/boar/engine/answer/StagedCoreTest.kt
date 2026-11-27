package team.sopa.boar.engine.answer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.settings.AppSettings

/** Staged answers became core on Nov 12 (adam): a setting, on by default, Pro's switch, the old plugin switch migrated. */
class StagedCoreTest {
    @Test fun `on by default, and a stored off holds`() {
        val f = java.nio.file.Files.createTempFile("settings", ".json").toFile().apply { delete() }
        val s = AppSettings(f)
        assertTrue(s.stagedQuotes)
        s.stagedQuotes = false
        assertTrue(!AppSettings(f).stagedQuotes)
        f.delete()
    }

    @Test fun `the old plugin switched off stays off, on or never set becomes the default on`() {
        assertEquals(false, StagedMigration.settingFrom(false))
        assertNull(StagedMigration.settingFrom(true))
        assertNull(StagedMigration.settingFrom(null))
    }

    @Test fun `the switch is in Device setup, the plugin and its manifest line are gone, the chat reads the setting`() {
        assertTrue(java.io.File("../app/src/main/java/team/sopa/boar/ui/eval/DeviceSetup.kt").readText().contains("s.stagedQuotes = it"))
        assertTrue(!java.io.File("../app/src/labs/plugins/staged").exists())
        assertTrue(!java.io.File("../app/src/labs/released.txt").readText().contains("staged:"))
        val services = java.io.File("../app/src/main/java/team/sopa/boar/app/BoarServices.kt").readText()
        assertTrue(services.contains("AnswerModes.staged = { settings.stagedQuotes }") && services.contains("StagedMigration.settingFrom("))
    }

    @Test fun `the chat shows Staged's text through the join that keeps it running text (the one-word-per-line bug)`() {
        val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
        assertTrue(vm.contains("StagedAnswer.join("))
    }
}
