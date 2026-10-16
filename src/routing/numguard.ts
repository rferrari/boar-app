// NUMGUARD: an English, single-topic numeric question that is a pure unit conversion, a clock calculation or an exact calculator
// match may be answered by a deterministic calculator when the learned detector fires; nothing else may answer it (answer.ts:
// no calculator answers -> the detector's refusal stands, a model never answers a fired message). Anything that names a body,
// person/age, drug, dose, symptom, injury, danger, alcohol or ingestion word, a body-range temperature, or that is not English,
// in this message or (for the lexicon) in any earlier user turn, keeps the detector's call. The regexes here are normative.
import { calculate } from "./calculators";
import { isHealthQuestion, isSpanishQuestion } from "./context";
import { isCardEmergency } from "./conditionCards";

// arithmetic.ts HEALTH + DANGER (verbatim): health, injury, medication or body words, and situations rather than word problems.
const HEALTH =
  /\b(?:heart|pulse|bpm|breath\w*|blood|dos(?:e|es|age|ing)|mg|mcg|ml|iv|drip|infusion|insulin|tablets?|pills?|fever|contractions?|labou?r|pain\w*|bleed\w*|injur\w*|symptoms?|medic\w*|drugs?|overdos\w*|poison\w*|bab(?:y|ies)|toddlers?|infants?|calories?|kcal|glucose|oxygen|sp[o0]2)\b/i;
const DANGER =
  /\b(?:trapped|stuck|rising|flood\w*|fire|smoke|drown\w*|sinking|emergency|leak\w*|gas|danger\w*|unsafe|collaps\w*|avalanche|hurricane|tornado|lost|dying|dead|unconscious|attack\w*|bitten|bite|stung|hypotherm\w*|frostbite|choking|suicid\w*|kill\w*)\b/i;
