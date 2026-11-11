package team.sopa.boar.engine.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ChatListTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_762_871_400_000L // 2025-11-11 14:30 UTC
    private val hour = 3_600_000L

    @Test fun `today, yesterday and earlier by the calendar day, not 24 hours`() {
        assertEquals(ChatList.Group.TODAY, ChatList.groupOf(now - 14 * hour, now, utc))
        assertEquals(ChatList.Group.YESTERDAY, ChatList.groupOf(now - 15 * hour, now, utc))
        assertEquals(ChatList.Group.YESTERDAY, ChatList.groupOf(now - 38 * hour, now, utc))
        assertEquals(ChatList.Group.EARLIER, ChatList.groupOf(now - 39 * hour, now, utc))
    }

    @Test fun `search by the title's words, accents and case ignored`() {
        assertTrue(ChatList.matches("brasilia", "Por que Brasília foi construída"))
        assertTrue(ChatList.matches("napoleon WARS", "Napoleon: his rise, his wars"))
        assertFalse(ChatList.matches("moon", "Napoleon"))
        assertTrue(ChatList.matches("", "anything"))
    }
}
