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

    /** Source without comments, so a word in one doesn't count. */
    private fun code(source: String) = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\n]*"), "")

    /** A test file that runs: an @Test outside comments that isn't @Ignore'd, in a class that isn't @Ignore'd. */
    private fun runsTests(source: String): Boolean {
        val c = code(source)
        if (Regex("@Ignore[\\s\\S]{0,200}?\\bclass\\s").containsMatchIn(c.substringBefore("@Test"))) return false
        return Regex("(@Ignore\\s+)?@Test").findAll(c).any { it.groupValues[1].isEmpty() } &&
            Regex("@Test\\s+@Ignore").findAll(c).count() < Regex("@Test").findAll(c).count()
    }

    /** Every engine class name declared in engine/src/main. */
    private val engineNames: Set<String> by lazy {
        File("src/main/java").walk().filter { it.extension == "kt" }.flatMap { f ->
            Regex("""^\s*(?:(?:internal|private|data|sealed|enum|fun)\s+)*(?:object|class|interface)\s+([A-Z]\w*)""", RegexOption.MULTILINE).findAll(f.readText()).map { it.groupValues[1] }
        }.toSet()
    }

    /**
     * The engine code a lab reaches: the engine classes its sources name, and the ones those classes name in turn (calc,
     * guard and cohazard go through LabTools to CalcShortcut, VerdictGuard and CoHazard).
     */
    private fun engineReach(id: String): Set<String> {
        val src = File(plugins, id).walk().filter { it.extension == "kt" }.joinToString("\n") { code(it.readText()) }
        val direct = Regex("""team\.sopa\.boar\.engine\.[a-z.]+\.([A-Z]\w*)""").findAll(src).map { it.groupValues[1] }.toSet()
        val next = direct.flatMap { name ->
            File("src/main/java").walk().filter { it.name == "$name.kt" }.flatMap { f -> Regex("""\b([A-Z]\w*)\b""").findAll(code(f.readText())).map { it.groupValues[1] } }
        }.filter { it in engineNames }.toSet()
        return direct + next
    }

    @Test fun `every listed lab has tests, and each named test exists and runs`() {
        for ((id, tests) in manifest) {
            assertTrue("$id lists no tests", tests.isNotEmpty())
            for (name in tests) {
                val f = testFiles[name]
                assertTrue("$id: $name not found under engine or app tests", f != null)
                assertTrue("$id: $name has no @Test that runs (outside comments, not @Ignore'd)", runsTests(f!!.readText()))
            }
        }
    }

    @Test fun `each named test exercises engine code its lab reaches`() {
        for ((id, tests) in manifest) {
            val reach = engineReach(id)
            assertTrue("$id reaches no engine code", reach.isNotEmpty())
            for (name in tests) {
                val used = Regex("""\b([A-Z]\w*)\b""").findAll(code(testFiles.getValue(name).readText())).map { it.groupValues[1] }.toSet()
                assertTrue("$id: $name tests none of $reach", (used intersect reach).isNotEmpty())
            }
        }
    }

    @Test fun `the test checks catch a comment, an ignored test and another lab's test`() {
        assertTrue(runsTests("class A {\n    @Test fun a() {}\n}"))
        assertTrue(!runsTests("class A {\n    // @Test fun a() {}\n}"))
        assertTrue(!runsTests("class A {\n    /* @Test */ fun a() {}\n}"))
        assertTrue(!runsTests("class A {\n    @Ignore @Test fun a() {}\n}"))
        assertTrue(!runsTests("class A {\n    @Test @Ignore fun a() {}\n}"))
        assertTrue(!runsTests("@Ignore\nclass A {\n    @Test fun a() {}\n}"))
        assertTrue(runsTests("class A {\n    @Ignore @Test fun a() {}\n    @Test fun b() {}\n}"))
        // Lunar's reach doesn't include Vitals: naming VitalsTest for it would fail.
        assertTrue("Vitals" !in engineReach("lunar"))
        assertTrue("MoonPhase" in engineReach("lunar"))
    }

    @Test fun `Big Boar and Boar Wisp stay out of releases (adam, Nov 10)`() {
        assertTrue("bmoe" !in manifest)
        assertTrue("colibri" !in manifest)
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
