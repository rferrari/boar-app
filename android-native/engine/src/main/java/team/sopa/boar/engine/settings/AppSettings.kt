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
import kotlinx.serialization.json.longOrNull
import team.sopa.boar.engine.EngineTweaks
import java.io.File

/**
 * The app's settings in files/settings.json, the Expo app's own file (src/models/settings.ts), read and written in
 * place so an upgrade keeps every setting. Keys this build doesn't know are kept as they are on every write. The
 * native-only Advanced tweaks live under "engineTweaks". Writes go to a temporary file renamed over the old one.
 */
class AppSettings(private val file: File) {
    private val json = Json { prettyPrint = false }
    /** The file is there but couldn't be read as settings (truncated, not an object): settling then writes nothing. */
    @Volatile private var loadFailed = false
    @Volatile private var data: JsonObject = load()

    private fun load(): JsonObject {
        // A file that is there but can't be read (EACCES, an I/O error) is a failure too, never an empty file to overwrite.
        val text = runCatching { file.readText() }.getOrElse { if (file.exists()) loadFailed = true; null }
        if (text.isNullOrBlank()) return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { loadFailed = true; JsonObject(emptyMap()) }
    }

    /** False when settings.json is there but couldn't be read: a one-time migration then waits, writing nothing (validator S-3). */
    val readable: Boolean get() = !loadFailed

    /** Told after each saved change: the key, its old and new value (null when unset). The app's debug log listens. */
    @Volatile var onChange: ((key: String, old: JsonElement?, new: JsonElement?) -> Unit)? = null

    init {
        // Executive Summary became Personal Files (rc1): a saved Summary opens in Detailed, the default, not in a style
        // that answers only from the user's files.
        if (str("personalityId") == RETIRED_SUMMARY_STYLE) update { put("personalityId", JsonPrimitive("detailed")) }
    }

    /** The one-time 2.1.0 migration ran ([retireProfiles]): profiles no longer gate any value. */
    val profilesRetired: Boolean get() = bool(PROFILES_RETIRED) ?: false

    /**
     * 2.1.0 has no profiles (BOAR-81): every value counts as stored. Run once at launch, so nobody's behaviour changes
     * silently: below Pro the seven Pro values were paused (read as defaults), so they go; on Pro, or on an install from
     * before profiles (everything counted then), they stay, and technical details, on by Pro's default, are written on
     * when nothing is stored. A fresh install (nothing stored yet) gets the new default: technical details off. A file
     * that couldn't be read is left as it is.
     */
    fun retireProfiles() {
        if (profilesRetired || loadFailed) return
        // The profile 2.0.0 stored: "simple" (Starter), "explorer" or "pro"; none on a fresh install or one from before profiles.
        val profile = str(PROFILE_KEY)
        // Fresh: nothing stored at all. Not "no model chosen": an install from before profiles may have its settings and no
        // active model, and had technical details on (CodeRabbit).
        val fresh = profile == null && data.isEmpty()
        update {
            when {
                fresh -> {}
                profile != null && profile != "pro" -> PAUSED_PRO_KEYS.forEach { remove(it) }
                else -> if (this["showLiveStats"] == null) put("showLiveStats", JsonPrimitive(true))
            }
            // The profile and its one-time note go too: nothing reads them now.
            remove(PROFILE_KEY); remove("profileNotePending")
            put(PROFILES_RETIRED, JsonPrimitive(true))
        }
    }

    /** Every key as stored, for the debug log's header. */
    fun snapshot(): Map<String, JsonElement> = data

    @Synchronized private fun update(change: MutableMap<String, JsonElement>.() -> Unit) {
        val before = data
        val m = data.toMutableMap()
        m.change()
        data = JsonObject(m)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(JsonObject.serializer(), data))
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        onChange?.let { tell -> (before.keys + m.keys).filter { before[it] != m[it] }.forEach { k -> runCatching { tell(k, before[k], m[k]) } } }
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

