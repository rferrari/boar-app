#!/usr/bin/env node
// Duplicate "not from an offline source" disclaimer (gate 19bb043): the app's preface plus the model's own sentence
// saying the same ("Essa resposta não está em uma fonte offline"). Counts gate answers (runs, pt, pt-compact, s32)
// with two or more such sentences. Blocking (Boar 2026-09-27): exit 1 = more than the control.
// Usage (from eval/): node scripts/dup-disclaimer.mjs <control-label> <candidate-label>
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const [control, candidate] = process.argv.slice(2);
// cd1478a: the model rewords it ("não está em um banco de dados offline"), so any offline store counts.
const DISCLAIMER = /(n[ãa]o (vem|est[áa]) (de |em |n[ao] )?(um |uma |nenhuma |nenhum )?(fonte|banco de dados|base( de dados| de conhecimento)?|acervo|biblioteca) offline|not (from|in) (an |any )?offline (source|database|library|knowledge base)|n[ãa]o h[áa] (nenhuma )?fonte offline)/gi;
function scan(label) {
  const hits = [];
  let n = 0;
  for (const sub of ["runs", "pt", "pt-compact", "s32"]) {
    const dir = join(EVAL_DIR, "results/gates", label, sub);
    if (!existsSync(dir)) continue;
    for (const f of readdirSync(dir).filter((x) => x.endsWith(".jsonl"))) {
      for (const r of readFileSync(join(dir, f), "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l))) {
        n++;
        if ((String(r.answer ?? "").match(DISCLAIMER) ?? []).length >= 2) hits.push(`${sub}/${f.replace(".jsonl", "")} · ${r.queryId}`);
      }
    }
  }
  return { n, hits };
}
const c = scan(control), k = scan(candidate);
const fail = k.hits.length > c.hits.length;
const L = [`# Duplicate offline-source disclaimer: ${candidate} vs ${control}`, "", `TL;DR: **${fail ? "FAIL" : "PASS"}**: ${k.hits.length} of ${k.n} answers repeat the disclaimer (control ${c.hits.length}). Regenerate with \`node eval/scripts/dup-disclaimer.mjs ${control} ${candidate}\`.`, "", ...k.hits.map((h) => `- ${h}`), ""];
writeFileSync(join(EVAL_DIR, "reports", `dup-disclaimer-${control}-vs-${candidate}.md`), L.join("\n"));
console.log(L.join("\n"));
process.exit(fail ? 1 : 0);
