package team.sopa.boar.engine.labs

/** Boar Vitals' readings, parsed off the phone's own files and battery extras so they can be tested. */
object Vitals {
    enum class Heat { UNKNOWN, COOL, WARM, HOT }

    /** Below 38 °C cool, below 42 °C warm, above that hot (the battery's own temperature). */
    fun heat(tempC: Double?): Heat = when {
        tempC == null -> Heat.UNKNOWN
        tempC < 38 -> Heat.COOL
        tempC < 42 -> Heat.WARM
        else -> Heat.HOT
    }

    /** A cpufreq file (kHz, e.g. "1804800\n") as MHz; null when it's missing or not a number. */
    fun mhzOf(cpufreqText: String?): Int? = cpufreqText?.trim()?.toIntOrNull()?.takeIf { it >= 0 }?.div(1000)

    /** The core ids under /sys/devices/system/cpu: "cpu0".."cpuN" only (not cpufreq, cpuidle), in order, gaps kept. */
    fun coreIds(names: List<String>): List<Int> = names.mapNotNull { n -> Regex("cpu(\\d+)").matchEntire(n)?.groupValues?.get(1)?.toInt() }.sorted()

    /** Battery percent from Android's level and scale extras; null when either is missing. */
    fun batteryPercent(level: Int, scale: Int): Int? = if (level >= 0 && scale > 0) level * 100 / scale else null

    /** The battery temperature extra (tenths of a degree) in °C; null when it's missing ([Int.MIN_VALUE]). */
    fun batteryTempC(tenths: Int): Double? = tenths.takeIf { it != Int.MIN_VALUE }?.div(10.0)
}
