// CO hazard (Compass 2026-10-03, ship L3 safety finding): "Can I run my portable generator in the attached garage if I keep
// the door open?" took the model path and got "Yes, … ensure it's well-ventilated". A planning question about a
// fuel-burning device in an enclosed or attached space now gets a fixed safety-fact answer, before any model.
// English only (bounty scope). Symptom or alarm questions are left to the health route (the reviewed CO card).
// Wording: Opus reviewer 2026-10-03 (REVIEWER.md, CHANGE applied). Sources (verbatim, compass/exp/co-hazard/SOURCES.md): CDC "What to Do to Protect Yourself During a Power Outage";
// US CPSC "Generators and Engine-Driven Tools"; NHS "Carbon monoxide poisoning".
import { emergencyLine } from "./context";
import { emergencyNumbersLine } from "./emergencyNumbers";

const B = "(?<![\\p{L}\\p{N}_])";
const E = "(?![\\p{L}\\p{N}_])";
const re = (s: string) => new RegExp(s, "iu");

/** A device that burns fuel. A plain "grill", "stove" or "heater" may be electric, so those need a fuel word. */
const DEVICE = re(
  [
    `${B}(?<!(?:random|number|code|site|password|static|image|text|qr|name|idea|key|signal|function|pulse|lorem|tone|wave|pdf|report|hash|ai|music|art|map|meme|solar|battery|jackery|ecoflow|bluetti)[ -])(?:generators?|gennys?|gennies)${E}`,
    `${B}(?<!activated )charcoal${E}(?! (?:drawing|pencil|filter|tablet|pill|capsule|mask|soap|toothpaste|grey|gray|colou?r|sketch|powder|supplement))`,
    `${B}(?:bbqs?|barbecues?|hibachis?)${E}`,
    `${B}(?:a|the|our|my|your) barbie${E}`,
    `${B}(?:gas|propane|charcoal|pellet|wood|camping|camp|portable gas|disposable) (?:grills?|smokers?)${E}`,
    `${B}(?:camping|camp|backpacking|coleman|primus|portable gas|canister|gas canister) (?:stoves?|burners?)${E}`,
    `${B}(?:propane|gas|kerosene|paraffin|butane|coleman) lanterns?${E}`,
    `${B}(?:gas|gasoline|petrol)[- ]powered${E}`,
    `${B}(?:pressure|power) washers?${E}`,
    `${B}(?:propane|butane|gas|coleman|camp|camping) (?:griddles?|torch(?:es)?|cookers?|rings?|fire ?pits?|lanterns?)${E}`,
    `${B}(?:jetboils?|pocket rockets?|blackstone(?: griddles?)?|weed torch(?:es)?|propane torch(?:es)?)${E}`,
    `${B}(?:kerosene|paraffin|oil) (?:lamps?|lanterns?|stoves?)${E}`,
    `${B}(?:gas|gasoline|petrol) (?:leaf blowers?|blowers?|lawn ?mowers?|mowers?|chainsaws?|engines?|tools?|trimmers?|augers?)${E}`,
  ].join("|")
);

/** Fuel heaters. Reviewer C2: an installed or vented appliance (water heater, furnace, boiler, direct-vent) is not this hazard. */
const HEATER = re(
  [
    `${B}(?:propane|kerosene|paraffin|butane|gas|diesel|oil|fuel|fuel-burning|camping|buddy|patio|catalytic|outdoor)(?: [\\p{L}-]+)? heaters?${E}`,
    `${B}(?:big|little) buddy${E}`,
    `${B}mr\\.? heater${E}`,
  ].join("|")
);
/** Follow-up (coordinator 2026-10-03): an installed appliance named in passing ("the furnace eats the battery") no longer vetoes the
 * whole message. Its name is removed before the heater test; only vented/direct-vent/flued/installed still veto. */
