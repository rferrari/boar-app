package team.sopa.boar.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import team.sopa.boar.chat.Source
import team.sopa.boar.engine.rag.SourceDigest
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/** Each answer's fold, for this run: open unless the user folded it (B+, "state remembered per answer"). */
internal object SourcesFold {
    private val folded = mutableStateMapOf<String, Boolean>()
    fun isOpen(answerId: String) = folded[answerId] != true
    fun toggle(answerId: String) { folded[answerId] = isOpen(answerId) }
}

/**
 * Layout B+ (adam, Nov 10): the sources stay open above the answer, never removed when it starts. One row per article
 * (its passages grouped), the sentence that answers best with the question's words in bold, a lead in italics when no
 * sentence matched. At most about 35% of the screen high, scrolling inside; the chevron folds it. A tap opens the
 * passage in a sheet ([onOpen]).
 */
@Composable
internal fun SourcesAbove(answerId: String, query: String, sources: List<Source>, onOpen: (Int) -> Unit) {
    val c = Boar.colors
    val rows = remember(sources, query) { SourceDigest.rows(query, sources.map { Triple(it.title, it.docId ?: it.title, it.body) }) }
    val open = SourcesFold.isOpen(answerId)
    val cap = (LocalConfiguration.current.screenHeightDp * 0.35f).dp
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp)).background(c.bgCard).border(1.dp, c.borderSubtle, RoundedCornerShape(10.dp))) {
        Row(Modifier.fillMaxWidth().clickable { SourcesFold.toggle(answerId) }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📚 " + t("native.sources.title", "count" to rows.size), style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (open) "▴" else "▾", style = Boar.type.body, color = c.textMuted)
        }
        if (open) Column(Modifier.heightIn(max = cap).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in rows) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(r.best.number) }.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(r.passages.joinToString(" ") { "[${it.number}]" }, style = Boar.type["mono.xs"], color = c.textDim)
                    Text(r.title, style = Boar.type.caption, color = c.textHeading, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    if (r.passages.size > 1) Text(t("native.sources.passages", "count" to r.passages.size), style = Boar.type["mono.xs"], color = c.textMuted)
                }
                Text(sentence(r.best), style = Boar.type.caption, color = c.textSecondary)
            }
        }
    }
}

/** The key sentence with its marked words bold; a lead (nothing matched) in italics, so it doesn't read as a match. */
@Composable
private fun sentence(p: SourceDigest.Passage) = buildAnnotatedString {
    val c = Boar.colors
    if (p.lead) { withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(p.sentence) }; return@buildAnnotatedString }
    var last = 0
    for (m in p.marks) {
        append(p.sentence.substring(last, m.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.textPrimary)) { append(p.sentence.substring(m)) }
        last = m.last + 1
    }
    append(p.sentence.substring(last))
}

/** The passage [number] of an answer, in a sheet: its title, the text with the key sentence marked, and the whole article. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PassageSheet(query: String, sources: List<Source>, number: Int, onRead: (Source) -> Unit, onClose: () -> Unit) {
    val c = Boar.colors
    val source = sources.getOrNull(number - 1) ?: return onClose()
    val passage = remember(source, query) {
        SourceDigest.rows(query, listOf(Triple(source.title, source.docId ?: source.title, source.body))).single().best
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = c.bgCardElevated) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("[$number] " + source.title, style = Boar.type.title, color = c.textHeading)
            SelectionContainer {
                Text(buildAnnotatedString {
                    val key = passage.sentence.removeSuffix("…")
                    val at = if (passage.lead) -1 else source.body.indexOf(key)
                    if (at < 0) append(source.body) else {
                        append(source.body.substring(0, at))
                        withStyle(SpanStyle(background = c.amberBgSubtle, fontWeight = FontWeight.SemiBold, color = c.textPrimary)) { append(source.body.substring(at, at + key.length)) }
                        append(source.body.substring(at + key.length))
                    }
                }, style = Boar.type.body, color = c.textSecondary)
            }
            Text(t("sourceFootnotes.readAll"), style = Boar.type.body, color = c.textAccentEmerald, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onClose(); onRead(source) }.padding(vertical = 6.dp))
        }
    }
}
