#!/usr/bin/env node
// UX check for the uncited-answer safety net (engine-routing dc41215, Tusk/Boar 2026-09-27): on the s32 answers of a
// gate candidate, which answers got the "not from an offline source" preface (grounding:uncited-preface) or the
// Compacto decline (grounding:uncited-declined-compact), and were those answers correct AND cited ([n]) in the control?
// A preface on an answer that was correct and cited is a UX regression (the net fired on a good grounded answer).
// Uses the Jev judgments of both gates (gate-s32-judge.sh). No model calls.
// Usage (from eval/): node scripts/preface-regression.mjs <control-label> <candidate-label>
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { normalizeRow } from "./lib/row-normalize.mjs";
import { combineOrders } from "./lib/judge-core.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const [control, candidate] = process.argv.slice(2);
const readJsonl = (p) => (existsSync(p) ? readFileSync(p, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l)) : []);
const CODES = ["grounding:uncited-preface", "grounding:uncited-declined-compact"];

function load(model, label) {
  const rows = Object.fromEntries(readJsonl(join(EVAL_DIR, "results/gates", label, "s32", `${model}__essential__app.jsonl`)).map(normalizeRow).map((r) => [r.queryId, r]));
  const byQ = {};
  for (const j of readJsonl(join(EVAL_DIR, "results/judgments-jev/v1", `${model}__essential__app__${label}__vs__claude-code__opus.jsonl`)).filter((j) => j.mapped)) (byQ[j.queryId] ??= {})[j.order] = j;
  const judged = Object.fromEntries(Object.entries(byQ).filter(([, o]) => o.boarA && o.boarB).map(([q, o]) => [q, combineOrders(o.boarA.mapped, o.boarB.mapped)]));
  return { rows, judged };
}

const L = [`# Uncited-answer net on s32: ${candidate} vs ${control}`, ""];
let regressions = 0;
for (const model of ["qwen3-4b-instruct-2507-q4km", "qwen2.5-1.5b-instruct-q4km"]) {
  const c = load(model, control), k = load(model, candidate);
  const hit = Object.values(k.rows).filter((r) => CODES.some((x) => (r.reasonCodes ?? []).includes(x)));
  const noCite = (rows) => Object.values(rows).filter((r) => !/\[\d+\]/.test(r.answer ?? "")).length;
  // Cost: the net fired on an answer the control got right. Gain: it fired on an answer the control got wrong.
  const cost = hit.filter((r) => c.judged[r.queryId]?.boar.correctness >= 4).length;
  const gain = hit.filter((r) => c.judged[r.queryId] && c.judged[r.queryId].boar.correctness < 4).length;
  L.push(`## ${model}`, "",
    `Answers with the net: ${hit.length}/${Object.keys(k.rows).length} — cost (correct in the control): **${cost}**, gain (wrong in the control): **${gain}**.`,
    `Answers without any [n]: control ${noCite(c.rows)}/${Object.keys(c.rows).length}, candidate ${noCite(k.rows)}/${Object.keys(k.rows).length} (post-processing strips unsupported citations, so "no [n]" is most answers).`, "");
  if (!hit.length) { L.push(""); continue; }
  L.push("| Question | Code | Control: correct (Jev) | Control: cited [n] | Candidate: correct (Jev) | UX regression |", "|---|---|---|---|---|---|");
  for (const r of hit) {
    const code = CODES.find((x) => (r.reasonCodes ?? []).includes(x)).replace("grounding:", "");
    const cj = c.judged[r.queryId], kj = k.judged[r.queryId];
    const cCorrect = cj ? cj.boar.correctness >= 4 : null;
    const cCited = /\[\d+\]/.test(c.rows[r.queryId]?.answer ?? "");
    const reg = cCorrect && cCited;
    if (reg) regressions++;
    L.push(`| ${r.queryId} | ${code} | ${cCorrect === null ? "n/a" : cCorrect ? "yes" : "no"} | ${cCited ? "yes" : "no"} | ${kj ? (kj.boar.correctness >= 4 ? "yes" : "no") : "n/a"} | ${reg ? "**yes**" : "no"} |`);
  }
  L.push("");
}
L.splice(2, 0, `TL;DR: ${regressions} answer(s) got the net although they were correct and cited in the control (UX regression). Regenerate with \`node eval/scripts/preface-regression.mjs ${control} ${candidate}\`.`, "");
writeFileSync(join(EVAL_DIR, "reports", `preface-regression-${control}-vs-${candidate}.md`), L.join("\n"));
console.log(L.join("\n"));
