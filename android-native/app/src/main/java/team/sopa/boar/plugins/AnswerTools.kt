package team.sopa.boar.plugins

import team.sopa.boar.engine.rag.ConversationTurn
import team.sopa.boar.engine.routing.LabTools

/**
 * The tool slot: a Boar Labs plugin looks at the question before the model runs (a calculator, a lookup table) and
 * either answers it itself or hands the model something to read first. The chat's default and adaptive paths and the
 * device evaluation use it; Deep Research and answers about the conversation don't. With no tool, nothing changes.
 */
fun interface AnswerTool {
    /** [history]: this chat's earlier turns, oldest first. Null: not this tool's question. */
    fun run(query: String, history: List<ConversationTurn>): ToolResult?
}

sealed interface ToolResult {
    /** Short and stable, kept in the answer's receipt ("calculator-battery"). */
    val tool: String

    /** The answer itself: shown as written, saved like a model's answer, no model call. */
    data class Instant(override val tool: String, val text: String) : ToolResult

    /** Read by the model before it answers, after the system prompt (a guard, a fact the tool worked out). */
    data class Context(override val tool: String, val text: String) : ToolResult
}

object AnswerTools {
    /** Set by Boar Labs at start: the tools of the labs switched on now, safety first, then grid order (LabTools.slotRank). */
    @Volatile var tools: () -> List<AnswerTool> = { emptyList() }

    /** The first tool with something to say; one that throws is skipped, as a search stage is. */
    fun run(query: String, history: List<ConversationTurn>): ToolResult? =
        LabTools.first(runCatching { tools() }.getOrDefault(emptyList())) { it.run(query, history) }
            ?: runCatching { offAnswer(query) }.getOrNull()

    /**
     * What a switched-off plugin says for a request only it can do, instead of the model making it up: "draw me a cat" with
     * Designer off gets one line on where to switch it on (adam, 26 Nov). Null: the model answers. Set by Labs.
     */
    @Volatile var offAnswer: (query: String) -> ToolResult? = { null }

    /**
     * Who wrote an instant answer, from its receipt's tool id: a Labs plugin speaks as itself in the chat (its name and art
     * in the bubble's header) so an answer that skipped the model never looks like BOAR's. Null keeps BOAR. Set by Labs.
     */
    @Volatile var speaker: (tool: String) -> Speaker? = { null }

    /**
     * [titleKey]: the plugin's name (native.labs.<id>.title); [canBypass]: "Ask BOAR instead" is offered (not for safety);
     * [id]: the plugin's id, so a tap on its picture and name opens its page (adam, Nov 13).
     */
    data class Speaker(val titleKey: String, val art: Int?, val emoji: String, val canBypass: Boolean, val id: String? = null)

    /** The id of a plugin that's off but would have answered [query] (Lunar for "what phase is the moon"); null otherwise. Set by Labs. */
    @Volatile var offPlugin: (query: String) -> String? = { null }

    /** The system prompt with a [ToolResult.Context]'s text after it; unchanged otherwise. */
    fun systemPrompt(base: String, r: ToolResult?): String = if (r is ToolResult.Context) "$base\n\n${r.text}" else base
}
