package team.sopa.boar.engine.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.1.0 (BOAR-81): Evaluation is reached from the Model Catalog, a card under "Discover GGUF models"; back returns there. */
class EvaluationCardTest {
    private val models = java.io.File("../app/src/main/java/team/sopa/boar/ui/models/ModelsScreen.kt").readText()
    private val main = java.io.File("../app/src/main/java/team/sopa/boar/MainActivity.kt").readText()

    @Test fun `the catalog has the card right under Discover GGUF models, same look`() {
        val list = models.substringAfter("fun ModelsScreen(").substringBefore("\n}\n")
        val discover = list.indexOf("item { HfCta(onBrowse) }")
        assertTrue(discover >= 0 && list.indexOf("item { EvaluationCard(onEvaluation) }") == list.indexOf("\n", discover) + 1 + "            ".length)
        val card = models.substringAfter("fun EvaluationCard(").substringBefore("\n}\n")
        assertTrue(card.contains("t(\"native.models.evaluationTitle\")") && card.contains("t(\"native.models.evaluationSubtitle\")") && card.contains("dashPathEffect"))
        for ((d, title, sub) in listOf(Triple("values", "Evaluation (for builders)", "Test models on this phone and find the setup that fits it."),
                Triple("values-pt", "Avaliação (para desenvolvedores)", "Teste modelos neste celular e encontre o ajuste que serve para ele."))) {
            val x = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
            assertTrue(d, x.contains(">$title<") && x.contains(">$sub<"))
        }
    }

    @Test fun `opened from the catalog, back returns to the catalog, from the menu to the chat`() {
        assertTrue(main.contains("var evalFrom by rememberSaveable { mutableStateOf(\"chat\") }"))
        assertTrue(main.contains("onEvaluation = { evalFrom = \"models\"; screen = \"evaluation\" }"))
        assertTrue(main.contains("BackHandler { if (!evalState.running) { vm.refreshModel(); screen = evalFrom } }"))
        assertTrue(main.contains("{ go { evalFrom = \"chat\"; screen = \"evaluation\" } }"))
    }
}
