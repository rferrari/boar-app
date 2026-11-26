package team.sopa.boar.engine.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BOAR-68 (from Biel's iOS port, boar-ios-app PR #57): the queue behind the prune note and the model's load note.
 * Read from ChatViewModel's code, as KeepChatsTest does: the view model needs Android to run.
 */
class QueueNotesTest {
    private val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
    private fun fn(name: String) = vm.substringAfter("fun $name(").let { it.substring(0, it.indexOf("\n    }\n")) }

    @Test fun `the question held by the prune note runs first, before those sent while it asked`() {
        val resume = fn("resumePruned")
        assertTrue(resume.contains("fromQueue = true"))
        // Sent while the note asks: it waits in the queue instead of running past the note.
        val send = vm.substringAfter("private fun send(query: String, limit: Int?, fromQueue: Boolean, serviceUp: Boolean)").substringBefore("gateNote()")
        assertTrue(send.contains("prunedQuestion != null"))
        assertTrue(send.contains("&& prunedQuestion == null) runNext"))
    }

    @Test fun `closing the prune note drops the waiting questions and says so, instead of leaving them stalled`() {
        assertTrue(fn("pruneCancel").contains("dropQueue(\"native.chat.queueDroppedPrune\")"))
    }

    @Test fun `switching chats drops the questions waiting behind a note, they never run in another chat`() {
        assertTrue(fn("newChat").contains("dropWaitingForSwitch()"))
        assertTrue(fn("openSession").contains("dropWaitingForSwitch()"))
        val drop = fn("dropWaitingForSwitch")
        assertTrue(drop.contains("heldQuestion = null"))
        assertTrue(drop.contains("prunedQuestion = null"))
        assertTrue(drop.contains("native.chat.queueDroppedSwitch"))
        // Staying in the same chat keeps them.
        assertTrue(fn("openSession").contains("if (_state.value.sessionId != id) { chatEpoch++; dropWaitingForSwitch() }"))
        assertFalse(fn("newChat").contains("if (_state.value.sessionId == null) dropWaitingForSwitch()"))
    }

    @Test fun `a chat switch during the model load drops the held question instead of sending it to the other chat`() {
        assertTrue(fn("newChat").contains("chatEpoch++"))
        assertTrue(fn("openSession").contains("chatEpoch++"))
        val load = fn("userLoad")
        assertTrue(load.contains("val epoch = chatEpoch"))
        val drop = load.indexOf("if (held != null && chatEpoch != epoch) Unit")
        assertTrue(drop >= 0 && drop < load.indexOf("send(held, null, fromQueue = true"))
    }

    @Test fun `the switch drops the loading question's options, and the stale load leaves a newer question's alone (CodeRabbit)`() {
        assertTrue(fn("userLoad").contains("loadHeld = held"))
        val drop = fn("dropWaitingForSwitch")
        assertTrue(drop.contains("listOfNotNull(heldQuestion, prunedQuestion?.first, loadHeld)"))
        assertTrue(drop.contains("dropOnce()"))
        // The stale completion clears nothing shared.
        val stale = fn("userLoad").substringAfter("if (held != null && chatEpoch != epoch)").substringBefore("\n")
        assertFalse(stale.contains("dropOnce"))
    }

    @Test fun `nothing leaves the queue while the prune note asks (CodeRabbit on PR 124)`() {
        // A load with nothing held ends in runNext; a question behind an open prune note must not start (it would skip
        // the note, and its answer would prune the chats the note listed before the user agreed).
        val next = fn("runNext")
        val guard = next.indexOf("if (prunedQuestion != null) return")
        assertTrue(guard >= 0 && guard < next.indexOf("queue.next()"))
    }

    @Test fun `each toast is in English and Portuguese`() {
        for (res in listOf("values", "values-pt")) {
            val xml = java.io.File("../app/src/main/res/$res/strings_native.xml").readText()
            assertTrue(xml.contains("name=\"native_chat_queueDroppedPrune\""))
            assertTrue(xml.contains("name=\"native_chat_queueDroppedSwitch\""))
        }
    }
}
