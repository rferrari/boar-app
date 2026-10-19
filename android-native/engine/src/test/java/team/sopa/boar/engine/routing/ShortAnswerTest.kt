package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortAnswerTest {
    @Test fun `health and seed questions under Detailed answer short`() {
        assertEquals("health", ShortAnswer.reason("detailed", "How do I treat a minor kitchen burn?", null))
        assertEquals("seed", ShortAnswer.reason("detailed", "How should I store my seed phrase?", null))
        assertNull(ShortAnswer.reason("detailed", "Why is Kyoto historically important?", null))
    }

    @Test fun `a follow-up in a health or seed chat stays short`() {
        assertEquals("health", ShortAnswer.reason("detailed", "And for how long?", "How do I treat a minor kitchen burn?"))
        assertEquals("seed", ShortAnswer.reason("detailed", "What about a second copy?", "How should I store my seed phrase?"))
        assertNull(ShortAnswer.reason("detailed", "And for how long?", "Why is Kyoto historically important?"))
    }

    @Test fun `a thank-you is small talk, even after a health question`() {
        assertEquals(ShortAnswer.SMALL_TALK, ShortAnswer.reason("detailed", "thanks, that helps", "How do I treat a minor kitchen burn?"))
        assertEquals(ShortAnswer.SMALL_TALK, ShortAnswer.reason("detailed", "merci beaucoup", null))
        assertEquals(true, ShortAnswer.isSmallTalk("thank you so much!"))
        assertEquals(false, ShortAnswer.isSmallTalk("How do I say thank you in Thai?"))
    }

    @Test fun `other styles are left alone`() {
        assertNull(ShortAnswer.reason("succinct", "How do I treat a minor kitchen burn?", null))
        assertNull(ShortAnswer.reason("summary", "And for how long?", "How do I treat a minor kitchen burn?"))
    }
}
