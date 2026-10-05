#!/usr/bin/env node
// Builds eval/reports/<name>.md + quality-vs-latency SVG from runs, references, judgments, calibration and spend.
// No model calls: it only reads result files, so the report is reproducible from the committed data.
// Usage (from eval/): node scripts/report.mjs [--dataset v1] [--subset s32|all] [--name baseline-v1] [--systems a,b]
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { RUBRIC, cohenKappa, combineOrders, percentile, summarize } from "./lib/judge-core.mjs";
import { totalSpend } from "./lib/claude-cli.mjs";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const a = process.argv.slice(2);
const get = (k, d) => (a.includes(k) ? a[a.indexOf(k) + 1] : d);
const dataset = get("--dataset", "v1");
const subset = get("--subset", "s32");
const refName = get("--ref", "claude-code__opus");
const name = get("--name", `baseline-${dataset}`);

const readJsonl = (p) => (existsSync(p) ? readFileSync(p, "utf8").trim().split("\n").filter(Boolean).map((l) => JSON.parse(l)) : []);
const median = (xs) => percentile([...xs].sort((x, y) => x - y), 0.5);
const p90 = (xs) => percentile([...xs].sort((x, y) => x - y), 0.9);
const fmt = (x, d = 2) => (Number.isFinite(x) ? x.toFixed(d) : "n/a");
const pct = (x) => (Number.isFinite(x) ? `${(x * 100).toFixed(0)}%` : "n/a");

const questionCount = readJsonl(join(EVAL_DIR, "dataset", `questions.${dataset}.jsonl`)).length;
const refs = readJsonl(join(EVAL_DIR, "references", dataset, `${refName}.jsonl`)).filter((r) => r.ok);
const runsDir = join(EVAL_DIR, "results", "runs", dataset);
// --systems a,b: report only these runs (the runs dir also holds A/B variants and exploratory models).
const onlySystems = get("--systems")?.split(",");
const systems = readdirSync(runsDir).filter((f) => f.endsWith(".jsonl") && !/\.(pre-|invalid)/.test(f)).map((f) => f.replace(/\.jsonl$/, ""))
  .filter((s) => !onlySystems || onlySystems.includes(s));
const subsetIds = subset === "all" ? null : new Set(JSON.parse(readFileSync(join(EVAL_DIR, "dataset", `subset.${subset}.${dataset}.json`), "utf8")).ids);

/** Groups judgments per query and combines the two orders; queries with a single order are left out. */
function pairsFor(system, dir = "judgments") {
  const js = readJsonl(join(EVAL_DIR, "results", dir, dataset, `${system}__vs__${refName}.jsonl`)).filter((j) => j.ok && (!subsetIds || subsetIds.has(j.queryId)));
  const byQ = {};
  for (const j of js) (byQ[j.queryId] ??= {})[j.order] = j;
  return Object.entries(byQ)
    .filter(([, o]) => o.boarA && o.boarB)
    .map(([queryId, o]) => ({ queryId, category: o.boarA.category, ...combineOrders(o.boarA.mapped, o.boarB.mapped) }));
}

