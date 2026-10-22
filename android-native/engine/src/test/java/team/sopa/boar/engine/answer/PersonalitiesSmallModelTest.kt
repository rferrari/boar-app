package team.sopa.boar.engine.answer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PersonalitiesSmallModelTest {
    private val p = Personalities.parse(File("../app/src/main/assets/boar-data/personalities.json").readText())

    @Test fun `a small model gets Detailed without the do-what-the-user-asks sentence`() {
        val (small, smallReminder) = p.promptFor("detailed", null, smallModel = true)
        val (big, _) = p.promptFor("detailed", null, smallModel = false)
        assertFalse(small.contains("Always do what the user asks"))
        assertTrue(big.contains("Always do what the user asks"))
        assertTrue(smallReminder!!.contains("Leave out examples"))
    }

    @Test fun `styles without small-model texts are the same for every model`() {
        assertEquals(p.promptFor("succinct", null, smallModel = true), p.promptFor("succinct", null, smallModel = false))
        assertEquals("mine" to null, p.promptFor("custom", "mine", smallModel = true))
    }
}
