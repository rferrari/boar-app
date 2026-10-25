package team.sopa.boar.labs.dailyquiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.vault.DailyQuiz
import team.sopa.boar.engine.vault.Vault
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabPrefs
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.time.LocalDate

/**
 * Boar Labs' Daily Quiz (Oct 24, was the Truffle Hunt teaser): five questions a day from BOAR's built-in library,
 * offline and without the model (engine DailyQuiz). Classified in normal builds (-Pboar.labs.declassify=dailyQuiz
 * builds it). Right answers unlock two avatars (Vault QUIZ_*: knight, elder), and with them the
 * legendary one. Today's answers are kept, so leaving resumes the quiz and replaying counts nothing more.
 */
object DailyQuizLab : LabPlugin {
    override val id = "dailyQuiz"
    override val emoji = "❔"
    override val art = team.sopa.boar.R.drawable.lab_daily_quiz
    override val status = LabStatus.ALPHA

    private const val DAY = "dailyQuiz.day"
    /** Today's picks, one per answered question ("2,0,3"). */
    private const val PICKS = "dailyQuiz.picks"
    /** Articles the day's quiz draws from (a sample of the library, by the date). */
    private const val POOL = 400

    private fun picksFor(today: String): List<Int> =
        if (LabPrefs.string(DAY) != today) emptyList() else LabPrefs.string(PICKS).orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    @Composable
    override fun Content() {
        val c = Boar.colors
        val services = BoarServices.get(LocalContext.current)
        val today = remember { LocalDate.now() }
        var quiz by remember { mutableStateOf<List<DailyQuiz.Question>?>(null) }
        LaunchedEffect(today) {
            quiz = withContext(Dispatchers.IO) {
                runCatching { DailyQuiz.daily(services.knowledgeBase.seedArticles(today.toEpochDay(), POOL), today) }.getOrDefault(emptyList())
            }
        }
        var picks by remember { mutableStateOf(picksFor(today.toString())) }
        // The question on screen after an answer, until Next: picks.size counts answered ones.
        var showing by remember { mutableStateOf<Int?>(null) }
        val q = quiz
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                q == null -> Text(t("native.labs.dailyQuiz.loading"), style = Boar.type.body, color = c.textMuted)
                q.isEmpty() -> Text(t("native.labs.dailyQuiz.empty"), style = Boar.type.body, color = c.textSecondary)
                showing == null && picks.size >= q.size -> {
                    val right = q.indices.count { picks.getOrNull(it) == q[it].answer }
                    Column(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("native.labs.dailyQuiz.score", "right" to right, "total" to q.size), style = Boar.type.title, color = c.textHeading)
                        Text(t("native.labs.dailyQuiz.tomorrow"), style = Boar.type.body, color = c.textSecondary)
                    }
                }
                else -> {
                    val i = showing ?: picks.size
                    val question = q[i]
                    val picked = picks.getOrNull(i)
                    Text(t("native.labs.dailyQuiz.progress", "n" to i + 1, "total" to q.size), style = Boar.type.micro, color = c.textAccentEmerald, fontWeight = FontWeight.Bold)
                    Text(t("native.labs.dailyQuiz.prompt"), style = Boar.type.title, color = c.textHeading)
                    Text(question.clue, style = Boar.type.body, color = c.textPrimary,
                        modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).padding(14.dp))
                    question.options.forEachIndexed { o, title ->
                        val isAnswer = o == question.answer
                        val bg = when {
                            picked == null -> c.bgCard
                            isAnswer -> c.emeraldBgSubtle
                            o == picked -> c.crimsonBgSubtle
                            else -> c.bgCard
                        }
                        Text(title, style = Boar.type.body, color = c.textHeading,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
                                .border(1.dp, if (picked != null && isAnswer) c.emerald500 else c.borderSubtle, RoundedCornerShape(12.dp))
                                .clickable(enabled = picked == null) {
                                    // A second tap before the screen updates finds this question answered already (Deep Scan).
                                    if (picks.size > i) return@clickable
                                    // Kept before counting: a second tap, or the app closing here, can't count it twice.
                                    picks = picks + o
                                    LabPrefs.putString(DAY, today.toString())
                                    LabPrefs.putString(PICKS, picks.joinToString(","))
                                    showing = i
                                    services.vault.record { f ->
                                        f.copy(quizRight = f.quizRight + if (o == question.answer) 1 else 0, quizDays = Vault.Days.add(f.quizDays, today))
                                    }
                                }.padding(14.dp))
                    }
                    if (picked != null) {
                        Text(if (picked == question.answer) t("native.labs.dailyQuiz.right") else t("native.labs.dailyQuiz.wrong", "title" to question.options[question.answer]),
                            style = Boar.type.body, color = if (picked == question.answer) c.textAccentEmerald else c.textSecondary)
                        Text(t("native.labs.dailyQuiz.next") + "  →", style = Boar.type.title, color = c.textInverse, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.emerald500).clickable { showing = null }.padding(14.dp))
                    }
                }
            }
        }
    }
}