const rows = systems.map((system) => {
  const runs = readJsonl(join(runsDir, `${system}.jsonl`));
  const inScope = runs.filter((r) => !subsetIds || subsetIds.has(r.queryId));
  const ok = inScope.filter((r) => r.outcome === "success");
  const pairs = pairsFor(system);
  const jevPairs = pairsFor(system, "judgments-jev");
  return {
    system,
    label: (runs[0]?.configLabel ?? system) + (runs[0]?.packs?.length ? ` + ${runs[0].packs.map((p) => p.id).join(", ")} pack` : ""),
    hardware: runs[0]?.hardware,
    n: inScope.length,
    successRate: ok.length / Math.max(1, inScope.length),
    medianTotalS: median(ok.map((r) => r.totalLatencyMs)) / 1000,
    p90TotalS: p90(ok.map((r) => r.totalLatencyMs)) / 1000,
    medianTtftS: median(ok.map((r) => r.ttftMs)) / 1000,
    medianTokPerSec: median(ok.map((r) => r.tokPerSec).filter(Number.isFinite)),
    kbHit: inScope.filter((r) => r.expectedKbHit === true).length / Math.max(1, inScope.filter((r) => r.expectedKbHit !== null).length),
    maxLoad: Math.max(...inScope.map((r) => r.loadAvg1m ?? 0)),
    pairs,
    judged: pairs.length ? summarize(pairs, { seed: 11 }) : null,
    correctRate: pairs.length ? pairs.filter((p) => p.boar.correctness >= 4).length / pairs.length : NaN,
    jev: jevPairs.length ? summarize(jevPairs, { seed: 11 }) : null,
    jevN: jevPairs.length,
    jevCorrectRate: jevPairs.length ? jevPairs.filter((p) => p.boar.correctness >= 4).length / jevPairs.length : NaN,
  };
});

// Same model in several runs (e.g. gate control vs candidate): tell them apart by the run's last name part.
const dupLabels = new Set(rows.map((r) => r.label).filter((l, i, all) => all.indexOf(l) !== i));
for (const r of rows) if (dupLabels.has(r.label)) r.label += ` [${r.system.split("__").pop()}]`;
const allPairs = rows.flatMap((r) => r.pairs);
const refCorrect = allPairs.length ? allPairs.filter((p) => p.ref.correctness >= 4).length / allPairs.length : NaN;
const refInScope = refs.filter((r) => !subsetIds || subsetIds.has(r.id));
const refMedianS = median(refInScope.map((r) => r.wallMs)) / 1000;

// ---------------------------------------------------------------- calibration
const calib = readJsonl(join(EVAL_DIR, "results", "calibration", dataset, "labels.jsonl"));
let calibText = "Not run yet.";
if (calib.length) {
  const judged = Object.fromEntries(rows.flatMap((r) => r.pairs.map((p) => [`${r.system}|${p.queryId}`, p.winner])));
  const matched = calib.filter((c) => judged[`${c.system}|${c.queryId}`]);
  const human = matched.map((c) => c.winner);
  const judge = matched.map((c) => judged[`${c.system}|${c.queryId}`]);
  const agree = human.filter((h, i) => h === judge[i]).length / Math.max(1, matched.length);
  const labels = ["boar", "tie", "ref"];
  const cm = labels.map((h) => labels.map((j) => matched.filter((_, i) => human[i] === h && judge[i] === j).length));
  calibText = `${matched.length} pairs labeled by hand (blind, before reading the judge's verdict) vs the combined judge verdict: ` +
    `agreement ${pct(agree)}, Cohen's kappa ${fmt(cohenKappa(human, judge))} (kappa is unstable when almost every label is the same class). Labeler: ${calib[0].labeler}.\n\n` +
    "| hand \\ judge | boar | tie | ref |\n|---|---|---|---|\n" + labels.map((h, i) => `| ${h} | ${cm[i].join(" | ")} |`).join("\n") +
    "\n\nRead: disagreements are hand 'tie' vs judge 'ref' on answers that are correct but less complete. On those the judge still scored BOAR correctness 4-5 and docked completeness, so hand and judge disagree on the tie threshold, not on facts. That is why the report also shows the correct-answer rate.";
}

