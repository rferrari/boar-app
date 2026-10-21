/**
 * HEALTHGAP: the planning/information forms of four health themes
 * that the learned emergency detector scores low and no lexical layer sees: a medicine dose ("How many mg of ibuprofen
 * for a 20 kg child?"), a poisoning ("Is 5 g of nutmeg toxic?", "Ate 30 g of death cap"), a carbon monoxide reading
 * ("CO reading 200 ppm in the house") and a survival time ("How long can a 2 year old go without water?").
 *
 * classify() is the predicate; answer.ts treats a hit as a detector fire (deps.healthgap only) and picks the no-source
 * answer by class. The term lists are normative (the native port copies them); the Python scan in the rule is their
 * source and the tests below pin both against the same goldens. Pure.
 */
import { FOLLOW_UP_WORDS } from "./context";

export type HealthGapClass = "dose" | "poison" | "co" | "survival";
export interface HealthGap {
  cls: HealthGapClass;
  /** poison only: the event form (a deadly mushroom named, or an ingestion verb + a substance): the card goes first. */
  event: boolean;
  /** The class came from the previous user turn joined with a follow-up-only current turn. */
  prevTurn: boolean;
}

const B = String.raw`(?<![\p{L}\p{N}_])`;
const E = String.raw`(?![\p{L}\p{N}_])`;
const W = String.raw`[\p{L}\p{N}_]`;
const bw = (alts: string) => new RegExp(`${B}(?:${alts})${E}`, "iu");

// ---- dose
const DRUG = bw(
  String.raw`ibuprofen|ibuprofeno|acetaminophen|paracetamol|tylenol|advil|calpol|nurofen|motrin|aspirin|aspirina|dipirona|metamizol|novalgina|amoxicillin|amoxicilina|azithromycin|antibiotics?|antibi[óo]tico|melatonin|benadryl|diphenhydramine|loratadine|cetirizine|antihistamine|loperamide|imodium|omeprazole|ondansetron|dramamine|insulin|insulina|codeine|tramadol|morphine|oxycodone|diazepam|lorazepam|xanax|valium|zolpidem|prednisone|dexamethasone|metformin|warfarin|iron tablets?|vitamin [a-k]\d?|painkillers?|analg[ée]sic[oa]?|rem[ée]dio|medicine|medication|medicamento|medicina`
);
const DOSE_WORD = bw(String.raw`dose|doses|dosage|dosagem|dosis|tablets?|pills?|capsules?|drops|gotas|gotitas|comprimidos?|pastillas?|cucharadas?|colheres?|teaspoons?|spoonfuls?|ml|mg|mcg|µg`);
const DOSE_INTENT = bw(
  String.raw`how (?:many|much)|quant[ao]s?|cu[áa]nt[ao]s?|too (?:much|many)|demais|is it (?:safe|ok|okay|too)|max(?:imum)?|m[áa]xim[oa]|can (?:i|she|he|we|they|my ${W}+) (?:have|take|give)|(?:what time|when) can|again|more|(?:every|per|each|a cada|cada) \d+ ?(?:h|hr|hrs|hours?|horas?|days?|dias?|d[íi]as?)` +
    String.raw`|what dose|dose of|twice|double|by mistake|accidentally|extra|missed|next (?:one|dose)|together`
);
const PATIENT = bw(String.raw`\d+ ?(?:kg|kilos?|lbs?|pounds?|months?|years?|yo|anos|meses|años)|child|kid|baby|toddler|infant|son|daughter|filh[oa]|criança|bebê|hij[oa]|niñ[oa]|bebé`);
const COOKING = bw(String.raw`recipe|bake|baking|flour|sugar|butter|dough|batter|yeast|cups?|oven|cooking`);
const DIGIT = /\d/u;
const WATER_PURIFY = new RegExp(
  String.raw`(?:purif${W}*|disinfect${W}*|treat${W}*|sanitiz${W}*|make .{0,20}safe to drink|safe to drink|potable|contaminated).{0,60}water|water.{0,60}(?:purif${W}*|disinfect${W}*|treat${W}*|safe to drink|potable|contaminated)`,
  "isu"
);
function isDose(q: string): boolean {
  if (DRUG.test(q)) return DOSE_INTENT.test(q) || DOSE_WORD.test(q) || PATIENT.test(q);
  return DOSE_WORD.test(q) && DIGIT.test(q) && DOSE_INTENT.test(q) && !COOKING.test(q) && !WATER_PURIFY.test(q);
}

