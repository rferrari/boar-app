package team.sopa.boar.engine.routing

/**
 * Ported from src/routing/coHazard.ts in r4to's PR #85 (BOAR R&D, MIT).
 *
 * CO hazard (Compass 2026-10-03, ship L3 safety finding): "Can I run my portable generator in the attached garage if I keep
 * the door open?" took the model path and got "Yes, … ensure it's well-ventilated". A planning question about a
 * fuel-burning device in an enclosed or attached space gets a fixed safety-fact answer, before any model. English only.
 * Symptom or alarm questions are left out (in the TypeScript the health route's CO card answers them; native has no card,
 * so they go to the model as health questions). Wording: reviewed 2026-10-03, kept verbatim. Sources: CDC "What to Do to
 * Protect Yourself During a Power Outage"; US CPSC "Generators and Engine-Driven Tools"; NHS "Carbon monoxide poisoning".
 * The regexes are the TypeScript ones verbatim, through JsRegex.
 *
 * Not ported: isCoExposure and isCoExposureAcrossTurns, which only pick that CO card.
 */
object CoHazard {
    private const val B = """(?<![\p{L}\p{N}_])"""
    private const val E = """(?![\p{L}\p{N}_])"""
    private fun re(s: String) = JsRegex.re(s, "iu")
    private fun reAll(s: String) = JsRegex.re(s, "giu")

    /** A device that burns fuel. A plain "grill", "stove" or "heater" may be electric, so those need a fuel word. */
    private val DEVICE = re(
        listOf(
            """$B(?<!(?:random|number|code|site|password|static|image|text|qr|name|idea|key|signal|function|pulse|lorem|tone|wave|pdf|report|hash|ai|music|art|map|meme|solar|battery|jackery|ecoflow|bluetti)[ -])(?:generators?|gennys?|gennies)$E""",
            """$B(?<!activated )charcoal$E(?! (?:drawing|pencil|filter|tablet|pill|capsule|mask|soap|toothpaste|grey|gray|colou?r|sketch|powder|supplement))""",
            """$B(?:bbqs?|barbecues?|hibachis?)$E""",
            """$B(?:a|the|our|my|your) barbie$E""",
            """$B(?:gas|propane|charcoal|pellet|wood|camping|camp|portable gas|disposable) (?:grills?|smokers?)$E""",
            """$B(?:camping|camp|backpacking|coleman|primus|portable gas|canister|gas canister) (?:stoves?|burners?)$E""",
            """$B(?:propane|gas|kerosene|paraffin|butane|coleman) lanterns?$E""",
            """$B(?:gas|gasoline|petrol)[- ]powered$E""",
            """$B(?:pressure|power) washers?$E""",
            """$B(?:propane|butane|gas|coleman|camp|camping) (?:griddles?|torch(?:es)?|cookers?|rings?|fire ?pits?|lanterns?)$E""",
            """$B(?:jetboils?|pocket rockets?|blackstone(?: griddles?)?|weed torch(?:es)?|propane torch(?:es)?)$E""",
            """$B(?:kerosene|paraffin|oil) (?:lamps?|lanterns?|stoves?)$E""",
            """$B(?:gas|gasoline|petrol) (?:leaf blowers?|blowers?|lawn ?mowers?|mowers?|chainsaws?|engines?|tools?|trimmers?|augers?)$E""",
        ).joinToString("|"),
    )

    /** Fuel heaters. Reviewer C2: an installed or vented appliance (water heater, furnace, boiler, direct-vent) is not this hazard. */
    private val HEATER = re(
        listOf(
            """$B(?:propane|kerosene|paraffin|butane|gas|diesel|oil|fuel|fuel-burning|camping|buddy|patio|catalytic|outdoor)(?: [\p{L}-]+)? heaters?$E""",
            """$B(?:big|little) buddy$E""",
            """${B}mr\.? heater$E""",
        ).joinToString("|"),
    )

    /** An installed appliance named in passing ("the furnace eats the battery") doesn't veto the message: its name is removed
     * before the heater test; only vented/direct-vent/flued/installed still veto. */
    private val APPLIANCE = reAll("""$B(?:water heaters?|furnaces?|boilers?)$E""")
    private val INSTALLED = re("""$B(?:direct[- ]vent\w*|vented|flued|installed)$E""")

