package team.sopa.boar.app

import java.io.File

/** The memory BOAR holds right now, as the drawer and Settings show it. */
object AppMemory {
    /**
     * VmRSS: everything resident, the model's pages included. RssAnon alone (heap and KV cache) read about 70 MB with
     * the 4B loaded (X6, Oct 11): its weights are mapped from the file, so they count as file pages. Those can drop
     * when Android needs the room, so the number moves a little after a load; that is what the app really holds.
     */
    fun residentBytes(): Long = runCatching {
        File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }.split(Regex("\\s+"))[1].toLong() * 1024
    }.getOrDefault(0L)
}
