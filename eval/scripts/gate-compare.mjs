#!/usr/bin/env node
// Item-by-item comparison of two gate runs (results/gates/<label>/runs): passing seeds per configuration and item,
// control vs candidate. No model calls. Writes reports/gate-compare-<control>-vs-<candidate>.md.
// Usage (from eval/): node scripts/gate-compare.mjs <control-label> <candidate-label> [--note "text"] [--no-model id,id]
// --no-model: items whose answer must never call the model (e.g. health answers quoted from the source); any call fails.
import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { PQ_QUERY_ID, checkQuantumAnswer } from "./lib/pq-check.mjs";
import { checkFirstAid, checkEarthquakeSource } from "./lib/firstaid-check.mjs";
import { checkSuggestion } from "./lib/suggestion-check.mjs";
import { checkPlaces } from "./lib/places-check.mjs";
import { checkCurrentEvents } from "./lib/current-events-check.mjs";
import { checkKnowledgeTopic } from "./lib/knowledge-topic-check.mjs";
import { checkPtTopic } from "./lib/pt-topic-check.mjs";
import { normalizeRow } from "./lib/row-normalize.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const a = process.argv.slice(2);
const [control, candidate] = a.filter((x, i) => !x.startsWith("--") && !["--note", "--no-model"].includes(a[i - 1]));
const noModel = a.includes("--no-model") ? a[a.indexOf("--no-model") + 1].split(",") : [];
const note = a.includes("--note") ? a[a.indexOf("--note") + 1] : "";
if (!control || !candidate) throw new Error("usage: gate-compare.mjs <control-label> <candidate-label>");

/** config -> item -> { pass, n, failures[] } */
function load(label) {
  const dir = join(EVAL_DIR, "results", "gates", label, "runs");
  const meta = JSON.parse(readFileSync(join(EVAL_DIR, "results", "gates", label, "meta.json"), "utf8"));
  const out = {};
  for (const f of readdirSync(dir).filter((f) => f.endsWith(".jsonl"))) {
    const cfg = f.replace(/\.jsonl$/, "").replace(/__seed\d+$/, "");
    for (const r of readFileSync(join(dir, f), "utf8").trim().split("\n").filter(Boolean).map((l) => normalizeRow(JSON.parse(l)))) {
      const res = r.queryId === PQ_QUERY_ID ? checkQuantumAnswer({ answer: r.answer ?? "", retrievedTitles: r.retrievedTitles })
        : r.queryId?.startsWith("sug-") ? checkSuggestion(r) : r.queryId?.startsWith("places-") ? checkPlaces(r) : r.queryId?.startsWith("ce-") ? checkCurrentEvents(r) : r.queryId?.startsWith("kt-") ? checkKnowledgeTopic(r) : r.queryId?.startsWith("ptt-") ? checkPtTopic(r) : checkFirstAid(r.queryId, r);
      const eq = checkEarthquakeSource(r);
      if (eq) {
        const ce = ((out[cfg] ??= {})[`${r.queryId}·gov-source`] ??= { pass: 0, n: 0, failures: [], modelCalled: 0, refusals: 0, target: true });
        ce.n++;
        if (eq.pass) ce.pass++;
        else ce.failures.push(`seed ${r.seed}: ${eq.failures[0]}`);
      }
      if (!res) continue;
      const cell = ((out[cfg] ??= {})[r.queryId] ??= { pass: 0, n: 0, failures: [], modelCalled: 0, refusals: 0, target: !!res.target });
      if (res.warnings?.some((w) => w.startsWith("honest refusal"))) cell.refusals++;
      cell.n++;
      if (res.pass) cell.pass++;
      else cell.failures.push(`seed ${r.seed}: ${res.failures[0]}`);
      if (r.modelCalled !== false) cell.modelCalled++;
    }
  }
  return { meta, cells: out };
}

const A = load(control), B = load(candidate);
const configs = [...new Set([...Object.keys(A.cells), ...Object.keys(B.cells)])].sort();
const items = [...new Set(configs.flatMap((c) => [...Object.keys(A.cells[c] ?? {}), ...Object.keys(B.cells[c] ?? {})]))].sort();
const fmt = (c) => (c ? `${c.pass}/${c.n}` : "–");
const verdict = (c) => (!c ? "MISSING" : c.pass === c.n ? "PASS" : "FAIL");

