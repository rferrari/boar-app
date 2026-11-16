package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Test

/** Boar Nomad's compass arithmetic. */
class CompassTest {
    @Test fun `eight directions, each centered on its own`() {
        val cases = mapOf(0 to "n", 22 to "n", 23 to "ne", 45 to "ne", 90 to "e", 135 to "se", 180 to "s", 225 to "sw", 270 to "w", 315 to "nw", 337 to "nw", 338 to "n", 359 to "n", 360 to "n", -10 to "n")
        for ((deg, dir) in cases) assertEquals("$deg°", dir, Compass.cardinal(deg))
    }

    @Test fun `azimuth in radians becomes a 0 to 360 heading`() {
        assertEquals(0f, Compass.headingOf(0.0), 0.01f)
        assertEquals(90f, Compass.headingOf(Math.PI / 2), 0.01f)
        assertEquals(270f, Compass.headingOf(-Math.PI / 2), 0.01f)
        assertEquals(180f, Compass.headingOf(Math.PI), 0.01f)
    }

    @Test fun `turns go the short way round`() {
        assertEquals(2f, Compass.turn(359f, 1f), 0.001f)
        assertEquals(-2f, Compass.turn(1f, 359f), 0.001f)
        assertEquals(90f, Compass.turn(0f, 90f), 0.001f)
    }

    @Test fun `smoothing crosses north without swinging through south`() {
        val next = Compass.smooth(359f, 1f, first = false, k = 0.5f)
        assertEquals(0f, next, 0.001f)
        assertEquals(1f, Compass.smooth(359f, 1f, first = true, k = 0.5f), 0.001f)
        assertEquals(355f, Compass.smooth(350f, 0f, first = false, k = 0.5f), 0.001f)
    }

    @Test fun `the shown degree holds until the heading is a full degree away`() {
        assertEquals(10, Compass.shown(10, 10.6f))
        assertEquals(12, Compass.shown(10, 11.6f))
        assertEquals(0, Compass.shown(359, 0.2f))
        assertEquals(359, Compass.shown(359, 359.4f))
    }

    @Test fun `low pass steps toward each new value`() {
        assertEquals(4f, Compass.lowPass(0f, 4f, first = true, k = 0.25f), 0.001f)
        assertEquals(1f, Compass.lowPass(0f, 4f, first = false, k = 0.25f), 0.001f)
    }
}
