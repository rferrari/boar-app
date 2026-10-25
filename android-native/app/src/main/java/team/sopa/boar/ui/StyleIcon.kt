package team.sopa.boar.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import team.sopa.boar.ui.theme.Boar

/**
 * An answer style's icon: the Boar icon pack's drawing when it has one (Oct 24), else the style's own emoji. In Settings,
 * setup, and the chat's style button and sheet (one look for the styles); the chat's other emoji stay (adam, Oct 24).
 */
@Composable
fun StyleIcon(id: String, emoji: String, size: Dp, tint: Color = Boar.colors.textHeading, description: String? = null) {
    val drawable = when (id) {
        "succinct" -> team.sopa.boar.R.drawable.ic_style_quick
        "detailed" -> team.sopa.boar.R.drawable.ic_settings_knowledge
        "personal" -> team.sopa.boar.R.drawable.ic_style_personal
        "custom" -> team.sopa.boar.R.drawable.ic_style_custom
        // Not a style, but shown with them (setup's Deep Research card).
        "deep" -> team.sopa.boar.R.drawable.ic_deep_research
        else -> null
    }
    val labelled = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    if (drawable != null) Icon(painterResource(drawable), description, Modifier.size(size), tint = tint)
    else Text(emoji, fontSize = (size.value * 0.9f).sp, modifier = labelled)
}
