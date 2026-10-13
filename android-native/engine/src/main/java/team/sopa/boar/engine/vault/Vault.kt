package team.sopa.boar.engine.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * The Truffle Vault's rules: which boar icon ("truffle") unlocks when, from facts the app counts on the phone. Nothing
 * here reads the phone or the clock; the app (appearance/TruffleVault) feeds the facts and stores [State].
 */
object Vault {
    /** What the vault knows about how BOAR has been used, all counted on the phone and never sent anywhere. */
    @Serializable
    data class Facts(
        val answers: Int = 0,
        /** Answers that cited at least one source. */
        val sourcedAnswers: Int = 0,
        /** Answers given with no network at all. */
        val offlineAnswers: Int = 0,
        /** Answers asked between 02:00 and 04:00 local time. */
        val nightAnswers: Int = 0,
        val deepResearch: Int = 0,
        val evalRuns: Int = 0,
        val evalShares: Int = 0,
        val chats: Int = 0,
        val documents: Int = 0,
        val exports: Int = 0,
        val packs: Int = 0,
        /** Models added from Hugging Face (not in BOAR's catalog). */
        val hfModels: Int = 0,
        val bestTokPerSec: Double = 0.0,
        val voice: Boolean = false,
        val portuguese: Boolean = false,
        val pinOn: Boolean = false,
        val themeChanged: Boolean = false,
        val tweaksChanged: Boolean = false,
        /** Not signed with BOAR's release key: someone built it from source. */
        val sourceBuild: Boolean = false,
        /** GrapheneOS, or no Google Play services at all. */
        val degoogled: Boolean = false,
        /** ISO dates (yyyy-MM-dd) the app was opened, newest last. */
        val usedDays: List<String> = emptyList(),
        /** ISO dates the app was opened in airplane mode, newest last. */
        val airplaneDays: List<String> = emptyList(),
    )

    /** Unlock rules. Each truffle has one; the app shows the rule's clue while the truffle is locked. */
    enum class Rule(val test: (Facts, LocalDate) -> Boolean) {
        ALWAYS({ _, _ -> true }),
        FIRST_ANSWER({ f, _ -> f.answers >= 1 }),
        FIRST_SOURCED({ f, _ -> f.sourcedAnswers >= 1 }),
        ANSWERS_10({ f, _ -> f.answers >= 10 }),
        ANSWERS_50({ f, _ -> f.answers >= 50 }),
        ANSWERS_100({ f, _ -> f.answers >= 100 }),
        ANSWERS_500({ f, _ -> f.answers >= 500 }),
        ANSWERS_1000({ f, _ -> f.answers >= 1000 }),
        FIRST_OFFLINE({ f, _ -> f.offlineAnswers >= 1 }),
        OFFLINE_10({ f, _ -> f.offlineAnswers >= 10 }),
        NIGHT({ f, _ -> f.nightAnswers >= 1 }),
        NIGHT_5({ f, _ -> f.nightAnswers >= 5 }),
        DEEP_RESEARCH({ f, _ -> f.deepResearch >= 1 }),
        DEEP_RESEARCH_10({ f, _ -> f.deepResearch >= 10 }),
        EVAL_RUN({ f, _ -> f.evalRuns >= 1 }),
        EVAL_RUNS_5({ f, _ -> f.evalRuns >= 5 }),
        EVAL_SHARED({ f, _ -> f.evalShares >= 1 }),
        CHATS_10({ f, _ -> f.chats >= 10 }),
        CHATS_100({ f, _ -> f.chats >= 100 }),
        DOCUMENT({ f, _ -> f.documents >= 1 }),
        EXPORT({ f, _ -> f.exports >= 1 }),
        PACK({ f, _ -> f.packs >= 1 }),
        PACKS_2({ f, _ -> f.packs >= 2 }),
        HF_MODEL({ f, _ -> f.hfModels >= 1 }),
        FAST_8({ f, _ -> f.bestTokPerSec >= 8.0 }),
        FAST_15({ f, _ -> f.bestTokPerSec > 15.0 }),
        VOICE({ f, _ -> f.voice }),
        PORTUGUESE({ f, _ -> f.portuguese }),
        PIN({ f, _ -> f.pinOn }),
        PIN_DARK({ f, d -> f.pinOn && Days.streak(f.airplaneDays, d) >= 1 }),
        THEME({ f, _ -> f.themeChanged }),
        TWEAKS({ f, _ -> f.tweaksChanged }),
        SOURCE_BUILD({ f, _ -> f.sourceBuild }),
        DEGOOGLED({ f, _ -> f.degoogled }),
        DAYS_3({ f, _ -> f.usedDays.size >= 3 }),
        DAYS_7({ f, _ -> f.usedDays.size >= 7 }),
        DAYS_30({ f, _ -> f.usedDays.size >= 30 }),
        AIRPLANE_STREAK_3({ f, d -> Days.streak(f.airplaneDays, d) >= 3 }),
        AIRPLANE_STREAK_7({ f, d -> Days.streak(f.airplaneDays, d) >= 7 }),
        AIRPLANE_DAYS_14({ f, _ -> f.airplaneDays.size >= 14 }),
    }