const APPLIANCE = new RegExp(`${B}(?:water heaters?|furnaces?|boilers?)${E}`, "giu");
const INSTALLED = re(`${B}(?:direct[- ]vent\\w*|vented|flued|installed)${E}`);
/** A propane or butane stove counts unless the only space named is a kitchen (a propane kitchen range is a normal appliance). */
const PROPANE_STOVE = re(`${B}(?:propane|butane) (?:stoves?|burners?)${E}`);
const KITCHEN = new RegExp(`${B}(?:(?:rv|camper|caravan|van|boat|motorhome|trailer|apartment|home|house)'?s? )?(?:kitchens?|galleys?)${E}`, "giu");

/** Using a gas oven or range to heat the home (CDC). */
const OVEN_HEAT = re(
  `${B}(?:gas )?(?:oven|stove|range)${E}.{0,40}${B}(?:to|for) (?:heat|warm)` +
    `|${B}(?:heat|heated|heating|warm|warmed|warming)${E}.{0,50}${B}(?:gas )?(?:oven|stove)${E} (?:on|open|with the door)` +
    `|${B}(?:oven|stove)${E} on with the door (?:open|down)`
);

/** An engine left running where the exhaust stays (CDC, NHS): a car warming up in the garage. */
const ENGINE = re(
  `${B}(?:car|truck|vehicle|engine|motor|motorcycle|motorbike|lawn ?mower|mower|chainsaw|snow ?blower|leaf blower|atv|quad|van|suv)s?${E}.{0,40}${B}(?:running|run|idle|idling|on|warm(?:ing)? up|start(?:ed|ing)?)${E}` +
    `|${B}(?:run|running|idle|idling|warm(?:ing)? up|leave|start)${E}.{0,25}${B}(?:car|truck|vehicle|engine|motor|motorcycle|motorbike|lawn ?mower|mower|snow ?blower|suv)s?${E}`
);

/** Generator placement near an opening ("is 10 ft from the back door ok?"): the 20-foot rule. */
const PLACEMENT = re(
  `${B}(?:generators?|gennys?)${E}.{0,80}${B}(?:how far|distance|gap|enough|far enough|minimum|close|ok|okay|safe|\\d+ ?(?:ft|feet|foot|m|metres|meters))${E}.{0,60}${B}(?:windows?|doors?|vents?|intakes?)${E}` +
    `|${B}(?:how far|distance|gap|minimum|\\d+ ?(?:ft|feet|foot|m|metres|meters))${E}.{0,60}${B}(?:windows?|doors?|vents?|intakes?)${E}.{0,60}${B}(?:generators?|gennys?)${E}` +
    `|${B}(?:how far|distance|gap|minimum)${E}.{0,40}${B}(?:generators?|gennys?)${E}.{0,60}${B}(?:windows?|doors?|vents?|intakes?)${E}` +
    `|${B}(?:is|are) \\d+ ?(?:ft|feet|foot|m|metres|meters)${E}.{0,40}${B}(?:windows?|doors?|vents?)${E}` +
    `|${B}(?:generators?|gennys?)${E}.{0,80}${B}(?:right next to|next to|right by|right under|under|beside|near|close to|up against)${E} (?:my |the |our |his |her |their |a |an )?(?:[\\p{L}-]+ ){0,2}(?:windows?|doors?|vents?|hatch(?:es)?)${E}`
);
/** Distances the message states, in feet. Every one at 20 ft (6 m) or more: the generator is already placed right. */
function statedFeet(q: string): number[] {
  return [...q.matchAll(/(\d+(?:\.\d+)?) ?(ft|feet|foot|m|metres|meters)(?![\p{L}])/giu)].map((m) => (/^m/i.test(m[2]) ? Number(m[1]) * 3.28 : Number(m[1])));
}

/** Heating or warming a space with a fuel burner ("warm up the tent with a gas canister stove"). */
const HEAT_SPACE = re(
  `${B}(?:heat|warm)(?:ing)?(?: up)? (?:the |my |our |a )?(?:[\\p{L}-]+ )?(?:tent|room|house|home|apartment|camper|van|cabin|shack|shelter|garage|basement|crawl ?space)${E}.{0,40}${B}(?:stoves?|ovens?|grills?|bbqs?|heaters?|burners?|lanterns?)${E}`
);

