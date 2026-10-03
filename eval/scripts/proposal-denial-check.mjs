#!/usr/bin/env node
// Blocking (Boar 2026-09-27): no gate answer may deny that an EIP/ERC/BIP exists when its page is in the search
// top-3 (scripts/lib/proposal-denial.mjs). Scans every answer the gate produced (runs, pt, s32). No model calls.
// Usage (from eval/): node scripts/proposal-denial-check.mjs <gate-label>
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { checkProposalDenial } from "./lib/proposal-denial.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const label = process.argv[2];
const base = join(EVAL_DIR, "results/gates", label);
let checked = 0;
const fails = [];
for (const sub of ["runs", "pt", "s32"]) {
  const dir = join(base, sub);
  if (!existsSync(dir)) continue;
  for (const f of readdirSync(dir).filter((x) => x.endsWith(".jsonl"))) {
    for (const r of readFileSync(join(dir, f), "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l))) {
      const c = checkProposalDenial(r);
      if (!c || !c.inTop3.length) continue;
      checked++;
      if (!c.pass) fails.push(`| ${sub}/${f.replace(".jsonl", "")} | ${r.queryId} | ${c.inTop3.join(", ")} | ${c.denial.replace(/\|/g, "/")} |`);
    }
  }
}
const L = [`# Proposal denial check: ${label}`, "", `TL;DR: **${fails.length ? "FAIL" : "PASS"}**: ${fails.length} of ${checked} answers on an EIP/ERC/BIP whose page is in the top-3 deny that it exists. Regenerate with \`node eval/scripts/proposal-denial-check.mjs ${label}\`.`, ""];
if (fails.length) L.push("| Run | Question | In top-3 | Denial |", "|---|---|---|---|", ...fails, "");
writeFileSync(join(EVAL_DIR, "reports", `proposal-denial-${label}.md`), L.join("\n"));
console.log(L.join("\n"));
process.exit(fails.length ? 1 : 0);
