package team.sopa.boar.labs.image

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import java.io.File

/**
 * Boar Designer's self-test (adb: run=labtest, lab=image): draws every size, each in a style taken in turn (all=1: every
 * size in every style), checks each picture's pixel size, then cancels one drawing part way and checks nothing is left.
 * The test pictures are deleted, so the user's gallery doesn't change.
 */
internal object DesignerSelfTest {
    private val SUBJECTS = listOf("a boar in a pine forest", "a lighthouse in a storm", "a campfire under the stars", "a mountain hut in snow",
        "an old map with a compass", "a fox on a mossy log", "a river canyon at dawn", "a desert well", "a red tent by a lake")

    suspend fun run(ctx: Context, args: Map<String, String>, say: (String) -> Unit) {
        if (!SdRunner.ready(ctx)) { say("FAIL designer: the drawing model isn't on this phone or the engine isn't in this build"); return }
        val styles = ImageLab.Style.entries
        val plan = if (args["all"] == "1") ImageLab.CANVASES.flatMap { c -> styles.map { c to it } }
            else ImageLab.CANVASES.mapIndexed { i, c -> c to styles[i % styles.size] }
        val seed = args["seed"]?.toIntOrNull() ?: 7
        var ok = 0; var fail = 0
        plan.forEachIndexed { i, (size, style) ->
            val subject = SUBJECTS[i % SUBJECTS.size]
            val line = "${size.key} ${style.key}"
            runCatching { SdRunner.draw(ctx, subject, style, size, seed) }.onSuccess { d ->
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(d.file.path, o)
                if (o.outWidth == size.w && o.outHeight == size.h) { ok++; say("ok designer $line ${"%.1f".format(d.seconds ?: -1.0)} s") }
                else { fail++; say("FAIL designer $line: picture is ${o.outWidth}x${o.outHeight}") }
                forget(d.file)
            }.onFailure { fail++; say("FAIL designer $line: ${it.message}") }
        }
        // Cancelled part way: no picture and no engine process left behind.
        val before = SdRunner.outDir(ctx).listFiles().orEmpty().toSet()
        coroutineScope {
            val job = launch { runCatching { SdRunner.draw(ctx, SUBJECTS[0], styles[0], ImageLab.CANVASES.last(), seed) } }
            delay(3_000); job.cancel(); job.join()
        }
        delay(500)
        val left = SdRunner.outDir(ctx).listFiles().orEmpty().toSet() - before
        if (left.isEmpty() && !alive()) { ok++; say("ok designer cancel: nothing left") }
        else { fail++; say("FAIL designer cancel: ${left.map { it.name }} left, engine running ${alive()}") }
        say("designer done: $ok ok, $fail failed")
    }

    private fun alive() = runCatching { Runtime.getRuntime().exec(arrayOf("pidof", "libsdcli.so", "libsdcli_i8mm.so")).inputStream.bufferedReader().readText().isNotBlank() }.getOrDefault(false)

    private fun forget(f: File) {
        f.delete()
        f.parentFile?.listFiles { x -> x.name.startsWith(f.nameWithoutExtension) }?.forEach { it.delete() }
    }
}
