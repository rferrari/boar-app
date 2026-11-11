package team.sopa.boar.engine.labs

import team.sopa.boar.engine.routing.QueryContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * The questions Lunar, Nomad and Vitals answer themselves, from the phone's own data (adam, Nov 12: "plugins that
 * answer"), and the answers' text in English and Portuguese. Only a question that clearly asks for that data is taken:
 * "the moon landing" or "north of France" go to the model as before.
 */
object PluginAnswers {
    // ---- Lunar ----
    private val MOON = Regex("\\b(moon|moons|lunar|lua|lunar)\\b", RegexOption.IGNORE_CASE)
    private val MOON_NOW = Regex(
        "\\b(phase|phases|tonight|today|now|this (week|month)|next (full|new)|full moon|new moon|waxing|waning|crescent|gibbous|lit|" +
            "fase|fases|hoje|esta noite|agora|esta semana|este m[eê]s|pr[oó]xima|cheia|nova|minguante|crescente)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val MOON_HISTORY = Regex("\\b(landing|landed|apollo|armstrong|mission|astronaut|pouso|miss[aã]o|astronauta|orbit|[oó]rbita|distance|dist[aâ]ncia|gravity|gravidade)\\b", RegexOption.IGNORE_CASE)

    fun isMoonQuestion(q: String): Boolean = MOON.containsMatchIn(q) && MOON_NOW.containsMatchIn(q) && !MOON_HISTORY.containsMatchIn(q)

    private val PHASE_EN = mapOf("new" to "a new moon", "waxingCrescent" to "a waxing crescent", "firstQuarter" to "at first quarter",
        "waxingGibbous" to "a waxing gibbous", "full" to "full", "waningGibbous" to "a waning gibbous", "lastQuarter" to "at last quarter", "waningCrescent" to "a waning crescent")
    private val PHASE_PT = mapOf("new" to "nova", "waxingCrescent" to "crescente", "firstQuarter" to "no quarto crescente",
        "waxingGibbous" to "crescente gibosa", "full" to "cheia", "waningGibbous" to "minguante gibosa", "lastQuarter" to "no quarto minguante", "waningCrescent" to "minguante")

    fun moonAnswer(q: String, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val s = MoonPhase.at(nowMs)
        val key = MoonPhase.nameKey(s)
        val pct = (s.illumination * 100).roundToInt()
        val pt = QueryContext.isPortugueseQuestion(q)
        val day = SimpleDateFormat(if (pt) "d 'de' MMMM, HH:mm" else "MMMM d, HH:mm", if (pt) Locale("pt", "BR") else Locale.US).apply { timeZone = zone }
        return if (pt) "A lua está ${PHASE_PT.getValue(key)} agora, com $pct% iluminada. Próxima lua cheia: ${day.format(Date(s.nextFullMs))}. Próxima lua nova: ${day.format(Date(s.nextNewMs))}. Calculado no celular, sem internet."
        else "The moon is ${PHASE_EN.getValue(key)} now, $pct% lit. Next full moon: ${day.format(Date(s.nextFullMs))}. Next new moon: ${day.format(Date(s.nextNewMs))}. Worked out on this phone, offline."
    }

    // ---- Nomad ----
    private val DIRECTION = Regex(
        "\\b(which way is|where is|what direction (is|am i)|which direction (is|am i)|point me|find) (the )?(north|south|east|west)\\b|" +
            "\\bwhich (way|direction) am i (facing|heading|pointing)\\b|\\bmy heading\\b|\\bcompass (heading|reading|direction)\\b|" +
            "\\b(onde fica|onde [eé]|para onde fica|para que lado fica|qual dire[cç][aã]o [eé]) o (norte|sul|leste|oeste)\\b|" +
            "\\bpara (onde|que lado|que dire[cç][aã]o) estou (virado|apontando|olhando)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun isDirectionQuestion(q: String): Boolean = DIRECTION.containsMatchIn(q)

    /** Past this tilt the compass reads wrong: "hold the phone flat". */
    const val FLAT_DEG = 18f

    /** [heading]: degrees from magnetic north the phone's top points at; null without a compass sensor. */
    fun directionAnswer(q: String, heading: Float?, tiltDeg: Float?): String {
        val pt = QueryContext.isPortugueseQuestion(q)
        if (heading == null) return if (pt) "Este celular não tem bússola (sensor magnético), então o Nomad não consegue dizer a direção."
        else "This phone has no compass (magnetic sensor), so Nomad can't tell the direction."
        val deg = heading.roundToInt() % 360
        val dir = Compass.cardinal(deg)
        val name = (if (pt) DIRS_PT else DIRS_EN).getValue(dir)
        val north = ((360 - deg) % 360)
        val turn = if (north <= 180) (if (pt) "$north° à direita" else "$north° to your right") else (if (pt) "${360 - north}° à esquerda" else "${360 - north}° to your left")
        val flat = tiltDeg != null && tiltDeg > FLAT_DEG
        val base = if (pt) "O topo do celular aponta para $name ($deg°). O norte fica a $turn." else "The top of your phone points $name ($deg°). North is $turn."
        val hold = if (!flat) "" else if (pt) " Segure o celular deitado, na horizontal: inclinado, a bússola erra." else " Hold the phone flat: tilted, the compass reads wrong."
        val note = if (pt) " Norte magnético; afaste de ímãs e metais." else " Magnetic north; keep it away from magnets and metal."
        return base + hold + note
    }

    private val DIRS_EN = mapOf("n" to "north", "ne" to "northeast", "e" to "east", "se" to "southeast", "s" to "south", "sw" to "southwest", "w" to "west", "nw" to "northwest")
    private val DIRS_PT = mapOf("n" to "o norte", "ne" to "o nordeste", "e" to "o leste", "se" to "o sudeste", "s" to "o sul", "sw" to "o sudoeste", "w" to "o oeste", "nw" to "o noroeste")

    // ---- Vitals ----
    private val PHONE = Regex("\\b(my|this|the) (phone|device|cellphone|mobile)\\b|\\b(meu|este|deste|do|o) (celular|telefone|aparelho)\\b|\\bphone'?s\\b", RegexOption.IGNORE_CASE)
    private val PHONE_WHAT = Regex(
        "\\b(battery|charge|charging|temperature|hot|heat|overheat|overheating|warm|memory|ram|cpu|processor|cores|processes|" +
            "bateria|carga|carregando|temperatura|quente|esquentando|aquecendo|mem[oó]ria|processador|n[uú]cleos|processos)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun isPhoneQuestion(q: String): Boolean = PHONE.containsMatchIn(q) && PHONE_WHAT.containsMatchIn(q)

    data class PhoneNow(val batteryPct: Int?, val charging: Boolean?, val batteryTempC: Double?, val ramFreeMb: Long?, val ramTotalMb: Long?, val cores: Int?)

    fun phoneAnswer(q: String, p: PhoneNow): String {
        val pt = QueryContext.isPortugueseQuestion(q)
        val parts = ArrayList<String>()
        p.batteryPct?.let { parts += if (pt) "bateria em $it%" + (if (p.charging == true) ", carregando" else "") else "battery at $it%" + (if (p.charging == true) ", charging" else "") }
        val loc = if (pt) Locale("pt", "BR") else Locale.US
        p.batteryTempC?.let { t ->
            val heat = Vitals.heat(t).name.lowercase()
            parts += if (pt) "temperatura da bateria ${"%.1f".format(loc, t)} °C (${HEAT_PT[heat] ?: heat})" else "battery temperature ${"%.1f".format(Locale.US, t)} °C ($heat)"
        }
        if (p.ramFreeMb != null && p.ramTotalMb != null) parts += if (pt) "${"%.1f".format(loc, p.ramFreeMb / 1024.0)} GB de memória livres de ${"%.1f".format(loc, p.ramTotalMb / 1024.0)} GB"
            else "${"%.1f".format(Locale.US, p.ramFreeMb / 1024.0)} GB of ${"%.1f".format(Locale.US, p.ramTotalMb / 1024.0)} GB memory free"
        p.cores?.let { parts += if (pt) "$it núcleos de processador" else "$it processor cores" }
        if (parts.isEmpty()) return if (pt) "O Vitals não conseguiu ler o celular agora." else "Vitals couldn't read the phone just now."
        val body = parts.joinToString("; ").replaceFirstChar { it.uppercase() }
        return if (pt) "$body. Lido agora no celular; o Vitals mostra mais." else "$body. Read on this phone just now; Vitals shows more."
    }

    private val HEAT_PT = mapOf("cool" to "fria", "warm" to "morna", "hot" to "quente", "unknown" to "desconhecida")
}
