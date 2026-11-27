package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.labs.DrawRequest.Lang

/** adam, 26 Nov (t9 2008): a message that STARTS with a draw request is answered by Designer in the chat. */
class DrawRequestTest {
    private val fires = listOf(
        "draw me a mouse wearing a shirt" to ("a mouse wearing a shirt" to Lang.EN),
        "Draw me a lighthouse at sunset." to ("a lighthouse at sunset" to Lang.EN),
        "draw a cat on a red sofa" to ("a cat on a red sofa" to Lang.EN),
        "Draw an owl in the snow" to ("an owl in the snow" to Lang.EN),
        "please draw me a boar" to ("a boar" to Lang.EN),
        "create an image of a volcano erupting at night" to ("a volcano erupting at night" to Lang.EN),
        "Generate a picture of two foxes in a forest" to ("two foxes in a forest" to Lang.EN),
        "make an image of the moon over the sea" to ("the moon over the sea" to Lang.EN),
        "make me a drawing of a castle" to ("a castle" to Lang.EN),
        "generate an illustration of a heart cell" to ("a heart cell" to Lang.EN),
        "paint me a field of sunflowers!" to ("a field of sunflowers" to Lang.EN),
        "paint a picture of a harbour" to ("a harbour" to Lang.EN),
        "desenhe um gato de chapéu" to ("um gato de chapéu" to Lang.PT),
        "Desenha um farol no pôr do sol" to ("um farol no pôr do sol" to Lang.PT),
        "desenhe pra mim uma casa na árvore" to ("uma casa na árvore" to Lang.PT),
        "gere uma imagem de um vulcão" to ("um vulcão" to Lang.PT),
        "Crie uma imagem do Cristo Redentor" to ("o Cristo Redentor" to Lang.PT),
        "faça um desenho de uma onça na floresta" to ("uma onça na floresta" to Lang.PT),
        "por favor, gere uma ilustração da lua" to ("a lua" to Lang.PT),
        "dibuja un perro con gafas" to ("un perro con gafas" to Lang.ES),
        "Dibújame una montaña nevada" to ("una montaña nevada" to Lang.ES),
        "genera una imagen de un barco pirata" to ("un barco pirata" to Lang.ES),
        "crea una imagen del sol" to ("el sol" to Lang.ES),
    )

    private val stays = listOf(
        "can you draw a conclusion from this?",
        "what does the chart draw on?",
        "draw a conclusion from these results",
        "Draw the line between weather and climate",
        "how do I draw a graph in Excel?",
        "create an account on the app",
        "make a list of the planets",
        "generate a summary of this chat",
        "what is an image of a function?",
        "who painted the Mona Lisa?",
        "draw",
        "draw me",
        "como desenhar um gato?",
        "desenho técnico, o que é?",
        "o que é uma imagem de satélite?",
        "crie uma lista de compras",
        "¿cómo dibujar un perro?",
        "dibujo técnico qué es",
        "pinta la pared de blanco?",
    )

    @Test fun `a message that starts with a draw request gives its subject and language`() {
        for ((text, want) in fires) {
            val ask = DrawRequest.parse(text)
            assertEquals(text, want.first, ask?.subject)
            assertEquals(text, want.second, ask?.lang)
        }
    }

    @Test fun `anything else stays with the model, including draw used another way`() {
        for (text in stays) assertNull(text, DrawRequest.parse(text))
    }

    @Test fun `the replies are short, in the request's language, and never claim a picture already exists`() {
        for (lang in Lang.entries) {
            val drawing = DrawRequest.drawing(lang, "a cat")
            assertTrue(drawing, drawing.contains("a cat"))
            for (line in listOf(drawing, DrawRequest.off(lang), DrawRequest.noModel(lang), DrawRequest.refused(lang), DrawRequest.busy(lang)))
                assertTrue("$lang $line", line.isNotBlank() && line.length < 200 && !Regex("here is|aqui está|aquí está", RegexOption.IGNORE_CASE).containsMatchIn(line))
        }
        assertTrue(DrawRequest.off(Lang.PT).contains("Plugins") && DrawRequest.off(Lang.EN).contains("Plugins"))
        assertTrue(DrawRequest.noModel(Lang.EN).contains("Designer"))
        assertTrue(DrawRequest.drawing(Lang.PT, "um gato").startsWith("Desenhando"))
        assertTrue(DrawRequest.drawing(Lang.ES, "un perro").startsWith("Dibujando"))
        // The tool ids Designer answers with; the chat shows them as Designer, with Ask BOAR under them.
        assertTrue(listOf(DrawRequest.TOOL, DrawRequest.TOOL_OFF, DrawRequest.TOOL_NO_MODEL, DrawRequest.TOOL_REFUSED).all { it.startsWith("designer-") })
    }

