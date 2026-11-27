package team.sopa.boar.engine.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.1.0 (BOAR-81) step 6: the profiles' strings are gone; the new card, sections and line are there, EN and PT. */
class SimpleSettingsStringsTest {
    private fun strings(d: String) = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()

    @Test fun `no profile strings, nor the ones only the old Settings used`() {
        for (d in listOf("values", "values-pt")) {
            assertFalse(d, strings(d).contains("name=\"native_settings_profile_"))
            for (k in listOf("native_settings_experimental", "native_settings_experimentalNote", "native_settings_experimentalNoteBelowPro",
                    "native_tweaks_evaluation", "native_tweaks_evaluationNote", "native_settings_summary_deep"))
                assertFalse("$d $k", strings(d).contains("name=\"$k\""))
        }
    }

    @Test fun `the card, the Device setup sections and the custom-setup line, in both languages`() {
        for (d in listOf("values", "values-pt")) for (k in listOf("native_models_evaluationTitle", "native_models_evaluationSubtitle", "native_eval_deviceSetup",
                "native_eval_deviceSetupNote", "native_deviceSetup_engine", "native_deviceSetup_answering", "native_deviceSetup_memory", "native_deviceSetup_diagnostics",
                "native_settings_chats", "native_settings_plugins", "native_settings_customSetup", "native_settings_customSetupReset"))
            assertTrue("$d $k", strings(d).contains("name=\"$k\""))
    }
}
