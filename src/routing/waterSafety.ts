/**
 * Emergency drinking-water card. "After a flood the tap water might be contaminated. How do I make water safe to drink?"
 * got an off-topic travel-guide line ("Tap water is not safe to drink…") instead of the treatment steps. A question about
 * making DRINKING water safe in an emergency (flood, storm, outage, boil-water notice, contamination, a flooded well, only
 * creek or lake water left) now gets a fixed card from CDC and US EPA text, before any model, in the carbon-monoxide card's
 * pattern (coHazard.ts). English only.
 *
 * Trigger: a drinking-water subject + a make-safe ask (boil, bleach, disinfect, purify, treat, "safe to drink", "drinkable")
 * + an emergency context + a present need (a person, or a how-to / can-I / is-it ask). Never swimming, pools, laundry,
 * cooking, appliances, plumbing, insurance or driving; never history, how-it-works or routine backcountry filtering.
 * It is tuned for precision: on its last fresh set it fired on 18 of 25 water questions and on none of 75 others (snakebite
 * and unrelated). A phrasing it misses takes the normal health route.
 *
 * Text: an independent reviewer's replacement text, every sentence matched to a quote from the two pages below (fetched
 * 2026-10-04; both re-checked 2026-10-19, figures unchanged). AI-reviewed, not clinically reviewed.
 * | CDC   | How to Make Water Safe in an Emergency  | https://www.cdc.gov/water-emergency/about/index.html                                      | updated Sept. 19, 2024 |
 * | EPA   | Emergency Disinfection of Drinking Water | https://www.epa.gov/ground-water-and-drinking-water/emergency-disinfection-drinking-water | updated Feb. 24, 2026  |
 * Boiling at altitude: CDC says 3 minutes above 6,500 ft, EPA above 1,000 m; the card uses the lower (EPA) threshold.
 * US government works, public domain.
 */

const B = "(?<![\\p{L}\\p{N}_])";
const E = "(?![\\p{L}\\p{N}_])";
const re = (s: string) => new RegExp(s, "iu");

/** Drinking water as the subject. */
// The faucet or tap itself, rain barrels, and creek/stream/river/lake named without "water".
const WATER = re(
  `${B}(?:(?:tap|drinking|well|stream|creek|river|lake|rain|flood|cistern|tank) ?water|water|wells?|cisterns?|faucets?|taps?|rain ?barrels?|creeks?|streams?|rivers?|lakes?|springs?)${E}`
);
/** Asking how to make it safe, or whether it is safe to drink. */
const MAKE_SAFE = re(
  [
    `${B}(?:boil(?:s|ed|ing)?|bleach|chlorin\\w*|disinfect\\w*|purif\\w*|steriliz\\w*|sanitiz\\w*|treat(?:s|ed|ing|ment)?)${E}`,
    `${B}(?:safe to drink|safe for (?:drinking|us to drink|the kids to drink)|ok(?:ay)? to drink|drinkable|potable|safely drink|(?:can|could|should) (?:i|we|you) (?:still )?drink)${E}`,
    `${B}(?:pool shock|calcium hypochlorite|water purification tablets?|purification tablets?|chlorine tablets?)${E}`,
    `${B}make (?:\\w+ ){0,3}(?:safe|drinkable|potable)${E}`,
    `${B}iodine${E}`,
  ].join("|")
);
/** The emergency that makes the water suspect. */
const CONTEXT = re(
  [
    `${B}(?:flood\\w*|hurricanes?|storms?|typhoons?|cyclones?|tornado\\w*|earthquakes?|tsunamis?|wildfires?|disasters?|emergenc\\w*|outages?|blackouts?|evacuat\\w*|shelters?)${E}`,
    `${B}power(?:'s| is| has been| was)? (?:out|down|off)${E}`,
    `${B}no (?:power|electricity)${E}`,
    `${B}boil(?:[- ]water)? (?:notice|advisory|advisories|order|alert)${E}`,
    `${B}(?:water )?mains? (?:break|broke|burst)${E}|${B}water main${E}`,
    `${B}contaminat\\w*${E}|${B}e\\.? ?coli${E}|${B}(?:coliform|bacteria|benzene|sewage|cholera|giardia|cryptosporidium)${E}|${B}unsafe${E}|${B}not safe${E}|${B}until further notice${E}`,
    `${B}do[- ]not[- ]drink${E}|${B}don'?t drink (?:notice|order|advisory|alert)${E}|${B}(?:broken|burst) pipes?${E}`,
    `${B}(?:wells?|cisterns?|tanks?)${E}`,
  ].join("|")
);
/** Lookalikes: swimming and pools, laundry, cooking, appliances and plumbing, insurance, driving, mold, how much to drink. */
const NOT = re(
  [
    `${B}(?:swim\\w*|pools?(?! shock)|hot ?tubs?|aquariums?|beach|sea|surf\\w*|laundry|stains?|whites|mou?ld|drywall|carpets?|insurance|insured|sandbags?)${E}`,
    `${B}(?:eggs?|pasta|potato(?:es)?|rice|noodles?|recipe)${E}`,
    `${B}(?:water heaters?|pilot|water pressure|leaky pipes?|plumb\\w*|dispenser|ice (?:machines?|makers?)|sparkling|carbonat\\w*|water bill|pump repair)${E}`,
    `${B}(?:driv\\w*|car|truck)${E}`,
    `${B}how much water (?:should|do|does|to)${E}`,
  ].join("|")
);

