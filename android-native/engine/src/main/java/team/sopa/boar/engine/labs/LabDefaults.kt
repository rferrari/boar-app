package team.sopa.boar.engine.labs

import team.sopa.boar.engine.settings.UserProfile

/**
 * Which Boar Labs start on (rc3, adam, Nov 10), replacing rc2's "every lab starts off". A lab the user never switched
 * keeps following this list; a switch the user flipped is kept as it is.
 */
object LabDefaults {
    /** adam's default-on list, without the ALPHA labs still waiting for tests and a phone pass ([PENDING_ALPHA]). */
    val ON: Set<String> = setOf("pulse", "image", "printer", "polyglot", "calc", "distance", "facts", "cohazard")

    /**
     * On adam's list but ALPHA: they start off until each has tests and a device pass, or is raised to BETA, or adam
     * writes an exception (t9, Nov 10). Moving one into [ON] is the whole change.
     */
    val PENDING_ALPHA: Set<String> = setOf("compass", "lunar", "staged", "guard")

    /** The switch a lab shows: the user's own choice when there is one, else the default. */
    fun switchOf(id: String, stored: Boolean?): Boolean = stored ?: (id in ON)

    /**
     * Boar Labs' own places (the menu item, the list, the setup page, the tour card, its settings) are Pro's. A lab
     * that is on keeps working in every profile: its rows under answers and its header badge are not gated.
     */
    fun managementShown(profile: UserProfile): Boolean = profile == UserProfile.PRO
}
