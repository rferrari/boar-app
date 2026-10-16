package team.sopa.boar.ui.security

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.sopa.boar.R
import team.sopa.boar.engine.security.PinKeys
import team.sopa.boar.security.PinLock
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/** Digits as dots and a number pad (no system keyboard, so no keyboard app sees the PIN). */
@Composable
private fun PinPad(title: String, subtitle: String?, error: String?, busy: Boolean, onSubmit: (String) -> Unit, footer: @Composable () -> Unit = {}) {
    val c = Boar.colors
    var pin by remember(title) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(c.bgTerminal).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.brand_pfp), null, Modifier.size(64.dp).clip(CircleShape))
        Text(title, style = Boar.type.titleLg, color = c.textHeading, textAlign = TextAlign.Center)
        subtitle?.let { Text(it, style = Boar.type.body, color = c.textSecondary, textAlign = TextAlign.Center) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) c.textAccentEmerald else c.bgCard).border(1.dp, c.borderDefault, CircleShape))
            }
        }
        Text(error ?: " ", style = Boar.type.subtext, color = c.crimson400, textAlign = TextAlign.Center)
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { k ->
                    val enabled = !busy && when (k) { "⌫" -> pin.isNotEmpty(); "✓" -> PinKeys.valid(pin); else -> pin.length < 8 }
                    Box(Modifier.size(72.dp).clip(CircleShape).background(if (k == "✓" && enabled) c.emeraldBgSubtle else c.bgCard)
                        .border(1.dp, if (k == "✓" && enabled) c.emeraldBorder else c.borderDefault, CircleShape)
                        .clickable(enabled = enabled) {
                            when (k) { "⌫" -> pin = pin.dropLast(1); "✓" -> { onSubmit(pin); pin = "" }; else -> pin += k }
                        }, contentAlignment = Alignment.Center) {
                        Text(k, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) c.textHeading else c.textDim)
                    }
                }
            }
        }
        footer()
        Spacer(Modifier.weight(1f))
    }
}

/** On start, and after a minute away: the PIN opens the app and the chats. */
@Composable
fun LockScreen(lock: PinLock, onUnlocked: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var wait by remember { mutableLongStateOf(lock.waitSeconds()) }
    var forgetting by remember { mutableStateOf(false) }
    LaunchedEffect(wait) { if (wait > 0) { delay(1000); wait = lock.waitSeconds() } }
    val waitText = if (wait > 0) t("native.pin.wait", "seconds" to wait) else null
    val wrongText = t("native.pin.wrong")
    PinPad(t("native.pin.enterTitle"), t("native.pin.enterSubtitle"), waitText ?: error, busy || wait > 0, onSubmit = { pin ->
        busy = true
        scope.launch {
            // PBKDF2 takes a moment: off the main thread.
            val r = withContext(Dispatchers.Default) { lock.unlock(pin) }
            busy = false
            when (r) {
                PinLock.Attempt.Ok -> onUnlocked()
                is PinLock.Attempt.Wrong -> { error = wrongText; wait = r.waitSeconds }
                is PinLock.Attempt.Wait -> wait = r.seconds
            }
        }
    }) {
        Text(t("native.pin.forgot"), style = Boar.type.caption, color = Boar.colors.textAccentCyan, modifier = Modifier.clickable { forgetting = true }.padding(8.dp))
    }
    if (forgetting) ForgetDialog(onErase = { forgetting = false; scope.launch { withContext(Dispatchers.IO) { lock.forget() }; onUnlocked() } }) { forgetting = false }
}

/** How long "Erase your chats?" stays open: time to read it and decide; then it closes by itself. */
private const val FORGET_WINDOW_MS = 8_000L

/**
 * The self-destruct question, with a countdown: the erase must be read and tapped within [FORGET_WINDOW_MS], or the
 * dialog closes and nothing is erased. Counted on the wall clock, so a test's virtual time can't close it early.
 */
