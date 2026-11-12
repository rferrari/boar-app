package team.sopa.boar.engine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** About as sections and rows (adam approved the mock-up, Nov 12; t9 1840). Read from the source. */
class AboutLayoutTest {
    private val about = java.io.File("../app/src/main/java/team/sopa/boar/ui/about/AboutScreen.kt").readText()
    private val debug = java.io.File("../app/src/main/java/team/sopa/boar/ui/about/AboutDebug.kt").readText()
    private fun res(d: String) = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
    private val screen = about.substringAfter("fun AboutScreen(").substringBefore("private fun Top(")

    @Test fun `the header, then the mark at 72 dp, the tagline and the version block`() {
        assertTrue(screen.contains("team.sopa.boar.ui.PageHeader(t(\"aboutScreen.title\"), onClose)"))
        assertTrue(screen.indexOf("Top(info, debug)") in 0 until screen.indexOf("AboutSection("))
        val top = about.substringAfter("private fun Top(").substringBefore("private fun Hairline(")
        assertTrue(top.contains("painterResource(R.drawable.boar_mark), null, Modifier.size(72.dp)"))
        assertTrue(top.indexOf("native.about.tagline") < top.indexOf("Hairline()"))
        // The thread is gone: no banner, no avatar column, no repeated name.
        for (gone in listOf("boar_banner", "private fun Post(", "private fun Profile(", "\"@boar_app\"", "Color(0xFF", "bgTerminal", "android.widget.Toast"))
            assertFalse(gone, about.contains(gone))
    }

    @Test fun `the sections in order`() {
        val order = listOf("native.about.section.what", "native.about.section.privacy", "native.about.section.measure", "native.about.section.links",
            "native.about.section.plugins", "native.about.packsTitle", "native.about.teamTitle", "native.about.section.credits", "native.about.holdToCopy")
        val at = order.map { screen.indexOf(it) }
        assertTrue(at.toString(), at.all { it >= 0 } && at == at.sorted())
    }

    @Test fun `addresses are copy rows, tap and press and hold both copy, nothing opens`() {
        for (a in listOf("\"boarapp.com\"", "\"github.com/rferrari/boar-app\"", "\"x.com/boar_app\"", "\"farcaster.xyz/boarapp\"", "\"huggingface.co/datasets/r4topunk/boar-packs\""))
            assertTrue(a, Regex("CopyRow\\([^\\n]*$a").containsMatchIn(screen))
        val row = about.substringAfter("private fun CopyRow(").substringBefore("private fun TeamRow(")
        assertTrue(row.contains("onClick = { copy(ctx, copyText, copied) }, onLongClick = { copy(ctx, copyText, copied) }"))
        assertTrue(row.contains("contentDescription = desc") && row.contains("native.about.copyAddress"))
        for (opens in listOf("Intent.ACTION_VIEW", "LocalUriHandler", "openUri", "startActivity")) assertFalse(opens, about.contains(opens))
        val copy = about.substringAfter("private fun copy(").substringBefore("\n}")
        assertTrue(copy.contains("setPrimaryClip") && copy.contains("team.sopa.boar.ui.Toasts.show(copied)"))
    }

    @Test fun `the core team keeps its six handles, each a copy row`() {
        assertTrue(about.contains("private val TEAM = listOf(\"Bielcx\" to \"bielthegoat\", \"Jasper\" to \"btcjasper\", \"r4to\" to \"r4topunk\", \"Sopa\" to \"sopaagency\",\n    \"vaipraonde\" to \"arferrari\", \"vlad\" to \"sk8ordao\")"))
        val team = about.substringAfter("private fun TeamRow(").substringBefore("private fun PluginsRow(")
        assertTrue(team.contains("val address = \"x.com/\$handle\"") && team.contains("onLongClick = { copy(ctx, address, copied) }"))
    }

    @Test fun `the version block copies the bug report, and plugins have their own section`() {
        val top = about.substringAfter("private fun Top(").substringBefore("private fun Hairline(")
        assertTrue(top.contains("onLongClick = { copy(ctx, report, copied) }") && top.contains("LabHooks.enabledLabs()"))
        assertTrue(debug.contains("Plugins (") && debug.contains("Device: "))
        val plugins = about.substringAfter("private fun PluginsRow(").substringBefore("\n}")
        assertTrue(plugins.contains("native.about.noPlugins") && plugins.contains("native.about.plugins\", \"count\" to labs.size"))
    }

    @Test fun `claims and strings, English and Portuguese`() {
        val keys = listOf("section_what", "section_privacy", "section_measure", "section_links", "section_plugins", "section_credits",
            "privacy_model", "privacy_modelNote", "privacy_offline", "privacy_offlineNote", "privacy_connection", "privacy_connectionNote",
            "measure_receipt", "measure_receiptNote", "measure_eval", "measure_evalNote", "measure_budget", "measure_budgetNote",
            "sourceTitle", "sourceNote", "versionHold", "copyAddress", "siteTitle", "packsTitle", "packsBody", "teamTitle")
        for (d in listOf("values", "values-pt")) {
            val s = res(d)
            for (k in keys) assertTrue("$d $k", s.contains("name=\"native_about_$k\""))
            for (gone in listOf("post_why", "post_offline", "post_receipt", "post_budget", "post_open", "post_packs", "post_team", "teamBody", "siteBody", "airGappedBody"))
                assertFalse("$d $gone", s.contains("name=\"native_about_$gone\""))
            assertFalse(d, s.contains("native_aboutScreen_benchmarkBody") || s.contains("[CHECK]"))
            assertEquals(d, 1, Regex("name=\"native_about_privacy_connectionNote\"[^>]*>[^<]*(evaluation results|resultados de avaliação)").findAll(s).count())
        }
        assertTrue(res("values").contains("No server receives your questions") && res("values").contains("Settings, Advanced tweaks, Evaluation"))
        val pt = java.io.File("../app/src/main/res/values-pt/strings.xml").readText()
        assertFalse(pt.contains("Build de release"))
    }
}
