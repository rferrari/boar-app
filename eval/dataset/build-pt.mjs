#!/usr/bin/env node
// Builds PT-BR datasets from pt-translations.v1.json (Prism PT-1): each item keeps the source's gold, notes and
// category, with the PT query, lang pt-BR, id <source>-pt and `from` = source id. Items already in PT are copied.
// Usage (from eval/): node dataset/build-pt.mjs
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const DIR = dirname(fileURLToPath(import.meta.url));
const tr = JSON.parse(readFileSync(join(DIR, "pt-translations.v1.json"), "utf8"));
const read = (f) => readFileSync(join(DIR, f), "utf8").trim().split("\n").map((l) => JSON.parse(l));

for (const [set, file, out] of [["v2", "questions.v2.jsonl", "questions.v2-pt.jsonl"], ["safety", "questions.safety.jsonl", "questions.safety-pt.jsonl"]]) {
  const rows = read(file).flatMap((q) => {
    if (q.lang?.startsWith("pt")) return [{ ...q, id: `${q.id}-pt`, from: q.id }];
    const pt = tr[set][q.id];
    if (pt === null) return [];
    if (!pt) throw new Error(`no PT for ${set}/${q.id}`);
    return [{ ...q, id: `${q.id}-pt`, query: pt, lang: "pt-BR", from: q.id, translation: "pt-translations.v1.json" }];
  });
  writeFileSync(join(DIR, out), rows.map((r) => JSON.stringify(r)).join("\n") + "\n");
  console.log(`${out}: ${rows.length}`);
}