/** Storing or winterising, nothing running. */
const STORAGE = re(`${B}(?:stor(?:e|ing|age)|winteri[sz]\\w*|drain(?:ing)?|put (?:it )?away|for the (?:summer|winter|season|off-season))${E}`);
/** Fiction or history writing, not a plan. */
const FICTION = re(`${B}(?:novel|my character|screenplay|short story|fiction|historically|historical)${E}`);
const RUNNING = re(`${B}(?:run|running|use|using|operate|operating|start|starting|turn(?:ed|ing)? on|fire up|light|lighting)${E}`);

/** An enclosed or attached space. Bare "inside" is not enough ("inside the function"). */
const SPACE = re(
  [
    `${B}indoors?${E}`,
    `${B}(?:inside|in|into) (?:the |my |our |a |your |an |his |her |their )?(?:[\\p{L}-]+ ){0,2}(?:house|home|bedroom|living room|apartment|flat|cabin|room|tent|camper|garage|basement|shed|van|minivan|car|suv|truck|truck cab|vehicle|rv|caravan|trailer|motorhome|boat|yurt|hut|shelter|dorm|condo|workshop|barn|porch|sunroom|conservatory)${E}`,
    `${B}garages?${E}(?! (?:sale|band|door opener))`,
    `${B}carports?${E}`,
    `${B}on (?:the |my |our |a )?(?:enclosed |screened |screened-in |covered |closed-in )?(?:porch|sunroom|veranda)${E}`,
    `${B}tents?${E}`,
    `${B}(?:campers?|rvs?|motorhomes?|caravans?|camper ?vans?|travel trailers?)${E}`,
    `${B}(?:basements?|cellars?|crawl ?spaces?|sheds?)${E}`,
    `${B}(?:enclosed|screened[- ]in|closed|covered) (?:porch|patio|balcony|space|area|room|deck)${E}`,
    `${B}(?:ice|fishing|pop-up|popup) (?:fishing )?(?:shack|house|hut|shanty|tent|shelter)s?${E}`,
    `${B}shacks?${E}`,
    // v2 (dev2): a fuel device is already required, so a bare "inside" or a room name is enough here.
    `${B}(?:inside|in here|in there)${E}`,
    `${B}(?:bathrooms?|kitchens?|stalls?|barns?|coops?|stables?|shant(?:y|ies)|cabins?|workshops?|below deck|bunkhouse)${E}`,
    `${B}while (?:we|i|you|they|the kids|everyone) (?:sleep|are asleep|'re asleep)${E}|${B}(?:overnight|all night)${E}`,
  ].join("|")
);

/** Someone may already be poisoned or an alarm is sounding: that is the health route's CO card, not this answer. */
const ACUTE = re(
  `${B}(?:headaches?|head (?:hurts|is (?:pounding|killing me|splitting))|pounding head|dizz\\w*|nause\\w*|vomit\\w*|throwing up|threw up|throw(?:s|n)? up|passed out|faint\\w*|unconscious|confus\\w*|drowsy|sleepy|chest pain|can'?t breathe|short of breath|barely waking|(?:won'?t|can'?t|hard to|not) wak(?:e|ing)(?: up)?|slurr\\w*|feel(?:s|ing)? (?:sick|ill|weird|woozy|weak))${E}` +
    `|${B}(?:alarm|detector)s?${E}.{0,60}${B}(?:going off|went off|beeping|sounding|chirp\\w*|is on|says|reads|reading|shows)${E}|${B}\\d+ ?ppm${E}`
);

/** "Can I / Is it ok to <run|use…>": a request to do it. Only then does the answer start with "No." (reviewer C1). */
const ASK_PERMISSION = re(
  `(?:^|[.?!]\\s+)(?:can|could|may|should|shall) (?:i|we)${E} (?:still |just |safely )?(?:run|use|put|keep|leave|bring|start|operate|light|cook|warm|heat|idle|set up|plug)${E}` +
    `|${B}is it (?:ok|okay|safe|fine|alright|all right) (?:for (?:me|us) )?to${E}`
);
/** Risk questions ("can you die", "is it dangerous"), outdoor or either/or alternatives, installed appliances: a bare "No." would mislead. */
const NO_LEAD_IF = re(
  `${B}(?:dangerous|danger|bad|die|dies|death|kill\\w*|poison\\w*|hurt|harm\\w*|worr\\w*|concern\\w*|risk\\w*|safer|unsafe|outside|outdoors?|out of|yard|driveway|instead|rather|or|not|away|move|moving|vent(?:ed)?|direct-vent|installed|flue|chimney|water heater|furnace|boiler)${E}`
);

