package team.sopa.boar.labs.polyglot

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Looper
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLanguage
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import team.sopa.boar.engine.rag.ConversationTurn
import team.sopa.boar.engine.routing.LanguageGate
import team.sopa.boar.labs.LabContext
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.plugins.AnswerTool
import team.sopa.boar.plugins.ToolResult
import team.sopa.boar.ui.I18n
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * Boar Polyglot: a full question in a language BOAR doesn't answer in (anything but English, Portuguese and, for now,
 * Spanish: LanguageGate.ALLOWED) gets an instant reply saying so, in the app's language, and the model never runs. The
 * decision is engine/routing LanguageGate; the phone's language detector (Android 10+) is asked only for Latin-script
 * text, and other scripts are told by their letters. On by default while Boar Labs is on; off, nothing changes.
 */
object PolyglotLab : LabPlugin {
    override val id = "polyglot"
    override val emoji = "🗣️"
    override val art = team.sopa.boar.R.drawable.lab_polyglot
    override val status = LabStatus.BETA
    override val onByDefault = true

    override val answerTool: AnswerTool = AnswerTool { query, history ->
        val app = LabContext.app ?: return@AnswerTool null
        val ui = uiContext(app)
        val decision = LanguageGate.decide(query, followUp(ui, history)) { PhoneLanguageDetector.detect(app, it) }
        val code = (decision as? LanguageGate.Decision.Unsupported)?.language ?: return@AnswerTool null
        ToolResult.Instant(LanguageGate.TOOL, I18n.t(ui, "native.labs.polyglot.reply", "language" to languageName(code, ui)))
    }

    /** The last question in this chat was answered (not with this reply): a short message now is a follow-up to it. */
    private fun followUp(ui: Context, history: List<ConversationTurn>): Boolean {
        val lastUser = history.indexOfLast { it.role == "user" }
        if (lastUser < 0) return false
        val answer = history.getOrNull(lastUser + 1)?.takeIf { it.role == "assistant" }?.text
        if (answer.isNullOrBlank()) return false
        val ours = I18n.t(ui, "native.labs.polyglot.reply", "language" to "\u0000").substringBefore('\u0000')
        return ours.isBlank() || !answer.startsWith(ours)
    }

    /** The language's name in the app's language: "Russian"; in Portuguese lowercase, as it writes them ("russo"). */
    private fun languageName(code: String, ui: Context): String {
        val uiLocale = ui.resources.configuration.locales[0] ?: Locale.getDefault()
        val name = Locale.forLanguageTag(code).getDisplayLanguage(uiLocale).ifBlank { code }
        return if (uiLocale.language == "pt") name.lowercase(uiLocale) else name
    }

    /** The app's language from Settings (MainActivity applies it to the activity only), else the phone's. */
    private fun uiContext(app: Context): Context {
        val lang = runCatching { team.sopa.boar.app.BoarServices.get(app).settings.languageId }.getOrNull() ?: return app
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) })
    }

    @Composable
    override fun Content() {
        val c = Boar.colors
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(t("native.labs.polyglot.answers")) { Text(t("native.labs.polyglot.answersList"), style = Boar.type.body, color = c.textPrimary) }
            Card(t("native.labs.polyglot.try")) {
                for (i in 1..3) Text("“${t("native.labs.polyglot.example$i")}”", style = Boar.type.caption, color = c.textSecondary)
            }
            Text(t("native.labs.polyglot.note"), style = Boar.type["mono.xs"], color = c.textDim)
        }
    }

    @Composable
    private fun Card(label: String, content: @Composable () -> Unit) {
        val c = Boar.colors
        Column(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = Boar.type["mono.xs"], color = c.textDim, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Android's on-device language detector (TextClassifier, API 29+). Null when there is none, it fails, or on the main thread. */
internal object PhoneLanguageDetector {
    fun detect(context: Context, text: String): List<Pair<String, Float>>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Looper.myLooper() == Looper.getMainLooper()) return null
        return runCatching { detectQ(context, text) }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun detectQ(context: Context, text: String): List<Pair<String, Float>>? {
        val classifier = context.getSystemService(TextClassificationManager::class.java)?.textClassifier ?: return null
        if (classifier === TextClassifier.NO_OP) return null
        val r = classifier.detectLanguage(TextLanguage.Request.Builder(text).build())
        return (0 until r.localeHypothesisCount).map { i -> r.getLocale(i).let { it.language to r.getConfidenceScore(it) } }
            .sortedByDescending { it.second }.ifEmpty { null }
    }
}
