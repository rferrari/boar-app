package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentPluginsTest {
    private val active = listOf("pulse", "image", "printer", "calc", "facts", "canvas", "staged")

    @Test fun `the last used come first, only plugins that are on, at most three`() {
        assertEquals(listOf("canvas", "printer", "pulse"), RecentPlugins.pick(active, listOf("canvas", "lunar", "printer")))
        assertEquals(3, RecentPlugins.pick(active, emptyList()).size)
        // Fewer used: the others that are on fill it, in list order.
        assertEquals(listOf("facts", "pulse", "image"), RecentPlugins.pick(active, listOf("facts")))
    }

    @Test fun `using a plugin moves it to the front, ten kept`() {
        val r = (1..10).map { "p$it" }
        assertEquals(listOf("p5", "p1", "p2", "p3", "p4", "p6", "p7", "p8", "p9", "p10"), RecentPlugins.used(r, "p5"))
        assertEquals(10, RecentPlugins.used(r, "new").size)
        assertEquals("new", RecentPlugins.used(r, "new").first())
    }

    @Test fun `search finds a plugin by its name or its line, accents and case ignored`() {
        assertTrue(RecentPlugins.matches("calc", "Calculadora", "Contas do dia a dia"))
        assertTrue(RecentPlugins.matches("distancia", "Distância", "a distância entre duas cidades"))
        assertTrue(RecentPlugins.matches("PDF basket", "Press", "Collect answers into a basket and compile them into a PDF"))
        assertFalse(RecentPlugins.matches("moon", "Press", "Collect answers"))
        assertTrue(RecentPlugins.matches("  ", "anything"))
    }
}
