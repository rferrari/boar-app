package team.sopa.boar.engine.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptQueueTest {
    @Test fun `questions run in the order they were sent`() {
        val q = PromptQueue()
        q.add("one"); q.add("two"); q.add("three")
        assertEquals(listOf("one", "two", "three"), generateSequence { q.next()?.text }.toList())
        assertNull(q.next())
    }

    @Test fun `at most five wait, a blank one never`() {
        val q = PromptQueue()
        repeat(5) { assertTrue(q.add("q$it")) }
        assertFalse(q.add("sixth"))
        assertFalse(PromptQueue().add("   "))
        assertEquals(5, q.size)
    }

    @Test fun `one can be removed, and Clear queue empties it`() {
        val q = PromptQueue()
        q.add("a"); q.add("b"); q.add("c")
        assertTrue(q.remove(q.waiting[1].id))
        assertEquals(listOf("a", "c"), q.waiting.map { it.text })
        q.clear()
        assertTrue(q.isEmpty())
    }

    @Test fun `a removed slot frees room for another`() {
        val q = PromptQueue(limit = 2)
        q.add("a"); q.add("b")
        assertFalse(q.add("c"))
        q.next()
        assertTrue(q.add("c"))
        assertEquals(listOf("b", "c"), q.waiting.map { it.text })
    }
}
