package team.sopa.boar.labs.distance

import androidx.compose.runtime.Composable
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
    override val emoji = "🗺️"
    override val art = team.sopa.boar.R.drawable.lab_distance
    override val status = LabStatus.BETA

    /** The city table, read on the first travel question (280 KB of JSON). */
    private val table: TravelDistance? by lazy {
        LabContext.app?.resources?.openRawResource(R.raw.lab_distance_world_cities_v1)?.use { TravelDistance(it.reader().readText()) }
    }

    override val answerTool: AnswerTool = AnswerTool { query, history ->
        LabTools.distance(table, query, history.filter { it.role == "user" }.map { it.text })?.let { ToolResult.Instant(it.tool, it.text) }
    }

    @Composable
    override fun Content() {
        // The plugin page template (Nov 12): the list line says what it does; a real example, Try, and the rest folded.
        team.sopa.boar.labs.AnswerPage(null, t("native.labs.distance.exampleQ"), t("native.labs.distance.exampleA"),
            (1..3).map { t("native.labs.distance.example$it") }, t("native.labs.distance.how"))
    }
}
