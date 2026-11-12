package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SketchTest {
    @Test fun `the same strokes every time, drawn a little more as time passes`() {
        val s = Sketch.strokes()
        assertEquals(s, Sketch.strokes())
        val early = Sketch.progress(s, 0.5, still = false).sum()
        val later = Sketch.progress(s, 5.0, still = false).sum()
        assertTrue(later > early)
        assertTrue(s.all { st -> st.points.all { (x, y) -> x in 0f..1f && y in 0f..1f } })
    }

    @Test fun `with animations off one calm frame that never changes`() {
        val s = Sketch.strokes()
        assertEquals(Sketch.progress(s, 0.1, still = true), Sketch.progress(s, 7.3, still = true))
        assertTrue(Sketch.progress(s, 0.0, still = true).any { it > 0f })
    }

    @Test fun `each style has its ink and paper, ink on paper for engraving`() {
        assertEquals(Sketch.Palette(0xFFF4EFE4, 0xFF1C1C1C), Sketch.palette("engraving"))
        assertTrue(Sketch.palette("cyber").paper < 0xFF202020)
    }

    @Test fun `Designer waits with the sketch and reveals the picture by a crossfade, instant with animations off`() {
        val page = java.io.File("../app/src/labs/plugins/designer/java/team/sopa/boar/labs/designer/ImageLabScreen.kt").readText()
        assertTrue(page.contains("DesignerWait(") && page.contains("Crossfade("))
        val wait = java.io.File("../app/src/labs/java/team/sopa/boar/labs/SketchWait.kt").readText()
        assertTrue(wait.contains("LabPrefs.WAIT_MATRIX) MatrixRain(") && wait.contains("ANIMATOR_DURATION_SCALE"))
        assertTrue(wait.contains("fun revealMs(): Int = if (animationsOff()) 0 else REVEAL_MS"))
    }

    @Test fun `the loop never restarts all at once, past the first seconds there is always ink, and no frame jumps`() {
        val s = Sketch.strokes()
        val p = FloatArray(s.size); val a = FloatArray(s.size)
        var last = -1f
        var t = 3.0
        while (t < Sketch.CYCLE_S * 4) {
            Sketch.frame(s, t, still = false, progress = p, alpha = a)
            val ink = p.indices.sumOf { (p[it] * a[it]).toDouble() }.toFloat()
            assertTrue("no ink at $t", ink > 1f)
            // 60 fps: from one frame to the next the total ink moves only a little (no reset frame at the seam).
            if (last >= 0f) assertTrue("jump at $t: $last -> $ink", kotlin.math.abs(ink - last) < 0.6f)
            last = ink
            t += 1.0 / 60
        }
    }

    @Test fun `the frame writes into the given arrays and allocates no list`() {
        val code = java.io.File("src/main/java/team/sopa/boar/engine/labs/Sketch.kt").readText()
        val frame = code.substringAfter("fun frame(").substringBefore("\n    }\n")
        assertTrue(!frame.contains("map {") && !frame.contains("List(") && !frame.contains("listOf("))
    }

    @Test fun `the sketch sits on a dark background with light ink tinted by the style`() {
        val wait = java.io.File("../app/src/labs/java/team/sopa/boar/labs/SketchWait.kt").readText()
        assertTrue(wait.contains("background(Color.Black)") && wait.contains("lerp(Color(palette.ink), Color.White"))
    }
}
