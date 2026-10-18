package team.sopa.boar.engine.models

/** The Models screen's sort and filter: which answer models show, in what order. Pure, so it's unit-tested. */
object ModelOrder {
    /** SMART: active, downloading, on the phone by last use, then the rest. LAST_USED and ADDED: newest first. */
    enum class Sort { SMART, LAST_USED, ADDED }
    /** MINE: models added from the Hugging Face search. */
    enum class Filter { ALL, ON_PHONE, DOWNLOADING, MINE }

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
            when (filter) {
                Filter.ALL -> true
                Filter.ON_PHONE -> it.id in f.onPhone
                Filter.DOWNLOADING -> it.id in f.downloading
                Filter.MINE -> it.id in f.mine
            }
        }
        return when (sort) {
            Sort.SMART -> shown.sortedWith(
                compareBy<CatalogAsset> {
                    when {
                        it.id == f.active -> 0
                        it.id in f.downloading -> 1
                        it.id in f.onPhone -> 2
                        else -> 3
                    }
                }.thenByDescending { if (it.id in f.onPhone) f.lastUsed[it.id] ?: 0L else 0L },
            )
            Sort.LAST_USED -> shown.sortedByDescending { f.lastUsed[it.id] ?: Long.MIN_VALUE }
            Sort.ADDED -> shown.sortedByDescending { f.added[it.id] ?: Long.MIN_VALUE }
        }
    }
}
