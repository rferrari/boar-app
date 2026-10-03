#!/usr/bin/env node
// CIT-1 (Tusk/Boar): per model and set, how many answers carry a citation, the citations the engine added
// (citations:added-*) and removed (citations:removed-*), and the mean context size before -> after compression
// (context:<before>-><after> reason code, tokens), to weigh citation quality against prefill cost. No model calls.
// Usage (from eval/): node scripts/cit1-report.mjs <gate-label>
import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { normalizeRow } from "./lib/row-normalize.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const label = process.argv[2];
const base = join(EVAL_DIR, "results", "gates", label);
const groups = {};
const add = (key, r) => {
  const g = (groups[key] ??= { n: 0, cited: 0, added: 0, removed: 0, ctxN: 0, before: 0, after: 0, repeated: 0 });
  g.n++;
  // CIT-2 (Prism, engine 4073be1): the same [n] twice around one sentence end ("… Zone [1]. [1]", "… [1] [1].").
  // The same source closing consecutive sentences is legitimate and not counted.
  if (/\[(\d+)\][ \t]*[.!?]?[ \t]*\[\1\]/.test(r.answer ?? "")) g.repeated++;
  if ((r.citedTitles?.length ?? 0) > 0 || /\[\d+\]/.test(r.answer ?? "")) g.cited++;
  for (const c of r.reasonCodes ?? []) {
    if (c.startsWith("citations:added-")) g.added += c.replace("citations:added-", "").split("-").length;
    if (c.startsWith("citations:removed-")) g.removed += c.replace("citations:removed-", "").split("-").length;
    const m = c.match(/^context:(\d+)->(\d+)$/);
    if (m) { g.ctxN++; g.before += Number(m[1]); g.after += Number(m[2]); }
  }
};
const model = (f) => (/1\.5b/.test(f) ? "1.5B" : /4b/.test(f) ? "4B" : "?");
for (const f of existsSync(join(base, "runs")) ? readdirSync(join(base, "runs")) : []) {
  if (!f.startsWith("suggestions__")) continue;
  for (const r of readFileSync(join(base, "runs", f), "utf8").trim().split("\n").filter(Boolean).map((l) => normalizeRow(JSON.parse(l)))) {
    add(`${model(f)} · suggestions (all EN+PT)`, r);
    const to = r.suggestion?.offeredTo;
    if (r.suggestion?.offered !== false && (!Array.isArray(to) || to.includes(r.modelId))) add(`${model(f)} · suggestions (offered)`, r);
  }
}
for (const f of existsSync(join(base, "s32")) ? readdirSync(join(base, "s32")) : []) {
  for (const r of readFileSync(join(base, "s32", f), "utf8").trim().split("\n").filter(Boolean).map((l) => normalizeRow(JSON.parse(l)))) add(`${model(f)} · s32`, r);
}
for (const f of existsSync(join(base, "pt")) ? readdirSync(join(base, "pt")) : []) {
  for (const r of readFileSync(join(base, "pt", f), "utf8").trim().split("\n").filter(Boolean).map((l) => normalizeRow(JSON.parse(l)))) add(`4B · ${f.replace(".jsonl", "")} (packs)`, r);
}
const pct = (a, b) => (b ? `${Math.round((100 * a) / b)}% (${a}/${b})` : "–");
const L = [`# CIT-1: citations and context size, ${label}`, "", "Answers with a citation ([n] in the text or done.cited), citations the engine added/removed after generation, and the mean retrieved context before → after compression (tokens, from the context:<before>-><after> reason code; answers without it, e.g. fixed or extractive ones, are not in the mean).", ""];
L.push("| Model · set | Answers | With a citation | [n] added | [n] removed | Duplicate [n] at a sentence end (CIT-2) | Mean context tokens before → after (answers) |", "|---|---|---|---|---|---|---|");
for (const [k, g] of Object.entries(groups).sort()) L.push(`| ${k} | ${g.n} | ${pct(g.cited, g.n)} | ${g.added} | ${g.removed} | ${g.repeated} | ${g.ctxN ? `${Math.round(g.before / g.ctxN)} → ${Math.round(g.after / g.ctxN)} (${g.ctxN})` : "–"} |`);
L.push("", `Regenerate with \`node eval/scripts/cit1-report.mjs ${label}\`.`, "");
writeFileSync(join(EVAL_DIR, "reports", `cit1-${label}.md`), L.join("\n"));
console.log(L.join("\n"));
