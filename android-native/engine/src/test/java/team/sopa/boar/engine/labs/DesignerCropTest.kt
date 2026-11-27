package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.labs.DesignerCrop.Ratio
import team.sopa.boar.engine.labs.DesignerCrop.Rect

/** BOAR-88 (adam, Nov 26): crop as a Designer tool, 16:9, 4:3, 9:16 and free, saved as a copy beside the 512 original. */
class DesignerCropTest {
    private fun inside(r: Rect, w: Int = 512, h: Int = 512) = r.x >= 0 && r.y >= 0 && r.w > 0 && r.h > 0 && r.x + r.w <= w && r.y + r.h <= h

    @Test fun `each ratio starts as its largest rectangle, centred inside the 512 picture`() {
        assertEquals(Rect(0, 112, 512, 288), DesignerCrop.initial(Ratio.WIDE, 512, 512))
        assertEquals(Rect(0, 64, 512, 384), DesignerCrop.initial(Ratio.STANDARD, 512, 512))
        assertEquals(Rect(112, 0, 288, 512), DesignerCrop.initial(Ratio.TALL, 512, 512))
        assertEquals(Rect(0, 0, 512, 512), DesignerCrop.initial(Ratio.FREE, 512, 512))
        assertEquals(listOf("16:9", "4:3", "9:16", "free"), Ratio.entries.map { it.key })
        // An older drawing of another size (448x640) crops inside its own pixels.
        val r = DesignerCrop.initial(Ratio.WIDE, 448, 640)
        assertTrue(inside(r, 448, 640)); assertEquals(448, r.w); assertEquals(252, r.h)
    }

    @Test fun `moving keeps the rectangle inside the picture and its size`() {
        val start = DesignerCrop.initial(Ratio.WIDE, 512, 512)
        assertEquals(Rect(0, 0, 512, 288), DesignerCrop.move(start, 50, -500, 512, 512))
        assertEquals(Rect(0, 224, 512, 288), DesignerCrop.move(start, 0, 9999, 512, 512))
        val tall = DesignerCrop.initial(Ratio.TALL, 512, 512)
        assertEquals(Rect(200, 0, 288, 512), DesignerCrop.move(tall, 88, 0, 512, 512))
        assertEquals(Rect(224, 0, 288, 512), DesignerCrop.move(tall, 1000, 0, 512, 512))
    }

    @Test fun `resizing keeps the ratio, stays inside, never below the smallest side`() {
        val wide = DesignerCrop.initial(Ratio.WIDE, 512, 512)
        val smaller = DesignerCrop.resize(wide, -256, 0, Ratio.WIDE, 512, 512)
        assertEquals(256, smaller.w); assertEquals(144, smaller.h); assertEquals(wide.x, smaller.x); assertEquals(wide.y, smaller.y)
        val tiny = DesignerCrop.resize(wide, -10_000, 0, Ratio.WIDE, 512, 512)
        assertTrue(tiny.w >= DesignerCrop.MIN_SIDE && tiny.h >= DesignerCrop.MIN_SIDE)
        assertTrue(kotlin.math.abs(tiny.w * 9 - tiny.h * 16) < 16)
        for (ratio in listOf(Ratio.WIDE, Ratio.STANDARD, Ratio.TALL)) {
            val big = DesignerCrop.resize(DesignerCrop.move(DesignerCrop.resize(DesignerCrop.initial(ratio, 512, 512), -200, -200, ratio, 512, 512), 40, 40, 512, 512), 10_000, 10_000, ratio, 512, 512)
            assertTrue("$ratio $big", inside(big))
            assertTrue("$ratio $big", kotlin.math.abs(big.w * ratio.h - big.h * ratio.w) < maxOf(ratio.w, ratio.h))
        }
        // Free: each side on its own, inside, at least the smallest side.
        val free = DesignerCrop.resize(Rect(0, 0, 512, 512), -400, -100, Ratio.FREE, 512, 512)
        assertEquals(Rect(0, 0, 112, 412), free)
        assertEquals(Rect(0, 0, 64, 64), DesignerCrop.resize(Rect(0, 0, 512, 512), -9999, -9999, Ratio.FREE, 512, 512))
        assertEquals(Rect(100, 100, 412, 412), DesignerCrop.resize(Rect(100, 100, 200, 200), 9999, 9999, Ratio.FREE, 512, 512))
    }

