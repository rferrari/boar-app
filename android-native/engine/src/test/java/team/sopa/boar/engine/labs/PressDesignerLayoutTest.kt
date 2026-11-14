package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Press, simplified (adam, Nov 12): the page, and one input bar. The project is the header's subtitle; one grouped menu
 * (add, save as, projects, page); a short action bar; the bar asks BOAR to change the selected text, or writes a note.
 */
class PressDesignerLayoutTest {
    private fun read(p: String) = java.io.File("../app/src/labs/$p").readText()
    private val s = read("plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt")
    private val screen = s.substringAfter("var note by remember").substringBefore("if (focusTitle)")

    @Test fun `under the page there is only the chat's input bar, no caption line, strip or second project button`() {
        assertTrue(screen.indexOf("PaperView(") in 0 until screen.indexOf("team.sopa.boar.ui.chat.PromptBar(note"))
        for (gone in listOf("private fun BottomBar(", "private fun ProjectBar(", "private fun CaptionLine(", "private fun AddStrip(")) assertTrue(gone, !s.contains(gone))
        assertTrue(Regex("Sheet\\.PROJECTS \\}").findAll(screen).count() == 1)
    }

    @Test fun `the project's name is the page header's subtitle`() {
        assertTrue(read("java/team/sopa/boar/labs/LabPlugin.kt").contains("@Composable fun pageSubtitle(): String? = null"))
        assertTrue(read("java/team/sopa/boar/labs/LabsScreen.kt").contains("full = true, subtitle = lab.pageSubtitle(), onExit = onExit,"))
        val lab = read("plugins/printer/java/team/sopa/boar/labs/printer/PrinterLab.kt")
        assertTrue(lab.substringAfter("override fun pageSubtitle()").substringBefore("\n    }").contains("current?.name"))
    }

    @Test fun `with a text selected the bar asks BOAR to change it, with nothing selected it writes a note`() {
        val bar = screen.substringAfter("team.sopa.boar.ui.chat.PromptBar(note")
        assertTrue(bar.contains("if (target != null) { askOwn = typed; editing = null; sheet = Sheet.EDIT }"))
        assertTrue(bar.contains("else addBlock(Block.Note(PressStore.newId(\"n\"), typed))"))
        assertTrue(bar.contains("\"native.labs.printer.askPlaceholder\" else \"native.labs.printer.notePlaceholder\""))
        // The sheet starts the rewrite with what was typed; nothing changes before Use (PressRewriteTest).
        assertTrue(s.contains("EditSheet(target, typed = askOwn,") && s.contains("LaunchedEffect(typed) { if (!typed.isNullOrBlank()) { own = typed; ask(PressRewrite.Option.OWN, typed) } }"))
    }

    @Test fun `one menu, grouped, with Save as and its two formats on the right`() {
        val menu = s.substringAfter("private fun ProjectsSheet(").substringBefore("\n}\n")
        // The page breaks switch is on the add bar now, not in the menu (adam, Nov 13).
        assertTrue(!menu.contains("pageGroup") && !menu.contains("pageBreaks"))
        // Nov 14: About this plugin is in Press settings, not in the menu; the menu ends with Rename and Delete.
        assertTrue(!menu.contains("AboutPluginFold"))
        val at = listOf("native.labs.printer.saveAs", "Group(\"native.labs.printer.projects\")", "native.labs.printer.rename", "native.labs.printer.delete\"").map { menu.indexOf(it) }
        assertTrue(at.toString(), at.all { it >= 0 } && at == at.sorted())
        assertTrue(menu.contains("Triple(\".pdf\", onPdf, canSave), Triple(\".md\", onMarkdown, true)"))
        // Adding is a toolbar now, not a menu row (adam, Nov 12).
        assertTrue(!menu.contains("native.labs.printer.addTitle"))
        assertTrue(screen.contains("onLeft = { sheet = Sheet.PROJECTS }") && screen.contains("team.sopa.boar.labs.PluginMenuIcon("))
    }