    /** A propane or butane stove counts unless the only space named is a kitchen (a propane kitchen range is a normal appliance). */
    private val PROPANE_STOVE = re("""$B(?:propane|butane) (?:stoves?|burners?)$E""")
    private val KITCHEN = reAll("""$B(?:(?:rv|camper|caravan|van|boat|motorhome|trailer|apartment|home|house)'?s? )?(?:kitchens?|galleys?)$E""")

    /** Using a gas oven or range to heat the home (CDC). */
    private val OVEN_HEAT = re(
        """$B(?:gas )?(?:oven|stove|range)$E.{0,40}$B(?:to|for) (?:heat|warm)""" +
            """|$B(?:heat|heated|heating|warm|warmed|warming)$E.{0,50}$B(?:gas )?(?:oven|stove)$E (?:on|open|with the door)""" +
            """|$B(?:oven|stove)$E on with the door (?:open|down)""",
    )

    /** An engine left running where the exhaust stays (CDC, NHS): a car warming up in the garage. */
    private val ENGINE = re(
        """$B(?:car|truck|vehicle|engine|motor|motorcycle|motorbike|lawn ?mower|mower|chainsaw|snow ?blower|leaf blower|atv|quad|van|suv)s?$E.{0,40}$B(?:running|run|idle|idling|on|warm(?:ing)? up|start(?:ed|ing)?)$E""" +
            """|$B(?:run|running|idle|idling|warm(?:ing)? up|leave|start)$E.{0,25}$B(?:car|truck|vehicle|engine|motor|motorcycle|motorbike|lawn ?mower|mower|snow ?blower|suv)s?$E""",
    )

    /** Generator placement near an opening ("is 10 ft from the back door ok?"): the 20-foot rule. */
    private val PLACEMENT = re(
        """$B(?:generators?|gennys?)$E.{0,80}$B(?:how far|distance|gap|enough|far enough|minimum|close|ok|okay|safe|\d+ ?(?:ft|feet|foot|m|metres|meters))$E.{0,60}$B(?:windows?|doors?|vents?|intakes?)$E""" +
            """|$B(?:how far|distance|gap|minimum|\d+ ?(?:ft|feet|foot|m|metres|meters))$E.{0,60}$B(?:windows?|doors?|vents?|intakes?)$E.{0,60}$B(?:generators?|gennys?)$E""" +
            """|$B(?:how far|distance|gap|minimum)$E.{0,40}$B(?:generators?|gennys?)$E.{0,60}$B(?:windows?|doors?|vents?|intakes?)$E""" +
            """|$B(?:is|are) \d+ ?(?:ft|feet|foot|m|metres|meters)$E.{0,40}$B(?:windows?|doors?|vents?)$E""" +
            """|$B(?:generators?|gennys?)$E.{0,80}$B(?:right next to|next to|right by|right under|under|beside|near|close to|up against)$E (?:my |the |our |his |her |their |a |an )?(?:[\p{L}-]+ ){0,2}(?:windows?|doors?|vents?|hatch(?:es)?)$E""",
    )

    private val FEET = JsRegex.re("""(\d+(?:\.\d+)?) ?(ft|feet|foot|m|metres|meters)(?![\p{L}])""", "giu")
    private val METRES = JsRegex.re("^m", "i")

    /** Distances the message states, in feet. Every one at 20 ft (6 m) or more: the generator is already placed right. */
    private fun statedFeet(q: String): List<Double> =
        FEET.findAll(q).map { m -> if (METRES.containsMatchIn(m.groupValues[2])) JsNumber.parse(m.groupValues[1]) * 3.28 else JsNumber.parse(m.groupValues[1]) }.toList()

    /** Heating or warming a space with a fuel burner ("warm up the tent with a gas canister stove"). */
    private val HEAT_SPACE = re(
        """$B(?:heat|warm)(?:ing)?(?: up)? (?:the |my |our |a )?(?:[\p{L}-]+ )?(?:tent|room|house|home|apartment|camper|van|cabin|shack|shelter|garage|basement|crawl ?space)$E.{0,40}$B(?:stoves?|ovens?|grills?|bbqs?|heaters?|burners?|lanterns?)$E""",
    )

    /** Storing or winterising, nothing running. */
    private val STORAGE = re("""$B(?:stor(?:e|ing|age)|winteri[sz]\w*|drain(?:ing)?|put (?:it )?away|for the (?:summer|winter|season|off-season))$E""")
    /** Fiction or history writing, not a plan. */
    private val FICTION = re("""$B(?:novel|my character|screenplay|short story|fiction|historically|historical)$E""")
    private val RUNNING = re("""$B(?:run|running|use|using|operate|operating|start|starting|turn(?:ed|ing)? on|fire up|light|lighting)$E""")

