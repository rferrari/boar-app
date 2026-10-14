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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sopa.boar.ui.theme.Boar
import kotlin.random.Random

/**
 * Green code falling in columns, for "the phone is working on it" (Image Gen while it draws). Each column has its own
 * speed and start, fixed by its index, so the rain is the same shape every time; the bright glyph leads, the trail fades.
 * [label], when given, sits in the middle on a dark pill.
 */
@Composable
fun MatrixRain(modifier: Modifier = Modifier, label: String? = null) {
    val c = Boar.colors
    val green = c.emerald400
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val glyphs = remember(green) { GLYPHS.map { measurer.measure(it.toString(), style) } }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { now = it } }
    Box(modifier.background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = 15.dp.toPx()
            val cols = (size.width / cell).toInt().coerceAtLeast(1)
            val rows = (size.height / cell).toInt() + 1
            val sec = now / 1e9
            for (col in 0 until cols) {
                val rnd = Random(col * 7919 + 13)
                val speed = 5f + rnd.nextFloat() * 9f // rows per second
                val start = rnd.nextFloat() * (rows + TRAIL)
                val head = ((sec * speed + start) % (rows + TRAIL)).toInt()
                for (k in 0 until TRAIL) {
                    val row = head - k
                    if (row !in 0 until rows) continue
                    // The glyph under the head changes as it moves; the trail keeps what it showed.
                    val g = glyphs[Math.floorMod(col * 31 + row * 17 + (if (k == 0) (sec * 8).toInt() else 0), glyphs.size)]
                    drawText(g, color = if (k == 0) Color.White else green, topLeft = Offset(col * cell, row * cell),
                        alpha = if (k == 0) 1f else (1f - k.toFloat() / TRAIL) * 0.85f)
                }
            }
        }
        label?.let {
            Text(it, style = Boar.type["mono.xs"], color = c.textAccentEmerald, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

private const val TRAIL = 14
private const val GLYPHS = "01ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789ABCDEF"
