#!/usr/bin/env node
// Two-judge report: Claude (judge.mjs) vs Jev (jev-judge.mjs, another model family) on the same pairs,
// plus agreement of each judge with the 20 hand calibration labels. No model calls.
// Usage (from eval/): node scripts/judge-agreement.mjs [--dataset v1] [--subset s32|all] [--system <run>]
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { cohenKappa, combineOrders, summarize } from "./lib/judge-core.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const a = process.argv.slice(2);
const get = (k, d) => (a.includes(k) ? a[a.indexOf(k) + 1] : d);
const dataset = get("--dataset", "v1");
const subset = get("--subset", "s32");
const ids = subset === "all"
  ? new Set(readFileSync(join(EVAL_DIR, "dataset", `questions.${dataset}.jsonl`), "utf8").trim().split("\n").map((l) => JSON.parse(l).id))
  : new Set(JSON.parse(readFileSync(join(EVAL_DIR, "dataset", `subset.${subset}.${dataset}.json`), "utf8")).ids);
const readJsonl = (p) => (existsSync(p) ? readFileSync(p, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l)) : []);
const fmt = (x, d = 2) => (Number.isFinite(x) ? x.toFixed(d) : "n/a");
const pct = (x) => (Number.isFinite(x) ? `${(x * 100).toFixed(0)}%` : "n/a");

const V1_COMPARISONS = [
  { label: "Qwen3-4B vs Qwen2.5-1.5B (direct)", file: "qwen3-4b-instruct-2507-q4km__bundled__vs__qwen2.5-1.5b-instruct-q4km__bundled", sides: ["4B", "1.5B"] },
  { label: "Qwen2.5-1.5B vs reference", file: "qwen2.5-1.5b-instruct-q4km__bundled__vs__claude-code__opus", sides: ["1.5B", "ref"] },
  { label: "Qwen3-4B vs reference", file: "qwen3-4b-instruct-2507-q4km__bundled__vs__claude-code__opus", sides: ["4B", "ref"] },
  { label: "Sources in user turn vs in system (1.5B)", file: "qwen2.5-1.5b-instruct-q4km__bundled__sources-user__vs__qwen2.5-1.5b-instruct-q4km__bundled__sources-system", sides: ["user", "system"] },
];
// v1.1 "20 crypto questions": the default model with and without the Ethereum and cryptography pack.
const CRYPTOPACK_COMPARISONS = [
  { label: "Qwen3-4B + crypto pack vs reference", file: "qwen3-4b-instruct-2507-q4km__bundled__pack-boar-crypto__vs__claude-code__opus", sides: ["4B+pack", "ref"] },
  { label: "Qwen3-4B (no pack) vs reference", file: "qwen3-4b-instruct-2507-q4km__bundled__vs__claude-code__opus", sides: ["4B", "ref"] },
  { label: "Qwen3-4B + crypto pack vs Qwen3-4B (direct)", file: "qwen3-4b-instruct-2507-q4km__bundled__pack-boar-crypto__vs__qwen3-4b-instruct-2507-q4km__bundled", sides: ["4B+pack", "4B"] },
];
// --system <run>: Claude x Jev on one run vs the reference (e.g. the official headline slice).
const oneSystem = get("--system");
const COMPARISONS = oneSystem
  ? [{ label: `${oneSystem} vs reference`, file: `${oneSystem}__vs__claude-code__opus`, sides: ["BOAR", "ref"] }]
  : dataset === "cryptopack" ? CRYPTOPACK_COMPARISONS : V1_COMPARISONS;

function combined(dir, file) {
  const byQ = {};
  for (const j of readJsonl(join(EVAL_DIR, "results", dir, dataset, `${file}.jsonl`)).filter((j) => j.ok && ids.has(j.queryId))) (byQ[j.queryId] ??= {})[j.order] = j;
  return Object.fromEntries(Object.entries(byQ).filter(([, o]) => o.boarA && o.boarB).map(([q, o]) => [q, combineOrders(o.boarA.mapped, o.boarB.mapped)]));
}

const L = ["# Two judges: Claude vs Jev (another model family)", ""];
L.push(`TL;DR: same blinded pairs, same rubric, both A/B orders; Jev = typesafe-ai/jev (calibrated classifier) via the Vercel AI Gateway, eval time only. Dataset \`${dataset}\`, subset \`${subset}\`. Regenerate with \`node eval/scripts/judge-agreement.mjs\`.`, "");
L.push("| Comparison | Judge | n | First side wins / tie / second side wins | Win score (95% CI) | Quality ratio (95% CI) | Position-consistent | Winner agreement Claude×Jev (kappa) |", "|---|---|---|---|---|---|---|---|");
const summaries = {};
for (const c of COMPARISONS) {
  const cl = combined("judgments", c.file), jv = combined("judgments-jev", c.file);
  const common = Object.keys(cl).filter((q) => jv[q]);
  const agree = common.filter((q) => cl[q].winner === jv[q].winner).length / Math.max(1, common.length);
  const kappa = common.length ? cohenKappa(common.map((q) => cl[q].winner), common.map((q) => jv[q].winner)) : NaN;
  for (const [judge, data] of [["Claude", cl], ["Jev", jv]]) {
    const ps = Object.values(data);
    if (!ps.length) { L.push(`| ${c.label} | ${judge} | 0 | not run | – | – | – | – |`); continue; }
    const s = summarize(ps, { seed: 41 });
    summaries[`${c.file}|${judge}`] = s;
    L.push(`| ${c.label} | ${judge} | ${ps.length} | ${pct(s.boarWin)} / ${pct(s.tie)} / ${pct(s.refWin)} | ${fmt(s.winScore)} (${fmt(s.winScoreCI[0])}–${fmt(s.winScoreCI[1])}) | ${fmt(s.qualityRatio)} (${fmt(s.qualityRatioCI[0])}–${fmt(s.qualityRatioCI[1])}) | ${pct(s.positionConsistency)} | ${judge === "Jev" && common.length ? `${pct(agree)} (${fmt(kappa)}) on ${common.length}` : ""} |`);
  }
}
L.push("", "Win score = first side's wins + ½ ties (0.5 = even). Quality ratio = first side's mean rubric score / second side's. Orders that disagree count as a tie.", "");

// Hand calibration: 20 labeled pairs (BOAR vs reference).
const calib = readJsonl(join(EVAL_DIR, "results", "calibration", dataset, "labels.jsonl"));
if (calib.length) {
  const judges = { Claude: "judgments", Jev: "judgments-jev" };
  L.push("## Agreement with the 20 hand-labeled pairs", "", "| Judge | Exact agreement | Kappa | Agreement on 'reference is better or equal' |", "|---|---|---|---|");
  for (const [name, dir] of Object.entries(judges)) {
    const got = calib.map((c) => combined(dir, `${c.system}__vs__claude-code__opus`)[c.queryId]?.winner);
    const m = calib.filter((_, i) => got[i]);
    const g = got.filter(Boolean);
    const exact = m.filter((c, i) => c.winner === g[i]).length / Math.max(1, m.length);
    const weak = m.filter((c, i) => (c.winner === "boar") === (g[i] === "boar")).length / Math.max(1, m.length);
    L.push(`| ${name} | ${pct(exact)} (${m.length}) | ${fmt(cohenKappa(m.map((c) => c.winner), g))} | ${pct(weak)} |`);
  }
  L.push("");
}
writeFileSync(join(EVAL_DIR, "reports", `judges-${dataset}-${subset}${oneSystem ? `-${oneSystem}` : ""}.md`), L.join("\n") + "\n");
console.log(L.join("\n"));
