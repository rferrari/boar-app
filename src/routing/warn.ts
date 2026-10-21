/**
 * WARN: the unsafe-answer warning. A finished turn that a model wrote (M) about something health-shaped (H) gets one
 * banner above the answer, with one of three tiers picked by grounding:
 *   no-source      no source was kept: the model answered from memory;
 *   beyond-source  sources were kept, but the answer cites none, or its first content sentence is uncited, or under
 *                  half of its content sentences are cited (uncited sentences, not a contradiction check);
 *   sourced        cited and covered. Grounding does not make an answer safe, so this tier still warns.
 * Reason codes only: no model-based gate. The term list (WARN_LEX) is normative (the native port copies it); word
 * boundaries are [\p{L}\p{N}_] (JS \w is ASCII even with /u). answer.ts runs it with deps.warn only. Pure.
 */
import { isPortugueseQuestion, isSafetyQuery, isSpanishQuestion } from "./context";
import type { WarnTier } from "./events";

export type { WarnTier };
export type WarnLang = "en" | "pt" | "es";

/**
 * The copy's language, Spanish first as the snakebite card picks it: isPortugueseQuestion also fires on Spanish ("Me picó una
 * culebra, que hago?"), and isSpanishQuestion's word list holds no words Portuguese shares.
 */
export function warnLang(query: string): WarnLang {
  return isSpanishQuestion(query) ? "es" : isPortugueseQuestion(query) ? "pt" : "en";
}

/** Receipts of answers no model wrote (answer.ts NOT_A_MODEL) plus small talk. */
const NOT_A_MODEL = new Set(["grounding-guard", "extractive", "none", "places", "calculator", "small-talk"]);
/** detector:p at or above this counts as health-shaped (the fire threshold is 0.2; the non-health self-tests max at 0.03). */
export const WARN_P = 0.05;

const B = String.raw`(?<![\p{L}\p{N}_])`;
const E = String.raw`(?![\p{L}\p{N}_])`;
const W = String.raw`[\p{L}\p{N}_]`;

/** Health words in the user's question (normative). */
export const WARN_LEX = new RegExp(
  `${B}(?:` +
    [
      // drugs / dosing (a superset of HEALTHGAP's DRUG, without the class logic)
      String.raw`ibuprofen${W}*|acetaminophen|paracetamol|tylenol|advil|calpol|nurofen|aspirin${W}*|dipirona|amoxicillin${W}*|antibiotics?|antibi[óo]tic${W}*|antihistamines?|insulin${W}*|opioids?|codeine|tramadol|morphine|benzodiazepines?|melatonin|vitamins?|supplements?|medicines?|medications?|medicamentos?|rem[ée]dios?|drugs? (?:interaction|dose)|overdos${W}*|mg/kg`,
      // poison / toxicity / exposure
      String.raw`poison${W}*|toxic${W}*|t[óo]xic[oa]s?|venom${W}*|veneno${W}*|carbon monoxide|ppm`,
      // diseases, conditions, symptoms
      String.raw`dengue|malaria|cholera|rabies|tetanus|typhoid|measles|covid${W}*|influenza|flu|hepatitis|hiv|tuberculosis|diabet${W}*|asthma${W}*|allerg${W}*|anaphyla${W}*|infection${W}*|infected|fever|febre|fiebre|symptoms?|sintomas?|s[íi]ntomas?|disease${W}*|doen[çc]a${W}*|enfermedad${W}*|illness|sickness|nausea|vomit${W}*|v[ôo]mit${W}*|diarrh${W}*|diarr[eé]i?as?|infec[çc][ãõa]o${W}*|infecci[óo]n${W}*|dehydrat${W}*|hypotherm${W}*|heat ?stroke|frostbite|concussion|seizures?|stroke|heart attack|cardiac|blood pressure|cholesterol|cancer|tumou?r|pregnan${W}*|gr[áa]vida|embarazada|breastfeed${W}*|vaccin${W}*|vacina${W}*|vacuna${W}*|wounds?|bleed${W}*|fractur${W}*|sprain${W}*|bites?|bitten|stings?|stung|burns? (?:on|from|to) (?:my|the|his|her|a)|injur${W}*|pain|dor|dolor|duel(?:e|en)|dói|doendo|queimadura${W}*|quemadura${W}*|picada${W}*|picadura${W}*|mordida${W}*|mordedura${W}*`,
      // red flags without a condition word (unexplained weight loss). The amount needs a person as its subject: "We lost 6 kg in the
      // bike frame" and "the airline dropped 20 kg" are no hit; a subjectless "Dropped 6 kg in 2 weeks without trying" is caught by
      // "without trying".
      String.raw`(?:i|i['’]ve|i have|i['’]m|i am|he|she|he['’]s|she['’]s|(?:my|our|his|her) (?:child|son|daughter|kid|baby|wife|husband|mother|father|mom|mum|dad|partner))(?: have| has| had)? (?:lost|dropped|losing|gained) \p{Nd}+(?:[.,]\p{Nd}+)? ?(?:kg|kilos?|lbs?|pounds) (?:in|over|since|without)|weight loss|without trying`,
      // body / tests (health knowledge with no condition word)
      String.raw`heart|blood|sangue|sangre|cora[çc][ãa]o|coraz[óo]n|pecho|peito|lungs?|kidneys?|liver|skin|teeth|tooth|throat|stomach|bowel|metabolic|metabolism|(?:lab|blood) (?:report|test|results?)|lice|hiccups?|caffeine|patients?|[vh]?ldl|hdl|radon`,
      // care-seeking
      String.raw`doctor|m[ée]dic[oa]s?|physician|pharmac${W}*|farm[áa]cia|hospital|emergency room|first aid|primeiros socorros|primeros auxilios|health|sa[úu]de|salud|medical|cl[íi]nic${W}*`,
    ].join("|") +
    `)${E}`,
  "iu"
);