    /** When each model was last loaded to answer ([MODEL_LAST_USED]) or added/downloaded ([MODEL_ADDED]): epoch ms by id. */
    fun modelTimes(key: String): Map<String, Long> =
        (data[key] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { k to it } }?.toMap() ?: emptyMap()
    fun stampModel(key: String, id: String, at: Long = System.currentTimeMillis()) = update {
        val m = (this[key] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        m[id] = JsonPrimitive(at)
        put(key, JsonObject(m))
    }

    /** The Models screen's sort and filter (ModelOrder names); null is the default (Smart, All). */
    var modelSort: String? get() = str("modelCatalogSort"); set(v) = set("modelCatalogSort", v?.let(::JsonPrimitive))
    var modelFilter: String? get() = str("modelCatalogFilter"); set(v) = set("modelCatalogFilter", v?.let(::JsonPrimitive))

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

    /**
     * Settings → Reset settings: every preference back to its default (theme, answer style and length, Deep Research,
     * Advanced tweaks, display and voice switches, chat history options). Kept: models and their roles, the PIN, the
     * language, the custom prompt and the app's own bookkeeping. Chats live elsewhere and stay too.
     */
    fun resetPreferences() = update { PREFERENCE_KEYS.forEach { remove(it) } }

    companion object {
        /** What "Reset settings" clears ([resetPreferences]). */
        val PREFERENCE_KEYS = listOf(
            "themeId", "fontScale", "screenOrientation", "personalityId", "maxTokens", "deepResearchMode", "researchBannerHidden",
            "adaptiveRoutingEnabled", "routingPreset", "engineTweaks", "showLiveStats", "showLiveSpeed", "voiceInputEnabled", "hapticsEnabled",
            "hidePromptIdeas", "autoSummarize", "historyTurnThreshold", "maxSavedSessions", "autoGenerateTitles", "confirmChatDelete",
            "freeMemoryInBackground", "freeMemoryAfterMinutes", "modelCatalogSort", "modelCatalogFilter", "sourcesLayout", "stagedQuotes",
        )
        /** Where 2.0.0 kept the profile; read once by [retireProfiles], then removed. */
        const val PROFILE_KEY = "userProfile"
        const val PROFILES_RETIRED = "profilesRetired"
        /** What 2.0.0 read as defaults below Pro (its Pro gate); [retireProfiles] clears them there. */
        val PAUSED_PRO_KEYS = listOf("engineTweaks", "adaptiveRoutingEnabled", "showLiveStats", "freeMemoryAfterMinutes", "sourcesLayout", "showDrawerStats", "debugLogEnabled")
        const val SOURCES_TODAY = "today"
        const val SOURCES_ABOVE = "above"

        /** [maxSavedSessions]: every chat is kept. */
        const val KEEP_ALL_CHATS = 0
        const val MODEL_LAST_USED = "modelLastUsedAt"
        const val MODEL_ADDED = "modelAddedAt"
        /** Portrait unless the user picks Auto or Landscape (Oct 18): the chat reads best upright on a phone. */
        const val DEFAULT_ORIENTATION = "portrait"
        const val AUTO_MAX_TOKENS = 512
        /** Detailed answers ran past 512 tokens and stopped mid-sentence (X6, Oct 11). */
        const val AUTO_MAX_TOKENS_DETAILED = 1024
        /** Personal Files: the fact first, then two to four sentences; between Succinct's and Detailed's lengths. */
        const val AUTO_MAX_TOKENS_PERSONAL = 768
        /** The retired Executive Summary style's id, moved to Detailed when settings load. */
        const val RETIRED_SUMMARY_STYLE = "summary"
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

        /**
         * No style set means Detailed, the native app's default (BoarServices.DEFAULT_PERSONALITY_ID). Personal Files
         * comes first: Deep Research steps aside in that style.
         */
        fun autoMaxTokens(personalityId: String?, deepResearch: Boolean = false): Int = when {
            personalityId == "personal" -> AUTO_MAX_TOKENS_PERSONAL
            deepResearch -> AUTO_MAX_TOKENS_RESEARCH
            (personalityId ?: "detailed") == "detailed" -> AUTO_MAX_TOKENS_DETAILED
            else -> AUTO_MAX_TOKENS
        }
    }
    var deepResearchMode: Boolean get() = bool("deepResearchMode") ?: false; set(v) = set("deepResearchMode", JsonPrimitive(v))
    /** The user closed the "Mixture of agents" line under the header (it stays closed). */
    var researchBannerHidden: Boolean get() = bool("researchBannerHidden") ?: false; set(v) = set("researchBannerHidden", JsonPrimitive(v))
    /** The one-time note on the first 👍/👎 (kept on the phone, not used yet) was shown. Not a preference: Reset keeps it. */
    var ratingNoteShown: Boolean get() = bool("ratingNoteShown") ?: false; set(v) = set("ratingNoteShown", JsonPrimitive(v))
    /** Run Boar Run's heat and battery card was shown and accepted once: picking it again doesn't ask. */
    var runPresetWarned: Boolean get() = bool("runPresetWarned") ?: false; set(v) = set("runPresetWarned", JsonPrimitive(v))
    var adaptiveRoutingEnabled: Boolean get() = bool("adaptiveRoutingEnabled") ?: false; set(v) = set("adaptiveRoutingEnabled", JsonPrimitive(v))

    /**
     * Staged answers, a core feature since Nov 12 (adam): the sources' quotes before the answer. On by default; the switch
     * is Pro's (Advanced tweaks), but a stored off (Pro's choice, or an old plugin switched off) holds in every profile.
     */
    var stagedQuotes: Boolean get() = bool("stagedQuotes") ?: true; set(v) = set("stagedQuotes", JsonPrimitive(v))
    var routingPreset: String? get() = str("routingPreset"); set(v) = set("routingPreset", v?.let(::JsonPrimitive))
    /** "campfire" or "moonlight"; the theme's default when unset. */
    var themeId: String? get() = str("themeId"); set(v) = set("themeId", v?.let(::JsonPrimitive))
    var fontScale: String get() = str("fontScale") ?: "standard"; set(v) = set("fontScale", JsonPrimitive(v))
    /** "auto" (follows the phone's rotation setting), "portrait" or "landscape". */
    var screenOrientation: String get() = str("screenOrientation") ?: DEFAULT_ORIENTATION; set(v) = set("screenOrientation", JsonPrimitive(v))
    var languageId: String? get() = str("languageId"); set(v) = set("languageId", v?.let(::JsonPrimitive))
    var voiceInputEnabled: Boolean get() = bool("voiceInputEnabled") ?: true; set(v) = set("voiceInputEnabled", JsonPrimitive(v))
    var hapticsEnabled: Boolean get() = bool("hapticsEnabled") ?: false; set(v) = set("hapticsEnabled", JsonPrimitive(v))
    var hidePromptIdeas: Boolean get() = bool("hidePromptIdeas") ?: false; set(v) = set("hidePromptIdeas", JsonPrimitive(v))
    /**
     * Advanced tweaks → Show technical details (the Expo app's live-stats key): off, the chat shows the short receipt
     * only; on, the receipt's details, live tok/s in the header, the menu's memory and storage bars, Hardware telemetry.
     */
    // Off by default since 2.1.0 (BOAR-81): a researcher's chat stays clean; builders turn it on in Evaluation › Device
    // setup › Diagnostics. Pro installs kept theirs on ([retireProfiles]).
    var showLiveStats: Boolean get() = bool("showLiveStats") ?: false; set(v) = set("showLiveStats", JsonPrimitive(v))

    /** Display & theme → the live speed (tok/s) in the chat's header while BOAR answers; off by default (adam, 16 Nov), in every profile. */
    var showLiveSpeed: Boolean get() = bool("showLiveSpeed") ?: false; set(v) = set("showLiveSpeed", JsonPrimitive(v))

    /** Settings → Free memory when I leave BOAR (on by default): see [FreeMemory]. */
    var freeMemoryInBackground: Boolean get() = bool("freeMemoryInBackground") ?: true; set(v) = set("freeMemoryInBackground", JsonPrimitive(v))
    /** Advanced tweaks → Free memory after, in minutes (0 = immediately); null is Auto ([FreeMemory.AUTO_SECONDS], in seconds). */
    var freeMemoryAfterMinutes: Int? get() = int("freeMemoryAfterMinutes"); set(v) = set("freeMemoryAfterMinutes", v?.let(::JsonPrimitive))

    /** DEFAULT_MEMORY_SETTINGS when unset. */
    var autoSummarize: Boolean get() = bool("autoSummarize") ?: true; set(v) = set("autoSummarize", JsonPrimitive(v))
    var historyTurnThreshold: Int get() = int("historyTurnThreshold") ?: 6; set(v) = set("historyTurnThreshold", JsonPrimitive(v))
    /**
     * How many chats are kept; 0 keeps them all, the default (adam, Nov 15: "keep all chats by default"). Only Settings
     * writes it, so a stored value was the user's choice and stays; an install that never chose had 10 and now keeps all.
     */
    var maxSavedSessions: Int get() = int("maxSavedSessions") ?: KEEP_ALL_CHATS; set(v) = set("maxSavedSessions", JsonPrimitive(v))
    /** The menu's 🗑️ asks before deleting a chat (issue #92); off deletes in one tap. The user's call, on by default. */
    var confirmChatDelete: Boolean get() = bool("confirmChatDelete") ?: true; set(v) = set("confirmChatDelete", JsonPrimitive(v))
    var autoGenerateTitles: Boolean get() = bool("autoGenerateTitles") ?: true; set(v) = set("autoGenerateTitles", JsonPrimitive(v))
    /** The question picked on setup's "How to ask" page: in the chat's input once setup ends, not sent; then cleared. */
    var pendingFirstQuestion: String? get() = str("pendingFirstQuestion"); set(v) = set("pendingFirstQuestion", v?.takeIf { it.isNotBlank() }?.let(::JsonPrimitive))

    /**
     * Advanced tweaks → Sources layout (an experiment to compare on the phone, adam, Nov 10): "today" (the list under
     * the answer) or "above" (B+: the sources open above it). Pro's, like the other tweaks: below Pro it reads "today".
     */
    var sourcesLayout: String get() = str("sourcesLayout") ?: SOURCES_TODAY
        set(v) = set("sourcesLayout", v.takeIf { it != SOURCES_TODAY }?.let(::JsonPrimitive))

    /** Advanced tweaks → the menu's memory and storage bars (builds with ALLOW_DRAWER_STATS): off, a clean menu. */
    var showDrawerStats: Boolean get() = bool("showDrawerStats") ?: false; set(v) = set("showDrawerStats", JsonPrimitive(v))

    /** Advanced tweaks → Debug log (native only, builds with ALLOW_DEBUG_LOG): off until the user turns it on. */
    var debugLogEnabled: Boolean get() = bool("debugLogEnabled") ?: false; set(v) = set("debugLogEnabled", JsonPrimitive(v))

    /** Advanced tweaks (native only); all Auto when unset or unreadable. */
    var engineTweaks: EngineTweaks
        get() = storedTweaks
        set(v) = set("engineTweaks", if (v.isAuto) null else json.parseToJsonElement(v.toJson()))

    private val storedTweaks: EngineTweaks get() = EngineTweaks.fromJson(data["engineTweaks"]?.toString())

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

    /**
     * Models the user chose to load although rated RED for this phone ("Load anyway"): loaded at start without asking
     * again. Kept by "Reset settings", like the load ledger above.
     */
    var loadAnywayIds: List<String> get() = strings("loadAnywayIds"); set(v) = setStrings("loadAnywayIds", v.distinct())

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

    /** The Truffle Vault (engine/vault/Vault.State) as JSON; null before anything was counted. Native only. */
    var truffleVault: String?
        get() = data["truffleVault"]?.takeIf { it is JsonObject }?.toString()
        set(v) = set("truffleVault", v?.let { json.parseToJsonElement(it) })
    /** The equipped boar (launcher icon and in-app avatar); null is BOAR's own. Native only. */
    var iconId: String? get() = str("iconId"); set(v) = set("iconId", v?.let(::JsonPrimitive))

    fun raw(): JsonObject = data
}
