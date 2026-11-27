package team.sopa.boar.engine.labs

/**
 * "Draw me…" in the chat (adam, 26 Nov, t9 2008): a message that STARTS with a draw request is answered by Designer, not
 * the model, which would only make up "here is your image". English, Portuguese and Spanish. Only at the start, and
 * "draw" used another way ("draw a conclusion", "draw the line") stays with the model, as does a how-to ("como desenhar").
 * The replies are written here in the request's own language, as Lunar's and Vitals' are.
 */
object DrawRequest {
    enum class Lang { EN, PT, ES }

    /** [subject]: what to draw, as the user wrote it; [lang]: the request's language (the subject's too, most likely). */
    data class Ask(val subject: String, val lang: Lang)

    /** Designer draws it; it is off; its model isn't on the phone; the subject is refused (ImageSafety). */
    const val TOOL = "designer-draw"
    const val TOOL_OFF = "designer-off"
    const val TOOL_NO_MODEL = "designer-no-model"
    const val TOOL_REFUSED = "designer-refused"

    // DOT_MATCHES_ALL: a subject may run over lines ("draw me a cat\nwearing a hat"); the ^ anchor stays the message start.
    private val O = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val POLITE = Regex("^(?:please|pls|por favor|porfa)[,!\\s]+", O)
    private val POLITE_END = Regex("[,\\s]+(?:please|por favor)$", O)

    private const val NOUN_EN = "(?:image|picture|drawing|illustration|painting|photo|sketch|portrait)"
    /** "create an image of…", "make me a drawing of…", "paint a picture of…". */
    private val EN_OF = Regex("^(?:create|generate|make|produce|render|paint|draw)\\s+(?:me\\s+|us\\s+)?(?:an?\\s+)?$NOUN_EN\\s+of\\s+(.+)$", O)
    /** "draw me…", "sketch me…", "paint me…". */
    private val EN_ME = Regex("^(?:draw|sketch|paint)\\s+(?:me|us)\\s+(.+)$", O)
    /** "draw a…", "draw an…", unless it's "draw a conclusion" and the like. */
    private val EN_A = Regex("^draw\\s+((?:a|an)\\s+(\\p{L}+).*)$", O)
    private val NOT_A_PICTURE = setOf("conclusion", "conclusions", "distinction", "comparison", "parallel", "blank", "breath", "card", "lesson",
        "line", "crowd", "salary", "inference", "analogy", "contrast", "veil", "bath", "check", "cheque", "lot", "tie", "draw", "bead", "sword", "gun", "pistol")

    /** "desenhe…", "desenha pra mim…". */
    private val PT_DRAW = Regex("^(?:desenhe|desenha|desenhe-me)(?:\\s+(?:pra|para)\\s+mim|\\s+me)?[,:]?\\s+(.+)$", O)
    /** "gere/crie/faça uma imagem de…", "um desenho do…". */
    private val PT_OF = Regex("^(?:gere|gera|crie|cria|faça|faca|faz)(?:\\s+(?:pra|para)\\s+mim)?\\s+(?:uma|um)\\s+(?:imagem|figura|ilustração|ilustracao|desenho|foto|pintura)\\s+(de|do|da|dos|das)\\s+(.+)$", O)
    /** "dibuja…", "dibújame…" (not "pinta…": "pinta la pared" is about a wall). */
    private val ES_DRAW = Regex("^(?:dibuja|dibújame|dibujame|dibuje|dibújeme|píntame|pintame)(?:\\s+me)?[,:]?\\s+(.+)$", O)
    /** "genera/crea/haz una imagen de…". */
    private val ES_OF = Regex("^(?:genera|crea|haz|hazme)\\s+(?:una|un)\\s+(?:imagen|ilustración|ilustracion|dibujo|foto|pintura)\\s+(de|del)\\s+(.+)$", O)

