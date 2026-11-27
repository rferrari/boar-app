package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One layout for every plugin page (adam, Nov 14; t9 0025): the settings gear in the header beside ✕, a left-aligned
 * toolbar of round tools, the prompt bar's ⋯ menu, About in settings, theme colours only. Read from the source.
 */
class PluginPagePatternTest {
    private fun read(p: String) = java.io.File("../app/src/$p").readText()
    private val press = read("labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt")
    private val designer = read("labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt")
    private val chrome = read("labs/java/team/sopa/boar/labs/PluginChrome.kt")

    @Test fun `the header gear is on Press and Designer and on no other plugin`() {
        val scaffold = read("labs/java/team/sopa/boar/labs/LabsScreen.kt")
        // The gear still only where hasSettings; with a gear, the on/off switch is in its sheet (t9 0450, PluginPageSwitchTest).
        assertTrue(scaffold.contains("actions = { if (lab.hasSettings) PluginSettingsGear(name) { PluginSettings.open = lab.id } else PluginPageSwitch(lab) }"))
        assertTrue(read("labs/java/team/sopa/boar/labs/LabPlugin.kt").contains("val hasSettings: Boolean get() = false"))
        val with = java.io.File("../app/src/labs/plugins").walk().filter { it.extension == "kt" && it.readText().contains("override val hasSettings = true") }.map { it.name }.toSet()
        assertEquals(setOf("PrinterLab.kt", "ImageLabScreen.kt"), with)
        // Each page opens its own sheet from the gear.
        assertTrue(press.contains("if (team.sopa.boar.labs.PluginSettings.open == lab.id) LaunchedEffect(Unit) { team.sopa.boar.labs.PluginSettings.open = null; sheet = Sheet.SETTINGS }"))
        assertTrue(designer.contains("PluginSettings.open == ImageGenLab.id") && !designer.contains("ic_settings_tweaks"))
        assertTrue(chrome.contains("Modifier.size(team.sopa.boar.ui.HeaderMetrics.ROW).clip(CircleShape).background(Boar.colors.wash(0.05f))"))
        for (d in listOf("values", "values-pt")) assertTrue(d, read("labs/res/$d/strings_labs.xml").contains("name=\"native_labs_pluginSettings\""))
    }

    @Test fun `no settings tool on Press's toolbar, and About is in settings, not the menu`() {
        val bar = press.substringAfter("private fun AddBar(").substringBefore("\n}\n")
        assertTrue(!bar.contains("settings") && !press.contains("lab_printer_ic_settings"))
        assertTrue(!press.substringAfter("private fun ProjectsSheet(").substringBefore("\n}\n").contains("AboutPluginFold"))
        assertTrue(press.substringAfter("private fun SettingsSheet(").substringBefore("\n}\n").contains("team.sopa.boar.labs.AboutPluginFold(lab)"))
        assertTrue(designer.substringAfter("private fun SettingsSheet(").substringBefore("\n}\n").contains("team.sopa.boar.labs.AboutPluginFold(ImageGenLab)"))
    }

    @Test fun `the prompt bar's left button is the ⋯ menu in both`() {
        assertTrue(press.contains("left = { team.sopa.boar.labs.PluginMenuIcon(t(\"native.labs.printer.menu\")) }"))
        assertTrue(designer.contains("left = { team.sopa.boar.labs.PluginMenuIcon(menuLabel,") && designer.contains("onLeft = { if (!busy) menu = true }"))
        assertTrue(read("labs/res/drawable/ic_plugin_menu.xml").contains("android:fillColor=\"#FFFFFFFF\""))
        // Designer's menu: the shown drawing, the three latest others, All drawings.
        val menu = designer.substringAfter("private fun DesignerMenu(").substringBefore("\n}\n")
        assertTrue(menu.contains("listOfNotNull(shown) + state.gallery.filter { it != shown }.take(MENU_RECENT)") && menu.contains("native.labs.image.allDrawings"))
    }

    @Test fun `the toolbars are left-aligned, one row, scrolling only where 48 dp targets don't fit`() {
        val bar = chrome.substringAfter("fun PluginToolbar(").substringBefore("\n}\n")
        // Golden change (validator A-4, t9 0426): adam's "never scrolling" (Nov 14) holds wherever 48 dp cells fit; on a
        // 320 dp phone with seven tools the row scrolls instead of shrinking the targets under 48 dp.
        assertTrue(bar.contains("horizontalArrangement = Arrangement.Start") && bar.contains(".coerceAtMost(TOOL_MAX)"))
        assertTrue(bar.contains("val scrolls = cell < TOUCH_MIN") && bar.contains("if (scrolls) Modifier.horizontalScroll("))
        assertTrue(press.contains("team.sopa.boar.labs.PluginToolbar(ADD_BUTTONS)") && designer.contains("team.sopa.boar.labs.PluginToolbar(DesignerCanvas.Section.entries.size)"))
        // "From your last chat" is a button now.
        assertTrue(designer.contains("FromChatButton(title) { subject = title }"))
    }

    @Test fun `theme colours only - no GOLD left in Press, and its sheets on the elevated surface`() {
        assertTrue(!Regex("\\bGOLD\\b|GOLD_INK|0xFFF0B44C").containsMatchIn(press))
        assertTrue(press.contains("containerColor = c.bgCardElevated") && !press.contains("containerColor = c.bgSurface"))
        assertTrue(chrome.contains("on -> c.emerald500") && !chrome.contains("Color(0x"))
    }

    @Test fun `no Default anywhere in Designer and every pick saves it`() {
        assertTrue(!designer.contains("★") && !designer.contains("native.labs.image.default"))
        val vm = read("labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLab.kt")
        assertEquals(1, Regex("\\); setDefaults\\(\\) }|\\}; setDefaults\\(\\) }").findAll(vm).count())
    }
}
