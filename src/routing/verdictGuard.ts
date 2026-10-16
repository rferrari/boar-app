/**
 * VG, the verdict guard (cut 3 dev; coordinator 4B-LOSS-ANATOMY, arm 1). The 4B commits to its first-sentence verdict
 * before it states the facts, and the facts are often right: "No, astronauts did not walk on the Moon before 1970. The
 * first human moonwalk occurred on July 20, 1969" (every 4B run), "Germany is more densely populated than Japan… Germany
 * 230/km², Japan 339/km²", "No country is bigger than India… India is the seventh-largest". A pure check of question +
 * answer after generation. English only; the engine runs it on model answers only (never health, cards, calculators).
 *  (a) date: a yes/no question with "before / after / by YYYY"; the Yes/No disagrees with the year of the answer's
 *      own "first …" fact (within the two sentences after the verdict).
 *  (b) compare: "Which <is more X>, A or B" / "Is A <more X> than B"; both entities get a number of the compared quantity
 *      (density, people, area, length, mass, age: v2) in the same unit, the ranges don't overlap, and the verdict names the
 *      other one (or says the wrong Yes/No). Ambiguous, mixed units or a missing quantity: no flag.
 *  (c) rank: "Which <things> are bigger than X" answered "No <thing> is bigger…" next to "X … the Nth-largest" (N > 1),
 *      and any list the answer gives has N - 1 items (v2).
 * Repair is a template from the parsed facts (plus the difference and ratio when the question asks "by how much"); the
 * model's self-correction notes and restatements of the old verdict are dropped. No regeneration (template only).
 * v3 (reviewer F1–F3): abstain when a number may belong to someone else (a capitalised name or "in <Name>" before it, a
 * possessive of another attribute), on a non-world rank scope or a subject inside another name, and on a "first …"
 * sentence with two years. An answer that isn't flagged is returned as null: nothing about it changes.
 */

export type VerdictGuardKind = "date" | "compare" | "rank";
export interface VerdictGuardResult {
  kind: VerdictGuardKind;
  text: string;
}

// ---- text segments (sentences and list lines), kept with their separators so a repair only touches what it drops ----

interface Seg {
  text: string;
  sep: string;
}

