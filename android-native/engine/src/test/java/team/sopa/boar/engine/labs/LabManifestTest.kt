package team.sopa.boar.engine.labs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** src/labs/released.txt: what a release build includes, and the tests behind each lab (rc3 slice 3). */
class LabManifestTest {
    private val plugins = File("../app/src/labs/plugins")
    private val manifest: Map<String, List<String>> = File("../app/src/labs/released.txt").readLines()
        .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
        .associate { line -> line.substringBefore(':').trim() to line.substringAfter(':', "").trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }

    private val testFiles: Map<String, File> = listOf(File("src/test"), File("../app/src/test"), File("../app/src/androidTest"))
        .filter { it.isDirectory }.flatMap { it.walk().filter { f -> f.extension == "kt" }.toList() }.associateBy { it.nameWithoutExtension }

    @Test fun `every listed lab is a plugin with code in this repo`() {
        assertTrue(manifest.isNotEmpty())
        for (id in manifest.keys) {
            val props = File(plugins, "$id/plugin.properties")
            assertTrue("$id has no plugin folder", props.isFile)
            assertTrue("$id has no code (object=)", props.readLines().any { it.trim().startsWith("object=") })
        }
    }

    @Test fun `every listed lab has tests, and each named test exists and has a test in it`() {
        for ((id, tests) in manifest) {
            assertTrue("$id lists no tests", tests.isNotEmpty())
            for (name in tests) {
                val f = testFiles[name]
                assertTrue("$id: $name not found under engine or app tests", f != null)
                assertTrue("$id: $name has no @Test", f!!.readText().contains("@Test"))
            }
        }
    }

    @Test fun `every lab that starts on, or waits to, ships in a release`() {
        for (id in LabDefaults.ON + LabDefaults.PENDING_ALPHA) assertTrue("$id starts on but isn't in released.txt", id in manifest)
    }

    @Test fun `a lab under review doesn't ship`() {
        for (dir in plugins.listFiles().orEmpty()) {
            val props = File(dir, "plugin.properties").takeIf { it.isFile }?.readLines() ?: continue
            if (props.any { it.trim() == "review=true" }) assertTrue("${dir.name} is under review but listed", dir.name !in manifest)
        }
    }

    @Test fun `the build reads the manifest`() {
        val gradle = File("../app/build.gradle.kts").readText()
        assertTrue(gradle.contains("src/labs/released.txt"))
        assertTrue(gradle.contains("inBuild()"))
    }
}