/** A fuel-burning device in an enclosed or attached space (or near its openings), symptoms aside. */
function hazardContext(query: string): boolean {
  // Common misspellings (dev2): "generater in the garge", "propain".
  const q = query
    .replace(/[’‘]/g, "'")
    .replace(/(?<![\p{L}])gen[ae]?rat[eoa]?rs?(?![\p{L}])/giu, "generator")
    .replace(/(?<![\p{L}])gar(?:ge|adge|rage)(?![\p{L}])/giu, "garage")
    .replace(/(?<![\p{L}])propain(?![\p{L}])/giu, "propane")
    .replace(/(?<![\p{L}])kerosine(?![\p{L}])/giu, "kerosene");
  if (OVEN_HEAT.test(q) || HEAT_SPACE.test(q)) return true;
  if (PLACEMENT.test(q)) {
    const ft = statedFeet(q);
    if (!(ft.length && ft.every((d) => d >= 19.5))) return true;
  }
  if (!SPACE.test(q)) return false;
  if ((STORAGE.test(q) && !RUNNING.test(q)) || FICTION.test(q)) return false;
  const device =
    DEVICE.test(q) ||
    (HEATER.test(q.replace(APPLIANCE, " ")) && !INSTALLED.test(q)) ||
    (PROPANE_STOVE.test(q) && SPACE.test(q.replace(KITCHEN, " ")));
  return device || (ENGINE.test(q) && /(?<![\p{L}\p{N}_])(?:garages?|carports?|shed|enclosed|basement|crawl ?space)(?![\p{L}\p{N}_])/iu.test(q));
}

/** English question about running a fuel-burning device in an enclosed or attached space (no symptoms, no alarm). */
export function isCoHazardQuestion(query: string): boolean {
  return !ACUTE.test(query.replace(/[’‘]/g, "'")) && hazardContext(query);
}

/** The same device-and-space context WITH symptoms or an alarm: possible CO poisoning now. The health route gives it the
 * CO card (Forge shipcut1 sanity: "generator in the garage … my head hurts, dizzy and sick" got the generic no-source line). */
export function isCoExposure(query: string): boolean {
  const q = query.replace(/[’‘]/g, "'");
  // A CO alarm going off is CO exposure by itself (Sonar ce-0485); a chirp is usually the battery, so that needs symptoms.
  if (CO_ALARM.test(q) && (SOUNDING.test(q) || symptoms(q))) return true;
  if (!ACUTE.test(q)) return false;
  if (hazardContext(query)) return true;
  if (ENGINE_EXHAUST.test(q) && (SPACE.test(q) || BOAT.test(q))) return true;
  // With symptoms already present, a plain "heater" or "stove" is enough when a space, an alarm or several people are
  // involved ("my whole family has a headache and the heater is on"; "the alarm next to the heater in our cabin").
  return BURNER.test(q) && (SPACE.test(q) || ALARM.test(q) || GROUP.test(q));
}
/** Symptoms only (ACUTE without the alarm part). */
const symptoms = (q: string) => ACUTE.test(q.replace(ALARM_SOUND, " "));
const ALARM_SOUND = new RegExp(`${B}(?:alarm|detector)s?${E}.{0,60}${B}(?:going off|went off|beeping|sounding|chirp\\w*|is on|says|reads|reading|shows)${E}|${B}\\d+ ?ppm${E}`, "giu");
const CO_ALARM = re(`${B}(?:co|carbon monoxide)${E} ?(?:alarms?|detectors?)${E}`);
const SOUNDING = re(`${B}(?:going off|went off|goes off|beeping|sounding|alarming|keeps going|\\d+ ?ppm)${E}`);
const ENGINE_EXHAUST = re(`${B}(?:(?:engine|motor|generator|car|truck|boat) exhaust|exhaust fumes|(?:engine|motor)s? (?:running|on|idling))${E}`);
const BOAT = re(`${B}(?:boats?|cabin|below deck|yacht|cruiser)${E}`);

