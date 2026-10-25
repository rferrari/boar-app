package team.sopa.boar.ui.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import team.sopa.boar.documents.DocumentsState
import team.sopa.boar.documents.DocumentsViewModel
import team.sopa.boar.engine.rag.KnowledgeBase
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * Knowledge (Oct 24): BOAR Knowledge (the packs, [packs]) first, then Personal Files (PersonalDocumentsManager.tsx).
 * [focus]: the section Settings opened this for ("packs" or "personal") starts open; from the menu ("") both are folded.
 */
@Composable
fun DocumentsScreen(state: DocumentsState, vm: DocumentsViewModel, focus: String = "", packs: @Composable () -> Unit = {}, onAskFiles: () -> Unit = {}, onBack: () -> Unit) {
    val c = Boar.colors
    var name by remember { mutableStateOf("") }
    var nameMissing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<KnowledgeBase.Collection?>(null) }
    // Open when Settings' "Open Personal Files" brought the user here; folded or opened, it stays so while on the screen.
    var personalOpen by rememberSaveable(focus) { mutableStateOf(focus == "personal") }
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.import(uris, name.trim()); name = "" }
    Column(Modifier.fillMaxSize().background(c.bgTerminal).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(c.bgSurface).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("‹ " + t("common.back"), style = Boar.type.body, color = c.textAccentEmerald, modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            Text(t("nav.knowledge"), style = Boar.type.titleLg, color = c.textHeading)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // BOAR Knowledge first, the library everyone has; then the user's own files.
            packs()
            // Personal Files: the user's own collections, what the Personal Files style answers from. Folded, its title
            // still says how many there are.
            val count = state.collections.size
            team.sopa.boar.ui.BoarSection(if (personalOpen) t("native.documents.personalTitle") else t("native.documents.personalFolded", "count" to count),
                personalOpen, { personalOpen = !personalOpen }, icon = team.sopa.boar.R.drawable.ic_style_personal) {
                Text(t("native.documents.personalNote"), style = Boar.type.subtext, color = c.textSecondary)
                Text(t("personalDocumentsManager.hint"), style = Boar.type.subtext, color = c.textMuted)
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.bgInput).border(1.dp, c.borderDefault, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    if (name.isEmpty()) Text(t("personalDocumentsManager.namePlaceholder"), style = Boar.type.body, color = c.textDim)
                    BasicTextField(name, { name = it }, textStyle = Boar.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(c.emerald500), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                val busy = state.stage != null
                Text(t("personalDocumentsManager.pickButton"), style = Boar.type.title, color = if (busy) c.textDim else c.textInverse,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (busy) c.bgCard else c.emerald600)
                        .clickable(enabled = !busy) {
                            if (name.isBlank()) nameMissing = true
                            else picker.launch(arrayOf("text/plain", "text/markdown", "text/csv", "text/comma-separated-values", "application/json", "application/pdf", "text/*"))
                        }.padding(14.dp))
                state.stage?.let { stage ->
                    val label = when (stage) {
                        "reading" -> t("personalDocumentsManager.stageReading")
                        "chunking" -> t("personalDocumentsManager.stageChunking")
                        else -> t("personalDocumentsManager.stageEmbedding", "current" to state.done, "total" to state.total)
                    }
                    Text(label, style = Boar.type["mono.xs"], color = c.textAccentCyan)
                    LinearProgressIndicator(progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f }, modifier = Modifier.fillMaxWidth(), color = c.emerald500, trackColor = c.bgSubtle)
                }
                state.error?.let { Text("${t("personalDocumentsManager.importFailedTitle")}: $it", style = Boar.type.subtext, color = c.crimson400) }
                // After an import: the default styles answer from everything, so point to the style that answers from the files.
                if (state.stage == null) state.imported?.let { col ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bgCardElevated).border(1.dp, c.emeraldBorder, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("native.documents.importedHint", "name" to col), style = Boar.type.subtext, color = c.textSecondary)
                        team.sopa.boar.ui.chat.ActionButton(null, t("native.documents.askAboutThem")) { vm.dismissImported(); onAskFiles() }
                    }
                }
                if (state.collections.isEmpty()) Text(t("personalDocumentsManager.empty"), style = Boar.type.body, color = c.textMuted)
                for (col in state.collections) Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bgCardElevated).border(1.dp, if (col.active) c.emeraldBorder else c.borderDefault, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(col.name, style = Boar.type.titleSm, color = c.textHeading)
                            Text(t("personalDocumentsManager.docCount", "count" to col.docCount) + " · " + t("personalDocumentsManager.chunkCount", "count" to col.chunkCount),
                                style = Boar.type["mono.xs"], color = c.textDim)
                            // Its files, up to three (DocumentImport joins them with ", "), then how many more.
                            col.sourceFilename?.split(", ")?.filter { it.isNotBlank() }?.let { names ->
                                names.take(3).forEach { Text(it, style = Boar.type.micro, color = c.textDim, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                                if (names.size > 3) Text(t("native.documents.moreFiles", "count" to names.size - 3), style = Boar.type.micro, color = c.textDim)
                            }
                        }
                        Switch(col.active, { vm.setActive(col.id, it) }, colors = SwitchDefaults.colors(checkedTrackColor = c.emerald500, checkedThumbColor = c.textInverse))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        Row(Modifier.clickable { removing = col }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_delete, 16.dp, c.crimson400)
                            Text(t("common.remove"), style = Boar.type.caption, color = c.crimson400, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
    if (nameMissing) AlertDialog({ nameMissing = false }, confirmButton = { TextButton({ nameMissing = false }) { Text(t("native.common.ok")) } },
        title = { Text(t("personalDocumentsManager.nameRequiredTitle")) }, text = { Text(t("personalDocumentsManager.nameRequiredMessage")) },
        containerColor = c.bgCard, titleContentColor = c.textHeading, textContentColor = c.textSecondary)
    removing?.let { col ->
        AlertDialog({ removing = null },
            confirmButton = { TextButton({ vm.remove(col.id); removing = null }) { Text(t("common.remove"), color = c.crimson400) } },
            dismissButton = { TextButton({ removing = null }) { Text(t("common.cancel")) } },
            title = { Text(t("personalDocumentsManager.removeConfirmTitle")) },
            text = { Text(t("personalDocumentsManager.removeConfirmMessage", "name" to col.name, "count" to col.chunkCount)) },
            containerColor = c.bgCard, titleContentColor = c.textHeading, textContentColor = c.textSecondary)
    }
}

