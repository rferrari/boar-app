package team.sopa.boar.labs.designer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import team.sopa.boar.engine.labs.DesignerCrop
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.io.File

/**
 * Crop (BOAR-88): the shown picture on black with a frame over it. Drag inside the frame to move it, drag its corner
 * handle to resize it; 16:9, 4:3, 9:16 or Free below. "Save copy" saves the frame as a new drawing beside the original,
 * which stays (ImageLabViewModel.crop). Without dragging (TalkBack), a ratio and Save copy crop the centre.
 */
@Composable
internal fun CropScreen(f: File, styleKey: String?, onSave: (DesignerCrop.Rect) -> Unit, onClose: () -> Unit) {
    val c = Boar.colors
    val (imgW, imgH) = remember(f.path) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        o.outWidth to o.outHeight
    }
    if (imgW <= 0 || imgH <= 0) { LaunchedEffect(Unit) { onClose() }; return }
    var ratio by remember { mutableStateOf(DesignerCrop.Ratio.WIDE) }
    var rect by remember { mutableStateOf(DesignerCrop.initial(DesignerCrop.Ratio.WIDE, imgW, imgH)) }
    val bmp by produceState<ImageBitmap?>(null, f.path) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
    }
    val handle = with(LocalDensity.current) { 44.dp.toPx() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("native.labs.image.crop"), style = Boar.type.titleSm, color = Color.White)
            Text(t("native.labs.image.cropNote"), style = Boar.type.caption, color = Color.White.copy(alpha = 0.7f))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(imgW.toFloat() / imgH)) {
                    // Picture pixels per screen pixel.
                    val scale = imgW / constraints.maxWidth.toFloat()
                    bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                    Canvas(Modifier.fillMaxSize().pointerInput(ratio, scale) {
                        var resizing = false
                        var accX = 0f; var accY = 0f
                        detectDragGestures(onDragStart = { p ->
                            val r = rect
                            resizing = kotlin.math.abs(p.x - (r.x + r.w) / scale) < handle && kotlin.math.abs(p.y - (r.y + r.h) / scale) < handle
                            accX = 0f; accY = 0f
                        }) { change, drag ->
                            change.consume()
                            accX += drag.x * scale; accY += drag.y * scale
                            val dx = accX.toInt(); val dy = accY.toInt()
                            accX -= dx; accY -= dy
                            if (dx != 0 || dy != 0) rect = if (resizing) DesignerCrop.resize(rect, dx, dy, ratio, imgW, imgH) else DesignerCrop.move(rect, dx, dy, imgW, imgH)
                        }
                    }) {
                        val r = rect
                        val left = r.x / scale; val top = r.y / scale; val right = (r.x + r.w) / scale; val bottom = (r.y + r.h) / scale
                        val dim = Color.Black.copy(alpha = 0.6f)
                        // What is cut away, dimmed; the frame and its corner handle on top.
                        drawRect(dim, Offset.Zero, Size(size.width, top))
                        drawRect(dim, Offset(0f, bottom), Size(size.width, size.height - bottom))
                        drawRect(dim, Offset(0f, top), Size(left, bottom - top))
                        drawRect(dim, Offset(right, top), Size(size.width - right, bottom - top))
                        drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(2.dp.toPx()))
                        drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(right, bottom))
                        drawCircle(c.emerald500, radius = 6.dp.toPx(), center = Offset(right, bottom))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                for (r in DesignerCrop.Ratio.entries) {
                    val on = r == ratio
                    Text(if (r == DesignerCrop.Ratio.FREE) t("native.labs.image.cropFree") else r.key, style = Boar.type.caption,
                        color = if (on) c.textInverse else Color.White, maxLines = 1,
                        modifier = Modifier.heightIn(min = 40.dp).background(if (on) c.emerald500 else Color.Transparent, RoundedCornerShape(50))
                            .border(1.dp, if (on) c.emerald500 else Color.White.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable { ratio = r; rect = DesignerCrop.initial(r, imgW, imgH) }.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("common.cancel"), style = Boar.type.body, color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onClose).padding(vertical = 13.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(t("native.labs.image.cropSave"), style = Boar.type.body, color = c.textInverse, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).background(c.emerald500, RoundedCornerShape(10.dp))
                        .clickable { onSave(rect) }.padding(vertical = 13.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
