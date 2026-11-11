package team.sopa.boar.labs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import team.sopa.boar.engine.labs.Sketch
import team.sopa.boar.ui.theme.Boar

/**
 * Designer's wait (adam, Nov 12): pen strokes sketched on the canvas in the picked style's ink and paper, until the
 * picture lands and takes their place (a crossfade, [revealMs]). Decoration only: sd-cli draws in one step and reports no
 * progress. With animations off, one calm frame and the picture at once. The Matrix rain choice keeps the rain.
 */
@Composable
fun DesignerWait(style: String?, modifier: Modifier = Modifier, label: String? = null) {
    LabPrefs.init(LocalContext.current)
    if (LabPrefs.waitAnimation == LabPrefs.WAIT_MATRIX) MatrixRain(modifier, label) else SketchWait(style, modifier, label)
}

@Composable
private fun SketchWait(style: String?, modifier: Modifier, label: String?) {
    val still = animationsOff()
    val strokes = remember { Sketch.strokes() }
    val palette = remember(style) { Sketch.palette(style) }
    var now by remember { mutableLongStateOf(0L) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    if (!still) LaunchedEffect(lifecycle) {
        val start = System.nanoTime()
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) withFrameNanos { now = it - start } }
    }
    val ink = Color(palette.ink)
    Box(modifier.background(Color(palette.paper))) {
        Canvas(Modifier.fillMaxSize()) {
            val shown = Sketch.progress(strokes, now / 1e9, still)
            for ((s, p) in strokes.zip(shown)) {
                if (p <= 0f) continue
                val n = (p * (s.points.size - 1)).toInt().coerceAtLeast(1)
                for (k in 1..n) {
                    val (x0, y0) = s.points[k - 1]; val (x1, y1) = s.points[k]
                    drawLine(ink.copy(alpha = 0.78f), Offset(x0 * size.width, y0 * size.height), Offset(x1 * size.width, y1 * size.height),
                        strokeWidth = s.width * size.minDimension / 180f, cap = StrokeCap.Round)
                }
            }
        }
        label?.let {
            Text(it, style = Boar.type["mono.xs"], color = ink, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).background(Color(palette.paper).copy(alpha = 0.85f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

/** How long the sketch takes to fade into the picture; 0 with animations off. */
@Composable
fun revealMs(): Int = if (animationsOff()) 0 else REVEAL_MS

private const val REVEAL_MS = 700

@Composable
private fun animationsOff(): Boolean {
    val ctx = LocalContext.current
    return remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
