package team.sopa.boar.engine.labs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** rc3 slice 4: the trail where the rain was, the rain kept as a choice, motion settings respected. */
class WaitingAnimationTest {
    private val labs = File("../app/src/labs")
    private fun read(p: String) = File(labs, p).readText()

    @Test fun `the closing screen uses the picked animation, Designer its sketch or the rain`() {
        assertTrue(read("java/team/sopa/boar/labs/LabHooks.kt").contains("WaitingAnimation(androidx.compose.ui.Modifier.fillMaxSize(), showBoar = false)"))
        // Designer has its own wait since Nov 12 (the sketch, or the Matrix rain when picked).
        assertTrue(read("plugins/image/java/team/sopa/boar/labs/image/ImageLabScreen.kt").contains("team.sopa.boar.labs.DesignerWait("))
    }

    @Test fun `the matrix rain stays, as the other choice, and the trail is the default`() {
        assertTrue(read("java/team/sopa/boar/labs/MatrixRain.kt").contains("fun MatrixRain("))
        val trail = read("java/team/sopa/boar/labs/BoarTrail.kt")
        assertTrue(trail.contains("LabPrefs.WAIT_MATRIX) MatrixRain(modifier, label) else BoarTrail(modifier, label, showBoar)"))
        assertTrue(read("java/team/sopa/boar/labs/LabPlugin.kt").contains("mutableStateOf(WAIT_TRAIL)"))
    }

    @Test fun `it holds still with animations off and draws nothing in the background`() {
        val trail = read("java/team/sopa/boar/labs/BoarTrail.kt")
        assertTrue(trail.contains("ANIMATOR_DURATION_SCALE"))
        assertTrue(trail.contains("repeatOnLifecycle(Lifecycle.State.RESUMED)"))
    }

    @Test fun `the choice is in the Labs settings, in English and Portuguese`() {
        assertTrue(read("java/team/sopa/boar/labs/LabHooks.kt").contains("native.labs.wait.setting"))
        for (dir in listOf("values", "values-pt")) {
            val xml = read("res/$dir/strings_labs.xml")
            for (k in listOf("native_labs_wait_setting", "native_labs_wait_trail", "native_labs_wait_matrix")) assertTrue("$dir $k", xml.contains("name=\"$k\""))
        }
    }
}
