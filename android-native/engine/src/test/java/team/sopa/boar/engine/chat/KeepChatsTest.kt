package team.sopa.boar.engine.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.db.JdbcSqlite
import team.sopa.boar.engine.rag.KnowledgeBase
import team.sopa.boar.engine.settings.AppSettings

/**
 * adam, Nov 15 (t9 0110 item 2): "keep all chats by default, warn before deleting when a user has set a limit". A
 * test phone lost every older chat to the silent limit of 10 (coder, 00:12).
 */
class KeepChatsTest {
    private var clock = 1_000L
    private fun store(): ChatStore { val db = JdbcSqlite(); KnowledgeBase(db); return ChatStore(db) { clock++ } }

    @Test fun `All is the default, an install that never chose keeps every chat, a chosen limit stays`() {
        val dir = kotlin.io.path.createTempDirectory("keepchats").toFile()
        try {
            val never = java.io.File(dir, "a.json").apply { writeText("{}") }
            assertEquals(AppSettings.KEEP_ALL_CHATS, AppSettings(never).maxSavedSessions)
            val chose = java.io.File(dir, "b.json").apply { writeText("""{"maxSavedSessions":10}""") }
            assertEquals(10, AppSettings(chose).maxSavedSessions)
            val s = AppSettings(never); s.maxSavedSessions = 25
            assertEquals(25, AppSettings(never).maxSavedSessions)
        } finally { dir.deleteRecursively() }
    }

    @Test fun `keeping all deletes nothing, however many chats`() {
        val s = store()
        repeat(30) { s.createSession("chat $it") }
        assertTrue(s.pushedOutByNew(AppSettings.KEEP_ALL_CHATS).isEmpty())
        s.prune(AppSettings.KEEP_ALL_CHATS)
        assertEquals(30, s.listSessions().size)
    }

    @Test fun `with a limit, the chats a new one would delete are named first, oldest first, and only when full`() {
        val s = store()
        repeat(4) { s.createSession("chat $it") }
        assertTrue(s.pushedOutByNew(5).isEmpty())
        s.createSession("chat 4")
        assertEquals(listOf("chat 0"), s.pushedOutByNew(5).map { it.title })
        // Over the limit (it was lowered): every chat past it, the oldest first.
        repeat(2) { s.createSession("chat ${5 + it}") }
        assertEquals(listOf("chat 0", "chat 1", "chat 2"), s.pushedOutByNew(5).map { it.title })
        // What the warning names is what the prune then deletes, with what the plugins made for it (P-1).
        val named = s.pushedOutByNew(5).map { it.id }.toSet()
        s.createSession("chat 7")
        val erased = ArrayList<String>()
        s.prune(5) { erased += it }
        assertEquals(named, erased.toSet())
    }

    @Test fun `the chat asks before the first question of a new chat, and its answers are wired`() {
        val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
        assertTrue(vm.contains("services.chats.pushedOutByNew(max)"))
        assertTrue(vm.contains("_state.value.sessionId == null && !pruneAccepted"))
        for (f in listOf("fun pruneKeepAll()", "fun pruneDelete()", "fun pruneCancel()")) assertTrue(f, vm.contains(f))
        assertTrue(vm.contains("services.settings.maxSavedSessions = team.sopa.boar.engine.settings.AppSettings.KEEP_ALL_CHATS"))
        val main = java.io.File("../app/src/main/java/team/sopa/boar/MainActivity.kt").readText()
        for (w in listOf("onPruneKeepAll = vm::pruneKeepAll", "onPruneDelete = vm::pruneDelete", "onPruneCancel = vm::pruneCancel")) assertTrue(w, main.contains(w))
        for (d in listOf("values", "values-pt")) {
            val x = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
            for (k in listOf("native_chat_prune_title", "native_chat_prune_text", "native_chat_prune_textMore", "native_chat_prune_keepAll",
                    "native_chat_prune_delete", "native_settings_summary_memoryAll", "native_settings_memory_all")) assertTrue("$d $k", x.contains("name=\"$k\""))
        }
    }

    @Test fun `lowering the limit names the chats it would delete, and asks first (validator R-A4)`() {
        val s = store()
        repeat(8) { s.createSession("chat $it") }
        assertEquals(listOf("chat 0", "chat 1", "chat 2"), s.pushedOutAt(5).map { it.title })
        assertTrue(s.pushedOutAt(10).isEmpty() && s.pushedOutAt(AppSettings.KEEP_ALL_CHATS).isEmpty())
        // Settings: the choice waits behind the dialog when chats would go; Keep all sets All; Delete them prunes with
        // the plugins' data; Back changes nothing.
        val st = java.io.File("../app/src/main/java/team/sopa/boar/ui/settings/SettingsScreen.kt").readText()
        assertTrue(st.contains("if (going.isEmpty()) { max = v; s.maxSavedSessions = v } else lowering = v to going"))
        assertTrue(st.contains("max = team.sopa.boar.engine.settings.AppSettings.KEEP_ALL_CHATS; s.maxSavedSessions = max; lowering = null"))
        assertTrue(st.contains("services.chats.prune(v) { id -> team.sopa.boar.plugins.UserData.eraseAnswers("))
        for (d in listOf("values", "values-pt")) {
            val x = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
            for (k in listOf("native_settings_lower_title", "native_settings_lower_text", "native_settings_lower_textMore", "native_settings_lower_delete")) assertTrue("$d $k", x.contains("name=\"$k\""))
        }
    }
}
