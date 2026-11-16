package team.sopa.boar.labs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * One example question on a plugin's page (adam, Nov 12): a plain row, not a quote. Tap: a new chat asks it. Long
 * press: it goes in the input box to edit first, as the empty chat's suggestions do. The copy icon copies it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TryRow(question: String) {
    val c = Boar.colors
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val copied = t("native.about.copied")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.bgSubtle).border(1.dp, c.borderSubtle, RoundedCornerShape(10.dp))
        .combinedClickable(onClickLabel = t("native.labs.tryAsk"), onLongClickLabel = t("native.labs.tryEdit"),
            onClick = { team.sopa.boar.plugins.AskFromPlugin.ask(question, send = true) },
            onLongClick = { team.sopa.boar.plugins.AskFromPlugin.ask(question, send = false) })
        .padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(question, style = Boar.type.body, color = c.textPrimary, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        Text("⧉", style = Boar.type.title, color = c.textMuted, modifier = Modifier.minimumInteractiveComponentSize().clip(CircleShape)
            .combinedClickable(onClickLabel = t("native.labs.tryCopy"), onClick = {
                clipboard.setText(AnnotatedString(question)); team.sopa.boar.ui.Toasts.show(copied)
            }).padding(8.dp))
    }
}
