package team.sopa.boar.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.rag.SourceArticle
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * A source under an answer, read whole (Oct 24): the article or file as it is on the phone (SourceArticle), with the
 * passage the answer used marked and scrolled into view. Offline: the source's address is shown, not opened.
 * [passage]: the text the model read, to mark.
 */
@Composable
fun SourceReaderScreen(services: BoarServices, docId: String, title: String, passage: String, onBack: () -> Unit) {
    val c = Boar.colors
    var article by remember(docId) { mutableStateOf<SourceArticle.Article?>(null) }
    var loaded by remember(docId) { mutableStateOf(false) }
    LaunchedEffect(docId) {
        article = withContext(Dispatchers.IO) { runCatching { SourceArticle.read(docId, title, services.knowledgeBase, services.packs) }.getOrNull() }
        loaded = true
    }
    Column(Modifier.fillMaxSize().background(c.bgTerminal).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(c.bgSurface).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("‹ " + t("common.back"), style = Boar.type.body, color = c.textAccentEmerald, modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            Text(article?.title ?: title, style = Boar.type.titleLg, color = c.textHeading, maxLines = 2, modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        val a = article
        when {
            !loaded -> Text(t("native.reader.loading"), style = Boar.type.body, color = c.textMuted, modifier = Modifier.padding(16.dp))
            a == null -> Text(t("native.reader.missing"), style = Boar.type.body, color = c.textSecondary, modifier = Modifier.padding(16.dp))
            else -> ArticleBody(a, passage)
        }
    }
}

@Composable
private fun ArticleBody(a: SourceArticle.Article, passage: String) {
    val c = Boar.colors
    val scroll = rememberScrollState()
    val used = remember(a, passage) { SourceArticle.passageAt(a.text, passage) }
    var usedAt by remember { mutableStateOf<Int?>(null) }
    // Once laid out, the marked passage comes into view, a little below the top.
    LaunchedEffect(usedAt) { usedAt?.let { scroll.animateScrollTo(maxOf(0, it - 48)) } }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        a.source?.let { SelectionContainer { Text(it, style = Boar.type["mono.xs"], color = c.textDim) } }
        if (used != null) Text(t("native.reader.used"), style = Boar.type.subtext, color = c.textAccentEmerald)
        @Composable fun part(text: String) {
            if (text.isBlank()) return
            if (a.markdown) MarkdownMessage(text, streaming = false) else SelectionContainer { Text(text, style = Boar.type.body, color = c.textPrimary) }
        }
        if (used == null) part(a.text)
        else {
            part(a.text.substring(0, used.first))
            Box(Modifier.fillMaxWidth().background(c.emeraldBgSubtle, RoundedCornerShape(8.dp)).border(1.dp, c.emeraldBorder, RoundedCornerShape(8.dp))
                .onGloballyPositioned { if (usedAt == null) usedAt = it.positionInParent().y.toInt() }.padding(10.dp)) {
                part(a.text.substring(used))
            }
            part(a.text.substring(used.last + 1))
        }
    }
}
