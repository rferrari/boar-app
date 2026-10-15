package team.sopa.boar.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import team.sopa.boar.app.BundledAssets
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/** The offline APK's first launch: its models are copied out of the APK and checked, with progress; then [onDone]. */
@Composable
fun UnpackScreen(bundled: BundledAssets, onDone: () -> Unit) {
    val c = Boar.colors
    var done by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(1L) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        val failure = withContext(Dispatchers.IO) {
            runCatching { bundled.unpack { d, t -> done = d; total = t } }.exceptionOrNull()
        }
        when (failure) {
            null -> onDone()
            is BundledAssets.NotEnoughSpace -> error = "space:" + "%.1f".format(failure.neededBytes / 1e9)
            else -> error = "failed"
        }
    }
    Column(
        Modifier.fillMaxSize().background(c.bgTerminal).statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text(t("native.unpack.title"), style = Boar.type.titleLg, color = c.textHeading)
        Text(t("native.unpack.body"), style = Boar.type.body, color = c.textSecondary)
        val e = error
        if (e == null) {
            LinearProgressIndicator(progress = { (done.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = c.emerald500)
            Text(t("native.unpack.progress", "done" to "%.1f".format(done / 1e9), "total" to "%.1f".format(total / 1e9)), style = Boar.type.subtext, color = c.textMuted)
        } else {
            Text(if (e.startsWith("space:")) t("native.unpack.noSpace", "gb" to e.removePrefix("space:")) else t("native.unpack.failed"),
                style = Boar.type.body, color = c.crimson400)
            TextButton({ attempt++ }) { Text(t("native.unpack.retry"), color = c.textAccentCyan) }
        }
    }
}
