package team.sopa.boar.labs.printer

import android.content.Context
import team.sopa.boar.engine.answer.SessionEngine
import team.sopa.boar.engine.labs.PressRewrite

/**
 * Runs "Ask BOAR to rewrite it" on the chat's model (as Designer's subject does): never while the chat uses it, and never
 * by cancelling the session, which another generation may be using; Stop and leaving cancel this coroutine only. It
 * returns the rewrite and never touches the projects: the block changes only when the user picks "Use the new text".
 */
internal object PressRewriter {
    sealed interface Outcome {
        object Busy : Outcome
        object NoModel : Outcome
        object Failed : Outcome
        data class Done(val text: String, val marksChanged: Boolean) : Outcome
    }

    private const val TIMEOUT_MS = 120_000L

    /** [text] is the block's text as stored; [languageOf] tells its language (an answer's question and text). [onText]: what's written so far. */
    suspend fun run(ctx: Context, text: String, option: PressRewrite.Option, own: String?, languageOf: String, onText: (String) -> Unit): Outcome {
        val services = team.sopa.boar.app.BoarServices.get(ctx)
        if (services.isModelBusy) return Outcome.Busy
        val session = services.activeSession() ?: return Outcome.NoModel
        if (services.isModelBusy) return Outcome.Busy
        val p = PressRewrite.prompt(option, text, own, languageOf)
        val written = StringBuilder()
        // While it writes, the model counts as busy (BOAR doesn't free it in the background meanwhile).
        val busy: () -> Boolean = { true }
        services.addBusyCheck(busy)
        try {
            val r = kotlinx.coroutines.withTimeoutOrNull(TIMEOUT_MS) {
                SessionEngine(session).generate(p.system, p.user, p.nPredict, 0.3f, timeoutMs = null) { piece ->
                    written.append(piece)
                    onText(PressRewrite.visible(written.toString()))
                }
            } ?: return Outcome.Failed
            val out = PressRewrite.clean(r.text)
            if (out.isBlank()) return Outcome.Failed
            return Outcome.Done(out, PressRewrite.marksChanged(text, out))
        } finally {
            services.removeBusyCheck(busy)
        }
    }
}
