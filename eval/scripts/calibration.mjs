#!/usr/bin/env node
// Judge calibration by hand labels.
//   sheet: writes a blind A/B sheet (no system names; order from a seed) + a separate key file.
//   apply: reads hand labels written against the sheet (A | B | tie per pair) and maps them to systems.
// Labels must be written BEFORE running or reading the judge on these pairs.
// Usage (from eval/):
//   node scripts/calibration.mjs sheet --pairs qwen2.5-1.5b-instruct-q4km__bundled:12,qwen3-4b-instruct-2507-q4km__bundled:8
//   node scripts/calibration.mjs apply --labels <file with lines "C01 A"> --labeler "<who>"
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { blind, rng } from "./lib/judge-core.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const [cmd, ...rest] = process.argv.slice(2);
const get = (k, d) => (rest.includes(k) ? rest[rest.indexOf(k) + 1] : d);
const dataset = get("--dataset", "v1");
const refName = get("--ref", "claude-code__opus");
const seed = Number(get("--seed", "99"));
const dir = join(EVAL_DIR, "results", "calibration", dataset);
const readJsonl = (p) => readFileSync(p, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l));

if (cmd === "sheet") {
  const spec = get("--pairs").split(",").map((s) => s.split(":")).map(([sys, n]) => ({ sys, n: Number(n) }));
  const questions = Object.fromEntries(readJsonl(join(EVAL_DIR, "dataset", `questions.${dataset}.jsonl`)).map((q) => [q.id, q]));
  const ref = Object.fromEntries(readJsonl(join(EVAL_DIR, "references", dataset, `${refName}.jsonl`)).filter((r) => r.ok).map((r) => [r.id, r]));
  const subset = JSON.parse(readFileSync(join(EVAL_DIR, "dataset", `subset.s32.${dataset}.json`), "utf8")).ids;
  const r = rng(seed);
  // Spread over the s32 subset (stratified), disjoint question sets per system.
  const pool = subset.filter((id) => ref[id]).map((id) => [r(), id]).sort((x, y) => x[0] - y[0]).map(([, id]) => id);
  const key = [];
  let cursor = 0;
  for (const { sys, n } of spec) {
    const runs = Object.fromEntries(readJsonl(join(EVAL_DIR, "results", "runs", dataset, `${sys}.jsonl`)).map((x) => [x.queryId, x]));
    for (let i = 0; i < n && cursor < pool.length; cursor++) {
      const id = pool[cursor];
      if (!runs[id]) continue;
      key.push({ pair: `C${String(key.length + 1).padStart(2, "0")}`, system: sys, queryId: id, boarIsA: r() < 0.5, boar: runs[id].answer, ref: ref[id].answer });
      i++;
    }
  }
  mkdirSync(dir, { recursive: true });
  const md = key.map((k) => {
    const q = questions[k.queryId];
    const [A, B] = k.boarIsA ? [k.boar, k.ref] : [k.ref, k.boar];
    return `## ${k.pair}\n\n**Q:** ${q.query}\n\n**Notes:** ${q.notes ?? "(none)"}\n\n### A\n\n${blind(A)}\n\n### B\n\n${blind(B)}\n`;
  }).join("\n---\n\n");
  writeFileSync(join(dir, "sheet.md"), md);
  writeFileSync(join(dir, "key.json"), JSON.stringify({ seed, refName, pairs: key.map(({ boar, ref, ...k }) => k) }, null, 2) + "\n");
  console.log(`wrote ${key.length} pairs to results/calibration/${dataset}/sheet.md (+ key.json)`);
} else if (cmd === "apply") {
  const key = JSON.parse(readFileSync(join(dir, "key.json"), "utf8"));
  const labels = Object.fromEntries(readFileSync(get("--labels"), "utf8").trim().split("\n").filter((l) => /^C\d+/.test(l)).map((l) => {
    const [pair, v, ...note] = l.trim().split(/\s+/);
    return [pair, { v, note: note.join(" ") }];
  }));
  const labeler = get("--labeler");
  if (!labeler) throw new Error("--labeler is required");
  const rows = key.pairs.filter((k) => labels[k.pair]).map((k) => {
    const { v, note } = labels[k.pair];
    if (!["A", "B", "tie"].includes(v)) throw new Error(`${k.pair}: label must be A, B or tie`);
    const winner = v === "tie" ? "tie" : (v === "A") === k.boarIsA ? "boar" : "ref";
    return { pair: k.pair, system: k.system, queryId: k.queryId, positional: v, winner, note, labeler, labeledAt: new Date().toISOString() };
  });
  const out = join(dir, "labels.jsonl");
  if (existsSync(out)) throw new Error(`${out} exists; labels are written once`);
  writeFileSync(out, rows.map((x) => JSON.stringify(x)).join("\n") + "\n");
  console.log(`wrote ${rows.length} labels`);
} else {
  console.error("usage: calibration.mjs sheet|apply ...");
  process.exit(1);
}