// ---- poison
const HAZARD = bw(String.raw`death cap|destroying angel|amanita|cogumelo venenoso|hongo venenoso`);
const INGEST = bw(
  String.raw`ate|eaten|eat|drank|drunk|drink(?:ing)?|swallow${W}*|ingest${W}*|licked|chewed|took|taken|sniffed|inhal${W}*|engoli${W}*|bebeu|comeu|tom(?:ou|ei)|trag[óo]|bebi[óo]|comi[óo]` +
    String.raw`|got into|chewing|sucked|tomó|ingirió`
);
const SUBSTANCE = bw(
  String.raw`bleach|detergent|dish soap|laundry pods?|cleaner|cleaning (?:fluid|product)|disinfectant|hand sanitizer|sanitizer|mouthwash|kerosene|petrol|gasoline|diesel|paint|glue|solvent|mushrooms?|wild berries|berries|lily|oleander|foxglove|nutmeg|pills|tablets|medicine|medication|cigarettes?|nicotine|vape (?:juice|liquid)|e-?liquid|alcohol|battery|magnet|coin|silica gel|moth ?balls?|cannabis|edibles?|gummies|cogumelo|alvejante|cloro|lavandina|lej[íi]a|hongos?|setas?` +
    String.raw`|antifreeze|ethylene glycol|rat poison|pesticide|insecticide|weed ?killer|paraquat|lamp oil|lighter fluid|paint thinner|turpentine|drain cleaner|oven cleaner|button battery` +
    String.raw`|wine|beer|vodka|liquor|spirits|whiske?y|rum|gin|weed|gumm(?:y|ies)|pill|tablet|dishwasher (?:tablet|pod)s?|toilet cleaner|paraffin|white spirit|methanol|rubbing alcohol|isopropyl${W}*|nail polish remover|acetone|ammonia|lye|caustic soda|rodenticide|fertili[sz]er|slug pellets|ant bait|essential oils?|eucalyptus oil|tea tree oil|button cell|coin cell|água sanitária|soda cáustica|querosene|gasolina|chumbinho|raticida`
);
const POISON_WORD = bw(String.raw`poison(?:ed|ing|ous)?|toxic|toxicity|overdos${W}*|lethal dose|deadly dose|envenen${W}*|intoxica${W}*|veneno|venenos[oa]|t[óo]xic[oa]`);
const QUANTITY = bw(String.raw`\d+(?:[.,]\d+)? ?(?:g|mg|ml|grams?|gramas?|cups?|tablets?|pills?|drops?|bottles?|spoons?|teaspoons?|capsules?|comprimidos?|copos?|x[íi]caras?)`);
const IS_THIS = bw(String.raw`(?:is|are) (?:it|that|this|\d)|how (?:much|many)`);
const FOOD_POISONING = /food poisoning/iu;
const LETHAL_AMOUNT = bw(String.raw`(?:lethal|deadly|toxic|fatal) (?:dose|amount)`);
function poisonForm(q: string): "event" | "info" | null {
  if (WATER_PURIFY.test(q)) return null;
  if (HAZARD.test(q) || (INGEST.test(q) && SUBSTANCE.test(q))) return "event";
  if ((POISON_WORD.test(q) && (INGEST.test(q) || QUANTITY.test(q) || IS_THIS.test(q)) && !FOOD_POISONING.test(q)) || LETHAL_AMOUNT.test(q)) return "info";
  return null;
}

