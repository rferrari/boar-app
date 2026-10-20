package team.sopa.boar.labs.distance

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
import team.sopa.boar.engine.routing.LabTools
import team.sopa.boar.engine.routing.TravelDistance
import team.sopa.boar.labs.LabContext
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.plugins.AnswerTool
import team.sopa.boar.plugins.ToolResult
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * Boar Distance: the travel distance step of r4to's src/routing/answer.ts, around src/routing/travelDistance.ts (PR #85,
 * BOAR R&D, MIT; engine/routing TravelDistance). "How far is it to walk from Paris to Rome and back?" gets the
 * straight-line distance between two cities in the GeoNames table (res/raw, CC BY 4.0) times a stated route factor,
 * always called an estimate, and the model never runs. English only. answer.ts skips it when its emergency detector
 * fires; as in Boar Calculator, NumGuard's health lexicon over this and the earlier user turns stands in for it.
 */
object DistanceLab : LabPlugin {
    override val id = "distance"
    // On at first launch in the bounty build (owner, Oct 20): instant, offline, no download.
    override val onByDefault = true
    override val emoji = "🗺️"
    override val art = team.sopa.boar.R.drawable.lab_distance
    override val status = LabStatus.ALPHA

    /** The city table, read on the first travel question (280 KB of JSON). */
    private val table: TravelDistance? by lazy {
        LabContext.app?.resources?.openRawResource(R.raw.lab_distance_world_cities_v1)?.use { TravelDistance(it.reader().readText()) }
    }

    override val answerTool: AnswerTool = AnswerTool { query, history ->
        LabTools.distance(table, query, history.filter { it.role == "user" }.map { it.text })?.let { ToolResult.Instant(it.tool, it.text) }
    }

    @Composable
    override fun Content() {
        val c = Boar.colors
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(t("native.labs.distance.answers")) { Text(t("native.labs.distance.answersList"), style = Boar.type.body, color = c.textPrimary) }
            Card(t("native.labs.distance.try")) {
                for (i in 1..3) Text("“${t("native.labs.distance.example$i")}”", style = Boar.type.caption, color = c.textSecondary)
            }
            Text(t("native.labs.distance.note"), style = Boar.type["mono.xs"], color = c.textDim)
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
