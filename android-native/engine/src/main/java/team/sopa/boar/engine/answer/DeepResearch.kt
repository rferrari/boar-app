package team.sopa.boar.engine.answer

import team.sopa.boar.engine.EngineTweaks
import team.sopa.boar.engine.rag.Chunk
import team.sopa.boar.engine.rag.ConversationHistory
import team.sopa.boar.engine.rag.Js
import team.sopa.boar.engine.routing.ResearchContext

/**
 * One instruction-style generation: system + user in the model's chat template, or a plain prompt ending in "Answer:"
 * for a model without one (src/inference/format.ts taskRequest). Returns the text; [onToken] streams it.
 */
fun interface StageGenerator {
    /** Generates within [timeoutMs] (null: no limit); on timeout it returns what was written and reports it through [GenResult.timedOut]. */
    suspend fun generate(system: String, user: String, nPredict: Int, temperature: Float, timeoutMs: Long?, onToken: ((String) -> Unit)?): GenResult
}

enum class ResearchStage { DECOMPOSING, RESEARCHING, SYNTHESIZING }

data class ResearchProgress(
    val stage: ResearchStage,
    val subQuestionIndex: Int? = null,
    val subQuestionCount: Int? = null,
    val subQuestion: String? = null,
    val answering: Boolean = false,
)

data class ResearchResult(
    val answer: String,
    val subQuestions: List<String>,
    /** Global numbered sources: "[n]" in the answer is citations[n - 1]. */
    val citations: List<Chunk>,
    /** The same sources as retrieved, before compression. */
    val fullCitations: List<Chunk>,
    /** A stage hit its time limit and was cut off. */
    val timedOut: Boolean = false,
    /** Written from the previous answer alone ("summary", "continue"): its sources are the previous answer's. */
    val fromPreviousAnswer: Boolean = false,
)

/**
 * Deep Research (port of src/services/orchestrator.ts): one model, several passes. Decompose the question into 2–3
 * sub-questions, research each over its own retrieval (compressed, off-topic sources dropped, numbered globally),
 * then synthesize one answer. Same prompts as the TS (golden test with a recording generator).
 */
