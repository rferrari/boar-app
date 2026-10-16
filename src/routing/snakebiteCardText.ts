/**
 * SC (cut 3 dev; coordinator 4B-LOSS-ANATOMY, arm 2): the English text of the snakebite card (SBt, cut-3 final candidate), in ONE
 * place so a reviewer's change is a text edit here and nothing else. Text = the independent reviewer's replacement text verbatim
 * (rnd/research/coordinator/arms/sc-REVIEW.md, 2026-10-04, verdict CHANGE -> approved after these edits).
 *
 * Sources (all fetched live with WebFetch on 2026-10-04; official health agencies only):
 * | key   | page                                                    | URL                                                                                      | page date shown            |
 * | CDC-W | CDC, How to Make Water Safe in an Emergency             | https://www.cdc.gov/water-emergency/about/index.html                                     | last updated Sept. 19, 2024 |
 * | EPA-W | US EPA, Emergency Disinfection of Drinking Water        | https://www.epa.gov/ground-water-and-drinking-water/emergency-disinfection-drinking-water | last updated Feb 24, 2026  |
 * | WHO-S | WHO, Questions and answers: Snakebite envenoming        | https://www.who.int/news-room/questions-and-answers/item/snakebite-envenoming            | 20 November 2019           |
 * | CDC-S | CDC NIOSH, Venomous Snakes at Work                      | https://www.cdc.gov/niosh/outdoor-workers/about/venomous-snakes.html                    | reviewed Sept. 23, 2026    |
 * | NHS-S | NHS, Snake bites                                        | https://www.nhs.uk/conditions/snake-bites/                                               | last reviewed 17 July 2026 |
 * | HD-S  | healthdirect (Australian Government), Snake bites       | https://www.healthdirect.gov.au/snake-bites                                              | last reviewed October 2024 |
 * Sentence-to-quote mapping: arms/sc-cards-REVIEW.md + arms/sc-REVIEW.md. AI-reviewed, not clinically reviewed.
 */

/** Snakebite first aid (WHO, CDC NIOSH, NHS, healthdirect). */
export const SNAKEBITE_EN =
  "Snakebite, first aid: move away from the snake and don't try to catch or kill it. Call emergency services now; " +
  "if you are alone, call right away and follow their instructions. Keep the person still and calm, lying down, on " +
  "their side if possible, and stop them from moving around at all; carry them on a makeshift stretcher if you can, " +
  "and get them to medical care without delay, for antivenom: don't wait for symptoms. Take off rings, watches and " +
  "bracelets, and loosen clothing near the bite, before swelling starts. Cover the bite with a clean, dry dressing. " +
  "Mark the edge of the swelling on the skin and write down the time. Do NOT use a tourniquet, do NOT cut the " +
  "wound, and do NOT suck the venom or put chemicals on it. Don't put ice on it or soak it in water. Don't drink " +
  "alcohol, and don't take aspirin, ibuprofen or naproxen. If you can do it safely from a distance, take a photo of " +
  "the snake to help identify it. If the person stops responding or stops breathing normally, start CPR.";

export const SNAKEBITE_SOURCES_EN =
  "Sources: WHO, questions and answers on snakebite envenoming (2019); CDC NIOSH, Venomous Snakes at Work; NHS, Snake bites; healthdirect Australia, Snake bites.";

/** The reviewed EN snakebite card's previous text (cut 2, firstAidCards.ts), body + source line, as the card starts. */
const PREVIOUS_EN = "Snakebite, first aid: move away from the snake and don't try to catch or kill it. Keep the person still and calm, and stop them from moving around at all; carry them on a makeshift stretcher if you can, and get them to medical care without delay. Do NOT use a tight tourniquet, do NOT cut the wound, and do NOT suck the venom or put chemicals on it.\n\nSource: WHO, questions and answers on snakebite envenoming (2019).\n\n";

/**
 * SBt: the cut-2 snakebite card with its English body and sources replaced by the reviewed text. The trigger and the card
 * code (firstAidCards.ts, conditionCards.ts) stay byte-identical to cut 2; only the EN text the card starts with changes.
 * PT/ES cards, any other card and null pass through untouched.
 */
export function withReviewedSnakebiteText<T extends string | null>(card: T): T {
  if (card === null || !card.startsWith(PREVIOUS_EN)) return card;
  return `${SNAKEBITE_EN}\n\n${SNAKEBITE_SOURCES_EN}\n\n${card.slice(PREVIOUS_EN.length)}` as T;
}
