/**
 * AR, arithmetic tools (cut 3 dev; coordinator 4B-LOSS-ANATOMY, arm 3). Two word-problem shapes the 4B sets up wrong
 * and the learned health detector can misroute (v0.1 vit-sci-003, "10 tokens per second… 30 tokens per second… 1,300
 * tokens of sources and a 400-token answer, how long?" got "I won't give health advice from memory" on every cut-2 seed):
 *  (1) rate × quantity -> time: "N units at R units per second/minute/hour", one phase or two (prompt then answer,
 *      download then install), each quantity paired with its rate by adjacency ("600 MB at 20 MB/s") or by phase words
 *      (prompt / sources / reads vs answer / generates / writes);
 *  (2) ceil-division: "how many X of size S for T" ("23 L of water, 5 L jerrycans, how many jerrycans?").
 * Constants come only from the question. English only; a message with a symptom, injury or medication word is never
 * answered here (it stays on the health path). Pure: the question in, the answer text out (or null).
 */

export type ArithmeticKind = "rate-time" | "ceil-division";
export interface ArithmeticAnswer {
  kind: ArithmeticKind;
  text: string;
  /** The computed value: seconds (rate-time) or a count (ceil-division). */
  value: number;
}

const NUM = String.raw`(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)`;
const num = (s: string) => Number(s.replace(/,/g, ""));

/** Health, injury, medication or body words: never a calculator answer (the detector's call stands). */
const HEALTH =
  /\b(?:heart|pulse|bpm|breath\w*|blood|dos(?:e|es|age|ing)|mg|mcg|ml|iv|drip|infusion|insulin|tablets?|pills?|fever|contractions?|labou?r|pain\w*|bleed\w*|injur\w*|symptoms?|medic\w*|drugs?|overdos\w*|poison\w*|bab(?:y|ies)|toddlers?|infants?|calories?|kcal|glucose|oxygen|sp[o0]2)\b/i;

/** A situation, not a word problem ("water rising 10 cm per minute, how long do we have?"): the normal (safety) path. */
const DANGER =
  /\b(?:trapped|stuck|rising|flood\w*|fire|smoke|drown\w*|sinking|emergency|leak\w*|gas|danger\w*|unsafe|collaps\w*|avalanche|hurricane|tornado|lost|dying|dead|unconscious|attack\w*|bitten|bite|stung|hypotherm\w*|frostbite|choking|suicid\w*|kill\w*)\b/i;

// ---- units ----

const TIME_UNITS: Record<string, [number, string]> = {
  s: [1, "second"], sec: [1, "second"], secs: [1, "second"], second: [1, "second"], seconds: [1, "second"],
  min: [60, "minute"], mins: [60, "minute"], minute: [60, "minute"], minutes: [60, "minute"],
  h: [3600, "hour"], hr: [3600, "hour"], hrs: [3600, "hour"], hour: [3600, "hour"], hours: [3600, "hour"],
  day: [86400, "day"], days: [86400, "day"],
};
const TIME = "seconds?|secs?|s|minutes?|mins?|min|hours?|hrs?|hr|h|days?";

/** "tokens" -> "token", "litres" / "L" -> "litre", "MB" -> "mb". */
function unitKey(raw: string): string {
  const u = raw.toLowerCase();
  if (/^(?:tok|tokens?)$/.test(u)) return "token";
  if (/^(?:l|lt|litres?|liters?)$/.test(u)) return "litre";
  if (/^(?:mb|megabytes?)$/.test(u)) return "mb";
  if (/^(?:gb|gigabytes?)$/.test(u)) return "gb";
  if (/^(?:kb|kilobytes?)$/.test(u)) return "kb";
  if (/^(?:km|kms|kilomet(?:er|re)s?)$/.test(u)) return "km";
  if (/^(?:mi|miles?)$/.test(u)) return "mile";
  if (/^(?:gal|gallons?)$/.test(u)) return "gallon";
  return u.replace(/ies$/, "y").replace(/(?<=[^s])s$/, "");
}
/** How a unit is written back: the singular or plural word. */
function unitLabel(key: string, n: number): string {
  const one = { token: "token", litre: "litre", mb: "MB", gb: "GB", kb: "KB", km: "km", mile: "mile", gallon: "gallon" }[key] ?? key;
  if (one === "MB" || one === "GB" || one === "KB" || one === "km" || n === 1) return one;
  return /[^aeiou]y$/.test(one) ? `${one.slice(0, -1)}ies` : `${one}s`;
}

