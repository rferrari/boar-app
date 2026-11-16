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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
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
import team.sopa.boar.ui.theme.wash

/**
 * About BOAR as a page of sections and rows (adam approved the mock-up, Nov 12; t9 1840): the mark, the tagline and the
 * version block at the top (press and hold copies it for a bug report), then what BOAR is, privacy, measuring, the
 * addresses, the plugins on, the knowledge packs, the core team and the credits. Addresses copy on tap or press and hold;
 * nothing opens a browser from here.
 */
@Composable
fun AboutScreen(onClose: () -> Unit) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val info = versionName(ctx)
    val debug = (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    Column(Modifier.fillMaxSize().background(c.bgSurface).statusBarsPadding().navigationBarsPadding()) {
        team.sopa.boar.ui.PageHeader(t("aboutScreen.title"), onClose)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 28.dp)) {
            Top(info, debug)
            AboutSection(R.drawable.ic_info, t("native.about.section.what")) {
                Text(t("native.about.bio"), style = Boar.type.body, color = c.textPrimary, modifier = Modifier.padding(vertical = 6.dp))
            }
            AboutSection(R.drawable.ic_settings_pin, t("native.about.section.privacy")) {
                InfoRow(t("native.about.privacy.model"), t("native.about.privacy.modelNote"), first = true)
                InfoRow(t("native.about.privacy.offline"), t("native.about.privacy.offlineNote"))
                InfoRow(t("native.about.privacy.connection"), t("native.about.privacy.connectionNote"))
            }
            AboutSection(R.drawable.ic_measure, t("native.about.section.measure")) {
                InfoRow(t("native.about.measure.receipt"), t("native.about.measure.receiptNote"), first = true)
                InfoRow(t("native.about.measure.eval"), t("native.about.measure.evalNote"))
                InfoRow(t("native.about.measure.budget"), t("native.about.measure.budgetNote"))
            }
            AboutSection(R.drawable.ic_link, t("native.about.section.links")) {
                CopyRow(t("native.about.siteTitle"), "boarapp.com", first = true)
                CopyRow(t("native.about.sourceTitle"), "github.com/rferrari/boar-app", note = t("native.about.sourceNote"))
                CopyRow("X", "x.com/boar_app")
                CopyRow("Farcaster", "farcaster.xyz/boarapp")
                Text(t("native.about.verify"), style = Boar.type.titleSm, color = c.textAccentEmerald, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text(t("native.about.post.verify"), style = Boar.type.subtext, color = c.textMuted, modifier = Modifier.padding(top = 2.dp))
            }
            AboutSection(R.drawable.ic_settings_experimental, t("native.about.section.plugins")) { PluginsRow() }
            AboutSection(R.drawable.ic_settings_knowledge, t("native.about.packsTitle")) {
                CopyRow(t("native.about.packsBody"), "huggingface.co/datasets/r4topunk/boar-packs", first = true)
            }
            AboutSection(R.drawable.ic_people, t("native.about.teamTitle")) {
                TEAM.forEachIndexed { i, (name, handle) -> TeamRow(name, handle, first = i == 0) }
            }
            AboutSection(R.drawable.ic_credits, t("native.about.section.credits")) {
                Text(t("native.about.post.credits"), style = Boar.type.subtext, color = c.textMuted, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                Credits()
            }
            // Last, as the footer: how the addresses copy (adam, Nov 10).
            Text(t("native.about.holdToCopy"), style = Boar.type.subtext, color = c.textDim, modifier = Modifier.padding(top = 30.dp))
        }
    }
}

/**
 * The mark, the tagline, then the version block between two hairlines: version, build and engine. Press and hold
 * copies it with the phone's model, Android version and the plugins on, for a bug report (adam, Nov 10). Only the
 * clipboard: nothing is sent.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Top(info: String, debug: Boolean) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val copied = t("native.about.copied")
    val labs = team.sopa.boar.labs.LabHooks.enabledLabs()
    val build = t(if (debug) "aboutScreen.buildDevelopment" else "aboutScreen.buildRelease")
    val engine = "llama.cpp · ${BoarEngine.loadedVariant ?: "—"}"
    val version = t("aboutScreen.versionBadge", "version" to info, "build" to build) + " • " + engine
    val report = AboutDebug.report(version, "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", android.os.Build.VERSION.RELEASE,
        android.os.Build.VERSION.SDK_INT, labs.map { it.second })
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.boar_mark), null, Modifier.size(72.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        Text(t("native.about.tagline"), style = Boar.type.title, color = c.textHeading, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))
        Hairline()
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .combinedClickable(onClickLabel = t("native.about.versionHold"), onClick = {}, onLongClick = { copy(ctx, report, copied) })
            .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("v$info", style = Boar.type["mono.sm"], color = c.textPrimary)
                Text("$build · $engine", style = Boar.type.subtext, color = c.textSecondary)
                Text(t("native.about.versionHold"), style = Boar.type.subtext, color = c.textDim)
            }
            team.sopa.boar.ui.PackIcon(R.drawable.ic_copy, 18.dp, c.textDim)
        }
        Hairline()
    }
}

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(Boar.colors.borderDefault))

/** A section: an 18 dp line icon and a small accent heading, 30 dp above it, then its rows. */
@Composable
private fun AboutSection(icon: Int, title: String, rows: @Composable () -> Unit) {
    val c = Boar.colors
    Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            team.sopa.boar.ui.PackIcon(icon, 18.dp, c.textAccentEmerald)
            Text(title, style = Boar.type.titleSm.copy(fontSize = 14.sp), color = c.textAccentEmerald, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() })
        }
        rows()
    }
}

