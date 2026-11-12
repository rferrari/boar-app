package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 2 (adam, Nov 12): plugins that answer, Ask BOAR instead, Illustrate's gate. Read from the source. */
class Wave2Test {
    private fun read(p: String) = java.io.File("../app/src/$p").readText()

    @Test fun `Lunar, Nomad and Vitals answer their own questions and speak as themselves`() {
        assertTrue(read("labs/plugins/lunar/java/team/sopa/boar/labs/lunar/LunarLab.kt").contains("PluginAnswers.isMoonQuestion(query)"))
        assertTrue(read("labs/plugins/compass/java/team/sopa/boar/labs/compass/CompassLab.kt").contains("PluginAnswers.isDirectionQuestion(query)"))
        assertTrue(read("labs/plugins/pulse/java/team/sopa/boar/labs/pulse/DevicePulseLab.kt").contains("PluginAnswers.isPhoneQuestion(query)"))
        val hooks = read("labs/java/team/sopa/boar/labs/LabHooks.kt")
        for (p in listOf("\"lunar-\") -> \"lunar\"", "\"compass-\") -> \"compass\"", "\"vitals-\") -> \"pulse\"")) assertTrue(p, hooks.contains(p))
    }

    @Test fun `Ask BOAR instead gives the model the plugin's result to explain, and the answer is BOAR's`() {
        val vm = read("main/java/team/sopa/boar/chat/ChatViewModel.kt")
        assertTrue(vm.contains("bypassed = instant?.receipt?.tool?.let { tool -> ToolResult.Context(tool, AskModelContext.of(instant.text)) }"))
        assertTrue(vm.contains("val tool = if (skipTools) bypass?.takeIf { !(deep || personal) }"))
        assertTrue(vm.contains("tool?.tool?.takeIf { tool !== bypass }"))
        assertTrue(team.sopa.boar.engine.chat.AskModelContext.of("About 2 hours.").contains("Keep its numbers as they are"))
    }

    @Test fun `Illustrate uses the gate`() {
        assertTrue(read("labs/plugins/designer/java/team/sopa/boar/labs/designer/Illustrator.kt").contains("IllustrateGate.allowed(a.tool, a.text)"))
    }
}
