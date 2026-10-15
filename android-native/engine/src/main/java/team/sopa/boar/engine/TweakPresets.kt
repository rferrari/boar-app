package team.sopa.boar.engine

/**
 * One-tap sets of [EngineTweaks] at the top of Advanced tweaks. Picking one sets every knob it names; changing any knob
 * by hand afterwards makes it Custom. The CPU build is the phone's own choice and no preset touches it.
 *
 * Boar's Path is Auto. The others lean one way from it, within what the measurements allow: more threads is not more
 * power on big.LITTLE phones (8 threads ran at half the speed of 4 on the X6), so Run Boar Run keeps Auto's threads on
 * the fast cores and spends the rest (bigger prompt batches, no time limit) instead.
 */
enum class TweakPreset(val emoji: String, private val values: EngineTweaks) {
    /**
     * Fastest answers: pinned to the fast cores, prompts read in bigger batches, no time limit. It also asks for the weights
     * locked in RAM, which Android refuses an app (0 MB locked, X6, Oct 15): harmless, so the prompt speed is the gain
     * (first word 10.9 s against 21.7 s on Boar's Path, X6).
     */
    RUN("🐗", EngineTweaks(strictCpu = true, batch = 1024, microBatch = 512, loadMode = LoadMode.MMAP_MLOCK, stageTimeoutSeconds = 0)),
    /** Auto: what we measured best on real phones. */
    PATH("🌲", EngineTweaks()),
    /** Cooler and easier on the battery: 2 threads, a smaller context, no reasoning, a 5-minute limit. */
    FORAGE("🍄", EngineTweaks(threads = 2, threadsBatch = 2, contextTokens = 2048, thinking = false, stageTimeoutSeconds = 300)),
    /** For a hot phone: 1 thread to generate, small prompt batches, a smaller context, no reasoning, a 5-minute limit. */
    COOL("🧊", EngineTweaks(threads = 1, threadsBatch = 2, contextTokens = 2048, batch = 128, microBatch = 64, thinking = false, stageTimeoutSeconds = 300)),
    ;

    /** This preset's tweaks, keeping [current]'s CPU build. */
    fun applyTo(current: EngineTweaks): EngineTweaks = values.copy(cpuBuild = current.cpuBuild)

    /** The string key stem: native.tweaks.preset.<key>. */
    val key: String get() = name.lowercase()

    companion object {
        /** The preset [tweaks] equal, or null when they are Custom. */
        fun of(tweaks: EngineTweaks): TweakPreset? = entries.firstOrNull { it.applyTo(tweaks) == tweaks }
    }
}