// ---- carbon monoxide (the token CO is case-sensitive: "co" is a word in PT/ES)
const CARBON_MONOXIDE = /carbon monoxide|mon[óo]xido de carbono/iu;
const PPM_HOME = new RegExp(String.raw`${B}\d+ ?ppm${E}(?=.{0,80}(?:house|home|room|cabin|garage|basement|rv|boat|tent|indoors|inside|bedroom|apartment|flat))`, "isu");
const CO_CS = new RegExp(
  String.raw`${B}CO(?![\p{L}\p{N}_])(?=.{0,40}(?:ppm|alarm|detector|reading|level|monitor|poison|leak|exposure))|${B}(?:ppm|alarm|detector|reading|level|monitor)s?${E}.{0,30}${B}CO(?![\p{L}\p{N}_])`,
  "su"
);
const CO_EXCLUDE = bw(String.raw`co2|carbon dioxide|chlorine|tds|nitrates?|fluoride|lead|radon|pm ?2[.,]5|particulates?|humidity|colorado`);
const CO_CITY = new RegExp(String.raw`,\s*CO${E}`, "u");
function isCo(q: string): boolean {
  if (CARBON_MONOXIDE.test(q)) return true;
  return (PPM_HOME.test(q) || CO_CS.test(q)) && !CO_EXCLUDE.test(q) && !CO_CITY.test(q);
}

// ---- survival time
const SURVIVAL = new RegExp(
  String.raw`how (?:long|many (?:hours|days|minutes)) (?:can|could|does|do|will|would) (?:a |an |the |some)?[\p{L}\p{N}_' -]{0,40}?(?:survive|go|last|live|stay alive|hold out)[^?]{0,40}?(?:without (?:water|food|eating|drinking|sleep|oxygen|air|insulin|dialysis)|in (?:cold|freezing|icy|the cold|the snow|the heat|the sun|the desert|\d+ ?°? ?[cf] water|\d+ ?degree))|(?:it'?s|it has|has) been \d+ (?:days|hours) (?:without|since (?:i|she|he|they|we) (?:ate|drank|had|slept))|without (?:water|food) for \d+ (?:days|hours)` +
    String.raw`|survive \d+ (?:days|hours) without|die (?:of|from) (?:thirst|dehydration|starvation|hunger)`,
  "isu"
);
const SURVIVAL_EXCLUDE = bw(String.raw`plants?|cactus|cacti|succulents?|trees?|seedlings?|lawn|grass|batter(?:y|ies)|phones?|laptops?|cars?|engines?|startups?|business(?:es)?|compan(?:y|ies)`);
const isSurvival = (q: string) => SURVIVAL.test(q) && !SURVIVAL_EXCLUDE.test(q);

/** Every class that fires on one text, in precedence order: poison-event > co > dose > poison-info > survival. */
export function healthGapClasses(q: string): string[] {
  const p = poisonForm(q);
  const out: string[] = [];
  if (p === "event") out.push("poison-event");
  if (isCo(q)) out.push("co");
  if (isDose(q)) out.push("dose");
  if (p === "info") out.push("poison-info");
  if (isSurvival(q)) out.push("survival");
  return out;
}

function classifyOne(q: string): Omit<HealthGap, "prevTurn"> | null {
  const top = healthGapClasses(q)[0];
  if (!top) return null;
  return { cls: (top.startsWith("poison") ? "poison" : top) as HealthGapClass, event: top === "poison-event" };
}

/** Follow-up-only words a split dose question uses ("when can she have more?") beyond isHealthFollowUp's list. */
const FOLLOW_UP_EXTRA = new Set(["more", "have", "another", "again", "dose", "doses", "much", "many", "too", "safe", "next", "one"]);
const words = (query: string) => query.toLowerCase().replace(/[¿¡?!.,;:]/g, " ").split(/\s+/).filter(Boolean);
function followUpOnly(query: string): boolean {
  const w = words(query);
  return w.length > 0 && w.length <= 12 && w.every((x) => FOLLOW_UP_WORDS.has(x) || FOLLOW_UP_EXTRA.has(x));
}

