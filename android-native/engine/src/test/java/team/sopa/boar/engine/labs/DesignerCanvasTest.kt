package team.sopa.boar.engine.labs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import team.sopa.boar.engine.labs.DesignerCanvas.Quality
import team.sopa.boar.engine.labs.DesignerCanvas.Shape
import team.sopa.boar.engine.labs.DesignerCanvas.Size
import team.sopa.boar.engine.labs.DesignerCanvas.Stop

/** Designer's canvas (adam's mock-up, Nov 12-13): shape and quality instead of pixels, the default, the drawer, the shared input bar, the variations. */
class DesignerCanvasTest {
    private val dir = "../app/src/labs/plugins/designer"
    private fun src(name: String) = java.io.File("$dir/java/team/sopa/boar/labs/designer/$name").readText()
    private fun strings(d: String) = java.io.File("$dir/res/$d/strings_lab_image.xml").readText()

    @Test fun `Low, Medium and High are the three sizes of each shape`() {
        val expect = mapOf(
            Shape.SQUARE to listOf(Size(384, 384), Size(512, 512), Size(640, 640)),
            Shape.PORTRAIT to listOf(Size(320, 448), Size(384, 512), Size(448, 640)),
            Shape.WIDE to listOf(Size(576, 256), Size(640, 320), Size(768, 320)),
        )
        for ((shape, sizes) in expect) assertEquals(shape.name, sizes, Quality.entries.map { DesignerCanvas.size(shape, it) })
        // Nine sizes offered, each its own shape and quality back.
        assertEquals(9, DesignerCanvas.OFFERED.size)
        for (s in DesignerCanvas.OFFERED) assertEquals(s, DesignerCanvas.size(s.shape, s.quality))
        assertEquals(listOf("low", "medium", "high"), Quality.entries.map { it.key })
    }

    @Test fun `migration both ways - Fast is Medium, Slow is High, and every saved size keeps its own level`() {
        assertEquals(Quality.MEDIUM, DesignerCanvas.fromSpeed("fast")); assertEquals(Quality.HIGH, DesignerCanvas.fromSpeed("Slow")); assertNull(DesignerCanvas.fromSpeed("x"))
        // What the Fast/Slow build saved (the middle and the largest sizes) reads back as Medium and High.
        assertEquals(Quality.MEDIUM, DesignerCanvas.migrate("512")!!.quality); assertEquals(Quality.HIGH, DesignerCanvas.migrate("640")!!.quality)
        assertEquals(Quality.MEDIUM, DesignerCanvas.migrate("384x512")!!.quality); assertEquals(Quality.HIGH, DesignerCanvas.migrate("768x320")!!.quality)
        // The smallest sizes, saved before Nov 13, are Low again, not moved.
        assertEquals(Size(384, 384), DesignerCanvas.migrate("384")); assertEquals(Quality.LOW, DesignerCanvas.migrate("320x448")!!.quality)
        assertEquals(Size(576, 256), DesignerCanvas.migrate("576x256"))
        for (s in DesignerCanvas.OFFERED) assertEquals(s, DesignerCanvas.migrate(s.key))
        // A size that is none of the nine becomes Medium of its shape.
        assertEquals(Size(640, 320), DesignerCanvas.migrate("1024x400")); assertEquals(Size(384, 512), DesignerCanvas.migrate("100x900"))
        assertNull(DesignerCanvas.migrate(null)); assertNull(DesignerCanvas.migrate("")); assertNull(DesignerCanvas.migrate("big"))
        // Nothing saved, or something unreadable: Square, Medium.
        assertEquals(null to DesignerCanvas.DEFAULT, DesignerCanvas.loadDefault { null })
        assertEquals(Size(512, 512), DesignerCanvas.DEFAULT)
        assertEquals(Size(512, 512), DesignerCanvas.loadDefault { k -> if (k == DesignerCanvas.SIZE_PREF) "nonsense" else null }.second)
        assertEquals(Size(384, 384), DesignerCanvas.loadDefault { k -> if (k == DesignerCanvas.SIZE_PREF) "384" else null }.second)
    }

