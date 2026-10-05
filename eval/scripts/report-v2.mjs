#!/usr/bin/env node
// Report for dataset v2 ("Vitalik style"): objective venue scoring for food items (BOAR and the reference),
// blind-judge results for crypto and travel items. No model calls.
// Usage (from eval/): node scripts/report-v2.mjs
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { combineOrders, percentile, summarize } from "./lib/judge-core.mjs";
import { scoreFoodAnswer } from "./lib/venues.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const dataset = "v2";
const refName = "claude-code__opus";
const readJsonl = (p) => (existsSync(p) ? readFileSync(p, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l)) : []);
const fmt = (x, d = 2) => (Number.isFinite(x) ? x.toFixed(d) : "n/a");
const pct = (x) => (Number.isFinite(x) ? `${(x * 100).toFixed(0)}%` : "n/a");
const median = (xs) => percentile([...xs].sort((a, b) => a - b), 0.5);

const questions = readJsonl(join(EVAL_DIR, "dataset", `questions.${dataset}.jsonl`));
const byId = Object.fromEntries(questions.map((q) => [q.id, q]));
const food = questions.filter((q) => q.category.startsWith("local-food"));
const gold = Object.fromEntries(food.map((q) => [q.id, JSON.parse(readFileSync(join(EVAL_DIR, "dataset", q.gold[0].file), "utf8"))]));
const refs = Object.fromEntries(readJsonl(join(EVAL_DIR, "references", dataset, `${refName}.jsonl`)).filter((r) => r.ok).map((r) => [r.id, r]));
const runsDir = join(EVAL_DIR, "results", "runs", dataset);
// --systems a,b: only these runs (runs/v2 also holds gate and A/B runs). --name: report file name.
const argv = process.argv.slice(2);
const only = argv.includes("--systems") ? argv[argv.indexOf("--systems") + 1].split(",") : null;
const reportName = argv.includes("--name") ? argv[argv.indexOf("--name") + 1] : "baseline-v2";
// --judge jev: read the Jev judgments (results/judgments-jev) instead of the Claude ones.
const judge = argv.includes("--judge") ? argv[argv.indexOf("--judge") + 1] : "claude";
const judgeDir = judge === "jev" ? "judgments-jev" : "judgments";
const systems = (existsSync(runsDir) ? readdirSync(runsDir).filter((f) => f.endsWith(".jsonl") && !/\.(pre-|invalid)/.test(f)).map((f) => f.replace(/\.jsonl$/, "")) : [])
  .filter((s) => !only || only.includes(s));

function foodScores(answers) {
  const rows = food.filter((q) => answers[q.id] != null).map((q) => ({ id: q.id, located: q.category === "local-food-located", ...scoreFoodAnswer(answers[q.id], gold[q.id], q.grading) }));
  return {
    n: rows.length,
    pass: rows.filter((r) => r.pass).length / Math.max(1, rows.length),
    passLocated: rows.filter((r) => r.located && r.pass).length,
    nLocated: rows.filter((r) => r.located).length,
    meanVerified: rows.reduce((a, r) => a + r.verifiedDietVenues, 0) / Math.max(1, rows.length),
    deflect: rows.filter((r) => r.deflects).length / Math.max(1, rows.length),
    rows,
  };
}

function judged(system) {
  const js = readJsonl(join(EVAL_DIR, "results", judgeDir, dataset, `${system}__vs__${refName}.jsonl`)).filter((j) => j.ok);
  const byQ = {};
  for (const j of js) (byQ[j.queryId] ??= {})[j.order] = j;
  const pairs = Object.entries(byQ).filter(([, o]) => o.boarA && o.boarB).map(([queryId, o]) => ({ queryId, category: o.boarA.category, ...combineOrders(o.boarA.mapped, o.boarB.mapped) }));
  const cat = (c) => pairs.filter((p) => p.category === c);
  const correct = (ps) => (ps.length ? ps.filter((p) => p.boar.correctness >= 4).length / ps.length : NaN);
  return { pairs, all: pairs.length ? summarize(pairs, { seed: 21 }) : null, crypto: correct(cat("crypto-expert")), travel: correct(cat("travel-practical")), danger: correct(cat("danger-situation")), math: correct(cat("math-reasoning")), nCrypto: cat("crypto-expert").length, nTravel: cat("travel-practical").length, nDanger: cat("danger-situation").length, nMath: cat("math-reasoning").length };
}

/** Math items: does the answer contain the computed number (rounded to 0-2 decimals, comma or dot)? */
const mathItems = questions.filter((q) => q.category === "math-reasoning");
function numberHit(answer, x) {
  const text = String(answer ?? "").replace(/(\d),(\d{3})/g, "$1$2");
  const forms = new Set([x.toFixed(2), x.toFixed(1), String(Math.round(x)), String(x)].flatMap((f) => [f, f.replace(".", ",")]));
  return [...forms].some((f) => new RegExp(`(^|[^\\d.,])${f.replace(".", "\\.")}(?![\\d])`).test(text));
}
const mathScore = (answers) => mathItems.filter((q) => answers[q.id] != null && numberHit(answers[q.id], q.answer)).length;

