package team.sopa.boar.engine.answer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.answer.AnswerLanguage.Lang
import team.sopa.boar.engine.rag.Chunk
import team.sopa.boar.engine.rag.ConversationHistory
import team.sopa.boar.engine.rag.ConversationTurn
import team.sopa.boar.engine.rag.RagPure

/** t9 0420 (adam: "send language fix. all prompts… this is important fix"): r4to's Portuguese line, back. */
class AnswerLanguageTest {
    private val src = listOf(Chunk("c1", "d1", "Seawater", "Seawater is salty because rivers carry dissolved salts to the sea."))
    @After fun reset() { AnswerLanguage.appLanguage = null }

    @Test fun `a Portuguese question gets r4to's line next to it, with and without sources, in both prompt shapes`() {
        val q = "Por que a água do mar é salgada?"
        val chat = RagPure.assembleChatMessages(q, src).last().content
        assertTrue(chat, chat.startsWith(q) && chat.endsWith("\n\n" + AnswerLanguage.PT_WITH_SOURCES))
        assertTrue(RagPure.assembleChatMessages(q, emptyList()).last().content.endsWith("\n\n" + AnswerLanguage.PT_NO_SOURCES))
        val plain = RagPure.assemblePrompt(q, src)
        assertTrue(plain, plain.endsWith("Question: $q\n\n${AnswerLanguage.PT_WITH_SOURCES}\n\nAnswer:"))
        // The system head (kept prefilled in the cache) is the same as for English.
        assertEquals(RagPure.assembleChatMessages("Why is seawater salty?", src).first().content, RagPure.assembleChatMessages(q, src).first().content)
    }

    @Test fun `an English question's prompt is exactly as before`() {
        val q = "Why is seawater salty?"
        assertEquals(q, RagPure.assembleChatMessages(q, src).last().content)
        assertFalse(RagPure.assemblePrompt(q, src).contains("Responda"))
        // Even in a Portuguese app: a clearly English question is answered in English.
        AnswerLanguage.appLanguage = "pt"
        assertEquals(q, RagPure.assembleChatMessages(q, src).last().content)
    }

    @Test fun `Staged's details step reads the language from the real question, not its English ask`() {
        val h = ConversationHistory(null, listOf(ConversationTurn("user", "Por que o mar é salgado?"), ConversationTurn("assistant", "Porque os rios…")))
        val details = RagPure.assembleChatMessages(StagedAnswer.DETAILS_ASK, src, history = h, languageOf = "Por que o mar é salgado?").last().content
        assertTrue(details.endsWith(AnswerLanguage.PT_WITH_SOURCES))
    }

    @Test fun `Personal Files in Portuguese says its own line, and dropHedge knows it`() {
        val p = RagPure.assembleChatMessages("Qual é o meu tipo sanguíneo?", src, filesOnly = true).last().content
        assertTrue(p.endsWith(AnswerLanguage.PT_FILES_ONLY) && p.contains(PersonalFiles.NOT_FOUND_PT))
        val a = PersonalFiles.NOT_FOUND_PT + " Porém, seu exame mostra tipo O positivo [1], segundo o laudo de março de 2026 que você salvou."
        assertEquals("Seu exame mostra tipo O positivo [1], segundo o laudo de março de 2026 que você salvou.", PersonalFiles.dropHedge(a))
    }

    @Test fun `which language - the question, then the conversation's last clear one, then the app`() {
        fun check(q: String, earlier: List<String>, app: String?, want: Lang) = assertEquals("$q / $earlier / $app", want, AnswerLanguage.of(q, earlier, app))
        check("Quantos ossos tem o corpo humano?", listOf(), null, Lang.PT)
        check("How many bones are in the human body?", listOf(), "pt", Lang.EN)
        check("e por quê?", listOf("Por que o céu é azul?"), null, Lang.PT)
        check("and why?", listOf("Why is the sky blue?"), "pt", Lang.EN)
        check("Everest?", listOf(), "pt", Lang.PT)
        check("Everest?", listOf(), "en", Lang.EN)
        check("26 km", listOf("Quanto é 26 milhas em km?"), null, Lang.PT)
    }

    @Test fun `r4to's lines, in adam's wording of the language`() {
        assertEquals("Responda em Português, mesmo que as fontes estejam em inglês, e cite cada afirmação com o número da fonte, como [1].", AnswerLanguage.PT_WITH_SOURCES)
        assertEquals("Responda em Português.", AnswerLanguage.PT_NO_SOURCES)
    }

