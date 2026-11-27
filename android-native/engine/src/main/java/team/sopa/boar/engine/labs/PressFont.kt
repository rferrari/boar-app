package team.sopa.boar.engine.labs

/**
 * The document's font (adam, 27 Nov: "can we add 3 fonts to be selected by user?"): the phone's own faces, so nothing
 * is downloaded and the PDF stays offline. Serif is how Press always printed.
 */
enum class PressFont(val key: String) {
    SERIF("serif"), SANS("sans"), MONO("mono");

    companion object {
        /** A saved key back to its font; Serif for none or one this build doesn't know. */
        fun of(key: String?): PressFont = entries.firstOrNull { it.key == key } ?: SERIF
    }
}