// A present need only. Never history, how-it-works or general curiosity about water treatment
// ("when did boil-water advisories start", "how do treatment plants remove bacteria"), and never routine backcountry
// filtering: a stream or creek alone is no longer an emergency context, and backpacking / backcountry trips are out of
// scope for a disaster card.
const CURIOSITY = re(
  [
    `${B}(?:histor\\w*|invent\\w*|origins?|originally|in the past|centur(?:y|ies)|when did|who (?:started|invented|decided))${E}`,
    `${B}why (?:do|does|did|are|is) (?:cities|towns|utilities|governments?|authorities|they|people|the city)${E}`,
    `${B}how (?:do|does|did|is|are) (?:cities|towns|utilities|municipal\\w*|(?:water )?(?:treatment )?plants?|the city|they|water utilities)${E}`,
    `${B}(?:treatment plants?|municipal\\w*|water utilit(?:y|ies))(?: [\\p{L}-]+){0,3} (?:remov|kill|filter|clean|treat|work|process|use|add)\\w*${E}|${B}reverse osmosis${E}`,
    `${B}(?:backcountry|backpack\\w*|thru-?hik\\w*|ultralight|long-distance hike)${E}`,
  ].join("|")
);
/** Someone needs drinkable water now: a person, or a how-to / can-I / is-it ask. */
const PRESENT = re(
  `${B}(?:i|i'm|i've|we|we're|we've|my|our|me|us|you|how to|how long|how many|how much|steps?|should|can|could|do i|do we|is it|is the|is our|is my|make|what do|what's|whats|what is|(?:best|safest|quickest) way)${E}` +
    `|^\\s*(?:purify|boil|disinfect|treat|sterili[sz]e|filter|chlorinate)${E}`
);

export function isWaterSafetyQuestion(query: string): boolean {
  const q = query.replace(/[’‘]/g, "'");
  return WATER.test(q) && MAKE_SAFE.test(q) && CONTEXT.test(q) && PRESENT.test(q) && !NOT.test(q) && !CURIOSITY.test(q);
}

/** Making drinking water safe after a flood, an outage or a boil-water notice (CDC, EPA). */
export const WATER_SAFETY_EN =
  "Use bottled water, or water you stored safely before the emergency, if you have it. If your local water " +
  "authority has issued a notice, follow its instructions; your local health department can also advise you. " +
  "Otherwise, boil the water. If it is cloudy, first let it settle or filter it through a clean cloth, paper towel " +
  "or coffee filter. Bring the clear water to a rolling boil for 1 minute (3 minutes if you are above 1,000 metres, " +
  "about 3,300 feet), let it cool, and store it in clean containers with tight covers. If you can't boil it, use " +
  "regular unscented household chlorine bleach. For bleach labelled 5–9% sodium hypochlorite, add 2 drops per litre " +
  "or 8 drops per gallon; for 1% bleach, add 10 drops per litre or 40 drops per gallon. Use double the amount if " +
  "the water is cloudy, coloured or very cold. Stir and let it stand for at least 30 minutes before you drink it. " +
  "It should smell slightly of chlorine; if it doesn't, add the same amount again and wait another 15 minutes. " +
  "Bleach doesn't work as well as boiling against some germs, such as the parasites Cryptosporidium and Giardia. " +
  "Boiling and bleach don't remove fuel, toxic chemicals, heavy metals, salt or radioactive material: if the water " +
  "might contain any of these, don't drink it, and use bottled water instead. Don't drink from a private well that " +
  "was flooded until it has been disinfected and tested.";

export const WATER_SAFETY_SOURCES_EN =
  "Sources: CDC, How to Make Water Safe in an Emergency; US EPA, Emergency Disinfection of Drinking Water.";

/** The card: the reviewed text and its sources line. No emergency-numbers line: it is not an acute-harm card. */
export function waterSafetyAnswer(): string {
  return `${WATER_SAFETY_EN}\n\n${WATER_SAFETY_SOURCES_EN}`;
}