const refFood = foodScores(Object.fromEntries(Object.entries(refs).map(([id, r]) => [id, r.rawResult ?? r.answer])));
const sys = systems.map((s) => {
  const runs = readJsonl(join(runsDir, `${s}.jsonl`));
  const answers = Object.fromEntries(runs.map((r) => [r.queryId, r.answer]));
  return { system: s, label: runs[0]?.configLabel ?? s, food: foodScores(answers), mathHits: mathScore(answers), judge: judged(s), medianS: median(runs.filter((r) => r.outcome === "success").map((r) => r.totalLatencyMs)) / 1000, runs };
});

const L = [];
L.push(`# BOAR eval: v2 "Vitalik style" (${reportName})`, "");
L.push(`TL;DR: the literal test from Vitalik's post. Food answers are scored objectively against OpenStreetMap; crypto and travel answers by the blind judge (${judge === "jev" ? "Jev" : "Claude"}) against Opus + web search. Regenerate with \`node eval/scripts/report-v2.mjs ${argv.join(" ")}\`.`, "");
L.push("## Local food (\"Tell me the best vegan restaurants in [city]\")", "");
L.push("An item passes when the answer names ≥ 3 distinct venues that exist in the OSM snapshot for that city and diet.", "");
L.push("| System | Pass (n) | Located items passed | Mean verified venues | Deflects | Median s |", "|---|---|---|---|---|---|");
for (const s of sys) L.push(`| ${s.label} | ${pct(s.food.pass)} (${s.food.n}) | ${s.food.passLocated}/${s.food.nLocated} | ${fmt(s.food.meanVerified, 1)} | ${pct(s.food.deflect)} | ${fmt(s.medianS, 1)} |`);
L.push(`| Reference (Opus + web search) | ${pct(refFood.pass)} (${refFood.n}) | ${refFood.passLocated}/${refFood.nLocated} | ${fmt(refFood.meanVerified, 1)} | ${pct(refFood.deflect)} | ${fmt(median(Object.values(refs).filter((r) => r.category?.startsWith("local-food")).map((r) => r.wallMs)) / 1000, 1)} |`, "");
L.push(`## Crypto, travel, danger situations and math (blind judge: ${judge === "jev" ? "Jev" : "Claude"}, both orders)`, "");
L.push("Correct = judge correctness ≥ 4 of 5. Math also has an objective check: the answer contains the computed number.", "");
L.push("| System | Crypto correct | Travel correct | Danger correct | Math correct (judge) | Math number right | Quality ratio vs ref (95% CI) | Win / tie / loss |", "|---|---|---|---|---|---|---|---|");
for (const s of sys) {
  const j = s.judge;
  L.push(`| ${s.label} | ${pct(j.crypto)} (${j.nCrypto}) | ${pct(j.travel)} (${j.nTravel}) | ${pct(j.danger)} (${j.nDanger}) | ${pct(j.math)} (${j.nMath}) | ${s.mathHits}/${mathItems.length} | ${j.all ? `${fmt(j.all.qualityRatio)} (${fmt(j.all.qualityRatioCI[0])}–${fmt(j.all.qualityRatioCI[1])})` : "not judged"} | ${j.all ? `${pct(j.all.boarWin)} / ${pct(j.all.tie)} / ${pct(j.all.refWin)}` : "–"} |`);
}
L.push(`| Reference (Opus + web search) | – | – | – | – | ${mathScore(Object.fromEntries(Object.entries(refs).map(([id, r]) => [id, r.answer])))}/${mathItems.length} | 1.00 | – |`, "");
const pq = "cry-001";
L.push(`## The exact prompt from the post: "${byId[pq].query}"`, "");
for (const s of sys) {
  const r = s.runs.find((x) => x.queryId === pq);
  const jp = s.judge.pairs.find((p) => p.queryId === pq);
  L.push(`**${s.label}**${jp ? ` (judge correctness ${fmt(jp.boar.correctness, 1)}/5)` : ""}:`, "", "> " + String(r?.answer ?? "(no answer)").trim().replace(/\n+/g, "\n> "), "");
}
L.push("## Per-item food results", "", "| Item | City | Query | " + sys.map((s) => s.label).join(" | ") + " | Reference |", "|---|---|---|" + sys.map(() => "---").join("|") + "|---|");
for (const q of food) {
  const cell = (fs) => { const r = fs.rows.find((x) => x.id === q.id); return r ? `${r.verifiedDietVenues}${r.pass ? " ✓" : ""}${r.deflects ? " (deflects)" : ""}` : "–"; };
  L.push(`| ${q.id} | ${gold[q.id].city} | ${q.query} | ${sys.map((s) => cell(s.food)).join(" | ")} | ${cell(refFood)} |`);
}
L.push("");
L.push("## Notes", "");
L.push("- BOAR today has no POI data and does not read the device location, so food items are expected to fail: this is the starting point for P1 (offline OSM POIs + GPS).");
L.push("- A venue missing from OSM is unverified, not necessarily wrong; OSM coverage of `diet:*` tags varies by city (see counts in `dataset/gold/v2`).");
L.push("- The reference gets the device coordinates for the 3 located items; BOAR's runner does not (the app does not use them yet).");
L.push("- Judge and reference are both Claude (same-family limitation as in v1).");
mkdirSync(join(EVAL_DIR, "reports"), { recursive: true });
writeFileSync(join(EVAL_DIR, "reports", `${reportName}.md`), L.join("\n") + "\n");
console.log(`wrote reports/${reportName}.md`);