    /** An enclosed or attached space. Bare "inside" is not enough on its own ("inside the function"); a fuel device is required too. */
    private val SPACE = re(
        listOf(
            """${B}indoors?$E""",
            """$B(?:inside|in|into) (?:the |my |our |a |your |an |his |her |their )?(?:[\p{L}-]+ ){0,2}(?:house|home|bedroom|living room|apartment|flat|cabin|room|tent|camper|garage|basement|shed|van|rv|caravan|trailer|motorhome|boat|yurt|hut|shelter|dorm|condo|workshop|barn|porch|sunroom|conservatory)$E""",
            """${B}garages?$E(?! (?:sale|band|door opener))""",
            """${B}carports?$E""",
            """${B}on (?:the |my |our |a )?(?:enclosed |screened |screened-in |covered |closed-in )?(?:porch|sunroom|veranda)$E""",
            """${B}tents?$E""",
            """$B(?:campers?|rvs?|motorhomes?|caravans?|camper ?vans?|travel trailers?)$E""",
            """$B(?:basements?|cellars?|crawl ?spaces?|sheds?)$E""",
            """$B(?:enclosed|screened[- ]in|closed|covered) (?:porch|patio|balcony|space|area|room|deck)$E""",
            """$B(?:ice|fishing|pop-up|popup) (?:fishing )?(?:shack|house|hut|shanty|tent|shelter)s?$E""",
            """${B}shacks?$E""",
            // v2 (dev2): a fuel device is already required, so a bare "inside" or a room name is enough here.
            """$B(?:inside|in here|in there)$E""",
            """$B(?:bathrooms?|kitchens?|stalls?|barns?|coops?|stables?|shant(?:y|ies)|cabins?|workshops?|below deck|bunkhouse)$E""",
            """${B}while (?:we|i|you|they|the kids|everyone) (?:sleep|are asleep|'re asleep)$E|$B(?:overnight|all night)$E""",
        ).joinToString("|"),
    )

    /** Someone may already be poisoned or an alarm is sounding: not this planning answer. */
    private val ACUTE = re(
        """$B(?:headaches?|head (?:hurts|is (?:pounding|killing me|splitting))|pounding head|dizz\w*|nause\w*|vomit\w*|throwing up|threw up|throw(?:s|n)? up|passed out|faint\w*|unconscious|confus\w*|drowsy|sleepy|chest pain|can'?t breathe|short of breath|barely waking|(?:won'?t|can'?t|hard to|not) wak(?:e|ing)(?: up)?|slurr\w*|feel(?:s|ing)? (?:sick|ill|weird|woozy|weak))$E""" +
            """|$B(?:alarm|detector)s?$E.{0,60}$B(?:going off|went off|beeping|sounding|chirp\w*|is on|says|reads|reading|shows)$E|$B\d+ ?ppm$E""",
    )

    /** "Can I / Is it ok to <run|use…>": a request to do it. Only then does the answer start with "No." (reviewer C1). */
    private val ASK_PERMISSION = re(
        """(?:^|[.?!]\s+)(?:can|could|may|should|shall) (?:i|we)$E (?:still |just |safely )?(?:run|use|put|keep|leave|bring|start|operate|light|cook|warm|heat|idle|set up|plug)$E""" +
            """|${B}is it (?:ok|okay|safe|fine|alright|all right) (?:for (?:me|us) )?to$E""",
    )

    /** Risk questions ("can you die", "is it dangerous"), outdoor or either/or alternatives, installed appliances: a bare "No." would mislead. */
    private val NO_LEAD_IF = re(
        """$B(?:dangerous|danger|bad|die|dies|death|kill\w*|poison\w*|hurt|harm\w*|worr\w*|concern\w*|risk\w*|safer|unsafe|outside|outdoors?|out of|yard|driveway|instead|rather|or|not|away|move|moving|vent(?:ed)?|direct-vent|installed|flue|chimney|water heater|furnace|boiler)$E""",
    )

