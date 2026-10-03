#!/usr/bin/env node
// s32 no-regression criterion for a gate candidate (Boar, 2026-09-27), from the s32 runs a gate made (GATE_S32=1)
// and their Claude + Jev judgments (gate-s32-judge.sh). No model calls.
//   4B: quality ratio may not drop more than 3 points vs the control on either judge, and at most 2/32 refusals
//       (more refusals = an onTopic / grounding-guard bug).
//   1.5B: refusing without a source is a product decision, so report refusal rate, correct-when-answering and
//       confident errors (an answer, not a refusal, with correctness <= 2); regression = correct-when-answering
//       falls or confident errors rise.
// Usage (from eval/): node scripts/s32-gate-check.mjs <control-label> <candidate-label>
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { normalizeRow } from "./lib/row-normalize.mjs";
import { combineOrders, summarize } from "./lib/judge-core.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const [control, candidate] = args.filter((x, i) => !x.startsWith("--") && args[i - 1] !== "--judges");
// --judges jev: decide on Jev alone (Claude kept for the final candidate; the judges agree ~91% on s32).
const JUDGES = (args.includes("--judges") ? args[args.indexOf("--judges") + 1] : "claude,jev").split(",");
if (!control || !candidate) throw new Error("usage: s32-gate-check.mjs <control-label> <candidate-label>");
const readJsonl = (p) => (existsSync(p) ? readFileSync(p, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l)) : []);
const REFUSAL = /(don't|do not) support this answer|n[ãa]o sustentam esta resposta|did(n't| not) find|(no|don't have a|do not have a) (reliable |good )?(offline )?source|won't (answer|give [^.]*) from memory|not (in|from) the offline library|n[ãa]o encontrei|n[ãa]o tenho (uma )?fonte/i;
const COMPACT_ERROR_CEILING = 10;
const MODELS = { "4B": "qwen3-4b-instruct-2507-q4km", "1.5B": "qwen2.5-1.5b-instruct-q4km" };
const fmt = (x, d = 2) => (Number.isFinite(x) ? x.toFixed(d) : "n/a");
const pct = (x) => (Number.isFinite(x) ? `${Math.round(100 * x)}%` : "n/a");

function measure(model, label) {
  const system = `${model}__essential__app__${label}`;
  const answers = Object.fromEntries(readJsonl(join(EVAL_DIR, "results/gates", label, "s32", `${model}__essential__app.jsonl`)).map(normalizeRow).map((r) => [r.queryId, r.answer ?? ""]));
  const rows = readJsonl(join(EVAL_DIR, "results/gates", label, "s32", `${model}__essential__app.jsonl`)).map(normalizeRow);
  // d3a4370: the Compacto also declines when attribution removes every citation ("The passages found don't support
// this answer"); a row flagged declined counts as a refusal whatever its wording.
  const declinedRows = new Set(rows.filter((r) => r.declined).map((r) => r.queryId));
  const refused = new Set(Object.entries(answers).filter(([q, a]) => REFUSAL.test(a) || declinedRows.has(q)).map(([q]) => q));
  // Kinds, from the app's reason codes: a health question without a source refuses by design (safety), a knowledge
  // question may refuse or answer from memory after the guard dropped every source.
  const codes = Object.fromEntries(rows.map((r) => [r.queryId, r.reasonCodes ?? []]));
  const healthRefusals = new Set([...refused].filter((q) => codes[q]?.includes("grounding:health-no-source")));
  const memory = Object.keys(answers).filter((q) => codes[q]?.includes("grounding:no-source-memory")).length;
  const out = { n: Object.keys(answers).length, refusals: refused.size, healthRefusals: healthRefusals.size, memory };
  for (const [judge, dir] of [["claude", "judgments"], ["jev", "judgments-jev"]]) {
    const byQ = {};
    for (const j of readJsonl(join(EVAL_DIR, "results", dir, "v1", `${system}__vs__claude-code__opus.jsonl`)).filter((j) => j.ok !== false && j.mapped)) (byQ[j.queryId] ??= {})[j.order] = j;
    const pairs = Object.entries(byQ).filter(([, o]) => o.boarA && o.boarB).map(([q, o]) => ({ q, ...combineOrders(o.boarA.mapped, o.boarB.mapped) }));
    if (!pairs.length) continue;
    const answered = pairs.filter((p) => !refused.has(p.q));
    const noHealthByDesign = pairs.filter((p) => !healthRefusals.has(p.q));
    out[judge] = {
      judged: pairs.length,
      ratio: summarize(pairs, { seed: 11 }).qualityRatio,
      ratioExclHealthRefusals: noHealthByDesign.length ? summarize(noHealthByDesign, { seed: 11 }).qualityRatio : NaN,
      correctWhenAnswering: answered.filter((p) => p.boar.correctness >= 4).length / Math.max(1, answered.length),
      confidentErrors: answered.filter((p) => p.boar.correctness <= 2).length,
    };
  }
  return out;
}

const L = [`# s32 gate check: ${candidate} vs ${control}`, ""];
let fail = false;
const verdicts = [];
for (const [name, model] of Object.entries(MODELS)) {
  const c = measure(model, control), k = measure(model, candidate);
  L.push(`## ${name}`, "", "| | Control | Candidate |", "|---|---|---|");
  L.push(`| Refusals (all) | ${c.refusals}/${c.n} | ${k.refusals}/${k.n} |`);
  L.push(`| Health fixed answer ("no source + emergency", by design; not a refusal, not in the ratio) | ${c.healthRefusals} | ${k.healthRefusals} |`);
  L.push(`| Answered from memory after the guard dropped every source | ${c.memory} | ${k.memory} |`);
  for (const j of ["claude", "jev"]) {
    L.push(`| Quality ratio (${j}) | ${fmt(c[j]?.ratio)} | ${fmt(k[j]?.ratio)} |`);
    L.push(`| Quality ratio without the health refusals (${j}) | ${fmt(c[j]?.ratioExclHealthRefusals)} | ${fmt(k[j]?.ratioExclHealthRefusals)} |`);
    L.push(`| Correct when answering (${j}) | ${pct(c[j]?.correctWhenAnswering)} | ${pct(k[j]?.correctWhenAnswering)} |`);
    L.push(`| Confident errors (${j}) | ${c[j]?.confidentErrors ?? "n/a"} | ${k[j]?.confidentErrors ?? "n/a"} |`);
  }
  const missing = JUDGES.some((j) => !c[j] || !k[j]);
  let ok;
  if (missing) ok = null;
  // Health refusals are the intended safe answer, so they count neither as refusals nor against the ratio.
  else if (name === "4B") ok = JUDGES.every((j) => k[j].ratioExclHealthRefusals >= c[j].ratioExclHealthRefusals - 0.03) && k.refusals - k.healthRefusals <= 2;
  // Boar 2026-09-27 (decision B, Compacto declines only without an on-topic source): the 1.5B fails only when its
  // confident errors rise above the control's; refusal rate and correct-when-answering are reported, not blocking.
  // Boar 2026-09-27 (after ea21e82): a FIXED ceiling of 10 confident errors (control-a8ee6bf-r2, the Compacto before
  // decision B), not the moving control: answering more (fewer declines) with a few more errors is the agreed trade-off.
  else ok = JUDGES.every((j) => k[j].confidentErrors <= COMPACT_ERROR_CEILING);
  if (ok === false) fail = true;
  verdicts.push(`${name} ${ok === null ? "INCOMPLETE (judgments missing)" : ok ? "PASS" : "FAIL"}`);
  L.push("", `Verdict ${name}: **${verdicts.at(-1).split(" ").slice(1).join(" ")}**`, "");
}
L.splice(2, 0, `TL;DR: ${verdicts.join(" · ")}. 4B: ratio (health fixed answers excluded) within 3 points of the control and <= 2/32 knowledge refusals. 1.5B (Compacto): at most ${COMPACT_ERROR_CEILING} confident errors (fixed ceiling); refusals and correct-when-answering are reported, not blocking. Health fixed answers are reported apart (decision a4644ef). Judges: ${JUDGES.join(" + ")}. Regenerate with \`node eval/scripts/s32-gate-check.mjs ${args.join(" ")}\`.`, "");
writeFileSync(join(EVAL_DIR, "reports", `s32-check-${control}-vs-${candidate}.md`), L.join("\n"));
console.log(L.join("\n"));
process.exit(fail ? 1 : 0);