// Compact rate spellings: "tok/s", "MB/s", "km/h", "mph", "wpm", "ppm", "L/min".
const COMPACT: Record<string, [string, string]> = { mph: ["mile", "h"], kph: ["km", "h"], wpm: ["word", "min"], ppm: ["page", "min"] };

interface Rate {
  start: number;
  end: number;
  value: number; // units per second
  per: number; // the stated value
  unit: string;
  time: string; // second / minute / hour / day
}
interface Quantity {
  start: number;
  end: number;
  value: number;
  unit: string;
}

const RATE_RE = new RegExp(
  String.raw`${NUM}\s*-?\s*([a-zA-Z]+)\s*(?:per|an?|each|every|\/)\s*(${TIME})\b|${NUM}\s*(mph|kph|wpm|ppm)\b`,
  "gi"
);

function rates(q: string): Rate[] {
  const out: Rate[] = [];
  RATE_RE.lastIndex = 0;
  for (let m = RATE_RE.exec(q); m; m = RATE_RE.exec(q)) {
    const [n, unitRaw, timeRaw] = m[1] !== undefined ? [m[1], m[2], m[3]] : [m[4], COMPACT[m[5].toLowerCase()][0], COMPACT[m[5].toLowerCase()][1]];
    const t = TIME_UNITS[timeRaw.toLowerCase()];
    if (!t) continue;
    // "a minute" after a bare count is not a rate unit ("5 minutes a day"): the unit must not be a time word itself.
    if (TIME_UNITS[unitRaw.toLowerCase()]) continue;
    const per = num(n);
    if (!(per > 0)) continue;
    out.push({ start: m.index, end: m.index + m[0].length, value: per / t[0], per, unit: unitKey(unitRaw), time: t[1] });
  }
  return out;
}

const QTY_RE = new RegExp(String.raw`${NUM}\s*-?\s*([a-zA-Z]+)`, "gi");
function quantities(q: string, rs: Rate[]): Quantity[] {
  const units = new Set(rs.map((r) => r.unit));
  const out: Quantity[] = [];
  QTY_RE.lastIndex = 0;
  for (let m = QTY_RE.exec(q); m; m = QTY_RE.exec(q)) {
    const start = m.index;
    if (rs.some((r) => start >= r.start && start < r.end)) continue;
    const unit = unitKey(m[2]);
    if (!units.has(unit)) continue;
    const value = num(m[1]);
    if (!(value > 0)) continue;
    out.push({ start, end: start + m[0].length, value, unit });
  }
  return out;
}

const INPUT = /\b(?:prompts?|prefill\w*|process\w*|reads?|reading|ingest\w*|sources?|input|context|download\w*|upload\w*|scan\w*|proofread\w*)\b/i;
const OUTPUT = /\b(?:answers?|generat\w*|writ\w*|wrote|decod\w*|output\w*|repl(?:y|ies)|responses?|install\w*|print\w*|format\w*)\b/i;
const phaseOf = (s: string): "in" | "out" | null => {
  const i = INPUT.test(s);
  const o = OUTPUT.test(s);
  return i && !o ? "in" : o && !i ? "out" : null;
};

const ASK_TIME =
  /\bhow long\b|\bhow (?:many|much) (?:time|seconds?|minutes?|hours?|days?)\b|\btime (?:needed|required)\b|\btotal (?:time|wait|seconds?|minutes?|hours?)\b|\btotal\s*\?|\bwhen will (?:it|this) (?:be )?(?:done|finish)/i;

// ---- formatting ----

