package team.sopa.boar.engine.models

/** The Models screen's sort and filter: which answer models show, in what order. Pure, so it's unit-tested. */
object ModelOrder {
    /**
     * SMART: active, on the phone by last use, then the rest. LAST_USED and ADDED: newest first. Under every sort and
     * filter a model that is downloading comes first (adam, Nov 13: "downloading models should always be displayed on
     * top to easy followup"), so there is no Downloading filter any more.
     */
    enum class Sort { SMART, LAST_USED, ADDED }
    /** MINE: models added from the Hugging Face search. */
    enum class Filter { ALL, ON_PHONE, MINE }

    /** What the screen knows about each model id; times are epoch ms (missing: never used or not known). */
    data class Facts(
        val active: String? = null,
        val onPhone: Set<String> = emptySet(),
        val downloading: Set<String> = emptySet(),
        val mine: Set<String> = emptySet(),
        val lastUsed: Map<String, Long> = emptyMap(),
        val added: Map<String, Long> = emptyMap(),
    )

    /** [items] filtered and sorted; ties and models without a time keep their catalog order. */
    fun apply(items: List<CatalogAsset>, sort: Sort, filter: Filter, f: Facts): List<CatalogAsset> {
        val shown = items.filter {
            it.id in f.downloading || when (filter) {
                Filter.ALL -> true
                Filter.ON_PHONE -> it.id in f.onPhone
                Filter.MINE -> it.id in f.mine
            }
        }
        val sorted = when (sort) {
            Sort.SMART -> shown.sortedWith(
                compareBy<CatalogAsset> {
                    when {
                        it.id == f.active -> 0
                        it.id in f.onPhone -> 2
                        else -> 3
                    }
                }.thenByDescending { if (it.id in f.onPhone) f.lastUsed[it.id] ?: 0L else 0L },
            )
            Sort.LAST_USED -> shown.sortedByDescending { f.lastUsed[it.id] ?: Long.MIN_VALUE }
            Sort.ADDED -> shown.sortedByDescending { f.added[it.id] ?: Long.MIN_VALUE }
        }
        return downloadsFirst(sorted, f.downloading)
    }

    /** The models in [downloads] first, in their order in [list], then the rest; a stable partition. */
    fun <T> downloadsFirst(list: List<T>, downloads: Set<String>, id: (T) -> String): List<T> =
        list.partition { id(it) in downloads }.let { (d, rest) -> d + rest }

    private fun downloadsFirst(list: List<CatalogAsset>, downloads: Set<String>) = downloadsFirst(list, downloads) { it.id }
}
