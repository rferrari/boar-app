package team.sopa.boar.ui.about

/** What About's version block copies on press and hold: plain text for a bug report. */
internal object AboutDebug {
    /** [labs]: "id status" per enabled lab. */
    fun report(version: String, device: String, android: String, sdk: Int, labs: List<String>): String = buildString {
        appendLine("BOAR $version")
        appendLine("Device: $device · Android $android (API $sdk)")
        append("Plugins (${labs.size}): ").append(if (labs.isEmpty()) "none" else labs.joinToString(", "))
    }
}
