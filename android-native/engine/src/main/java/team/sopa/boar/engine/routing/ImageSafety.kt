package team.sopa.boar.engine.routing

import java.text.Normalizer

/**
 * What Boar Designer won't draw, checked on the words that reach the image model (the chat model's pick, the question
 * fallback, or what the user typed), in English and Portuguese. The image model (SDXS-512, one step) has no safety
 * checker of its own, and the chat model's "never…" instruction is a request, not a block. A check of the finished
 * image comes later (further developments #12).
 *
 * The text is normalized first: lower case, accents off, punctuation to spaces, and spaced-out letters joined ("n u d e",
 * "n.u.d.e"). Harmless phrases that contain a blocked word ("chicken breast", "gun dog", "blood orange") are taken out
 * before the check.
 */
object ImageSafety {
    /** What a blocked pick from the chat model (or the fallback) is drawn as instead. */
    const val SAFE_SUBJECT = "a calm landscape"

    private val HARMLESS = listOf(
        "breast cancer", "chicken breast", "chicken breasts", "turkey breast", "breaststroke", "gun dog", "glue gun", "nail gun",
        "water gun", "heat gun", "staple gun", "spray gun", "blood orange", "blood moon", "blood cell", "blood cells",
        "blood pressure", "blood type", "blood donation", "peito de frango", "cancer de mama", "laranja sanguinea",
        "pistola de cola", "pistola de agua", "great tit", "blue tit", "coal tit", "great tits", "blue tits", "shooting star",
        "shooting stars", "bath bomb", "bath bombs", "bomba de agua", "photo shoot",
    )

    private val BLOCKED = Regex(
        "\\b(" + listOf(
            // sexual / nudity, EN
            "nude", "nudes", "nudity", "naked", "nsfw", "porn\\w*", "sex", "sexy", "sexual\\w*", "erotic\\w*", "explicit",
            "genital\\w*", "penis\\w*", "vagina\\w*", "vulva", "nipples?", "boobs?", "tits", "breasts", "topless", "bottomless",
            "lingerie", "fetish\\w*", "bdsm", "hentai", "onlyfans", "orgasm\\w*", "masturbat\\w*", "rape\\w*", "incest",
            "strip ?tease", "stripper\\w*", "undress\\w*",
            // sexual / nudity, PT
            "nua", "nuas", "nus", "pelad[oa]s?", "sem roupa", "pornografi\\w*", "sexo", "erotic[oa]s?", "transar", "transando", "penis",
            "mamilos?", "seios", "estupro\\w*", "fetiche\\w*",
            // gore / violence, EN
            "gore", "gory", "bloody", "covered in blood", "pool of blood", "blood splatter\\w*", "decapitat\\w*", "behead\\w*",
            "dismember\\w*", "mutilat\\w*", "corpses?", "dead bod(?:y|ies)", "tortur\\w*", "suicide", "self harm", "murder\\w*",
            "killing", "massacre", "execution", "shooting",
            // gore / violence, PT
            "sangrent[oa]s?", "cadaver\\w*", "decapitad\\w*", "tortura\\w*", "suicidio", "assassin\\w*", "massacre", "execucao",
            "matanca",
            // weapons
            "weapons?", "guns?", "rifles?", "pistols?", "shotguns?", "firearms?", "bombs?", "grenades?", "explosives?",
            "armas?", "revolver\\w*", "pistolas?", "fuzil", "fuzis", "metralhadora\\w*", "granadas?", "bombas?",
        ).joinToString("|") + ")\\b",
    )

    /** Lower case, no accents, letters and digits only, spaced-out single letters joined, harmless phrases removed. */
    fun normalize(text: String): String {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        val words = plain.replace(Regex("[^a-z0-9]+"), " ").trim().split(' ').filter { it.isNotEmpty() }
        // "n u d e" → "nude": a run of two or more one-letter words becomes one word.
        val joined = ArrayList<String>()
        var run = StringBuilder()
        fun flush() { if (run.length >= 2) joined += run.toString() else if (run.isNotEmpty()) joined += run.toString(); run = StringBuilder() }
        for (w in words) if (w.length == 1) run.append(w) else { flush(); joined += w }
        flush()
        var s = " " + joined.joinToString(" ") + " "
        for (h in HARMLESS) s = s.replace(" $h ", " ")
        return s.trim()
    }

    /** True when the words ask for something Boar Designer won't draw. */
    fun blocked(text: String): Boolean = BLOCKED.containsMatchIn(normalize(text))

    /** A subject picked by the chat model or the fallback: the same words, or [SAFE_SUBJECT] when they're blocked. */
    fun safeSubject(subject: String): String = if (blocked(subject)) SAFE_SUBJECT else subject
}
