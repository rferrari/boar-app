package team.sopa.boar.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sopa.boar.chat.ChatState
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * The header's style button: every answer style with what it does, the current one marked, and Deep Research below
 * (it runs in any style). Replaces cycling through the styles blind. Engine presets stay in Advanced tweaks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToneSheet(state: ChatState, onSetTone: (String) -> Unit, onSetDeepResearch: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val c = Boar.colors
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bgSurface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("modelSetupScreen.sections.tone"), style = Boar.type.titleLg, color = c.textHeading)
            for ((id, icon) in state.tones) {
                val selected = id == state.personalityId
                val shape = RoundedCornerShape(10.dp)
                Row(
                    Modifier.fillMaxWidth().background(if (selected) c.emeraldBgSubtle else c.bgCard, shape)
                        .border(1.dp, if (selected) c.emeraldBorder else c.borderSubtle, shape)
                        .clickable { onSetTone(id); onDismiss() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(icon, fontSize = 20.sp)
                    Column(Modifier.weight(1f)) {
                        Text(t("personalities.$id.label"), style = Boar.type.body, color = if (selected) c.textAccentEmerald else c.textPrimary)
                        Text(t("personalities.$id.description"), style = Boar.type.subtext, color = c.textMuted)
                    }
                    if (selected) Text("✓", color = c.textAccentEmerald, fontSize = 18.sp)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = c.borderSubtle)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🔬", fontSize = 20.sp)
                Column(Modifier.weight(1f)) {
                    Text(t("personalitySettings.deepResearchTitle"), style = Boar.type.body, color = c.textPrimary)
                    Text(t("personalitySettings.deepResearchNote"), style = Boar.type.subtext, color = c.textMuted)
                }
                Switch(state.deepResearch, onSetDeepResearch, colors = SwitchDefaults.colors(checkedTrackColor = c.emerald500, checkedThumbColor = c.textInverse))
            }
        }
    }
}
