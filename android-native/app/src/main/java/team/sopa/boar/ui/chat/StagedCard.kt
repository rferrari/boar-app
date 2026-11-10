package team.sopa.boar.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import team.sopa.boar.chat.KeyFactView
import team.sopa.boar.chat.Source
import team.sopa.boar.chat.StagedView
import team.sopa.boar.engine.answer.StagedStep
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * Boar Staged answers, over the answer's text: the sentences from the sources that answer (quotes, never BOAR's own
 * words; a tap shows the passage and opens the article), the step running now, what the research step added and the
 * number check. While the answer is written the facts are open; once it's done they fold into one line.
 */
@Composable
internal fun StagedCard(v: StagedView, streaming: Boolean, written: Boolean, onRead: (Source) -> Unit) {
    val c = Boar.colors
    var open by remember { mutableStateOf<Boolean?>(null) }
    // Open, and kept open when the model starts answering (adam, Nov 10); a tap on the heading folds it.
    val factsOpen = open ?: true
    var moreOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (v.keyFacts.isNotEmpty()) {
            Text(t("native.staged.facts", "count" to v.keyFacts.size) + if (factsOpen) "" else " ›", style = Boar.type["mono.xs"], color = c.textDim,
                fontWeight = FontWeight.Bold, modifier = Modifier.clickable { open = !factsOpen }.padding(vertical = 2.dp))
            if (factsOpen) {
                v.keyFacts.forEach { KeyFact(it, v.withPassages, onRead) }
                // The rest of the sources, one tap away: "+2 sources ›" (adam, Nov 10: 2 or 3 quotes are good as they are).
                if (v.more.isNotEmpty()) {
                    Text(if (moreOpen) t("native.staged.lessSources") else t("native.staged.moreSources", "count" to v.more.size) + " ›",
                        style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { moreOpen = !moreOpen }.padding(vertical = 2.dp))
                    if (moreOpen) v.more.forEach { KeyFact(it, v.withPassages, onRead) }
                }
                Text(t("native.staged.factsNote"), style = Boar.type.subtext, color = c.textDim, fontStyle = FontStyle.Italic)
            }
        }
        // The step line once the answer has started; before its first word the chat's wait line says what's happening.
        if (streaming && written) v.step?.let { step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(10.dp), color = c.textAccentEmerald, strokeWidth = 1.5.dp)
                Text(t("native.staged.step.${step.name.lowercase()}"), style = Boar.type.subtext, color = c.textMuted)
            }
        }
        if (v.added > 0) Text(t("native.staged.added", "count" to v.added), style = Boar.type.subtext, color = c.textSecondary)
    }
}

/** Under the answer: the number check, once it's done. */
@Composable
internal fun StagedCheck(v: StagedView) {
    val c = Boar.colors
    val u = v.unsupported ?: return
    Text(if (u.isEmpty()) "✓ " + t("native.staged.checkOk") else "⚠ " + t("native.staged.checkMissing", "numbers" to u.joinToString(", ")),
        style = Boar.type.subtext, color = if (u.isEmpty()) c.textAccentEmerald else c.textAccentAmber, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun KeyFact(k: KeyFactView, withPassage: Boolean, onRead: (Source) -> Unit) {
    val c = Boar.colors
    // A health answer shows each sentence with its passage: a sentence alone, out of context, can mislead.
    var passage by remember { mutableStateOf(withPassage) }
    Column(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(8.dp)).clickable { passage = !passage }.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("“${k.sentence}”", style = Boar.type.body, color = c.textPrimary)
        Text("— ${k.source.title}", style = Boar.type.subtext, color = c.textSecondary)
        if (passage) {
            Text(k.passage, style = Boar.type.subtext, color = c.textMuted)
            Text(t("native.staged.readArticle"), style = Boar.type.subtext, color = c.textAccentCyan, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onRead(k.source) }.padding(vertical = 2.dp))
        }
    }
}
