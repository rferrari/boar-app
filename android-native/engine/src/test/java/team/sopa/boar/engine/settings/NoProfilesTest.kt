package team.sopa.boar.engine.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.1.0 (BOAR-81) step 5: the profiles are gone from the code, not only from the screen. */
class NoProfilesTest {
    private fun read(p: String) = java.io.File(p).readText()

    @Test fun `no UserProfile, SettingsPart, settleProfile or Labs profile hook anywhere`() {
        assertFalse(java.io.File("src/main/java/team/sopa/boar/engine/settings/UserProfile.kt").exists())
        val files = listOf("src/main/java/team/sopa/boar/engine/settings/AppSettings.kt", "src/main/java/team/sopa/boar/engine/labs/LabDefaults.kt",
            "../app/src/main/java/team/sopa/boar/MainActivity.kt", "../app/src/main/java/team/sopa/boar/setup/SetupViewModel.kt",
            "../app/src/labs/java/team/sopa/boar/labs/LabPlugin.kt", "../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt")
        for (f in files) for (w in listOf("UserProfile", "SettingsPart", "settleProfile", "profileNotePending", "profileSettled", "managementShown", "proProfile", "anyChosenOn"))
            // The migration names the old note's key once, to delete it.
            assertFalse("$f $w", read(f).replace("remove(\"profileNotePending\")", "").contains(w))
    }

    @Test fun `the migration also drops the stored profile and its note`() {
        val f = java.nio.file.Files.createTempDirectory("boar-noprof").toFile().resolve("settings.json")
        f.writeText("""{"userProfile":"explorer","profileNotePending":true,"activeModelId":{"llm":"m"}}""")
        AppSettings(f).retireProfiles()
        val raw = AppSettings(f).raw()
        assertFalse(raw.containsKey("userProfile")); assertFalse(raw.containsKey("profileNotePending")); assertTrue(AppSettings(f).profilesRetired)
    }

    @Test fun `setup is as a fresh install sees it - no Boar Labs page`() {
        val setup = read("../app/src/main/java/team/sopa/boar/setup/SetupViewModel.kt")
        assertTrue(setup.contains("private fun guidePages() = SetupGuide.pages(false, services.pinLock.isSet, team.sopa.boar.app.NotificationAsk.applies())"))
    }
}
