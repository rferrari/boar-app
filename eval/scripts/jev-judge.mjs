#!/usr/bin/env node
// Second judge from another model family: typesafe-ai/jev (calibrated classifier) via the Vercel AI Gateway
// /v1/evaluate endpoint. Build/eval time only, never in the app. Same blinding, same rubric and both A/B orders as
// the Claude judge (judge.mjs), so the two can be compared item by item.
// Key: env VERCEL_AI_GATEWAY only (never written to a file or a log). Responses cached on disk by request hash.
// Cost logged per request to the team ledger; hard stop at --max-usd (default 0.50).
// Usage (from eval/): node scripts/jev-judge.mjs --system <run> [--against <run>] [--dataset v1] [--subset s32]
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { RUBRIC, blind, mapVerdict } from "./lib/judge-core.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const ENDPOINT = "https://ai-gateway.vercel.sh/v1/evaluate";
const LEDGER = "/Users/r4to/Script/boar/shared-data/jev-spend.jsonl";
const CACHE = join(EVAL_DIR, ".cache", "jev");
const JUDGE_VERSION = "jev-judge.v1";

const a = process.argv.slice(2);
const get = (k, d) => (a.includes(k) ? a[a.indexOf(k) + 1] : d);
const dataset = get("--dataset", "v1");
const system = get("--system");
const against = get("--against");
const refName = against ?? get("--ref", "claude-code__opus");
const subset = get("--subset", "s32");
const maxUsd = Number(get("--max-usd", "0.50"));
if (!system) throw new Error("--system is required");
const KEY = process.env.VERCEL_AI_GATEWAY;
if (!KEY) throw new Error("VERCEL_AI_GATEWAY is not set");

const readJsonl = (p) => readFileSync(p, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l));
const questions = Object.fromEntries(readJsonl(join(EVAL_DIR, "dataset", `questions.${dataset}.jsonl`)).map((q) => [q.id, q]));
const boar = Object.fromEntries(readJsonl(join(EVAL_DIR, "results", "runs", dataset, `${system}.jsonl`)).map((r) => [r.queryId, r]));
const ref = against
  ? Object.fromEntries(readJsonl(join(EVAL_DIR, "results", "runs", dataset, `${against}.jsonl`)).filter((r) => r.outcome === "success").map((r) => [r.queryId, r]))
  : Object.fromEntries(readJsonl(join(EVAL_DIR, "references", dataset, `${refName}.jsonl`)).filter((r) => r.ok).map((r) => [r.id, r]));
let ids = subset === "all" ? Object.keys(questions) : JSON.parse(readFileSync(join(EVAL_DIR, "dataset", `subset.${subset}.${dataset}.json`), "utf8")).ids;
ids = ids.filter((id) => boar[id] && ref[id]);

// The same rubric text as the Claude judge, as 5 ordered levels per dimension (Jev scores are 0..4 -> 1..5).
const LEVELS = {
  correctness: ["Mostly wrong, or an uncorrected false premise", "A wrong claim that matters to the question", "Minor inaccuracies only", "Accurate with a trivial slip", "Fully accurate"],
  completeness: ["Misses the question entirely", "Covers a small part of the key points", "Covers about half of the key points", "Covers most key points", "Covers every key point the question needs"],
  usefulness: ["Useless or unsafe for the person asking", "Barely helps", "Somewhat helps", "Helps well", "Exactly what the person (e.g. a traveler without internet) needs; safe"],
};
function questionsFor() {
  const q = {
    winner: {
      type: "choice",
      instructions: "Which answer is better for `question`, judging substance only: correctness first, then covering the key points in `notes`, then usefulness. Do not reward length, formatting or confidence; a short correct answer can win or tie.",
      criteria: { A: "`answerA` is clearly better", B: "`answerB` is clearly better", tie: "Both are of practically equal quality" },
    },
  };
  for (const [dim] of RUBRIC) for (const side of ["A", "B"]) {
    q[`${dim}_${side}`] = { type: "score", instructions: `Rate the ${dim} of \`answer${side}\` for \`question\` (see \`notes\` for what a good answer contains).`, criteria: LEVELS[dim] };
  }
  return q;
}