/** Previous user turn names the device (or a CO alarm), the current turn has the symptoms ("generator running in the basement"
 * / "now grandma is short of breath"). Sonar: the immediately previous user turn only. */
export function isCoExposureAcrossTurns(previousTurn: string, query: string): boolean {
  const q = query.replace(/[’‘]/g, "'"), p = previousTurn.replace(/[’‘]/g, "'");
  if (!symptoms(q)) return false;
  if (isCoExposure(previousTurn) || (CO_ALARM.test(p) && SOUNDING.test(p))) return true;
  // exposure2.1 (Sonar): the previous turn must say the device is in use; a planning, shopping or sizing question never
  // carries over ("what size generator for my garage workshop?" / "headache from stress").
  if (!IN_USE.test(p) || PLANNING.test(p)) return false;
  return hazardContext(previousTurn) || (ENGINE_EXHAUST.test(p) && (SPACE.test(p) || BOAT.test(p)));
}
const IN_USE = re(
  `${B}(?:running|(?:has|have|had|'s|'ve) been (?:on|running|going|burning)|been using|using|burning|lit|fired up|started|turned on|left (?:it |the \\w+ )?on|(?:is|was|are|were) on)${E}`
);
const PLANNING = re(
  `${B}(?:what size|which (?:size|one|model|brand)|how (?:big|much|many)|buy(?:ing)?|shopping|recommend\\w*|best (?:generator|heater|brand)|brand tips|planning to|thinking (?:of|about))${E}|(?:^|[.?!]\\s+)(?:can|could|may|should|shall) (?:i|we)${E}|${B}is it (?:ok|okay|safe|fine|alright|all right) to${E}`
);

const BURNER = re(`${B}(?:heaters?|stoves?|fireplaces?|wood ?burners?|furnaces?|boilers?|grills?|bbqs?|barbecues?)${E}`);
const ALARM = re(`${B}(?:alarms?|detectors?)${E}`);
const GROUP = re(`${B}(?:whole family|all of us|everyone|we all|we both|all (?:\\d+|two|three|four|five) of us|the kids|both kids|the family)${E}`);

export const CO_HAZARD_EN =
  "Carbon monoxide (CO) from a generator, a charcoal or gas grill, a camping stove or lantern, or a fuel-burning heater that isn't professionally installed and vented to the outside is a poison you can't see or smell, and it can kill in minutes. " +
  "Never run them inside a home, garage, carport, basement, shed, tent or camper, even with the doors or windows open: that is not enough ventilation. " +
  "Run a generator outside only, at least 20 feet (6 metres) from any door, window or vent, with the exhaust pointing away from buildings. " +
  "Never leave a car or other engine running in a garage, even with the door open, and never heat a home with a gas oven or stove. " +
  "Have a battery-powered CO alarm, but an alarm does not make it safe to run these indoors. " +
  "If anyone gets a headache, dizziness, weakness, tiredness, nausea, vomiting, chest pain or confusion, or passes out, get everyone out into fresh air right away, call emergency services, and don't go back inside until you're told it's safe.";

export const CO_HAZARD_SOURCES_EN =
  "Sources: CDC, Power Outage and Carbon Monoxide guidance; US CPSC, Portable Generator Hazards and Generators and Engine-Driven Tools; NHS, Carbon monoxide poisoning.";

export function coHazardAnswer(query: string): string {
  const q = query.replace(/[’‘]/g, "'");
  const lead = ASK_PERMISSION.test(q) && !NO_LEAD_IF.test(q) && !statedFeet(q).some((d) => d >= 19.5) ? "No. " : "";
  return `${lead}${CO_HAZARD_EN}\n\n${CO_HAZARD_SOURCES_EN}\n\n${emergencyNumbersLine(query, false) ?? emergencyLine(false)}`;
}
