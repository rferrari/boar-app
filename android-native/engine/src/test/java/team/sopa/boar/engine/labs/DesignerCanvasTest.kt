package team.sopa.boar.engine.labs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import team.sopa.boar.engine.labs.DesignerCanvas.Size
import team.sopa.boar.engine.labs.DesignerCanvas.Stop

/** Designer's canvas (adam's mock-up, Nov 12-13; 512 only since BOAR-88, Nov 26): the default, the tools, the shared input bar, the variations. */
class DesignerCanvasTest {
    private val dir = "../app/src/labs/plugins/designer"
    private fun src(name: String) = java.io.File("$dir/java/team/sopa/boar/labs/designer/$name").readText()
    private fun strings(d: String) = java.io.File("$dir/res/$d/strings_lab_image.xml").readText()

    @Test fun `Designer draws only at the model's own size, 512 by 512 (BOAR-88)`() {
        // t9 on the F3, Nov 26: 640 drew two mouths and a three-eared mouse, 448x640 a distorted face; 512 was clean.
        assertEquals(Size(512, 512), DesignerCanvas.SIZE)
        assertEquals(listOf(Size(512, 512)), DesignerCanvas.OFFERED)
        assertEquals(DesignerCanvas.SIZE, DesignerCanvas.DEFAULT)
        assertEquals("512", DesignerCanvas.SIZE.key)
        // The lab and the runner draw only that: no shape or quality picks left to reach sd-cli.
        val lab = src("ImageLab.kt")
        assertTrue(lab.contains("val CANVASES = DesignerCanvas.OFFERED.map { Canvas(it.w, it.h) }"))
        assertTrue(!lab.contains("fun setShape(") && !lab.contains("fun setQuality("))
        assertTrue(!lab.contains("DesignerCanvas.Quality") && !lab.contains("DesignerCanvas.Shape"))
    }

    @Test fun `any saved size migrates to 512, silently - old drawings keep their own size and time`() {
        for (k in listOf("384", "512", "640", "320x448", "384x512", "448x640", "576x256", "640x320", "768x320", "1024x400"))
            assertEquals(k, Size(512, 512), DesignerCanvas.migrate(k))
        assertNull(DesignerCanvas.migrate(null)); assertNull(DesignerCanvas.migrate("")); assertNull(DesignerCanvas.migrate("big"))
        assertEquals(null to DesignerCanvas.DEFAULT, DesignerCanvas.loadDefault { null })
        for (k in listOf("640", "384x512", "nonsense")) assertEquals(Size(512, 512), DesignerCanvas.loadDefault { key -> if (key == DesignerCanvas.SIZE_PREF) k else null }.second)
        // A saved style carries over.
        assertEquals("photo", DesignerCanvas.loadDefault { key -> if (key == DesignerCanvas.STYLE_PREF) "photo" else "640" }.first)
        // An old drawing's own size is still read from its name (its picture opens at its shape, its time average stays per size).
        assertEquals(Size(448, 640), DesignerCanvas.parse("448x640")); assertEquals(DesignerCanvas.Shape.PORTRAIT, Size(448, 640).shape)
        val lab = src("ImageLab.kt")
        assertTrue(lab.contains("fun canvasOf(image: File): Canvas?") && lab.contains("\"image.times.\${size.key}\""))
    }

    @Test fun `the default is the style, saved with the one size`() {
        val prefs = HashMap<String, String>()
        DesignerCanvas.saveDefault(prefs::put, "watercolor")
        assertEquals("watercolor", prefs[DesignerCanvas.STYLE_PREF]); assertEquals("512", prefs[DesignerCanvas.SIZE_PREF])
        assertEquals("watercolor" to Size(512, 512), DesignerCanvas.loadDefault(prefs::get))
        assertEquals("image.style", DesignerCanvas.STYLE_PREF); assertEquals("image.size", DesignerCanvas.SIZE_PREF)
        val lab = src("ImageLab.kt")
        assertTrue(lab.contains("DesignerCanvas.saveDefault(team.sopa.boar.labs.LabPrefs::putString, style.key)"))
        assertTrue(lab.contains("fun setStyle(s: ImageLab.Style) { _state.update { it.copy(style = s) }; setDefaults() }"))
        val screen = src("ImageLabScreen.kt")
        assertTrue(!screen.contains("native.labs.image.default") && !screen.contains("★"))
        // Showing an old drawing puts its recipe on the canvas, not in the default.
        assertTrue(!lab.substringAfter("fun show(f: File) {").substringBefore("\n    }").contains("setDefaults"))
    }

