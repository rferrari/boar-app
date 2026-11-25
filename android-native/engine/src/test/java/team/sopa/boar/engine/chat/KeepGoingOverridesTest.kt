package team.sopa.boar.engine.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validator A-2 (t9 0010 item 4): a Keep going step, or Ask BOAR, sets overrides for the next question only. When that
 * question waits behind a load note and the note is closed, or the catalog opened, the question goes and so must they.
 * The ViewModel needs Android, so this reads its source, as the validator re-gates it by assertion.
 */
class KeepGoingOverridesTest {
    private val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()

    private fun body(name: String): String {
        val start = vm.indexOf("fun $name(")
        assertTrue(name, start >= 0)
        val open = vm.indexOf('{', start)
        var depth = 0
        for (i in open until vm.length) {
            if (vm[i] == '{') depth++ else if (vm[i] == '}') { depth--; if (depth == 0) return vm.substring(open, i + 1) }
        }
        error(name)
    }

    @Test fun `closing the note or opening the catalog drops every override with the held question`() {
        for (f in listOf("dismissLoadNote", "openedCatalog")) assertTrue(f, body(f).contains("dropOnce()"))
        val drop = body("dropOnce")
        for (v in listOf("stepOnce = null", "styleOnce = null", "deepOnce = null", "skipToolsOnce = false", "bypassed = null")) assertTrue(v, drop.contains(v))
        // Loading the model instead sends the held question with its overrides: userLoad doesn't drop them.
        // Only the failed load (R-A5) and a chat switch during the load (BOAR-68) drop them; a load that works in the
        // same chat sends the held question with them.
        assertEquals(2, Regex("dropOnce\\(\\)").findAll(body("userLoad")).count())
        assertTrue(body("userLoad").contains("if (loaded && held != null) send(held, null, fromQueue = true, serviceUp = false)"))
    }

    @Test fun `the bubbles hide while a load note is up, where a tap would do nothing (validator A-3)`() {
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("state.loadNote == null && state.pruneNote == null)\n                        item(key = \"keep-going-\" + last.id)"))
        // keepGoing refuses under a load note, so hiding is the only honest choice there.
        assertTrue(body("keepGoing").contains("_state.value.loadNote != null) return"))
    }

    @Test fun `a failed load drops the overrides with the held question (validator R-A5)`() {
        assertTrue(body("userLoad").contains("else { dropOnce(); dropQueueWithNote() }"))
    }
}
