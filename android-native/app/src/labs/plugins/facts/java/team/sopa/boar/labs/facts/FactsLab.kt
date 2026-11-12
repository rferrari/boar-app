package team.sopa.boar.labs.facts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    override val status = LabStatus.BETA

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
        // The plugin page template (Nov 12), with what it knows in three groups (adam): Places, Measures, Other.
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(t("native.labs.facts.summary"), style = Boar.type.bodyLg, color = c.textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (g in listOf("places", "measures", "other")) Column {
                    Text(t("native.labs.facts.$g"), style = Boar.type.body, color = c.textHeading, fontWeight = FontWeight.SemiBold)
                    Text(t("native.labs.facts.${g}List"), style = Boar.type.body, color = c.textSecondary)
                }
            }
            Text(t("native.labs.facts.source"), style = Boar.type.caption, color = c.textMuted)
            team.sopa.boar.labs.AnswerPage(null, t("native.labs.facts.exampleQ"), t("native.labs.facts.exampleA"),
                (1..3).map { t("native.labs.facts.example$it") }, t("native.labs.facts.how"))
        }
    }
}
