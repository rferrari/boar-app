package team.sopa.boar.engine.models

/**
 * The pages setup walks through while it downloads (Oct 23): the user makes BOAR theirs and learns to use it in the
 * same flow, instead of waiting on a progress bar. Every page can be skipped; what it sets is saved as it's chosen.
 */
object SetupGuide {
    /**
     * In this order (Oct 24, adam's test on the X6): the files come before the answer modes, so Personal Files is taught
     * with files in hand; the first question has its own page.
     */
    enum class Page { LOOK, FILES, ANSWERS, ASK, FIRST, PIN, PRIVACY, LABS, READY }

    /**
     * The pages in order. [pinSetBefore]: a PIN was already set when this setup started (setup run again from Settings
     * → Recovery), so there's nothing to offer; a PIN set on the PIN page keeps the page, so the list doesn't shift
     * under the user. [experimental]: the Boar Labs page, only in builds with the Experimental section.
     */
    fun pages(experimental: Boolean, pinSetBefore: Boolean): List<Page> = Page.entries.filter {
        when (it) {
            Page.PIN -> !pinSetBefore
            Page.LABS -> experimental
            else -> true
        }
    }

    /**
     * The pages and the page index of a setup that resumes in a new process. [pinPage]: whether its pages had the PIN
     * page when it was saved (null: saved by a build without it). A PIN set on that page must not drop the page now,
     * or every page after it would shift by one. [page]: the saved page's name; an unknown one starts over.
     */
    fun resume(experimental: Boolean, pinPage: Boolean?, pinSetNow: Boolean, page: String?): Pair<List<Page>, Int> {
        val pages = pages(experimental, pinSetBefore = pinPage?.not() ?: pinSetNow)
        return pages to maxOf(0, pages.indexOfFirst { it.name == page })
    }

    /**
     * Whether files picked during setup can be imported now. They need the search model to embed them; and not while
     * the index builds, which runs its own copy of the search model over the same database: both at once would slow
     * the index and hold the model twice. Nor twice at once.
     */
    fun canImport(searchModelPresent: Boolean, indexBuilding: Boolean, importing: Boolean): Boolean =
        searchModelPresent && !indexBuilding && !importing

    /** The picked files not in the last finished import: an import is needed while this isn't empty. */
    fun pending(onDisk: List<String>, imported: List<String>): List<String> = onDisk.filter { it !in imported }

    /**
     * A name for a copied file that doesn't overwrite another picked one: "notes.txt", then "notes (2).txt".
     * [taken]: the names already in the folder.
     */
    fun freeName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        return generateSequence(2) { it + 1 }.map { "$base ($it)$ext" }.first { it !in taken }
    }
}