    @Test fun `the action bar is one row that doesn't scroll, with one placement button`() {
        val bar = s.substringAfter("private fun ActionBar(").substringBefore("\n}\n")
        assertTrue(!bar.contains("horizontalScroll") && bar.contains("Arrangement.SpaceBetween"))
        assertTrue(bar.contains("PressDoc.Place.LEFT -> PressDoc.Place.CENTER; PressDoc.Place.CENTER -> PressDoc.Place.RIGHT; else -> PressDoc.Place.LEFT"))
        assertTrue(Regex("IconAction\\(").findAll(bar).count() <= 7)
    }

    @Test fun `with nothing selected the row is the add toolbar, icons with spoken names, and a selected block's toolbar takes its place`() {
        assertTrue(screen.contains("if (i >= 0) ActionBar(") && screen.contains("else AddBar("))
        val bar = s.substringAfter("private fun AddBar(").substringBefore("\n}\n")
        for (x in listOf("lab_printer_ic_photo", "lab_printer_ic_heading", "lab_printer_ic_drawing", "lab_printer_ic_chat")) assertTrue(x, bar.contains(x))
        // Nov 13: paragraph, list and divider too; Nov 14: settings went to the header's gear. Seven tools, the shared
        // left-aligned toolbar (no scroll, 44 dp at most), no "Add" word.
        assertTrue(Regex("IconAction\\(").findAll(bar).count() == 7 && !bar.contains("native.labs.printer.add\""))
        assertTrue(!bar.contains("horizontalScroll") && bar.contains("team.sopa.boar.labs.PluginToolbar(ADD_BUTTONS)") && s.contains("private const val ADD_BUTTONS = 7"))
        assertTrue(!bar.contains("settings") && !bar.contains("showBreaks"))
        assertTrue(s.contains("Sheet.ADD -> ChatsSheet { id, title -> sheet = null; PressStore.addChat(ctx, id, title) }"))
        assertTrue(read("plugins/printer/java/team/sopa/boar/labs/printer/PrinterLab.kt").contains("PressStore.addChat(ctx, sessionId, title)"))
        for (d in listOf("values", "values-pt")) {
            val x = read("plugins/printer/res/$d/strings_lab_printer.xml")
            for (k in listOf("notePlaceholder", "askPlaceholder", "addNote", "menu", "saveAs", "placeNext", "fromChat")) assertTrue("$d $k", x.contains("name=\"native_labs_printer_$k\""))
        }
    }
}

class PressManyProjectsTest {
    private fun lib(n: Int): PressDoc.Library {
        var l = PressDoc.Library("p0", listOf(PressDoc.Project("p0", "Field notes", 1)))
        for (i in 1 until n) l = l.newProject("p$i", if (i == 7) "Viagem a São Paulo" else "Project $i", 10L * i)
        return l.switchTo("p3")
    }

    @Test fun `the menu lists the open project and the three most recent, however many there are`() {
        val l = lib(12)
        assertEquals(listOf("p3", "p11", "p10", "p9"), l.shortList().map { it.id })
        assertEquals(12, l.search("").size)
        assertEquals("p3", l.search("").first().id)
        assertEquals(2, lib(2).shortList().size)
    }

    @Test fun `search finds by name, case and accents aside`() {
        val l = lib(12)
        assertEquals(listOf("p7"), l.search("sao paulo").map { it.id })
        assertEquals(listOf("p11", "p10", "p1"), l.search("project 1").map { it.id })
        assertTrue(l.search("zzz").isEmpty())
    }

    @Test fun `the screen uses the short list, All projects and the search`() {
        val s = java.io.File("../app/src/labs/plugins/printer/java/team/sopa/boar/labs/printer/PressScreen.kt").readText()
        assertTrue(s.contains("library.shortList().forEach") && s.contains("if (library.projects.size > PressDoc.SHORT_LIST)"))
        assertTrue(s.contains("Sheet.ALL_PROJECTS -> AllProjectsSheet(library)") && s.contains("val found = library.search(query)"))
    }
}