/**
 * The current turn's class; if it has none and it is a follow-up-only turn (≤ 12 words, all in the follow-up lists),
 * the class of the previous user turn joined with it ("I gave her Calpol at 9." + "when can she have more?" → dose).
 * The word guard keeps a new question out ("when can I visit Lisbon again?").
 */
export function classify(query: string, userTurns: string[] = []): HealthGap | null {
  const now = classifyOne(query);
  if (now) return { ...now, prevTurn: false };
  const last = userTurns[userTurns.length - 1];
  // A short turn that only adds a patient or an amount ("and for a 30 kg kid?", "what about 2 g?") continues a previous turn
  // that has a class of its own.
  const addsDetail = !!last && words(query).length <= 12 && (PATIENT.test(query) || QUANTITY.test(query)) && !!classifyOne(last);
  if (!last || !(followUpOnly(query) || addsDetail)) return null;
  const joined = classifyOne(`${last}. ${query}`);
  return joined ? { ...joined, prevTurn: true } : null;
}

/** Receipt codes for a gap turn (after the detector's own codes). */
export function healthGapCodes(gap: HealthGap): string[] {
  return [`healthgap:${gap.cls}`, ...(gap.event ? ["healthgap:event"] : []), ...(gap.prevTurn ? ["healthgap:prev-turn"] : [])];
}

/** The card a gap turn falls back to when no specific condition card applies: poisoning or CO; null for dose and survival. */
export function healthGapCard(gap: HealthGap | null): "poisoning" | "co" | null {
  return gap?.cls === "poison" ? "poisoning" : gap?.cls === "co" ? "co" : null;
}

/**
 * HEALTHGAP sources (rule v1.2 §2.5): the titles a gap turn's own source may carry, so the health topic filter keeps it.
 * Dose: a medicine the question names ("Vitamin D", "Ibuprofen"; generic words such as "medicine" never count). Survival: the
 * condition its cue names (cold → hypothermia / cold weather; water or heat → dehydration / desert / heat illness; food →
 * starvation). Title only, never body text. Poison and co: none (their cards stay first).
 */
const GENERIC_DRUG = /^(?:antibiotics?|antibi[óo]tico|painkillers?|analg[ée]sic[oa]?|rem[ée]dio|medicine|medication|medicamento|medicina|antihistamine|iron tablets?)$/iu;
const COLD_CUE = /cold|freez|icy|snow|winter|°|degree|frio|gelad/iu;
const HEAT_CUE = /desert|heat|hot|sun/iu;
const WATER_CUE = /water|thirst|drink|dehydrat/iu;
const FOOD_CUE = /food|eat|starv|hunger/iu;
const esc = (x: string) => x.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
export function healthGapTitle(gap: HealthGap | null, query: string, userTurns: string[] = []): RegExp | null {
  if (!gap || (gap.cls !== "dose" && gap.cls !== "survival")) return null;
  const q = gap.prevTurn ? `${userTurns[userTurns.length - 1] ?? ""}. ${query}` : query;
  let alts: string[];
  if (gap.cls === "dose") alts = [...new Set((q.match(new RegExp(DRUG.source, "giu")) ?? []).filter((d) => !GENERIC_DRUG.test(d)).map((d) => esc(d)))];
  else if (COLD_CUE.test(q)) alts = [String.raw`hypotherm${W}*`, "cold (?:weather|water|climates?)", "cold water immersion"];
  else
    alts = [
      ...(HEAT_CUE.test(q) ? ["desert", String.raw`heat (?:illness|exhaustion|stroke)`, "heatstroke"] : []),
      ...(WATER_CUE.test(q) || HEAT_CUE.test(q) ? [String.raw`dehydrat${W}*`] : []),
      ...(FOOD_CUE.test(q) ? ["starvation", "fasting", "malnutrition"] : []),
    ];
  return alts.length ? bw(alts.join("|")) : null;
}

