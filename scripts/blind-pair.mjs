#!/usr/bin/env node
// A blind read of two models' answers (t9 0443 item 4: Qwen3-0.6B against the 1.7B): from bench runs (.jsonl), each
// question's two answers side by side as A and B, the model drawn at random per question, the key in its own file.
//
//   node scripts/blind-pair.mjs --models qwen3-0.6b,qwen3-1.7b --out blind/ run-06-first.jsonl run-17-first.jsonl
//   node scripts/blind-pair.mjs --models qwen3-0.6b,qwen3-1.7b --pass succinct --run <runId> --seed 42 --out blind/ <runs>
//
// Writes <out>/blind.md (to read and mark), <out>/marks.csv (the marks) and <out>/key.json (which is A, which is B; open
// it after marking). One item per run, pass (succinct, detailed, deep) and question both models answered. --seed makes
// the same draw again (it is recorded in key.json); without it the seed is random. Needs only Node.
import { mkdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { randomInt } from "node:crypto";
import { join, basename } from "node:path";
import { blind, pairs, seeded } from "./lib/blind-pair.mjs";

const args = process.argv.slice(2);
const opt = (name) => {
  const i = args.indexOf(name);
  if (i < 0) return undefined;
  const v = args[i + 1];
  if (v === undefined || v.startsWith("--")) fail(`${name} needs a value`);
  args.splice(i, 2);
  return v;
};
function fail(msg) {
  console.error(msg);
  console.error("usage: node scripts/blind-pair.mjs --models <a>,<b> --out <dir> [--pass P] [--run RUN_ID] [--seed N] <run.jsonl> ...");
  process.exit(1);
}
const models = opt("--models")?.split(",").map((s) => s.trim()).filter(Boolean);
const out = opt("--out");
const pass = opt("--pass");
const run = opt("--run");
const seedArg = opt("--seed");
const files = args.filter((a) => !a.startsWith("--"));
if (!models || !out || files.length === 0) fail("missing --models, --out or a run file");
const seed = seedArg === undefined ? randomInt(2 ** 31) : Number(seedArg);
if (!Number.isInteger(seed) || seed < 0) fail("--seed must be a whole number");
for (const f of ["blind.md", "key.json", "marks.csv"]) if (existsSync(join(out, f))) fail(`${join(out, f)} exists: pick a new --out`);

let result;
try {
  const p = pairs(files.map((f) => readFileSync(f, "utf8")), models, { run, pass });
  if (p.items.length === 0) fail("no question was answered by both models in these runs");
  result = blind(p, seeded(seed), { seed, sources: files.map((f) => basename(f)) });
} catch (e) {
  fail(e.message);
}
mkdirSync(out, { recursive: true });
writeFileSync(join(out, "blind.md"), result.markdown);
writeFileSync(join(out, "marks.csv"), result.marksCsv);
writeFileSync(join(out, "key.json"), JSON.stringify(result.key, null, 2) + "\n");
console.log(`${result.key.items.length} pairs -> ${join(out, "blind.md")}, ${join(out, "marks.csv")}; key (seed ${seed}) -> ${join(out, "key.json")}`);
