#!/usr/bin/env node
// Generates "frontier + web search" reference answers with Claude Code headless (`claude -p`),
// on the user's subscription: sequential, one process at a time, resumable (skips ids already written).
// Usage (from eval/): node scripts/gen-references.mjs [--dataset v1] [--ids a,b] [--limit N] [--model opus]
import { appendFileSync, existsSync, mkdirSync, readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { cleanEnv, isRateLimited, logSpend, runClaude, sleep, totalSpend, usageSummary } from "./lib/claude-cli.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const PROMPT_VERSION = "reference.v1";
const TOOLS = ["WebSearch", "WebFetch"];

const a = process.argv.slice(2);
const get = (k, d) => (a.includes(k) ? a[a.indexOf(k) + 1] : d);
const dataset = get("--dataset", "v1");
const model = get("--model", "opus");
const ids = get("--ids")?.split(",");
const limit = Number(get("--limit", "0"));
const gapMs = Number(get("--gap-ms", "5000"));
const SPEND = join(EVAL_DIR, "results", "spend.jsonl");
const WARN_USD = 25;
const out = get("--out", join(EVAL_DIR, "references", dataset, `claude-code__${model}.jsonl`));

const systemPrompt = readFileSync(join(EVAL_DIR, "prompts", `${PROMPT_VERSION}.md`), "utf8");
const promptSha = createHash("sha256").update(systemPrompt).digest("hex").slice(0, 12);
const cliVersion = execFileSync("claude", ["--version"], { env: cleanEnv(), encoding: "utf8" }).trim();

let questions = readFileSync(join(EVAL_DIR, "dataset", `questions.${dataset}.jsonl`), "utf8").trim().split("\n").map((l) => JSON.parse(l));
if (ids) questions = questions.filter((q) => ids.includes(q.id));
if (limit) questions = questions.slice(0, limit);

mkdirSync(dirname(out), { recursive: true });
const done = new Set(existsSync(out) ? readFileSync(out, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l)).filter((r) => r.ok).map((r) => r.id) : []);

/** Splits the trailing "Sources:" block off the answer; falls back to any URLs in the text. */
export function splitSources(text) {
  const m = text.match(/\n\**Sources:?\**\s*\n([\s\S]*)$/i);
  const body = m ? text.slice(0, m.index).trim() : text.trim();
  const urls = [...new Set(((m ? m[1] : text).match(/https?:\/\/[^\s)\]>"']+/g) ?? []).map((u) => u.replace(/[.,;]+$/, "")))];
  return { answer: body, sources: urls };
}

let first = true;
for (const q of questions) {
  if (done.has(q.id)) continue;
  if (!first) await sleep(gapMs);
  first = false;
  // Location-dependent items carry the device position; the reference gets it the way a phone app would.
  const prompt = q.context ? `${q.query}\n\n(My current location: ${q.context.lat}, ${q.context.lon})` : q.query;
  const r = await runClaude({ prompt, systemPrompt, model, tools: TOOLS });
  logSpend(SPEND, `reference:${dataset}`, r);
  if (isRateLimited(r)) {
    console.error(`RATE LIMITED at ${q.id}: ${r.error ?? r.stderr}. Stopping; rerun resumes from here.`);
    process.exit(3);
  }
  const text = r.json?.result ?? "";
  const { answer, sources } = splitSources(text);
  const row = {
    id: q.id,
    category: q.category,
    query: q.query,
    ok: r.ok && answer.length > 0,
    error: r.error,
    answer,
    sources,
    rawResult: text,
    wallMs: Math.round(r.wallMs),
    ...(r.json ? usageSummary(r.json) : {}),
    webSearches: r.toolCalls.filter((c) => c.name === "WebSearch").map((c) => c.input?.query),
    webFetches: r.toolCalls.filter((c) => c.name === "WebFetch").map((c) => c.input?.url),
    requestedModel: model,
    tools: TOOLS,
    promptVersion: PROMPT_VERSION,
    promptSha,
    cliVersion,
    createdAt: new Date().toISOString(),
  };
  appendFileSync(out, JSON.stringify(row) + "\n");
  console.log(`${q.id} ${row.ok ? "ok" : "FAIL"} wall=${(row.wallMs / 1000).toFixed(1)}s search=${row.webSearches.length} fetch=${row.webFetches.length} out=${row.outputTokens ?? "?"}tok listUsd=${row.listPriceEquivalentUsd?.toFixed(3) ?? "?"}`);
}
const spent = totalSpend(SPEND);
console.log(`wrote ${out}; cumulative list-price equivalent ${spent.toFixed(2)} USD${spent > WARN_USD ? ` (over ${WARN_USD}: tell Boar)` : ""}`);