    @Test fun `the copy is named so Designer, the gallery and Press read its size, and never replaces the original`() {
        val name = DesignerCrop.fileName(1732650000000, "photo", Rect(0, 112, 512, 288))
        assertEquals("1732650000000-photo-crop-512x288.png", name)
        assertEquals("1732650000000-none-crop-288x512.png", DesignerCrop.fileName(1732650000000, null, Rect(112, 0, 288, 512)))
        // The size in the name is what ImageLab.canvasOf reads ("-(\d+(?:x\d+)?)\.png$").
        assertEquals("512x288", Regex("-(\\d+(?:x\\d+)?)\\.png$").find(name)!!.groupValues[1])
        assertTrue(DesignerCrop.isCrop(name)); assertFalse(DesignerCrop.isCrop("1732650000000-photo-512.png"))
        // Its shape in the Press receipt comes from its pixels.
        assertEquals(PressReceipt.Shape.WIDE, PressReceipt.Shape.of(512, 288)); assertEquals(PressReceipt.Shape.PORTRAIT, PressReceipt.Shape.of(288, 512))
    }

    @Test fun `the Designer page crops the shown picture into a copy, from its toolbar`() {
        val dir = "../app/src/labs/plugins/designer"
        val screen = java.io.File("$dir/java/team/sopa/boar/labs/designer/ImageLabScreen.kt").readText()
        val lab = java.io.File("$dir/java/team/sopa/boar/labs/designer/ImageLab.kt").readText()
        assertEquals(listOf("style", "crop"), DesignerCanvas.Section.entries.map { it.key })
        assertTrue(screen.contains("team.sopa.boar.R.drawable.ic_designer_crop") && screen.contains("CropScreen(f, state.recipe?.style ?: state.style.key"))
        // Crop only with a picture shown, and not while drawing or showing the variations.
        assertTrue(screen.contains("enabled = can && state.image != null && state.variations == null"))
        val crop = lab.substringAfter("fun crop(").substringBefore("\n    }\n")
        assertTrue(crop.contains("DesignerCrop.fileName(") && crop.contains("Bitmap.createBitmap(") && crop.contains("ImageLab.writeRecipe("))
        assertTrue(!crop.contains("deleteDrawing") && !crop.contains(".delete()"))
        // Draw again on a crop draws a new 512 picture and keeps the crop.
        assertTrue(lab.contains("!DesignerCrop.isCrop(it.name)"))
        val cropScreen = java.io.File("$dir/java/team/sopa/boar/labs/designer/CropScreen.kt").readText()
        assertTrue(cropScreen.contains("DesignerCrop.Ratio.entries") && cropScreen.contains("DesignerCrop.move(") && cropScreen.contains("DesignerCrop.resize("))
        // No crop in the chat's drawer: a chat drawing is cropped in Designer.
        assertFalse(screen.substringAfter("private fun IllustrateSheet(").contains("Crop"))
        for (d in listOf("values", "values-pt")) {
            val s = java.io.File("$dir/res/$d/strings_lab_image.xml").readText()
            for (k in listOf("crop", "cropNote", "cropFree", "cropSave", "cropFailed")) assertTrue("$d $k", s.contains("name=\"native_labs_image_$k\""))
        }
    }

    @Test fun `the guide, Designer's How it works and its README say square and Crop (BOAR-88)`() {
        val assets = "../app/src/main/assets/boar-guide"
        assertTrue(java.io.File("$assets/en.md").readText().contains("Crop saves a wide or tall copy"))
        assertTrue(java.io.File("$assets/pt.md").readText().contains("Recortar salva uma cópia larga ou alta"))
        val dir = "../app/src/labs/plugins/designer"
        assertTrue(java.io.File("$dir/res/values/strings_lab_image.xml").readText().contains("• Every drawing is square; Crop saves a wide or tall copy and keeps the drawing."))
        assertTrue(java.io.File("$dir/res/values-pt/strings_lab_image.xml").readText().contains("• Todo desenho é quadrado; Recortar salva uma cópia larga ou alta e mantém o desenho."))
        val readme = java.io.File("$dir/README.md").readText()
        assertTrue(readme.contains("## Size and crop") && readme.contains("512x512 only"))
    }

    @Test fun `a ratio frame grows and shrinks one pixel at a time too (CodeRabbit on PR 125)`() {
        // A tall 180x320 frame dragged down 1 px at a time: each step used to round back to the same size.
        var r = Rect(0, 0, 180, 320)
        repeat(40) { r = DesignerCrop.resize(r, 0, 1, Ratio.TALL, 512, 512) }
        assertTrue("$r", r.h >= 350 && kotlin.math.abs(r.w * 16 - r.h * 9) < 16)
        repeat(40) { r = DesignerCrop.resize(r, 0, -1, Ratio.TALL, 512, 512) }
        assertTrue("$r", r.h <= 330)
        var w = Rect(0, 0, 320, 180)
        repeat(30) { w = DesignerCrop.resize(w, 0, 1, Ratio.WIDE, 512, 512) }
        assertTrue("$w", w.h >= 200)
    }
}