    // Common misspellings (dev2): "generater in the garge", "propain".
    private val QUOTES = Regex("[’‘]")
    private val GENERATOR_TYPO = JsRegex.re("""(?<![\p{L}])gen[ae]?rat[eoa]?rs?(?![\p{L}])""", "giu")
    private val GARAGE_TYPO = JsRegex.re("""(?<![\p{L}])gar(?:ge|adge|rage)(?![\p{L}])""", "giu")
    private val PROPANE_TYPO = JsRegex.re("""(?<![\p{L}])propain(?![\p{L}])""", "giu")
    private val KEROSENE_TYPO = JsRegex.re("""(?<![\p{L}])kerosine(?![\p{L}])""", "giu")
    private val ENGINE_SPACE = JsRegex.re("""(?<![\p{L}\p{N}_])(?:garages?|carports?|shed|enclosed|basement|crawl ?space)(?![\p{L}\p{N}_])""", "iu")

    /** A fuel-burning device in an enclosed or attached space (or near its openings), symptoms aside. */
    private fun hazardContext(query: String): Boolean {
        var q = QUOTES.replace(query, "'")
        q = GENERATOR_TYPO.replace(q, "generator")
        q = GARAGE_TYPO.replace(q, "garage")
        q = PROPANE_TYPO.replace(q, "propane")
        q = KEROSENE_TYPO.replace(q, "kerosene")
        if (OVEN_HEAT.containsMatchIn(q) || HEAT_SPACE.containsMatchIn(q)) return true
        if (PLACEMENT.containsMatchIn(q)) {
            val ft = statedFeet(q)
            if (!(ft.isNotEmpty() && ft.all { it >= 19.5 })) return true
        }
        if (!SPACE.containsMatchIn(q)) return false
        if ((STORAGE.containsMatchIn(q) && !RUNNING.containsMatchIn(q)) || FICTION.containsMatchIn(q)) return false
        val device = DEVICE.containsMatchIn(q) ||
            (HEATER.containsMatchIn(APPLIANCE.replace(q, " ")) && !INSTALLED.containsMatchIn(q)) ||
            (PROPANE_STOVE.containsMatchIn(q) && SPACE.containsMatchIn(KITCHEN.replace(q, " ")))
        return device || (ENGINE.containsMatchIn(q) && ENGINE_SPACE.containsMatchIn(q))
    }

    /** English question about running a fuel-burning device in an enclosed or attached space (no symptoms, no alarm). */
    fun isCoHazardQuestion(query: String): Boolean = !ACUTE.containsMatchIn(QUOTES.replace(query, "'")) && hazardContext(query)

    const val CO_HAZARD_EN =
        "Carbon monoxide (CO) from a generator, a charcoal or gas grill, a camping stove or lantern, or a fuel-burning heater that isn't professionally installed and vented to the outside is a poison you can't see or smell, and it can kill in minutes. " +
            "Never run them inside a home, garage, carport, basement, shed, tent or camper, even with the doors or windows open: that is not enough ventilation. " +
            "Run a generator outside only, at least 20 feet (6 metres) from any door, window or vent, with the exhaust pointing away from buildings. " +
            "Never leave a car or other engine running in a garage, even with the door open, and never heat a home with a gas oven or stove. " +
            "Have a battery-powered CO alarm, but an alarm does not make it safe to run these indoors. " +
            "If anyone gets a headache, dizziness, weakness, tiredness, nausea, vomiting, chest pain or confusion, or passes out, get everyone out into fresh air right away, call emergency services, and don't go back inside until you're told it's safe."

    const val CO_HAZARD_SOURCES_EN =
        "Sources: CDC, Power Outage and Carbon Monoxide guidance; US CPSC, Portable Generator Hazards and Generators and Engine-Driven Tools; NHS, Carbon monoxide poisoning."

    /** src/routing/context.ts emergencyLine(false): the last line when the question names no listed country. */
    private const val EMERGENCY_LINE_EN = "In an emergency, call your local emergency number (911 in the US, 112 in Europe)."

    fun coHazardAnswer(query: String): String {
        val q = QUOTES.replace(query, "'")
        val lead = if (ASK_PERMISSION.containsMatchIn(q) && !NO_LEAD_IF.containsMatchIn(q) && statedFeet(q).none { it >= 19.5 }) "No. " else ""
        return "$lead$CO_HAZARD_EN\n\n$CO_HAZARD_SOURCES_EN\n\n${EmergencyNumbers.emergencyNumbersLine(query, false) ?: EMERGENCY_LINE_EN}"
    }
}