// ---------------------------------------------------------------- chart (SVG, no deps)
function chart(points, refS) {
  const W = 640, H = 380, L = 64, R = 24, T = 24, B = 52;
  const xs = [...points.map((p) => p.x), refS].filter(Number.isFinite);
  const xMax = Math.max(1, ...xs) * 1.15;
  const X = (v) => L + (v / xMax) * (W - L - R);
  const Y = (v) => T + (1 - v / 1.1) * (H - T - B);
  const ticksX = Array.from({ length: 6 }, (_, i) => (xMax / 5) * i);
  const ticksY = [0, 0.25, 0.5, 0.75, 1];
  const g = [];
  g.push(`<rect width="${W}" height="${H}" fill="#fff"/>`);
  for (const t of ticksY) g.push(`<line x1="${L}" x2="${W - R}" y1="${Y(t)}" y2="${Y(t)}" stroke="#e5e5e5"/><text x="${L - 8}" y="${Y(t) + 4}" font-size="11" text-anchor="end" fill="#555">${t.toFixed(2)}</text>`);
  for (const t of ticksX) g.push(`<text x="${X(t)}" y="${H - B + 18}" font-size="11" text-anchor="middle" fill="#555">${t.toFixed(0)}</text>`);
  g.push(`<text x="${(L + W - R) / 2}" y="${H - 12}" font-size="12" text-anchor="middle" fill="#222">median seconds per answer (lower is better)</text>`);
  g.push(`<text transform="translate(16 ${(T + H - B) / 2}) rotate(-90)" font-size="12" text-anchor="middle" fill="#222">quality ratio vs reference (rubric mean)</text>`);
  if (Number.isFinite(refS)) g.push(`<circle cx="${X(refS)}" cy="${Y(1)}" r="6" fill="#111"/><text x="${X(refS) - 8}" y="${Y(1) - 10}" font-size="11" text-anchor="end" fill="#111">Reference: Opus + web search</text>`);
  const colors = ["#2563eb", "#d97706", "#059669", "#db2777", "#7c3aed"];
  points.forEach((p, i) => {
    const c = colors[i % colors.length];
    if (p.ci) g.push(`<line x1="${X(p.x)}" x2="${X(p.x)}" y1="${Y(p.ci[0])}" y2="${Y(p.ci[1])}" stroke="${c}" stroke-width="2"/>`);
    g.push(`<circle cx="${X(p.x)}" cy="${Y(p.y)}" r="6" fill="${c}"/><text x="${X(p.x) + 10}" y="${Y(p.y) + 4}" font-size="11" fill="${c}">${p.label}</text>`);
  });
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" font-family="system-ui, sans-serif">${g.join("")}</svg>\n`;
}

const points = rows.filter((r) => r.judged).map((r) => ({ x: r.medianTotalS, y: r.judged.qualityRatio, ci: r.judged.qualityRatioCI, label: r.label }));
const outDir = join(EVAL_DIR, "reports");
mkdirSync(outDir, { recursive: true });
writeFileSync(join(outDir, `${name}.quality-latency.svg`), chart(points, refMedianS));

// ---------------------------------------------------------------- markdown
const lines = [];
lines.push(`# BOAR eval: ${name}`, "");
lines.push(`TL;DR: quality of on-device answers relative to a frontier model with web search, and seconds per answer. Dataset \`${dataset}\`, scope \`${subset}\` (${subsetIds ? subsetIds.size : "all"} questions). Regenerate with \`node eval/scripts/report.mjs ${a.join(" ")}\` (all official reports: \`npm --prefix eval run report:official\`).`, "");
// Headline: every judged system in one sentence each, both judges, so the main number is never typed by hand.
const headline = rows.filter((r) => r.judged).sort((x, y) => y.judged.qualityRatio - x.judged.qualityRatio).map((r) =>
  `- **${r.label}** reaches **${pct(r.judged.qualityRatio)}** of the reference's rubric score (95% CI ${pct(r.judged.qualityRatioCI[0])}–${pct(r.judged.qualityRatioCI[1])}, Claude judge, ${r.pairs.length} questions)` +
  (r.jev ? ` / **${pct(r.jev.qualityRatio)}** (${pct(r.jev.qualityRatioCI[0])}–${pct(r.jev.qualityRatioCI[1])}, Jev judge, ${r.jevN})` : "") +
  ` in **${fmt(r.medianTotalS, 1)} s** median per answer, vs ${fmt(refMedianS, 1)} s for the reference. Correct answers: ${pct(r.correctRate)} (reference ${pct(refCorrect)}).`);
