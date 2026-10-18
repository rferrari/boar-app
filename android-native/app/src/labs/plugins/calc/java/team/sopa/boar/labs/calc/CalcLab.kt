package team.sopa.boar.labs.calc

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
 * Boar Calculator: the first tool in the tool slot. Ported from r4to's calculator shortcuts (PR #85, BOAR R&D, MIT;
 * engine/routing CalcShortcut, Arithmetic, NumGuard): a calculation in the question is answered exactly, with its formula,
 * and the model never runs; the compact models got these wrong. Never on a chat that names health, an injury, danger or
 * a person (NumGuard's lexicon, over this and the earlier user turns): those stay with the model.
 */
object CalcLab : LabPlugin {
    override val id = "calc"
    override val emoji = "🧮"
    override val art = team.sopa.boar.R.drawable.lab_calc
    override val status = LabStatus.BETA

    override val answerTool: AnswerTool = AnswerTool { query, history ->
        LabTools.calc(query, history.filter { it.role == "user" }.map { it.text })?.let { ToolResult.Instant(it.tool, it.text) }
    }

    @Composable
    override fun Content() {
        val c = Boar.colors
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(t("native.labs.calc.answers")) { Text(t("native.labs.calc.answersList"), style = Boar.type.body, color = c.textPrimary) }
            Card(t("native.labs.calc.try")) {
                for (i in 1..3) Text("“${t("native.labs.calc.example$i")}”", style = Boar.type.caption, color = c.textSecondary)
            }
            Text(t("native.labs.calc.note"), style = Boar.type["mono.xs"], color = c.textDim)
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
