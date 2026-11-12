package team.sopa.boar.engine.labs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PluginAnswersTest {
    @Test fun `moon questions about now are taken, history and science are not`() {
        for (q in listOf("what phase is the moon tonight?", "when is the next full moon", "is the moon waxing or waning now?", "qual a fase da lua hoje?", "quando é a próxima lua cheia?"))
            assertTrue(q, PluginAnswers.isMoonQuestion(q))
        for (q in listOf("who was the first man on the moon", "Apollo 11 moon landing", "how far is the moon", "why does the moon have phases in physics terms gravity"))
            assertFalse(q, PluginAnswers.isMoonQuestion(q))
    }

    @Test fun `the moon answer gives the phase, the lit share and the next full and new moon, EN and PT`() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = 1_762_819_200_000L // 2025-11-11
        val en = PluginAnswers.moonAnswer("what phase is the moon tonight?", now, utc)
        assertTrue(en, en.contains("% lit") && en.contains("Next full moon:") && en.contains("Next new moon:"))
        val pt = PluginAnswers.moonAnswer("qual a fase da lua hoje?", now, utc)
        assertTrue(pt, pt.contains("% iluminada") && pt.contains("Próxima lua cheia:"))
    }

    @Test fun `direction questions are taken, geography is not`() {
        for (q in listOf("which way is north?", "where is north", "which direction am I facing", "onde fica o norte?", "para que lado estou virado?", "para onde estou virado?"))
            assertTrue(q, PluginAnswers.isDirectionQuestion(q))
        for (q in listOf("what is north of France", "the north pole", "history of West Germany")) assertFalse(q, PluginAnswers.isDirectionQuestion(q))
    }

    @Test fun `the direction answer says where north is, and to hold the phone flat when it's tilted`() {
        val flat = PluginAnswers.directionAnswer("which way is north?", 90f, 5f)
        assertTrue(flat, flat.contains("points east (90°)") && flat.contains("North is 90° to your left") && !flat.contains("Hold the phone flat"))
        val tilted = PluginAnswers.directionAnswer("which way is north?", 10f, 30f)
        assertTrue(tilted, tilted.contains("Hold the phone flat") && tilted.contains("North is 10° to your left"))
        assertTrue(PluginAnswers.directionAnswer("onde fica o norte?", 270f, 0f).contains("O norte fica a 90° à direita"))
        assertTrue(PluginAnswers.directionAnswer("which way is north?", null, null).contains("no compass"))
    }

    @Test fun `phone questions are taken, phones in general are not`() {
        for (q in listOf("how hot is my phone?", "what's my phone's battery level", "how much memory does this phone have free", "qual a temperatura do meu celular?", "quanta bateria tem o celular?"))
            assertTrue(q, PluginAnswers.isPhoneQuestion(q))
        for (q in listOf("who invented the phone", "how does a phone battery work in general chemistry")) assertFalse(q, PluginAnswers.isPhoneQuestion(q))
    }

    @Test fun `the phone answer reads what it has`() {
        val a = PluginAnswers.phoneAnswer("how hot is my phone?", PluginAnswers.PhoneNow(81, true, 39.4, 4300, 11400, 8))
        assertTrue(a, a.contains("Battery at 81%, charging") && a.contains("39.4 °C (warm)") && a.contains("4.2 GB of 11.1 GB memory free") && a.contains("8 processor cores"))
        val pt = PluginAnswers.phoneAnswer("qual a temperatura do meu celular?", PluginAnswers.PhoneNow(50, false, 30.0, null, null, null))
        assertTrue(pt, pt.contains("30,0 °C (fria)"))
    }

    @Test fun `natural phrasings are taken too, the history guard stays`() {
        for (q in listOf("where's the north?", "where’s the north?", "whats north", "which way's north?", "where's north"))
            assertTrue(q, PluginAnswers.isDirectionQuestion(q))
        assertFalse(PluginAnswers.isDirectionQuestion("what's north of France?"))
        for (q in listOf("moon tonight", "whats the moon like", "what's the moon doing", "moon phase", "como está a lua?"))
            assertTrue(q, PluginAnswers.isMoonQuestion(q))
        for (q in listOf("moon landing tonight documentary", "apollo moon phase mission", "moon distance today"))
            assertFalse(q, PluginAnswers.isMoonQuestion(q))
        for (q in listOf("my battery", "how hot is my phone", "battery level?", "minha bateria"))
            assertTrue(q, PluginAnswers.isPhoneQuestion(q))
    }

    @Test fun `a place named after a cardinal is not a heading (validator P-1)`() {
        for (q in listOf("Where is North Carolina?", "where's South Dakota", "What's North Korea's capital", "what's West Virginia known for",
            "where is East Timor", "whats South Africa", "find North Dakota", "onde fica o Sul do Brasil", "onde fica o Norte Shopping", "where is the north pole"))
            assertFalse(q, PluginAnswers.isDirectionQuestion(q))
        for (q in listOf("which way is north from here?", "where is north right now", "find north", "onde fica o norte daqui?", "where's the north."))
            assertTrue(q, PluginAnswers.isDirectionQuestion(q))
    }

    @Test fun `a show, an eclipse or a holiday is not the moon now (validator P-2)`() {
        for (q in listOf("what time does Moon Knight air today", "is there a lunar eclipse tonight", "Sailor Moon episodes today",
            "when is the lunar new year this month", "lua de mel hoje"))
            assertFalse(q, PluginAnswers.isMoonQuestion(q))
        for (q in listOf("Moon phase today", "lunar phase today", "what's the moon like tonight?")) assertTrue(q, PluginAnswers.isMoonQuestion(q))
    }

    @Test fun `advice about a phone goes to the model (validator P-3)`() {
        for (q in listOf("how do I charge this phone faster", "what battery should I buy for my phone", "how does the device driver manage memory",
            "how can I make my battery last longer", "como faço meu celular esquentar menos?"))
            assertFalse(q, PluginAnswers.isPhoneQuestion(q))
        for (q in listOf("is my phone charging?", "how much battery does my phone have", "is this phone too hot?")) assertTrue(q, PluginAnswers.isPhoneQuestion(q))
    }
}
