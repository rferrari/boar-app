package team.sopa.boar.labs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import team.sopa.boar.ui.Toasts
import team.sopa.boar.ui.theme.Boar
import team.sopa.boar.ui.theme.wash

/**
 * One layout for every plugin page with a full layout (adam, Nov 14; Press and Designer, and every later page from it):
 * header (‹ Back, name, subtitle, the settings gear, ✕), the work area, an optional status line and shelf, one toolbar of
 * round tools, left-aligned, and the chat's prompt bar whose left round button is the plugin's ⋯ menu. The pieces here
 * are shared; the colours come from the theme only.
 */
object PluginSettings {
    /** The header gear asks the open plugin's page to show its settings sheet: the plugin's id, until the page takes it. */
    var open by mutableStateOf<String?>(null)
}

/** The header's settings gear: the ✕'s 42 dp round, spoken "<Plugin> settings". */
@Composable
fun PluginSettingsGear(name: String, onClick: () -> Unit) {
    val label = team.sopa.boar.ui.t("native.labs.pluginSettings", "name" to name)
    Box(Modifier.size(team.sopa.boar.ui.HeaderMetrics.ROW).clip(CircleShape).background(Boar.colors.wash(0.05f))
        .combinedClickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_settings_tweaks, 20.dp, description = label)
    }
}

/**
 * A round tool, 44 dp at most: its name spoken and shown on a long press; the accent ring and icon when [on]; crimson for
 * [danger]; a small accent [dot] for "open" (a caption being typed), so the ring stays the current choice's alone.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PluginTool(icon: Int, label: String, enabled: Boolean = true, on: Boolean = false, danger: Boolean = false, dot: Boolean = false,
               size: Dp = TOOL_MAX, touch: Dp = toolTouch, onClick: () -> Unit) {
    val c = Boar.colors
    val tint = when { danger -> c.crimson400; on -> c.emerald500; else -> c.textHeading }
    // The circle is [size]; what a finger hits is at least 48 dp each way (validator A-4: 36 dp on a 320 dp phone).
    Box(Modifier.width(maxOf(touch, size)).height(maxOf(TOUCH_MIN, size))
        .combinedClickable(enabled = enabled, role = Role.Button, onClickLabel = label, onLongClick = { Toasts.show(label) }, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).alpha(if (enabled) 1f else 0.35f).clip(CircleShape).background(c.bgCard)
            .border(1.dp, if (on) c.emerald500 else c.borderSubtle, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), label, Modifier.size(20.dp), tint = tint)
            if (dot) Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(7.dp).background(c.emerald500, CircleShape))
        }
    }
}

/**
 * The toolbar (adam, Nov 14: "make toolbar buttons left aligned"): one row of [count] round tools from the left. Each
 * tool's touch area is its cell (the width shared out, 52 dp at most) and its circle sits inside, 44 dp at most, so a
 * finger always has 48 dp (validator A-4). It doesn't scroll unless the cells would be under 48 dp (seven tools on a
 * 320 dp phone): then 48 dp cells, scrolling. [content] gets the circle's size and passes [toolTouch] to each tool.
 */
@Composable
fun PluginToolbar(count: Int, content: @Composable RowScope.(Dp) -> Unit) {
    val c = Boar.colors
    Column(Modifier.fillMaxWidth().background(c.bgSurface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            val cell = (maxWidth / count).coerceAtMost(TOOL_CELL_MAX)
            val scrolls = cell < TOUCH_MIN
            val touch = if (scrolls) TOUCH_MIN else cell
            val size = (touch - TOOL_GAP).coerceAtMost(TOOL_MAX)
            val row = if (scrolls) Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier.fillMaxWidth()
            androidx.compose.runtime.CompositionLocalProvider(LocalToolTouch provides touch) {
                Row(row, horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) { content(size) }
            }
        }
    }
}

/** The touch width a [PluginToolbar] gives each of its tools (its cell); 48 dp elsewhere. */
val LocalToolTouch = androidx.compose.runtime.staticCompositionLocalOf { TOUCH_MIN }
val toolTouch: Dp @Composable get() = LocalToolTouch.current

/** The prompt bar's left round button on a plugin page: the plugin's menu, ⋯ (adam, Nov 14: "⋯ in every plugin"). */
@Composable
fun PluginMenuIcon(label: String, tint: Color = Boar.colors.textHeading) =
    team.sopa.boar.ui.PackIcon(team.sopa.boar.R.drawable.ic_plugin_menu, 22.dp, tint = tint, description = label)

/** A sheet's one title (no section label repeating it underneath; adam, Nov 14). */
@Composable
fun PluginSheetTitle(text: String) = Text(text, style = Boar.type.titleSm, color = Boar.colors.textHeading, modifier = Modifier.padding(bottom = 8.dp))

val TOOL_MAX = 44.dp
/** The smallest touch target (Android's accessibility guideline). */
val TOUCH_MIN = 48.dp
val TOOL_CELL_MAX = 52.dp
val TOOL_GAP = 8.dp