/** A plain row: a title and one line under it, a hairline above all but the first; no box. */
@Composable
private fun InfoRow(title: String, note: String?, first: Boolean = false) {
    val c = Boar.colors
    if (!first) Hairline()
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = Boar.type.body.copy(fontSize = 15.sp), color = c.textPrimary, fontWeight = FontWeight.Medium)
        note?.let { Text(it, style = Boar.type.subtext.copy(fontSize = 13.5.sp), color = c.textSecondary) }
    }
}

/**
 * An address: its label, the address under it and a copy icon. Tap and press and hold both copy it; it never opens a
 * browser (adam, Nov 10). [copyText]: what goes to the clipboard, the address itself by default.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CopyRow(label: String, address: String, note: String? = null, first: Boolean = false, copyText: String = address) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val copied = t("native.about.copied")
    val desc = t("native.about.copyAddress", "address" to copyText)
    if (!first) Hairline()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .combinedClickable(onClickLabel = desc, onClick = { copy(ctx, copyText, copied) }, onLongClick = { copy(ctx, copyText, copied) })
        .semantics(mergeDescendants = true) { contentDescription = desc }
        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = Boar.type.body.copy(fontSize = 15.sp), color = c.textPrimary, fontWeight = FontWeight.Medium)
            Text(address, style = Boar.type["mono.sm"], color = c.textSecondary)
            note?.let { Text(it, style = Boar.type.subtext.copy(fontSize = 13.5.sp), color = c.textMuted) }
        }
        team.sopa.boar.ui.PackIcon(R.drawable.ic_copy, 18.dp, c.textDim)
    }
}

/** A core team member: the name left, the @handle right, a copy icon; tap or hold copies x.com/<handle>. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TeamRow(name: String, handle: String, first: Boolean) {
    val c = Boar.colors
    val ctx = LocalContext.current
    val copied = t("native.about.copied")
    val address = "x.com/$handle"
    val desc = t("native.about.copyAddress", "address" to address)
    if (!first) Hairline()
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp)
        .combinedClickable(onClickLabel = desc, onClick = { copy(ctx, address, copied) }, onLongClick = { copy(ctx, address, copied) })
        .semantics(mergeDescendants = true) { contentDescription = desc }
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name, style = Boar.type.body.copy(fontSize = 15.sp), color = c.textPrimary, modifier = Modifier.weight(1f))
        Text("@$handle", style = Boar.type["mono.sm"], color = c.textSecondary)
        team.sopa.boar.ui.PackIcon(R.drawable.ic_copy, 16.dp, c.textDim)
    }
}

/** The plugins switched on: the count as the title, the list under it; "None on" without any. */
@Composable
private fun PluginsRow() {
    val labs = team.sopa.boar.labs.LabHooks.enabledLabs()
    if (labs.isEmpty()) InfoRow(t("native.about.noPlugins"), null, first = true)
    else InfoRow(t("native.about.plugins", "count" to labs.size), labs.joinToString(", ") { it.first }, first = true)
}

/** Copies [text] and says so in the app's own toast. Only the clipboard: nothing is sent or opened. */
private fun copy(ctx: android.content.Context, text: String, copied: String) {
    (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
        .setPrimaryClip(android.content.ClipData.newPlainText("BOAR", text))
    team.sopa.boar.ui.Toasts.show(copied)
}

/** The open work BOAR stands on: libraries, Wikipedia and Hugging Face, then each model with its license, all shown. */
@Composable
private fun Credits() {
    val c = Boar.colors
    val ctx = LocalContext.current
    val models = remember {
        team.sopa.boar.app.BoarServices.get(ctx).catalog.models
            // Hidden models (no longer offered, such as Phi-3.5) aren't credited.
            .filter { !it.hidden && !it.license.startsWith("Unknown") && it.license.isNotBlank() }
            .map { it.label to it.license }
            .distinct()
    }
    val all = AboutCredits.libraries + AboutCredits.sources.map { (key, license) -> t(key) to license } + AboutCredits.extra() + models
    // Name and license pairs at 13.5 sp, no dividers (lifted from 10 sp mono; t9 1840).
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        all.forEach { (name, license) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, style = Boar.type.subtext.copy(fontSize = 13.5.sp), color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(license, style = Boar.type.subtext.copy(fontSize = 13.5.sp), color = c.textSecondary)
            }
        }
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
