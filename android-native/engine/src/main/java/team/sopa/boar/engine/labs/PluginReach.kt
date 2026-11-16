package team.sopa.boar.engine.labs

/**
 * How an off plugin stays reachable, and the safety plugin that can't go with one tap (validator Q-B1; t9 0115). Below
 * Pro, a plugin switched off from its own page used to vanish: the header's sheet listed only plugins that are on, its
 * icon went when none was, and the Plugins list is Pro's. Cohazard, the only source of the acute carbon monoxide card,
 * could go that way with one tap. Pure, so the decisions are tested as behaviour, not as source strings.
 */
object PluginReach {
    /** Plugins that deliver a safety card: on below Pro; off only in Pro, after a question. */
    val SAFETY: Set<String> = setOf("cohazard")

    /**
     * The page's on/off switch: every plugin's in Pro; below Pro a safety plugin's only while it is off (so it can come
     * back on, never go).
     */
    fun pageSwitchShown(id: String, on: Boolean, pro: Boolean): Boolean = pro || id !in SAFETY || !on

    /** Turning a safety plugin off asks first ("Turn off the carbon monoxide safety card?"), from its page or the list. */
    fun asksBeforeOff(id: String, turningOn: Boolean): Boolean = !turningOn && id in SAFETY

    /** The plugins switched off from their pages after a switch: on leaves the set, off from a page joins it. */
    fun offFromPageAfter(set: Set<String>, id: String, on: Boolean, fromPage: Boolean): Set<String> =
        if (on) set - id else if (fromPage) set + id else set

    /**
     * The header sheet's "Off" section, in [ids]' order: each off plugin the user switched off from its page, and any
     * safety plugin that is off, so either is one tap from its page and its switch.
     */
    fun offListed(ids: List<String>, enabled: (String) -> Boolean, offFromPage: Set<String>): List<String> =
        ids.filter { !enabled(it) && (it in offFromPage || it in SAFETY) }

    /** The header's plugin icon: while any plugin is on, or one is listed as off. */
    fun headerIconShown(activeIds: List<String>, offListed: List<String>): Boolean = activeIds.isNotEmpty() || offListed.isNotEmpty()
}