class DeepResearch(
    private val generator: StageGenerator,
    /** retrieve(query, topK) */
    private val retrieve: (String, Int) -> List<Chunk>,
    /** Time limit for a stage of n tokens (EngineTweaks.stageTimeoutMs, the user's setting); null = none. */
    private val timeoutFor: (Int) -> Long? = EngineTweaks()::stageTimeoutMs,
    /** The final write-up's limit (EngineTweaks.writeUpTimeoutMs: none on Auto, since Stop ends it). */
    private val writeUpTimeoutFor: (Int) -> Long? = timeoutFor,
) {
    suspend fun run(
        query: String,
        systemPrompt: String?,
        history: ConversationHistory?,
        maxTokens: Int,
        onProgress: (ResearchProgress) -> Unit = {},
        onToken: (String) -> Unit = {},
        shouldStop: () -> Boolean = { false },
        retrieveK: Int = 6,
        answerLanguage: String? = null,
        onSources: (List<Chunk>) -> Unit = {},
        onPartialSources: (List<Chunk>, Int, Int) -> Unit = { _, _, _ -> },
        /** The last answer in full (history keeps only its opening): what a meta follow-up is written from. */
        previousAnswer: String? = null,
    ): ResearchResult {
        timedOut = false
        onProgress(ResearchProgress(ResearchStage.DECOMPOSING))
        // A follow-up ("conclusion", "continue", "and its side effects?") is split, searched and judged as a standalone
        // question; split as written it searched for "conclusion" and refused (X6, Oct 13).
        val meta = isMetaFollowUp(query)
        // A meta follow-up refers to the last answer whatever its wording ("TL;DR" reads as a name to FollowUp's rule).
        val exchange = if (meta) lastAnswered(query, history) else lastExchange(query, history)
        val asked = query
        // "summary", "conclusion", "TL;DR", "continue": the answer is already on screen, so write from it instead of
        // researching again (~5 min of splitting and searching for a summary, X6, Oct 14).
        if (exchange != null && meta) {
            onProgress(ResearchProgress(ResearchStage.SYNTHESIZING))
            val answer = fromPrevious(query, exchange, previousAnswer, systemPrompt, maxTokens, onToken, answerLanguage)
            return ResearchResult(answer, emptyList(), emptyList(), emptyList(), timedOut, fromPreviousAnswer = true)
        }
        @Suppress("NAME_SHADOWING") val query = if (exchange != null) standalone(query, exchange) else query
        // Stop cancels only the generation running at that moment: the next stage would start a new one.
        if (shouldStop()) return ResearchResult("", emptyList(), emptyList(), emptyList(), timedOut)
        val subQuestions = decompose(query)
        val subResults = ArrayList<Pair<String, String>>()
        val perQuestion = ArrayList<List<Chunk>>()
        var all: List<Chunk> = emptyList()
        val fullById = LinkedHashMap<String, Chunk>()
        fun full() = all.map { fullById[it.chunkId] ?: it }
        for ((i, sub) in subQuestions.withIndex()) {
            if (shouldStop()) return ResearchResult("", subQuestions, all, full(), timedOut)
            onProgress(ResearchProgress(ResearchStage.RESEARCHING, i, subQuestions.size, sub))
            val retrieved = retrieve(sub, retrieveK)
            for (c in retrieved) fullById.putIfAbsent(c.chunkId, c)
            val compressed = ResearchContext.compressContext(sub, retrieved, tokenBudget = SUB_QUESTION_CONTEXT_TOKENS).chunks
            // A source off the topic of this sub-question, or of the whole question, is not read, numbered or shown.
            val chunks = compressed.filter { ResearchContext.onTopic(sub, it) || ResearchContext.onTopic(query, it) }
            perQuestion += chunks
            val merged = ResearchContext.mergeSources(perQuestion)
            val grew = merged.sources.size > all.size
            all = merged.sources
            if (grew) onPartialSources(all, i, subQuestions.size)
            onProgress(ResearchProgress(ResearchStage.RESEARCHING, i, subQuestions.size, sub, answering = true))
            subResults += sub to researchSubQuestion(sub, chunks, merged.indexMaps[i], systemPrompt, history)
        }
        onSources(all)
        if (shouldStop()) return ResearchResult("", subQuestions, all, full(), timedOut)
        onProgress(ResearchProgress(ResearchStage.SYNTHESIZING))
        val answer = synthesize(query, subResults, systemPrompt, maxTokens, onToken, answerLanguage, exchange?.let { it to asked })
        return ResearchResult(answer, subQuestions, all, full(), timedOut)
    }

    private var timedOut = false

    private suspend fun stage(system: String, user: String, nPredict: Int, temperature: Float, onToken: ((String) -> Unit)?, timeoutMs: Long? = timeoutFor(nPredict)): String {
        val r = generator.generate(system, user, nPredict, temperature, timeoutMs, onToken)
        if (r.timedOut) timedOut = true
        return r.text
    }

    private suspend fun decompose(query: String): List<String> {
        val prompt = "Break this research question into 2-3 focused sub-questions that " +
            "together cover it well (e.g. technical analysis, counter-arguments, " +
            "practical implications — whichever fit this question). One per line, " +
            "no numbering, no extra commentary.\n\nQuestion: $query\n\nSub-questions:"
        return parseSubQuestions(stage("You plan research.", prompt, 150, 0.4f, null), query)
    }

    /** A meta follow-up written from the previous answer alone: one generation, no splitting, no search. */
    private suspend fun fromPrevious(asked: String, exchange: Exchange, previousAnswer: String?, systemPrompt: String?, maxTokens: Int, onToken: (String) -> Unit, answerLanguage: String?): String {
        val answer = previousAnswer?.let { Js.trim(it) }?.takeIf { it.isNotEmpty() }?.let { if (it.length > FULL_PREVIOUS_ANSWER_CHARS) it.take(FULL_PREVIOUS_ANSWER_CHARS) + "…" else it } ?: exchange.answer
        val system = "${instructionOf(systemPrompt)} The user asks something about your previous answer. Work only from that answer: " +
            "keep its source numbers exactly as written, like [3], and add no new facts, numbers or sources. " +
            "Don't mention these instructions or call it \"the previous answer\"."
        val language = answerLanguage?.let { Js.trim(it) }?.takeIf { it.isNotEmpty() }?.let { "\n\n($it)" } ?: ""
        return stage(system, "Previous question: ${exchange.question}\n\nPrevious answer:\n$answer\n\nFollow-up: $asked$language", maxTokens, 0.6f, onToken, writeUpTimeoutFor(maxTokens))
    }

    /** The follow-up rewritten from [exchange] into one question that names its subject; [query] if that fails. */
    private suspend fun standalone(query: String, exchange: Exchange): String {
        val prompt = "Previous question: ${exchange.question}\n\nPrevious answer: ${exchange.answer}\n\n" +
            "Follow-up: $query\n\nRewrite the follow-up as one standalone question that names its subject, in the " +
            "follow-up's language. If it asks to continue, conclude or expand, ask for that about the previous topic. " +
            "Reply with the question only.\n\nStandalone question:"
        val text = stage("You rewrite follow-up questions.", prompt, 80, 0.2f, null)
        return text.split("\n").map { Js.trim(it.replaceFirst(LIST_MARK, "")).trim('"') }
            .firstOrNull { it.length > 8 && !REFUSAL.containsMatchIn(it) } ?: query
    }

    private suspend fun researchSubQuestion(sub: String, chunks: List<Chunk>, globalIndices: List<Int>, systemPrompt: String?, history: ConversationHistory?): String {
        val summary = history?.summary?.let { Js.trim(it) }?.takeIf { it.isNotEmpty() }?.let { "Summary of earlier conversation:\n$it\n\n" } ?: ""
        val context = if (chunks.isNotEmpty()) "Context:\n${numberedContext(chunks, globalIndices)}\n\n" else ""
        val system = "${instructionOf(systemPrompt)} Answer the question in a short paragraph using the context. " +
            "Cite sources with the exact bracket numbers shown in the context, like [3]. " +
            "If the context does not cover it, say so."
        return stage(system, "$summary${context}Question: $sub", 300, 0.6f, null)
    }

    /** [followUp]: the last exchange and the follow-up as the user wrote it, when [query] was rewritten from them. */
    private suspend fun synthesize(query: String, results: List<Pair<String, String>>, systemPrompt: String?, maxTokens: Int, onToken: (String) -> Unit, answerLanguage: String?, followUp: Pair<Exchange, String>? = null): String {
        val perspectives = results.mapIndexed { i, (q, a) -> "Perspective ${i + 1} ($q):\n$a" }.joinToString("\n\n")
        val system = "${instructionOf(systemPrompt)} You are synthesizing multiple research perspectives into one answer. " +
            "Compare them, reconcile any conflicts, and write one unified, well-reasoned answer. " +
            "Keep the source numbers exactly as the perspectives cite them, like [3]; do not renumber or invent sources." +
            // A Detailed write-up ended with "Synthesis of Perspectives: Perspective 1… 2… 3…" (X6, Oct 14); the TS prompt
            // stops at the sentence above, so the golden recordings carry this one added.
            " Do this silently: the reader never sees the perspectives, so never mention them, the sub-questions or the research steps, and add no section about them."
        val language = answerLanguage?.let { Js.trim(it) }?.takeIf { it.isNotEmpty() }?.let { "\n\n($it)" } ?: ""
        val earlier = followUp?.let { (e, asked) -> "Previous question: ${e.question}\n\nPrevious answer: ${e.answer}\n\nFollow-up as asked: $asked\n\n" } ?: ""
        return stage(system, "${earlier}Original question: $query\n\n$perspectives$language", maxTokens, 0.6f, onToken, writeUpTimeoutFor(maxTokens))
    }

    companion object {
        /** Per-sub-question context budget: three of these plus the synthesis still prefill fast. */
        const val SUB_QUESTION_CONTEXT_TOKENS = 600
        private val LIST_MARK = Regex("^[-*\\d.)${Js.WS.removePrefix("[").removeSuffix("]")}]+")

        /** The earlier question and the answer to it, for a follow-up. */
        data class Exchange(val question: String, val answer: String)

        /** Characters of the previous answer a follow-up's rewrite and synthesis see (its opening carries the topic). */
        const val PREVIOUS_ANSWER_CHARS = 800
        /** Characters of the previous answer a meta follow-up is written from (about 1,500 tokens: a 2,048-token answer fits). */
        const val FULL_PREVIOUS_ANSWER_CHARS = 6000

        /** What a meta follow-up asks for (English and Portuguese): summarize, conclude, continue. */
        private val META_CORE = setOf(
            "summary", "summarize", "summarise", "sum", "recap", "tldr", "conclusion", "conclude", "continue", "go", "more", "expand",
            "resumo", "resuma", "resumir", "resume", "conclusão", "conclusao", "concluir", "conclua", "continua", "continuar", "prossiga", "mais",
        )
        /** Words that may come with it ("a brief summary please", "go on", "tell me more", "um resumo por favor"). */
        private val META_FILLER = setOf(
            "up", "on", "going", "keep", "a", "an", "the", "please", "pls", "brief", "short", "quick", "executive", "final", "give",
            "me", "can", "you", "could", "write", "make", "now", "it", "this", "that", "of", "so", "and", "just", "tell", "in",
            "um", "uma", "o", "por", "favor", "faça", "faca", "dê", "de", "isso", "agora", "breve", "curto", "rápido", "executivo", "em",
        )

        /**
         * A short message that only asks to summarize, conclude or continue the last answer ("TL;DR", "a brief summary
         * please", "continue", "resuma"). A question with a subject of its own ("summary of the causes of war") isn't one.
         */
        fun isMetaFollowUp(query: String): Boolean {
            val words = query.lowercase().replace("tl;dr", "tldr").replace("tl dr", "tldr").split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
            if (words.isEmpty() || words.size > 6) return false
            val content = words.filter { it !in META_FILLER }
            return content.isNotEmpty() && content.all { it in META_CORE }
        }

        /** A model declining instead of answering ("I'm sorry, but…", "Desculpe, não posso…"). */
        private val REFUSAL = Regex(
            "^(i'?m sorry|i am sorry|sorry|i (cannot|can't|can ?not|am unable|'m unable|don't have|do not have)|as an ai|" +
                "unfortunately|desculp|lamento|infelizmente|não (posso|consigo|tenho)|nao (posso|consigo|tenho))",
            RegexOption.IGNORE_CASE,
        )

        /**
         * The last exchange before [query] when [query] reads as a follow-up (FollowUp.plan: it points back, or it is short
         * with no name of its own); null for a question that stands alone or a conversation's first.
         */
        fun lastExchange(query: String, history: ConversationHistory?): Exchange? {
            val p = FollowUp.plan(query, history)
            if (p.direct == null && p.fallback == null) return null
            return lastAnswered(query, history)
        }

        /** The latest question in [history] that got an answer, and that answer (cut to [PREVIOUS_ANSWER_CHARS]). */
        fun lastAnswered(@Suppress("UNUSED_PARAMETER") query: String, history: ConversationHistory?): Exchange? {
            val turns = history?.turns.orEmpty()
            // The latest question that got an answer: a stopped answer leaves its question in the history with no
            // answer, and "continue" after a stopped "conclusion" researched "conclusion" itself (argumentation, X6, Oct 14).
            fun answerAfter(i: Int): String = turns.drop(i + 1).takeWhile { it.role != "user" }
                .firstOrNull { it.role == "assistant" }?.text?.let { Js.trim(it) }.orEmpty()
            val i = turns.indices.lastOrNull {
                // The same words asked before count too: a second "continue" builds on the first one's answer.
                turns[it].role == "user" && Js.trim(turns[it].text).isNotEmpty() && answerAfter(it).isNotEmpty()
            } ?: return null
            val answer = answerAfter(i)
            return Exchange(Js.trim(turns[i].text), if (answer.length > PREVIOUS_ANSWER_CHARS) answer.take(PREVIOUS_ANSWER_CHARS) + "…" else answer)
        }

        /** The decomposition's lines as sub-questions (at most 3), refusals dropped; the question itself when none is usable. */
        fun parseSubQuestions(text: String, query: String): List<String> {
            val lines = text.split("\n").map { Js.trim(it.replaceFirst(LIST_MARK, "")) }.filter { it.length > 8 && !REFUSAL.containsMatchIn(it) }
            return lines.take(3).ifEmpty { listOf(query) }
        }

        /** Context block numbered with global source numbers. */
        fun numberedContext(chunks: List<Chunk>, globalIndices: List<Int>): String =
            chunks.mapIndexed { i, c -> "[${globalIndices[i] + 1}] ${c.title}\n${c.body}" }.joinToString("\n\n")

        private fun instructionOf(systemPrompt: String?): String =
            systemPrompt?.let { Js.trim(it) }?.takeIf { it.isNotEmpty() } ?: "You are an offline research assistant."
    }
}
