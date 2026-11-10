package team.sopa.boar.engine.answer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import team.sopa.boar.engine.ChatMessage
import team.sopa.boar.engine.GenerationEvent
import team.sopa.boar.engine.GenerationMetrics
import team.sopa.boar.engine.Sampling
import team.sopa.boar.engine.TextModel
import team.sopa.boar.engine.rag.Chunk
import team.sopa.boar.engine.rag.Compress
import team.sopa.boar.engine.rag.ConversationHistory
import team.sopa.boar.engine.rag.ConversationTurn
import team.sopa.boar.engine.rag.RagPure

/** The steps of a staged answer, in the order they run. */
enum class StagedStep { SOURCES, INTRO, RESEARCH, DETAILS, CHECK }

/** A sentence from a source that answers the question, shown before the model writes: [source] is its index in the sources. */
data class KeyFact(val source: Int, val sentence: String)

/** When each step happened, in ms from the start of the staged answer (the model load included). Null: it didn't run. */
data class StageTimes(
    val sourcesMs: Double? = null,
    val modelReadyMs: Double? = null,
    val introFirstMs: Double? = null,
    val introDoneMs: Double? = null,
    val researchMs: Double? = null,
    val detailsFirstMs: Double? = null,
    val totalMs: Double? = null,
) {
    fun toMap(): Map<String, Double> = listOfNotNull(
        sourcesMs?.let { "sources" to it }, modelReadyMs?.let { "model" to it }, introFirstMs?.let { "introFirst" to it },
        introDoneMs?.let { "introDone" to it }, researchMs?.let { "research" to it }, detailsFirstMs?.let { "detailsFirst" to it },
        totalMs?.let { "total" to it },
    ).toMap()
}

data class StagedRequest(
    val query: String,
    val systemPrompt: String?,
    /** The short intro's style line (Succinct's). */
    val introReminder: String?,
    /** The details' style line (the picked style's); null: the intro is the whole answer (a health question kept short). */
    val detailsReminder: String?,
    val maxTokens: Int,
    val history: ConversationHistory? = null,
    val enableThinking: Boolean = false,
)

