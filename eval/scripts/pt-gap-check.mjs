#!/usr/bin/env node
// PT vs EN criterion of the gate (Boar, 2026-09-27, after the PT lexicon fix): the candidate's gap (EN - PT quality
// ratio, Jev, 4B with packs, 41 v2 items) blocks when it exceeds the control's gap + 1 point; gap <= 5 points is the
// recorded target, reported only. Judges the gate's PT runs (results/gates/<label>/pt) with Jev when not cached.
// Usage (from eval/): node scripts/pt-gap-check.mjs <control-label> <candidate-label>
import { copyFileSync, existsSync, readFileSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { combineOrders, summarize } from "./lib/judge-core.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const [control, candidate] = process.argv.slice(2);
if (!control || !candidate) throw new Error("usage: pt-gap-check.mjs <control-label> <candidate-label>");
const MARGIN = 0.01, TARGET = 0.05;

function ratio(label, lang) {
  const [dataset, system] = lang === "en" ? ["v2", `qwen3-4b__pt-gate-${label}-en`] : ["v2-pt", `qwen3-4b__pt-gate-${label}-pt`];
  const src = join(EVAL_DIR, "results/gates", label, "pt", `v2-${lang}.jsonl`);
  if (!existsSync(src)) throw new Error(`${src} missing: run the gate with GATE_PT=1`);
  copyFileSync(src, join(EVAL_DIR, "results/runs", dataset, `${system}.jsonl`));
  execFileSync("node", ["scripts/jev-judge.mjs", "--system", system, "--dataset", dataset, "--subset", "all"], { cwd: EVAL_DIR, stdio: "ignore" });
  const by = {};
  for (const l of readFileSync(join(EVAL_DIR, "results/judgments-jev", dataset, `${system}__vs__claude-code__opus.jsonl`), "utf8").trim().split("\n")) {
    const j = JSON.parse(l);
    if (j.mapped && j.ok !== false) (by[j.queryId] ??= {})[j.order] = j;
  }
  const pairs = Object.entries(by).filter(([, o]) => o.boarA && o.boarB).map(([q, o]) => ({ q, ...combineOrders(o.boarA.mapped, o.boarB.mapped) }));
  const s = summarize(pairs, { seed: 21 });
  // Per item (Boar, after bc7db6d): BOAR's mean rubric score and correctness, to show which items moved the gap.
  const rowsById = Object.fromEntries(readFileSync(src, "utf8").trim().split("\n").map((l) => JSON.parse(l)).map((r) => [r.queryId, r]));
  const answers = Object.fromEntries(Object.entries(rowsById).map(([q, r]) => [q, r.answer ?? ""]));
  const items = Object.fromEntries(pairs.map((p) => [p.q, { mean: Object.values(p.boar).reduce((a, x) => a + x, 0) / Object.values(p.boar).length, correct: p.boar.correctness, answer: answers[p.q], sources: rowsById[p.q]?.promptSources?.length ?? 0, cited: rowsById[p.q]?.citedTitles?.length ?? 0 }]));
  return { n: pairs.length, r: s.qualityRatio, ci: s.qualityRatioCI, items };
}

const f = (x) => x.toFixed(3);
const rows = [], gaps = {}, per = {};
for (const label of [control, candidate]) {
  const en = ratio(label, "en"), pt = ratio(label, "pt");
  gaps[label] = en.r - pt.r;
  per[label] = { en: en.items, pt: pt.items };
  rows.push(`| ${label} | ${f(en.r)} (${f(en.ci[0])}–${f(en.ci[1])}, n=${en.n}) | ${f(pt.r)} (${f(pt.ci[0])}–${f(pt.ci[1])}, n=${pt.n}) | ${(100 * gaps[label]).toFixed(1)} pts |`);
}
const pass = gaps[candidate] <= gaps[control] + MARGIN;
const L = [`# PT vs EN: ${candidate} vs ${control}`, "",
  `TL;DR: **${pass ? "PASS" : "FAIL"}**: candidate gap ${(100 * gaps[candidate]).toFixed(1)} pts vs control ${(100 * gaps[control]).toFixed(1)} pts (blocks above control + ${100 * MARGIN} pt). Target gap <= ${100 * TARGET} pts: ${gaps[candidate] <= TARGET ? "met" : "not met (recorded goal, not blocking)"}. Jev, 4B with packs, v2 non-food items. Regenerate with \`node eval/scripts/pt-gap-check.mjs ${control} ${candidate}\`.`, "",
  "| Gate | EN quality ratio (95% CI) | PT quality ratio (95% CI) | Gap EN − PT |", "|---|---|---|---|", ...rows, ""];
// Items whose score moved (mean rubric score, 1-5, both A/B orders), PT drops first.
for (const lang of ["pt", "en"]) {
  const moved = Object.keys(per[candidate][lang]).map((q) => ({ q, c: per[control][lang][q], k: per[candidate][lang][q] }))
    .filter((x) => x.c && Math.abs(x.k.mean - x.c.mean) >= 0.5).sort((a, b) => (a.k.mean - a.c.mean) - (b.k.mean - b.c.mean));
  L.push(`## ${lang.toUpperCase()} items that moved (|Δ mean score| ≥ 0.5)`, "", moved.length ? "| Item | Mean score control → candidate | Correctness control → candidate | Sources in prompt / cited, control → candidate | Candidate answer |" : "None.", ...(moved.length ? ["|---|---|---|---|---|"] : []));
  for (const x of moved) L.push(`| ${x.q} | ${x.c.mean.toFixed(1)} → **${x.k.mean.toFixed(1)}** | ${x.c.correct.toFixed(1)} → ${x.k.correct.toFixed(1)} | ${x.c.sources}/${x.c.cited} → ${x.k.sources !== x.c.sources ? `**${x.k.sources}**` : x.k.sources}/${x.k.cited} | ${x.k.answer.slice(0, 140).replace(/\n/g, " ").replace(/\|/g, "/")} |`);
  L.push("");
}
writeFileSync(join(EVAL_DIR, "reports", `pt-gap-${control}-vs-${candidate}.md`), L.join("\n"));
console.log(L.join("\n"));
process.exit(pass ? 0 : 1);
