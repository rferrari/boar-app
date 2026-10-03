#!/usr/bin/env node
// Fixed case trv-007 over every gate answer to it (pt, pt-compact; EN and PT). Reported (Boar 2026-09-27).
// Usage (from eval/): node scripts/foreign-word-check.mjs <gate-label>
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { checkForeignWord } from "./lib/foreign-word-check.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const label = process.argv[2];
const out = [];
for (const sub of ["pt", "pt-compact"]) {
  const dir = join(EVAL_DIR, "results/gates", label, sub);
  if (!existsSync(dir)) continue;
  for (const f of readdirSync(dir).filter((x) => x.endsWith(".jsonl"))) {
    for (const r of readFileSync(join(dir, f), "utf8").trim().split("\n").map((l) => JSON.parse(l))) {
      const c = checkForeignWord(r);
      if (c) out.push({ k: `${sub}/${f.replace(".jsonl", "")} · ${r.queryId}`, ...c });
    }
  }
}
const fails = out.filter((x) => !x.pass);
const L = [`# Fixed case trv-007 (foreign word, script): ${label}`, "", `TL;DR: **${fails.length ? "FAIL" : "PASS"}** (reported): ${out.length - fails.length}/${out.length} answers give Thai "thank you" in Thai script or romanization, no Khmer; gender particle missing in ${out.filter((x) => x.warnings.includes("no gender particle (khrap / kha)")).length}. Regenerate with \`node eval/scripts/foreign-word-check.mjs ${label}\`.`, "",
  ...out.map((x) => `- ${x.k}: ${x.pass ? "pass" : "**fail**"}${x.failures.length ? ` (${x.failures.join("; ")})` : ""}${x.warnings.length ? ` [${x.warnings.join("; ")}]` : ""}`), ""];
writeFileSync(join(EVAL_DIR, "reports", `foreign-word-${label}.md`), L.join("\n"));
console.log(L.join("\n"));
