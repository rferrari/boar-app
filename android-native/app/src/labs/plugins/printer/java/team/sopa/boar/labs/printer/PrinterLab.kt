package team.sopa.boar.labs.printer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import team.sopa.boar.engine.labs.PressDoc
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.ui.t

/**
 * Boar Press: projects as documents (adam's mock-up, Nov 13). Each project is a page of blocks: answers sent from a chat
 * (📎 in an answer's plugins sheet, or Add to Press on a whole chat), headings, notes and Designer drawings. The page in
 * the app is what the PDF prints; every document ends with how it was made. The screen is [PressScreen].
 */
object PrinterLab : LabPlugin {
    override val id = "printer"
    override val emoji = "🖨️"
    override val art = team.sopa.boar.R.drawable.truffle_steampunk
    override val status = LabStatus.BETA

    /** The paper fills the page under the header; About this plugin is in the project sheet. */
    override val fullPage = true

    override suspend fun selfTest(context: android.content.Context, args: Map<String, String>, say: (String) -> Unit): Boolean {
        PressSelfTest.run(context, args, say); return true
    }

    /** The projects hold answers' text: they go with the chats (Forgot PIN, Clear all), every project and its folder. */
    override fun eraseUserData(context: android.content.Context, scope: team.sopa.boar.plugins.UserData.Scope) {
        PressStore.eraseAll(context)
    }

    /** One chat deleted (after its Undo window, or pruned): its answers leave every project (adam chose (a), Nov 12). Runs even when off. */
    override fun eraseAnswers(context: android.content.Context, answerIds: List<String>) = PressStore.eraseAnswers(context, answerIds)

    override fun deleteNote(context: android.content.Context, answerIds: List<String>): String? =
        if (PressStore.countOf(context, answerIds) > 0) team.sopa.boar.ui.I18n.t(context, "native.labs.printer.deleteNote") else null

    /** An answer as a block, with what the chat knows of it. */
    private fun answerBlock(id: String, chatTitle: String, question: String, text: String, sources: List<Pair<String, String?>>, model: String?, tokPerSec: Double?) =
        PressDoc.Block.Answer(id, chatTitle, question, text, sources.map { PressDoc.Source(it.first, it.second?.takeIf { s -> s.isNotEmpty() }) },
            System.currentTimeMillis(), model, tokPerSec)

    /** 📎 in the answer's plugins sheet: into the open project, at the end, with the drawing it already has. */
    @Composable
    override fun answerActions(a: team.sopa.boar.plugins.AnswerRef): List<team.sopa.boar.labs.LabAction> {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        remember { PressStore.load(ctx) }
        val lib = PressStore.library.collectAsState().value
        val added = lib?.has(a.id) == true
        val project = lib?.current?.name.orEmpty()
        return listOf(team.sopa.boar.labs.LabAction("📎", t(if (added) "native.labs.printer.inBasket" else "native.labs.printer.addToBasket", "project" to project),
            done = added, art = art) {
            if (added) PressStore.change { it.removeAnswer(a.id) }
            else scope.launch(Dispatchers.IO) {
                val title = runCatching { team.sopa.boar.app.BoarServices.get(ctx).chats.listSessions().firstOrNull { it.id == a.sessionId }?.title }.getOrNull().orEmpty()
                PressStore.addAnswer(ctx, answerBlock(a.id, title, a.question, a.text, a.sources, a.model, a.tokPerSec), listOfNotNull(PressPictures.ofAnswer(ctx, a.id)))
            }
        })
    }

    /** "Add to Press" on a chat in the menu (adam, Nov 12): every answer of the chat, into the open project. */
    override val chatActions = listOf(team.sopa.boar.plugins.ChatAction("native.labs.printer.addChat") { ctx, sessionId, title ->
        Thread {
            val msgs = runCatching { team.sopa.boar.app.BoarServices.get(ctx).chats.messages(sessionId) }.getOrDefault(emptyList())
            var question = ""
            for (m in msgs) {
                if (m.role == "user") { question = m.text; continue }
                if (m.role != "assistant" || m.text.isBlank()) continue
                val sources = runCatching {
                    val arr = org.json.JSONObject(m.meta ?: "{}").optJSONArray("sources") ?: org.json.JSONArray()
                    (0 until arr.length()).map { i -> arr.getJSONObject(i).let { o -> o.optString("title") to o.optString("source").takeIf { it.isNotEmpty() } } }
                }.getOrDefault(emptyList())
                PressStore.addAnswer(ctx, answerBlock(m.id, title, question, m.text, sources, null, null), listOfNotNull(PressPictures.ofAnswer(ctx, m.id)))
            }
        }.start()
    })

    /** The open project's answers, on the chat header's plugins icon and beside Press in its list. */
    @Composable
    override fun headerCount(): Int {
        val ctx = LocalContext.current
        remember { PressStore.load(ctx) }
        return PressStore.library.collectAsState().value?.current?.answers?.size ?: 0
    }

    @Composable
    override fun headerCountLabel(n: Int): String =
        t("native.labs.printer.inProject", "count" to n, "project" to PressStore.library.collectAsState().value?.current?.name.orEmpty())

    @Composable
    override fun Content() = PressScreen(this)
}
