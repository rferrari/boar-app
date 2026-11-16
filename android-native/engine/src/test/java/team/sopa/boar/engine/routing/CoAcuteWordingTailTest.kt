package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Test

/** Validator Q-A1 and Q-A2 (t9 0115): the acute CO card's remaining misses, and its over-triggers. */
class CoAcuteWordingTailTest {
    private fun lang(q: String) = CoHazard.acuteLanguage(q)

    @Test fun `PT and ES past tense 'I felt ill' is a symptom`() {
        assertEquals("pt", lang("a caldeira estava ligada e me senti mal"))
        assertEquals("pt", lang("senti-me mal depois de ligar o aquecedor a gás"))
        assertEquals("es", lang("la estufa de leña estuvo encendida y me sentí mal"))
    }

    @Test fun `Spanish 'horno de gas' is a device`() {
        assertEquals("es", lang("tengo el horno de gas encendido y me duele la cabeza"))
    }

    @Test fun `the wording tail`() {
        for (q in listOf(
            "the furnace was on all night and I'm woozy",
            "my head is spinning and the generator is in the garage",
            "the coal stove was burning all night and I have a headache",
            "we had a fire pit in the garage and now everyone is dizzy",
            "camp stove inside the cabin and I feel sick to my stomach",
            "my carbon monoxide alarm keeps going",
            "ALARM ALARM, we feel awful",
            "the gas heater was on and I have flu-like symptoms",
        )) assertEquals(q, "en", lang(q))
        assertEquals("es", lang("el detector no deja de sonar y estoy mareado"))
        assertEquals("es", lang("mi hijo está muy dormido y la estufa a gas está encendida"))
    }

    @Test fun `no card where a person wouldn't give one (Q-A2)`() {
        for (q in listOf(
            "my gas stove is great, I feel sick of cooking",
            "the boiler broke and I have a headache from the bill",
            "I felt sick after the boiler repair bill",
            "CO2 alarm beeping and I feel sick",
        )) assertEquals(q, null, lang(q))
        // Still a card: a bill named elsewhere doesn't hide a real exposure with carbon monoxide named.
        assertEquals("en", lang("carbon monoxide alarm going off and I have a headache, we just paid the boiler bill"))
    }
}