/** A sentence that states an amount: a number and a dose unit. */
const DOSE_FIGURE = new RegExp(String.raw`\d[\d,.]*\s?(?:IU|international units?|mg|mcg|µg|micrograms?|milligrams?|ml|milliliters?|millilitres?)${E}`, "iu");
const DOSE_EXCERPT_MAX = 600;
/**
 * Dose with a kept source (rule v1.2 §2.5): the source's own sentences that state an amount, quoted verbatim and cited, from the
 * first source whose title names the medicine; then the dose line without its first sentence. null = no such sentence:
 * the caller gives the dose line. Never a number that is not in the source.
 */
export function doseExcerpt(sources: { title: string; body: string }[], title: RegExp | null, pt: boolean, es = false): string | null {
  if (!title) return null;
  for (let k = 0; k < sources.length; k++) {
    if (!title.test(sources[k].title)) continue;
    let quote = "";
    for (const s of sources[k].body.split(/(?<=[.!?])\s+/)) {
      if (!DOSE_FIGURE.test(s)) continue;
      if (quote && quote.length + s.length + 1 > DOSE_EXCERPT_MAX) break;
      quote = quote ? `${quote} ${s.trim()}` : s.trim();
    }
    if (!quote) continue;
    const lead = es && !pt ? "De la fuente offline (en inglés):" : pt ? "Da fonte offline (em inglês):" : "From the offline source:";
    return `${lead}\n${quote.slice(0, DOSE_EXCERPT_MAX)} [${k + 1}]\n\n${doseTail(pt, es)}`;
  }
  return null;
}

/** After a quoted amount: the dose line without its first sentence (label, child, overdose: the reviewed wording). */
function doseTail(pt: boolean, es: boolean): string {
  return noDoseAnswer(pt, es).split(/(?<=\.)\s+/).slice(1).join(" ");
}

/**
 * The no-source answer for a dose question (rule v1.1 §3.1): no medication dose from memory, the label and a pharmacist,
 * and the overdose line. Text reviewed by an independent Opus reviewer (HEALTHGAP review, 2026-10-15): AI-reviewed, not
 * clinically reviewed. PT/ES are translations of the reviewed EN text.
 */
export function noDoseAnswer(pt: boolean, es = false): string {
  if (es && !pt)
    return "No tengo una fuente offline confiable sobre esto, así que no voy a dar dosis de medicamentos de memoria. Usa la dosis y el intervalo entre dosis que indican el envase o el prospecto, o pregunta a un farmacéutico o a un médico. Para un niño, usa la dosis para su edad o su peso que indica la etiqueta y el dosificador que viene con el medicamento (vasito, jeringa o gotero); si la etiqueta no indica su edad o su peso, pregunta antes a un farmacéutico o a un médico. Si alguien pudo haber tomado más de la dosis máxima de la etiqueta, o el mismo principio activo en dos productos distintos, llama ahora a un centro de toxicología o al número de emergencias local (911 en gran parte de América, 112 en Europa), aunque parezca estar bien.";
  return pt
    ? "Não tenho uma fonte offline confiável sobre isso, então não vou indicar doses de remédios de memória. Use a dose e o intervalo entre as doses que estão na bula ou na embalagem, ou pergunte a um farmacêutico ou médico. Para uma criança, use a dose para a idade ou o peso dela indicada na bula e o medidor que vem com o remédio (copinho, seringa dosadora ou conta-gotas); se a bula não trouxer a idade ou o peso dela, pergunte antes a um farmacêutico ou médico. Se houver chance de alguém ter tomado mais do que a dose máxima da bula, ou o mesmo princípio ativo em dois remédios diferentes, ligue agora para o Disque-Intoxicação (0800 722 6001) ou para o serviço de emergência (192 SAMU ou 193 Bombeiros no Brasil, 112 na Europa, 911 nos EUA), mesmo que a pessoa pareça bem."
    : "I don't have a reliable offline source on this, so I won't give medication doses from memory. Use the dose and the time between doses printed on the package, or ask a pharmacist or doctor. For a child, use the dose for their age or weight on the label and the measuring device that comes with the medicine; if the label doesn't list their age or weight, ask a pharmacist or doctor first. If someone may have taken more than the label's maximum, or the same ingredient from two different products, call a poison control center or your local emergency number now (911 in the US, 112 in Europe), even if they seem fine.";
}