    @Test fun `the default keeps style, shape and quality together, and is read back the same`() {
        for (s in DesignerCanvas.OFFERED) {
            val prefs = HashMap<String, String>()
            DesignerCanvas.saveDefault(prefs::put, "watercolor", s)
            assertEquals("watercolor" to s, DesignerCanvas.loadDefault(prefs::get))
        }
        val prefs = HashMap<String, String>()
        DesignerCanvas.saveDefault(prefs::put, "cyber", Size(576, 256))
        assertEquals("576x256", prefs[DesignerCanvas.SIZE_PREF])
        // The app saves and reads through these, under its old keys (a default saved before carries over).
        assertEquals("image.style", DesignerCanvas.STYLE_PREF); assertEquals("image.size", DesignerCanvas.SIZE_PREF)
        val lab = src("ImageLab.kt")
        assertTrue(lab.contains("DesignerCanvas.saveDefault(team.sopa.boar.labs.LabPrefs::putString, style.key, canvas.size)"))
        assertTrue(lab.contains("DesignerCanvas.loadDefault(team.sopa.boar.labs.LabPrefs::string).second"))
        assertTrue(lab.substringAfter("fun setDefaults() {").substringBefore("\n    }").contains("ImageLab.setDefaults(s.style, s.size)"))
        // No "★ Default" (adam, Nov 14): every pick is the default at once. Shape keeps the quality and Quality keeps the shape.
        val screen = src("ImageLabScreen.kt")
        assertTrue(!screen.contains("native.labs.image.default") && !screen.contains("★"))
        assertTrue(lab.contains("fun setStyle(s: ImageLab.Style) { _state.update { it.copy(style = s) }; setDefaults() }"))
        assertTrue(lab.contains("fun setShape(shape: DesignerCanvas.Shape) { setSize(ImageLab.Canvas.of(shape, _state.value.size.quality)); setDefaults() }"))
        assertTrue(lab.contains("fun setQuality(quality: DesignerCanvas.Quality) { setSize(ImageLab.Canvas.of(_state.value.size.shape, quality)); setDefaults() }"))
        // Showing an old drawing puts its recipe on the canvas, not in the default.
        assertTrue(!lab.substringAfter("fun show(f: File) {").substringBefore("\n    }").contains("setDefaults"))
    }

    @Test fun `the caption is style, shape and this phone's seconds, else the quality`() {
        assertEquals("Engraving · Square · 9 s", DesignerCanvas.caption("Engraving", "Square", 9, "Low quality"))
        assertEquals("Lo-fi · Wide · Low quality", DesignerCanvas.caption("Lo-fi", "Wide", null, "Low quality"))
        assertEquals(40, DesignerCanvas.variationsSeconds(10)); assertNull(DesignerCanvas.variationsSeconds(null))
        assertTrue(src("ImageLabScreen.kt").contains("DesignerCanvas.caption(styleName(state.recipe?.style ?: state.style.key), shapeName(shown.shape), ImageLab.expectedSeconds(shown), qualityLong(shown.quality))"))
    }

    @Test fun `no rail and no drawer - Style, Shape and Quality are toolbar tools, each its own sheet titled once`() {
        assertEquals(listOf("style", "shape", "quality"), DesignerCanvas.Section.entries.map { it.key })
        val screen = src("ImageLabScreen.kt")
        // adam, Nov 14: "Style, Shape, Quality on the toolbar… each opens its small sheet, titled once".
        assertTrue(!screen.contains("DesignerDrawer") && screen.contains("team.sopa.boar.labs.PluginToolbar(3)"))
        for (t in listOf("ic_designer_style", "ic_designer_shape", "ic_designer_quality")) assertTrue(t, screen.contains("team.sopa.boar.R.drawable.$t"))
        assertTrue(screen.contains("t(\"native.labs.image.styleLabel\") + \": \" + styleName(state.style.key)"))
        val sheet = screen.substringAfter("private fun ToolSheet(").substringBefore("\n}\n")
        assertTrue(sheet.contains("team.sopa.boar.labs.PluginSheetTitle(t(tool.titleKey))") && Regex("Text\\(").findAll(sheet).count() == 0)
        assertTrue(screen.contains("Tool.STYLE -> StyleSection(state, vm); Tool.SHAPE -> ShapeSection(state, vm); Tool.QUALITY -> QualitySection(state, vm)"))
        for (gone in listOf("fun Rail(", "Tray(", "DesignerCanvas.Tool")) assertTrue(gone, !screen.contains(gone))
        for (d in listOf("values", "values-pt")) {
            for (k in listOf("section_style", "section_shape", "section_quality", "drawer", "default")) assertTrue("$d $k", !strings(d).contains("name=\"native_labs_image_$k\""))
            assertTrue(d, !strings(d).contains("native_labs_image_tool_"))
        }
        // Hidden by adam on Nov 13: no Seed, Edit or More anywhere on the page.
        for (gone in listOf("tool.seed", "tool.edit", "tool.more", "seedKeep", "seedNew", "setSeedLocked(")) assertTrue(gone, !screen.contains(gone))
        // The Shape section has only the shapes; the levels are in the Quality section.
        val shape = screen.substringAfter("private fun ShapeSection(").substringBefore("\n}\n")
        assertTrue(!shape.contains("Quality.entries") && shape.contains("DesignerCanvas.Shape.entries"))
        assertTrue(screen.substringAfter("private fun QualitySection(").substringBefore("\n}\n").contains("DesignerCanvas.Quality.entries"))
    }

