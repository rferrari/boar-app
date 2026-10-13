package team.sopa.boar.engine.vault

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class VaultTest {
    private val today = LocalDate.of(2026, 10, 13)

    @Test fun `every icon in the art roster has a rule, and every rule an icon`() {
        val roster = Json.parseToJsonElement(File("../art/icons/truffles.json").readText()).jsonObject["icons"]!!.jsonArray
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(roster, Vault.RULES.keys.toList())
    }

    @Test fun `a fresh install has only the default truffle`() {
        val (state, fresh) = Vault.apply(Vault.State(), Vault.Facts(), today, 1L)
        assertEquals(listOf("tactical"), fresh)
        assertEquals(mapOf("tactical" to 1L), state.unlocked)
    }

    @Test fun `thresholds unlock once and keep their first date`() {
        var (state, _) = Vault.apply(Vault.State(), Vault.Facts(), today, 1L)
        val first = Vault.apply(state, Vault.Facts(answers = 10), today, 2L)
        assertEquals(listOf("cosmo", "solarpunk"), first.second)
        state = first.first
        val again = Vault.apply(state, Vault.Facts(answers = 11), today, 3L)
        assertTrue(again.second.isEmpty())
        assertEquals(2L, again.first.unlocked["cosmo"])
        assertFalse("planter" in again.first.unlocked)
    }

    @Test fun `quantum needs more than 15 tok per s, volcanic 8`() {
        assertFalse(Vault.Rule.FAST_15.test(Vault.Facts(bestTokPerSec = 15.0), today))
        assertTrue(Vault.Rule.FAST_15.test(Vault.Facts(bestTokPerSec = 15.1), today))
        assertTrue(Vault.Rule.FAST_8.test(Vault.Facts(bestTokPerSec = 8.0), today))
    }

    @Test fun `an airplane streak counts days in a row ending today or yesterday`() {
        val d = { s: String -> LocalDate.parse(s) }
        var days = emptyList<String>()
        for (s in listOf("2026-10-07", "2026-10-08", "2026-10-09", "2026-10-11", "2026-10-12")) days = Vault.Days.add(days, d(s))
        assertEquals(2, Vault.Days.streak(days, today)) // 11th and 12th; today not opened yet
        assertEquals(3, Vault.Days.streak(Vault.Days.add(days, today), today))
        assertEquals(0, Vault.Days.streak(days, d("2026-10-15")))
        assertFalse(Vault.Rule.AIRPLANE_STREAK_3.test(Vault.Facts(airplaneDays = days), today))
        assertTrue(Vault.Rule.AIRPLANE_STREAK_3.test(Vault.Facts(airplaneDays = Vault.Days.add(days, today)), today))
    }

    @Test fun `day lists stay distinct, sorted and bounded`() {
        var days = emptyList<String>()
        repeat(3) { days = Vault.Days.add(days, today) }
        assertEquals(listOf("2026-10-13"), days)
        for (i in 1..100) days = Vault.Days.add(days, today.minusDays(i.toLong()))
        assertEquals(Vault.Days.KEEP, days.size)
        assertEquals("2026-10-13", days.last())
    }

    @Test fun `night is 02 00 to 03 59`() {
        assertEquals(listOf(2, 3), (0..23).filter(Vault::isNight))
    }

    @Test fun `state round-trips through JSON and survives junk`() {
        val s = Vault.State(Vault.Facts(answers = 3, usedDays = listOf("2026-10-13")), mapOf("cosmo" to 5L), mapOf("hacker_green" to 7L))
        assertEquals(s, Vault.fromJson(Vault.toJson(s)))
        assertEquals(Vault.State(), Vault.fromJson("not json"))
        assertEquals(Vault.State(), Vault.fromJson(null))
    }
}
