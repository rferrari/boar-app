#!/usr/bin/env node
// The Compacto (1.5B) in PT (Boar, 20e8c65): its answers to the 41 PT v2 items (gate results/gates/<label>/pt-compact),
// judged by Jev against the reference. Confident error = a model answer (not a refusal, not a health excerpt or fixed answer) with correctness <= 2.
// Blocks above the fixed ceiling of 10 (the s32 ceiling); reports the uncited answers ("sem fonte citada") too.
// Usage (from eval/): node scripts/pt-compact-check.mjs [<control-label>] <candidate-label>
import { copyFileSync, existsSync, readFileSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { combineOrders } from "./lib/judge-core.mjs";
import { normalizeRow } from "./lib/row-normalize.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const labels = process.argv.slice(2);
const CEILING = 10, MARGIN = 2;
const REFUSAL = /(don't|do not) support this answer|n[ãa]o sustentam esta resposta|did(n't| not) find|n[ãa]o encontrei|no (reliable |good )?(offline )?source|won't (answer|give [^.]*) from memory/i;

function measure(label) {
  const src = join(EVAL_DIR, "results/gates", label, "pt-compact/v2-pt.jsonl");
  if (!existsSync(src)) return null;
  const system = `qwen2.5-1.5b__pt-compact-${label}`;
  copyFileSync(src, join(EVAL_DIR, "results/runs/v2-pt", `${system}.jsonl`));
  execFileSync("node", ["scripts/jev-judge.mjs", "--system", system, "--dataset", "v2-pt", "--subset", "all"], { cwd: EVAL_DIR, stdio: "ignore" });
  const rows = Object.fromEntries(readFileSync(src, "utf8").trim().split("\n").map((l) => normalizeRow(JSON.parse(l))).map((r) => [r.queryId, r]));
  const by = {};
  for (const l of readFileSync(join(EVAL_DIR, "results/judgments-jev/v2-pt", `${system}__vs__claude-code__opus.jsonl`), "utf8").trim().split("\n")) {
    const j = JSON.parse(l);
    if (j.mapped && j.ok !== false) (by[j.queryId] ??= {})[j.order] = j;
  }
  const judged = Object.entries(by).filter(([, o]) => o.boarA && o.boarB).map(([q, o]) => ({ q, c: combineOrders(o.boarA.mapped, o.boarB.mapped).boar.correctness }));
  const refused = (q) => rows[q]?.declined || REFUSAL.test(rows[q]?.answer ?? "");
  // Only what the model wrote counts (decision a4644ef on s32): a health excerpt, the health fixed answer and an
  // extractive passage are the engine's, not the Compacto's, and are reported apart.
  const codes = (q) => rows[q]?.reasonCodes ?? [];
  // No model call (calculator 55d36c0, health excerpt/fixed answer, final extractive passage): the engine's answer.
  const engineOnly = (q) => rows[q]?.modelCalled === false || codes(q).some((c) => /^grounding:health-(extractive|no-source)$|^instant:final$|^answer:calculator-|^answer:temperature-conversion$/.test(c));
  const answered = judged.filter((x) => !refused(x.q) && !engineOnly(x.q));
  const engineRows = judged.filter((x) => !refused(x.q) && engineOnly(x.q));
  const engine = engineRows.length;
  const engineWrong = engineRows.filter((x) => x.c <= 2).map((x) => x.q);
  const uncited = Object.values(rows).filter((r) => !refused(r.queryId) && !(r.citedTitles?.length) && (r.reasonCodes ?? []).some((c) => c.startsWith("grounding:uncited"))).length;
  return { n: judged.length, engine, refusals: judged.length - answered.length - engine, answered: answered.length, correct: answered.filter((x) => x.c >= 4).length, errors: answered.filter((x) => x.c <= 2).map((x) => x.q), uncited, engineWrong };
}

const res = labels.map((l) => [l, measure(l)]);
// Boar 2026-09-27: blocks above the control's errors + 2 (one seed of noise); the fixed ceiling of 10 is a target.
const last = res.at(-1)[1];
const control = res.length > 1 ? res[0][1] : null;
const limit = control ? control.errors.length + MARGIN : CEILING;
// Boar 2026-09-27: the engine is deterministic (no seed noise), so its wrong answers block above the control's, no margin.
const engineLimit = control ? control.engineWrong.length : Infinity;
const pass = last && last.errors.length <= limit && last.engineWrong.length <= engineLimit;
const L = [`# Compacto (1.5B) in PT: ${labels.join(" vs ")}`, "",
  `TL;DR: **${!last ? "NOT RUN" : pass ? "PASS" : "FAIL"}**: ${labels.at(-1)} has ${last?.errors.length ?? "?"} model confident errors in PT${control ? ` vs control ${control.errors.length} (blocks above ${limit})` : ""}, ${last?.engineWrong.length ?? "?"} engine answers judged wrong${control ? ` vs control ${control.engineWrong.length} (blocks above it)` : ""}; target <= ${CEILING}: ${last && last.errors.length <= CEILING ? "met" : "not met"}. Answered without a cited source: ${last?.uncited ?? "?"}${control ? ` (control ${control.uncited})` : ""}. Jev, 1.5B seed 42 with packs, 41 PT v2 items. Regenerate with \`node eval/scripts/pt-compact-check.mjs ${labels.join(" ")}\`.`, "",
  "| Gate | Judged | Refusals | Engine answers (calculator / excerpt / fixed; not counted) | Engine answers judged wrong | Model answers | Correct when answering | Confident errors | Answered without a cited source |", "|---|---|---|---|---|---|---|---|---|"];
for (const [l, m] of res) L.push(m ? `| ${l} | ${m.n} | ${m.refusals} | ${m.engine} | ${m.engineWrong.length} | ${m.answered} | ${m.answered ? Math.round((100 * m.correct) / m.answered) : 0}% | **${m.errors.length}** | ${m.uncited} |` : `| ${l} | not run | | | | | | | |`);
L.push("", ...res.filter(([, m]) => m).map(([l, m]) => `- ${l} confident errors: ${m.errors.join(", ") || "none"}; engine answers judged wrong (read them: a health excerpt may be right but partial): ${m.engineWrong.join(", ") || "none"}`), "");
writeFileSync(join(EVAL_DIR, "reports", `pt-compact-${labels.join("-vs-")}.md`), L.join("\n"));
console.log(L.join("\n"));
process.exit(!last ? 2 : pass ? 0 : 1);