/** Inline self-correction notes: "(Note: This response contains a factual error…)", "[Note: …]". */
const INLINE_NOTE = /\s*[([]\s*note:[^)\]]*[)\]]/gi;
const META =
  /^(?:wait\b|correct(?:ed|ion|ing)?\s*:|\(?\[?note\s*:)|\bthis (?:response|answer) contains (?:a |an )?(?:factual )?error\b|\bcontradicts the previous\b/i;

function segments(text: string): Seg[] {
  const parts = text.split(/((?<=[.!?:])[ \t]+(?=["'(\[*A-Z-])|\s*\n+\s*)/);
  const out: Seg[] = [];
  for (let i = 0; i < parts.length; i += 2) {
    const t = parts[i] ?? "";
    const sep = parts[i + 1] ?? "";
    if (!t.trim()) {
      if (out.length) out[out.length - 1].sep += sep;
      continue;
    }
    out.push({ text: t, sep });
  }
  return out;
}

function join(segs: Seg[]): string {
  return segs
    .map((s, i) => s.text + (i < segs.length - 1 ? s.sep : ""))
    .join("")
    .trim();
}

const isMeta = (s: string) => META.test(s.trim());

// ---- numbers ----

const NUM = String.raw`(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)`;
const SCALE: Record<string, number> = { thousand: 1e3, million: 1e6, billion: 1e9, trillion: 1e12 };
const NUMBER_RE = new RegExp(
  `${NUM}(?:\\s*(?:–|—|-|to)\\s*${NUM})?(?:\\s+(thousand|million|billion|trillion)\\b)?`,
  "gi"
);
const num = (s: string) => Number(s.replace(/,/g, ""));

interface Quantity {
  lo: number;
  hi: number;
  unit: string;
  raw: string;
  /** Where the number starts in the text it was read from. */
  index: number;
}

const UNIT_STOP = new Set(["while", "and", "whereas", "compared", "than", "which", "in", "on", "at", "for", "but", "making", "so", "or", "as", "with", "versus", "vs"]);

/** "people per square kilometer, while…" -> "people per km2"; "lb (700 kg)" -> "lb". */
function unitSig(rest: string): string {
  const head = (/^\s*([^,;.()[\]]{0,48})/.exec(rest)?.[1] ?? "")
    .toLowerCase()
    .replace(/\b(?:square|sq\.?)\s+(?:kilomet(?:er|re)s?|km)\b|\bkm²|\bkm2\b/g, "km2")
    .replace(/\b(?:square|sq\.?)\s+miles?\b|\bmi²/g, "mi2")
    .replace(/\bkilomet(?:er|re)s?\b/g, "km")
    .replace(/\bmet(?:er|re)s?\b/g, "m")
    .replace(/\b(?:pounds?|lbs?)\b/g, "lb")
    .replace(/\bkilo(?:gram)?s?\b|\bkgs\b/g, "kg")
    .replace(/\b(?:feet|foot)\b/g, "ft")
    .replace(/\bpersons\b/g, "people");
  const words: string[] = [];
  for (const w of head.split(/\s+/).filter(Boolean)) {
    if (UNIT_STOP.has(w) || words.length >= 4) break;
    words.push(w);
  }
  return words.join(" ");
}

/** Every number (or range) in `s`, with its scale and unit, in order. Bare years (1500-2100, no unit), percentages and
 * ranks ("7th") are skipped. */
function allQuantities(s: string): Quantity[] {
  const out: Quantity[] = [];
  NUMBER_RE.lastIndex = 0;
  for (let m = NUMBER_RE.exec(s); m; m = NUMBER_RE.exec(s)) {
    const scale = m[3] ? SCALE[m[3].toLowerCase()] : 1;
    const a = num(m[1]) * scale;
    const b = m[2] !== undefined ? num(m[2]) * scale : a;
    const unit = (m[3] ? `${m[3].toLowerCase()} ` : "") + unitSig(s.slice(m.index + m[0].length));
    const yearLike = !m[2] && !m[3] && /^\d{4}$/.test(m[1]) && a >= 1500 && a <= 2100 && !unitSig(s.slice(m.index + m[0].length));
    if (yearLike) continue;
    // A percentage or a rank ("7th") is not a measurement of the entity.
    if (/^\s*(?:%|st\b|nd\b|rd\b|th\b)/.test(s.slice(m.index + m[0].length))) continue;
    out.push({ lo: Math.min(a, b), hi: Math.max(a, b), unit: unit.trim(), raw: m[0], index: m.index });
  }
  return out;
}

// ---- the compared quantity (VG v2, reviewer R2/R3: the number tied to the question's quantity, never just the first) ----

type QuantityClass = "density" | "people" | "area" | "length" | "mass" | "age";
/** The quantity a comparison question is about; null = unclear (no flag). Generic size words allow area, mass or length. */
function questionClasses(q: string): QuantityClass[] | null {
  if (/\bdens(?:e|ely|er|est|ity)\b/i.test(q)) return ["density"];
  if (/\b(?:more|fewer|less) (?:people|inhabitants|residents)\b|\bpopul(?:ous|ation)\b/i.test(q)) return ["people"];
  if (/\b(?:older|younger)\b/i.test(q)) return ["age"];
  if (/\barea\b|\bsquare\b/i.test(q)) return ["area"];
  if (/\b(?:taller|higher|longer|deeper|shallower)\b/i.test(q)) return ["length"];
  if (/\b(?:heavier|lighter|weighs?|weight)\b/i.test(q)) return ["mass"];
  if (/\b(?:bigger|larger|smaller|greater)\b/i.test(q)) return ["area", "mass", "length"];
  return null;
}
function unitClass(unit: string): QuantityClass | null {
  if (/\bper (?:km2|mi2)\b|\/ ?(?:km2|mi2)\b/.test(unit)) return "density";
  if (/\b(?:years? old|year-old|years of age)\b/.test(unit)) return "age";
  if (/\b(?:km2|mi2|hectares?|acres?)\b/.test(unit)) return "area";
  const first = unit.replace(/^(?:thousand|million|billion|trillion) /, "").split(" ")[0] ?? "";
  if (/^(?:kg|lb|g|tons?|tonnes?|t|grams?|ounces?|oz)$/.test(first)) return "mass";
  if (/^(?:m|km|ft|mi|miles?|cm|mm|inch(?:es)?|in|yards?|yd)$/.test(first)) return "length";
  if (/\b(?:people|inhabitants|residents|citizens)\b/.test(unit) || /^(?:thousand|million|billion)$/.test(unit)) return "people";
  return null;
}

// ---- comparatives ----

const MORE_WORDS =
  "more|bigger|larger|greater|higher|denser|older|taller|longer|heavier|deeper|hotter|warmer|faster|richer|wider|farther|further|stronger|wealthier|busier|louder|brighter";
const LESS_WORDS = "less|fewer|smaller|lower|younger|shorter|lighter|shallower|colder|cooler|slower|poorer|narrower|weaker|cheaper";
const MORE = new RegExp(`\\b(?:${MORE_WORDS})\\b`, "i");
const LESS = new RegExp(`\\b(?:${LESS_WORDS})\\b`, "i");
const COMPARATIVE = `(?:${MORE_WORDS}|${LESS_WORDS})`;

/** "max" when the bigger number wins ("more densely populated"), "min" for "smaller / fewer", null when unclear. */
function polarity(s: string): "max" | "min" | null {
  const less = LESS.test(s);
  const more = MORE.test(s);
  if (less && !/\bmore\b/i.test(s.replace(new RegExp(`\\b(?:${LESS_WORDS})\\b`, "gi"), ""))) return "min";
  if (/\b(?:less|fewer)\b/i.test(s)) return "min";
  if (more) return "max";
  return null;
}

const ENTITY_STOP = new Set(["the", "a", "an", "of", "adult", "male", "female", "average", "on", "in", "typical", "common", "wild"]);
const words = (s: string) => s.toLowerCase().match(/[\p{L}\p{N}]+/gu) ?? [];

/** Words that tell the two entities apart ("saltwater" vs "nile"; "moose" vs "american", "bison"). */
function entityKeys(a: string, b: string): [string[], string[]] {
  const wa = words(a).filter((w) => !ENTITY_STOP.has(w));
  const wb = words(b).filter((w) => !ENTITY_STOP.has(w));
  return [wa.filter((w) => !wb.includes(w)), wb.filter((w) => !wa.includes(w))];
}

const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
function mentionRe(keys: string[]): RegExp {
  return new RegExp(`(?<![\\p{L}])(?:${keys.map(escapeRe).join("|")})(?:'s|’s|s|es)?(?![\\p{L}])`, "giu");
}

interface Mention {
  who: 0 | 1;
  index: number;
  end: number;
}
function mentions(s: string, res: [RegExp, RegExp]): Mention[] {
  const out: Mention[] = [];
  for (const who of [0, 1] as const) {
    const re = new RegExp(res[who].source, res[who].flags);
    for (let m = re.exec(s); m; m = re.exec(s)) out.push({ who, index: m.index, end: m.index + m[0].length });
  }
  return out.sort((x, y) => x.index - y.index);
}

/** Each entity's stated quantity of the compared kind: from its mention to the other entity's next mention, within one
 * segment, the numbers whose unit fits the question's quantity. Two different same-unit values in one window: ambiguous. */
/** After a possessive mention ("Germany's …"), the words before the number must name the compared quantity itself. */
const OWN_MEASURE =
  /^\s*(?:(?:total|land|average|overall|population|surface|estimated|current|mean|peak|summit|official)\s+)*(?:population density|density|population|area|size|height|elevation|altitude|length|weight|mass|diameter|width|age)\b|^\s*(?:is|was|are|were|of|stands|sits|reaches|averages|measures|weighs|covers)\b/i;
/** A capitalised word (not the entities' own key words) or "in <Name>" between an entity's mention and its number. */
function otherOwner(between: string, keys: string[]): boolean {
  const own = new Set(keys.map((k) => k.toLowerCase()));
  for (const m of between.matchAll(/(?<![\p{L}\p{N}])\p{Lu}[\p{L}-]*/gu)) if (!own.has(m[0].toLowerCase().replace(/['’]s$/, ""))) return true;
  return false;
}

function quantities(segs: Seg[], res: [RegExp, RegExp], classes: QuantityClass[], keys: string[] = []): [Quantity | null, Quantity | null] | null {
  const found: [Quantity | null, Quantity | null] = [null, null];
  for (const { text } of segs) {
    const ms = mentions(text, res);
    for (let i = 0; i < ms.length; i++) {
      const m = ms[i];
      if (found[m.who]) continue;
      const nextOther = ms.slice(i + 1).find((n) => n.who !== m.who);
      const window = text.slice(m.index, nextOther ? nextOther.index : undefined);
      const fit = allQuantities(window).filter((q) => {
        const c = unitClass(q.unit);
        return c !== null && classes.includes(c);
      });
      if (!fit.length) continue;
      // VG v3 F1 (reviewer P1, P6b): the number must be the entity's own. Abstain when a capitalised name or an "in <Name>"
      // phrase sits between the mention and the number ("in Germany the city-state of Berlin reaches 4,100 per km²"), or
      // the mention is a possessive of something other than the compared quantity ("Saturn's rings are 282,000 km wide").
      const between = window.slice(m.end - m.index, fit[0].index);
      if (otherOwner(between, keys)) return null;
      if (/['’]s$/i.test(text.slice(m.index, m.end)) && !OWN_MEASURE.test(between)) return null;
      if (fit.some((q) => q.unit === fit[0].unit && (q.lo !== fit[0].lo || q.hi !== fit[0].hi))) return null;
      found[m.who] = fit[0];
    }
    if (found[0] && found[1]) break;
  }
  return found;
}

// ---- formatting ----

const fmt = (n: number) => n.toLocaleString("en-US", { maximumFractionDigits: n >= 100 ? 0 : n >= 10 ? 1 : 2 });
const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);
const CARDINALS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"];
const ORDINALS: Record<string, number> = {
  second: 2, third: 3, fourth: 4, fifth: 5, sixth: 6, seventh: 7, eighth: 8, ninth: 9, tenth: 10,
  eleventh: 11, twelfth: 12, thirteenth: 13, fourteenth: 14, fifteenth: 15, sixteenth: 16, seventeenth: 17, eighteenth: 18, nineteenth: 19, twentieth: 20,
};

/** A shown entity name: "an adult male moose" -> "an adult male moose" (the question's own words, article kept). */
const display = (s: string) => s.trim().replace(/\s+/g, " ");

// ---- (a) date threshold ----

const YESNO_Q = /^\s*(?:did|was|were|has|have|had|is|are|do|does)\b/i;
const THRESHOLD = /\b(before|after|by)\s+(?:the\s+(?:year\s+)?)?(1\d{3}|20\d{2})\b/i;
const VERDICT = /^\s*(yes|no)\b[,.!:;—–-]?\s*/i;
const NEGATION = /\b(did|was|were|had|has|have|is|are|could|would)(?:\s+not\b|n't\b)/i;

function checkDate(question: string, segs: Seg[]): VerdictGuardResult | null {
  if (!YESNO_Q.test(question)) return null;
  const t = THRESHOLD.exec(question);
  if (!t) return null;
  const rel = t[1].toLowerCase();
  const year = Number(t[2]);
  const v = VERDICT.exec(segs[0].text);
  if (!v) return null;
  const said = v[1].toLowerCase() === "yes";
  const factAt = segs.findIndex((s, i) => i > 0 && i <= 2 && /\bfirst\b/i.test(s.text) && /\b(?:1\d{3}|20\d{2})\b/.test(s.text));
  if (factAt < 0) return null;
  // VG v3 F3 (reviewer R7/P13): a "first …" sentence with more than one year ("first plans from 1802, but it opened in
  // 1994") can't say which year is the event's: abstain.
  const years = new Set(segs[factAt].text.match(/\b(?:1\d{3}|20\d{2})\b/g) ?? []);
  if (years.size > 1) return null;
  const first = Number(/\b(1\d{3}|20\d{2})\b/.exec(segs[factAt].text)![1]);
  // The first time it happened decides "before / by" both ways; "after" only when the first time is already after.
  const truth = rel === "before" ? first < year : rel === "by" ? first <= year : first > year ? true : null;
  if (truth === null || truth === said) return null;

  const rest = segs[0].text.slice(v[0].length);
  let verdict: string;
  if (truth) {
    const neg = rest.match(new RegExp(NEGATION.source, "gi")) ?? [];
    verdict =
      neg.length === 1 && !/\b(?:never|no|none|nobody)\b/i.test(rest)
        ? `Yes, ${rest.replace(NEGATION, (_m, aux: string) => aux)}`
        : "Yes.";
  } else {
    verdict = "No.";
  }
  const phrase = new RegExp(`\\b(?:${t[1]}\\s+(?:the\\s+(?:year\\s+)?)?|${rel === "before" ? "pre-" : rel === "after" ? "post-" : "x^"})${t[2]}\\b`, "i");
  const kept = segs.slice(1).filter((s) => !isMeta(s.text) && !(phrase.test(s.text) && /\b(?:no|not|never|none|n't)\b/i.test(s.text)));
  return { kind: "date", text: join([{ text: verdict, sep: segs[0].sep }, ...kept]) };
}

// ---- (b) two-entity numeric comparison ----

const WHICH_Q = new RegExp(
  String.raw`^\s*(?:which|who|what)\s+(.*?\b${COMPARATIVE}\b.*?)\s*[,:]\s*(.+?)\s+or\s+(.+?)(?:\s*,\s*and\b.*?|\s+and\s+by\s+how\s+much.*?)?\s*\?\s*$`,
  "i"
);
const YESNO_CMP_Q = new RegExp(
  String.raw`^\s*(?:is|are|was|were|does|do|did|has|have)\s+(.+?)\s+((?:have\s+|has\s+)?(?:a\s+)?${COMPARATIVE}\b.*?)\s+than\s+(.+?)\s*\?\s*$`,
  "i"
);
const BY_HOW_MUCH = /\bby how much\b|\bhow much (?:more|less|bigger|larger|denser)\b|\bhow many more\b/i;

function checkCompare(question: string, segs: Seg[]): VerdictGuardResult | null {
  const which = WHICH_Q.exec(question);
  const yesno = which ? null : YESNO_CMP_Q.exec(question);
  if (!which && !yesno) return null;
  const vp = which ? which[1] : yesno![2];
  const a = which ? which[2] : yesno![1];
  const b = which ? which[3] : yesno![3];
  const pol = polarity(vp);
  if (!pol) return null;
  const [ka, kb] = entityKeys(a, b);
  if (!ka.length || !kb.length) return null;
  const res: [RegExp, RegExp] = [mentionRe(ka), mentionRe(kb)];
  const classes = questionClasses(question);
  if (!classes) return null;
  const content = segs.filter((s) => !isMeta(s.text));
  const qs = quantities(content, res, classes, [...ka, ...kb]);
  if (!qs) return null;
  const [qa, qb] = qs;
  if (!qa || !qb || qa.unit !== qb.unit) return null;
  if (qa.lo <= qb.hi && qb.lo <= qa.hi) return null; // overlapping ranges: no decision
  const aWins = pol === "max" ? qa.lo > qb.hi : qa.hi < qb.lo;

  const v0 = segs[0].text;
  if (yesno) {
    const v = VERDICT.exec(v0);
    if (!v) return null;
    if ((v[1].toLowerCase() === "yes") === aWins) return null;
    const verdict = aWins ? "Yes." : "No.";
    const both = (s: string) => mentions(s, res).some((m) => m.who === 0) && mentions(s, res).some((m) => m.who === 1);
    const kept = segs.slice(1).filter((s) => !isMeta(s.text) && !(both(s.text) && polarity(s.text) && !/\d/.test(s.text)));
    return { kind: "compare", text: join([{ text: verdict, sep: segs[0].sep }, ...kept]) };
  }

  // Which-form: whom does the verdict sentence name as the winner?
  const ms = mentions(v0, res);
  if (!ms.length) return null;
  const vpol = polarity(v0);
  if (!vpol) return null;
  const than = /\bthan\b/i.exec(v0);
  let named: 0 | 1;
  if (than && ms.some((m) => m.index < than.index) && ms.some((m) => m.index > than.index)) named = ms.find((m) => m.index < than.index)!.who;
  else if (new Set(ms.map((m) => m.who)).size === 1) named = ms[0].who;
  else return null;
  const claimedA = vpol === pol ? named === 0 : named === 1;
  if (claimedA === aWins) return null;

  const [w, l, qw, ql] = aWins ? [a, b, qa, qb] : [b, a, qb, qa];
  const verb = vp.trim().replace(/\s+/g, " ");
  let verdict = `${capitalize(display(w))} ${verb} than ${display(l)}`;
  // Difference and ratio only for two single positive values (VG v2, reviewer R4: never for ranges).
  if (BY_HOW_MUCH.test(question) && qw.lo === qw.hi && ql.lo === ql.hi && ql.lo > 0) {
    const diff = Math.abs(qw.lo - ql.lo);
    const ratio = Math.max(qw.lo, ql.lo) / Math.min(qw.lo, ql.lo);
    const unit = qw.unit.replace(/\bkm2\b/, "km²").replace(/\bmi2\b/, "mi²");
    verdict += `, by about ${fmt(diff)}${unit ? ` ${unit}` : ""} (${fmt(qw.lo)} versus ${fmt(ql.lo)}, about ${ratio.toFixed(1)} times as much)`;
  }
  verdict += ".";
  const both = (s: string) => mentions(s, res).some((m) => m.who === 0) && mentions(s, res).some((m) => m.who === 1);
  // Restatements of the verdict (both entities and a comparative, no figures) go; the figures stay.
  const kept = segs.slice(1).filter((s) => !isMeta(s.text) && !(both(s.text) && polarity(s.text) && !/\bwhile\b|\bwhereas\b/i.test(s.text)));
  return { kind: "compare", text: join([{ text: verdict, sep: segs[0].sep }, ...kept]) };
}

// ---- (c) rank ----

const RANK_Q = /^\s*which\s+([\p{L} ]+?)\s+(are|is)\s+((?:bigger|larger|greater)\b.*?)\s*\?\s*$/iu;
const DENIAL = /\b(?:no|none of the)\s+[\p{L}]+(?:\s+[\p{L}]+)?\s+(?:is|are|has|have)\s+(?:any\s+)?(?:bigger|larger|greater)\b|\bthere (?:is|are) no\s+[\p{L}]+(?:\s+[\p{L}]+)?\s+(?:bigger|larger|greater)\b/iu;
const RANK = /\b(second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|(\d{1,2})(?:st|nd|rd|th))[- ](?:largest|biggest)\b/i;
const LIST = /\b(?:after|following|behind)\s+((?:the\s+)?[A-Z][\p{L}.]*(?:\s+[A-Z][\p{L}.]*)*(?:\s*,\s*(?:and\s+)?(?:the\s+)?[A-Z][\p{L}.]*(?:\s+[A-Z][\p{L}.]*)*)+)/u;
const INCLUDE_LIST = /\b(?:include|includes|including)\s*:?\s+((?:the\s+)?[A-Z][\p{L}.]*(?:\s+[A-Z][\p{L}.]*)*(?:\s*,\s*(?:and\s+)?(?:the\s+)?[A-Z][\p{L}.]*(?:\s+[A-Z][\p{L}.]*)*)+)/u;
const BULLET = /^\s*(?:[-*•]|\d{1,2}[.)])\s+\S/;

function checkRank(question: string, segs: Seg[]): VerdictGuardResult | null {
  const q = RANK_Q.exec(question);
  if (!q) return null;
  const denialAt = segs.findIndex((s) => DENIAL.test(s.text));
  if (denialAt < 0) return null;
  // VG v2 (reviewer R9): the rank must be the subject's own ("India is the seventh-largest").
  const subject = /\bthan\s+(?:the\s+)?(.+?)(?:\s+(?:by|in|when|on)\b.*)?$/i.exec(q[3])?.[1]?.trim() ?? "";
  if (!subject) return null;
  // VG v3 F2 (reviewer P11, P12): the subject on word boundaries and not right after another capitalised word ("South
  // Sudan" is not "Sudan"); and the rank must be a world rank: any other scope ("in Africa", "in the EU") abstains.
  const subjectRe = new RegExp(`(?<![\\p{Lu}][\\p{L}.-]*\\s+)(?<![\\p{L}\\p{N}])${escapeRe(subject)}(?![\\p{L}\\p{N}])`, "u");
  const rankSeg = segs.find((s) => RANK.test(s.text) && subjectRe.test(s.text));
  if (!rankSeg) return null;
  const afterRank = rankSeg.text.slice(RANK.exec(rankSeg.text)!.index);
  if (/\b(?:in|of|within)\s+(?:the\s+)?(?!world\b)(?:\p{Lu}[\p{L}-]*|EU|continent|region|hemisphere)/u.test(afterRank)) return null;
  const r = RANK.exec(rankSeg.text)!;
  const n = r[2] ? Number(r[2]) : ORDINALS[r[1].toLowerCase()];
  if (!(n > 1 && n <= 50)) return null;
  const count = n - 1;
  let noun = q[1].trim();
  let be = q[2];
  if (count === 1) {
    noun = noun.replace(/ies$/i, "y").replace(/(?<!s)s$/i, "");
    be = "is";
  } else be = "are";
  let verdict = `${capitalize(count <= 20 ? CARDINALS[count] : String(count))} ${noun} ${be} ${q[3].trim()}`;
  // VG v2 (reviewer R1, V5 #32): the answer's own list must agree with the new count, else no flag. A bulleted or
  // numbered list, or a prose list ("after A, B and C", "include A, B and C").
  const bullets = segs.filter((s) => BULLET.test(s.text));
  if (bullets.length && bullets.length !== count) return null;
  for (const s of segs) {
    const l = LIST.exec(s.text) ?? INCLUDE_LIST.exec(s.text);
    if (!l) continue;
    const items = l[1].split(/\s*,\s*(?:and\s+)?|\s+and\s+/).map((x) => x.trim().replace(/[.]+$/, "")).filter(Boolean);
    if (items.length !== count) return null;
    verdict += `: ${items.slice(0, -1).join(", ")} and ${items[items.length - 1]}`;
    break;
  }
  verdict += ".";
  const kept = segs.filter((s, i) => i !== 0 && !isMeta(s.text) && !DENIAL.test(s.text));
  return { kind: "rank", text: join([{ text: verdict, sep: segs[0].sep }, ...kept]) };
}

/**
 * The verdict guard: null when the answer is not flagged (it stays byte-identical), else the repaired text and which
 * check fired. English questions only (the caller checks the language).
 */
export function verdictGuard(question: string, answer: string): VerdictGuardResult | null {
  if (!question.trim() || !answer.trim()) return null;
  const segs = segments(answer.replace(INLINE_NOTE, ""));
  if (!segs.length) return null;
  return checkDate(question, segs) ?? checkCompare(question, segs) ?? checkRank(question, segs);
}
