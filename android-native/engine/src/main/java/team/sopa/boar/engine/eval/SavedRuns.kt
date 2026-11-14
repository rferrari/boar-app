package team.sopa.boar.engine.eval

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import team.sopa.boar.engine.eval.BenchPresets.num
import team.sopa.boar.engine.eval.BenchPresets.str
import java.io.File

/**
 * What a saved run says about itself, next to its rows as <runId>.meta.json (adam, Nov 14: "i miss a saved benchmark
 * results, like, i run, closed, and now, cant review? where it is saved and i could load it from evaluations").
 */
@Serializable
data class RunMeta(
    val runId: String,
    val createdAt: Long,
    val appVersion: String? = null,
    val models: List<String> = emptyList(),
    /** null: a run chosen by hand (models ticked) or one from before presets. */
    val level: String? = null,
    val topics: List<String> = emptyList(),
    val topicVersions: Map<String, Int> = emptyMap(),
    /** The passes: "succinct", "detailed", "deep". */
    val passes: List<String> = emptyList(),
    /** "app" (the Evaluations page) or "adb" (BenchActivity). */
    val source: String = "app",
    val requestId: String? = null,
    /** running, done, stopped or failed. */
    val state: String = STATE_RUNNING,
    val rows: Int = 0,
    val finishedAt: Long? = null,
    val error: String? = null,
) {
    companion object {
        const val STATE_RUNNING = "running"
        const val STATE_DONE = "done"
        const val STATE_STOPPED = "stopped"
        const val STATE_FAILED = "failed"
        /** A run whose app closed before it ended: "running" on disk, no runner going. */
        const val STATE_INTERRUPTED = "interrupted"
    }
}

/**
 * Every evaluation run kept on the phone, in the app's own files/eval (nothing leaves it unless shared): the page's
 * list, opening one, deleting one. Runs from the page and from adb are in the same folder, so both are listed. A run
 * from before this change has no meta: what its rows carry stands in (date from its id, build, models).
 */
class SavedRunStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun rowsFile(runId: String) = File(dir, "$runId.jsonl")
    fun metaFile(runId: String) = File(dir, "$runId.meta.json")
    private fun progressFile(runId: String) = File(dir, "$runId.run.json")
    /** The scorecard as text, written at the end of a run for the adb script. */
    fun scorecardFile(runId: String) = File(dir, "$runId.scorecard.txt")

    fun writeMeta(meta: RunMeta) {
        dir.mkdirs()
        val f = metaFile(meta.runId)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(json.encodeToString(RunMeta.serializer(), meta))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun readMeta(runId: String): RunMeta? = metaFile(runId).takeIf { it.isFile }?.let { f ->
        runCatching { json.decodeFromString(RunMeta.serializer(), f.readText()) }.getOrNull()
    }

    fun rows(runId: String): List<JsonObject> {
        val f = rowsFile(runId).takeIf { it.isFile } ?: return emptyList()
        return f.readLines().filter { it.isNotBlank() }.mapNotNull { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    }

    /**
     * Newest first. [active]: the run going now, which stays "running"; any other run still "running" on disk was cut off
     * by the app closing and is listed as interrupted.
     */
    fun list(active: String? = null): List<RunMeta> {
        val ids = dir.listFiles { f -> f.name.endsWith(".jsonl") }.orEmpty().map { it.name.removeSuffix(".jsonl") }
        return ids.map { id ->
            val meta = readMeta(id) ?: fromRows(id, rows(id), progressFile(id).exists())
            if (meta.state == RunMeta.STATE_RUNNING && id != active) meta.copy(state = RunMeta.STATE_INTERRUPTED) else meta
        }.sortedWith(compareByDescending<RunMeta> { it.createdAt }.thenByDescending { it.runId })
    }

    /** The last finished run before [run] with its level and topics, to compare with; null for a run by hand. */
    fun previous(run: RunMeta, all: List<RunMeta> = list()): RunMeta? =
        if (run.level == null) null
        else all.firstOrNull { it.runId != run.runId && it.level == run.level && it.topics == run.topics &&
            it.createdAt < run.createdAt && it.state in setOf(RunMeta.STATE_DONE, RunMeta.STATE_STOPPED) }

    /** The last finished run of [level], any topics: what the expected time is read from. */
    fun lastOfLevel(level: String, all: List<RunMeta> = list()): RunMeta? =
        all.firstOrNull { it.level == level && it.state == RunMeta.STATE_DONE }

    /** Deletes the run's rows, meta and progress; true when its rows are gone. */
    fun delete(runId: String): Boolean {
        listOf(metaFile(runId), progressFile(runId), scorecardFile(runId), File(dir, "$runId.jsonl.tmp")).forEach { it.delete() }
        val rows = rowsFile(runId)
        return !rows.exists() || rows.delete()
    }

    companion object {
        private val RUN_ID = Regex("""^eval-(\d{4})-(\d{2})-(\d{2})T(\d{2})-(\d{2})-(\d{2})-(\d{3})Z$""")

        /** "eval-2026-11-14T05-21-00-000Z" -> its time; null for another name. */
        fun timeOf(runId: String): Long? = RUN_ID.matchEntire(runId)?.groupValues?.let { g ->
            runCatching { java.time.Instant.parse("${g[1]}-${g[2]}-${g[3]}T${g[4]}:${g[5]}:${g[6]}.${g[7]}Z").toEpochMilli() }.getOrNull()
        }

        /** An old run's meta, from what its rows carry. */
        fun fromRows(runId: String, rows: List<JsonObject>, unfinished: Boolean = false): RunMeta {
            val created = timeOf(runId) ?: rows.firstNotNullOfOrNull { it.num("createdAt")?.toLong() } ?: 0L
            val state = when {
                unfinished -> RunMeta.STATE_RUNNING
                rows.any { it.str("outcome") == "cancelled" } -> RunMeta.STATE_STOPPED
                else -> RunMeta.STATE_DONE
            }
            return RunMeta(runId, created, rows.firstNotNullOfOrNull { it.str("appVersion") },
                rows.mapNotNull { it.str("modelId") }.distinct(), rows.firstNotNullOfOrNull { it.str("level") },
                rows.mapNotNull { it.str("topic") }.distinct(), passes = rows.map(BenchPresets::passKey).distinct(),
                source = "unknown", state = state, rows = rows.size, finishedAt = rows.mapNotNull { it.num("createdAt")?.toLong() }.maxOrNull())
        }
    }
}

/** Export as JSONL or CSV (exportEvalResults), for a run just made or one opened from the list. */
object EvalExport {
    /** evalRowsToCsv: the same columns, arrays joined with "|"; the bench columns at the end. */
    val CSV_COLUMNS = listOf("runId", "evalSetVersion", "configId", "configLabel", "routingPreset", "personalityId", "maxTokens",
        "queryId", "category", "query", "modelId", "promptFormat", "taskType", "adaptiveRoutingUsed", "retrievalUsed",
        "retrievedTitles", "expectedKbTitles", "expectedKbHit", "reasonCodes", "modelSwitches", "crossMessageModelSwitch",
        "modelResidency", "modelLoadMs", "ttftMs", "retrievalMs", "contextTokensBefore", "contextTokensAfter",
        "generationLatencyMs", "promptTokens", "promptMs", "cachedTokens", "predictedTokens", "predictedMs", "totalLatencyMs",
        "tokensGenerated", "tokPerSec", "peakRssBytes", "outcome", "errorMessage", "timedOut", "answer", "createdAt", "appVersion", "tool",
        "retrievalMode", "style", "notFound", "level", "topic", "path")

    fun jsonl(rows: List<JsonObject>): String = rows.joinToString("\n") { it.toString() }

    fun csv(rows: List<JsonObject>): String =
        (listOf(CSV_COLUMNS.joinToString(",")) + rows.map { r -> CSV_COLUMNS.joinToString(",") { csvCell(r[it]) } }).joinToString("\n")

    private fun csvCell(v: JsonElement?): String {
        val s = when (v) {
            null, JsonNull -> return ""
            is JsonArray -> v.joinToString("|") { cellText(it) }
            else -> cellText(v)
        }
        return if (s.contains(Regex("[\",\n]"))) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** String(value) for a JSON value: whole numbers without ".0", as JavaScript prints them. */
    private fun cellText(v: JsonElement): String {
        val p = v as? JsonPrimitive ?: return v.toString()
        if (p.isString) return p.content
        val d = p.content.toDoubleOrNull()
        return if (d != null && d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e21 && p.content.contains('.')) d.toLong().toString() else p.content
    }
}
