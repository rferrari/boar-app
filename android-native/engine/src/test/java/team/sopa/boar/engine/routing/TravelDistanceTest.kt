package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Ported from src/routing/travelDistance.test.ts in r4to's PR #85 (BOAR R&D, MIT), case for case. */
class TravelDistanceTest {
    companion object {
        // The table ships with the Boar Distance lab (app/src/labs/plugins/distance).
        val td = TravelDistance(File("../app/src/labs/plugins/distance/res/raw/lab_distance_world_cities_v1.json").readText())
    }

    @Test fun `answers distance, round trip and walking time, always as an estimate`() {
        val r = td.answer("How far is it to walk from Paris to Rome and back?")
        assertNotNull(r)
        assertEquals("walk", r!!.mode)
        assertTrue(r.roundTrip)
        assertTrue(r.straightKm > 1090 && r.straightKm < 1120)
        assertTrue(r.text.contains("estimate"))
        assertTrue(r.text.contains("there and back"))
        assertTrue(r.text.contains("days each way"))
    }

    @Test fun `covers the usual shapes, any city pair in the table`() {
        for (q in listOf(
            "how far is it from Lisbon to Madrid?",
            "How far is Kyoto from Tokyo?",
            "What's the distance between Madrid and Barcelona?",
            "distance from Oslo to Bergen",
            "how many km from Bangkok to Chiang Mai by car?",
            "How long would it take to walk from Berlin to Prague?",
            "how many days to bike from Vienna to Budapest and back",
            "How far is it to drive from Los Angeles to San Francisco?",
        )) {
            val r = td.answer(q)
            assertNotNull(q, r)
            assertTrue(q, r!!.text.contains("estimate"))
        }
    }

    @Test fun `a time question leads with the time`() {
        assertTrue(Regex("^About \\d+ days each way").containsMatchIn(td.answer("How long would it take to walk from Berlin to Prague?")!!.text))
    }

    @Test fun `driving and cycling only state the factor, never a mapped road distance`() {
        val d = td.answer("How far is it to drive from Los Angeles to San Francisco?")!!
        assertTrue(d.text.contains("usually about 25% longer"))
        assertTrue(d.text.contains("not a mapped route"))
    }

    @Test fun `falls back (null) when a place isn't a city in the table, or isn't a travel question`() {
        for (q in listOf(
            "How far is the Moon from the Earth?",
            "how far is it from my hotel to the beach?",
            "distance from Narnia to Mordor",
            "How far can a person walk in a day?",
            "How far is it to walk from Paris to Paris?",
            "Why is Rome far from Paris culturally?",
        )) assertNull(q, td.answer(q))
    }

    @Test fun `resolves the most populous city of a name, with or without a country`() {
        assertEquals("FR", td.lookupCity("Paris")!!.country)
        assertEquals("FR", td.lookupCity("Paris, France")!!.country)
        assertEquals(111, Math.round(TravelDistance.greatCircleKm(0.0, 0.0, 0.0, 1.0)).toInt())
    }
}
