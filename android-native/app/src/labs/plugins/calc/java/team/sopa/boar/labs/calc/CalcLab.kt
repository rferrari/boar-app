package team.sopa.boar.labs.calc

import androidx.compose.runtime.Composable
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
        // The plugin page template (Nov 12): what it does, a real example (Calculators.battery), Try, and the rest folded.
        team.sopa.boar.labs.AnswerPage(t("native.labs.calc.summary"), t("native.labs.calc.exampleQ"), t("native.labs.calc.exampleA"),
            (1..3).map { t("native.labs.calc.example$it") }, t("native.labs.calc.how"))
    }
}
