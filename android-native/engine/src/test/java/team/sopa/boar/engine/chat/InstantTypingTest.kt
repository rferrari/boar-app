package team.sopa.boar.engine.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantTypingTest {
    @Test fun `typed at about 80 characters a second, the whole text, within six seconds`() {
        val text = "About 2 hours: 120 miles ÷ 60 miles per hour = 2 hours. Computed from the numbers in your question."
        val p = InstantTyping.pieces(text)
        assertEquals(text, p.joinToString(""))
        val seconds = p.size * InstantTyping.STEP_MS / 1000.0
        assertTrue("$seconds", seconds in 1.0..1.5)
        assertTrue(InstantTyping.pieces("x".repeat(5000)).size * InstantTyping.STEP_MS <= InstantTyping.MAX_MS)
    }

    @Test fun `the safety answer, animations off and a screen reader show it at once`() {
        assertFalse(InstantTyping.typed("safety-fact-co-hazard", false, false))
        assertFalse(InstantTyping.typed("calculator-rate-time", true, false))
        assertFalse(InstantTyping.typed("calculator-rate-time", false, true))
        assertTrue(InstantTyping.typed("lunar-phase", false, false))
    }

    @Test fun `the chat types it, a tap skips, the receipt keeps the real time and says instant`() {
        val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
        assertTrue(vm.contains("for (piece in team.sopa.boar.engine.chat.InstantTyping.pieces(instant.text))") && vm.contains("if (skipTyping || stopRequested) { onPiece(rest(instant.text, shownLen))"))
        assertTrue(vm.contains("Receipt(totalMs = ((instantAt.takeIf { it > 0 } ?: System.nanoTime()) - startAt) / 1e6"))
        val screen = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        // adam, Nov 13: the short line is the time only; "instant" is the opened receipt's Speed row; no typing-speed note.
        assertTrue(!screen.contains("native.receipt.instantTyped") && screen.contains("private fun receiptRate(r: Receipt): String? = if (r.instant()) null"))
        assertTrue(screen.contains("t(\"chat.receipt.speedLabel\") to (if (r.instant()) t(\"native.receipt.instant\")"))
    }
}