const fmt = (n: number, digits = 1) => n.toLocaleString("en-US", { maximumFractionDigits: digits, minimumFractionDigits: 0 });
function duration(sec: number): string {
  if (sec < 120) {
    const s = `${fmt(sec)} seconds`;
    const r = Math.round(sec % 60);
    return sec >= 60 ? `${s} (${Math.floor(sec / 60)} min${r ? ` ${r} s` : ""})` : s;
  }
  if (sec < 2 * 3600) {
    const m = Math.floor(sec / 60);
    const s = Math.round(sec - m * 60);
    return `${fmt(sec / 60)} minutes${s ? ` (${m} min ${s} s)` : ""}`;
  }
  if (sec < 2 * 86400) {
    const h = Math.floor(sec / 3600);
    const m = Math.round((sec - h * 3600) / 60);
    return `${fmt(sec / 3600)} hours${m ? ` (${h} h ${m} min)` : ""}`;
  }
  return `${fmt(sec / 86400)} days`;
}
/** One step: "1,300 tokens ÷ 30 tokens per second ≈ 43.3 seconds". */
function step(q: Quantity, r: Rate): string {
  const t = q.value / r.per;
  const exact = Math.abs(t - Math.round(t * 10) / 10) < 1e-9;
  return `${fmt(q.value, 2)} ${unitLabel(q.unit, q.value)} ÷ ${fmt(r.per, 2)} ${unitLabel(r.unit, r.per)} per ${r.time} ${exact ? "=" : "≈"} ${fmt(t)} ${t === 1 ? r.time : `${r.time}s`}`;
}

// ---- (1) rate × quantity -> time ----

function rateTime(q: string): ArithmeticAnswer | null {
  if (!ASK_TIME.test(q)) return null;
  const rs = rates(q);
  if (!rs.length || rs.length > 2) return null;
  const qs = quantities(q, rs);
  if (qs.length !== rs.length) return null;
  const pairs: [Quantity, Rate][] = [];
  const freeQ = new Set(qs);
  const freeR = new Set(rs);
  // Adjacency: "600 MB at 20 MB/s", "a 3,000 token prompt at 60 tokens per second", "400 tokens generated at 8 tok/s".
  for (const x of qs)
    for (const r of rs)
      if (freeQ.has(x) && freeR.has(r) && r.start > x.end && /^\s*(?:[a-z]+\s+){0,2}(?:at|@)\s*$/i.test(q.slice(x.end, r.start))) {
        pairs.push([x, r]);
        freeQ.delete(x);
        freeR.delete(r);
      }
  // Phase words: a rate's are just before it, a quantity's just after it (then just before).
  if (freeQ.size === 2 && freeR.size === 2) {
    const marks = [...rs, ...qs].map((m) => [m.start, m.end]).sort((a, b) => a[0] - b[0]);
    const before = (s: number) => q.slice(Math.max(0, ...marks.filter(([, e]) => e <= s).map(([, e]) => e)), s);
    const after = (e: number) => q.slice(e, Math.min(q.length, ...marks.filter(([st]) => st >= e).map(([st]) => st)));
    const rp = [...freeR].map((r) => [r, phaseOf(before(r.start))] as const);
    const qp = [...freeQ].map((x) => [x, phaseOf(after(x.end)) ?? phaseOf(before(x.start))] as const);
    for (const phase of ["in", "out"] as const) {
      const r = rp.filter(([, p]) => p === phase);
      const x = qp.filter(([, p]) => p === phase);
      if (r.length === 1 && x.length === 1) {
        pairs.push([x[0][0], r[0][0]]);
        freeQ.delete(x[0][0]);
        freeR.delete(r[0][0]);
      }
    }
  }
  if (freeQ.size === 1 && freeR.size === 1) {
    pairs.push([[...freeQ][0], [...freeR][0]]);
    freeQ.clear();
    freeR.clear();
  }
  if (freeQ.size || freeR.size || pairs.some(([x, r]) => x.unit !== r.unit)) return null;
  pairs.sort((a, b) => a[0].start - b[0].start);
  const total = pairs.reduce((s, [x, r]) => s + x.value / r.value, 0);
  const steps = pairs.map(([x, r]) => step(x, r));
  const text =
    pairs.length === 1
      ? `About ${duration(total)}: ${steps[0]}. Computed from the numbers in your question.`
      : `About ${duration(total)} in total: ${steps[0]}, plus ${steps[1]}. Computed from the numbers in your question.`;
  return { kind: "rate-time", text, value: total };
}

// ---- (2) ceil-division ----

