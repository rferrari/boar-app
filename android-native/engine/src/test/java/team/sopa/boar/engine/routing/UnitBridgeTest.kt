package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitBridgeTest {
    @Test fun `miles at mph asked in km gives the time and the distance in km`() {
        val a = CalcShortcut.answer("how long to go 120 miles in km, speed in miles per hour 60 mph")!!
        assertEquals("calculator-rate-time", a.code)
        assertTrue(a.text, a.text.contains("2 hours") && a.text.contains("120 miles = 193.1 km"))
        val b = CalcShortcut.answer("how long does it take to drive 120 miles at 60 mph, answer in km")!!
        assertTrue(b.text, b.text.contains("2 hours") && b.text.contains("193.1 km"))
    }

    @Test fun `km at mph is converted to miles first`() {
        val a = CalcShortcut.answer("how many hours to travel 200 km at 50 mph?")!!
        assertTrue(a.text, a.text.contains("2.5 hours") || a.text.contains("2 hours 29") || a.text.contains("2.49"))
        assertTrue(a.text, a.text.contains("200 km = 124.3 miles"))
    }

    @Test fun `miles at km per hour is converted to km`() {
        val a = CalcShortcut.answer("how long to drive 62 miles at 100 km/h?")
        assertTrue(a?.text ?: "null", a != null && a.text.contains("62 miles = 99.8 km"))
    }

    @Test fun `one unit system is left as it was`() {
        assertEquals(UnitBridge.Prepared("how long to drive 120 miles at 60 mph", null), UnitBridge.prepare("how long to drive 120 miles at 60 mph"))
        assertEquals("About 2 hours: 120 miles ÷ 60 miles per hour = 2 hours. Computed from the numbers in your question.",
            CalcShortcut.answer("how long to drive 120 miles at 60 mph")!!.text)
    }
}
