package team.sopa.boar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import team.sopa.boar.ui.theme.Boar

/**
 * The app's one toast (B1, Oct 13 lab test): at the top just below the header, never over the input or Send; fades
 * in, hides by itself (longer with an action), and a new one replaces the one showing. Any screen or view model calls
 * [show]; [ToastHost] sits once over the whole app.
 */
object Toasts {
    class Toast(val text: String, val image: Int? = null, val action: String? = null, val onAction: (() -> Unit)? = null, val millis: Long = if (action != null) 6_000 else 1_800) {
        internal val id = System.nanoTime()
    }

    private val _current = MutableStateFlow<Toast?>(null)
    val current: StateFlow<Toast?> = _current

    fun show(toast: Toast) { _current.value = toast }
    fun show(text: String) = show(Toast(text))
    internal fun hide(t: Toast) { _current.compareAndSet(t, null) }
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val toast by Toasts.current.collectAsState()
    val t = toast ?: return
    val c = Boar.colors
    val alpha = remember(t.id) { Animatable(0f) }
    LaunchedEffect(t.id) {
        alpha.animateTo(1f, tween(150))
        kotlinx.coroutines.delay(t.millis)
        alpha.animateTo(0f, tween(200))
        Toasts.hide(t)
    }
    // Inverse (adam, Nov 14: the dark toast was "one more card" on a page of cards): a light card in both themes
    // (textHeading), dark text, a soft shadow and no border; an action is an accent pill, readable on the light card.
    Row(
        modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 84.dp).widthIn(max = 560.dp).fillMaxWidth()
            .graphicsLayer { this.alpha = alpha.value }
            .shadow(8.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(c.textHeading)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        t.image?.let { Image(painterResource(it), null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))) }
        Text(t.text, style = Boar.type.body, color = c.textInverse, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (t.action != null) Text(t.action, style = Boar.type.body, color = c.textInverse, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.emerald500)
                .clickable { t.onAction?.invoke(); Toasts.hide(t) }.padding(vertical = 6.dp, horizontal = 12.dp))
    }
}