    @Test fun `the caption keeps the style and this phone's seconds only (BOAR-88)`() {
        assertEquals("Engraving · 9 s", DesignerCanvas.caption("Engraving", 9))
        assertEquals("Lo-fi", DesignerCanvas.caption("Lo-fi", null))
        assertEquals(40, DesignerCanvas.variationsSeconds(10)); assertNull(DesignerCanvas.variationsSeconds(null))
        assertTrue(src("ImageLabScreen.kt").contains("DesignerCanvas.caption(styleName(state.recipe?.style ?: state.style.key), ImageLab.expectedSeconds(shown))"))
    }

    @Test fun `the toolbar has Style - Shape and Quality are gone (BOAR-88)`() {
        assertEquals("style", DesignerCanvas.Section.entries.first().key)
        assertTrue(DesignerCanvas.Section.entries.none { it.key == "shape" || it.key == "quality" })
        val screen = src("ImageLabScreen.kt")
        assertTrue(!screen.contains("DesignerDrawer") && screen.contains("team.sopa.boar.labs.PluginToolbar(DesignerCanvas.Section.entries.size)"))
        assertTrue(screen.contains("team.sopa.boar.R.drawable.ic_designer_style"))
        for (gone in listOf("ic_designer_shape", "ic_designer_quality", "ShapeSection(", "QualitySection(", "Tool.SHAPE", "Tool.QUALITY", "qualityLong(", "shapeQuality(", "qualityLabel("))
            assertTrue(gone, !screen.contains(gone))
        assertTrue(screen.contains("t(\"native.labs.image.styleLabel\") + \": \" + styleName(state.style.key)"))
        val sheet = screen.substringAfter("private fun ToolSheet(").substringBefore("\n}\n")
        assertTrue(sheet.contains("team.sopa.boar.labs.PluginSheetTitle(t(tool.titleKey))") && Regex("Text\\(").findAll(sheet).count() == 0)
        for (gone in listOf("fun Rail(", "Tray(", "DesignerCanvas.Tool")) assertTrue(gone, !screen.contains(gone))
        for (d in listOf("values", "values-pt")) {
            for (k in listOf("section_style", "section_shape", "section_quality", "drawer", "default", "qualityLabel", "quality_low", "qualityLong_high",
                    "qualitySeconds", "shapeQuality", "qualityHint", "qualityHintUntimed", "shapeLabel")) assertTrue("$d $k", !strings(d).contains("name=\"native_labs_image_$k\""))
            assertTrue(d, !strings(d).contains("native_labs_image_tool_"))
        }
        for (gone in listOf("tool.seed", "tool.edit", "tool.more", "seedKeep", "seedNew", "setSeedLocked(")) assertTrue(gone, !screen.contains(gone))
        // The i sheet keeps words, style and time; no shape or quality line.
        val info = screen.substringAfter("private fun InfoSheet(").substringBefore("\n}\n")
        assertTrue(!info.contains("shapeLabel") && !info.contains("qualityLabel") && info.contains("native.labs.image.time"))
    }

