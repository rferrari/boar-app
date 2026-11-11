package team.sopa.boar.labs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import team.sopa.boar.engine.labs.Trail
import team.sopa.boar.ui.theme.Boar

/**
 * The waiting animation (rc3 slice 4, adam): a boar trots across and leaves hoofprints that fade behind it (Trail).
 * Same call shape as [MatrixRain], so either can stand anywhere the phone is working. It stops drawing while BOAR isn't
 * on screen, and holds one calm frame when Android's animations are off.
 */
@Composable
fun BoarTrail(modifier: Modifier = Modifier, label: String? = null, showBoar: Boolean = true) {
    val c = Boar.colors
    val ink = rainColor(c)
    val ctx = LocalContext.current
    val still = remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var now by remember { mutableLongStateOf(0L) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    if (!still) LaunchedEffect(lifecycle) {
        // Frames only while BOAR is on screen: a backgrounded wait costs nothing.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) withFrameNanos { now = it } }
    }
    BoxWithConstraints(modifier.background(Color.Black)) {
        val frame = Trail.at(now / 1e9, still)
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.012f
            for (p in frame.prints) {
                val at = Offset(p.x * size.width, p.y * size.height)
                // A hoof: two toes side by side.
                drawOval(ink.copy(alpha = p.alpha * 0.9f), topLeft = at + Offset(-r * 1.6f, -r), size = Size(r * 1.4f, r * 2.2f))
                drawOval(ink.copy(alpha = p.alpha * 0.9f), topLeft = at + Offset(r * 0.2f, -r), size = Size(r * 1.4f, r * 2.2f))
            }
        }
        val side = minOf(maxWidth, maxHeight) * 0.22f
        // The closing screen shows the hoofprints alone (adam, Nov 10); Designer's wait keeps the boar.
        if (showBoar) Image(painterResource(team.sopa.boar.R.drawable.mascot), null, Modifier.size(side)
            .offset(x = maxWidth * frame.boar.x - side / 2, y = maxHeight * frame.boar.y - side * 0.8f))
        label?.let {
            Text(it, style = Boar.type["mono.xs"], color = ink, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

/** The waiting animation the user picked (Boar Labs settings, Pro): the boar's trail by default, the matrix rain as before. */
@Composable
fun WaitingAnimation(modifier: Modifier = Modifier, label: String? = null, showBoar: Boolean = true) {
    LabPrefs.init(LocalContext.current)
    if (LabPrefs.waitAnimation == LabPrefs.WAIT_MATRIX) MatrixRain(modifier, label) else BoarTrail(modifier, label, showBoar)
}
