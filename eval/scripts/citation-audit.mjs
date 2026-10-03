#!/usr/bin/env node
// Citation audit (Boar, 2026-09-27): for app-pipeline runs that kept the citation fields (runner 391a0f4+),
//   1. unsupported rate: each sentence of the shown answer that cites [k], checked against shown source k;
//   2. citations removed by post-processing (e.g. engine-routing CT-1, support >= 50% per [n]): citations in the
//      model's own text (against the sources in its prompt) with no match in the shown answer; those the source
//      does support are false positives of the filter.
// Support is judged by Jev (typesafe-ai/jev, another model family, calibrated yes/no) via the Vercel AI Gateway,
// eval time only; key from env VERCEL_AI_GATEWAY, responses cached by request hash, cost to the team ledger,
// hard stop at --max-usd (default 0.20). Writes reports/citations-<name>.md.
// Usage (from eval/): node scripts/citation-audit.mjs --name <name> --runs <dir-or-file> [...]
import { appendFileSync, existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { basename, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const ENDPOINT = "https://ai-gateway.vercel.sh/v1/evaluate";
const LEDGER = "/Users/r4to/Script/boar/shared-data/jev-spend.jsonl";
const CACHE = join(EVAL_DIR, ".cache", "jev");
const a = process.argv.slice(2);
const name = a.includes("--name") ? a[a.indexOf("--name") + 1] : "audit";
const maxUsd = Number(a.includes("--max-usd") ? a[a.indexOf("--max-usd") + 1] : "0.20");
const runsArgs = a.includes("--runs") ? a.slice(a.indexOf("--runs") + 1).filter((x) => !x.startsWith("--")) : [];
const KEY = process.env.VERCEL_AI_GATEWAY;
if (!KEY) throw new Error("VERCEL_AI_GATEWAY is not set");

const files = runsArgs.map((p) => (p.startsWith("/") ? p : join(EVAL_DIR, p))).filter(existsSync).flatMap((p) =>
  statSync(p).isDirectory() ? readdirSync(p).filter((f) => f.endsWith(".jsonl")).map((f) => join(p, f)) : [p]);

/** Sentences that cite, as { claim, refs: [k...] }. */
export function citedSentences(text) {
  return (text ?? "").split(/(?<=[.!?])\s+|\n+/).map((s) => s.trim()).filter(Boolean).flatMap((s) => {
    const refs = [...new Set([...s.matchAll(/\[(\d+)\]/g)].map((m) => Number(m[1])))];
    const claim = s.replace(/\s*\[\d+\]/g, "").trim();
    return refs.length && claim.split(/\s+/).length >= 4 ? [{ claim, refs }] : [];
  });
}
const words = (s) => new Set(s.toLowerCase().normalize("NFKD").replace(/[^a-z0-9 ]+/g, " ").split(/\s+/).filter((w) => w.length > 2));
const jaccard = (x, y) => { const A = words(x), B = words(y); const i = [...A].filter((w) => B.has(w)).length; return i / Math.max(1, A.size + B.size - i); };

/** The whole cited chunk when it fits, else the window around the claim's terms (Boar: never a blind prefix). */
export function sourceWindow(claim, source, max = 6000, win = 3000) {
  if (source.length <= max) return source;
  const terms = [...words(claim)];
  let best = 0, bestScore = -1;
  for (let i = 0; i < source.length; i += 250) {
    const seg = source.slice(i, i + win).toLowerCase();
    const score = terms.filter((t) => seg.includes(t)).length;
    if (score > bestScore) (bestScore = score), (best = i);
  }
  return source.slice(best, best + win);
}

let spent = 0;
async function supported(claim, source) {
  const body = {
    model: "typesafe-ai/jev",
    state: { claim, source: sourceWindow(claim, source) },
    questions: {
      supported: {
        type: "boolean",
        instructions: "Does `source` state or directly imply the factual content of `claim`? Paraphrase and translation count as support; a claim that adds facts, numbers or names the source does not contain is not supported.",
        criteria: { true: "The source supports the claim", false: "The source does not support the claim, or supports only part of it" },
      },
    },
  };
  const hash = createHash("sha256").update(JSON.stringify(body)).digest("hex");
  const file = join(CACHE, `${hash}.json`);
  let j;
  if (existsSync(file)) j = JSON.parse(readFileSync(file, "utf8"));
  else {
    if (spent >= maxUsd) throw new Error(`budget cap ${maxUsd} USD reached`);
    for (let attempt = 1; attempt <= 5 && !j; attempt++) {
      const res = await fetch(ENDPOINT, { method: "POST", headers: { authorization: `Bearer ${KEY}`, "content-type": "application/json" }, body: JSON.stringify(body) });
      if (res.ok) j = await res.json();
      else if (res.status === 503 || res.status === 429) await new Promise((r) => setTimeout(r, Math.min(8000, 1000 * 2 ** attempt)));
      else throw new Error(`jev ${res.status}: ${(await res.text()).slice(0, 200)}`);
    }
    if (!j) throw new Error("jev: giving up after 5 attempts");
    mkdirSync(CACHE, { recursive: true });
    writeFileSync(file, JSON.stringify(j));
    const cost = Number(j.providerMetadata?.gateway?.marketCost ?? 0);
    spent += cost;
    appendFileSync(LEDGER, JSON.stringify({ ts: new Date().toISOString(), who: "Sextant", what: `citation audit ${name}`, marketCost: cost }) + "\n");
  }
  return j.answers.supported.probability;
}

const byCfg = {};
const examples = { unsupported: [], removedSupported: [], addedUnsupported: [] };
for (const f of files) {
  const cfg = basename(f, ".jsonl").replace(/__seed\d+$/, "");
  for (const r of readFileSync(f, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l))) {
    if (!r.shownSources) continue; // runs without the audit fields
    // Places listings are built from the POI records (distance, hours formatted by the app), not claims to verify.
    if ((r.reasonCodes ?? []).includes("task:places")) continue;
    const c = (byCfg[cfg] ??= { answers: 0, cited: 0, unsupported: 0, badIndex: 0, raw: 0, removed: 0, removedSupported: 0, added: 0, addedUnsupported: 0 });
    c.answers++;
    const final = citedSentences(r.answer);
    for (const { claim, refs } of final) for (const k of refs) {
      c.cited++;
      const src = r.shownSources[k - 1];
      if (!src) { c.badIndex++; c.unsupported++; continue; }
      const p = await supported(claim, `${src.title}\n${src.body}`);
      if (p < 0.5) { c.unsupported++; if (examples.unsupported.length < 8) examples.unsupported.push({ cfg, q: r.queryId, claim, src: src.title, p }); }
    }
    // Citations the engine attributed after generation (engine-routing 40ef8a2: reason code citations:added-<k>-<k>…):
    // each sentence citing an added index must be supported by that source, or the attribution sourced a false claim.
    const addedCode = (r.reasonCodes ?? []).find((x) => x.startsWith("citations:added-"));
    if (addedCode) {
      const addedIdx = new Set(addedCode.replace("citations:added-", "").split("-").map(Number));
      for (const { claim, refs } of final) for (const k of refs) {
        if (!addedIdx.has(k)) continue;
        c.added++;
        const src = r.shownSources[k - 1];
        const p = src ? await supported(claim, `${src.title}\n${src.body}`) : 0;
        if (p < 0.5) { c.addedUnsupported++; if (examples.addedUnsupported.length < 8) examples.addedUnsupported.push({ cfg, q: r.queryId, claim, src: src?.title ?? "(missing)", p }); }
      }
    }
    if (!r.modelText || !r.promptSources) continue;
    const finalPairs = final.flatMap(({ claim, refs }) => refs.map((k) => ({ claim, title: r.shownSources[k - 1]?.title })));
    for (const { claim, refs } of citedSentences(r.modelText)) for (const k of refs) {
      const src = r.promptSources[k - 1];
      if (!src) continue;
      c.raw++;
      const kept = finalPairs.some((fp) => fp.title === src.title && jaccard(fp.claim, claim) >= 0.6);
      if (kept) continue;
      c.removed++;
      const p = await supported(claim, `${src.title}\n${src.body}`);
      if (p >= 0.5) { c.removedSupported++; if (examples.removedSupported.length < 8) examples.removedSupported.push({ cfg, q: r.queryId, claim, src: src.title, p }); }
    }
  }
}

const pct = (x, n) => (n ? `${Math.round((100 * x) / n)}% (${x}/${n})` : "–");
const L = [`# Citation audit: ${name}`, ""];
L.push("TL;DR: support of every [n] in the shown answers, and what post-processing removed. Judge: Jev (another model family), p(supported) < 0.5 = unsupported. Regenerate with `node eval/scripts/citation-audit.mjs " + a.join(" ") + "` (cached, no new cost).", "");
L.push("| Configuration | Answers | Citations shown | Unsupported | Index out of range | Citations in model text | Removed by post-processing | Removed but supported (false positive) | Added by attribution | Added but unsupported |", "|---|---|---|---|---|---|---|---|---|---|");
const T = { answers: 0, cited: 0, unsupported: 0, badIndex: 0, raw: 0, removed: 0, removedSupported: 0, added: 0, addedUnsupported: 0 };
for (const [cfg, c] of Object.entries(byCfg)) {
  for (const k of Object.keys(T)) T[k] += c[k];
  L.push(`| ${cfg} | ${c.answers} | ${c.cited} | ${pct(c.unsupported, c.cited)} | ${c.badIndex} | ${c.raw} | ${pct(c.removed, c.raw)} | ${pct(c.removedSupported, c.removed)} | ${c.added} | ${pct(c.addedUnsupported, c.added)} |`);
}
L.push(`| **All** | ${T.answers} | ${T.cited} | ${pct(T.unsupported, T.cited)} | ${T.badIndex} | ${T.raw} | ${pct(T.removed, T.raw)} | ${pct(T.removedSupported, T.removed)} | ${T.added} | ${pct(T.addedUnsupported, T.added)} |`, "");
for (const [title, xs] of [["Unsupported citations (sample)", examples.unsupported], ["Removed although supported (sample)", examples.removedSupported], ["Added by attribution but unsupported (sample)", examples.addedUnsupported]]) {
  L.push(`## ${title}`, "");
  for (const x of xs) L.push(`- ${x.cfg} · ${x.q} · p=${x.p.toFixed(2)} · source "${x.src}": ${x.claim.slice(0, 200)}`);
  L.push("");
}
L.push("Limits: a removed citation is one whose (source title, sentence) has no match in the shown answer (word Jaccard >= 0.6), so a rewritten sentence can count as removed. Jev sees the whole cited chunk up to 6000 characters, else the 3000-character window with most of the claim's terms; runs recorded before runner 8000-char bodies (gates up to 383fe96) hold only the first 2000 characters.", "");
writeFileSync(join(EVAL_DIR, "reports", `citations-${name}.md`), L.join("\n"));
console.log(L.slice(0, 6 + Object.keys(byCfg).length).join("\n"));
console.log(`Jev spend this run: US$${spent.toFixed(4)}`);
