package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.1.0 (BOAR-81): Evaluation gains a folded Device setup, where the builders' rows from Settings went. */
class DeviceSetupTest {
    private val app = "../app/src/main"
    private val setup = java.io.File("$app/java/team/sopa/boar/ui/eval/DeviceSetup.kt").readText()
    private fun group(key: String) = setup.substringAfter("t(\"native.deviceSetup.$key\")").substringBefore("\n        }\n")

    @Test fun `four groups - Engine, Answering, Memory, Diagnostics - each with its rows`() {
        val engine = group("engine")
        for (k in listOf("native.tweaks.presets", "native.tweaks.cpuBuild", "native.tweaks.threads", "native.tweaks.context", "native.tweaks.kv",
                "native.tweaks.flash", "native.tweaks.load", "native.tweaks.thinking", "native.tweaks.timeout", "native.tweaks.resetAll"))
            assertTrue("engine $k", engine.contains(k) || setup.substringAfter("fun EngineRows(").contains(k))
        val answering = group("answering")
        for (k in listOf("personalitySettings.maxOutputTokens", "native.settings.adaptiveTitle", "native.tweaks.stagedQuotes", "memorySettings.turnsBeforeSummarizing"))
            assertTrue("answering $k", answering.contains(k))
        val memory = group("memory")
        val memoryRows = setup.substringAfter("internal fun MemoryRows(").substringBefore("\n}\n")
        assertTrue(memory.contains("MemoryRows(services)"))
        for (k in listOf("native.settings.freeMemory", "native.tweaks.freeMemoryAfter")) assertTrue("memory $k", memoryRows.contains(k))
        val diagnostics = group("diagnostics")
        for (k in listOf("native.tweaks.technicalDetails", "TelemetryRows(services)", "DebugLogRows()")) assertTrue("diagnostics $k", diagnostics.contains(k))
    }

    @Test fun `Evaluation shows it folded, and the app hands it the services`() {
        val eval = java.io.File("$app/java/team/sopa/boar/ui/eval/EvaluationScreen.kt").readText()
        assertTrue(eval.contains("deviceSetup: @Composable () -> Unit = {}"))
        assertTrue(eval.contains("var setupOpen by remember { mutableStateOf(false) }") && eval.contains("if (setupOpen) item { deviceSetup() }"))
        val main = java.io.File("$app/java/team/sopa/boar/MainActivity.kt").readText()
        assertTrue(main.contains("deviceSetup = { team.sopa.boar.ui.eval.DeviceSetup(services) }"))
        // The engine rows still apply to the chat on its next answer (2.1.0; try-first is 2.2.0).
        assertTrue(setup.contains("services.host.unload()") && setup.contains("t(\"native.tweaks.appliesNext\")"))
    }

    @Test fun `its words are in English and Portuguese`() {
        for (d in listOf("values", "values-pt")) {
            val x = java.io.File("$app/res/$d/strings_native.xml").readText()
            for (k in listOf("native_eval_deviceSetup", "native_eval_deviceSetupNote", "native_deviceSetup_engine", "native_deviceSetup_answering",
                    "native_deviceSetup_memory", "native_deviceSetup_diagnostics")) assertTrue("$d $k", x.contains("name=\"$k\""))
        }
    }
}