if (headline.length) lines.push("## Headline", "", ...headline, "", "The reference wins almost every head-to-head pair; the ratio measures how much of its quality BOAR keeps, offline and on device-class hardware.", "");
lines.push(`![quality vs latency](./${name}.quality-latency.svg)`, "");
lines.push("## Results", "");
lines.push("| System | Judged | Quality ratio (95% CI) | Correct answers (correctness ≥ 4) | Win / tie / loss vs ref | Win score (95% CI) | Position-consistent | Median s (p90) | TTFT s | tok/s | KB hit | Success |");
lines.push("|---|---|---|---|---|---|---|---|---|---|---|---|");
for (const r of rows) {
  const j = r.judged;
  lines.push(`| ${r.label} | ${r.pairs.length} | ${j ? `${fmt(j.qualityRatio)} (${fmt(j.qualityRatioCI[0])}–${fmt(j.qualityRatioCI[1])})` : "not judged"} | ${j ? pct(r.correctRate) : "–"} | ${j ? `${pct(j.boarWin)} / ${pct(j.tie)} / ${pct(j.refWin)}` : "–"} | ${j ? `${fmt(j.winScore)} (${fmt(j.winScoreCI[0])}–${fmt(j.winScoreCI[1])})` : "–"} | ${j ? pct(j.positionConsistency) : "–"} | ${fmt(r.medianTotalS, 1)} (${fmt(r.p90TotalS, 1)}) | ${fmt(r.medianTtftS, 1)} | ${fmt(r.medianTokPerSec, 0)} | ${pct(r.kbHit)} | ${pct(r.successRate)} |`);
}
lines.push(`| Reference (Opus + web search) | – | 1.00 | ${pct(refCorrect)} | – | – | – | ${fmt(refMedianS, 1)} | – | – | – | ${pct(refInScope.length / Math.max(1, subsetIds ? subsetIds.size : questionCount))} |`, "");
lines.push("Quality ratio = mean rubric score of the BOAR answer / mean rubric score of the reference answer (rubric: " + RUBRIC.map(([k]) => k).join(", ") + ", 1–5 each). Win score = wins + ½ ties. CIs are percentile bootstrap over questions (2,000 resamples, seeded).", "");

if (rows.some((r) => r.jev)) {
  lines.push("## Second judge (Jev, another model family)", "");
  lines.push("Same blinded pairs, rubric and A/B orders, judged by typesafe-ai/jev through the Vercel AI Gateway (eval time only). Agreement between the two judges: `reports/judges-" + dataset + "-" + subset + ".md`.", "");
  lines.push("| System | n | Quality ratio (95% CI) | Correct answers | Win / tie / loss vs ref |", "|---|---|---|---|---|");
  for (const r of rows.filter((r) => r.jev)) lines.push(`| ${r.label} | ${r.jevN} | ${fmt(r.jev.qualityRatio)} (${fmt(r.jev.qualityRatioCI[0])}–${fmt(r.jev.qualityRatioCI[1])}) | ${pct(r.jevCorrectRate)} | ${pct(r.jev.boarWin)} / ${pct(r.jev.tie)} / ${pct(r.jev.refWin)} |`);
  lines.push("");
}

lines.push("## Rubric means", "");
lines.push("| System | " + RUBRIC.map(([k]) => `${k} (BOAR / ref)`).join(" | ") + " |");
lines.push("|---|" + RUBRIC.map(() => "---").join("|") + "|");
for (const r of rows.filter((r) => r.judged)) lines.push(`| ${r.label} | ` + RUBRIC.map(([k]) => `${fmt(r.judged.rubricBoar[k])} / ${fmt(r.judged.rubricRef[k])}`).join(" | ") + " |");
lines.push("");