async function evaluate(body) {
  const hash = createHash("sha256").update(JSON.stringify(body)).digest("hex");
  const file = join(CACHE, `${hash}.json`);
  if (existsSync(file)) return { ...JSON.parse(readFileSync(file, "utf8")), cached: true };
  for (let attempt = 1; attempt <= 5; attempt++) {
    const res = await fetch(ENDPOINT, { method: "POST", headers: { authorization: `Bearer ${KEY}`, "content-type": "application/json" }, body: JSON.stringify(body) });
    if (res.ok) {
      const j = await res.json();
      mkdirSync(CACHE, { recursive: true });
      writeFileSync(file, JSON.stringify(j));
      return { ...j, cached: false };
    }
    if (res.status === 503 || res.status === 429) { await new Promise((r) => setTimeout(r, Math.min(8000, 1000 * 2 ** attempt))); continue; }
    throw new Error(`jev ${res.status}: ${(await res.text()).slice(0, 200)}`);
  }
  throw new Error("jev: giving up after 5 attempts");
}

const out = join(EVAL_DIR, "results", "judgments-jev", dataset, `${system}__vs__${refName}.jsonl`);
mkdirSync(dirname(out), { recursive: true });
const done = new Set(existsSync(out) ? readJsonl(out).map((r) => `${r.queryId}|${r.order}`) : []);
let spent = 0;
for (const id of ids) {
  for (const order of ["boarA", "boarB"]) {
    if (done.has(`${id}|${order}`)) continue;
    if (spent >= maxUsd) { console.error(`budget cap ${maxUsd} USD reached; stopping`); process.exit(3); }
    const boarIsA = order === "boarA";
    const [answerA, answerB] = boarIsA ? [blind(boar[id].answer), blind(ref[id].answer)] : [blind(ref[id].answer), blind(boar[id].answer)];
    const body = { model: "typesafe-ai/jev", state: { question: questions[id].query, notes: questions[id].notes ?? "", answerA, answerB }, questions: questionsFor() };
    const j = await evaluate(body);
    const cost = j.cached ? 0 : Number(j.providerMetadata?.gateway?.marketCost ?? 0);
    spent += cost;
    if (!j.cached) appendFileSync(LEDGER, JSON.stringify({ ts: new Date().toISOString(), who: "Sextant", what: `jev judge ${dataset}/${subset} ${system} vs ${refName} ${id} ${order}`, marketCost: cost }) + "\n");
    const s = (dim, side) => Math.round(j.answers[`${dim}_${side}`].score) + 1;
    const verdict = {
      winner: j.answers.winner.choice,
      scores_A: Object.fromEntries(RUBRIC.map(([d]) => [d, s(d, "A")])),
      scores_B: Object.fromEntries(RUBRIC.map(([d]) => [d, s(d, "B")])),
    };
    appendFileSync(out, JSON.stringify({
      queryId: id, category: questions[id].category, order, ok: true, verdict,
      winnerProbabilities: j.answers.winner.probabilities, winnerConfidence: j.answers.winner.confidence,
      mapped: mapVerdict(verdict, boarIsA), system, reference: refName, judgeModel: "typesafe-ai/jev", judgeVersion: JUDGE_VERSION,
      inputTokens: j.usage?.inputTokens, marketCost: cost, cached: j.cached, createdAt: new Date().toISOString(),
    }) + "\n");
    console.log(`${id} ${order} ${verdict.winner}->${mapVerdict(verdict, boarIsA).winner} conf=${j.answers.winner.confidence?.toFixed(2)} $${cost.toFixed(6)}`);
  }
}
console.log(`wrote ${out}; this run ${spent.toFixed(6)} USD`);
