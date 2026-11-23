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
 * model never runs: a model answered "yes, with ventilation". English, and Portuguese and Spanish with draft translations
 * of the English text (not yet reviewed). A question with symptoms or an alarm going
 * off is not a planning question and goes to the model as a health question.
 */
object CoHazardLab : LabPlugin {
    override val id = "cohazard"
    override val emoji = "⚠️"
    override val art = team.sopa.boar.R.drawable.lab_cohazard
    override val status = LabStatus.BETA

    override val answerTool: AnswerTool = AnswerTool { query, _ -> LabTools.coHazard(query)?.let { ToolResult.Instant(it.tool, it.text) } }

    @Composable
    override fun Content() {
        val c = Boar.colors
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // A safety page: what to do now comes first, in amber, before the example.
            Text("⚠ " + t("native.labs.cohazard.safety"), style = Boar.type.body, color = c.amber400, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().background(c.amberBgSubtle, RoundedCornerShape(10.dp)).padding(12.dp))
            // The plugin page template (Nov 12): a real example (an excerpt of CoHazard's fixed text), Try, the rest folded.
            team.sopa.boar.labs.AnswerPage(null, t("native.labs.cohazard.exampleQ"), t("native.labs.cohazard.exampleA"),
                (1..3).map { t("native.labs.cohazard.example$it") }, t("native.labs.cohazard.how"))
        }
    }
}
