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
 * An answer style's icon outside the chat (Settings, setup): the Boar icon pack's drawing when it has one (Oct 24), else
 * the style's own emoji. The chat keeps the colored emoji everywhere, so its controls stand out (adam, Oct 24).
 */
@Composable
fun StyleIcon(id: String, emoji: String, size: Dp, tint: Color = Boar.colors.textHeading) {
    val drawable = when (id) {
        "succinct" -> team.sopa.boar.R.drawable.ic_style_quick
        "detailed" -> team.sopa.boar.R.drawable.ic_settings_knowledge
        "personal" -> team.sopa.boar.R.drawable.ic_style_personal
        "custom" -> team.sopa.boar.R.drawable.ic_style_custom
        // Not a style, but shown with them (setup's Deep Research card).
        "deep" -> team.sopa.boar.R.drawable.ic_deep_research
        else -> null
    }
    if (drawable != null) Icon(painterResource(drawable), null, Modifier.size(size), tint = tint)
    else Text(emoji, fontSize = (size.value * 0.9f).sp)
}
