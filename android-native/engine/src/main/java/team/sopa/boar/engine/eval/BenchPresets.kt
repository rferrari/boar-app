package team.sopa.boar.engine.eval

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Bench presets: a level and topics (adam, Nov 14: "we should have a light benchmark and a long benchmark. this waiting
 * sucks"; "lets create some categories, like fast, only succinct mode. medium, complete. and by topics, like fast
 * plugins, fast problematics"). A preset is named "<level>" or "<level>-<topic>": "fast", "fast-plugins",
 * "medium-research". Without a topic it runs the fixed 17 ("keep those as we have since start"). Pure.
 */
enum class BenchLevel(val id: String) {
    /** Succinct on the 1.7B (or the only answer model installed): about 9 s a question on the F3. */
    FAST("fast"),
    /** Succinct on both models: about 36 s a question. */
    MEDIUM("medium"),
    /** Succinct and Detailed on both models, and Deep Research for a topic that asks for it: about 3.9 min a question. */
    COMPLETE("complete");

    companion object {
        fun of(id: String?): BenchLevel? = entries.firstOrNull { it.id == id }
    }
}

/** One way every question is answered in a run: a style, through Deep Research or not. */
data class BenchPass(val style: String, val deep: Boolean = false) {
    /** "succinct", "detailed", "deep". */
    val key get() = if (deep) PATH_DEEP else style

    companion object {
        const val PATH_DEEP = "deep"
    }
}

data class BenchPreset(val level: BenchLevel, val topics: List<String>) {
    val name get() = (listOf(level.id) + topics.filter { it != BenchPresets.CORE }).joinToString("-")
}

/** What a preset runs: its models, its passes, its questions (each with its topic), and the labs it switches on. */
data class BenchPlan(
    val preset: BenchPreset,
    val models: List<ModelRef>,
    val passes: List<BenchPass>,
    val queries: List<EvalQuery>,
    /** queryId -> topic id. */
    val topicOf: Map<String, String>,
    /** The questions each pass runs ([BenchPass.key] -> ids); a Deep Research pass runs only its topic's. */
    val passQueries: Map<String, List<String>>,
    val labs: List<String>?,
) {
    /** Answers the run will write. */
    val answers get() = models.size * passes.sumOf { passQueries[it.key].orEmpty().size }
}

object BenchPresets {
    /** The fixed 17, as a topic: a level alone runs them. */
    const val CORE = "core"
    const val SMALL_MODEL = "qwen3-1.7b-q4km"
    const val LARGE_MODEL = "qwen3-4b-instruct-2507-q4km"

    /**
     * Seconds a question on the F3 when no run of the level is on the phone yet: the Cards runs' medians (t9, Nov 14,
     * 14 questions): Succinct 8.9 s on the 1.7B and 26.8 s on the 4B; Detailed 46.3 s and 150.7 s.
     */
    val SEED_SECONDS = mapOf(
        (SMALL_MODEL to "succinct") to 8.9, (LARGE_MODEL to "succinct") to 26.8,
        (SMALL_MODEL to "detailed") to 46.3, (LARGE_MODEL to "detailed") to 150.7,
    )

    /** "fast" or "fast-plugins" (a topic id may hold dashes); null for anything else. */
    fun parse(name: String): BenchPreset? {
        val level = BenchLevel.entries.firstOrNull { name == it.id || name.startsWith(it.id + "-") } ?: return null
        val topic = name.removePrefix(level.id).removePrefix("-")
        return BenchPreset(level, if (topic.isEmpty()) listOf(CORE) else topic.split('+'))
    }

    /**
     * The level's models among [installed] (catalog order): fast takes the 1.7B, else the only one, else the first;
     * medium and complete take the 1.7B and the 4B, filled up to two from the others when one is missing.
     */
    fun models(level: BenchLevel, installed: List<ModelRef>): List<ModelRef> {
        val small = installed.firstOrNull { it.id == SMALL_MODEL }
        val large = installed.firstOrNull { it.id == LARGE_MODEL }
        if (level == BenchLevel.FAST) return listOfNotNull(small ?: installed.firstOrNull())
        val both = listOfNotNull(small, large)
        return (both + installed.filter { it !in both }).take(2)
    }

    fun passes(level: BenchLevel, deepTopic: Boolean): List<BenchPass> = when (level) {
        BenchLevel.FAST, BenchLevel.MEDIUM -> listOf(BenchPass("succinct"))
        BenchLevel.COMPLETE -> listOf(BenchPass("succinct"), BenchPass("detailed")) + if (deepTopic) listOf(BenchPass("detailed", deep = true)) else emptyList()
    }

    sealed interface Planned {
        data class Ok(val plan: BenchPlan) : Planned
        data class Err(val error: String) : Planned
    }

    /**
     * The plan for [preset]. [topics]: every topic file the app has, by id. [models]: the request's own models, over the
     * level's. [labs]: the request's labs, over the topics'.
     */
    fun plan(preset: BenchPreset, installed: List<ModelRef>, topics: Map<String, BenchTopic>, sets: EvalSets,
             models: List<ModelRef>? = null, labs: List<String>? = null): Planned {
        val chosen = preset.topics.map { id -> topics[id] ?: return Planned.Err("unknown topic \"$id\" (${topics.keys.sorted().joinToString(", ")})") }
        val picked = models ?: models(preset.level, installed)
        if (picked.isEmpty()) return Planned.Err("no answer model installed")
        val topicOf = LinkedHashMap<String, String>()
        val queries = ArrayList<EvalQuery>()
        for (t in chosen) for (q in runCatching { t.queries(sets) }.getOrElse { return Planned.Err(it.message ?: "bad topic ${t.id}") }) {
            if (topicOf.putIfAbsent(q.id, t.id) == null) queries += q
        }
        val deepIds = queries.filter { q -> chosen.first { it.id == topicOf[q.id] }.deepResearch }.map { it.id }
        val passes = passes(preset.level, deepIds.isNotEmpty())
        val passQueries = passes.associate { p -> p.key to if (p.deep) deepIds else queries.map { it.id } }
        val topicLabs = chosen.mapNotNull { it.labs }.takeIf { it.isNotEmpty() }?.flatten()?.distinct()
        return Planned.Ok(BenchPlan(preset, picked, passes, queries, topicOf, passQueries, labs ?: topicLabs))
    }

    /** A run's expected length: [seconds] known, plus [untimed] Deep Research answers nobody has timed yet. */
    data class Estimate(val seconds: Double, val untimed: Int, val fromPrevious: Boolean)

    /**
     * Before a run starts (adam, Nov 14: "this waiting sucks"): per model and pass, the median whole answer of the last
     * run of the same level when it has rows for them, else [SEED_SECONDS] (a model not there counts as the 4B).
     */
    fun estimate(plan: BenchPlan, previousRows: List<JsonObject>? = null): Estimate {
        var seconds = 0.0
        var untimed = 0
        var fromPrevious = false
        for (m in plan.models) for (p in plan.passes) {
            val n = plan.passQueries[p.key].orEmpty().size
            val measured = previousRows?.filter { r ->
                r.str("modelId") == m.id && passKey(r) == p.key && r.str("outcome") == "success"
            }?.mapNotNull { it.num("totalLatencyMs") }?.let { EvalScore.median(it) }
            when {
                measured != null -> { seconds += n * measured / 1000; fromPrevious = true }
                p.deep -> untimed += n
                else -> seconds += n * (SEED_SECONDS[m.id to p.style] ?: SEED_SECONDS[LARGE_MODEL to p.style] ?: SEED_SECONDS[LARGE_MODEL to "detailed"]!!)
            }
        }
        return Estimate(seconds, untimed, fromPrevious)
    }

    /** A row's pass: "deep" for Deep Research, else its style ("succinct"; the default style when it says none). */
    fun passKey(row: JsonObject): String = if (row.str("path") == BenchPass.PATH_DEEP) BenchPass.PATH_DEEP else row.str("style") ?: "detailed"

    internal fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    internal fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
}
