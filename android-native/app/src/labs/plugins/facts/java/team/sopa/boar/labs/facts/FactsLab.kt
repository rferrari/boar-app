package team.sopa.boar.labs.facts

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
import team.sopa.boar.R
import team.sopa.boar.engine.rag.Facts
import team.sopa.boar.engine.routing.LabTools
import team.sopa.boar.labs.LabContext
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.plugins.AnswerTool
import team.sopa.boar.plugins.ToolResult
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * Boar Facts: the FACTS step of r4to's src/routing/answer.ts, around src/rag/facts.ts (PR #85, BOAR R&D, MIT;
 * engine/rag Facts). A question that names a place, landmark, event or award in the Wikidata table (res/raw, CC0) and
 * asks one property of it (population, area, capital, height, dates, plugs and voltage, a winner) gets the value with
 * its source line, or a computed verdict for two of them, and the model never runs. English only. answer.ts keeps it
 * off health and emergency messages with its detector; here QueryContext's health detector, over this and the earlier
 * user turns, stands in for it.
 */
object FactsLab : LabPlugin {
    override val id = "facts"
    override val emoji = "📇"
    override val art = team.sopa.boar.R.drawable.lab_facts
    override val status = LabStatus.ALPHA

    /** The fact table, read on the first question (760 KB of JSON). */
    private val table: Facts? by lazy {
        LabContext.app?.resources?.openRawResource(R.raw.lab_facts_facts_v1)?.use { Facts(it.reader().readText()) }
    }

    override val answerTool: AnswerTool = AnswerTool { query, history ->
        LabTools.facts(table, query, history.filter { it.role == "user" }.map { it.text })?.let { ToolResult.Instant(it.tool, it.text) }
    }

    @Composable
    override fun Content() {
        val c = Boar.colors
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(t("native.labs.facts.answers")) { Text(t("native.labs.facts.answersList"), style = Boar.type.body, color = c.textPrimary) }
            Card(t("native.labs.facts.try")) {
                for (i in 1..3) Text("“${t("native.labs.facts.example$i")}”", style = Boar.type.caption, color = c.textSecondary)
            }
            Text(t("native.labs.facts.note"), style = Boar.type["mono.xs"], color = c.textDim)
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
