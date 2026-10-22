package team.sopa.boar.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import team.sopa.boar.ui.theme.Boar
import team.sopa.boar.ui.t

/**
 * One card of the tour: its strings are native.tour.<key>.title / .body / .where (no where line for welcome and end).
 * [art]: a picture on top (the brand avatar, the mascot); [emoji]: a small one where there's no preview; else neither.
 */
data class TourCard(val key: String, val emoji: String? = null, val where: Boolean = true, val art: Int? = null)

/** The setup download under the tour: its line ("Downloading · 42% · 6 min"), its fraction, and whether BOAR is ready. */
data class TourProgress(val line: String, val fraction: Float, val ready: Boolean, val problem: Boolean = false)

/** The tour's cards in order; the Boar Labs card only in builds with the Experimental section. */
fun tourCards(experimental: Boolean): List<TourCard> = listOfNotNull(
    TourCard("welcome", where = false, art = team.sopa.boar.R.drawable.brand_pfp),
    TourCard("theme"),
    TourCard("styles"),
    TourCard("deep"),
    TourCard("sources"),
    TourCard("knowledge"),
    TourCard("models"),
    if (experimental) TourCard("adaptive") else null,
    TourCard("pin", "🔑"),
    if (experimental) TourCard("labs", "🧪") else null,
    TourCard("end", where = false, art = team.sopa.boar.R.drawable.mascot),
)

/**
 * BOAR's tour, full screen, one card at a time (Oct 20): opened from setup's download screen, where [progress] keeps a
 * live bar on top and a "BOAR is ready" banner when the index is done, or from Settings ([progress] null). It shows and
 * changes nothing, except the theme card, which applies a theme live through [onTheme]. [index] is held by the caller,
 * so the same card stays while setup moves from download to index to done under it. [onReady]: Open BOAR (setup only).
 */
@Composable
fun TourScreen(
    progress: TourProgress?, index: Int, onIndex: (Int) -> Unit, themeId: String?, onTheme: (String) -> Unit,
    onReady: (() -> Unit)?, onClose: () -> Unit,
) {
    val cards = tourCards(team.sopa.boar.BuildConfig.EXPERIMENTAL_FEATURES)
    val i = index.coerceIn(0, cards.lastIndex)
    val card = cards[i]
    var picked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(themeId) }
    var bannerHidden by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)) {
        // Back goes to the previous card, and closes the tour on the first.
        BackHandler { if (i > 0) onIndex(i - 1) else onClose() }
        val c = Boar.colors
        Column(Modifier.fillMaxSize().background(c.bgTerminal).statusBarsPadding().navigationBarsPadding()) {
            // The app's page header (as Boar Labs' pages): the title, and Done to leave the tour.
            Row(Modifier.fillMaxWidth().background(c.bgCardElevated).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("native.tour.title"), style = Boar.type.titleSm, color = c.textHeading, modifier = Modifier.weight(1f))
                Text(t("common.done"), style = Boar.type["mono.xs"], color = c.textAccentCyan, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
            // The download never hides: the same numbers as the setup screen, live.
            progress?.let { p ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(progress = { p.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = c.emerald500, trackColor = c.bgSubtle)
                    // A stalled or failed download: the line says so, and a tap goes back to its retry.
                    if (p.problem) Text(p.line, style = Boar.type.subtext, color = c.textAccentAmber, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClose).padding(vertical = 2.dp))
                    else Text(p.line, style = Boar.type.subtext, color = c.textSecondary)
                }
                if (p.ready && !bannerHidden && onReady != null) Column(
                    Modifier.padding(16.dp).fillMaxWidth().background(c.emeraldBgSubtle, RoundedCornerShape(14.dp)).border(1.dp, c.emerald500, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(team.sopa.boar.R.drawable.mascot), null, Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)))
                        Text(t("native.tour.ready"), style = Boar.type.title, color = c.textHeading)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(t("native.tour.openBoar"), primary = true, modifier = Modifier.weight(1f), onClick = onReady)
                        Button(t("native.tour.keepTouring"), primary = false, modifier = Modifier.weight(1f)) { bannerHidden = true }
                    }
                    Text(t("native.tour.again"), style = Boar.type.subtext, color = c.textSecondary)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                card.art?.let { r ->
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(r), null, Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                card.emoji?.let { Text(it, fontSize = 32.sp) }
                Text(t("native.tour.${card.key}.title"), style = Boar.type.headline, color = c.textHeading)
                Text(t("native.tour.${card.key}.body"), style = Boar.type.body, color = c.textPrimary)
                if (card.key != "theme") TourPreview(card.key)
                if (card.key == "theme") team.sopa.boar.ui.appearance.ThemePicker(team.sopa.boar.app.BoarServices.get(androidx.compose.ui.platform.LocalContext.current).vault, picked) {
                    picked = it; onTheme(it)
                }
                // Below the chips, so a tap repaints the example right under the finger.
                if (card.key == "theme") TourPreview("theme")
                if (card.where) Text(t("native.tour.${card.key}.where"), style = Boar.type.subtext, color = c.textAccentCyan,
                    modifier = Modifier.background(c.cyanBgSubtle, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    if (i > 0) Text("← " + t("native.tour.back"), style = Boar.type.body, color = c.textMuted,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onIndex(i - 1) }.padding(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    cards.indices.forEach { n -> Box(Modifier.size(6.dp).background(if (n == i) c.emerald500 else c.bgSubtle, CircleShape)) }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    val last = i == cards.lastIndex
                    // Last card: into BOAR when it's ready; otherwise Done, back to the download or to Settings.
                    val label = when {
                        !last -> t("native.tour.next") + " →"
                        progress == null -> t("native.tour.done")
                        progress.ready && onReady != null -> t("native.tour.openBoar")
                        else -> t("native.tour.done")
                    }
                    Button(label, primary = true) {
                        when {
                            !last -> onIndex(i + 1)
                            progress?.ready == true && onReady != null -> onReady()
                            else -> onClose()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Button(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Boar.colors
    Text(label, style = Boar.type.titleSm, color = if (primary) c.textInverse else c.textPrimary, textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).then(if (primary) Modifier.background(c.emerald500) else Modifier.border(1.dp, c.borderDefault, RoundedCornerShape(12.dp)))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp))
}
