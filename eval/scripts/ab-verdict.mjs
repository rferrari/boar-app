#!/usr/bin/env node
// Verdict for a prompt-layout A/B (v1.1 item 5): variant B (sources in the user turn) vs A (sources in the system).
// Pre-registered criterion (written before the judge ran, 2026-09-26):
//   REGRESSION if the direct B-vs-A win score 95% CI lies entirely below 0.5,
//   OR B's correct-answer rate (judge correctness >= 4 vs the reference) is more than 10 points below A's.
// Usage (from eval/): node scripts/ab-verdict.mjs [--a <run>] [--b <run>] [--dataset v1] [--subset s32]
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { combineOrders, summarize } from "./lib/judge-core.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const a = process.argv.slice(2);
const get = (k, d) => (a.includes(k) ? a[a.indexOf(k) + 1] : d);
const dataset = get("--dataset", "v1");
const A = get("--a", "qwen2.5-1.5b-instruct-q4km__bundled__sources-system");
const B = get("--b", "qwen2.5-1.5b-instruct-q4km__bundled__sources-user");
const ref = "claude-code__opus";
const name = get("--name", "ab-sources-layout");
const label = get("--label", "Qwen2.5-1.5B");
const readJsonl = (p) => (existsSync(p) ? readFileSync(p, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l)) : []);
const fmt = (x, d = 2) => (Number.isFinite(x) ? x.toFixed(d) : "n/a");
const pct = (x) => (Number.isFinite(x) ? `${(x * 100).toFixed(0)}%` : "n/a");

function pairs(system, against, dir = "judgments") {
  const byQ = {};
  for (const j of readJsonl(join(EVAL_DIR, "results", dir, dataset, `${system}__vs__${against}.jsonl`)).filter((j) => j.ok)) (byQ[j.queryId] ??= {})[j.order] = j;
  return Object.entries(byQ).filter(([, o]) => o.boarA && o.boarB).map(([queryId, o]) => ({ queryId, ...combineOrders(o.boarA.mapped, o.boarB.mapped) }));
}
const correct = (ps) => ps.filter((p) => p.boar.correctness >= 4).length / Math.max(1, ps.length);

const runs = (n) => readJsonl(join(EVAL_DIR, "results", "runs", dataset, `${n}.jsonl`));
const cites = (n) => runs(n).filter((r) => /\[\d+\]/.test(r.answer)).length;
const literalN = (n) => runs(n).filter((r) => /\[n\]/.test(r.answer)).length;
// Short answers that only say the sources do not cover the question (no real answer).
const refusals = (n) => runs(n).filter((r) => /do(es)? not contain|don.t cover|do not (include|mention)|not (in|covered by) the (provided )?(sources|context)/i.test(r.answer) && r.answer.length < 400).length;
// Portuguese questions answered mostly in English (function-word count heuristic).
const qLang = Object.fromEntries(readJsonl(join(EVAL_DIR, "dataset", `questions.${dataset}.jsonl`)).map((q) => [q.id, q.lang]));
const ptInEnglish = (n) => {
  const pt = runs(n).filter((r) => qLang[r.queryId] === "pt-BR");
  const bad = pt.filter((r) => (r.answer.match(/\b(the|and|is|are|of|provided|sources)\b/gi) ?? []).length > (r.answer.match(/\b(o|a|os|as|de|do|da|que|é|são|para)\b/gi) ?? []).length);
  return `${bad.length}/${pt.length}`;
};
const sections = [];
let anyRegressed = false;
for (const [judge, dir] of [["Claude", "judgments"], ["Jev", "judgments-jev"]]) {
  const direct = pairs(B, A, dir);
  if (!direct.length) { sections.push(`### Judge: ${judge}\n\nNot run.\n`); continue; }
  const d = summarize(direct, { seed: 31 });
  const vsA = pairs(A, ref, dir), vsB = pairs(B, ref, dir);
  const haveRef = vsA.length && vsB.length;
  const sA = haveRef ? summarize(vsA, { seed: 32 }) : null, sB = haveRef ? summarize(vsB, { seed: 33 }) : null;
  const cA = haveRef ? correct(vsA) : NaN, cB = haveRef ? correct(vsB) : NaN;
  const regressDirect = d.winScoreCI[1] < 0.5;
  const regressCorrect = haveRef && cA - cB > 0.10;
  const regressed = regressDirect || regressCorrect;
  anyRegressed ||= regressed;
  sections.push(`### Judge: ${judge} — ${regressed ? "REGRESSED" : "no regression"}

Direct blind comparison B vs A (both orders, ${direct.length} questions): B wins ${pct(d.boarWin)}, ties ${pct(d.tie)}, loses ${pct(d.refWin)}; win score ${fmt(d.winScore)} (95% CI ${fmt(d.winScoreCI[0])}–${fmt(d.winScoreCI[1])}); position-consistent ${pct(d.positionConsistency)}.

${haveRef ? `| vs reference | A: sources in system | B: sources in user turn |
|---|---|---|
| Correct answers (correctness ≥ 4) | ${pct(cA)} | ${pct(cB)} |
| Quality ratio (95% CI) | ${fmt(sA.qualityRatio)} (${fmt(sA.qualityRatioCI[0])}–${fmt(sA.qualityRatioCI[1])}) | ${fmt(sB.qualityRatio)} (${fmt(sB.qualityRatioCI[0])}–${fmt(sB.qualityRatioCI[1])}) |` : "Against-reference runs not available for this judge."}

Criterion: direct win-score CI entirely below 0.5 — ${regressDirect ? "met" : "not met"}; correct rate down more than 10 points — ${haveRef ? (regressCorrect ? "met" : "not met") : "not evaluated"}.
`);
}
const md = `# A/B: sources in the user turn vs in the system prompt — ${label} (v1.1 item 5)

TL;DR: **${anyRegressed ? "REGRESSED (see judges below)" : "NO REGRESSION"}** on v1 s32 (${label}, seed 42, same job and machine). A = \`${A}\`, B = \`${B}\`.

Pre-registered criterion (written before any judge ran): regression if the direct B-vs-A win score 95% CI lies entirely below 0.5, or B's correct-answer rate vs the reference is more than 10 points below A's.

| | A | B |
|---|---|---|
| Answers citing [1], [2]… | ${cites(A)}/32 | ${cites(B)}/32 |
| Answers with a literal "[n]" | ${literalN(A)}/32 | ${literalN(B)}/32 |
| Refusals ("the sources do not cover it", no answer) | ${refusals(A)}/32 | ${refusals(B)}/32 |
| Portuguese questions answered in English | ${ptInEnglish(A)} | ${ptInEnglish(B)} |

${sections.join("\n")}
Scope: quality only. TTFT with prompt-cache reuse is measured by the engine owner (llama-server cache_prompt, multi-turn); this runner builds a fresh context per question.
`;
writeFileSync(join(EVAL_DIR, "reports", `${name}.md`), md);
console.log(md);
