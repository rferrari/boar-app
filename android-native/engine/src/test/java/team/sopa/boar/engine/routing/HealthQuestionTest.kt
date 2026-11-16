package team.sopa.boar.engine.routing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Health questions answer in Succinct even when Detailed is picked: which questions count (QueryContext, r4to's list). */
class HealthQuestionTest {
    @Test fun firstAidAndInjuriesAreHealth() {
        listOf(
            "When should a burn see a doctor?",
            "How do I treat a minor kitchen burn?",
            "My hiking partner is shivering, confused and slurring words",
            "I was stung by a box jellyfish while swimming. Should I rinse it?",
            "My child spilled boiling water on their arm. What do I do?",
            "Meu amigo está tremendo, confuso e com a fala enrolada",
            "Como estancar um sangramento no nariz?",
        ).forEach { assertTrue(it, QueryContext.isHealthQuestion(it)) }
    }

    @Test fun researchQuestionsAreNot() {
        listOf(
            "What are the main sights to see in Lisbon?",
            "What is the difference between optimistic and zk-rollups?",
            "What is a seed phrase in a crypto wallet?",
            "Which countries are bigger than India by area?",
        ).forEach { assertFalse(it, QueryContext.isHealthQuestion(it)) }
    }

    @Test fun `a medicine's dose is a health question, kept short (t9 0110 item 3)`() {
        for (q in listOf("How much paracetamol (acetaminophen) can an adult take in a day?", "ibuprofen dose for adults", "Is 1000 mg of Tylenol too much?",
            "Quantos mg de dipirona posso tomar?", "Qual a dosagem máxima de paracetamol por dia?", "¿Cuánto ibuprofeno puedo tomar al día?",
            "What is the maximum daily dose of aspirin?")) {
            assertTrue(q, QueryContext.isHealthQuestion(q))
            assertTrue(q, ShortAnswer.reason("detailed", q, null) == "health")
        }
        // A medicine named without a dose word, or a dose word without a medicine, is not this rule.
        for (q in listOf("Who invented aspirin?", "How much does a car cost?", "What is the history of ibuprofen?"))
            assertFalse(q, QueryContext.isHealthQuestion(q))
    }

    @Test fun `carbon monoxide named is a health question, kept short, card or not (t9 0110 item 1)`() {
        for (q in listOf("My CO alarm keeps chirping every minute", "my CO detector says error", "Is 50 ppm of carbon monoxide dangerous?",
            "O detector de monóxido mostra erro")) assertTrue(q, ShortAnswer.reason("detailed", q, null) == "health")
        assertFalse(QueryContext.isHealthQuestion("CO2 levels in the office are high"))
        assertFalse(QueryContext.isHealthQuestion("Who is the CO of the company?"))
    }

    @Test fun `the health topic runs with Cohazard on, as users get it`() {
        val t = java.io.File("../app/src/main/assets/boar-data/topics/health.v1.json").readText()
        assertTrue(t.contains("\"labs\": [\n    \"cohazard\"\n  ]"))
    }

    @Test fun `lab results are health, and a Local Files health answer is kept short with its own prompt (t9 2200 c)`() {
        for (q in listOf("hows health blood test? summarize", "summarize my blood test", "resuma meu exame de sangue", "what does my LDL mean?", "meu colesterol está alto?"))
            assertTrue(q, QueryContext.isHealthQuestion(q))
        assertTrue(ShortAnswer.reason("personal", "summarize my blood test", null) == "health")
        assertTrue(ShortAnswer.reason("personal", "what did my landlord say?", null) == null)
        val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
        assertTrue(vm.contains("val style = if (shortReason != null && !personal) \"succinct\" else chosenStyle"))
        assertTrue(vm.contains("team.sopa.boar.engine.routing.ShortAnswer.PERSONAL_SHORT.takeIf { personal && shortReason != null }"))
    }
}
