package team.sopa.boar.labs.guard

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
import team.sopa.boar.engine.routing.LabTools
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.plugins.AnswerCheck
import team.sopa.boar.plugins.Repair
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * Boar Guard: the verdict guard step of r4to's src/routing/answer.ts, around src/routing/verdictGuard.ts (PR #85, BOAR
 * R&D, MIT; engine/routing VerdictGuard), in the check slot. After the model answers, a Yes/No or a winner that the
 * answer's own figures or dates contradict ("No, astronauts did not walk on the Moon before 1970. The first moonwalk was
 * on July 20, 1969") is rewritten from those figures; anything it isn't sure of stays as written. English only; never a
 * health question (answer.ts skips its health route), and an answer with a thinking block is left alone.
 */
object GuardLab : LabPlugin {
    override val id = "guard"
    override val emoji = "🛡️"
    override val art = team.sopa.boar.R.drawable.lab_guard
    override val status = LabStatus.ALPHA

    override val answerCheck: AnswerCheck = AnswerCheck { question, answer -> LabTools.guard(question, answer)?.let { Repair(it.tool, it.text) } }

    @Composable
    override fun Content() {
        val c = Boar.colors
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(t("native.labs.guard.answers")) { Text(t("native.labs.guard.answersList"), style = Boar.type.body, color = c.textPrimary) }
            Card(t("native.labs.guard.try")) {
                for (i in 1..3) Text("“${t("native.labs.guard.example$i")}”", style = Boar.type.caption, color = c.textSecondary)
            }
            Text(t("native.labs.guard.note"), style = Boar.type["mono.xs"], color = c.textDim)
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
