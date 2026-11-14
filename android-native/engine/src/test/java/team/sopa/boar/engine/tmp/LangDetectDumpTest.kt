package team.sopa.boar.engine.tmp
import org.junit.Test
import team.sopa.boar.engine.answer.AnswerLanguage
class LangDetectDumpTest {
    @Test fun dump() {
        val pt = listOf("Por que o céu é azul?", "Quantos ossos tem o corpo humano?", "O que causa as marés?", "Quem foi Santos Dumont?", "e por quê?", "quanto custa?",
            "Capital da Austrália?", "Me explica a fotossíntese", "tem vacina pra dengue?", "qual a altura do Everest", "Napoleão", "como fazer pão", "isso é perigoso?",
            "e no Brasil?", "Febre de 39 graus, o que faço?", "Água do mar é salgada por quê", "Quem descobriu o Brasil?", "Diferença entre vírus e bactéria",
            "melhor horário pra correr", "obrigado!")
        val en = listOf("Why is the sky blue?", "How many bones are in the human body?", "What causes tides?", "Who was Napoleon?", "and why?", "how much?",
            "Capital of Australia?", "Explain photosynthesis", "is there a dengue vaccine?", "Everest height", "Napoleon", "how to bake bread", "is it dangerous?",
            "and in Brazil?", "Fever of 39, what should I do?", "Why is seawater salty", "Who discovered Brazil?", "Virus vs bacteria", "best time to run", "thanks!")
        val out = StringBuilder("| question | want | clear() | of() with app=en | of() with app=pt |\n|---|---|---|---|---|\n")
        var miss = 0
        for ((list, want) in listOf(pt to "PT", en to "EN")) for (q in list) {
            val c = AnswerLanguage.clear(q)?.name ?: "?"
            val a = AnswerLanguage.of(q, emptyList(), "en").name; val b = AnswerLanguage.of(q, emptyList(), "pt").name
            if (c != want) miss++
            out.append("| $q | $want | $c | $a | $b |\n")
        }
        out.append("\nclear() misses: $miss of 40 (\"?\" = can't tell, the conversation then the app setting decide)\n")
        java.io.File("build/lang-detect.md").writeText(out.toString())
    }
}
