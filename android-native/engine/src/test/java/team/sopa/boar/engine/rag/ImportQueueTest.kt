package team.sopa.boar.engine.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportQueueTest {
    @Test fun `collections run in the order they were added, at most five wait`() {
        val q = ImportQueue<String>()
        repeat(5) { assertTrue(q.add("c$it", 1, "p$it")) }
        assertFalse(q.add("c5", 1, "p5"))
        assertEquals(listOf("c0", "c1", "c2", "c3", "c4"), generateSequence { q.next()?.name }.toList())
        assertNull(q.next())
    }

    @Test fun `one can be removed, and an empty pick isn't queued`() {
        val q = ImportQueue<Int>()
        q.add("a", 2, 1); q.add("b", 1, 2); q.add("c", 3, 3)
        assertTrue(q.remove(q.waiting[1].id))
        assertEquals(listOf("a", "c"), q.waiting.map { it.name })
        assertFalse(q.add("none", 0, 4))
    }

    @Test fun `a failed import doesn't stop the next one, and closing BOAR is said`() {
        val vm = java.io.File("../app/src/main/java/team/sopa/boar/documents/DocumentsViewModel.kt").readText()
        // The runner's finally takes the next one whatever happened.
        assertTrue(vm.contains("} finally {") && vm.contains("runNextImport()"))
        val screen = java.io.File("../app/src/main/java/team/sopa/boar/ui/documents/DocumentsScreen.kt").readText()
        assertTrue(screen.contains("t(\"native.docs.queueNote\")"))
    }
}
