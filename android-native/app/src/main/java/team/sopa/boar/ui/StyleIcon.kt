package team.sopa.boar.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import team.sopa.boar.ui.theme.Boar

/**
 * An answer style's icon where it shows big (Settings, setup, the chat's style sheet): the Boar icon pack's drawing
 * when it has one (Oct 24), else the style's own emoji. The chat's small style button keeps the emoji.
 */
@Composable
fun StyleIcon(id: String, emoji: String, size: Dp, tint: Color = Boar.colors.textHeading) {
    val drawable = when (id) {
        "succinct" -> team.sopa.boar.R.drawable.ic_style_quick
        "detailed" -> team.sopa.boar.R.drawable.ic_settings_knowledge
        "custom" -> team.sopa.boar.R.drawable.ic_style_custom
        else -> null
    }
    if (drawable != null) Icon(painterResource(drawable), null, Modifier.size(size), tint = tint)
    else Text(emoji, fontSize = (size.value * 0.9f).sp)
}