sealed interface StagedEvent {
    data class Step(val step: StagedStep) : StagedEvent
    /** What the search found, before any model runs: the passages ([1]..[n]) and the sentences that answer best. */
    data class Cards(val sources: List<Chunk>, val keyFacts: List<KeyFact>, val retrievalMs: Double) : StagedEvent
    data class Piece(val step: StagedStep, val text: String) : StagedEvent
    /** Passages the search between the steps added, numbered after the first ones. */
    data class MoreSources(val added: List<Chunk>) : StagedEvent
    /** Numbers in the answer that no source (nor the question) has. */
    data class Checked(val unsupported: List<String>) : StagedEvent
    data class Done(
        val intro: String,
        val details: String,
        val sources: List<Chunk>,
        val keyFacts: List<KeyFact>,
        val unsupported: List<String>?,
        val times: StageTimes,
        val retrievalMs: Double?,
        /** The details' generation (the intro's when there were no details). */
        val metrics: GenerationMetrics?,
        /** The model couldn't be loaded: the cards are all there is. */
        val noModel: Boolean = false,
        /** Stop was pressed: what was shown so far is the answer. */
        val stopped: Boolean = false,
    ) : StagedEvent {
        /** The answer as saved: the intro, then the details. */
        val text: String get() = listOf(intro.trim(), details.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
    }
}

/**
 * Boar Staged answers (a Boar Lab): the answer arrives in steps, each one useful on its own, and Stop keeps whatever is
 * on screen. SOURCES: search and the sentences that answer, before the model is loaded. INTRO: two or three sentences.
 * RESEARCH: a second search with what the intro said, for passages the first one missed (no model). DETAILS: the same
 * model adds what the intro left out, reading every passage. CHECK: each number written must be in a source (in code,
 * no second model). One model at a time: the search runs between the generations, never during one.
 */
class StagedAnswer(
    private val retrieve: (String, Int) -> List<Chunk>,
    private val classify: (String) -> String,
    private val retrievalIrrelevant: (String) -> Boolean,
    private val clock: () -> Double = { System.nanoTime() / 1e6 },
) {
    fun answer(req: StagedRequest, load: suspend () -> TextModel?, shouldStop: () -> Boolean): Flow<StagedEvent> = flow {
        val t0 = clock()
        fun now() = clock() - t0
        var times = StageTimes()
        val task = classify(req.query)
        var sources: List<Chunk> = emptyList()
        var keyFacts: List<KeyFact> = emptyList()
        var retrievalMs: Double? = null
        if (!retrievalIrrelevant(task)) {
            emit(StagedEvent.Step(StagedStep.SOURCES))
            val found = FollowUp.search(req.query, req.history, { c: Chunk -> "${c.title} ${c.body}" }) { retrieve(it, AnswerPipeline.ANSWER_CONTEXT_CHUNKS) }
            sources = Compress.forAnswer(found.searchedFor, found.chunks, task).chunks
            keyFacts = keyFacts(found.searchedFor, sources)
            retrievalMs = now()
            times = times.copy(sourcesMs = retrievalMs)
            emit(StagedEvent.Cards(sources, keyFacts, retrievalMs))
        }
        fun done(intro: String, details: String, metrics: GenerationMetrics?, unsupported: List<String>?, noModel: Boolean = false) =
            StagedEvent.Done(intro, details, sources, keyFacts, unsupported, times.copy(totalMs = now()), retrievalMs, metrics, noModel, shouldStop())
        if (shouldStop()) { emit(done("", "", null, null)); return@flow }

        emit(StagedEvent.Step(StagedStep.INTRO))
        val session = load()
        times = times.copy(modelReadyMs = now())
        if (session == null) { emit(done("", "", null, null, noModel = true)); return@flow }
        val intro = StringBuilder()
        var metrics = generate(session, prompt(session, req.query, sources, req.systemPrompt, req.history, req.introReminder, req.enableThinking), INTRO_TOKENS.coerceAtMost(req.maxTokens)) { piece ->
            if (times.introFirstMs == null) times = times.copy(introFirstMs = now())
            intro.append(piece)
            emit(StagedEvent.Piece(StagedStep.INTRO, piece))
        }
        times = times.copy(introDoneMs = now())
        val introText = AnswerText.stripThinking(intro.toString()).trim()

        val details = StringBuilder()
        if (req.detailsReminder != null && introText.isNotEmpty() && !shouldStop()) {
            if (sources.isNotEmpty()) {
                emit(StagedEvent.Step(StagedStep.RESEARCH))
                val added = moreSources(req.query, introText, sources)
                times = times.copy(researchMs = now())
                if (added.isNotEmpty()) {
                    sources = sources + added
                    emit(StagedEvent.MoreSources(added))
                }
            }
            if (!shouldStop()) {
                emit(StagedEvent.Step(StagedStep.DETAILS))
                // The intro stays in the conversation as the model's own turn, so the details go on from it.
                val history = ConversationHistory(req.history?.summary, req.history?.turns.orEmpty() +
                    ConversationTurn("user", req.query) + ConversationTurn("assistant", introText))
                metrics = generate(session, prompt(session, DETAILS_ASK, sources, req.systemPrompt, history, req.detailsReminder, req.enableThinking), req.maxTokens) { piece ->
                    if (times.detailsFirstMs == null) times = times.copy(detailsFirstMs = now())
                    details.append(piece)
                    emit(StagedEvent.Piece(StagedStep.DETAILS, piece))
                }
            }
        }

        val detailsText = AnswerText.stripThinking(details.toString()).trim()
        var unsupported: List<String>? = null
        val written = listOf(introText, detailsText).filter { it.isNotEmpty() }.joinToString("\n\n")
        if (written.isNotEmpty() && sources.isNotEmpty()) {
            emit(StagedEvent.Step(StagedStep.CHECK))
            unsupported = unsupportedNumbers(written, sources.map { "${it.title}\n${it.body}" } + req.query)
            emit(StagedEvent.Checked(unsupported))
        }
        emit(done(introText, detailsText, metrics, unsupported))
    }

    private suspend fun generate(session: TextModel, p: Pair<String, List<String>>, maxTokens: Int, onPiece: suspend (String) -> Unit): GenerationMetrics? {
        var metrics: GenerationMetrics? = null
        session.generate(p.first, Sampling(nPredict = maxTokens, temperature = 0.7f, stops = p.second)).collect { e ->
            when (e) {
                is GenerationEvent.Piece -> onPiece(e.text)
                is GenerationEvent.Done -> metrics = e.metrics
            }
        }
        return metrics
    }

    private suspend fun prompt(session: TextModel, query: String, sources: List<Chunk>, systemPrompt: String?, history: ConversationHistory?, reminder: String?, thinking: Boolean): Pair<String, List<String>> =
        if (session.hasChatTemplate) {
            val f = session.formatChat(RagPure.assembleChatMessages(query, sources, systemPrompt, history, reminder).map { ChatMessage(it.role, it.content) }, enableThinking = thinking)
            f.prompt to f.stops
        } else RagPure.assemblePrompt(query, sources, systemPrompt, history, reminder) to AnswerPipeline.DEFAULT_STOP_SEQUENCES

    /** Searched again with the question and the intro: passages from articles not already read, the answering sentences only. */
    private fun moreSources(query: String, intro: String, have: List<Chunk>): List<Chunk> {
        val seen = have.map { it.docId }.toSet()
        val fresh = retrieve("$query $intro", AnswerPipeline.ANSWER_CONTEXT_CHUNKS).filter { it.docId !in seen }
        if (fresh.isEmpty()) return emptyList()
        return Compress.forAnswer(query, fresh, classify(query)).chunks.take(MORE_SOURCES)
    }

    companion object {
        /** The intro's cap: Succinct's two or three sentences with room to finish one. */
        const val INTRO_TOKENS = 160
        /** Passages the research step may add. */
        const val MORE_SOURCES = 2
        const val KEY_FACTS = 3
        private const val KEY_FACT_CHARS = 280
        /** The details' turn: the intro above is the model's own. */
        const val DETAILS_ASK = "Now give the details your short answer above left out, from the context, citing [n]. Don't repeat the short answer."

        /** The best answering sentence of each source, best first, at most [KEY_FACTS]; none when no word of the question is in it. */
        fun keyFacts(query: String, sources: List<Chunk>): List<KeyFact> =
            Compress.scoreSentences(query, sources).filter { it.score > 0 }
                .groupBy { it.chunkIndex }.values.map { s -> s.maxBy { it.score } }
                .sortedByDescending { it.score }.take(KEY_FACTS)
                .map { KeyFact(it.chunkIndex, it.text.trim().let { t -> if (t.length > KEY_FACT_CHARS) t.take(KEY_FACT_CHARS).trimEnd() + "…" else t }) }

        private val NUMBER = Regex("\\d[\\d,.]*\\d|\\d")
        private val CITATION = Regex("\\[\\d+(?:\\s*[,–-]\\s*\\d+)*]")
        private val LIST_MARK = Regex("(?m)^\\s*\\d+[.)]\\s")

        /**
         * The numbers in [text] that none of [evidence] has (Commonplace's check, done in code): "1,200" and "1200" match.
         * Citations, list numbers and numbers up to 10 are left out (counts like "3 steps" are the model's own wording).
         */
        fun unsupportedNumbers(text: String, evidence: List<String>): List<String> {
            fun norm(n: String) = n.trimEnd('.', ',').replace(",", "")
            val known = evidence.flatMap { e -> NUMBER.findAll(e).map { norm(it.value) } }.toSet()
            val body = text.replace(CITATION, " ").replace(LIST_MARK, " ")
            return NUMBER.findAll(body).map { it.value.trimEnd('.', ',') }
                .filter { n -> norm(n).toDoubleOrNull()?.let { it > 10 } ?: true }
                .filter { norm(it) !in known }.distinct().toList()
        }
    }
}
