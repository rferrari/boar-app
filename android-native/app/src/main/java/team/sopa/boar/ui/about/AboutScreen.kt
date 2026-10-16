package team.sopa.boar.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import team.sopa.boar.R
import team.sopa.boar.engine.BoarEngine
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * About BOAR as a thread from @boar_app (brand book, manifesto register: one idea per post, ends on what BOAR does),
 * with the brand mark (direction A, "Tusk moon") as the avatar. Addresses copy on press and hold; nothing opens from
 * here. The version, build and engine line closes the thread.
 */
@Composable
fun AboutScreen(onClose: () -> Unit) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val info = versionName(ctx)
    val debug = (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    Column(Modifier.fillMaxSize().background(c.bgSurface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(c.bgCardElevated).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("aboutScreen.title"), style = Boar.type.titleSm, color = c.textHeading, modifier = Modifier.weight(1f))
            Text(t("common.done"), style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp).padding(bottom = 28.dp)) {
            val posts = POSTS
            posts.forEachIndexed { i, key ->
                Post(last = i == posts.lastIndex) {
                    Text(t("native.about.post.$key"), style = Boar.type.body, color = c.textPrimary)
                    when (key) {
                        "open" -> Link("github.com/rferrari/boar-app")
                        "verify" -> {
                            Link("boarapp.com"); Link("x.com/boar_app"); Link("farcaster.xyz/boarapp")
                            Text(t("native.about.verify"), style = Boar.type.title, color = c.textAccentEmerald, fontWeight = FontWeight.ExtraBold)
                        }
                        "packs" -> Link("huggingface.co/datasets/r4topunk/boar-packs")
                        "team" -> for ((name, handle) in TEAM) Link("$name · @$handle", copy = "x.com/$handle")
                        "credits" -> Credits()
                    }
                }
            }
            Text(t("native.about.holdToCopy"), style = Boar.type["mono.xs"], color = c.textDim, modifier = Modifier.padding(start = 56.dp, top = 4.dp))
            Text(
                t("aboutScreen.versionBadge", "version" to info, "build" to t(if (debug) "aboutScreen.buildDevelopment" else "aboutScreen.buildRelease")) + " • llama.cpp · ${BoarEngine.loadedVariant ?: "—"}",
                style = Boar.type["mono.xs"], color = c.textSecondary,
                modifier = Modifier.padding(start = 56.dp, top = 12.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** The thread, in order: the why, offline, the receipt, memory and storage, open source, verify, packs, team, credits. */
private val POSTS = listOf("why", "offline", "receipt", "budget", "open", "verify", "packs", "team", "credits")

/** One post: the round avatar with the thread line under it (none after the last), the name, then the content. */
@Composable
private fun Post(last: Boolean, content: @Composable () -> Unit) {
    val c = Boar.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.boar_mark), null, Modifier.size(44.dp).clip(CircleShape).border(1.dp, c.borderDefault, CircleShape),
                contentScale = ContentScale.Crop)
            if (!last) Box(Modifier.width(2.dp).weight(1f).padding(vertical = 4.dp).background(c.borderDefault))
        }
        Column(Modifier.weight(1f).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Boar", style = Boar.type.titleSm, color = c.textHeading, fontWeight = FontWeight.Bold)
                Text("@boar_app", style = Boar.type.caption, color = c.textDim)
            }
            content()
        }
    }
}

/** The open work BOAR stands on: libraries, then each model and pack with its license; the full list one tap away. */
@Composable
private fun Credits() {
    val c = Boar.colors
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val models = remember {
        team.sopa.boar.app.BoarServices.get(ctx).catalog.models
            .filter { !it.license.startsWith("Unknown") && it.license.isNotBlank() }
            .map { it.label to it.license }
            .distinct()
    }
    val all = AboutCredits.libraries + AboutCredits.extra() + models
    val shown = if (open) all else all.take(4)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { (name, license) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = Boar.type.caption, color = c.textSecondary, modifier = Modifier.weight(1f))
                Text(license, style = Boar.type["mono.xs"], color = c.textDim)
            }
        }
        Text(if (open) t("native.about.creditsLess") else t("native.about.creditsAll", "n" to all.size), style = Boar.type.caption, color = c.textAccentCyan,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { open = !open }.padding(vertical = 4.dp))
    }
}

@Composable
/** "2.0.0-dev · 14193eea · 2026-10-15": the APK's version, the commit it was built from (+ if it had local changes) and the day. */
private fun versionName(ctx: android.content.Context): String = listOfNotNull(
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "—",
    team.sopa.boar.BuildConfig.GIT_SHA.ifEmpty { null },
    team.sopa.boar.BuildConfig.BUILD_DATE,
).joinToString(" · ")

/** BOAR's core team on X, alphabetical by name. */
private val TEAM = listOf("Bielcx" to "bielthegoat", "Jasper" to "btcjasper", "r4to" to "r4topunk", "Sopa" to "sopaagency",
    "vaipraonde" to "arferrari", "vlad" to "sk8ordao")

/** An address to type or paste, never opened from here; press and hold copies [copy] (the text itself by default). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Link(text: String, copy: String = text) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val copied = t("native.about.copied")
    Text(text, style = Boar.type["mono.sm"], color = c.textAccentCyan,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.bgTerminal, RoundedCornerShape(6.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(6.dp))
            .combinedClickable(onClick = {}, onLongClick = {
                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("BOAR", copy))
                android.widget.Toast.makeText(ctx, copied, android.widget.Toast.LENGTH_SHORT).show()
            })
            .padding(10.dp))
}