    /** Each truffle's rule, in the vault's order (art/icons/truffles.json has the same ids). */
    val RULES: Map<String, Rule> = linkedMapOf(
        "cosmo" to Rule.FIRST_ANSWER,
        "quantum" to Rule.FAST_15,
        "tactical" to Rule.ALWAYS,
        "hacker" to Rule.SOURCE_BUILD,
        "solarpunk" to Rule.ANSWERS_10,
        "nomad" to Rule.AIRPLANE_STREAK_7,
        "synthetic" to Rule.HF_MODEL,
        "senior" to Rule.DEEP_RESEARCH,
        "mecha" to Rule.EVAL_RUN,
        "plasma" to Rule.VOICE,
        "steampunk" to Rule.DAYS_3,
        "retrowave" to Rule.PORTUGUESE,
        "ice" to Rule.AIRPLANE_STREAK_3,
        "volcanic" to Rule.FAST_8,
        "astronaut" to Rule.DAYS_7,
        "stealth" to Rule.DEGOOGLED,
        "biolum" to Rule.NIGHT_5,
        "knight" to Rule.EVAL_SHARED,
        "void" to Rule.CHATS_10,
        "atomic" to Rule.EVAL_RUNS_5,
        "lunar" to Rule.NIGHT,
        "digital_nomad" to Rule.DOCUMENT,
        "chrono" to Rule.DAYS_30,
        "ethereal" to Rule.ANSWERS_500,
        "sovereign" to Rule.ANSWERS_1000,
        "guardian" to Rule.FIRST_SOURCED,
        "tactical_city" to Rule.PIN,
        "pixel_hacker" to Rule.THEME,
        "moe" to Rule.PACKS_2,
        "explorer" to Rule.FIRST_OFFLINE,
        "spirit" to Rule.DEEP_RESEARCH_10,
        "planter" to Rule.ANSWERS_50,
        "cloud" to Rule.OFFLINE_10,
        "elder" to Rule.EXPORT,
        "fomo_unicorn" to Rule.CHATS_100,
        "tactical_ii" to Rule.PIN_DARK,
        "totem" to Rule.PACK,
        "hammock" to Rule.ANSWERS_100,
        "snow" to Rule.AIRPLANE_DAYS_14,
        "hacker_ii" to Rule.TWEAKS,
    )

    /** Everything the vault stores: the facts, when each truffle was unearthed (epoch ms), and themes plugins granted. */
    @Serializable
    data class State(
        val facts: Facts = Facts(),
        val unlocked: Map<String, Long> = emptyMap(),
        val themes: Map<String, Long> = emptyMap(),
    )

    /** [state] with [facts] and every truffle they newly unlock, stamped [now]; also the ids that are new. */
    fun apply(state: State, facts: Facts, today: LocalDate, now: Long): Pair<State, List<String>> {
        val fresh = RULES.filter { (id, rule) -> id !in state.unlocked && rule.test(facts, today) }.keys.toList()
        return state.copy(facts = facts, unlocked = state.unlocked + fresh.associateWith { now }) to fresh
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    fun toJson(state: State): String = json.encodeToString(State.serializer(), state)
    fun fromJson(text: String?): State = text?.let { runCatching { json.decodeFromString(State.serializer(), it) }.getOrNull() } ?: State()

    /** Day lists: distinct ISO dates, oldest first, at most [KEEP]. */
    object Days {
        const val KEEP = 64

        fun add(days: List<String>, day: LocalDate): List<String> =
            (days + day.toString()).distinct().sorted().takeLast(KEEP)

        /** Days in a row that end today (or yesterday, so a streak isn't lost before the first open of the day). */
        fun streak(days: List<String>, today: LocalDate): Int {
            val set = days.toHashSet()
            var d = if (today.toString() in set) today else today.minusDays(1)
            var n = 0
            while (d.toString() in set) { n++; d = d.minusDays(1) }
            return n
        }
    }

    /** 02:00 to 04:00 local time. */
    fun isNight(hour: Int): Boolean = hour in 2..3
}