/** Packs whose passages are health material: a kept chunk from one makes the turn health-shaped. */
export const HEALTH_PACK_IDS = new Set(["boar-medlineplus-en", "qa-health-se", "boar-firstaid-en"]);
/** The same packs by their source labels (a chunk without a pack id, and the eval traces, which keep the label only). */
const HEALTH_PACK_SOURCE = /medlineplus\.gov|medicalsciences\.stackexchange\.com|health\.stackexchange\.com|archive\.org\/details\/army-/i;

export interface WarnChunk {
  chunkId?: string;
  source?: string;
}

/** "pack:<packId>:<chunk>" -> packId (src/rag/packs.ts packHitToChunk). */
function packIdOf(c: WarnChunk): string | null {
  const m = /^pack:([^:]+):/.exec(c.chunkId ?? "");
  return m ? m[1] : null;
}

export function isHealthPackChunk(c: WarnChunk): boolean {
  const id = packIdOf(c);
  return (id !== null && HEALTH_PACK_IDS.has(id)) || HEALTH_PACK_SOURCE.test(c.source ?? "");
}

const has = (codes: readonly string[], prefix: string) => codes.some((c) => c.startsWith(prefix));

/** The detector score as the receipt shows it (detector:p=0.052), so a port reading receipts decides the same. */
function detectorP(codes: readonly string[]): number | null {
  const c = codes.find((x) => x.startsWith("detector:p="));
  return c ? Number(c.slice("detector:p=".length)) : null;
}

/** M: a model wrote this turn's text (no card, excerpt, fixed line, calculator, FACTS, small talk or decline). */
export function isModelGenerated(t: { modelId: string | null | undefined; codes: readonly string[]; outcome: string; text: string }): boolean {
  return (
    t.text.trim() !== "" &&
    t.outcome === "success" &&
    !!t.modelId &&
    !NOT_A_MODEL.has(t.modelId) &&
    !has(t.codes, "answer:") &&
    !t.codes.some((c) => c.includes("declined")) &&
    !t.codes.includes("instant:final") &&
    // The model's text was swapped for the source's excerpt (or the no-safe-steps line): a deterministic text.
    !has(t.codes, "grounding:health-unsafe-")
  );
}

/** H: why the turn is health-shaped (empty = it is not). */
export function healthReasons(t: { query: string; codes: readonly string[]; kept: readonly WarnChunk[] }): string[] {
  const out: string[] = [];
  if (t.codes.includes("detector:fire")) out.push("detector:fire");
  if ((detectorP(t.codes) ?? 0) >= WARN_P) out.push(`detector:p>=${WARN_P}`);
  if (has(t.codes, "healthgap:")) out.push("healthgap");
  if (has(t.codes, "grounding:health-")) out.push("grounding:health-*");
  if (isSafetyQuery(t.query)) out.push("lexicon:isSafetyQuery");
  // NFC first: a decomposed "doença" (c + U+0327) would miss doen[çc]a.
  if (WARN_LEX.test(t.query.normalize("NFC"))) out.push("lexicon:warn");
  if (t.kept.some(isHealthPackChunk)) out.push("source:health-pack");
  return out;
}

