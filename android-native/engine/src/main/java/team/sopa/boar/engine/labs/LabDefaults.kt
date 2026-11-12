package team.sopa.boar.engine.labs

import team.sopa.boar.engine.settings.UserProfile

/**
 * Which Boar Labs start on (rc3, adam, Nov 10), replacing rc2's "every lab starts off". A lab the user never switched
 * keeps following this list; a switch the user flipped is kept as it is.
 */
object LabDefaults {
    /**
     * adam's default-on list, without the ALPHA labs still waiting for tests and a phone pass ([PENDING_ALPHA]).
     * Polyglot left it on Nov 11 (adam): off by default until it's redesigned; it stays in the list to switch on.
     */
    val ON: Set<String> = setOf("pulse", "image", "printer", "calc", "distance", "facts", "cohazard")

    /**
     * On adam's list but ALPHA: they start off until each has tests and a device pass, or is raised to BETA, or adam
     * writes an exception (t9, Nov 10). Moving one into [ON] is the whole change.
     */
    val PENDING_ALPHA: Set<String> = setOf("compass", "lunar", "guard")

    /**
     * The labs an update switched on: default-on labs the user never switched (no stored choice), in [ON]'s order of
     * [ids]. The one-time update note names them, so nobody finds a lab on without being told.
     */
    fun turnedOnByUpdate(ids: List<String>, stored: (String) -> Boolean?): List<String> = ids.filter { it in ON && stored(it) == null }

    /**
     * The user switched a lab on themselves (a stored true), counted only while Boar Labs is on and built: a switch
     * left on behind a master switch that's off shows nowhere, so it must not make the install Pro.
     */
    fun userChoseOn(labsOn: Boolean, stored: Iterable<Boolean?>): Boolean = labsOn && stored.any { it == true }

    /** The switch a lab shows: the user's own choice when there is one, else the default. */
    fun switchOf(id: String, stored: Boolean?): Boolean = stored ?: (id in ON)

    /**
     * Boar Labs' own places (the menu item, the list, the setup page, the tour card, its settings) are Pro's. A lab
     * that is on keeps working in every profile: its rows under answers and its header badge are not gated.
     */
    fun managementShown(profile: UserProfile): Boolean = profile == UserProfile.PRO
}
