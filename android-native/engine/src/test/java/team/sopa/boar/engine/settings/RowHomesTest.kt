package team.sopa.boar.engine.settings

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.1.0 (BOAR-81), the spec's table "Where each removed row goes (nothing is lost)": every row Settings had, found in its
 * new home. One line per row of the table.
 */
class RowHomesTest {
    private fun read(p: String) = java.io.File("../app/src/main/java/team/sopa/boar/$p").readText()
    private val settings = read("ui/settings/SettingsScreen.kt")
    private val setup = read("ui/eval/DeviceSetup.kt")
    private val tone = read("ui/chat/ToneSheet.kt")
    private val models = read("ui/models/ModelsScreen.kt")
    private val main = read("MainActivity.kt")
    private fun group(key: String) = setup.substringAfter("t(\"native.deviceSetup.$key\")").substringBefore("\n        }\n")
    private val engine = setup.substringAfter("internal fun EngineRows(").substringBefore("\n}\n")
    private val memory = setup.substringAfter("internal fun MemoryRows(").substringBefore("\n}\n")
    private val display = settings.substringAfter("t(\"modelSetupScreen.sections.displayTheme\")").substringBefore("t(\"native.settings.languageVoice\")")
    private val plugins = settings.substringAfter("private fun PluginsSection(").substringBefore("\n}\n")

    /** Old row -> (where it is now, the text that proves it). */
    private val table: List<Triple<String, String, String>> = listOf(
        Triple("Styles", tone, "t(\"personalities.\$id.label\")"),
        Triple("Deep Research", tone, "t(\"personalitySettings.deepResearchTitle\")"),
        Triple("Max answer length", group("answering"), "t(\"personalitySettings.maxOutputTokens\")"),
        Triple("Advanced tweaks: presets", engine, "t(\"native.tweaks.presets\")"),
        Triple("Advanced tweaks: CPU build", engine, "t(\"native.tweaks.cpuBuild\")"),
        Triple("Advanced tweaks: threads", engine, "t(\"native.tweaks.threads\")"),
        Triple("Advanced tweaks: batch threads", engine, "t(\"native.tweaks.threadsBatch\")"),
        Triple("Advanced tweaks: cores", engine, "t(\"native.tweaks.cores\")"),
        Triple("Advanced tweaks: strict CPU", engine, "t(\"native.tweaks.strict\")"),
        Triple("Advanced tweaks: context", engine, "t(\"native.tweaks.context\")"),
        Triple("Advanced tweaks: batch", engine, "t(\"native.tweaks.batch\")"),
        Triple("Advanced tweaks: micro batch", engine, "t(\"native.tweaks.microBatch\")"),
        Triple("Advanced tweaks: KV cache", engine, "t(\"native.tweaks.kv\")"),
        Triple("Advanced tweaks: flash attention", engine, "t(\"native.tweaks.flash\")"),
        Triple("Advanced tweaks: load mode", engine, "t(\"native.tweaks.load\")"),
        Triple("Advanced tweaks: weight repack", engine, "t(\"native.tweaks.repack\")"),
        Triple("Advanced tweaks: thinking", engine, "t(\"native.tweaks.thinking\")"),
        Triple("Advanced tweaks: MoE experts", engine, "t(\"native.tweaks.moe\")"),
        Triple("Advanced tweaks: stage timeout", engine, "t(\"native.tweaks.timeout\")"),
        Triple("Advanced tweaks: reset", engine, "t(\"native.tweaks.resetAll\")"),
        Triple("Adaptive routing", group("answering"), "t(\"native.settings.adaptiveTitle\")"),
        Triple("Staged quotes", group("answering"), "t(\"native.tweaks.stagedQuotes\")"),
        Triple("Turns before summarising", group("answering"), "t(\"memorySettings.turnsBeforeSummarizing\")"),
        Triple("Free memory in background", memory, "t(\"native.settings.freeMemory\")"),
        Triple("Free memory after", memory, "t(\"native.tweaks.freeMemoryAfter\")"),
        Triple("Show technical details", group("diagnostics"), "t(\"native.tweaks.technicalDetails\")"),
        Triple("Hardware telemetry", group("diagnostics"), "TelemetryRows(services)"),
        Triple("Debug log", group("diagnostics"), "DebugLogRows()"),
        Triple("Boarverse switch", display, "t(\"native.vault.experimental\")"),
        Triple("Boar Labs switch and waiting animation", plugins, "team.sopa.boar.labs.LabHooks.SettingsRow()"),
        Triple("Plugins list link", plugins, "t(\"native.labs.openList\")"),
        Triple("Evaluation link: catalog card", models, "item { EvaluationCard(open) }"),
        Triple("Evaluation link: menu in builder builds", main, "DrawerItem(\"evaluation\", t(\"evaluation.title\"))"),
    )

    @Test fun `every row Settings had is in its new home`() {
        for ((row, home, proof) in table) assertTrue(row, home.contains(proof))
    }

    @Test fun `the Memory group holds the Memory rows, and Device setup is on Evaluation`() {
        assertTrue(group("memory").contains("MemoryRows(services)"))
        assertTrue(main.contains("deviceSetup = { team.sopa.boar.ui.eval.DeviceSetup(services) }"))
    }

    @Test fun `the waiting animation is in the Boar Labs row Settings shows`() {
        val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText().substringAfter("fun SettingsRow()").substringBefore("\n    }\n")
        assertTrue(hooks.contains("native.labs.wait.setting"))
    }
}
