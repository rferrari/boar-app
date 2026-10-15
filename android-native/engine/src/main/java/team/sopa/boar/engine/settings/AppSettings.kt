package team.sopa.boar.engine.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import team.sopa.boar.engine.EngineTweaks
import java.io.File

/**
 * The app's settings in files/settings.json, the Expo app's own file (src/models/settings.ts), read and written in
 * place so an upgrade keeps every setting. Keys this build doesn't know are kept as they are on every write. The
 * native-only Advanced tweaks live under "engineTweaks". Writes go to a temporary file renamed over the old one.
 */
class AppSettings(private val file: File) {
    private val json = Json { prettyPrint = false }
    @Volatile private var data: JsonObject = load()

    private fun load(): JsonObject = runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrDefault(JsonObject(emptyMap()))

    @Synchronized private fun update(change: MutableMap<String, JsonElement>.() -> Unit) {
        val m = data.toMutableMap()
        m.change()
        data = JsonObject(m)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(JsonObject.serializer(), data))
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }

    private fun str(key: String) = (data[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun int(key: String) = (data[key] as? JsonPrimitive)?.intOrNull
    private fun bool(key: String) = (data[key] as? JsonPrimitive)?.booleanOrNull
    private fun set(key: String, value: JsonElement?) = update { if (value == null || value is JsonNull) remove(key) else put(key, value) }

    /** activeModelId.llm, .embedding… */
    fun activeModelId(kind: String): String? =
        (data["activeModelId"] as? JsonObject)?.get(kind)?.jsonPrimitive?.contentOrNull?.ifEmpty { null }
    fun setActiveModelId(kind: String, id: String?) = update {
        val m = (this["activeModelId"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (id == null) m.remove(kind) else m[kind] = JsonPrimitive(id)
        put("activeModelId", JsonObject(m))
    }

    /** modelRoleAssignments: role ("fast", "general"…) to model id. */
    fun modelRoleAssignments(): Map<String, String> =
        (data["modelRoleAssignments"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.ifEmpty { null }?.let { k to it } }?.toMap() ?: emptyMap()

    var personalityId: String? get() = str("personalityId"); set(v) = set("personalityId", v?.let(::JsonPrimitive))
    var customSystemPrompt: String? get() = str("customSystemPrompt"); set(v) = set("customSystemPrompt", v?.let(::JsonPrimitive))
    /** The answer length the user picked in Settings; null is Auto. */
    var maxTokensChoice: Int? get() = int("maxTokens"); set(v) = set("maxTokens", v?.let(::JsonPrimitive))
    /** The answer's token limit: the user's pick, else Auto by style (and Deep Research). */
    val maxTokens: Int get() = maxTokensChoice ?: autoMaxTokens(personalityId, deepResearchMode)

    /** The limit for an answer now: the user's pick as is, else Auto fitted to the context the model was opened with. */
    fun answerTokens(openedContext: Int?): Int = maxTokensChoice ?: fitToContext(autoMaxTokens(personalityId, deepResearchMode), openedContext)

    companion object {
        const val AUTO_MAX_TOKENS = 512
        /** Detailed answers ran past 512 tokens and stopped mid-sentence (X6, Oct 11). */
        const val AUTO_MAX_TOKENS_DETAILED = 1024
        /**
         * A Deep Research write-up, any style: a Detailed one ran past 1024 tokens and stopped mid-sentence inside its own
         * conclusion (X6, Oct 14). The largest choice in Settings; the style still sets how long it really is.
         */
        const val AUTO_MAX_TOKENS_RESEARCH = 2048

        /** Context kept for the prompt (instructions, sources, history) when Auto sizes the answer. */
        const val PROMPT_RESERVE = 1500
        /** Auto never goes below this, however small the context. */
        const val MIN_AUTO_TOKENS = 256

        /**
         * Auto length on a small context: a phone with up to 4.5 GB of RAM opens 2048 tokens (contextSizeForRam), where
         * a 2048-token Deep Research write-up can't fit next to its prompt. Unknown context (no model loaded yet): [cap].
         */
        fun fitToContext(cap: Int, openedContext: Int?): Int =
            if (openedContext == null) cap else minOf(cap, maxOf(MIN_AUTO_TOKENS, openedContext - PROMPT_RESERVE))

        /** No style set means Detailed, the native app's default (BoarServices.DEFAULT_PERSONALITY_ID). */
        fun autoMaxTokens(personalityId: String?, deepResearch: Boolean = false): Int = when {
            deepResearch -> AUTO_MAX_TOKENS_RESEARCH
            (personalityId ?: "detailed") == "detailed" -> AUTO_MAX_TOKENS_DETAILED
            else -> AUTO_MAX_TOKENS
        }
    }
    var deepResearchMode: Boolean get() = bool("deepResearchMode") ?: false; set(v) = set("deepResearchMode", JsonPrimitive(v))
    /** The user closed the "Mixture of agents" line under the header (it stays closed). */
    var researchBannerHidden: Boolean get() = bool("researchBannerHidden") ?: false; set(v) = set("researchBannerHidden", JsonPrimitive(v))
    var adaptiveRoutingEnabled: Boolean get() = bool("adaptiveRoutingEnabled") ?: false; set(v) = set("adaptiveRoutingEnabled", JsonPrimitive(v))
    var routingPreset: String? get() = str("routingPreset"); set(v) = set("routingPreset", v?.let(::JsonPrimitive))
    /** "campfire" or "moonlight"; the theme's default when unset. */
    var themeId: String? get() = str("themeId"); set(v) = set("themeId", v?.let(::JsonPrimitive))
    var fontScale: String get() = str("fontScale") ?: "standard"; set(v) = set("fontScale", JsonPrimitive(v))
    var languageId: String? get() = str("languageId"); set(v) = set("languageId", v?.let(::JsonPrimitive))
    var voiceInputEnabled: Boolean get() = bool("voiceInputEnabled") ?: true; set(v) = set("voiceInputEnabled", JsonPrimitive(v))
    var hapticsEnabled: Boolean get() = bool("hapticsEnabled") ?: true; set(v) = set("hapticsEnabled", JsonPrimitive(v))
    var hidePromptIdeas: Boolean get() = bool("hidePromptIdeas") ?: false; set(v) = set("hidePromptIdeas", JsonPrimitive(v))
    /**
     * Advanced tweaks → Show technical details (the Expo app's live-stats key): off, the chat shows the short receipt
     * only; on, the receipt's details, live tok/s in the header, the menu's memory and storage bars, Hardware telemetry.
     */
    var showLiveStats: Boolean get() = bool("showLiveStats") ?: false; set(v) = set("showLiveStats", JsonPrimitive(v))

    /** DEFAULT_MEMORY_SETTINGS when unset. */
    var autoSummarize: Boolean get() = bool("autoSummarize") ?: true; set(v) = set("autoSummarize", JsonPrimitive(v))
    var historyTurnThreshold: Int get() = int("historyTurnThreshold") ?: 6; set(v) = set("historyTurnThreshold", JsonPrimitive(v))
    var maxSavedSessions: Int get() = int("maxSavedSessions") ?: 10; set(v) = set("maxSavedSessions", JsonPrimitive(v))
    var autoGenerateTitles: Boolean get() = bool("autoGenerateTitles") ?: true; set(v) = set("autoGenerateTitles", JsonPrimitive(v))

    /** Advanced tweaks (native only); all Auto when unset or unreadable. */
    var engineTweaks: EngineTweaks
        get() = EngineTweaks.fromJson(data["engineTweaks"]?.toString())
        set(v) = set("engineTweaks", if (v.isAuto) null else json.parseToJsonElement(v.toJson()))

    /** Every key as stored, for tests and diagnostics. */
    private fun strings(key: String): List<String> = (data[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()
    private fun setStrings(key: String, v: List<String>) = set(key, if (v.isEmpty()) null else kotlinx.serialization.json.JsonArray(v.map(::JsonPrimitive)))

    /** CR-2's bookkeeping, under the Expo app's keys (loadCrashedIds, pendingLoadCrash) plus #73's loadMappedIds. */
    val loadLedger = object : team.sopa.boar.engine.models.LoadGuard.Ledger {
        override var crashedIds: List<String> get() = strings("loadCrashedIds"); set(v) = setStrings("loadCrashedIds", v)
        override var mappedIds: List<String> get() = strings("loadMappedIds"); set(v) = setStrings("loadMappedIds", v)
        override var pendingCrash: team.sopa.boar.engine.models.LoadGuard.Crash?
            get() = data["pendingLoadCrash"]?.takeIf { it is JsonObject }?.let { team.sopa.boar.engine.models.LoadGuard.crashFromJson(it.toString()) }
            set(v) = set("pendingLoadCrash", v?.let { json.parseToJsonElement(team.sopa.boar.engine.models.LoadGuard.crashToJson(it)) })
    }

    /** The last model that loaded: CR-2's fallback after a crash on a cold start. */
    var lastLoadedModelId: String? get() = str("lastLoadedModelId"); set(v) = set("lastLoadedModelId", v?.let(::JsonPrimitive))

    /** The id submit-results gave this phone's key (settings.ts shareDeviceId). */
    var shareDeviceId: String? get() = str("shareDeviceId"); set(v) = set("shareDeviceId", v?.let(::JsonPrimitive))

    // The PIN lock (native app): the data key sealed with the PIN and the device key, its salt, and the wrong-try count.
    var pinWrapped: String? get() = str("pinWrapped"); set(v) = set("pinWrapped", v?.let(::JsonPrimitive))
    var pinSalt: String? get() = str("pinSalt"); set(v) = set("pinSalt", v?.let(::JsonPrimitive))
    var pinIterations: Int get() = int("pinIterations") ?: team.sopa.boar.engine.security.PinKeys.ITERATIONS; set(v) = set("pinIterations", JsonPrimitive(v))
    var pinFailures: Int get() = int("pinFailures") ?: 0; set(v) = set("pinFailures", JsonPrimitive(v))
    var pinLockedUntil: Long get() = (data["pinLockedUntil"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L; set(v) = set("pinLockedUntil", JsonPrimitive(v))

    fun raw(): JsonObject = data
}
