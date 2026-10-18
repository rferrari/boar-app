package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Boar Cohazard in Portuguese and Spanish (draft answers, CoHazard's PT and ES patterns), through LabTools.coHazard as
 * the lab runs it: the CO planning questions fire and answer in the question's language; symptoms, alarms and other
 * questions don't; the English answer is unchanged.
 */
class CoHazardPtEsTest {
    private val pt = listOf(
        "Posso usar aquecedor a gás dentro da barraca?",
        "gerador na garagem fechada",
        "Posso ligar o gerador na garagem com a porta aberta?",
        "churrasqueira dentro de casa",
        "Posso usar a churrasqueira a carvão dentro de casa?",
        "fogareiro na barraca",
        "Posso usar o fogareiro dentro da barraca para cozinhar?",
        "É seguro acender a churrasqueira na varanda fechada?",
        "Dá monóxido de carbono usar o fogareiro dentro da barraca?",
        "Acabou a luz. Posso deixar o gerador no porão com a janela aberta?",
        "Posso esquentar o carro na garagem com o portão fechado?",
        "Posso usar o forno a gás para aquecer a casa?",
        "O gerador a 3 metros da janela está bom?",
        "Podemos levar o aquecedor a querosene para o trailer durante a noite?",
        "Tudo bem usar carvão no quarto pra esquentar?",
    )
    private val es = listOf(
        "¿Puedo usar un generador en el garaje?",
        "brasero dentro de la tienda",
        "¿Puedo usar un brasero de carbón dentro de casa?",
        "¿Es seguro usar una estufa de butano dentro de la carpa?",
        "¿Puedo encender la barbacoa en el garaje si dejo la puerta abierta?",
        "¿Puedo dejar el generador en el sótano toda la noche?",
        "¿Puedo calentar el coche en el garaje con la puerta cerrada?",
        "¿Puedo usar el horno de gas para calentar la casa?",
        "¿El monóxido de carbón del hornillo dentro de la tienda es peligroso?",
        "¿A qué distancia de la ventana tiene que estar el generador?",
    )

    @Test fun `Portuguese and Spanish CO planning questions fire, in their language`() {
        for (q in pt) {
            val hit = LabTools.coHazard(q)
            assertTrue(q, hit != null && CoHazard.CO_HAZARD_PT in hit.text && CoHazard.CO_HAZARD_SOURCES_PT in hit.text)
            assertEquals(q, "safety-fact-co-hazard", hit!!.tool)
        }
        for (q in es) {
            val hit = LabTools.coHazard(q)
            assertTrue(q, hit != null && CoHazard.CO_HAZARD_ES in hit.text && CoHazard.CO_HAZARD_SOURCES_ES in hit.text)
        }
    }

    @Test fun `symptoms, alarms and other questions in Portuguese and Spanish don't`() {
        for (q in listOf(
            "estou com dor de cabeça perto do gerador",
            "Liguei o gerador na garagem e agora estou com tontura, o que faço?",
            "O alarme de monóxido está apitando no porão, o que eu faço?",
            "Usamos o fogareiro na barraca e meu filho está vomitando",
            "me duele la cabeza, el generador está en el garaje",
            "La alarma de CO está sonando en el sótano, ¿qué hago?",
            "qual a capital do Quênia",
            "Qual é a distância de Paris a Roma?",
            "¿Cuál es la capital de Kenia?",
            "Como funciona um gerador de senhas?",
            "¿Qué es un generador de contraseñas en la casa?",
            "Posso deixar o carro na garagem?",
            "Posso usar um aquecedor elétrico dentro da barraca?",
            "Como guardar o gerador na garagem para o inverno?",
            "Quanto custa um gerador?",
            "Onde compro carvão ativado dentro do shopping?",
            "¿Puedo comprar un generador en la tienda?",
            "O que é monóxido de carbono?",
        )) assertNull(q, LabTools.coHazard(q))
    }

    @Test fun `starts with No only when asked whether to do it`() {
        assertTrue(LabTools.coHazard("Posso usar aquecedor a gás dentro da barraca?")!!.text.startsWith("Não. "))
        assertTrue(LabTools.coHazard("gerador na garagem fechada")!!.text.startsWith(CoHazard.CO_HAZARD_PT))
        assertTrue(LabTools.coHazard("É perigoso usar o gerador na garagem?")!!.text.startsWith(CoHazard.CO_HAZARD_PT))
        assertTrue(LabTools.coHazard("¿Puedo usar un generador en el garaje?")!!.text.startsWith("No. ${CoHazard.CO_HAZARD_ES}"))
        assertTrue(LabTools.coHazard("brasero dentro de la tienda")!!.text.startsWith(CoHazard.CO_HAZARD_ES))
        assertTrue(LabTools.coHazard("O gerador a 7 metros da janela está bom?") == null || !LabTools.coHazard("O gerador a 7 metros da janela está bom?")!!.text.startsWith("Não"))
    }

    @Test fun `the emergency line follows the language`() {
        assertTrue(LabTools.coHazard("Posso usar o gerador na garagem aqui no Brasil?")!!.text.endsWith("Emergência — Brasil: polícia 190, ambulância (SAMU) 192, bombeiros 193."))
        assertTrue(LabTools.coHazard("gerador na garagem fechada")!!.text.endsWith("(192 SAMU, 193 Bombeiros no Brasil, 112 na Europa, 911 nos EUA)."))
        assertTrue(LabTools.coHazard("brasero dentro de la tienda")!!.text.endsWith("(911 en EE. UU., 112 en Europa)."))
    }

    @Test fun `the English answer is unchanged`() {
        val q = "Can I run my portable generator in the attached garage if I keep the door open?"
        assertEquals(CoHazard.coHazardAnswer(q), LabTools.coHazard(q)!!.text)
        assertTrue(LabTools.coHazard(q)!!.text.startsWith("No. Carbon monoxide (CO) from a generator"))
        assertEquals(CoHazard.coHazardAnswer(q), CoHazard.coHazardAnswer(q, "en"))
    }
}