let fails = 0, missing = 0;
const L = [`# Gate: ${candidate} vs control ${control}`, ""];
const rows = [];
for (const cfg of configs) for (const it of items) {
  const ca = A.cells[cfg]?.[it], cb = B.cells[cfg]?.[it];
  if (!ca && !cb) continue;
  let v = verdict(cb);
  if (v === "FAIL" && cb?.target) v = "TARGET miss";
  if (v === "PASS" && noModel.includes(it) && cb.modelCalled > 0) v = "FAIL (model called)";
  if (v.startsWith("FAIL")) fails++;
  if (v === "MISSING") missing++;
  rows.push(`| ${cfg} | ${it} | ${fmt(ca)} | ${fmt(cb)} | ${v === "PASS" ? "PASS" : `**${v}**`} | ${cb ? `${cb.modelCalled}/${cb.n}` : "–"} | ${cb?.refusals ? `${cb.refusals}/${cb.n}` : "–"} | ${(cb?.failures ?? []).slice(0, 2).join("<br>").replace(/\|/g, "\\|") || "–"} |`);
}
L.push(`TL;DR: candidate ${fails || missing ? `**FAIL** (${fails} item-configurations fail${missing ? `, ${missing} missing` : ""})` : "**PASS**"}. A cell passes only if every seed passes. Pipeline \`${B.meta.pipeline}\`, corpus \`${B.meta.corpus}\`, seeds ${B.meta.seeds}, models ${B.meta.models}.`, "");
if (note) L.push(`> ${note}`, "");
L.push(`- Control: \`${A.meta.ref}\` @ ${A.meta.sha} (${A.meta.at})`, `- Candidate: \`${B.meta.ref}\` @ ${B.meta.sha} (${B.meta.at})`, `- Packs (pinned sha256): ${Object.entries(B.meta.packs).map(([k, v]) => `${k} ${v.slice(0, 8)}…`).join(", ")}`, "");
L.push("| Configuration | Item | Control (passing seeds) | Candidate | Candidate verdict | Model called | Honest refusals | First failures |", "|---|---|---|---|---|---|---|---|", ...rows, "");
// Model calls per health item (the app should quote the source instead of generating).
const health = items.filter((i) => i.startsWith("safety-"));
L.push("## Model calls per health item (candidate, calls / answers)", "", "| Configuration | " + health.join(" | ") + " |", "|---|" + health.map(() => "---").join("|") + "|");
for (const cfg of configs) if (B.cells[cfg]) L.push(`| ${cfg} | ` + health.map((i) => { const c = B.cells[cfg][i]; return c ? `${c.modelCalled}/${c.n}` : "–"; }).join(" | ") + " |");
L.push("", noModel.length ? `Zero-model criterion on: ${noModel.join(", ")}.` : "", "");
// Reason codes the engine reports on answers (Tusk dc41215): an uncited knowledge answer gets the "not from an offline
// source" preface on the 4B and is declined on the Compacto. Counted over the gate runs and the s32 answers.
const WATCH = ["grounding:uncited-preface", "grounding:uncited-declined-compact", "grounding:uncited-on-topic", "grounding:uncited-warning"];
function codeCounts(label) {
  const base = join(EVAL_DIR, "results", "gates", label);
  const out = {};
  for (const sub of ["runs", "s32"]) {
    const dir = join(base, sub);
    if (!existsSync(dir)) continue;
    for (const f of readdirSync(dir).filter((f) => f.endsWith(".jsonl"))) {
      // Suggestions always run on the 4B (gate-mini.sh SUG_MODEL).
      const model = /1\.5b/.test(f) ? "1.5B" : /4b/.test(f) || f.startsWith("suggestions") ? "4B" : "other";
      const key = `${model} · ${sub === "s32" ? "s32" : "gate items"}`;
      for (const r of readFileSync(join(dir, f), "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l))) {
        const c = (out[key] ??= { n: 0, ...Object.fromEntries(WATCH.map((w) => [w, 0])) });
        c.n++;
        for (const w of WATCH) if ((r.reasonCodes ?? []).includes(w)) c[w]++;
      }
    }
  }
  return out;
}
const cc = codeCounts(control), kc = codeCounts(candidate);
L.push("## Uncited-answer safety net (reason codes, answers with the code / answers)", "", "| Model · set | " + WATCH.map((w) => `${w} (control → candidate)`).join(" | ") + " |", "|---|" + WATCH.map(() => "---").join("|") + "|");
for (const key of [...new Set([...Object.keys(cc), ...Object.keys(kc)])].sort()) {
  L.push(`| ${key} | ` + WATCH.map((w) => `${cc[key] ? `${cc[key][w]}/${cc[key].n}` : "–"} → ${kc[key] ? `${kc[key][w]}/${kc[key].n}` : "–"}`).join(" | ") + " |");
}
L.push("");
L.push(`Full answers: \`reports/regression-gate-${control}.md\`, \`reports/regression-gate-${candidate}.md\`. Regenerate with \`node eval/scripts/gate-compare.mjs ${control} ${candidate}\`.`, "");
writeFileSync(join(EVAL_DIR, "reports", `gate-compare-${control}-vs-${candidate}.md`), L.join("\n"));
console.log(L.join("\n"));
process.exit(fails || missing ? 1 : 0);