// A short explicit list on top: people, care settings and symptoms the two lists above do not name.
const PERSON_CARE =
  /\b(?:pregnan\w*|child(?:ren)?|kids?|son|daughter|sick|ill|illness|hospital|doctor|nurse|vomit\w*|diarrh\w*|rash|cough\w*|headaches?|ache\w*|dizz\w*|nause\w*|thermometer|body temperature|my temperature|weigh(?:t|s)? loss|bmi|alcohol|drunk|safe(?:ly)?|hurt\w*|wound\w*|burn\w*|cpr|(?:my|his|her|their|your|our) (?:temp|temperature)|\d+[\s-]*(?:year|month|week|day)s?[\s-]*old|paracetamol|acetaminophen|ibuprofen|aspirin|tylenol|advil|benadryl|antibiotic\w*|amoxicillin|units? of|urin\w*|pee\w*|poop\w*|stool|toxic\w*|bleach|ingest\w*|swallow\w*|drank|drink\w*|beers?|wine|vodka|bac|sober|bp|blood pressure|saturation|unconscious|wake up|survive|surviv\w*|without (?:food|water)|fell|fall(?:s|en)?|hit (?:his|her|my|their)? ?head|faint\w*|\w+'s (?:core )?temp\w*|core temp\w*|boy|girl|husband|wife|partner|mom|mum|mother|dad|father|grand\w*|friend|patient|newborn|stranded|chest|passed out|water broke)\b/i;
// A body temperature (30-45 °C, 86-113 °F) is a person's, whatever the wording.
const BODY_TEMP = /\b(?:3\d|4[0-5])(?:[.,]\d+)?\s?(?:°\s?|degrees? )?c(?:elsius)?\b|\b(?:8[6-9]|9\d|10\d|11[0-3])(?:[.,]\d+)?\s?(?:°\s?|degrees? )?f(?:ahrenheit)?\b/i;
// Accented Latin letters (not × ÷): not an English message.
const NON_EN = /[À-ÖØ-öø-ɏ]/;

const NUM = String.raw`\d[\d,]*(?:\.\d+)?`;
// A number followed by a unit, a currency or a percent.
const UNIT = new RegExp(
  String.raw`(?:[$€£¥]\s?${NUM}|${NUM}\s?(?:%|°\s?[cf]?\b|degrees?\b|(?:k?m|cm|mm|km\/h|kph|mph|mi|miles?|ft|feet|foot|inch(?:es)?|yards?|metres?|meters?|kilomet(?:re|er)s?|kg|g|lbs?|pounds?|ounces?|oz|tons?|tonnes?|l|litres?|liters?|gallons?|gal|pints?|cups?|bar|psi|kpa|hpa|mah|wh|kwh|w|watts?|v|volts?|amps?|hz|ghz|mhz|gb|mb|kb|tb|tokens?|bytes?|bits?|sec(?:ond)?s?|min(?:ute)?s?|h|hrs?|hours?|days?|weeks?|dollars?|euros?|usd|eur|gbp|cents?|btc|eth|sats?|gwei|coins?|million|billion|thousand)\b))`,
  "i"
);
const CLOCK = /\b\d{1,2}(?::\d{2}\s?(?:am|pm)?|\s?(?:am|pm))\b/i;
// A conversion target ("in psi", "to Celsius", "convert") or a clock question: the shape of a misfire, not of a situation.
const CONVERT =
  /\b(?:convert(?:s|ed|ing)?|(?:in|to|into) (?:fahrenheit|celsius|kelvin|psi|bar|kpa|hpa|mph|km\/h|kph|feet|ft|inches|cm|mm|metres|meters|miles|kilomet(?:re|er)s|yards|pounds|lbs|kg|kilograms|grams|ounces|oz|litres|liters|gallons|pints|cups|ml|watt-hours|wh|kwh|euros?|dollars?|usd|eur|gbp))\b/i;
const HOW_MANY_UNIT =
  /\bhow many (?:kilomet(?:re|er)s|km|miles|feet|ft|metres|meters|inches|cm|yards|pounds|lbs|kg|kilograms|grams|ounces|oz|litres|liters|gallons|pints|cups|psi|watt-hours|wh|kwh) (?:is|are|in|make)\b/i;
const WHEN = /\b(?:what time|when (?:does|do|will|did)|arrive|arrives)\b/i;

/** True when the question names a health, injury, danger or person-care word: the detector's call stands. */
export function numguardHealthLexicon(query: string): boolean {
  return HEALTH.test(query) || DANGER.test(query) || PERSON_CARE.test(query) || isHealthQuestion(query) || isCardEmergency(query);
}

/** A number with a unit plus a conversion target ("in psi", "to Celsius", "how many feet is"), or a clock time plus a clock question. */
export function numericIntent(query: string): boolean {
  // Identifiers ("EIP-4444", "DS-160") and years or decades ("in 1900", "the 1600s", "mid-2012") are not quantities.
  const q = query.replace(/\b[A-Za-z]+-\d+\b/g, " ").replace(/\b(?:1[5-9]\d\d|20\d\d)s?\b/g, " ");
  if (!/\d/.test(q)) return false;
  return (UNIT.test(q) && (CONVERT.test(q) || HOW_MANY_UNIT.test(q))) || (CLOCK.test(q) && WHEN.test(q));
}

/** The guard: English (not PT/ES, no accented letters), no body-range temperature, no health lexicon in this or any earlier user
 * turn, AND (a calculator match OR numeric intent). answer.ts lets only a calculator answer a guarded turn. */
export function numericGuard(query: string, pt: boolean, userTurns: string[] = []): boolean {
  // EN only (the lexicon is English, like the arithmetic and travel shortcuts; JS \b ignores accented letters, so any accented
  // letter blocks); a body-range temperature is a person's; any earlier user turn with a health word keeps the refusal.
  if (pt || isSpanishQuestion(query) || NON_EN.test(query) || BODY_TEMP.test(query)) return false;
  if ([query, ...userTurns].some(numguardHealthLexicon)) return false;
  return calculate(query, pt) !== null || numericIntent(query);
}
