package team.sopa.boar.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import team.sopa.boar.ui.theme.Boar

/**
 * One of the Boar icon pack's drawings (res/drawable/ic_*, Oct 24), tinted like text. The app's controls use these; the
 * answers keep their colored emoji (adam, Oct 24). One icon per meaning everywhere: memory boar-icon-pack.
 * [description]: what an icon-only control does, for screen readers and UI automation (the tests, the chaos monkey);
 * null for an icon beside its own text.
 */
@Composable
fun PackIcon(res: Int, size: Dp, tint: Color = Boar.colors.textHeading, modifier: Modifier = Modifier, description: String? = null) =
    Icon(painterResource(res), description, modifier.size(size), tint = tint)