    @Test fun `the app wires it - Designer answers when on, a plain line when off, never the model`() {
        val app = "../app/src"
        val lab = java.io.File("$app/labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt").readText()
        val tool = lab.substringAfter("override val answerTool").substringBefore("\n    }\n")
        assertTrue(tool.contains("DrawRequest.parse(query)") && tool.contains("ImageSafety.blocked(") && tool.contains("SdRunner.ready(") && tool.contains("Illustrator.ask("))
        assertTrue(lab.contains("if (a.tool == DrawRequest.TOOL)") && lab.contains("Illustrator.drawAsked("))
        val ill = java.io.File("$app/labs/plugins/designer/java/team/sopa/boar/labs/designer/Illustrator.kt").readText()
        val asked = ill.substringAfter("fun drawAsked(").substringBefore("\n    }\n")
        assertTrue(asked.contains("busy()") && asked.contains("enoughMemory(ctx)") && asked.contains("ImageSafety.safeSubject("))
        // A Portuguese or Spanish subject is put in English first (SDXS reads English), with the chat model.
        assertTrue(asked.contains("ask.lang != DrawRequest.Lang.EN") && ill.contains("private suspend fun englishSubject("))
        val hooks = java.io.File("$app/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()
        assertTrue(hooks.contains("tool.startsWith(\"designer-\") -> \"image\""))
        assertTrue(hooks.contains("team.sopa.boar.plugins.AnswerTools.offAnswer = "))
        val tools = java.io.File("$app/main/java/team/sopa/boar/plugins/AnswerTools.kt").readText()
        assertTrue(tools.contains("?: runCatching { offAnswer(query) }.getOrNull()"))
        // The guide says it, English and Portuguese.
        assertTrue(java.io.File("$app/main/assets/boar-guide/en.md").readText().contains("A chat message that starts with \"draw me…\" is drawn by Designer."))
        assertTrue(java.io.File("$app/main/assets/boar-guide/pt.md").readText().contains("Uma mensagem que começa com \"desenhe…\" é desenhada pelo Designer."))
    }

    @Test fun `the answer's receipt counts the drawing, not only the instant reply (adam, F3)`() {
        val app = "../app/src"
        val ill = java.io.File("$app/labs/plugins/designer/java/team/sopa/boar/labs/designer/Illustrator.kt").readText()
        val asked = ill.substringAfter("fun drawAsked(").substringBefore("\n    }\n")
        // From the request's start (the English subject included) to the picture, added once the drawing is done.
        assertTrue(asked.contains("val started = System.nanoTime()") && asked.contains("onDone = { team.sopa.boar.plugins.AnswerReceipts.addTime(a.id, (System.nanoTime() - started) / 1e6, \"drawing\") }"))
        val vm = java.io.File("$app/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
        val hook = vm.substringAfter("team.sopa.boar.plugins.AnswerReceipts.addTime = ").substringBefore("\n        }\n")
        // Saved in the answer's meta (a reopened chat shows it) and on screen at once; the total and the stage both.
        assertTrue(hook.contains("totalMs = r.totalMs + ms") && hook.contains("stages = r.stages.orEmpty() + (stage to ms)"))
        assertTrue(hook.contains("chats.setMessageMeta(id,") && hook.contains("it.copy(receipt = updated)"))
        assertTrue(vm.contains("team.sopa.boar.plugins.AnswerReceipts.addTime = team.sopa.boar.plugins.AnswerReceipts.NONE"))
    }

    @Test fun `with Boar Labs off, the line says to turn it on first - Plugins isn't in the menu then (CodeRabbit on PR 125)`() {
        for (lang in Lang.entries) assertTrue("$lang", DrawRequest.offLabs(lang) != DrawRequest.off(lang) && DrawRequest.offLabs(lang).length < 200)
        assertTrue(DrawRequest.offLabs(Lang.EN).contains("Boar Labs") && DrawRequest.offLabs(Lang.EN).contains("Settings › Advanced tweaks"))
        assertTrue(DrawRequest.offLabs(Lang.PT).contains("Configurações › Ajustes avançados"))
        val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()
        assertTrue(hooks.contains("if (!LabPrefs.labsOn) team.sopa.boar.engine.labs.DrawRequest.offLabs(it.lang) else team.sopa.boar.engine.labs.DrawRequest.off(it.lang)"))
    }
}
