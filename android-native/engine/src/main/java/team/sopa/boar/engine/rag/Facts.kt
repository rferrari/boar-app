package team.sopa.boar.engine.rag

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import team.sopa.boar.engine.routing.JsNumber
import team.sopa.boar.engine.routing.JsRegex
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Ported from src/rag/facts.ts in r4to's PR #85 (BOAR R&D, MIT).
 *
 * FACTS: a structured-knowledge instant answer from a compact offline table (Wikidata, CC0; facts.v1.json, passed in as
 * its JSON text). Fires only when (a) the question names one or two table entities by label or alias, found with the
 * same word n-grams the pack title lookup uses (WikiPackText.titleCandidates), and (b) its wording matches exactly one
 * property template of the lexicon below. One entity: the value with unit, "as of" year and a source line; two entities
 * and a comparison word: a computed verdict. Anything else (no entity, 0 or 2+ templates, an unsupported slot, an entity
 * without the value, 3+ entities) is null and the normal path. English only; the caller keeps it off health questions.
 *
 * The TypeScript's review rounds are kept as written: r2 (names typed capitalised word by word, no name with two
 * entities), r3 (lowercase one-word country or capital names, the China/UK/US/the Amazon aliases, "size of" and
 * "bigger than" ask for area, "area code" is not an area, "(dated YEAR)" before 2023), r4 (a lowercase multi-word name
 * that names one entity; "thw"/"teh" read as "the" before a name), r5 (an allowed word on both sides of every name;
 * "whats …" and "capital of …" are questions without "?"; a whole multi-word label beats the risk list) and r6 (opener
 * typos, "official language of X", "what continent is <place> in", "what countries surround X", taller/higher frames).
 * The regexes are the TypeScript ones verbatim, through JsRegex.
 */
class Facts(json: String) {
    class Entity(
        val q: String, val n: String, val a: List<String>, val k: String, val d: String?,
        val pop: Pair<Double, Double?>?, val area: Double?, val elev: Double?, val height: Double?, val len: Double?, val depth: Double?,
        val cap: List<String>?, val cur: List<String>?, val lang: List<String>?, val cont: List<String>?, val bord: List<String>?,
        val plug: List<String>?, val volt: List<Double>?, val hz: List<Double>?, val inc: String?, val end: String?,
        val loc: List<String>?, val win: Map<String, List<String>>?, val ctry: List<String>?, val t: List<String>?,
    )

    /** [template]: "population", "area", "capital"…; [entities]: their Wikidata ids. */
    data class Answer(val template: String, val entities: List<String>, val text: String)

    private val snapshot: String
    private val entities: List<Entity>
    private val index = HashMap<String, MutableList<Entity>>()
    private val lowerOk: Set<String>

