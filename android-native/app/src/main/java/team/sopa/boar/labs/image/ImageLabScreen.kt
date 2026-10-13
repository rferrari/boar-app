package team.sopa.boar.labs.image

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.io.File
import java.util.Locale

/** Image Gen as a Boar Labs plugin: the screen stays on while it draws or downloads, and back waits for the drawing. */
object ImageGenLab : team.sopa.boar.labs.LabPlugin {
    override val id = "image"
    override val emoji = "🎨"
    override val status = team.sopa.boar.labs.LabStatus.BETA

    @Composable
    override fun Content() {
        val vm: ImageLabViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        val state by vm.state.collectAsState()
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.DisposableEffect(state.phase, state.downloadPct) {
            view.keepScreenOn = state.phase != null || state.downloadPct != null
            onDispose { view.keepScreenOn = false }
        }
        if (state.phase != null) androidx.activity.compose.BackHandler { vm.cancel() }
        ImageLabScreen(state, vm)
    }
}

/** Pick a style, type the subject, draw. The last images stay on the phone below. */
@Composable
fun ImageLabScreen(state: ImageLabState, vm: ImageLabViewModel) {
    val c = Boar.colors
    val ctx = LocalContext.current
    var subject by rememberSaveable { mutableStateOf("") }
    val busy = state.phase != null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("native.labs.image.intro"), style = Boar.type.caption, color = c.textMuted)
            when {
                !state.engineFound -> Text(t("native.labs.image.noEngine"), style = Boar.type.body, color = c.textSecondary)
                !state.modelPresent -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("native.labs.image.needModel"), style = Boar.type.body, color = c.textSecondary)
                    if (state.downloadPct != null) Busy(t("native.labs.image.downloading", "pct" to state.downloadPct))
                    else Button(t("native.labs.image.download")) { vm.download() }
                }
                else -> {
                    Label(t("native.labs.image.style"))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ImageLab.Style.entries.forEach { s -> Chip(t("native.labs.image.style.${s.key}"), s == state.style, Modifier.weight(1f)) { vm.setStyle(s) } }
                    }
                    Label(t("native.labs.image.size"))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ImageLab.SIZES.forEach { px -> Chip(t("native.labs.image.size.$px"), px == state.size, Modifier.weight(1f)) { vm.setSize(px) } }
                    }
                    Label(t("native.labs.image.subject"))
                    BasicTextField(subject, { subject = it }, singleLine = true, enabled = !busy,
                        textStyle = Boar.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(c.textAccentEmerald),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { vm.generate(subject) }),
                        modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(8.dp)).border(1.dp, c.borderSubtle, RoundedCornerShape(8.dp)).padding(12.dp),
                        decorationBox = { inner -> if (subject.isEmpty()) Text(t("native.labs.image.placeholder"), style = Boar.type.body, color = c.textDim); inner() })
                    state.lastChat?.takeIf { it != subject && !busy }?.let { title ->
                        Text(t("native.labs.image.fromChat", "title" to title), style = Boar.type.caption, color = c.textAccentCyan,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { subject = title }.padding(vertical = 4.dp))
                    }
                    Text(state.style.prompt(subject.ifBlank { "…" }), style = Boar.type["mono.xs"], color = c.textDim)
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Busy(t("native.labs.image.phase.${state.phase}"))
                        Button(t("common.cancel")) { vm.cancel() }
                    } else Button(t("native.labs.image.generate"), enabled = subject.isNotBlank()) { vm.generate(subject) }
                }
            }
            state.error?.let { Text(it, style = Boar.type.caption, color = c.crimson500) }
            state.image?.let { f ->
                Picture(f, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.seconds?.let { Text(String.format(Locale.US, "%.1f s", it), style = Boar.type["mono.xs"], color = c.textAccentCyan, modifier = Modifier.weight(1f)) }
                        ?: Box(Modifier.weight(1f))
                    Button(t("native.labs.image.share")) { ctx.startActivity(vm.shareIntent(f)) }
                    Button(t("native.labs.image.delete")) { vm.delete(f) }
                }
            }
            if (state.gallery.isNotEmpty()) {
                Label(t("native.labs.image.gallery"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.gallery, key = { it.path }) { f ->
                        Picture(f, Modifier.size(72.dp).clip(RoundedCornerShape(6.dp))
                            .then(if (f == state.image) Modifier.border(2.dp, c.emerald400, RoundedCornerShape(6.dp)) else Modifier)
                            .clickable { vm.show(f) })
                    }
                }
            }
        }
}

@Composable
private fun Picture(f: File, modifier: Modifier) {
    val bmp by produceState<ImageBitmap?>(null, f.path, f.lastModified()) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
    }
    Box(modifier.background(Boar.colors.bgCard)) { bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
}

@Composable
private fun Label(text: String) = Text(text, style = Boar.type["mono.xs"], color = Boar.colors.textDim, fontWeight = FontWeight.Bold)

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Boar.colors
    Text(label, style = Boar.type.caption.copy(fontSize = 12.sp), color = if (selected) c.textAccentEmerald else c.textSecondary, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) c.emeraldBgSubtle else c.bgCard)
            .border(1.dp, if (selected) c.emeraldBorder else c.borderSubtle, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
private fun Button(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Boar.colors
    Text(label, style = Boar.type.titleSm.copy(fontSize = 13.sp), color = if (enabled) c.textAccentEmerald else c.textDim,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.emeraldBgSubtle).border(1.dp, c.emeraldBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
private fun Busy(label: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    CircularProgressIndicator(Modifier.size(16.dp), color = Boar.colors.emerald400, strokeWidth = 1.5.dp)
    Text(label, style = Boar.type.caption, color = Boar.colors.textAccentEmerald)
}