const HOW_MANY = /\bhow many\s+(?:more\s+)?([a-z][a-z-]*)/i;
const CLAUSE = /[,.;:?!()]|\b(?:and|with|if|then|into|but|so)\b/i;
const stem = (w: string) => {
  const l = w.toLowerCase();
  if (/(?:ches|shes|xes|sses|uses)$/.test(l)) return l.slice(0, -2);
  if (/ies$/.test(l)) return `${l.slice(0, -3)}y`;
  return l.replace(/(?<=[^s])s$/, "");
};

function ceilDivision(q: string): ArithmeticAnswer | null {
  const hm = HOW_MANY.exec(q);
  if (!hm) return null;
  const x = stem(hm[1]);
  if (x.length < 3) return null;
  const rest = q.slice(0, hm.index) + " " + q.slice(hm.index + hm[0].length);
  // Numbers, minus identifiers ("EIP-7251", "ERC-20") and percentages.
  const nums = [...rest.matchAll(new RegExp(String.raw`(?<![\p{L}-])${NUM}(?!\s*%)(?![\p{L}\d-]*\d)`, "gu"))].map((m) => ({ value: num(m[1]), index: m.index!, raw: m[1] }));
  if (nums.length !== 2) return null;
  const clauses: { from: number; to: number; text: string }[] = [];
  let at = 0;
  for (const part of rest.split(new RegExp(`(${CLAUSE.source})`, "i"))) {
    clauses.push({ from: at, to: at + part.length, text: part });
    at += part.length;
  }
  const clauseOf = (i: number) => clauses.find((c) => i >= c.from && i < c.to)!.text;
  const xRe = new RegExp(`\\b${x.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}(?:e?s)?\\b|\\bper\\b|\\beach\\b|\\bmax\\b|\\bcap\\b`, "i");
  const sizeCands = nums.filter((n) => xRe.test(clauseOf(n.index)));
  if (sizeCands.length !== 1) return null;
  const size = sizeCands[0];
  const total = nums.find((n) => n !== size)!;
  if (!(size.value > 0 && total.value > size.value)) return null;
  // Units next to the numbers must agree when both are written ("23 L … 5 L", "10,000 ETH … 32 ETH").
  const unitAfter = (n: { index: number; raw: string }) => /^\s*-?\s*([a-zA-Z]+)/.exec(rest.slice(n.index + n.raw.length))?.[1] ?? "";
  const us = unitKey(unitAfter(size));
  const ut = unitKey(unitAfter(total));
  const MEASURE = /^(?:litre|mb|gb|kb|km|mile|gallon|kg|g|lb|eth|btc|m|cm|ft|day|week|hour|minute|second|tb)$/;
  if (MEASURE.test(us) && MEASURE.test(ut) && us !== ut) return null;
  if (rates(q).length) return null;
  const quotient = total.value / size.value;
  const count = Math.ceil(quotient - 1e-9);
  const full = Math.floor(quotient + 1e-9);
  const raw = unitAfter(size);
  const unitFor = (n: number) => (!MEASURE.test(us) ? "" : /^[A-Z]|^(?:kg|g|lb|m|cm|ft|km|mb|gb|kb|tb)$/i.test(raw) && raw.length <= 3 ? ` ${raw}` : ` ${unitLabel(us, n)}`);
  const noun = hm[1].toLowerCase();
  const exact = count === full;
  const text = exact
    ? `${count.toLocaleString("en-US")} ${noun}: ${fmt(total.value, 2)} ÷ ${fmt(size.value, 2)} = ${count.toLocaleString("en-US")} exactly.`
    : `${count.toLocaleString("en-US")} ${noun}: ${fmt(total.value, 2)} ÷ ${fmt(size.value, 2)} = ${fmt(quotient, 2)}, rounded up. ${full.toLocaleString("en-US")} full ones hold ${fmt(full * size.value, 2)}${unitFor(full * size.value)}; the remaining ${fmt(total.value - full * size.value, 2)}${unitFor(total.value - full * size.value)} needs one more.`;
  return { kind: "ceil-division", text, value: count };
}

/** The exact answer to a rate or ceil-division word problem (EN), or null. Never on health/medication messages. */
export function arithmeticAnswer(query: string): ArithmeticAnswer | null {
  if (HEALTH.test(query) || DANGER.test(query)) return null;
  return rateTime(query) ?? ceilDivision(query);
}