    @Test fun `Designer's bottom is the chat's own input bar - voice, Draw that becomes Stop, the same rest after a send`() {
        val screen = src("ImageLabScreen.kt")
        val canvas = screen.substringAfter("fun DesignerCanvasScreen(").substringBefore("\n}\n")
        assertTrue(canvas.contains("team.sopa.boar.ui.chat.PromptBar(subject, { subject = it }, answering = busy,"))
        assertTrue(canvas.contains("onSend = { vm.generate(subject); keyboard?.hide(); focus.clearFocus() }, onStop = { vm.cancel() }, voiceEnabled = voiceEnabled"))
        // The chat's own send icon, not a pencil (adam, 26 Nov); it still says Draw to TalkBack.
        assertTrue(canvas.contains("sendLabel = t(\"native.labs.image.generate\"), queues = false, enabled = ready") && !canvas.contains("sendIcon ="))
        assertTrue(canvas.contains("placeholder = t(\"native.labs.image.placeholder\")") && canvas.contains("settings.voiceInputEnabled"))
        // Its own copy is gone: one bar for both.
        assertTrue(!screen.contains("private fun PromptBar(") && !screen.contains("BasicTextField("))
        val bar = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/PromptBar.kt").readText()
        // Voice fills the prompt: the mic writes what was said into the same text (onText), as in the chat.
        assertTrue(bar.contains("if (voiceEnabled) MicButton(text, onText, enabled = !answering && enabled, size = 34.dp)"))
        assertTrue(bar.contains("voice.start(onPartial = { onText(join(it)) }, onResult = { onText(join(it)) }, onEnd = { listening = false })"))
        // Without the queue (Designer), the button is Stop all the while it draws; Draw with the words otherwise.
        assertTrue(bar.contains("val stops = answering && (!canSend || !queues)"))
        assertTrue(bar.contains("if (stops) onStop() else { onSend(); resting = true }") && bar.contains("private const val SEND_REST_MS = 500L"))
        // The chat calls the same bar, with its own placeholder and the answer style on the left.
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("PromptBar(text, onText, answering, onSend, onStop, voiceEnabled,") && !chat.contains("private fun MicButton("))
        // The chip stays above the bar (adam, Nov 12).
        assertTrue(canvas.indexOf("FromChatButton(title)") in 0 until canvas.indexOf("team.sopa.boar.ui.chat.PromptBar("))
    }

    @Test fun `no pixel numbers in Designer's strings, English and Portuguese`() {
        val px = Regex("\\d+\\s*[x×]\\s*\\d+|\\d+\\s*px\\b")
        for (d in listOf("values", "values-pt")) for (m in Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(strings(d)))
            assertTrue("$d ${m.groupValues[1]}: ${m.groupValues[2]}", !px.containsMatchIn(m.groupValues[2]))
        // Nor built in code: the pixel label is gone, the screen says sizes with the shape and quality words.
        val lab = src("ImageLab.kt"); val screen = src("ImageLabScreen.kt")
        assertTrue(!lab.contains("val label") && !screen.contains(".label") && !screen.contains("\" px"))
    }

    @Test fun `variations are drawn one by one and stop on the first failure, keeping what was drawn`() = runBlocking {
        val kept = ArrayList<Int>()
        var tries = 0
        val failed = DesignerCanvas.variations(enoughMemory = { true }, onDrawn = { i: Int -> kept += i }) { i -> tries++; if (i == 2) error("sd-cli exit 1") else i }
        assertEquals(Stop.FAILED, failed.stop); assertEquals(listOf(0, 1), failed.drawn); assertEquals(listOf(0, 1), kept); assertEquals(3, tries)

        var free = 2
        val low = DesignerCanvas.variations(enoughMemory = { free-- > 0 }) { i -> i }
        assertEquals(Stop.LOW_MEMORY, low.stop); assertEquals(listOf(0, 1), low.drawn)

        val all = DesignerCanvas.variations(enoughMemory = { true }) { i -> "v$i" }
        assertEquals(Stop.DONE, all.stop); assertEquals(4, all.drawn.size)

        // Stopped (Stop, Back): the cancellation goes through, and the ones drawn before it were already handed over.
        val handed = ArrayList<Int>()
        try {
            DesignerCanvas.variations(enoughMemory = { true }, onDrawn = { i: Int -> handed += i }) { i -> if (i == 1) throw CancellationException("stop") else i }
            fail("cancellation must not be swallowed")
        } catch (_: CancellationException) {}
        assertEquals(listOf(0), handed)
    }

    @Test fun `variations use Illustrate's memory gate, and every draw path checks ImageSafety`() {
        val lab = src("ImageLab.kt")
        val vari = lab.substringAfter("fun variations() {").substringBefore("\n    }\n")
        assertTrue(vari.contains("DesignerCanvas.variations(") && vari.contains("enoughMemory = { Illustrator.enoughMemory(app) }") && vari.contains("refused(subject)"))
        assertTrue(lab.substringAfter("fun generate(subject: String) {").substringBefore("\n    }\n").contains("if (refused(subject))"))
        assertTrue(lab.contains("team.sopa.boar.engine.routing.ImageSafety.blocked(subject)"))
        // Draw again goes through generate, so through the same check.
        assertTrue(lab.substringAfter("fun regenerate() {").substringBefore("\n    }\n").contains("generate(subject)"))
        val ill = src("Illustrator.kt")
        assertTrue(ill.contains("fun enoughMemory(ctx: Context): Boolean = freeBytes(ctx) >= MIN_FREE_BYTES") && ill.contains("if (!enoughMemory(ctx))"))
        assertTrue(ill.contains("ImageSafety.safeSubject("))
    }

    @Test fun `the page is a full canvas, its settings in a sheet, Back stops a drawing`() {
        val screen = src("ImageLabScreen.kt")
        assertTrue(screen.contains("override val fullPage = true"))
        assertTrue(screen.contains("if (state.phase != null) androidx.activity.compose.BackHandler { vm.cancel() }"))
        val canvas = screen.substringAfter("fun DesignerCanvasScreen(").substringBefore("\n}\n")
        assertTrue(!canvas.contains("removeModel") && canvas.contains("SettingsSheet(state, vm)"))
        val settings = screen.substringAfter("private fun SettingsSheet(").substringBefore("\n}\n")
        assertTrue(settings.contains("native.labs.image.removeModel") && settings.contains("native.labs.wait.setting") && settings.contains("AboutPluginFold(ImageGenLab)"))
        // The strip: the latest 12, and All opens the media manager.
        assertTrue(screen.contains("private const val STRIP = 12") && screen.contains("HistoryStrip(state, onShow = showFile, onManage = onManage)"))
    }

    @Test fun `the i sheet draws again, four variations, deletes, and shares nothing`() {
        val screen = src("ImageLabScreen.kt")
        val sheet = screen.substringAfter("private fun InfoSheet(").substringBefore("\n}\n")
        assertTrue(sheet.contains("vm.regenerate()") && sheet.contains("vm.variations()") && sheet.contains("vm.delete(f)"))
        for (k in listOf("words", "styleLabel", "time")) assertTrue(k, sheet.contains("native.labs.image.$k"))
        assertTrue(!Regex("share|photos|ACTION_SEND|MediaStore", RegexOption.IGNORE_CASE).containsMatchIn(sheet))
        for (d in listOf("values", "values-pt")) assertTrue(d, !strings(d).contains("native_labs_image_share"))
    }

    @Test fun `new and changed strings in English and Portuguese`() {
        val keys = listOf("menu", "drawings", "allDrawings",
            "styleHint", "styleHintNone", "emptyCanvas", "noModelTitle", "noModelCaption",
            "all", "next", "infoButton", "fullButton", "infoTitle", "words", "time", "drawAgain", "drawAgainNote", "variations", "variationsTime",
            "variationsNote", "variationOf", "variationsPick", "variationOpen", "settingsTitle", "waitSketch", "shape_banner", "placeholder")
        for (d in listOf("values", "values-pt")) for (k in keys) assertTrue("$d $k", strings(d).contains("name=\"native_labs_image_$k\""))
        assertTrue(strings("values").contains("name=\"native_labs_image_shape_banner\" formatted=\"false\">Wide<"))
    }
}
