#!/usr/bin/env node
// Blind pairwise judge with Claude Code headless (`claude -p`, no tools), on the user's subscription.
// Every pair runs in BOTH orders (A/B swapped) to measure position bias; the first order is drawn from a
// recorded seed. Sequential, resumable (skips (queryId, order) already judged), stops on a rate limit.
// Usage (from eval/): node scripts/judge.mjs --system qwen2.5-1.5b-instruct-q4km__bundled [--dataset v1] [--subset s32|all] [--categories a,b] [--seed 7]
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { cleanEnv, isRateLimited, logSpend, runClaude, sleep, totalSpend } from "./lib/claude-cli.mjs";
import { JUDGE_SYSTEM, VERDICT_SCHEMA, blind, firstOrders, judgePrompt, mapVerdict } from "./lib/judge-core.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const JUDGE_VERSION = "judge.v1";

const a = process.argv.slice(2);
const get = (k, d) => (a.includes(k) ? a[a.indexOf(k) + 1] : d);
const dataset = get("--dataset", "v1");
const system = get("--system");
// --against <runs file name>: compare two BOAR systems (e.g. deep vs fast tier) instead of BOAR vs the reference.
// "boar" in the output rows is then --system and "ref" is --against.
const against = get("--against");
const refName = against ?? get("--ref", "claude-code__opus");
const subset = get("--subset", "s32");
const seed = Number(get("--seed", "7"));
const judgeModel = get("--judge-model", "opus");
const gapMs = Number(get("--gap-ms", "5000"));
if (!system) throw new Error("--system <runs file name without .jsonl> is required");

const readJsonl = (p) => readFileSync(p, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l));
const questions = Object.fromEntries(readJsonl(join(EVAL_DIR, "dataset", `questions.${dataset}.jsonl`)).map((q) => [q.id, q]));
const boar = Object.fromEntries(readJsonl(join(EVAL_DIR, "results", "runs", dataset, `${system}.jsonl`)).map((r) => [r.queryId, r]));
const ref = against
  ? Object.fromEntries(readJsonl(join(EVAL_DIR, "results", "runs", dataset, `${against}.jsonl`)).filter((r) => r.outcome === "success").map((r) => [r.queryId, r]))
  : Object.fromEntries(readJsonl(join(EVAL_DIR, "references", dataset, `${refName}.jsonl`)).filter((r) => r.ok).map((r) => [r.id, r]));

let ids = subset === "all" ? Object.keys(questions) : JSON.parse(readFileSync(join(EVAL_DIR, "dataset", `subset.${subset}.${dataset}.json`), "utf8")).ids;
// --categories a,b: judge only these categories (e.g. v2 food items are scored objectively, not by the judge).
const categories = get("--categories")?.split(",");
if (categories) ids = ids.filter((id) => categories.includes(questions[id]?.category));
const missing = ids.filter((id) => !boar[id] || !ref[id]);
if (missing.length) console.warn(`skipping ${missing.length} ids without both answers: ${missing.join(",")}`);
ids = ids.filter((id) => boar[id] && ref[id]);

const out = join(EVAL_DIR, "results", "judgments", dataset, `${system}__vs__${refName}.jsonl`);
const SPEND = join(EVAL_DIR, "results", "spend.jsonl");
mkdirSync(dirname(out), { recursive: true });
const done = new Set(existsSync(out) ? readJsonl(out).filter((r) => r.ok).map((r) => `${r.queryId}|${r.order}`) : []);
const cliVersion = execFileSync("claude", ["--version"], { env: cleanEnv(), encoding: "utf8" }).trim();
const first = firstOrders(ids, seed);

let calls = 0;
for (const id of ids) {
  const q = questions[id];
  const orders = first[id] === "boarA" ? ["boarA", "boarB"] : ["boarB", "boarA"];
  for (const order of orders) {
    if (done.has(`${id}|${order}`)) continue;
    if (calls++) await sleep(gapMs);
    const boarIsA = order === "boarA";
    const [answerA, answerB] = boarIsA ? [blind(boar[id].answer), blind(ref[id].answer)] : [blind(ref[id].answer), blind(boar[id].answer)];
    const r = await runClaude({
      prompt: judgePrompt({ question: q.query, notes: q.notes, answerA, answerB }),
      systemPrompt: JUDGE_SYSTEM,
      model: judgeModel,
      tools: [],
      jsonSchema: VERDICT_SCHEMA,
      timeoutMs: 180_000,
    });
    logSpend(SPEND, `judge:${dataset}:${subset}`, r);
    if (isRateLimited(r)) {
      console.error(`RATE LIMITED at ${id}/${order}: ${r.error ?? r.stderr}. Stopping; rerun resumes from here.`);
      process.exit(3);
    }
    const verdict = r.json?.structured_output;
    const ok = r.ok && verdict != null;
    const row = {
      queryId: id,
      category: q.category,
      order,
      ok,
      error: ok ? undefined : r.error ?? "no structured_output",
      verdict,
      mapped: ok ? mapVerdict(verdict, boarIsA) : undefined,
      system,
      reference: refName,
      judgeModel: r.json ? Object.keys(r.json.modelUsage ?? {})[0] : undefined,
      judgeVersion: JUDGE_VERSION,
      seed,
      subset,
      durationMs: r.json?.duration_ms,
      listPriceEquivalentUsd: r.json?.total_cost_usd,
      cliVersion,
      createdAt: new Date().toISOString(),
    };
    appendFileSync(out, JSON.stringify(row) + "\n");
    console.log(`${id} ${order} ${ok ? `${verdict.winner}->${row.mapped.winner}` : "FAIL"} ${((row.durationMs ?? 0) / 1000).toFixed(1)}s`);
  }
}
console.log(`wrote ${out}; cumulative list-price equivalent ${totalSpend(SPEND).toFixed(2)} USD`);
