package team.sopa.boar.labs.staged

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
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * Boar Staged answers: the chat answers in steps instead of making the user wait for the whole answer (adam's idea,
 * after Commonplace). The sources and the sentences that answer show first, before the model loads; then a short
 * answer; then a second search and the details; then a check of every number against the sources. Stop at any step keeps
 * what is on screen. The work is the engine's StagedAnswer, through the answer-mode slot (AnswerModes); this lab only
 * switches it on and explains it.
 */
object StagedLab : LabPlugin {
    override val id = "staged"
    override val emoji = "🪜"
    // TODO(rc3): its own icon. An existing Boar avatar stands in for now (adam, Nov 10).
    override val art = team.sopa.boar.R.drawable.truffle_elder
    override val status = LabStatus.ALPHA

    override val stagedAnswers = true
    /** The engine code this switch turns on (the chat runs it through AnswerModes); named so the release manifest can tie its tests to it. */
    internal val engineCode = team.sopa.boar.engine.answer.StagedAnswer::class
    // Its quotes and "+N sources" show every source above the answer: no second list under it.
    override val ownsSources = true

    @Composable
    override fun Content() {
        val c = Boar.colors
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(t("native.labs.staged.steps")) { Text(t("native.labs.staged.stepsList"), style = Boar.type.body, color = c.textPrimary) }
            Card(t("native.labs.staged.try")) {
                for (i in 1..3) team.sopa.boar.labs.TryRow(t("native.labs.staged.example$i"))
            }
            Text(t("native.labs.staged.note"), style = Boar.type["mono.xs"], color = c.textDim)
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
