#!/usr/bin/env node
// Blocking (CIT-2, Prism/Boar 2026-09-27): no chat screen in the gate shows the same [n] twice around a sentence end
// (scripts/lib/screen-check.mjs). Needs runner rows with `screen` (runner since 2026-09-27); older gates are skipped.
// Usage (from eval/): node scripts/screen-check.mjs <gate-label>
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { checkScreen } from "./lib/screen-check.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const label = process.argv[2];
let checked = 0;
const fails = [];
for (const sub of ["runs", "pt", "s32"]) {
  const dir = join(EVAL_DIR, "results/gates", label, sub);
  if (!existsSync(dir)) continue;
  for (const f of readdirSync(dir).filter((x) => x.endsWith(".jsonl"))) {
    for (const r of readFileSync(join(dir, f), "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l))) {
      const c = checkScreen(r);
      if (!c) continue;
      checked++;
      if (!c.pass) fails.push(`| ${sub}/${f.replace(".jsonl", "")} | ${r.queryId} | ${c.where} | ${c.text.replace(/\|/g, "/").replace(/\n/g, " ")} |`);
    }
  }
}
const verdict = !checked ? "NOT RUN (no screen in these rows)" : fails.length ? "FAIL" : "PASS";
const L = [`# Chat screen check (CIT-2): ${label}`, "", `TL;DR: **${verdict}**: ${fails.length} of ${checked} chat screens show the same [n] twice around a sentence end. Regenerate with \`node eval/scripts/screen-check.mjs ${label}\`.`, ""];
if (fails.length) L.push("| Run | Question | Where | Text |", "|---|---|---|---|", ...fails, "");
writeFileSync(join(EVAL_DIR, "reports", `screen-${label}.md`), L.join("\n"));
console.log(L.join("\n"));
process.exit(fails.length ? 1 : 0);
