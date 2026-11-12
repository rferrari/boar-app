package team.sopa.boar.engine.labs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import team.sopa.boar.engine.labs.DesignerCanvas.Shape
import team.sopa.boar.engine.labs.DesignerCanvas.Size
import team.sopa.boar.engine.labs.DesignerCanvas.Stop
import team.sopa.boar.engine.labs.DesignerCanvas.Tier

/** Designer's canvas (adam's mock-up, Nov 13): shape and speed instead of pixels, the default, the rail, the variations. */
class DesignerCanvasTest {
    private val dir = "../app/src/labs/plugins/designer"
    private fun src(name: String) = java.io.File("$dir/java/team/sopa/boar/labs/designer/$name").readText()
    private fun strings(d: String) = java.io.File("$dir/res/$d/strings_lab_image.xml").readText()

    @Test fun `Fast is the middle size of each shape and Slow the largest`() {
        assertEquals(Size(512, 512), DesignerCanvas.size(Shape.SQUARE, Tier.FAST))
        assertEquals(Size(640, 640), DesignerCanvas.size(Shape.SQUARE, Tier.SLOW))
        assertEquals(Size(384, 512), DesignerCanvas.size(Shape.PORTRAIT, Tier.FAST))
        assertEquals(Size(448, 640), DesignerCanvas.size(Shape.PORTRAIT, Tier.SLOW))
        assertEquals(Size(640, 320), DesignerCanvas.size(Shape.WIDE, Tier.FAST))
        assertEquals(Size(768, 320), DesignerCanvas.size(Shape.WIDE, Tier.SLOW))
        // Six sizes offered, each its own shape and tier back, and none of the removed small ones.
        assertEquals(6, DesignerCanvas.OFFERED.size)
        for (s in DesignerCanvas.OFFERED) assertEquals(s, DesignerCanvas.size(s.shape, s.tier))
        assertTrue(DesignerCanvas.REMOVED.none { it in DesignerCanvas.OFFERED })
        assertEquals(listOf("512", "640", "384x512", "448x640", "640x320", "768x320"), DesignerCanvas.OFFERED.map { it.key })
    }

    @Test fun `a saved size of a removed one becomes Fast of the same shape, an offered one stays`() {
        assertEquals(Size(512, 512), DesignerCanvas.migrate("384"))
        assertEquals(Size(384, 512), DesignerCanvas.migrate("320x448"))
        assertEquals(Size(640, 320), DesignerCanvas.migrate("576x256"))
        for (s in DesignerCanvas.OFFERED) assertEquals(s, DesignerCanvas.migrate(s.key))
        for (s in DesignerCanvas.REMOVED) assertEquals(Tier.FAST, DesignerCanvas.migrate(s.key)!!.tier)
        for (s in DesignerCanvas.REMOVED) assertEquals(s.shape, DesignerCanvas.migrate(s.key)!!.shape)
        assertNull(DesignerCanvas.migrate(null)); assertNull(DesignerCanvas.migrate("")); assertNull(DesignerCanvas.migrate("big"))
        // Nothing saved, or something unreadable: Engraving's default size, Square and Fast.
        assertEquals(null to DesignerCanvas.DEFAULT, DesignerCanvas.loadDefault { null })
        assertEquals(Size(512, 512), DesignerCanvas.loadDefault { k -> if (k == DesignerCanvas.SIZE_PREF) "nonsense" else null }.second)
        assertEquals(Size(512, 512), DesignerCanvas.loadDefault { k -> if (k == DesignerCanvas.SIZE_PREF) "384" else null }.second)
    }

    @Test fun `the default keeps style, shape and speed together, and is read back the same`() {
        for (s in DesignerCanvas.OFFERED) {
            val prefs = HashMap<String, String>()
            DesignerCanvas.saveDefault(prefs::put, "watercolor", s)
            assertEquals("watercolor" to s, DesignerCanvas.loadDefault(prefs::get))
        }
        val prefs = HashMap<String, String>()
        DesignerCanvas.saveDefault(prefs::put, "cyber", Size(576, 256))
        assertEquals("640x320", prefs[DesignerCanvas.SIZE_PREF])
        // The app saves and reads through these, under its old keys (a default saved before Nov 13 carries over).
        assertEquals("image.style", DesignerCanvas.STYLE_PREF); assertEquals("image.size", DesignerCanvas.SIZE_PREF)
        val lab = src("ImageLab.kt")
        assertTrue(lab.contains("DesignerCanvas.saveDefault(team.sopa.boar.labs.LabPrefs::putString, style.key, canvas.size)"))
        assertTrue(lab.contains("DesignerCanvas.loadDefault(team.sopa.boar.labs.LabPrefs::string).second"))
        assertTrue(lab.substringAfter("fun setDefaults() {").substringBefore("\n    }").contains("ImageLab.setDefaults(s.style, s.size)"))
        // "★ Default" in both trays, lit when the pick is the default.
        val screen = src("ImageLabScreen.kt")
        assertTrue(screen.contains("Pill(t(\"native.labs.image.default\"), selected = isDefault) { vm.setDefaults() }"))
    }