@Composable
private fun ForgetDialog(onErase: () -> Unit, onClose: () -> Unit) {
    val openedAt = remember { android.os.SystemClock.elapsedRealtime() }
    var left by remember { mutableLongStateOf(FORGET_WINDOW_MS) }
    LaunchedEffect(Unit) {
        while (true) {
            left = FORGET_WINDOW_MS - (android.os.SystemClock.elapsedRealtime() - openedAt)
            if (left <= 0) { onClose(); break }
            delay(200)
        }
    }
    val seconds = (left + 999) / 1000
    AlertDialog(onClose,
        title = { Text(t("native.pin.forgotTitle")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("native.pin.forgotBody"))
                androidx.compose.material3.LinearProgressIndicator(progress = { (left.toFloat() / FORGET_WINDOW_MS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = Boar.colors.crimson400, trackColor = Boar.colors.bgSubtle)
                Text(t("native.pin.forgotCountdown", "seconds" to seconds), style = Boar.type.caption, color = Boar.colors.textSecondary)
            }
        },
        confirmButton = {
            // A tap that lands after the deadline (between two countdown ticks) closes instead of erasing.
            TextButton({ if (android.os.SystemClock.elapsedRealtime() - openedAt < FORGET_WINDOW_MS) onErase() else onClose() }) { Text(t("native.pin.forgotConfirm"), color = Boar.colors.crimson400, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        },
        // One countdown, the line above; at zero the question closes and nothing is erased.
        dismissButton = { TextButton(onClose) { Text(t("common.cancel"), color = Boar.colors.textSecondary) } })
}

enum class PinFlow { NEW, CHANGE, REMOVE }

/** Set a PIN (new, then again), change it (current, new, again) or remove it (current). */
@Composable
fun PinSetup(lock: PinLock, flow: PinFlow, onDone: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (flow == PinFlow.NEW) "new" else "current") }
    var current by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val title = when (step) { "current" -> t("native.pin.currentTitle"); "new" -> t("native.pin.newTitle"); else -> t("native.pin.confirmTitle") }
    val subtitle = if (step == "new") t("native.pin.newSubtitle") else null
    val wrong = t("native.pin.wrong")
    val mismatch = t("native.pin.mismatch")
    PinPad(title, subtitle, error, busy, onSubmit = { pin ->
        error = null
        when (step) {
            "current" -> {
                busy = true
                scope.launch {
                    if (flow == PinFlow.REMOVE) {
                        val ok = withContext(Dispatchers.Default) { lock.disable(pin) }
                        busy = false
                        if (ok) onDone() else error = wrong
                    } else {
                        // Checked when the new PIN is saved (change() re-derives); keep it until then.
                        current = pin; busy = false; step = "new"
                    }
                }
            }
            "new" -> { first = pin; step = "confirm" }
            else -> if (pin != first) { error = mismatch; step = "new" } else {
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { if (flow == PinFlow.NEW) { lock.enable(pin); true } else lock.change(current, pin) }
                    busy = false
                    if (ok) onDone() else { error = wrong; step = "current" }
                }
            }
        }
    }) {
        Text(t("common.cancel"), style = Boar.type.caption, color = Boar.colors.textSecondary, modifier = Modifier.clickable(onClick = onCancel).padding(8.dp))
    }
}

/** The last step of setup: offer a PIN, never require it. */
@Composable
fun PinOffer(onSet: () -> Unit, onSkip: () -> Unit) {
    val c = Boar.colors
    Column(Modifier.fillMaxSize().background(c.bgTerminal).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Image(painterResource(R.drawable.brand_pfp), null, Modifier.size(72.dp).clip(CircleShape))
        Text(t("native.pin.offerTitle"), style = Boar.type.titleLg, color = c.textHeading, textAlign = TextAlign.Center)
        Text(t("native.pin.offerBody"), style = Boar.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(t("native.pin.offerSet"), style = Boar.type.titleSm, color = c.textAccentEmerald, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.emeraldBgSubtle).border(1.dp, c.emeraldBorder, RoundedCornerShape(10.dp)).clickable(onClick = onSet).padding(14.dp))
        Text(t("native.pin.offerSkip"), style = Boar.type.body, color = c.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSkip).padding(12.dp))
    }
}
