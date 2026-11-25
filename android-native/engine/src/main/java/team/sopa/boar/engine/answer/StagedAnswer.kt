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
    /**
     * The intro so far is dropped and written again as the whole answer (a model that reasons first spent the short
     * budget on its reasoning): clear what's shown.
     */
    data object Restart : StagedEvent
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
        val text: String get() = StagedAnswer.join(intro, details)
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
        var introText = AnswerText.stripThinking(intro.toString()).trim()
        var fullAnswer = false
        // Only reasoning came back (LFM2.5 spent the 160 tokens inside <think>, X6, Nov 10): write it again as the whole
        // answer, the normal answer's prompt and budget, with no details step after it.
        if (introText.isEmpty() && intro.isNotBlank() && !shouldStop()) {
            emit(StagedEvent.Restart)
            intro.setLength(0)
            fullAnswer = true
            metrics = generate(session, prompt(session, req.query, sources, req.systemPrompt, req.history, req.detailsReminder ?: req.introReminder, req.enableThinking), req.maxTokens) { piece ->
                intro.append(piece)
                emit(StagedEvent.Piece(StagedStep.INTRO, piece))
            }
            introText = AnswerText.stripThinking(intro.toString()).trim()
        }

        val details = StringBuilder()
        // BOAR-78 (F3, 25 Nov): "quanto e 20% de 300" after a Brazil/Russia question wrote its details on Brazil and
        // Russia, the earlier turns being the only material. A question on its own topic (not a follow-up, by the same
        // rule the search uses) gets details from the intro alone; with no sources and an intro that ended by itself,
        // the intro is the answer. An intro cut at its cap (code, a translation) still goes on.
        val ownTopic = !FollowUp.isFollowUp(req.query, req.history)
        val introEnded = metrics?.stopReason != "length"
        if (req.detailsReminder != null && introText.isNotEmpty() && !fullAnswer && !shouldStop() && !(ownTopic && sources.isEmpty() && introEnded)) {
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
                // The intro stays in the conversation as the model's own turn, so the details go on from it; earlier
                // turns only for a follow-up.
                val earlier = req.history.takeUnless { ownTopic }
                val history = ConversationHistory(earlier?.summary, earlier?.turns.orEmpty() +
                    ConversationTurn("user", req.query) + ConversationTurn("assistant", introText))
                metrics = generate(session, prompt(session, DETAILS_ASK, sources, req.systemPrompt, history, req.detailsReminder, req.enableThinking, languageOf = req.query), req.maxTokens) { piece ->
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

    /** [languageOf]: the question that sets the answer's language (the details step asks DETAILS_ASK, in English). */
    private suspend fun prompt(session: TextModel, query: String, sources: List<Chunk>, systemPrompt: String?, history: ConversationHistory?, reminder: String?, thinking: Boolean,
                               languageOf: String = query): Pair<String, List<String>> =
        if (session.hasChatTemplate) {
            val f = session.formatChat(RagPure.assembleChatMessages(query, sources, systemPrompt, history, reminder, languageOf = languageOf).map { ChatMessage(it.role, it.content) }, enableThinking = thinking)
            f.prompt to f.stops
        } else RagPure.assemblePrompt(query, sources, systemPrompt, history, reminder, languageOf = languageOf) to AnswerPipeline.DEFAULT_STOP_SEQUENCES

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

        /**
         * The answer as shown and saved: the intro, then the details on their own paragraph. The chat calls it while the
         * pieces stream in, so the text reads as it will once done (a break added per piece put one token per line, X6).
         */
        fun join(intro: String, details: String): String =
            listOf(intro.trim(), details.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
        /** Passages the research step may add. */
        const val MORE_SOURCES = 2
        const val KEY_FACTS = 3
        private const val KEY_FACT_CHARS = 280
        /** The details' turn: the intro above is the model's own. */
        const val DETAILS_ASK = "Now give the details your short answer above left out, from the context, citing [n]. Don't repeat the short answer."

        /**
         * One source's line for the "+N sources" list (adam, Nov 10): its best answering sentence, or its first sentence
         * when none holds a word of the question; cut like the key facts.
         */
        fun factOf(query: String, source: Chunk): String {
            val best = team.sopa.boar.engine.rag.Compress.scoreSentences(query, listOf(source)).filter { it.score > 0 }.maxByOrNull { it.score }?.text
            val text = (best ?: team.sopa.boar.engine.rag.Compress.splitSentences(source.body).firstOrNull() ?: source.body).trim()
            return if (text.length > KEY_FACT_CHARS) text.take(KEY_FACT_CHARS).trimEnd() + "…" else text
        }

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
            val values by lazy { evidence.flatMap { e -> NUMBER.findAll(e).mapNotNull { valueOf(it.value.trimEnd('.', ',')) } } }
            val body = text.replace(CITATION, " ").replace(LIST_MARK, " ")
            return NUMBER.findAll(body)
                // Small counts are the model's own wording ("3 steps"), unless a scale word makes them big ("1.5 million").
                .filter { m -> norm(m.value).toDoubleOrNull()?.let { it > 10 } ?: true || SCALE.containsMatchIn(body.substring(m.range.last + 1)) }
                .filter { m -> norm(m.value) !in known && !scaledMatch(m, body, values) }
                .map { it.value.trimEnd('.', ',') }.distinct().toList()
        }

        /** A scale word right after a number: "146 million", "1,5 bilhão", "200 mil". */
        private val SCALE = Regex("""^\s*(thousand|million|millions|billion|billions|trillion|mil|milhão|milhao|milhões|milhoes|bilhão|bilhao|bilhões|bilhoes|trilhão|trilhões)(?![\p{L}])""", RegexOption.IGNORE_CASE)

        /**
         * "146 million" against a source's 146,119,928 (t9 0400 item 3: flagged on the F3): a figure written with a scale
         * word is backed when some source number, at that scale, rounds to it at the digits stated ("146", "1.5", "1,5").
         */
        private fun scaledMatch(m: MatchResult, body: String, values: List<Double>): Boolean {
            val word = SCALE.find(body.substring(m.range.last + 1))?.groupValues?.get(1)?.lowercase() ?: return false
            val scale = when {
                word == "thousand" || word == "mil" -> 1e3
                word.startsWith("mil") -> 1e6
                word.startsWith("bil") -> 1e9
                else -> 1e12
            }
            val raw = m.value.trimEnd('.', ',')
            // The scale word says the mark (validator R-B6: "1.234 million" was read 1,234 million, a 1000x accept):
            // English "million" takes a decimal dot ("1.234 million", "1,500 million" in thousands), Portuguese "milhões"
            // a decimal comma ("1,234 milhões", "1.500 milhões" in thousands).
            val ptWord = word != "thousand" && word != "million" && word != "millions" && word != "billion" && word != "billions" && word != "trillion"
            val decimalMark = if (ptWord) ',' else '.'
            val single = raw.count { it == '.' || it == ',' } == 1
            val dec = single && raw.contains(decimalMark)
            val stated = (if (dec) raw.replace(if (ptWord) "." else ",", "").replace(',', '.') else raw.replace(",", "").replace(".", "")).toDoubleOrNull() ?: return false
            val places = if (dec) raw.length - raw.indexOf(decimalMark) - 1 else 0
            val f = Math.pow(10.0, places.toDouble())
            return values.any { v -> Math.round(v / scale * f) / f == stated }
        }

        /** A written number's value: "146,119,928", "146.119.928" (pt), "8,515,767.5", "3,5"; null when it can't tell. */
        private fun valueOf(n: String): Double? = when {
            Regex("""^\d{1,3}(,\d{3})+(\.\d+)?$""").matches(n) -> n.replace(",", "").toDoubleOrNull()
            Regex("""^\d{1,3}(\.\d{3})+(,\d+)?$""").matches(n) -> n.replace(".", "").replace(',', '.').toDoubleOrNull()
            Regex("""^\d+,\d{1,2}$""").matches(n) -> n.replace(',', '.').toDoubleOrNull()
            else -> n.toDoubleOrNull()
        }
    }
}