    init {
        val root = Json.parseToJsonElement(json).jsonObject
        snapshot = root["snapshot"]!!.jsonPrimitive.content
        fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.content
        fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
        fun JsonObject.strs(k: String) = (this[k] as? JsonArray)?.map { it.jsonPrimitive.content }
        fun JsonObject.nums(k: String) = (this[k] as? JsonArray)?.map { it.jsonPrimitive.double }
        entities = root["entities"]!!.jsonArray.map { el ->
            val o = el.jsonObject
            Entity(
                o.str("q")!!, o.str("n")!!, o.strs("a") ?: emptyList(), o.str("k")!!, o.str("d"),
                (o["pop"] as? JsonArray)?.let { it[0].jsonPrimitive.double to it[1].jsonPrimitive.doubleOrNull },
                o.num("area"), o.num("elev"), o.num("height"), o.num("len"), o.num("depth"),
                o.strs("cap"), o.strs("cur"), o.strs("lang"), o.strs("cont"), o.strs("bord"), o.strs("plug"), o.nums("volt"), o.nums("hz"),
                o.str("inc"), o.str("end"), o.strs("loc"),
                (o["win"] as? JsonObject)?.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }, o.strs("ctry"), o.strs("t"),
            )
        }
        fun add(key: String, e: Entity) {
            val list = index.getOrPut(key) { mutableListOf() }
            if (e !in list) list += e
        }
        for (e in entities) {
            for (name in listOf(e.n) + e.a + (R3_ALIASES[e.q] ?: emptyList())) {
                val key = name.lowercase()
                // Short aliases ("US", "UK", "PRC") only as the question's own upper-case token (checked at lookup).
                if (key.length < 2) continue
                add(key, e)
                // "U.S." is typed as the word "U.S" (titleCandidates drops a trailing dot).
                if (key.endsWith(".")) add(key.trimEnd('.'), e)
            }
        }
        // r3 gate 1: one-word country and capital labels may be typed lowercase, unless the word is on the risk list.
        val capitals = entities.filter { it.k == "country" }.flatMap { e -> (e.cap ?: emptyList()).map { it.lowercase() } }.toSet()
        lowerOk = (entities.filter { e -> e.n.none(Js::isSpace) && (e.k == "country" || (e.k == "city" && e.n.lowercase() in capitals)) }.map { it.n.lowercase() } + LOWER_ALIASES)
            .filter { it !in LOWER_RISK }.toSet()
    }

    private class Resolved(val ents: List<Entity>, val ambiguous: Boolean, val starts: List<Int>)
    private class Tok(val w: String, val s: Int, val e: Int)
    private class Span(val e: Entity, val words: List<String>)

    /** Entities the question names, in question order, longest n-gram first; a word inside a found name never counts again. */
    fun factEntities(query: String): List<Entity> = resolve(query).ents

    /** As [factEntities], plus whether any name resolved to two or more entities (r2 gate 3: then the question is not answered). */
    private fun resolve(query: String): Resolved {
        // r4 rule 2: each "the" typo is read as "the" only if, so read, a resolved name starts at the next word.
        val typos = THE_TYPOS.findAll(query).toList()
        if (typos.isEmpty()) return resolveTyped(query)
        fun tokenAt(q: String, offset: Int) = TOKEN.findAll(q.substring(0, offset)).count()
        val accepted = typos.filter { m ->
            val q2 = query.substring(0, m.range.first) + "the" + query.substring(m.range.last + 1)
            (tokenAt(query, m.range.first) + 1) in resolveTyped(q2).starts
        }
        var q = query
        for (m in accepted.reversed()) q = q.substring(0, m.range.first) + "the" + q.substring(m.range.last + 1)
        return resolveTyped(q)
    }

    private fun resolveTyped(query: String): Resolved {
        var ambiguous = false
        val opening = (WORD.find(query)?.value ?: "").lowercase()
        val used = ArrayList<String>()
        class Found(val e: Entity, val at: Int, val tok: Int)
        val found = ArrayList<Found>()
        val spans = ArrayList<Span>()
        val low = query.lowercase()
        // Word tokens with offsets, split exactly like titleCandidates (r3: neighbours of a name, a leading "the").
        val toks = TOKEN.findAll(query).map { Tok(TOKEN_TRAIL.replace(it.value, ""), it.range.first, it.range.last + 1) }.toList()
        fun span(words: List<String>): Int {
            for (i in 0..toks.size - words.size) if (words.indices.all { j -> toks[i + j].w.lowercase() == words[j] }) return i
            return -1
        }
        fun glued(a: Int, b: Int) = query.substring(toks[a].e, toks[b].s).let { it.isNotEmpty() && it.all(Js::isSpace) }
        // r3 gate 2: "US" is a stopword ("us") for titleCandidates; typed upper-case it is the country.
        val cands = WikiPackText.titleCandidates(query).toMutableList()
        if (toks.any { it.w == "US" }) cands += "US"
        for (cand in cands) {
            var lower = cand.lowercase()
            if (used.any { has(it, lower) }) continue
            val single = !cand.contains(' ')
            val words = lower.split(SPACES)
            val at = span(words)
            val prev = if (at > 0 && glued(at - 1, at)) toks[at - 1] else null
            // r3 gate 1: a one-word country/capital name may be typed lowercase ("what is the population of canada").
            val lowerName = single && !UPPER_START.containsMatchIn(cand) && lower in lowerOk
            // r4 rule 1 / r5 rule C: a lowercase or mixed-case name of 2+ words resolves when, case-folded, it names exactly
            // one entity (with an optional typed "the" before it); the known outside homonym still blocks it.
            var safeList: List<Entity>? = null
            if (!single && !typedAsName(cand) && words.none { it in HOMONYMS }) {
                val l = index[lower] ?: (if (prev?.w?.lowercase() == "the") index["the $lower"] else null)
                if (l?.size == 1) safeList = l
            }
            // r3: otherwise a lowercase multi-word name ("south sudan") still needs capitals, and its inner word must not
            // answer for it ("sudan"): the question is not answered.
            if (safeList == null && !single && !typedAsName(cand) && index.containsKey(lower) && words.any { it in lowerOk }) {
                used += lower
                ambiguous = true
                continue
            }
            // A lone word must be written as a name (capitalized), and never be the sentence's opening word.
            if (single && ((!UPPER_START.containsMatchIn(cand) && !lowerName) || lower == opening)) continue
            if (cand.length <= 3 && cand != cand.uppercase() && !lowerName) continue
            if (!lowerName && safeList == null && !typedAsName(cand)) continue
            var list: List<Entity>? = index[lower]
            // r3 gate 2: "the UK", "the Amazon": an alias with a leading "the" matches when the question types the "the".
            if (list == null && prev?.w?.lowercase() == "the") {
                lower = "the $lower"
                list = index[lower]
            }
            if (list == null) continue
            // r3: a lowercase one-word name after "south"/"new" is another place.
            if (lowerName && prev != null && prev.w.lowercase() in MODIFIERS) {
                used += lower
                ambiguous = true
                continue
            }
            // Same name, several entities: the exact label wins; two same-label entities (Bandung city/regency) -> ambiguous.
            val exact = list.filter { it.n.lowercase() == lower }
            val pool = exact.ifEmpty { list }
            used += lower
            if (pool.size > 1 || lower in HOMONYMS) {
                ambiguous = true
                continue
            }
            val pick = pool[0]
            spans += Span(pick, words)
            if (found.none { it.e === pick }) found += Found(pick, low.indexOf(lower), at)
        }
        if (!ambiguous && neighbourBlocked(toks, spans)) ambiguous = true
        return Resolved(found.sortedBy { it.at }.map { it.e }, ambiguous, found.map { it.tok })
    }

    /**
     * r5 rule A: true when some resolved name (every occurrence, any casing) has a disallowed neighbour on either side.
     * A glued Mount/Mt/Mountain/River/Lake of the entity's kind extends the name (r3 exception); then R5_SKIP words and
     * numbers are skipped; the first other word must be allowed (r5Allowed, inside a multi-word lexicon phrase, or a word
     * of another resolved name), or there must be none.
     */
    private fun neighbourBlocked(toks: List<Tok>, spans: List<Span>): Boolean {
        val lw = toks.map { it.w.lowercase().replace('’', '\'') }
        fun starts(words: List<String>) = lw.indices.filter { i -> words.indices.all { j -> lw.getOrNull(i + j) == words[j] } }
        val owner = HashMap<Int, Entity>()
        for (s in spans) for (i in starts(s.words)) s.words.indices.forEach { j -> owner[i + j] = s.e }
        fun inPhrase(j: Int) = R5_PHRASES.any { ph -> ph.indices.any { k -> ph.indices.all { m -> lw.getOrNull(j - k + m) == ph[m] } } }
        fun ok(start: Int, dir: Int, e: Entity): Boolean {
            var j = start
            if (j >= 0 && j < lw.size && FEATURE[lw[j]] == e.k) j += dir
            while (j >= 0 && j < lw.size && (lw[j] in R5_SKIP || NUMBER_START.containsMatchIn(lw[j]))) j += dir
            if (j < 0 || j >= lw.size) return true
            val o = owner[j]
            if (o != null && o !== e) return true
            return r5Allowed(lw[j]) || inPhrase(j)
        }
        return spans.any { s -> starts(s.words).any { i -> !ok(i - 1, -1, s.e) || !ok(i + s.words.size, 1, s.e) } }
    }

    private fun source(asOf: Double?): String =
        "\n\nSource: Wikidata (CC0), offline table as of $snapshot${if (asOf != null && asOf != 0.0) "; figure dated ${JsNumber.toString(asOf)}" else ""}."

    private class Num(val v: Double, val text: String, val asOf: Double?)

    /** The numeric value of a template for an entity, with its phrase; null when the table has none. */
    private fun numeric(t: String, e: Entity): Num? = when (t) {
        // r3 gate 5: a figure dated before 2023 says so ("(dated 2021)").
        "population" -> e.pop?.let { (v, y) ->
            Num(v, "${millions(v)}${if (y != null && y != 0.0) (if (y < 2023) " (dated ${JsNumber.toString(y)})" else " (${JsNumber.toString(y)})") else ""}", y)
        }
        "area" -> e.area?.takeIf { it != 0.0 }?.let { Num(it, "${fmt(it)} km² (${fmt(it / 2.58999)} sq mi)", null) }
        "height" -> (e.height ?: e.elev)?.takeIf { it != 0.0 }?.let { Num(it, "${fmt(it)} m (${fmt(it * 3.28084)} ft)", null) }
        "length" -> e.len?.takeIf { it != 0.0 }?.let { Num(it, "${fmt(it)} km (${fmt(it / 1.609344)} mi)", null) }
        "depth" -> e.depth?.takeIf { it != 0.0 }?.let { Num(it, "${fmt(it)} m", null) }
        else -> null
    }

    /** One entity, one template -> the value line; null when the entity has no value for it. */
    private fun single(t: String, e: Entity, query: String): String? {
        val name = e.n
        val where = if (!e.ctry.isNullOrEmpty() && e.ctry.none { e.d?.contains(it) == true }) list(e.ctry) else ""
        val types = (e.t ?: emptyList()).filter { e.d?.contains(it) != true }
        val inner = listOf(listOfNotNull(e.d, where).filter { it.isNotEmpty() }.joinToString(", "), if (types.isNotEmpty()) "type: ${types.joinToString(", ")}" else "")
            .filter { it.isNotEmpty() }.joinToString("; ")
        val head = if (inner.isNotEmpty()) "$name ($inner)" else name
        numeric(t, e)?.let { num ->
            val what = if (t == "height" && (e.height == null || e.height == 0.0)) "elevation" else NOUN.getValue(t)
            return "$head: $what ${num.text}.${source(num.asOf)}"
        }
        return when (t) {
            "capital" -> e.cap?.takeIf { it.isNotEmpty() }?.let { "The capital of $name is ${list(it)}.${source(null)}" }
            "currency" -> e.cur?.takeIf { it.isNotEmpty() }?.let { "$name's currency: ${list(it)}.${source(null)}" }
            "languages" -> e.lang?.takeIf { it.isNotEmpty() }?.let { "Official language${if (it.size > 1) "s" else ""} of $name: ${list(it)}.${source(null)}" }
            "continent" -> e.cont?.takeIf { it.isNotEmpty() }?.let { "$name is in ${list(it)}.${source(null)}" }
            "borders" -> e.bord?.takeIf { it.isNotEmpty() }?.let { "$name borders ${list(it)}.${source(null)}" }
            "dates" -> e.inc?.takeIf { it.isNotEmpty() }?.let { inc ->
                val end = e.end?.takeIf { it.isNotEmpty() }?.let { "; it ended (came down / was dissolved) on ${date(it)}" } ?: ""
                "$head: built/founded ${date(inc)}$end.${source(null)}"
            }
            "electricity" -> {
                if (e.plug.isNullOrEmpty() && e.volt.isNullOrEmpty()) return null
                val parts = listOf(
                    e.plug?.takeIf { it.isNotEmpty() }?.let { "plug type${if (it.size > 1) "s" else ""} ${list(it)}" } ?: "",
                    e.volt?.takeIf { it.isNotEmpty() }?.let { v -> "mains voltage ${v.joinToString(" / ") { "${JsNumber.toString(it)} V" }}" } ?: "",
                    e.hz?.takeIf { it.isNotEmpty() }?.joinToString(" / ") { "${JsNumber.toString(it)} Hz" } ?: "",
                ).filter { it.isNotEmpty() }
                "$name: ${parts.joinToString(", ")}. Where two voltages are listed, it differs by region: check the socket or ask locally.${source(null)}"
            }
            "host" -> e.loc?.takeIf { it.isNotEmpty() }?.let { "$name: held in ${list(it)}.${source(null)}" }
            "winner" -> {
                val y = WINNER_YEAR.find(query)?.groupValues?.get(1)
                val w = y?.let { e.win?.get(it) }
                if (w.isNullOrEmpty()) null else "$name, $y: ${list(w)}.${source(null)}"
            }
            else -> null
        }
    }

    /** Two entities and a numeric template -> the computed verdict; null when either value is missing or they tie. */
    private fun compare(t: String, a: Entity, b: Entity, query: String): String? {
        val x = numeric(t, a) ?: return null
        val y = numeric(t, b) ?: return null
        if (x.v == y.v) return null
        val low = query.lowercase()
        val asOf = maxOf(x.asOf ?: 0.0, y.asOf ?: 0.0).takeIf { it != 0.0 }
        val verdict = "${if (x.v > y.v) a.n else b.n} ${MORE.getValue(t)} ${if (x.v > y.v) b.n else a.n}: ${a.n} ${x.text} vs ${b.n} ${y.text}."
        if (YES_NO.containsMatchIn(Js.trim(query))) {
            val greater = GREATER.any { has(low, it) }
            val lesser = LESSER.any { has(low, it) }
            if (greater == lesser) return null
            val yes = if (greater) x.v > y.v else x.v < y.v
            return "${if (yes) "Yes" else "No"}. $verdict${source(asOf)}"
        }
        return "$verdict${source(asOf)}"
    }

    /** Height of one entity against the depth of a lake: would it stick out of the water? */
    private fun fit(tall: Entity, lake: Entity, query: String): String? {
        val h = (tall.height ?: tall.elev)?.takeIf { it != 0.0 } ?: return null
        val d = lake.depth?.takeIf { it != 0.0 } ?: return null
        val low = query.lowercase()
        val above = h > d
        val gap = Math.abs(h - d)
        val asksOut = ASKS_OUT.containsMatchIn(low)
        val asksUnder = ASKS_UNDER.containsMatchIn(low)
        val lead = if (asksOut) (if (above) "Yes. " else "No. ") else if (asksUnder) (if (above) "No. " else "Yes. ") else ""
        return "$lead${tall.n} is ${fmt(h)} m ${if (tall.height != null && tall.height != 0.0) "tall" else "high"}; ${lake.n} is ${fmt(d)} m deep at its deepest point, so its top would be " +
            "${if (above) "about ${fmt(gap)} m above the surface" else "about ${fmt(gap)} m below the surface (fully submerged)"}.${source(null)}"
    }

    /** r6 fix 3: a place whose countries (P17) include exactly one table country, which is on exactly one continent. */
    private fun placeContinent(e: Entity): String? {
        if (e.k == "country" || e.ctry.isNullOrEmpty()) return null
        val c = entities.filter { it.k == "country" && it.n in e.ctry }
        // A country on several continents (Egypt, France with its overseas regions) does not tell where the place is.
        if (c.size != 1 || c[0].cont?.size != 1) return null
        return "${e.n} is in ${c[0].n}, which is in ${c[0].cont!![0]}.${source(null)}"
    }

    /**
     * r6 fix 5: "which is taller/higher, X or Y" / "is X taller/higher than Y" where each side, case-folded and with an
     * optional leading "the", is exactly one label or alias of exactly one entity, both of the same height class.
     */
    private fun tallerFrame(q: String): Answer? {
        for (re in R6_TALLER) {
            val m = re.find(q) ?: continue
            fun side(s: String) = index[s.lowercase().replace('’', '\'')]?.takeIf { it.size == 1 }?.get(0)
            val a = side(m.groupValues[1])
            val b = side(m.groupValues[2])
            if (a == null || b == null || a === b || TALL_CLASS[a.k] == null || TALL_CLASS[a.k] != TALL_CLASS[b.k]) return null
            return compare("height", a, b, q)?.let { Answer("height", listOf(a.q, b.q), it) }
        }
        return null
    }

    /** The FACTS instant answer, or null (the question takes the normal path). English questions only. */
    fun answer(query: String): Answer? {
        var q = Js.trim(query)
        // r6 fix 1: an opener typo is read as "what" / "how".
        for ((re, w) in OPENER_TYPOS) q = re.replaceFirst(q, w)
        // r6 fix 4: "what country surrounds X" is read as "what country borders X".
        q = R6_SURROUND.replaceFirst(q, "$1 $2 border")
        if (q.isEmpty() || !(QUESTION.containsMatchIn(q) || QUESTION_OPENERS.containsMatchIn(q) || R6_OPENERS.containsMatchIn(q))) return null
        val low = APOSTROPHES.replace(q.lowercase(), "'")
        if (VETO.any { has(low, it) }) return null
        val templates = templates(q)
        if (templates.size != 1) return null
        val t = templates[0]
        if (t == "height") tallerFrame(q)?.let { return it }
        val r = resolve(q)
        val ents = r.ents
        if (r.ambiguous || ents.isEmpty() || ents.size > 2) return null
        fun done(text: String?) = text?.let { Answer(t, ents.map { e -> e.q }, it) }
        val comparing = COMPARE.any { has(low, it) }
        // r6 fix 3: "what continent is <place> in/on" -> the continent of the place's one country.
        if (ents.size == 1 && t == "continent" && ents[0].cont == null && R6_CONTINENT.containsMatchIn(q)) return done(placeContinent(ents[0]))
        if (ents.size == 1) return if (comparing) null else done(single(t, ents[0], q))
        // Two entities: a comparison of one numeric value, or a height against a lake's depth.
        if (t == "depth") {
            val lake = ents.firstOrNull { it.depth != null && it.depth != 0.0 }
            val tall = ents.firstOrNull { it !== lake && ((it.height ?: it.elev) ?: 0.0) != 0.0 }
            if (lake != null && tall != null && (tall.depth == null || tall.depth == 0.0)) return done(fit(tall, lake, q))
        }
        return if (comparing && t in NOUN) done(compare(t, ents[0], ents[1], q)) else null
    }

    companion object {
        /** Property lexicon: the wording that asks for each template (lowercase; whole words/phrases), in the TypeScript's order. */
        val LEXICON: Map<String, List<String>> = linkedMapOf(
            "population" to listOf("population", "populous", "inhabitants", "residents", "how many people", "more people", "fewer people", "less people", "people live", "people living"),
            "area" to listOf("area", "how big", "size of", "square kilometres", "square kilometers", "square km", "sq km", "km2", "km²", "square miles", "sq mi"),
            "capital" to listOf("capital", "capital city"),
            "currency" to listOf("currency", "currencies"),
            "languages" to listOf("language", "languages", "official language", "official languages", "spoken"),
            "continent" to listOf("continent", "which continent"),
            "borders" to listOf("border", "borders", "bordering", "neighbouring countries", "neighboring countries", "neighbours", "neighbors"),
            "height" to listOf("how high", "how tall", "height", "elevation", "taller", "higher", "tallest", "highest"),
            "length" to listOf("how long is", "how long are", "length", "longer", "longest", "km long", "miles long"),
            "depth" to listOf("how deep", "depth", "deepest", "deeper", "deep"),
            "dates" to listOf("built", "founded", "established", "constructed", "erected", "inception", "came down", "come down", "fell", "fall of", "torn down", "demolished", "dissolved"),
            "electricity" to listOf("plug", "plugs", "plug type", "socket", "sockets", "outlet", "outlets", "voltage", "volts", "mains", "adapter", "adaptor"),
            "host" to listOf("host", "hosted", "hosting", "held"),
            "winner" to listOf("won", "win", "winner", "winners", "received", "awarded", "laureate"),
        )

        /** Slots the table cannot answer, and explanation wording: any of these -> no fire (the model answers). */
        private val VETO = listOf(
            "density", "densely", "gdp", "economy", "time zone", "timezone", "driving side", "drive on", "climate", "weather", "religion",
            "president", "prime minister", "leader", "government", "history", "historically", "known for", "why", "explain", "describe",
            "overview", "tell me about", "essentials", "key facts", "how did", "independent", "independence", "safe", "sick", "sickness",
            "danger", "dangerous", "visa", "flight", "take", "stay", "per capita",
        )
        /** r3: comparative wording that asks for area only when no other property is named ("Which is bigger, Canada or the US?"). */
        private val WEAK_AREA = listOf("bigger than", "larger than", "smaller than", "which is bigger", "which is larger")
        /** r3: compound phrases whose property word is not the property ("area code", "area of expertise"): removed before matching. */
        private val STOP_PHRASES = listOf(
            "area code", "area codes", "area of expertise", "area of study", "area of interest", "area of focus", "area of research",
            "capital letter", "capital letters", "capital gains", "capital punishment",
        ).map { JsRegex.re("""\b$it\b""", "g") }
        private val COMPARE = listOf("more", "less", "fewer", "than", "bigger", "larger", "smaller", "higher", "taller", "longer", "shorter", "deeper", "shallower", "or", "compare", "compared", "versus", "vs")
        private val GREATER = listOf("more", "bigger", "larger", "higher", "taller", "longer", "deeper")
        private val LESSER = listOf("less", "fewer", "smaller", "lower", "shorter", "shallower")
        private val YES_NO = JsRegex.re("""^(is|are|does|do|did|has|have|was|were|can|could|would|will)\b""", "i")
        private val QUESTION = JsRegex.re("""\?\s*$|^(what|which|who|when|where|how|is|are|does|do|did|has|have|was|were|can|could|would|will|if|tell|give|list|name)\b""", "i")
        /** r5 rule B: question openers typed without "?" ("whats the capital of the philippines"). */
        private val QUESTION_OPENERS = JsRegex.re("""^(whats|what['’]s|hows|how['’]s|wheres|where['’]s)(?![\p{L}\p{N}])|^(size|capital|population|elevation|height|length) of\b""", "iu")
        /** r6 fix 1: typos of the opening "what" / "how" (exact list), read as the word they stand for. */
        private val OPENER_TYPOS = listOf(
            JsRegex.re("""^(whta|waht|wat)(?![\p{L}\p{N}'’])""", "iu") to "what",
            JsRegex.re("""^hwo(?![\p{L}\p{N}'’])""", "iu") to "how",
            JsRegex.re("""^whats['’](?![\p{L}\p{N}])""", "iu") to "whats",
        )
        /** r6 fix 2: property-first openers typed without "?" ("official language of iran", "continent of kenya"). */
        private val R6_OPENERS = JsRegex.re("""^(official languages?|continent) of\b""", "iu")
        /** r6 fix 3: "what continent is X in/on" (a place: answered with its country's continent). */
        private val R6_CONTINENT = JsRegex.re("""^(what|which) continent (is|are)\b.*\b(in|on)\s*\??\s*$""", "iu")
        /** r6 fix 4: "what country/countries border(s)/surround(s) X" asks for borders. */
        private val R6_SURROUND = JsRegex.re("""^(what|which) (country|countries) (?:surround|surrounds)\b""", "iu")
        /** r6 fix 5: the two height-comparison frames; each side must be one whole name. */
        private val R6_TALLER = listOf(
            JsRegex.re("""^(?:which|what)(?: one)? is (?:taller|higher)\s*[,:]?\s+(?:the\s+)?(.+?)\s+or\s+(?:the\s+)?(.+?)\s*\??\s*$""", "iu"),
            JsRegex.re("""^is (?:the\s+)?(.+?) (?:taller|higher) than (?:the\s+)?(.+?)\s*\??\s*$""", "iu"),
        )
        /** r6 fix 5: kinds compared by height in those frames (a mountain with a mountain, a structure with a structure). */
        private val TALL_CLASS = mapOf("mountain" to "mountain", "building" to "structure", "dam" to "structure")

        private val HAS = ConcurrentHashMap<String, Regex>()
        /** The cue as a whole word or phrase of [low] (letters and digits around it end it). */
        private fun has(low: String, cue: String) =
            HAS.getOrPut(cue) { Regex("(?:^|[^\\p{L}\\p{N}])" + Pattern.quote(cue) + "(?=\\z|[^\\p{L}\\p{N}])") }.containsMatchIn(low)

        /** r3: everyday short names the Wikidata aliases lack (or the r2 nickname gate removed). */
        private val R3_ALIASES = mapOf(
            "Q148" to listOf("China"),
            "Q145" to listOf("UK", "U.K.", "the UK", "Britain"),
            "Q30" to listOf("US", "U.S.", "USA", "the USA", "America"),
            "Q3783" to listOf("the Amazon"),
        )
        /** r3: one-word country/capital names that are also common words, first names or part of other places: lowercase never resolves. */
        private val LOWER_RISK = setOf(
            "georgia", "turkey", "chad", "jordan", "guinea", "chile", "china", "america", "us", "wellington", "victoria", "sofia",
            "israel", "santiago", "kingston", "nice", "reading", "male", "malé",
        )
        /** r4 rule 2: typos of "the", read as "the" only when a resolved name follows right after. */
        private val THE_TYPOS = JsRegex.re("""(?<![\p{L}\p{N}'’.-])(thw|teh|hte|tje|th)(?![\p{L}\p{N}'’.-])""", "gu")
        /** r3: r3 aliases that may be typed lowercase ("what currency does the uk use"). */
        private val LOWER_ALIASES = listOf("uk", "u.k", "britain", "usa", "u.s")
        /** r3: a lowercase name right after one of these is part of another place ("south sudan", "new mexico"): no answer. */
        private val MODIFIERS = setOf("north", "south", "east", "west", "northern", "southern", "eastern", "western", "central", "new", "latin", "equatorial", "american", "papua", "upper", "lower", "great", "french", "british", "dutch")

        // r5 rule A allowlists: the word right before and right after a resolved name, after skipping R5_SKIP words,
        // punctuation and numbers, must be absent (edge) or in one of these sets, or a word of another resolved name.
        /** Skipped when looking for a name's neighbour. */
        private val R5_SKIP = setOf("the", "a", "an", "of", "in", "at", "on", "for", "to", "and", "or")
        /** Question words: wh-words, auxiliaries/modals, pronouns, closed-class function words, and question-frame verbs. */
        private val R5_QUESTION = setOf(
            "what", "whats", "what's", "which", "who", "whom", "whose", "when", "where", "wheres", "where's", "why", "how", "hows", "how's",
            "is", "isn't", "are", "aren't", "was", "wasn't", "were", "weren't", "am", "be", "been", "being", "does", "doesn't", "do", "don't",
            "did", "didn't", "has", "have", "had", "having", "can", "could", "would", "will", "shall", "should", "may", "might", "must",
            "i", "i'm", "you", "we", "they", "he", "she", "it", "it's", "its", "their", "there", "there's", "this", "that", "these", "those",
            "me", "my", "your", "our", "us", "if", "tell", "give", "list", "name", "show", "know", "wonder", "by", "with", "from", "into",
            "as", "so", "but", "not", "no", "yes", "any", "some", "all", "both", "each", "also", "just", "really", "very", "much", "many",
            "more", "most", "less", "fewer", "then", "compare", "above", "below", "over", "under", "up", "down",
            "use", "uses", "used", "using", "speak", "speaks", "spoken", "speaking", "live", "lives", "living", "located",
            "stand", "stands", "stood", "put", "placed",
        )
        /** Property lexicon: every one-word cue of LEXICON, their superlatives and feature points. */
        private val R5_PROPERTY = setOf(
            "tall", "high", "big", "long", "deep", "population", "people", "capital", "currency", "language", "languages", "official",
            "continent", "border", "borders", "area", "size", "elevation", "height", "length", "depth", "bigger", "larger", "smaller",
            "taller", "longer", "shorter", "higher", "lower", "deeper", "shallower", "biggest", "largest", "smallest", "tallest",
            "longest", "highest", "deepest", "lowest", "point", "top", "peak", "summit", "total",
        ) + LEXICON.values.flatten().filter { ' ' !in it }
        /** Hedge and time words (plus the time phrase "these days" / "nowadays"). */
        private val R5_HEDGE = setOf("roughly", "about", "approximately", "approx", "around", "exactly", "today", "now", "currently", "called", "please", "again", "anyway", "days", "nowadays")
        /** Unit words (plus spelled-out forms). */
        private val R5_UNITS = setOf("m", "km", "feet", "ft", "foot", "miles", "mile", "mi", "sqkm", "sq", "square", "km2", "km²", "meters", "metres", "meter", "metre", "kilometres", "kilometers", "kilometre", "kilometer")
        /** Comparison connectors. */
        private val R5_COMPARE = setOf("or", "than", "vs", "versus", "compared")
        /** Multi-word property phrases ("capital city", "square kilometres"): a neighbour inside one counts as a property word. */
        private val R5_PHRASES = LEXICON.values.flatten().filter { ' ' in it }.map { it.split(" ") }
        private fun r5Allowed(w: String) = w in R5_QUESTION || w in R5_PROPERTY || w in R5_HEDGE || w in R5_UNITS || w in R5_COMPARE
        /** r3: a feature word glued to a name of that kind is the same place ("Mount Kilimanjaro", "Danube River"). */
        private val FEATURE = mapOf("mount" to "mountain", "mt" to "mountain", "mountain" to "mountain", "river" to "river", "lake" to "lake")

        /** Words of a name that may stay lowercase when the name is typed ("Nobel Prize in Literature", "Lake of the Woods"). */
        private val NAME_STOP = setOf("the", "of", "and", "de", "del", "da", "do", "dos", "das", "du", "des", "la", "le", "les", "el", "in", "on", "at", "y", "e", "a", "an", "to", "for", "by", "von", "van", "der", "den", "di", "al")
        private val NAME_START = Regex("^[\\p{Lu}\\p{N}]")
        private val NAME_ELIDED = Regex("^\\p{Ll}{1,2}['’]\\p{Lu}")
        /** Every non-stopword of the name must be capitalised as typed (r2 gate 4: "white house", "the district" never resolve). */
        private fun typedAsName(cand: String) = cand.split(SPACES).all { it.lowercase() in NAME_STOP || NAME_START.containsMatchIn(it) || NAME_ELIDED.containsMatchIn(it) }
        /** Names shared with a well-known place that is not in the table (the US state Georgia): never answered (r2 gate 3). */
        private val HOMONYMS = setOf("georgia")

        private val TOKEN = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’.-]*")
        private val TOKEN_TRAIL = Regex("[.'’-]+$")
        private val WORD = Regex("[\\p{L}\\p{N}]+")
        private val SPACES = Regex(Js.WS + "+")
        private val UPPER_START = Regex("^\\p{Lu}")
        private val NUMBER_START = Regex("^\\p{N}")
        private val APOSTROPHES = Regex("[’']")
        private val WINNER_YEAR = JsRegex.re("""\b(1[89]\d\d|20\d\d)\b""")
        private val ASKS_OUT = JsRegex.re("""\b(stick|poke|rise|be|show) (out|above)|\breach the surface|\babove the (water|surface)|\bbreak the surface""")
        private val ASKS_UNDER = JsRegex.re("""\b(submerged|underwater|under water|covered|disappear)""")

        private val NOUN = mapOf("population" to "population", "area" to "area", "height" to "height", "length" to "length", "depth" to "maximum depth")
        private val MORE = mapOf("population" to "has more people than", "area" to "is larger by area than", "height" to "is taller than", "length" to "is longer than", "depth" to "is deeper than")

        /** The templates whose wording the question uses. */
        fun templates(query: String): List<String> {
            var low = APOSTROPHES.replace(query.lowercase(), "'")
            // r3 gate 4: the property word must stand alone ("area code" is not an area).
            for (p in STOP_PHRASES) low = p.replace(low, " ")
            val found = LEXICON.filter { (_, cues) -> cues.any { has(low, it) } }.keys.toList()
            // r3 gate 3: "bigger than" / "which is larger" ask for area when nothing else is named.
            return if (found.isNotEmpty() || WEAK_AREA.none { has(low, it) }) found else listOf("area")
        }

        private fun fmt(n: Double, d: Int = 0) = JsNumber.locale(n, false, d)
        private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        private fun n(s: String) = JsNumber.toString(JsNumber.parse(s))
        // Years < 1000 print without a leading zero ("476", not "0476").
        private fun date(s: String) =
            if (s.length >= 10) "${n(s.substring(8, 10))} ${MONTHS[JsNumber.parse(s.substring(5, 7)).toInt() - 1]} ${n(s.substring(0, 4))}" else n(s.substring(0, 4))
        private fun list(xs: List<String>) = if (xs.size <= 1) xs.joinToString("") else "${xs.dropLast(1).joinToString(", ")} and ${xs.last()}"
        private fun millions(n: Double) = when {
            n >= 1e9 -> "${fmt(n / 1e9, 2)} billion"
            n >= 1e6 -> "${fmt(n / 1e6, 1)} million"
            else -> fmt(n)
        }
    }
}
