package team.sopa.boar.engine.models

/**
 * Setup's knowledge step (rc3, spec §7, adam: option b): the two packages looked the same (5.2 GB and 5.4 GB) because the
 * models are almost all of the download; the real difference is the Wikipedia Vital Articles pack (about 164 MB). So
 * everyone with the space gets the Encyclopedia without being asked, and the choice shows only when it doesn't fit,
 * with the difference spelled out.
 */
object SetupKnowledge {
    /** No choice to make: the Encyclopedia fits the free space ([encyclopediaShortfall] 0). Unknown space asks. */
    fun skipChoice(encyclopediaShortfall: Long?): Boolean = encyclopediaShortfall == 0L

    /** What the Encyclopedia adds over Essential: its packs Essential doesn't have, by their catalog sizes. */
    fun extraBytes(essential: List<CatalogAsset>, encyclopedia: List<CatalogAsset>): Long =
        encyclopedia.filter { e -> essential.none { it.id == e.id } }.sumOf { it.sizeBytes }
}
