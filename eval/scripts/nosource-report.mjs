#!/usr/bin/env node
// Answers from memory (no source): correct / partial / wrong rate per model and language, for Boar's decision on
// refusing sourceless knowledge answers (revert the Compacto refusal if the 1.5B is correct ~70%+ without a source).
// Reads results/runs/nosource and the judgments (Jev for every seed; Claude where it ran). No model calls.
// Usage (from eval/): node scripts/nosource-report.mjs
import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { combineOrders } from "./lib/judge-core.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const readJsonl = (p) => (existsSync(p) ? readFileSync(p, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l)) : []);
const runs = readdirSync(join(EVAL_DIR, "results/runs/nosource")).filter((f) => f.endsWith(".jsonl")).map((f) => f.replace(/\.jsonl$/, ""));

// Wilson 95% interval for a proportion.
function wilson(k, n) {
  if (!n) return [NaN, NaN];
  const z = 1.96, p = k / n, d = 1 + (z * z) / n;
  const c = (p + (z * z) / (2 * n)) / d, h = (z * Math.sqrt((p * (1 - p)) / n + (z * z) / (4 * n * n))) / d;
  return [c - h, c + h];
}
const pct = (x) => (Number.isFinite(x) ? `${Math.round(100 * x)}%` : "n/a");
// PT answer check: more English than Portuguese function words = answered in the wrong language.
const EN = /\b(the|and|is|are|of|to|in|that|it|with|for)\b/gi, PT = /\b(o|a|os|as|e|é|de|do|da|que|em|para|com|não|um|uma)\b/gi;
const wrongLang = (text) => (text.match(EN)?.length ?? 0) > (text.match(PT)?.length ?? 0);

function verdicts(dir, system) {
  const byQ = {};
  for (const j of readJsonl(join(EVAL_DIR, "results", dir, "nosource", `${system}__vs__claude-code__opus.jsonl`)).filter((j) => j.ok !== false && j.mapped)) (byQ[j.queryId] ??= {})[j.order] = j;
  return Object.fromEntries(Object.entries(byQ).filter(([, o]) => o.boarA && o.boarB).map(([q, o]) => [q, combineOrders(o.boarA.mapped, o.boarB.mapped)]));
}

const L = ["# Answers from memory, without a source (nosource)", ""];
L.push("TL;DR: v2 crypto and travel questions (30 EN + the same 30 in PT), `--corpus none`, answered from memory. Correct = judge correctness >= 4 of 5 (mean of both A/B orders), wrong = <= 1.5 (a false or nonsensical answer), partial = in between. Wilson 95% intervals. Regenerate with `node eval/scripts/nosource-report.mjs`.", "");
for (const [judge, dir] of [["Jev", "judgments-jev"], ["Claude", "judgments"]]) {
  const rows = [];
  for (const model of [...new Set(runs.map((r) => r.replace(/__none__seed\d+$/, "")))]) for (const lang of ["en", "pt"]) {
    let n = 0, correct = 0, wrong = 0, wl = 0, seeds = 0;
    for (const sys of runs.filter((r) => r.startsWith(model))) {
      const v = verdicts(dir, sys);
      if (!Object.keys(v).length) continue;
      seeds++;
      const answers = Object.fromEntries(readJsonl(join(EVAL_DIR, "results/runs/nosource", `${sys}.jsonl`)).map((r) => [r.queryId, r.answer ?? ""]));
      for (const [q, c] of Object.entries(v)) {
        if ((lang === "pt") !== q.endsWith("-pt")) continue;
        n++;
        if (c.boar.correctness >= 4) correct++;
        else if (c.boar.correctness <= 1.5) wrong++;
        if (lang === "pt" && wrongLang(answers[q] ?? "")) wl++;
      }
    }
    if (!n) continue;
    const ci = wilson(correct, n);
    rows.push(`| ${model} | ${lang.toUpperCase()} | ${seeds} | ${n} | **${pct(correct / n)}** (${pct(ci[0])}–${pct(ci[1])}) | ${pct((n - correct - wrong) / n)} | ${pct(wrong / n)} | ${lang === "pt" ? pct(wl / n) : "–"} |`);
  }
  if (!rows.length) continue;
  L.push(`## ${judge}`, "", "| Model | Lang | Seeds | Answers | Correct (95% CI) | Partial | Wrong / nonsense | PT answered in English |", "|---|---|---|---|---|---|---|---|", ...rows, "");
}
L.push("## Scope and decision", "", "- **Scope: only crypto and travel knowledge questions** (v2), 30 per language. Other kinds (general science, history, how-to) were not measured and may do better from memory; do not generalize beyond these two categories.", "- The judge compares each answer with the Opus + web search reference (PT answers against the English reference).", "- Decision (Boar, 2026-09-27): the Compacto (1.5B) keeps refusing sourceless knowledge questions; the bar to revert was ~70% correct without a source.", "");
writeFileSync(join(EVAL_DIR, "reports", "nosource.md"), L.join("\n"));
console.log(L.join("\n"));
