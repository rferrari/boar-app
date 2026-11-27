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
        // Since 2.1.0 (BOAR-81) the Boar Labs switch is in Settings › Plugins; Advanced tweaks is gone.
        assertTrue(DrawRequest.offLabs(Lang.EN).contains("Boar Labs") && DrawRequest.offLabs(Lang.EN).contains("Settings › Plugins"))
        assertTrue(DrawRequest.offLabs(Lang.PT).contains("Configurações › Plugins"))
        assertTrue(DrawRequest.offLabs(Lang.ES).contains("Ajustes › Plugins"))
        for (lang in Lang.entries) assertTrue("$lang", !DrawRequest.offLabs(lang).contains("Experimental") && !DrawRequest.offLabs(lang).contains("tweaks") && !DrawRequest.offLabs(lang).contains("avançados") && !DrawRequest.offLabs(lang).contains("avanzados"))
        val hooks = java.io.File("../app/src/labs/java/team/sopa/boar/labs/LabHooks.kt").readText()
        assertTrue(hooks.contains("if (!LabPrefs.labsOn) team.sopa.boar.engine.labs.DrawRequest.offLabs(it.lang) else team.sopa.boar.engine.labs.DrawRequest.off(it.lang)"))
    }

    @Test fun `a subject over two lines is still a draw request (CodeRabbit on PR 125)`() {
        assertEquals(DrawRequest.Ask("a cat\nwearing a red hat", Lang.EN), DrawRequest.parse("draw me a cat\nwearing a red hat"))
        assertEquals("uma onça\nna floresta", DrawRequest.parse("desenhe uma onça\nna floresta")?.subject)
        assertNull(DrawRequest.parse("what is this?\ndraw me a cat"))
    }

    @Test fun `erasing everything forgets requests not drawn yet (CodeRabbit on PR 125)`() {
        val ill = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/Illustrator.kt").readText()
        assertTrue(ill.substringAfter("fun eraseAll(ctx: Context) {").substringBefore("\n    }\n").contains("asked.clear()"))
    }

    @Test fun `the English subject never asks the chat model while it answers (CodeRabbit on PR 125)`() {
        val ill = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/Illustrator.kt").readText()
        val asked = ill.substringAfter("fun drawAsked(").substringBefore("\n    }\n")
        assertTrue(asked.contains("if (ask.lang != DrawRequest.Lang.EN && !a.chatBusy)"))
    }

    @Test fun `a receipt that can't be saved never touches the answer running now (CodeRabbit on PR 125)`() {
        val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
        val hook = vm.substringAfter("team.sopa.boar.plugins.AnswerReceipts.addTime = ").substringBefore("\n        }\n")
        // Caught where it happens: safeScope's handler would reset the current answer's state.
        assertTrue(hook.contains("runCatching {") && hook.contains(".onFailure { e -> android.util.Log.w(\"BoarChat\", \"receipt time not saved: \${e.javaClass.simpleName}\") }"))
    }

    @Test fun `a subject may start with a digit (CodeRabbit)`() {
        assertEquals(DrawRequest.Ask("a 3D cube", Lang.EN), DrawRequest.parse("draw a 3D cube"))
        assertEquals("a cat", DrawRequest.parse("draw a cat")?.subject)
    }

    @Test fun `draw me a bath is not a picture either (CodeRabbit)`() {
        assertNull(DrawRequest.parse("draw me a bath"))
        assertNull(DrawRequest.parse("Draw me a conclusion from this"))
        assertEquals("a cat", DrawRequest.parse("draw me a cat")?.subject)
        assertEquals("the moon", DrawRequest.parse("draw me the moon")?.subject)
    }

    @Test fun `a stopped translation draws nothing (CodeRabbit)`() {
        val ill = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/Illustrator.kt").readText()
        val asked = ill.substringAfter("fun drawAsked(").substringBefore("\n    }\n")
        assertTrue(asked.contains("if (!kotlinx.coroutines.currentCoroutineContext().let { it[kotlinx.coroutines.Job]?.isActive ?: true } || status[a.id] !is Status.Choosing) return@launch"))
    }

    @Test fun `create or generate an image takes a colon, showing, depicting or with a thing, not only of (adam)`() {
        val fires = listOf(
            "generate an image: a cat on a roof" to ("a cat on a roof" to Lang.EN),
            "Create an image showing a red fox in the snow" to ("a red fox in the snow" to Lang.EN),
            "generate a picture depicting the moon over the sea" to ("the moon over the sea" to Lang.EN),
            "create an image with a dog on a beach" to ("a dog on a beach" to Lang.EN),
            "generate image of a lighthouse" to ("a lighthouse" to Lang.EN),
            "create image: two owls" to ("two owls" to Lang.EN),
            "gere uma imagem: um gato no telhado" to ("um gato no telhado" to Lang.PT),
            "crie uma imagem com um cachorro na praia" to ("um cachorro na praia" to Lang.PT),
            "crie uma imagem mostrando uma onça" to ("uma onça" to Lang.PT),
            "genera una imagen con un perro en la playa" to ("un perro en la playa" to Lang.ES),
            "crea una imagen: un barco" to ("un barco" to Lang.ES),
        )
        for ((text, want) in fires) {
            val ask = DrawRequest.parse(text)
            assertEquals(text, want.first, ask?.subject)
            assertEquals(text, want.second, ask?.lang)
        }
        // A tool after "with", or nothing to draw, stays with the model.
        for (text in listOf("create an image with Python", "generate an image", "create an image:", "crie uma imagem com Python"))
            assertNull(text, DrawRequest.parse(text))
    }

    @Test fun `a listed word is no exclusion when the request names a picture, as in a line drawing (CodeRabbit)`() {
        assertEquals(DrawRequest.Ask("a line drawing of a cat", Lang.EN), DrawRequest.parse("draw me a line drawing of a cat"))
        assertEquals("a line sketch of a horse", DrawRequest.parse("draw a line sketch of a horse")?.subject)
        assertNull(DrawRequest.parse("draw me a bath"))
        assertNull(DrawRequest.parse("draw the line between weather and climate"))
        assertNull(DrawRequest.parse("draw a line under this"))
    }

    @Test fun `an older answer with the same question never takes a newer draw request (CodeRabbit)`() {
        val ill = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/Illustrator.kt").readText()
        val marker = "fun takeAsk(ctx: Context, answerId: String, question: String): DrawRequest.Ask?"
        assertTrue(ill.contains(marker))
        val take = ill.substringAfter(marker).substringBefore("\n    }\n")
        // An answer takes at most one request, ever (kept on disk), and only while it has no drawing of its own.
        assertTrue(take.contains("if (answerId in took || status[answerId] != null) return null"))
        val removed = take.indexOf("asked.remove(question.trim())")
        assertTrue(removed >= 0 && take.indexOf("took += answerId") > removed)
        assertTrue(ill.substringAfter("private fun load(ctx: Context) {").substringBefore("\n    }\n").contains("tookFile(ctx)"))
        assertTrue(ill.substringAfter("fun eraseAll(ctx: Context) {").substringBefore("\n    }\n").contains("took.clear()"))
        val screen = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt").readText()
        assertTrue(screen.contains("Illustrator.takeAsk(ctx, a.id, a.question)"))
    }

    @Test fun `Designer claims the chat model before it asks it for words, and lets it go after (CodeRabbit)`() {
        val ill = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/Illustrator.kt").readText()
        val marker = "private suspend fun <T> withChatModel("
        assertTrue(ill.contains(marker))
        val claim = ill.substringAfter(marker).substringBefore("\n    }\n")
        assertTrue(claim.contains("if (!services.claimModelForLab()) return null"))
        assertTrue(claim.contains("services.activeSession(forLab = true)"))
        assertTrue(claim.contains("} finally {") && claim.contains("services.releaseModelForLab()"))
        for (fn in listOf("private suspend fun englishSubject(", "private suspend fun subjectFromModel(")) {
            assertTrue(fn, ill.contains(fn))
            val body = ill.substringAfter(fn).substringBefore("\n    }\n")
            assertTrue(fn, body.contains("withChatModel(ctx)") && !body.contains("activeSession()"))
        }
    }
}