    /** The request at the start of [text], or null when it isn't one. */
    fun parse(text: String): Ask? {
        val t = POLITE.replaceFirst(text.trim().replace('’', '\''), "").trim()
        EN_OF.find(t)?.let { return ask(it.groupValues[1], Lang.EN) }
        PT_OF.find(t)?.let { m -> return ask(article(m.groupValues[1].lowercase(), m.groupValues[2]), Lang.PT) }
        ES_OF.find(t)?.let { m -> return ask((if (m.groupValues[1].lowercase() == "del") "el " else "") + m.groupValues[2], Lang.ES) }
        EN_ME.find(t)?.let { return ask(it.groupValues[1], Lang.EN) }
        EN_A.find(t)?.let { m -> return if (m.groupValues[2].lowercase() in NOT_A_PICTURE) null else ask(m.groupValues[1], Lang.EN) }
        PT_DRAW.find(t)?.let { return ask(it.groupValues[1], Lang.PT) }
        ES_DRAW.find(t)?.let { return ask(it.groupValues[1], Lang.ES) }
        return null
    }

    /** "do Cristo" -> "o Cristo": the article the contraction held. */
    private fun article(prep: String, rest: String) = when (prep) { "do" -> "o "; "da" -> "a "; "dos" -> "os "; "das" -> "as "; else -> "" } + rest

    private fun ask(raw: String, lang: Lang): Ask? {
        val s = POLITE_END.replace(raw.trim(), "").trim().trimEnd('.', '!', '?', '…', ' ').trim()
        return if (s.any { it.isLetter() }) Ask(s, lang) else null
    }

    /** Under the drawing as it is made: what it draws, and that it lands in Designer, where Crop is. */
    fun drawing(lang: Lang, subject: String): String = when (lang) {
        Lang.EN -> "Drawing $subject. It goes to Designer's drawings too, where you can crop it."
        Lang.PT -> "Desenhando $subject. Ele vai também para os desenhos do Designer, onde dá para recortar."
        Lang.ES -> "Dibujando $subject. También queda en los dibujos de Designer, donde puedes recortarlo."
    }

    /** Designer is switched off: say it can, and where to switch it on; nothing is drawn. */
    fun off(lang: Lang): String = when (lang) {
        Lang.EN -> "Designer can draw this. Switch it on in Plugins (menu › Plugins), then ask again."
        Lang.PT -> "O Designer pode desenhar isso. Ligue-o em Plugins (menu › Plugins) e peça de novo."
        Lang.ES -> "Designer puede dibujar esto. Actívalo en Plugins (menú › Plugins) y vuelve a pedirlo."
    }

    /** Boar Labs itself is off: Plugins isn't in the menu, so the switch to name first is Boar Labs, in Settings. */
    fun offLabs(lang: Lang): String = when (lang) {
        Lang.EN -> "Designer can draw this. Turn on Boar Labs in Settings › Advanced tweaks › Experimental, then Designer in Plugins."
        Lang.PT -> "O Designer pode desenhar isso. Ligue o Boar Labs em Configurações › Ajustes avançados › Experimental e depois o Designer em Plugins."
        Lang.ES -> "Designer puede dibujar esto. Activa Boar Labs en Ajustes › Ajustes avanzados › Experimental y luego Designer en Plugins."
    }

    /** Designer is on, its drawing model isn't downloaded. */
    fun noModel(lang: Lang): String = when (lang) {
        Lang.EN -> "Designer can draw this once its drawing model (683 MB) is on the phone: open Designer in Plugins to download it."
        Lang.PT -> "O Designer desenha isso depois que o modelo de desenho (683 MB) estiver no celular: abra o Designer em Plugins para baixar."
        Lang.ES -> "Designer dibuja esto cuando su modelo de dibujo (683 MB) esté en el teléfono: abre Designer en Plugins para descargarlo."
    }

    /** The subject is one the image model shouldn't draw (ImageSafety). */
    fun refused(lang: Lang): String = when (lang) {
        Lang.EN -> "Designer doesn't draw that."
        Lang.PT -> "O Designer não desenha isso."
        Lang.ES -> "Designer no dibuja eso."
    }

    /** Another drawing is being made: one at a time. */
    fun busy(lang: Lang): String = when (lang) {
        Lang.EN -> "Designer is drawing something else. Ask again when it's done."
        Lang.PT -> "O Designer está desenhando outra coisa. Peça de novo quando terminar."
        Lang.ES -> "Designer está dibujando otra cosa. Vuelve a pedirlo cuando termine."
    }
}
