package team.sopa.boar.ui.appearance

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import team.sopa.boar.R
import team.sopa.boar.appearance.TruffleArt
import team.sopa.boar.appearance.TruffleVault
import team.sopa.boar.engine.vault.Vault
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import team.sopa.boar.ui.theme.Themes
import java.text.DateFormat
import java.util.Date

/** A locked truffle's look: its shape in near-black, so the art stays a surprise. */
private val SILHOUETTE = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    0f, 0f, 0f, 0f, 18f,
    0f, 0f, 0f, 0f, 18f,
    0f, 0f, 0f, 0f, 24f,
    0f, 0f, 0f, 1f, 0f,
)))

/** Settings → Appearance: the theme, the boar (app icon and chat avatar) and the way into the vault. */
@Composable
fun ThemePicker(vault: TruffleVault, themeId: String?, onTheme: (String) -> Unit) {
    val c = Boar.colors
    val state by vault.state.collectAsState()
    val avatarsOn by vault.enabled.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("native.vault.themes"), style = Boar.type.caption, color = c.textSecondary, fontWeight = FontWeight.SemiBold)
        // Unlocked themes show (and apply) only while Boar Avatars is on.
        val openThemes = Themes.all.filter { it.id in Themes.open || (avatarsOn && (it.id in Themes.starter || it.id in state.themes)) }
        val selected = Themes.byId(themeId?.takeIf { avatarsOn || it in Themes.open }).id
        // Wraps like Settings' other choices: scrolling sideways cut the third theme at the edge (Large text, Oct 15).
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (th in openThemes) Chip("${th.icon} ${th.name}", th.id == selected) { onTheme(th.id) }
        }
        val locked = Themes.all.size - openThemes.size
        if (avatarsOn && locked > 0) Text(t("native.vault.themesLocked", "n" to locked), style = Boar.type.subtext, color = c.textMuted)
    }
}

/** Settings → Boar Avatars (while the experiment is on): the equipped boar and the way into the Truffle Vault. */
@Composable
fun AvatarPicker(vault: TruffleVault, onOpenVault: () -> Unit) {
    val c = Boar.colors
    val state by vault.state.collectAsState()
    val icon by vault.icon.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("native.vault.boar"), style = Boar.type.caption, color = c.textSecondary, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(R.drawable.boar, "BOAR", icon == null) { vault.equip(null) }
            for (id in TruffleArt.byId.keys.filter { it in state.unlocked }) Avatar(TruffleArt.byId.getValue(id).avatar, t("native.truffle.$id"), icon == id) { vault.equip(id) }
        }
        Text(t(if (TruffleVault.LAUNCHER_ICONS) "native.vault.iconNoteLauncher" else "native.vault.iconNote"), style = Boar.type.subtext, color = c.textMuted)
        Row(Modifier.fillMaxWidth().background(c.bgCardElevated, RoundedCornerShape(10.dp)).clickable(onClick = onOpenVault).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(t("native.vault.card", "n" to state.unlocked.size, "total" to Vault.RULES.size), style = Boar.type.body, color = c.textPrimary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(end = 8.dp))
            // One line: the arrow was left alone on a second line at Large text.
            Text(t("native.vault.open"), style = Boar.type.body, color = c.textAccentEmerald, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Boar.colors
    Text(text, style = Boar.type.caption, color = if (on) c.textInverse else c.textPrimary,
        modifier = Modifier.background(if (on) c.emerald600 else c.bgCard, RoundedCornerShape(16.dp))
            .border(1.dp, if (on) c.emerald600 else c.borderDefault, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
private fun Avatar(res: Int, name: String, on: Boolean, onClick: () -> Unit) {
    val c = Boar.colors
    Image(painterResource(res), name, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
        .border(if (on) 3.dp else 1.dp, if (on) c.emerald500 else c.borderDefault, RoundedCornerShape(12.dp)).clickable(onClick = onClick))
}

/** Settings → Appearance → Truffle Vault: every truffle, unearthed in color, still buried as a silhouette with its clue. */
@Composable
fun VaultScreen(vault: TruffleVault, onBack: () -> Unit) {
    val c = Boar.colors
    val state by vault.state.collectAsState()
    val icon by vault.icon.collectAsState()
    val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)
    Column(Modifier.fillMaxSize().background(c.bgTerminal).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(c.bgSurface).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("‹ " + t("common.back"), style = Boar.type.body, color = c.textAccentEmerald, modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            Text(t("native.vault.title"), style = Boar.type.titleLg, color = c.textHeading)
        }
        LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("native.vault.card", "n" to state.unlocked.size, "total" to Vault.RULES.size), style = Boar.type.titleLg, color = c.textHeading)
                    Text(t("native.vault.intro"), style = Boar.type.subtext, color = c.textMuted)
                }
            }
            items(Vault.RULES.entries.toList(), key = { it.key }) { (id, rule) ->
                val at = state.unlocked[id]
                val art = TruffleArt.byId.getValue(id)
                Column(Modifier.background(c.bgCard, RoundedCornerShape(12.dp))
                    .border(if (icon == id) 2.dp else 1.dp, if (icon == id) c.emerald500 else c.borderDefault, RoundedCornerShape(12.dp))
                    .then(if (at != null) Modifier.clickable { vault.equip(id) } else Modifier).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        Image(painterResource(art.avatar), null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
                            colorFilter = if (at == null) SILHOUETTE else null)
                        if (at == null) Text("?", style = Boar.type.titleLg, color = c.textDim, modifier = Modifier.align(Alignment.Center))
                    }
                    if (at != null) {
                        Text(t("native.truffle.$id"), style = Boar.type.body, color = c.textHeading, fontWeight = FontWeight.SemiBold)
                        Text(if (icon == id) t("native.vault.equipped") else t("native.vault.unlockedOn", "date" to dates.format(Date(at))),
                            style = Boar.type.micro, color = if (icon == id) c.textAccentEmerald else c.textDim)
                    } else {
                        Text(t("native.rule.${rule.name}"), style = Boar.type.subtext, color = c.textMuted, textAlign = TextAlign.Start)
                    }
                }
            }
        }
    }
}

/** "Truffle unearthed" over any screen, for a few seconds, with a way to equip it at once. */
/** A newly unearthed boar, offered as the app's toast (top, below the header) with Equip. */
@Composable
fun FreshTruffle(vault: TruffleVault) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(vault) {
        vault.fresh.collect { id ->
            val art = TruffleArt.byId[id] ?: return@collect
            team.sopa.boar.ui.Toasts.show(team.sopa.boar.ui.Toasts.Toast(
                team.sopa.boar.ui.I18n.t(ctx, "native.vault.fresh", "name" to team.sopa.boar.ui.I18n.t(ctx, "native.truffle.$id")),
                image = art.avatar, action = team.sopa.boar.ui.I18n.t(ctx, "native.vault.equip"), onAction = { vault.equip(id) }))
        }
    }
}
