package team.sopa.boar.eval

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.rag.Retriever
import java.io.File

/**
 * The phone's half of the retrieval parity check (scripts/retrieval-parity.mjs). The questions it sends (a JSON array,
 * base64 in the intent: a folder adb creates under Android/data is the shell's, which the app can't read) go through
 * the app's own retrieval, with the real embedding model, the knowledge base and the installed packs. Written to
 * Android/data/<pkg>/files/retrieval/:
 * - results.json: each question's chunks (ids, titles, scores, similarity), in order;
 * - embeddings.json: every text embedded on the way with its vector, so the TypeScript engine on the desktop runs the
 *   same search with the same vectors;
 * - kb.sqlite (a copy of the knowledge base) and manifest.json (the installed packs, their files and sizes);
 * - packs/: the pack files themselves, only with copyPacks (they can be gigabytes).
 * Started from adb: am start -n team.sopa.aoair/team.sopa.boar.bench.BenchActivity --es run retrieval --es q64 <base64> [--ez copyPacks true]
 */
object RetrievalParityRunner {
    const val TOP_K = 6

    suspend fun run(context: Context, questionsJson: String, copyPacks: Boolean, log: (String) -> Unit) = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "retrieval").apply { mkdirs() }
        val questions = JSONArray(questionsJson).let { a -> List(a.length()) { a.getString(it) } }
        val services = BoarServices.get(context)
        val embedded = LinkedHashMap<String, FloatArray>()
        val retriever = Retriever(services.knowledgeBase, { services.packs }, { t -> runBlocking { services.embed(t) }.also { embedded[t] = it } }, { services.lexicon })
        val results = JSONArray()
        for ((i, q) in questions.withIndex()) {
            val t0 = android.os.SystemClock.elapsedRealtime()
            val chunks = retriever.retrieve(q, TOP_K)
            val ms = android.os.SystemClock.elapsedRealtime() - t0
            results.put(JSONObject().put("query", q).put("ms", ms).put("chunks", JSONArray(chunks.map { c ->
                JSONObject().put("chunkId", c.chunkId).put("docId", c.docId).put("title", c.title).put("score", finite(c.score))
                    .put("similarity", c.similarity?.let(::finite) ?: JSONObject.NULL).put("matchType", c.matchType.name).put("source", c.source ?: JSONObject.NULL)
            })))
            log("${i + 1}/${questions.size} ${ms} ms  $q -> ${chunks.joinToString(" | ") { it.title }}")
        }
        File(dir, "results.json").writeText(results.toString(1))
        File(dir, "embeddings.json").writeText(JSONObject().apply { embedded.forEach { (t, v) -> put(t, JSONArray(v.map { it.toDouble() })) } }.toString())

        val kb = File(dir, "kb.sqlite").apply { delete() }
        services.exportKnowledgeBase(kb)
        val corpus = File(context.filesDir, "corpus")
        val packFiles = corpus.listFiles { f -> f.name.endsWith(".sqlite") }.orEmpty().sortedBy { it.name }
        val manifest = JSONObject()
            .put("device", android.os.Build.MODEL).put("embedding", services.installed(team.sopa.boar.engine.models.AssetKind.EMBEDDING).firstOrNull()?.filename ?: JSONObject.NULL)
            .put("openPacks", JSONArray(services.packs.map { it.id }))
            .put("packFiles", JSONArray(packFiles.map { JSONObject().put("name", it.name).put("bytes", it.length()) }))
            .put("topK", TOP_K).put("questions", questions.size)
        if (copyPacks) {
            val out = File(dir, "packs").apply { mkdirs() }
            packFiles.forEach { f -> log("copying ${f.name} (${f.length() / 1048576} MB)"); f.copyTo(File(out, f.name), overwrite = true) }
        }
        File(dir, "manifest.json").writeText(manifest.toString(1))
        log("retrieval parity: done, ${questions.size} questions, ${embedded.size} texts embedded -> ${dir.path}")
    }

    /** org.json refuses NaN and infinities. */
    private fun finite(d: Double): Any = if (d.isFinite()) d else d.toString()
}