/** The engine's emergency line at the end of a model's health answer, and the app's preface (not content). */
const ENGINE_TAIL = /\n+(?:In an emergency, call your local emergency number[^\n]*|Em uma emergência[^\n]*|En una emergencia[^\n]*)\s*$/iu;
const PREFACE = /^(?:This answer is not from an offline source[^\n]*|Esta resposta não vem de uma fonte offline[^\n]*|From the offline source[^\n]*)\n+/iu;
const SENTENCE_SPLIT = /(?<=[.!?])(?:\s*\[\p{Nd}+\](?:\s*\[\p{Nd}+\])*)?\s+(?=[A-Z0-9"“(¿¡-])|\n+/u;
const CITE = /\[\p{Nd}+\]/u;
const CITE_AFTER = /^\s*\[\p{Nd}+\]/u;

const body = (text: string) => text.trim().replace(PREFACE, "").replace(ENGINE_TAIL, "");

/** Content sentences: 5 words or more, after the preface and the emergency line are taken off. */
export function contentSentences(text: string): string[] {
  return body(text)
    .split(SENTENCE_SPLIT)
    .filter((s) => s && s.split(/\s+/).filter(Boolean).length >= 5)
    .map((s) => s.trim());
}

/** Content sentences with no [n] in them and none right after their full stop ("… dose. [2]"). */
export function uncoveredSentences(text: string): { uncovered: string[]; count: number } {
  const ss = contentSentences(text);
  const t = body(text);
  const uncovered = ss.filter((s) => {
    const i = t.indexOf(s);
    // 8 characters (code points, as the reference) after the sentence.
    const tail = i >= 0 ? Array.from(t.slice(i + s.length)).slice(0, 8).join("") : "";
    return !CITE.test(s) && !CITE_AFTER.test(tail);
  });
  return { uncovered, count: ss.length };
}

/** Citation coverage of an answer's content sentences. */
export interface WarnCoverage {
  /** Content sentences. */
  count: number;
  /** Of them, uncited. */
  uncovered: number;
  /** The first content sentence is cited. */
  firstCited: boolean;
}

export function coverageOf(text: string): WarnCoverage {
  const ss = contentSentences(text);
  const { uncovered, count } = uncoveredSentences(text);
  return { count, uncovered: uncovered.length, firstCited: ss.length > 0 && !uncovered.includes(ss[0]) };
}

/** The tier of a turn already known to be M and H, from its kept and cited counts and its coverage. */
export function tierFromCoverage(t: { kept: number; cited: number } & WarnCoverage): WarnTier {
  if (t.kept === 0) return "no-source";
  if (t.cited === 0 || t.count === 0 || !t.firstCited || (t.count - t.uncovered) * 2 < t.count) return "beyond-source";
  return "sourced";
}

/** The tier of a turn already known to be M and H. */
export function warnTier(t: { kept: number; cited: number; text: string }): WarnTier {
  return t.kept === 0 ? "no-source" : tierFromCoverage({ kept: t.kept, cited: t.cited, ...coverageOf(t.text) });
}

export interface WarnTurn {
  query: string;
  codes: readonly string[];
  modelId: string | null | undefined;
  outcome: string;
  text: string;
  kept: readonly WarnChunk[];
  cited: readonly number[];
}

/** The whole rule over a finished turn: null = no banner. */
export function warnOf(t: WarnTurn): WarnTier | null {
  if (!isModelGenerated(t) || healthReasons(t).length === 0) return null;
  return warnTier({ kept: t.kept.length, cited: t.cited.length, text: t.text });
}

/** At stream start only M's model side, H and the kept sources are known: no-source, else beyond-source until done. */
export function streamStartTier(kept: number): WarnTier {
  return kept === 0 ? "no-source" : "beyond-source";
}

/**
 * done may only move beyond-source -> sourced; any other change keeps the tier shown since stream start. lateSources (multi-pass:
 * the sources arrive while it runs, so stream start knows none) lets no-source move to the tier the finished answer earns.
 */
export function settleTier(start: WarnTier | null, final: WarnTier | null, lateSources = false): WarnTier | null {
  if (final === null || start === null) return final;
  if (lateSources && start === "no-source") return final;
  return start === "beyond-source" && final === "sourced" ? "sourced" : start;
}

const COPY: Record<WarnTier, Record<WarnLang, string>> = {
  "no-source": {
    en: "Health caution: no offline source covers this. The AI answered from memory; not clinically reviewed, and it may be wrong. Ask a health professional.",
    pt: "Aviso de saúde: nenhuma fonte offline cobre isso. A IA respondeu de memória, sem revisão clínica, e pode estar errada. Consulte um profissional de saúde.",
    es: "Aviso de salud: ninguna fuente offline cubre esto. La IA respondió de memoria, sin revisión clínica; puede equivocarse. Consulte a un profesional de la salud.",
  },
  "beyond-source": {
    en: "Health caution: this AI answer may go beyond its offline source. Not clinically reviewed; compare it with the source and ask a health professional.",
    pt: "Aviso de saúde: esta resposta da IA pode ir além da fonte offline. Sem revisão clínica; confira a fonte e consulte um profissional de saúde.",
    es: "Aviso de salud: esta respuesta de IA puede decir más que la fuente offline. Sin revisión clínica; verifique la fuente y consulte a un profesional.",
  },
  sourced: {
    en: "Health caution: AI answer drawn from an offline source, not clinically reviewed, and it may still be wrong. For health decisions, ask a health professional.",
    pt: "Aviso de saúde: resposta da IA com base em fonte offline, sem revisão clínica; ainda pode estar errada. Para decisões de saúde, consulte um profissional.",
    es: "Aviso de salud: respuesta de IA basada en fuente offline, sin revisión clínica; aún puede estar equivocada. Para decisiones de salud, consulte a un profesional.",
  },
};

/** The banner text for a tier (shown above the answer, stored with the message, never recomputed). */
export function warnCopy(tier: WarnTier, lang: WarnLang): string {
  return COPY[tier][lang];
}
