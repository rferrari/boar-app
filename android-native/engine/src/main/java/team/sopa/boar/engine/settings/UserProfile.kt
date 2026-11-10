package team.sopa.boar.engine.settings

/**
 * Who BOAR is set up for (rc3, adam, Nov 10): Simple shows only the settings everyone needs, Explorer adds how BOAR
 * answers and what it keeps, Pro adds the technical corner. Each profile shows everything the one before it does.
 * A fresh install starts on [SIMPLE]; an rc2 install gets its profile once from [UserProfile.forUpdate].
 */
enum class UserProfile(val key: String) {
    SIMPLE("simple"), EXPLORER("explorer"), PRO("pro");

    fun includes(other: UserProfile) = ordinal >= other.ordinal

    companion object {
        val DEFAULT = SIMPLE
        fun of(key: String?): UserProfile? = entries.firstOrNull { it.key == key }

        /**
         * The profile for an install that has settings from before profiles (rc2): the lowest one that still shows
         * everything the user changed, so nothing they chose is hidden or switched off. [proChanged]: something only
         * Pro shows (an Advanced tweak, adaptive routing, the free-memory delay, the menu's bars, the debug log, a lab
         * switched on). [explorerChanged]: something Explorer shows (a style, an answer length, rotation, haptics,
         * voice off, a chat-history option, Boarverse on). Technical details on (rc2's default) is not a sign.
         */
        fun forUpdate(proChanged: Boolean, explorerChanged: Boolean): UserProfile = when {
            proChanged -> PRO
            explorerChanged -> EXPLORER
            else -> SIMPLE
        }
    }
}

/**
 * Each Settings section, or row inside one, and the first profile that shows it. A part not listed here would show
 * for nobody, so every part has a profile. Anything not proven on a phone stays out of Simple.
 */
enum class SettingsPart(val from: UserProfile) {
    PROFILE(UserProfile.SIMPLE),
    LANGUAGE(UserProfile.SIMPLE),
    THEME(UserProfile.SIMPLE),
    TEXT_SIZE(UserProfile.SIMPLE),
    SECURITY(UserProfile.SIMPLE),
    MODELS(UserProfile.SIMPLE),
    RECOVERY(UserProfile.SIMPLE),

    TONE(UserProfile.EXPLORER),
    KNOWLEDGE(UserProfile.EXPLORER),
    MEMORY(UserProfile.EXPLORER),
    VOICE(UserProfile.EXPLORER),
    AVATARS(UserProfile.EXPLORER),
    BOARVERSE_SWITCH(UserProfile.EXPLORER),
    ORIENTATION(UserProfile.EXPLORER),
    HAPTICS(UserProfile.EXPLORER),

    TWEAKS(UserProfile.PRO),
    TECHNICAL_DETAILS(UserProfile.PRO),
    TELEMETRY(UserProfile.PRO),
    LABS_SETTINGS(UserProfile.PRO),
    ADAPTIVE_ROUTING(UserProfile.PRO);

    fun shownIn(profile: UserProfile) = profile.includes(from)

    companion object {
        /** The Experimental section shows when any of its rows does. */
        val EXPERIMENTAL_ROWS = listOf(BOARVERSE_SWITCH, LABS_SETTINGS, ADAPTIVE_ROUTING)
        fun experimentalShownIn(profile: UserProfile) = EXPERIMENTAL_ROWS.any { it.shownIn(profile) }
    }
}