lines.push("## Per category (correct answers · quality ratio)", "");
const cats = [...new Set(rows.flatMap((r) => r.pairs.map((p) => p.category)))].sort();
lines.push("| System | " + cats.join(" | ") + " |", "|---|" + cats.map(() => "---").join("|") + "|");
const rubricMean = (s) => RUBRIC.reduce((a, [k]) => a + s[k], 0) / RUBRIC.length;
for (const r of rows.filter((r) => r.judged)) {
  lines.push(`| ${r.label} | ` + cats.map((c) => {
    const ps = r.pairs.filter((p) => p.category === c);
    if (!ps.length) return "–";
    const correct = ps.filter((p) => p.boar.correctness >= 4).length / ps.length;
    const ratio = ps.reduce((a, p) => a + rubricMean(p.boar), 0) / ps.reduce((a, p) => a + rubricMean(p.ref), 0);
    return `${pct(correct)} · ${fmt(ratio)}`;
  }).join(" | ") + " |");
}
lines.push("", "Win scores per category are omitted: the reference wins almost every pair, so they are all near 0.", "");

lines.push("## Judge calibration", "", calibText, "");

lines.push("## Method", "");
lines.push(`- **BOAR answers**: desktop runner (\`eval/runner/desktop.ts\`) replaying the app pipeline (same classifier, lexical+semantic retrieval, fusion, prompt assembly, succinct personality, 512 max tokens, temperature 0.7, seed 42) with node-llama-cpp on ${rows[0]?.hardware ?? "n/a"}. Every GGUF is checked against a pinned sha256 before loading. Latency here is a desktop proxy, not phone latency.`);
lines.push(`- **Reference**: Claude Code headless (\`claude -p\`, model ${refs[0]?.modelId ?? "n/a"}, CLI ${refs[0]?.cliVersion ?? "n/a"}) with only WebSearch and WebFetch, fixed prompt \`eval/prompts/${refs[0]?.promptVersion ?? "reference.v1"}.md\`, run once per question. Reference latency includes web search round-trips.`);
lines.push("- **Judge**: Claude Code headless with no tools, blind pairwise comparison (citations, links, source lists and bold stripped from both answers), each pair judged in both A/B orders, first order drawn from a recorded seed. Orders that disagree count as a tie.");
lines.push("- **Consumption**: runs on the user's Claude Code subscription; no API key, no gateway. Cost below is the CLI's list-price equivalent, not a charge.");
lines.push("");
lines.push("## Limitations", "");
lines.push(rows.some((r) => r.jev)
  ? "- **The main judge and the reference are the same family (Claude).** Self-preference bias would favor the reference, so BOAR's ratio is, if anything, understated. The second judge (Jev, another family) is the check on that bias."
  : "- **Judge and reference are the same family (Claude).** Self-preference bias would favor the reference, so BOAR's ratio is, if anything, understated.");
lines.push("- Author notes in the dataset guide the judge; they can be incomplete or outdated for time-sensitive facts.");
lines.push("- Desktop latency (Apple M4, Metal) is not phone latency; device numbers come from `scripts/eval-device.mjs`.");
if (dataset === "v1") lines.push("- KB hit is 0% by design in v1: no question may target the corpus bundled with the app (dataset README, Exclusions), so v1 measures answers from the model plus whatever the bundled corpus happens to cover.");
const busy = rows.filter((r) => r.maxLoad > 8);
if (busy.length) lines.push(`- The host was busy during some runs (1-min load average up to ${fmt(Math.max(...busy.map((r) => r.maxLoad)), 1)}): ${busy.map((r) => r.label).join(", ")}. Latency may be inflated.`);
lines.push("");
lines.push("## Consumption", "", `Cumulative list-price equivalent logged in \`results/spend.jsonl\`: US$${fmt(totalSpend(join(EVAL_DIR, "results", "spend.jsonl")))}.`, "");

writeFileSync(join(outDir, `${name}.md`), lines.join("\n"));
console.log(`wrote reports/${name}.md and reports/${name}.quality-latency.svg`);