    @Test fun `Designer's bottom is the chat's own input bar - voice, Draw that becomes Stop, the same rest after a send`() {
        val screen = src("ImageLabScreen.kt")
        val canvas = screen.substringAfter("fun DesignerCanvasScreen(").substringBefore("\n}\n")
        assertTrue(canvas.contains("team.sopa.boar.ui.chat.PromptBar(subject, { subject = it }, answering = busy,"))
        assertTrue(canvas.contains("onSend = { vm.generate(subject); keyboard?.hide(); focus.clearFocus() }, onStop = { vm.cancel() }, voiceEnabled = voiceEnabled"))
        assertTrue(canvas.contains("sendIcon = team.sopa.boar.R.drawable.ic_designer_draw, sendLabel = t(\"native.labs.image.generate\"), queues = false, enabled = ready"))
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

    @Test fun `the quality hint says not timed only for the picked level when it isn't timed`() {
        val tray = src("ImageLabScreen.kt").substringAfter("private fun QualitySection(").substringBefore("\n}\n")
        assertTrue(tray.contains("if (ImageLab.expectedSeconds(state.size) != null) t(\"native.labs.image.qualityHint\")"))
        assertTrue(tray.contains("t(\"native.labs.image.qualityHintUntimed\", \"level\" to qualityLong(state.size.quality))"))
        for (d in listOf("values", "values-pt")) assertTrue(d, Regex("native_labs_image_qualityHintUntimed\"[^>]*>[^<]*\\{\\{level}}").containsMatchIn(strings(d)))
        assertTrue(strings("values").contains(">Higher quality draws a bigger picture and takes longer. Seconds are this phone\\'s own average.<"))
    }

    @Test fun `no pixel numbers in Designer's strings, English and Portuguese`() {
        val px = Regex("\\d+\\s*[x×]\\s*\\d+|\\d+\\s*px\\b")
        for (d in listOf("values", "values-pt")) for (m in Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(strings(d)))
            assertTrue("$d ${m.groupValues[1]}: ${m.groupValues[2]}", !px.containsMatchIn(m.groupValues[2]))
        // Nor built in code: the pixel label is gone, the screen says sizes with the shape and quality words.
        val lab = src("ImageLab.kt"); val screen = src("ImageLabScreen.kt")
        assertTrue(!lab.contains("val label") && !screen.contains(".label") && !screen.contains("\" px"))
        assertTrue(screen.contains("t(\"native.labs.image.shapeQuality\""))
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
        for (k in listOf("words", "styleLabel", "shapeLabel", "qualityLabel", "time")) assertTrue(k, sheet.contains("native.labs.image.$k"))
        // Shape and Quality on separate lines.
        assertTrue(sheet.contains("Fact(t(\"native.labs.image.shapeLabel\"), shapeName(it.shape)); Fact(t(\"native.labs.image.qualityLabel\"), qualityName(it.quality))"))
        assertTrue(!Regex("share|photos|ACTION_SEND|MediaStore", RegexOption.IGNORE_CASE).containsMatchIn(sheet))
        for (d in listOf("values", "values-pt")) assertTrue(d, !strings(d).contains("native_labs_image_share"))
    }

    @Test fun `new and changed strings in English and Portuguese`() {
        val keys = listOf("menu", "drawings", "allDrawings", "shapeLabel", "qualityLabel", "quality_low", "quality_medium",
            "quality_high", "qualityLong_low", "qualityLong_medium", "qualityLong_high", "qualitySeconds", "shapeQuality", "qualityHint", "qualityHintUntimed",
            "styleHint", "styleHintNone", "emptyCanvas", "noModelTitle", "noModelCaption",
            "all", "next", "infoButton", "fullButton", "infoTitle", "words", "time", "drawAgain", "drawAgainNote", "variations", "variationsTime",
            "variationsNote", "variationOf", "variationsPick", "variationOpen", "settingsTitle", "waitSketch", "shape_banner", "placeholder")
        for (d in listOf("values", "values-pt")) for (k in keys) assertTrue("$d $k", strings(d).contains("name=\"native_labs_image_$k\""))
        assertTrue(strings("values").contains("name=\"native_labs_image_shape_banner\" formatted=\"false\">Wide<"))
    }
}
