package team.sopa.boar.engine.settings

import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.1.0 (BOAR-81): x.x.x names (adam, 25 Nov); versionCode next after 2.0.0's 8, so it installs over 2.0.0. */
class Version210Test {
    @Test fun `the build is 2_1_0, versionCode 9`() {
        val gradle = java.io.File("../app/build.gradle.kts").readText()
        assertTrue(gradle.contains("        versionCode = 9\n"))
        assertTrue(gradle.contains("?.takeIf { it.isNotBlank() } ?: \"2.1.0\""))
        val agents = java.io.File("../../AGENTS.md").readText()
        assertTrue(agents.contains("Without `-Pboar.versionName` the version is `2.1.0`") && agents.contains("2.1.0 has 9"))
    }
}