    @Test fun `the caption is style, shape and this phone's seconds, else the speed word`() {
        assertEquals("Engraving · Square · 9 s", DesignerCanvas.caption("Engraving", "Square", 9, "Fast"))
        assertEquals("Lo-fi · Wide · Slow", DesignerCanvas.caption("Lo-fi", "Wide", null, "Slow"))
        assertEquals(40, DesignerCanvas.variationsSeconds(10)); assertNull(DesignerCanvas.variationsSeconds(null))
        assertTrue(src("ImageLabScreen.kt").contains("DesignerCanvas.caption(styleName(state.recipe?.style ?: state.style.key), shapeName(shown.shape), ImageLab.expectedSeconds(shown), tierName(shown.tier))"))
    }

    @Test fun `the rail has exactly two tools, Style and Shape`() {
        assertEquals(listOf("style", "shape"), DesignerCanvas.Tool.entries.map { it.key })
        val screen = src("ImageLabScreen.kt")
        val rail = screen.substringAfter("private fun Rail(").substringBefore("\n}\n")
        assertTrue(rail.contains("for (tool in DesignerCanvas.Tool.entries)"))
        assertEquals(1, Regex("Rail\\(tray").findAll(screen).count())
        for (d in listOf("values", "values-pt")) {
            val tools = Regex("name=\"native_labs_image_tool_([a-z]+)\"").findAll(strings(d)).map { it.groupValues[1] }.toList()
            assertEquals(d, listOf("style", "shape"), tools.sorted().reversed())
        }
        // Hidden by adam on Nov 13: no Seed, Edit or More anywhere on the page.
        for (gone in listOf("tool.seed", "tool.edit", "tool.more", "seedKeep", "seedNew", "setSeedLocked(")) assertTrue(gone, !screen.contains(gone))
    }

    @Test fun `no pixel numbers in Designer's strings, English and Portuguese`() {
        val px = Regex("\\d+\\s*[x×]\\s*\\d+|\\d+\\s*px\\b")
        for (d in listOf("values", "values-pt")) for (m in Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(strings(d)))
            assertTrue("$d ${m.groupValues[1]}: ${m.groupValues[2]}", !px.containsMatchIn(m.groupValues[2]))
        // Nor built in code: the pixel label is gone, the screen says sizes with the shape and speed words.
        val lab = src("ImageLab.kt"); val screen = src("ImageLabScreen.kt")
        assertTrue(!lab.contains("val label") && !screen.contains(".label") && !screen.contains("\" px"))
        assertTrue(screen.contains("t(\"native.labs.image.shapeTier\""))
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
        // One round button: Draw, or Stop while drawing.
        assertTrue(screen.contains("if (busy) onStop() else onDraw()"))
    }

    @Test fun `the i sheet draws again, four variations, deletes, and shares nothing`() {
        val screen = src("ImageLabScreen.kt")
        val sheet = screen.substringAfter("private fun InfoSheet(").substringBefore("\n}\n")
        assertTrue(sheet.contains("vm.regenerate()") && sheet.contains("vm.variations()") && sheet.contains("vm.delete(f)"))
        for (k in listOf("words", "styleLabel", "shapeLabel", "time")) assertTrue(k, sheet.contains("native.labs.image.$k"))
        assertTrue(!Regex("share|photos|ACTION_SEND|MediaStore", RegexOption.IGNORE_CASE).containsMatchIn(sheet))
        for (d in listOf("values", "values-pt")) assertTrue(d, !strings(d).contains("native_labs_image_share"))
    }

    @Test fun `new and changed strings in English and Portuguese`() {
        val keys = listOf("tool_style", "tool_shape", "shapeTitle", "shapeLabel", "speedLabel", "tier_fast", "tier_slow", "tierSeconds", "shapeTier",
            "shapeHint", "shapeHintUntimed", "default", "done", "styleHint", "styleHintNone", "emptyCanvas", "noModelTitle", "noModelCaption", "stop",
            "all", "next", "infoButton", "fullButton", "infoTitle", "words", "time", "drawAgain", "drawAgainNote", "variations", "variationsTime",
            "variationsNote", "variationOf", "variationsPick", "variationOpen", "settingsTitle", "waitSketch", "shape_banner", "placeholder")
        for (d in listOf("values", "values-pt")) for (k in keys) assertTrue("$d $k", strings(d).contains("name=\"native_labs_image_$k\""))
        assertTrue(strings("values").contains(">Seconds are this phone\\'s own average. Slower draws a bigger picture.<"))
        assertTrue(strings("values").contains("name=\"native_labs_image_shape_banner\" formatted=\"false\">Wide<"))
    }
}
