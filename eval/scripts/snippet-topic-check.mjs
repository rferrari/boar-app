#!/usr/bin/env node
// Blocking fixed case (Boar 2026-09-27): every instant passage the chat shows for a suggestion is on its topic
// (scripts/lib/snippet-topic.mjs). Needs rows with `screen` (runner ab655a6 and later).
// Usage (from eval/): node scripts/snippet-topic-check.mjs <gate-label>
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { checkSnippetTopic } from "./lib/snippet-topic.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const label = process.argv[2];
const dir = join(EVAL_DIR, "results/gates", label, "runs");
let checked = 0;
const fails = [];
for (const f of existsSync(dir) ? readdirSync(dir).filter((x) => x.startsWith("suggestions__")) : []) {
  for (const r of readFileSync(join(dir, f), "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l))) {
    const c = checkSnippetTopic(r);
    if (!c) continue;
    checked++;
    const offered = r.suggestion?.offered !== false && (!Array.isArray(r.suggestion?.offeredTo) || r.suggestion.offeredTo.includes(r.modelId));
    if (!c.pass) fails.push({ offered, line: `| ${f.replace(".jsonl", "")} | ${r.queryId} | ${offered ? "yes" : "no (measured)"} | ${c.title ?? "?"} | ${c.passage.replace(/\|/g, "/").replace(/\n/g, " ")} |` });
  }
}
const blocking = fails.filter((x) => x.offered).length;
const verdict = !checked ? "NOT RUN (no passage shown, or no screen in these rows)" : blocking ? "FAIL" : "PASS";
const L = [`# Instant passage on topic: ${label}`, "", `TL;DR: **${verdict}**: ${fails.length} of ${checked} passages shown for a suggestion are off topic (${blocking} on a pair the app offers). Regenerate with \`node eval/scripts/snippet-topic-check.mjs ${label}\`.`, ""];
if (fails.length) L.push("| Run | Question | Offered | Passage source | Passage |", "|---|---|---|---|---|", ...fails.map((x) => x.line), "");
writeFileSync(join(EVAL_DIR, "reports", `snippet-topic-${label}.md`), L.join("\n"));
console.log(L.join("\n"));
process.exit(blocking ? 1 : 0);