    @Test fun `the chat title follows the chat's language, English unchanged`() {
        assertEquals("\"Why is the sky blue?\"", Summarize.titleInput("Why is the sky blue?"))
        assertEquals("\"Por que o céu é azul?\"\n\n" + AnswerLanguage.PT_NO_SOURCES, Summarize.titleInput("Por que o céu é azul?"))
    }
}

/** t9 0420 item 5: the arms, bench only; English never moves. */
class AnswerLanguageArmsTest {
    private val src = listOf(team.sopa.boar.engine.rag.Chunk("c1", "d1", "Seawater", "Seawater is salty."))
    private val sys = "You are Boar, a concise offline research assistant. Answer in 2-3 short, direct sentences or a brief paragraph, no preamble. Do not default to a bulleted list or a single takeaway sentence followed by three bullet points — use bullets only when the content is genuinely a list of distinct items."
    private val rem = "Keep it short: 2-3 sentences. No lists unless the question asks for one."
    @org.junit.After fun reset() { AnswerLanguage.arm = 1 }

    @Test fun `arm 1 keeps the English style texts, arm 2 the reminder in Portuguese, arm 3 the prompt too`() {
        val q = "Por que a água do mar é salgada?"
        fun msgs() = team.sopa.boar.engine.rag.RagPure.assembleChatMessages(q, src, sys, styleReminder = rem)
        assertTrue(msgs().last().content.contains(rem) && msgs().first().content.startsWith(sys))
        AnswerLanguage.arm = 2
        assertTrue(msgs().last().content.contains("Seja breve: 2-3 frases.") && msgs().first().content.startsWith(sys))
        AnswerLanguage.arm = 3
        assertTrue(msgs().first().content.startsWith("Você é o Boar, um assistente de pesquisa offline e conciso."))
    }

    @Test fun `English never moves, whatever the arm`() {
        AnswerLanguage.arm = 1
        val en = team.sopa.boar.engine.rag.RagPure.assembleChatMessages("Why is seawater salty?", src, sys, styleReminder = rem)
        AnswerLanguage.arm = 3
        assertEquals(en, team.sopa.boar.engine.rag.RagPure.assembleChatMessages("Why is seawater salty?", src, sys, styleReminder = rem))
    }

    @Test fun `every style text in personalities json has its Portuguese`() {
        val json = java.io.File("../app/src/main/assets/boar-data/personalities.json").readText()
        val texts = Regex("\"(systemPrompt|styleReminder|systemPromptSmall|styleReminderSmall)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)+)\"").findAll(json)
            .map { kotlinx.serialization.json.Json.decodeFromString<String>("\"" + it.groupValues[2] + "\"") }.toList()
        assertTrue(texts.isNotEmpty())
        for (t in texts) assertTrue(t.take(40), StylePt.of(t) != t)
    }
}

class LanguageArmRequestTest {
    @org.junit.After fun reset() { AnswerLanguage.arm = 1 }

    @Test fun `a request sets the arm for its run, 0 to 3 only, and arm 0 sends no line`() {
        val r = team.sopa.boar.engine.eval.DeviceEval.parseRequest("""{"requestId":"lang-a0","languageArm":0}""")
        assertEquals(0, r.languageArm)
        AnswerLanguage.arm = 0
        assertEquals(null, AnswerLanguage.line(AnswerLanguage.Lang.PT, withSources = true))
        AnswerLanguage.arm = 1
        assertEquals(AnswerLanguage.PT_WITH_SOURCES, AnswerLanguage.line(AnswerLanguage.Lang.PT, withSources = true))
        assertTrue(runCatching { team.sopa.boar.engine.eval.DeviceEval.parseRequest("""{"requestId":"x","languageArm":7}""") }.isFailure)
        val runner = java.io.File("../app/src/main/java/team/sopa/boar/eval/DeviceEvalRunner.kt").readText()
        assertTrue(runner.contains("AnswerLanguage.arm = request.languageArm ?: 1") && runner.contains("finally { team.sopa.boar.engine.answer.AnswerLanguage.arm = 1 }"))
    }
}

/** Validator C-1 to C-3 (t9 0010 item 5), and the Nov 14 detection measure kept as it was. */
class AnswerLanguageValidatorTest {
    @After fun reset() { AnswerLanguage.appLanguage = null }

