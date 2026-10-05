#!/usr/bin/env node
// Health routing diff (Boar 2026-09-27, lng-009): which gate answers took the health path (grounding:health-*,
// answer:first-aid-card*) and which one, candidate vs control, over runs, pt, pt-compact, s32 and lng. Lists every
// answer whose path changed. Reported in the gate verdict.
// Usage (from eval/): node scripts/health-route.mjs <control-label> <candidate-label>
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const [control, candidate] = process.argv.slice(2);
const route = (codes) => {
  const h = codes.filter((c) => /^grounding:health-(strict|extractive|no-source|general-source)$|^answer:first-aid-card/.test(c));
  return h.length ? h.sort().join("+") : "not health";
};
function load(label) {
  const out = {};
  for (const sub of ["runs", "pt", "pt-compact", "s32", "lng"]) {
    const dir = join(EVAL_DIR, "results/gates", label, sub);
    if (!existsSync(dir)) continue;
    for (const f of readdirSync(dir).filter((x) => x.endsWith(".jsonl"))) {
      for (const r of readFileSync(join(dir, f), "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l))) {
        out[`${sub}/${f.replace(".jsonl", "")} · ${r.queryId} · ${r.seed ?? ""}`] = { route: route(r.reasonCodes ?? []), q: r.query ?? "" };
      }
    }
  }
  return out;
}
const C = load(control), K = load(candidate);
const changed = Object.keys(K).filter((k) => C[k] && C[k].route !== K[k].route);
const health = Object.values(K).filter((x) => x.route !== "not health").length;
const L = [`# Health routing: ${candidate} vs ${control}`, "", `TL;DR: ${changed.length} of ${Object.keys(K).length} answers changed path (${health} on the health path in the candidate). Regenerate with \`node eval/scripts/health-route.mjs ${control} ${candidate}\`.`, ""];
if (changed.length) L.push("| Answer | Control | Candidate | Question |", "|---|---|---|---|", ...changed.map((k) => `| ${k} | ${C[k].route} | ${K[k].route} | ${K[k].q.slice(0, 80).replace(/\|/g, "/")} |`), "");
writeFileSync(join(EVAL_DIR, "reports", `health-route-${control}-vs-${candidate}.md`), L.join("\n"));
console.log(L.join("\n"));
