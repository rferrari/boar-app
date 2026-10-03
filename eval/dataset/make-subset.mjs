#!/usr/bin/env node
// Stratified iteration subset: N questions per category, drawn with a recorded seed. Written once per dataset version.
// Usage (from eval/): node dataset/make-subset.mjs [--dataset v1] [--per 4] [--seed 2026]
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { rng } from "../scripts/lib/judge-core.mjs";

const DIR = dirname(fileURLToPath(import.meta.url));
const a = process.argv.slice(2);
const get = (k, d) => (a.includes(k) ? a[a.indexOf(k) + 1] : d);
const dataset = get("--dataset", "v1");
const per = Number(get("--per", "4"));
const seed = Number(get("--seed", "2026"));
const qs = readFileSync(join(DIR, `questions.${dataset}.jsonl`), "utf8").trim().split("\n").map((l) => JSON.parse(l));
const r = rng(seed);
const byCat = {};
for (const q of qs) (byCat[q.category] ??= []).push(q.id);
const ids = Object.values(byCat).flatMap((list) => list.map((id) => [r(), id]).sort((x, y) => x[0] - y[0]).slice(0, per).map(([, id]) => id));
const name = `subset.s${ids.length}.${dataset}.json`;
writeFileSync(join(DIR, name), JSON.stringify({ dataset, per, seed, ids }, null, 2) + "\n");
console.log(`wrote ${name} (${ids.length} ids)`);
