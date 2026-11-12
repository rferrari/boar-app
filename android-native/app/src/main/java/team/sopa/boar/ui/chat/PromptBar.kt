package team.sopa.boar.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/** After a send while BOAR answers, the round button rests this long before it can stop the answer. */
private const val SEND_REST_MS = 500L

/**
 * The input bar (styles.inputContainer / input / sendBtn / stopBtn), shared by the chat and Designer (adam, Nov 12: "use
 * the same prompt input component for designer"): a round button on the left ([left] draws its icon, [onLeft] opens
 * its sheet: the chat's answer styles, Designer's drawer), the field with [placeholder] and the mic inside it on the
 * right (VoiceInputButton.tsx), and one round button that sends ([sendIcon], [sendLabel]) or stops.
 *
 * [answering]: BOAR is busy (answering, or drawing). [queues]: typing while busy turns the button back into Send (the
 * chat's queue); without it the button stays Stop while busy (Designer draws one picture at a time). [enabled] false
 * (Designer without its model) leaves nothing to send. [highlight] colors the field's border (Deep Research).
 */
@Composable
fun PromptBar(
    text: String, onText: (String) -> Unit, answering: Boolean, onSend: () -> Unit, onStop: () -> Unit, voiceEnabled: Boolean,
    placeholder: String, onLeft: () -> Unit, left: @Composable () -> Unit, leftLabel: String? = null,
    stopping: Boolean = false, highlight: Boolean = false, placeholderColor: Color? = null,
    sendIcon: Int = team.sopa.boar.R.drawable.ic_send, sendLabel: String = t("native.a11y.send"),
    queues: Boolean = true, enabled: Boolean = true,
) {
    val c = Boar.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
    // Tight gaps, full-size buttons: the field gets the room, and the round buttons keep a 48 dp tap target.
    Row(Modifier.fillMaxWidth().background(c.bgCardElevated).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.bgInput).border(1.dp, c.borderDefault, CircleShape).clickable(onClickLabel = leftLabel, onClick = onLeft), contentAlignment = Alignment.Center) {
            left()
        }
        // A pill on one line; a rounded box once the question wraps (a pill's ends cut into the text).
        var lines by remember { mutableStateOf(1) }
        val fieldShape = if (lines > 1) RoundedCornerShape(16.dp) else RoundedCornerShape(50)
        Row(Modifier.weight(1f).clip(fieldShape).background(c.bgInput).border(if (highlight) 1.5.dp else 1.dp, if (highlight) c.frontierBadgeBorder else c.borderDefault, fieldShape)
            .padding(start = 18.dp, end = if (voiceEnabled) 6.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                val style = Boar.type.body.copy(fontSize = 14.sp, color = c.textPrimary)
                if (text.isEmpty()) Text(placeholder, style = style, color = placeholderColor ?: c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(text, onText, textStyle = style, cursorBrush = SolidColor(c.emerald500), modifier = Modifier.fillMaxWidth(), maxLines = 5,
                    enabled = enabled, onTextLayout = { lines = it.lineCount })
            }
            if (voiceEnabled) MicButton(text, onText, enabled = !answering && enabled, size = 34.dp)
        }
        val canSend = enabled && text.isNotBlank()
        // One round button that follows what the user does (adam, Nov 12): while BOAR answers it's Stop, and Send (to the
        // queue) while something is typed. After a send it ignores taps for half a second, dimmed, so a second tap meant
        // for Send can't land on the Stop it turns back into (the bug of Nov 11).
        val stops = answering && (!canSend || !queues)
        var resting by remember { mutableStateOf(false) }
        LaunchedEffect(resting) { if (resting) { kotlinx.coroutines.delay(SEND_REST_MS); resting = false } }
        val bg = if (stops) c.crimson600 else if (canSend) c.emerald600 else c.borderElevated
        // After Stop, the button pulses until the answer really ends: a library search or a chunk of prompt finishes first.
        val transition = rememberInfiniteTransition(label = "stopping")
        val blink by transition.animateFloat(1f, 0.35f, infiniteRepeatable(tween<Float>(500), RepeatMode.Reverse), label = "pulse")
        val pulse = if (stopping) blink else 1f
        Box(
            Modifier.size(48.dp).alpha(if (resting) 0.45f else if (answering || canSend) pulse else 0.35f).clip(CircleShape).background(bg)
                .clickable(enabled = (answering || canSend) && !resting) { if (stops) onStop() else { onSend(); resting = true } },
            contentAlignment = Alignment.Center,
        ) { if (stops) team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_stop, 18.dp, Color.White, description = t("native.a11y.stop")) else team.sopa.boar.ui.PackIcon(sendIcon, 20.dp, Color.White, description = sendLabel) }
    }
}

/** Dictation: tap to listen (asks for the microphone the first time), tap again to stop; the words go into the input. */
@Composable
private fun MicButton(text: String, onText: (String) -> Unit, enabled: Boolean, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val c = Boar.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (!team.sopa.boar.voice.VoiceInput.available(ctx)) return
    val voice = remember { team.sopa.boar.voice.VoiceInput(ctx) }
    var listening by remember { mutableStateOf(false) }
    val before = remember { mutableStateOf("") }
    val disposed = remember { booleanArrayOf(false) }
    // The permission answer comes later: by then an answer may have started and disabled the mic.
    val enabledNow = androidx.compose.runtime.rememberUpdatedState(enabled)
    DisposableEffect(Unit) { onDispose { disposed[0] = true; voice.stop() } }
    fun listen() {
        before.value = text.trimEnd()
        listening = true
        val join = { said: String -> listOf(before.value, said).filter { it.isNotBlank() }.joinToString(" ") }
        voice.start(onPartial = { onText(join(it)) }, onResult = { onText(join(it)) }, onEnd = { listening = false })
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> if (granted && !disposed[0] && enabledNow.value) listen() }
    Box(Modifier.size(size).alpha(if (enabled) 1f else 0.35f).clip(CircleShape).background(if (listening) c.crimson600 else c.bgInput)
        .border(1.dp, if (listening) c.crimsonBorder else c.borderDefault, CircleShape)
        .clickable(enabled = enabled) {
            when {
                listening -> { voice.stop(); listening = false }
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED -> listen()
                else -> permission.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }, contentAlignment = Alignment.Center) {
        if (listening) team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_stop, 18.dp, Color.White, description = t("native.a11y.stopListening")) else team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_mic, 20.dp, c.textHeading, description = t("native.a11y.mic"))
    }
}
