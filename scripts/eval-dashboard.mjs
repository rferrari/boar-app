#!/usr/bin/env node
// A local dashboard of every BOAR evaluation run: one self-contained HTML page, no server, no network.
//
//   node scripts/eval-dashboard.mjs              # rebuild ~/Desktop/boar-evals/index.html from what is on disk
//   node scripts/eval-dashboard.mjs --pull       # first copy new runs off every phone on adb (release, debug, Labs ids)
//   node scripts/eval-dashboard.mjs --out DIR    # another folder (default ~/Desktop/boar-evals)
//
// Reads every *.jsonl under DIR/runs and docs/evidence (one row per answer, the runner's own format; see
// AGENTS.md "Benchmarking models on a connected phone"). Rows are deduped by runId + configId + queryId.
// The questions' text and grading notes come from the app's eval set. Nothing is graded here: the page shows what
// the phone measured (outcome, expected source found, the number check, speed, times) and the notes to judge by.
import { readFileSync, writeFileSync, mkdirSync, readdirSync, existsSync, statSync, copyFileSync } from "node:fs";
import { join, dirname, basename } from "node:path";
import { homedir } from "node:os";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const repo = join(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const out = args.includes("--out") ? args[args.indexOf("--out") + 1] : join(homedir(), "Desktop", "boar-evals");
const runsDir = join(out, "runs");
mkdirSync(runsDir, { recursive: true });

function adb(serial, ...a) {
  return execFileSync("adb", serial ? ["-s", serial, ...a] : a, { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
}

if (args.includes("--pull")) {
  let devices = [];
  try { devices = adb(null, "devices").split("\n").slice(1).map((l) => l.trim().split(/\s+/)).filter((p) => p[1] === "device").map((p) => p[0]); }
  catch { console.error("adb not found: skipping --pull"); }
  for (const serial of devices) {
    for (const pkg of ["team.sopa.aoair", "team.sopa.aoair.native", "team.sopa.aoair.labs"]) {
      const dir = `/sdcard/Android/data/${pkg}/files/eval-out`;
      let names = [];
      try { names = adb(serial, "shell", "ls", dir).split(/\s+/).filter((n) => n.endsWith(".jsonl")); } catch { continue; }
      for (const n of names) {
        const dest = join(runsDir, `${serial}-${n}`);
        if (existsSync(dest)) continue;
        try { adb(serial, "pull", `${dir}/${n}`, dest); console.log(`pulled ${serial} ${pkg} ${n}`); } catch { console.error(`could not pull ${serial} ${n}`); }
      }
    }
  }
}

function jsonlFiles(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n);
    return statSync(p).isDirectory() ? jsonlFiles(p) : n.endsWith(".jsonl") ? [p] : [];
  });
}

/**
 * Names for the team's phones by adb serial, so the page says "F3" and "X6": read from scripts/phones.local.json
 * ({"<serial>": "<name>"}, gitignored) when it exists; otherwise each phone is labelled by its serial.
 */
const PHONES_FILE = join(repo, "scripts", "phones.local.json");
const PHONES = existsSync(PHONES_FILE) ? JSON.parse(readFileSync(PHONES_FILE, "utf8")) : {};

const files = [...jsonlFiles(runsDir), ...jsonlFiles(join(repo, "docs", "evidence"))];
const seen = new Map();
for (const f of files) {
  const serial = basename(f).match(/^([A-Za-z0-9]+)-eval-/)?.[1];
  const device = serial ? PHONES[serial] ?? serial : f.includes("docs/evidence") ? "evidence (docs)" : "";
  for (const line of readFileSync(f, "utf8").split("\n")) {
    if (!line.trim().startsWith("{")) continue;
    let r;
    try { r = JSON.parse(line); } catch { continue; }
    if (!r.runId || !r.queryId) continue;
    const key = `${r.runId}|${r.configId}|${r.queryId}`;
    if (!seen.has(key)) seen.set(key, { ...r, _device: device, _file: basename(f) });
  }
}
const rows = [...seen.values()].sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0));

const evalSet = JSON.parse(readFileSync(join(repo, "android-native/app/src/main/assets/boar-data/eval-set.json"), "utf8"));
const questions = Object.fromEntries([...(evalSet.evalSet ?? []), ...(evalSet.extraSets ?? [])].map((q) => [q.id, q]));

const keep = ["runId", "configId", "configLabel", "modelId", "queryId", "category", "query", "outcome", "errorMessage", "expectedKbHit",
  "expectedKbTitles", "retrievedTitles", "unsupportedNumbers", "tokPerSec", "ttftMs", "totalLatencyMs", "tokensGenerated", "modelLoadMs",
  "retrievalMs", "promptTokens", "cachedTokens", "peakRssBytes", "path", "personalityId", "style", "tool", "appVersion", "createdAt", "answer",
  "stagedSourcesMs", "stagedIntroFirstMs", "stagedIntroDoneMs", "stagedDetailsFirstMs", "stagedKeyFacts", "_device"];
const data = rows.map((r) => Object.fromEntries(keep.filter((k) => r[k] !== undefined && r[k] !== null).map((k) => [k, r[k]])));
const notes = Object.fromEntries(Object.values(questions).map((q) => [q.id, { query: q.query, category: q.category, notes: q.gradingNotes ?? "" }]));

const html = readFileSync(join(repo, "scripts", "eval-dashboard.template.html"), "utf8")
  // Function replacements: an answer containing "$&" or "$'" must not be read as a replacement pattern (validator).
  .replace("/*DATA*/null", () => JSON.stringify(data).replace(/</g, "\\u003c"))
  .replace("/*QUESTIONS*/null", () => JSON.stringify(notes).replace(/</g, "\\u003c"))
  .replace("/*BUILT*/null", () => JSON.stringify(new Date().toISOString()));
writeFileSync(join(out, "index.html"), html);
console.log(`${data.length} answers from ${new Set(data.map((r) => r.runId)).size} runs (${files.length} files) -> ${join(out, "index.html")}`);
