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

    /** Source without comments or string literals, so a word in one doesn't count. */
    private fun code(source: String) = source
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("\"\"\".*?\"\"\"", RegexOption.DOT_MATCHES_ALL), "\"\"")
        .replace(Regex("\"(?:\\\\.|[^\"\\\\\n])*\""), "\"\"")
        .replace(Regex("//[^\n]*"), "")

    private val ignore = "@(?:org\\.junit\\.)?Ignore(?:\\([^)]*\\))?"

    /**
     * A test file that runs: a class not @Ignore'd, at least one @Test (outside comments and strings) with no @Ignore
     * before or after it, and an assertion somewhere, so an empty test doesn't count.
     */
    private fun runsTests(source: String): Boolean {
        val c = code(source)
        if (Regex("$ignore\\s+(?:\\w+\\s+)*class\\s").containsMatchIn(c)) return false
        val active = Regex("((?:$ignore\\s+)*)@(?:org\\.junit\\.)?Test\\b((?:\\s+$ignore)*)").findAll(c)
            .any { it.groupValues[1].isBlank() && it.groupValues[2].isBlank() }
        return active && Regex("\\bassert\\w*\\(|\\bfail\\(").containsMatchIn(c)
    }

    /** Every engine class name declared in engine/src/main. */
    private val engineNames: Set<String> by lazy {
        File("src/main/java").walk().filter { it.extension == "kt" }.flatMap { f ->
            Regex("""^\s*(?:(?:internal|private|data|sealed|enum|fun)\s+)*(?:object|class|interface)\s+([A-Z]\w*)""", RegexOption.MULTILINE).findAll(f.readText()).map { it.groupValues[1] }
        }.toSet()
    }

    /** Shared plumbing every part of the engine uses: naming one says nothing about which lab a test covers. */
    private val generic = setOf("QueryContext", "SessionEngine", "ChatMessage", "Chunk", "TextModel", "Sampling", "GenerationEvent",
        "GenerationMetrics", "AnswerText", "AnswerPipeline", "RagPure", "ConversationHistory", "ConversationTurn", "PromptMessage",
        "Js", "JsRegex", "Compress", "FollowUp", "StageGenerator", "GenRequest", "GenResult", "AssetKind", "AssetStore", "CatalogAsset")

    /** Engine classes that hand each lab its own code: a lab using one reaches the classes it calls (LabTools → CalcShortcut…). */
    private val hubs = setOf("LabTools")

    /**
     * The engine code a lab reaches: the engine classes its sources name, shared plumbing left out, and through a hub the
     * classes that hub calls (each with its own file in the engine). calc, cohazard and guard share LabTools, so they
     * may name each other's tests (one engine area, accepted by the validator); nothing wider.
     */
    private fun engineReach(id: String): Set<String> {
        val src = File(plugins, id).walk().filter { it.extension == "kt" }.joinToString("\n") { code(it.readText()) }
        val direct = Regex("""team\.sopa\.boar\.engine\.[a-z.]+\.([A-Z]\w*)""").findAll(src).map { it.groupValues[1] }.toSet() - generic
        val ownFile = { n: String -> File("src/main/java").walk().any { it.name == "$n.kt" } }
        val viaHubs = direct.filter { it in hubs }.flatMap { hub ->
            File("src/main/java").walk().filter { it.name == "$hub.kt" }.flatMap { f -> Regex("""\b([A-Z]\w*)\b""").findAll(code(f.readText())).map { it.groupValues[1] } }
        }.filter { it in engineNames && ownFile(it) }.toSet() - generic
        return direct + viaHubs
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

    @Test fun `the test checks catch ignored, commented, quoted and empty tests`() {
        val ok = "class A {\n    @Test fun a() { assertTrue(true) }\n}"
        assertTrue(runsTests(ok))
        for (bad in listOf(
            "class A {\n    @Ignore(\"why\") @Test fun a() { assertTrue(true) }\n}",
            "class A {\n    @org.junit.Ignore @Test fun a() { assertTrue(true) }\n}",
            "class A {\n    @Test @org.junit.Ignore(\"r\") fun a() { assertTrue(true) }\n}",
            "class A {\n    @Ignore @Test fun a() { assertTrue(true) }\n    @Test @Ignore(\"x\") fun b() { assertTrue(true) }\n}",
            "class A {\n    val s = \"@Test\"\n    fun a() { assertTrue(true) }\n}",
            "class A {\n    @Test fun a() {}\n}",
            "@org.junit.Ignore(\"later\")\nclass A {\n    @Test fun a() { assertTrue(true) }\n}",
        )) assertTrue(bad, !runsTests(bad))
    }

    @Test fun `a lab can't name a test of another engine area`() {
        val tests = { n: String -> Regex("""\b([A-Z]\w*)\b""").findAll(code(testFiles.getValue(n).readText())).map { it.groupValues[1] }.toSet() }
        for ((lab, other) in listOf("lunar" to "FactsTest", "staged" to "LabToolsCombinationTest", "canvas" to "LabToolsCompoundTest",
                "pulse" to "MoonPhaseTest", "compass" to "VitalsTest", "printer" to "CanvasTest"))
            assertTrue("$lab could name $other", (tests(other) intersect engineReach(lab)).isEmpty())
    }

    @Test fun `the test checks catch a comment, an ignored test and another lab's test`() {
        assertTrue(runsTests("class A {\n    @Test fun a() { assertTrue(true) }\n}"))
        assertTrue(!runsTests("class A {\n    // @Test fun a() { assertTrue(true) }\n}"))
        assertTrue(!runsTests("class A {\n    /* @Test */ fun a() { assertTrue(true) }\n}"))
        assertTrue(!runsTests("class A {\n    @Ignore @Test fun a() { assertTrue(true) }\n}"))
        assertTrue(!runsTests("class A {\n    @Test @Ignore fun a() { assertTrue(true) }\n}"))
        assertTrue(!runsTests("@Ignore\nclass A {\n    @Test fun a() { assertTrue(true) }\n}"))
        assertTrue(runsTests("class A {\n    @Ignore @Test fun a() { assertTrue(true) }\n    @Test fun b() { assertTrue(true) }\n}"))
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
