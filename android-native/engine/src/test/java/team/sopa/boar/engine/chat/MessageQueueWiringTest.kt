package team.sopa.boar.engine.chat

import org.junit.Assert.assertTrue
import org.junit.Test

/** The chat's side of PromptQueue (t9, Nov 10): read from the source, as the other app-wiring tests do. */
class MessageQueueWiringTest {
    private val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
    private val screen = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()

    @Test fun `a question sent while answering waits, at most five`() {
        assertTrue(vm.contains("if (job?.isActive == true || (!fromQueue && (handoff || !queue.isEmpty()))) {\n            if (queue.add(q)) publishQueue() else showToast(\"native.chat.queueFull\")"))
        assertTrue(vm.contains("private val queue = team.sopa.boar.engine.chat.PromptQueue()"))
    }

    @Test fun `the next one starts however the answer ended, stopped or failed included`() {
        // invokeOnCompletion runs on done, Stop (cancel) and failure alike; Stop doesn't touch the queue.
        val done = vm.substringAfter("job?.invokeOnCompletion {").substringBefore("private val queue")
        assertTrue(done.contains("runNext(fresh = false)"))
        val next = vm.substringAfter("private fun runNext(").substringBefore("\n    }\n")
        assertTrue(next.contains("val next = queue.next()") && next.contains("send(next.text, null, fromQueue = true, serviceUp = !fresh)"))
        assertTrue(!vm.substringAfter("fun stop() {").substringBefore("\n    }").contains("queue"))
    }

    @Test fun `the notification stays up between queued answers and goes with the last`() {
        assertTrue(vm.contains("if (next == null) { if (!fresh) team.sopa.boar.app.AnswerService.stop(getApplication()); return }"))
        assertTrue(vm.contains("if (!serviceUp) team.sopa.boar.app.AnswerService.start(getApplication())"))
    }

    @Test fun `deleting the open chat, or the PIN's erase, drops its queue`() {
        assertTrue(vm.contains("if (_state.value.sessionId == id) { queue.clear(); publishQueue(); newChat() }"))
        assertTrue(vm.contains("queue.clear(); publishQueue()\n                // Forgot PIN erased every chat"))
    }

    @Test fun `a closed app says what it dropped`() {
        assertTrue(vm.contains("\"native.chat.queueDropped\""))
    }

    @Test fun `one button that stops or queues, resting after a send, the strip has remove and Clear queue`() {
        // One button: Stop while answering with nothing typed, Send (to the queue) while something is typed; after a send it
        // rests, dimmed, so a second tap can't stop the answer (adam, Nov 12).
        assertTrue(screen.contains("val stops = answering && !canSend"))
        assertTrue(!screen.contains("if (answering && canSend) Box("))
        assertTrue(screen.contains("clickable(enabled = (answering || canSend) && !resting) { if (stops) onStop() else { onSend(); if (answering) resting = true } }"))
        assertTrue(screen.contains("private const val SEND_REST_MS = 500L"))
        assertTrue(screen.contains("if (state.queued.isNotEmpty()) QueueStrip(state.queued, onRemoveQueued, onClearQueue, onMergeQueue)"))
    }

    @Test fun `strings in English and Portuguese`() {
        for (d in listOf("values", "values-pt")) {
            val s = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
            for (k in listOf("queueFull", "queueDropped", "queueDroppedNote", "mergeQueue", "clearQueue", "queueNote")) assertTrue("$d $k", s.contains("name=\"native_chat_$k\""))
        }
    }

    @Test fun `Close BOAR, a model switch or delete, and leaving during a Labs engine answer clear the queue (Q-1)`() {
        assertTrue(vm.contains("private val stopper: () -> Unit = { stopAll() }"))
        assertTrue(vm.substringAfter("suspend fun closeAndFreeMemory(").substringBefore("onStage(\"saving\")").contains("stopAll()"))
        assertTrue(vm.substringAfter("fun stopAll() {").substringBefore("\n    }").contains("queue.clear()"))
        // stopAnswersAndWait and onBackground call the stoppers, so they clear it too.
        val services = java.io.File("../app/src/main/java/team/sopa/boar/app/BoarServices.kt").readText()
        assertTrue(services.substringAfter("fun stopAnswersAndWait(").substringBefore("\n    }").contains("stoppers.forEach"))
    }

    @Test fun `a question sent between an answer's end and the next start waits its turn (Q-2)`() {
        val done = vm.substringAfter("job?.invokeOnCompletion {").substringBefore("private val queue")
        assertTrue(done.indexOf("handoff = true") in 0 until done.indexOf("safeScope.launch(Dispatchers.Main)"))
        assertTrue(vm.contains("if (job?.isActive == true) { queue.pushFront(next); publishQueue(); return }"))
    }

    @Test fun `a queue behind the load note starts when the model loads, or goes with the note (Q-3)`() {
        assertTrue(vm.contains("else if (loaded) runNext(fresh = true)"))
        for (f in listOf("fun dismissLoadNote()", "fun openedCatalog()")) assertTrue(f, vm.substringAfter(f).substringBefore("\n    }").contains("dropQueueWithNote()"))
        assertTrue(vm.contains("if (loaded && held != null) send(held, null, fromQueue = true, serviceUp = false)"))
    }

    @Test fun `each waiting row is the hourglass and its number, clear and merge are icons`() {
        assertTrue(screen.contains("Text(\"⏳ \${i + 1}\""))
        assertTrue(screen.contains("onClickLabel = t(\"native.chat.mergeQueue\"), onClick = onMerge") && screen.contains("if (queued.size >= 2) Text(\"⇉\""))
        assertTrue(vm.contains("fun mergeQueue() { if (queue.merge()) publishQueue() }"))
    }
}
