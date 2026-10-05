#!/usr/bin/env node
// Blocking fixed case lng-009 (scripts/lib/foreign-word-check.mjs) over the gate's lng answers (both models).
// Usage (from eval/): node scripts/allergy-translation-check.mjs <gate-label>
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { checkAllergyTranslation } from "./lib/foreign-word-check.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const label = process.argv[2];
const dir = join(EVAL_DIR, "results/gates", label, "lng");
const out = [];
for (const f of existsSync(dir) ? readdirSync(dir).filter((x) => x.endsWith(".jsonl")) : []) {
  for (const r of readFileSync(join(dir, f), "utf8").trim().split("\n").map((l) => JSON.parse(l))) {
    const c = checkAllergyTranslation(r);
    if (c) out.push({ k: `${f.replace(".jsonl", "")} · ${r.queryId}`, ...c, answer: String(r.answer ?? "").slice(0, 120).replace(/\n/g, " ") });
  }
}
const fails = out.filter((x) => !x.pass);
const verdict = !out.length ? "NOT RUN (no lng answers)" : fails.length ? "FAIL" : "PASS";
const L = [`# Fixed case lng-009 (allergy sentence in French): ${label}`, "", `TL;DR: **${verdict}**: ${out.length - fails.length}/${out.length} answers refuse or name the allergen in French. Regenerate with \`node eval/scripts/allergy-translation-check.mjs ${label}\`.`, "",
  ...out.map((x) => `- ${x.k}: ${x.pass ? "pass" : `**fail** (${x.failures.join("; ")})`} — ${x.answer}`), ""];
writeFileSync(join(EVAL_DIR, "reports", `allergy-translation-${label}.md`), L.join("\n"));
console.log(L.join("\n"));
process.exit(verdict === "PASS" ? 0 : 1);