    @Test fun `Spanish, French, German and a hola get no Portuguese line, in a Portuguese app too (validator C-1)`() {
        AnswerLanguage.appLanguage = "pt"
        for (q in listOf("hola", "¡Hola!", "¿Qué es la fotosíntesis?", "Cuál es la capital de Australia y por qué es importante",
            "Pourquoi le ciel est bleu?", "Qu'est-ce que la photosynthèse?", "Bonjour", "Warum ist der Himmel blau?", "Wie funktioniert eine Impfung?", "Hallo")) {
            assertEquals(q, Lang.OTHER, AnswerLanguage.of(q))
            assertEquals(q, null, AnswerLanguage.line(AnswerLanguage.of(q), withSources = true))
        }
        // Portuguese with a Spanish-looking name stays Portuguese.
        assertEquals(Lang.PT, AnswerLanguage.of("Qual é a capital de El Salvador e de Los Angeles?"))
        assertEquals(Lang.PT, AnswerLanguage.of("O que é o El Niño?"))
    }

    @Test fun `System follows the phone's language, not English (validator C-2)`() {
        val was = java.util.Locale.getDefault()
        try {
            AnswerLanguage.appLanguage = null
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-BR"))
            assertEquals(Lang.PT, AnswerLanguage.of("Napoleão"))
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("en-US"))
            assertEquals(Lang.EN, AnswerLanguage.of("Napoleão"))
            // A setting chosen in the app still wins over the phone.
            AnswerLanguage.appLanguage = "pt"
            assertEquals(Lang.PT, AnswerLanguage.of("Napoleão"))
        } finally { java.util.Locale.setDefault(was) }
    }

    @Test fun `quoted words and a translate request don't make an English question Portuguese (validator C-3)`() {
        AnswerLanguage.appLanguage = "pt"
        for (q in listOf("Translate 'bom dia' to English", "Translate \"obrigado\" into English", "How do you say saudade in English?",
            "What does 'cafuné' mean?", "What does the word \u201cgostoso\u201d mean?"))
            assertEquals(q, Lang.EN, AnswerLanguage.of(q))
        // The other way: a Portuguese question about an English word stays Portuguese.
        assertEquals(Lang.PT, AnswerLanguage.of("O que significa 'deadline' em inglês?"))
    }

    @Test fun `the 40 short questions of the Nov 14 measure read as they did`() {
        val cases = listOf(
            "Por que o céu é azul?" to Lang.PT,
            "Quantos ossos tem o corpo humano?" to Lang.PT,
            "O que causa as marés?" to Lang.PT,
            "Quem foi Santos Dumont?" to Lang.PT,
            "e por quê?" to Lang.PT,
            "quanto custa?" to Lang.PT,
            "Capital da Austrália?" to null,
            "Me explica a fotossíntese" to null,
            "tem vacina pra dengue?" to Lang.PT,
            "qual a altura do Everest" to Lang.PT,
            "Napoleão" to null,
            "como fazer pão" to Lang.PT,
            "isso é perigoso?" to Lang.PT,
            "e no Brasil?" to Lang.PT,
            "Febre de 39 graus, o que faço?" to Lang.PT,
            "Água do mar é salgada por quê" to Lang.PT,
            "Quem descobriu o Brasil?" to Lang.PT,
            "Diferença entre vírus e bactéria" to Lang.PT,
            "melhor horário pra correr" to null,
            "obrigado!" to null,
            "Why is the sky blue?" to Lang.EN,
            "How many bones are in the human body?" to Lang.EN,
            "What causes tides?" to Lang.EN,
            "Who was Napoleon?" to Lang.EN,
            "and why?" to Lang.EN,
            "how much?" to Lang.EN,
            "Capital of Australia?" to Lang.EN,
            "Explain photosynthesis" to Lang.EN,
            "is there a dengue vaccine?" to Lang.EN,
            "Everest height" to Lang.EN,
            "Napoleon" to null,
            "how to bake bread" to Lang.EN,
            "is it dangerous?" to Lang.EN,
            "and in Brazil?" to Lang.EN,
            "Fever of 39, what should I do?" to Lang.EN,
            "Why is seawater salty" to Lang.EN,
            "Who discovered Brazil?" to Lang.EN,
            "Virus vs bacteria" to Lang.EN,
            "best time to run" to Lang.EN,
            "thanks!" to Lang.EN,
        )
        for ((q, want) in cases) assertEquals(q, want, AnswerLanguage.clear(q))
    }
}
