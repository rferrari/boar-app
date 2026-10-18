package team.sopa.boar.labs.cohazard

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
import team.sopa.boar.plugins.AnswerTool
import team.sopa.boar.plugins.ToolResult
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * Boar Cohazard: the CO hazard step of r4to's src/routing/answer.ts, around src/routing/coHazard.ts (PR #85, BOAR R&D,
 * MIT; engine/routing CoHazard). A planning question about a generator, grill, camping stove, fuel heater or engine in a
 * garage, tent, camper or home gets a fixed carbon monoxide answer, reviewed and sourced (CDC, US CPSC, NHS), and the
 * model never runs: a model answered "yes, with ventilation". English only. A question with symptoms or an alarm going
 * off is not a planning question and goes to the model as a health question.
 */
object CoHazardLab : LabPlugin {
    override val id = "cohazard"
    override val emoji = "⚠️"
    override val status = LabStatus.BETA

    override val answerTool: AnswerTool = AnswerTool { query, _ -> LabTools.coHazard(query)?.let { ToolResult.Instant(it.tool, it.text) } }

    @Composable
    override fun Content() {
        val c = Boar.colors
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(t("native.labs.cohazard.answers")) { Text(t("native.labs.cohazard.answersList"), style = Boar.type.body, color = c.textPrimary) }
            Card(t("native.labs.cohazard.try")) {
                for (i in 1..3) Text("“${t("native.labs.cohazard.example$i")}”", style = Boar.type.caption, color = c.textSecondary)
            }
            Text(t("native.labs.cohazard.note"), style = Boar.type["mono.xs"], color = c.textDim)
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
