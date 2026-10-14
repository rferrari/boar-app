package team.sopa.boar.app

import java.io.File

/**
 * The memory BOAR holds right now, as the drawer, Settings, Device Pulse and the debug log show it: this process plus
 * the engines it started as their own processes (a Labs engine like Big Boar's bmoe, Image Gen's sd-cli). Counting only
 * this process read 0.1 GB while Big Boar's engine held 5.1 GB (X6, Oct 14).
 *
 * VmRSS: everything resident, the model's pages included. RssAnon alone (heap and KV cache) read about 70 MB with the
 * 4B loaded (X6, Oct 11): its weights are mapped from the file, so they count as file pages. Those can drop when
 * Android needs the room, so the number moves a little after a load; that is what the app really holds.
 */
object AppMemory {
    /** One process BOAR runs: [name] is [APP] for this one, else the engine ("bmoe", "sd-cli", "colibri"). */
    data class Part(val name: String, val bytes: Long)

    /**
     * The breakdown, this process first. [mappedFileBytes]: the part of this process that is file pages (a model the
     * built-in engine maps from storage, plus code), which Android drops first when it needs the room.
     */
    data class Breakdown(val parts: List<Part>, val mappedFileBytes: Long) {
        val totalBytes: Long get() = parts.sumOf { it.bytes }
        val appBytes: Long get() = parts.firstOrNull { it.name == APP }?.bytes ?: 0L
        val engines: List<Part> get() = parts.filter { it.name != APP }
    }

    /** Everything BOAR holds: this process and its engine processes. */
    fun residentBytes(): Long = breakdown().totalBytes

    fun breakdown(): Breakdown {
        val self = File("/proc/self/status")
        return Breakdown(listOf(Part(APP, kb(self, "VmRSS:"))) + children(), kb(self, "RssFile:"))
    }

    /**
     * BOAR's child processes (same user, so their /proc entries are readable), found by parent id. Their VmRSS includes
     * mapped model pages: BigMoeOnEdge's streamed experts count while they're resident.
     */
    private fun children(): List<Part> {
        val me = android.os.Process.myPid()
        return File("/proc").listFiles { f -> f.name.all(Char::isDigit) }.orEmpty().mapNotNull { dir ->
            runCatching {
                // pid (comm) state ppid …: comm can hold spaces, so read after the last ')'.
                val ppid = File(dir, "stat").readText().substringAfterLast(')').trim().split(' ')[1].toInt()
                if (ppid != me) null
                else Part(engineName(File(dir, "cmdline").readText().substringBefore('\u0000').substringAfterLast('/')), kb(File(dir, "status"), "VmRSS:"))
            }.getOrNull()
        }.filter { it.bytes > 0 }
    }

    /** "libbmoe_cli.so" → "bmoe", "libsdcli_i8mm.so" → "sd-cli", "libcolibri_olmoe.so" → "colibri". */
    fun engineName(executable: String): String = when {
        executable.startsWith("libbmoe") -> "bmoe"
        executable.startsWith("libsdcli") -> "sd-cli"
        executable.startsWith("libcolibri") -> "colibri"
        else -> executable.removePrefix("lib").removeSuffix(".so")
    }

    private fun kb(status: File, key: String): Long = runCatching {
        status.readLines().first { it.startsWith(key) }.split(Regex("\\s+"))[1].toLong() * 1024
    }.getOrDefault(0L)

    const val APP = "app"
}
