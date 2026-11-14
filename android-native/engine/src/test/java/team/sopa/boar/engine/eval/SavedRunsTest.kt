package team.sopa.boar.engine.eval

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** t9 0521 items 5 and 8: the saved list (open, delete, kept across a restart and an update), and an old run's export. */
class SavedRunsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun row(s: String) = Json.parseToJsonElement(s) as JsonObject

    private fun save(dir: File, meta: RunMeta, rows: List<String>) {
        File(dir, "${meta.runId}.jsonl").writeText(rows.joinToString("") { it + "\n" })
        SavedRunStore(dir).writeMeta(meta)
    }

    private val oldRun = "eval-2026-11-01T10-00-00-000Z"
    private val oldRows = listOf(
        """{"runId":"$oldRun","queryId":"factual-1","modelId":"qwen3-4b-instruct-2507-q4km","outcome":"success","answer":"The capital of Australia is Canberra, \"the capital\"","appVersion":"2.0.0-rc2","createdAt":1761991300000,"retrievedTitles":["Canberra","Australia"]}""",
        """{"runId":"$oldRun","queryId":"factual-2","modelId":"qwen3-4b-instruct-2507-q4km","outcome":"success","answer":"Charles Darwin proposed it, and Wallace did too.","appVersion":"2.0.0-rc2","createdAt":1761991400000}""",
    )

    @Test fun `runs are listed newest first, old ones from what their rows carry`() {
        val dir = tmp.newFolder("eval")
        File(dir, "$oldRun.jsonl").writeText(oldRows.joinToString("\n"))
        save(dir, RunMeta("eval-2026-11-14T05-00-00-000Z", SavedRunStore.timeOf("eval-2026-11-14T05-00-00-000Z")!!, "2.0.0-rc3", listOf("qwen3-1.7b-q4km"), "fast", listOf("plugins"),
            source = "adb", state = RunMeta.STATE_DONE, rows = 1), listOf("""{"queryId":"calc-conversion","outcome":"success"}"""))
        val list = SavedRunStore(dir).list()
        assertEquals(listOf("eval-2026-11-14T05-00-00-000Z", oldRun), list.map { it.runId })
        val old = list[1]
        assertEquals(SavedRunStore.timeOf(oldRun), old.createdAt)
        assertEquals("2.0.0-rc2", old.appVersion)
        assertEquals(listOf("qwen3-4b-instruct-2507-q4km"), old.models)
        assertNull(old.level)
        assertEquals(2, old.rows)
        assertEquals(RunMeta.STATE_DONE, old.state)
        assertEquals("adb", list[0].source)
    }

    @Test fun `kept across an app restart and an update with data kept - the files are the list`() {
        val dir = tmp.newFolder("eval")
        save(dir, RunMeta("eval-2026-11-14T05-00-00-000Z", 1, "2.0.0-rc3", level = "medium", state = RunMeta.STATE_DONE, rows = 1), listOf("""{"queryId":"a"}"""))
        // A new store on the same folder is the app after a restart, or after an update that kept its data.
        val again = SavedRunStore(File(dir.path))
        assertEquals(listOf("eval-2026-11-14T05-00-00-000Z"), again.list().map { it.runId })
        assertEquals("medium", again.readMeta("eval-2026-11-14T05-00-00-000Z")?.level)
        assertEquals(1, again.rows("eval-2026-11-14T05-00-00-000Z").size)
    }

    @Test fun `opening gives the rows, deleting removes the run and only it`() {
        val dir = tmp.newFolder("eval")
        File(dir, "$oldRun.jsonl").writeText(oldRows.joinToString("\n"))
        save(dir, RunMeta("eval-2026-11-14T05-00-00-000Z", 2, state = RunMeta.STATE_DONE), listOf("""{"queryId":"a"}"""))
        File(dir, "eval-2026-11-14T05-00-00-000Z.scorecard.txt").writeText("x")
        val store = SavedRunStore(dir)
        assertEquals(listOf("factual-1", "factual-2"), store.rows(oldRun).map { (it["queryId"] as kotlinx.serialization.json.JsonPrimitive).content })
        assertTrue(store.delete("eval-2026-11-14T05-00-00-000Z"))
        assertEquals(listOf(oldRun), store.list().map { it.runId })
        assertFalse(File(dir, "eval-2026-11-14T05-00-00-000Z.meta.json").exists())
        assertFalse(File(dir, "eval-2026-11-14T05-00-00-000Z.scorecard.txt").exists())
    }

    @Test fun `a run the app closed on is interrupted, unless it is the one going`() {
        val dir = tmp.newFolder("eval")
        save(dir, RunMeta("eval-2026-11-14T05-00-00-000Z", 2), listOf("""{"queryId":"a"}"""))
        assertEquals(RunMeta.STATE_INTERRUPTED, SavedRunStore(dir).list().single().state)
        assertEquals(RunMeta.STATE_RUNNING, SavedRunStore(dir).list(active = "eval-2026-11-14T05-00-00-000Z").single().state)
    }

    @Test fun `the previous run is the last finished one of the same level and topics`() {
        val dir = tmp.newFolder("eval")
        save(dir, RunMeta("eval-2026-11-10T00-00-00-000Z", 10, level = "fast", topics = listOf("plugins"), state = RunMeta.STATE_DONE), listOf("{}"))
        save(dir, RunMeta("eval-2026-11-11T00-00-00-000Z", 20, level = "fast", topics = listOf("cards"), state = RunMeta.STATE_DONE), listOf("{}"))
        save(dir, RunMeta("eval-2026-11-12T00-00-00-000Z", 30, level = "fast", topics = listOf("plugins"), state = RunMeta.STATE_FAILED), listOf("{}"))
        val now = RunMeta("eval-2026-11-13T00-00-00-000Z", 40, level = "fast", topics = listOf("plugins"), state = RunMeta.STATE_DONE)
        save(dir, now, listOf("{}"))
        val store = SavedRunStore(dir)
        assertEquals("eval-2026-11-10T00-00-00-000Z", store.previous(now)?.runId)
        assertNull(store.previous(now.copy(level = null)))
        assertEquals("eval-2026-11-13T00-00-00-000Z", store.lastOfLevel("fast")?.runId)
    }

    @Test fun `an old run exports as JSONL and CSV`() {
        val dir = tmp.newFolder("eval")
        File(dir, "$oldRun.jsonl").writeText(oldRows.joinToString("\n"))
        val rows = SavedRunStore(dir).rows(oldRun)
        assertEquals(oldRows.map { row(it) }, EvalExport.jsonl(rows).lines().map { row(it) })
        val csv = EvalExport.csv(rows).lines()
        assertEquals(EvalExport.CSV_COLUMNS.joinToString(","), csv[0])
        assertEquals(3, csv.size)
        assertTrue(csv[1], csv[1].contains("\"The capital of Australia is Canberra, \"\"the capital\"\"\""))
        assertTrue(csv[1], csv[1].contains("Canberra|Australia"))
        assertTrue(csv[1], csv[1].contains("1761991300000"))
    }

    @Test fun `scorecard of an old run opened from the list`() {
        val dir = tmp.newFolder("eval")
        File(dir, "$oldRun.jsonl").writeText(oldRows.joinToString("\n"))
        val card = Scorecard.of(SavedRunStore(dir).rows(oldRun), BenchPresetsTest.topics)
        val core = card.topics.single()
        assertEquals("core", core.topic)
        // Old rows have no topic: their ids place them in core, whose rules read their text (Canberra, Darwin, English).
        assertEquals(2, core.checked)
        assertEquals(2, core.passed)
    }
}
