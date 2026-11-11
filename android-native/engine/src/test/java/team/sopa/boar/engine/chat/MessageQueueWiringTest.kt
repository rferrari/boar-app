package team.sopa.boar.engine.chat

import org.junit.Assert.assertTrue
import org.junit.Test

/** The chat's side of PromptQueue (t9, Nov 10): read from the source, as the other app-wiring tests do. */
class MessageQueueWiringTest {
    private val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
    private val screen = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()

    @Test fun `a question sent while answering waits, at most five`() {
        assertTrue(vm.contains("if (job?.isActive == true) {\n            if (queue.add(q)) publishQueue() else showToast(\"native.chat.queueFull\")"))
        assertTrue(vm.contains("private val queue = team.sopa.boar.engine.chat.PromptQueue()"))
    }

    @Test fun `the next one starts however the answer ended, stopped or failed included`() {
        // invokeOnCompletion runs on done, Stop (cancel) and failure alike; Stop doesn't touch the queue.
        val done = vm.substringAfter("job?.invokeOnCompletion {").substringBefore("private val queue")
        assertTrue(done.contains("val next = queue.next()") && done.contains("send(next.text, null, fromQueue = true)"))
        assertTrue(!vm.substringAfter("fun stop() {").substringBefore("\n    }").contains("queue"))
    }

    @Test fun `the notification stays up between queued answers and goes with the last`() {
        assertTrue(vm.contains("if (next == null) team.sopa.boar.app.AnswerService.stop(getApplication())"))
        assertTrue(vm.contains("if (!fromQueue) team.sopa.boar.app.AnswerService.start(getApplication())"))
    }

    @Test fun `deleting the open chat, or the PIN's erase, drops its queue`() {
        assertTrue(vm.contains("if (_state.value.sessionId == id) { queue.clear(); publishQueue(); newChat() }"))
        assertTrue(vm.contains("queue.clear(); publishQueue()\n                job?.cancel()"))
    }

    @Test fun `a closed app says what it dropped`() {
        assertTrue(vm.contains("\"native.chat.queueDropped\""))
    }

    @Test fun `Send queues while answering, Stop only with nothing typed, the strip has remove and Clear queue`() {
        assertTrue(screen.contains("val stops = answering && !canSend"))
        assertTrue(screen.contains("if (state.queued.isNotEmpty()) QueueStrip(state.queued, onRemoveQueued, onClearQueue)"))
    }

    @Test fun `strings in English and Portuguese`() {
        for (d in listOf("values", "values-pt")) {
            val s = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
            for (k in listOf("queueFull", "queueDropped", "waiting", "clearQueue", "queueNote")) assertTrue("$d $k", s.contains("name=\"native_chat_$k\""))
        }
    }
}
