#!/usr/bin/env node
// Validates eval/dataset/questions.v<N>.jsonl:
//  - schema and id/category rules (offline, always)
//  - no gold title collides with the app's bundled corpus or in-app EVAL_SET titles (offline)
//  - with --online: every gold title resolves on en.wikipedia / en.wikivoyage and is not a
//    disambiguation page; redirects are reported so the gold can use the canonical title.
// Usage: node eval/scripts/validate-dataset.mjs [--file eval/dataset/questions.v1.jsonl] [--online]
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const args = process.argv.slice(2);
const file = args.includes("--file") ? args[args.indexOf("--file") + 1] : join(ROOT, "eval/dataset/questions.v1.jsonl");
const online = args.includes("--online");

const CATEGORIES = {
  explain: "exp", compare: "cmp", synthesis: "syn", multistep: "mst",
  "traveler-firstaid": "fa", "traveler-geo": "geo", "traveler-lang": "lng", "hard-for-1b": "h1b",
};
const SOURCES = new Set(["enwiki", "enwikivoyage"]);

const errors = [];
const items = readFileSync(file, "utf8").trim().split("\n").map((l, n) => {
  try { return JSON.parse(l); } catch { errors.push(`line ${n + 1}: invalid JSON`); return null; }
}).filter(Boolean);

const seen = new Set();
for (const it of items) {
  const where = it.id ?? "(no id)";
  for (const k of ["id", "category", "query", "lang", "license", "source_url"]) {
    if (typeof it[k] !== "string") errors.push(`${where}: ${k} must be a string`);
  }
  if (!Array.isArray(it.gold)) errors.push(`${where}: gold must be an array`);
  if (seen.has(it.id)) errors.push(`${where}: duplicate id`);
  seen.add(it.id);
  const prefix = CATEGORIES[it.category];
  if (!prefix) errors.push(`${where}: unknown category ${it.category}`);
  else if (!new RegExp(`^${prefix}-\\d{3}$`).test(it.id)) errors.push(`${where}: id must be ${prefix}-NNN`);
  for (const g of it.gold ?? []) {
    if (!SOURCES.has(g.source)) errors.push(`${where}: gold source ${g.source}`);
    if (!g.title || g.title.includes("_")) errors.push(`${where}: gold title must use spaces: ${g.title}`);
  }
}

// Contamination guard: the bundled corpus titles were picked to match the old in-app eval set.
const corpus = JSON.parse(readFileSync(join(ROOT, "assets/corpus/corpus.json"), "utf8"));
const corpusDocs = Array.isArray(corpus) ? corpus : corpus.documents ?? corpus.docs ?? [];
const banned = new Set(corpusDocs.map((d) => d.title.toLowerCase()));
const evalSetSrc = readFileSync(join(ROOT, "src/eval/evalSet.ts"), "utf8");
for (const m of evalSetSrc.matchAll(/expectedKbTitles:\s*\[([^\]]*)\]/g)) {
  for (const t of m[1].matchAll(/"([^"]+)"/g)) banned.add(t[1].toLowerCase());
}
for (const it of items) {
  for (const g of it.gold ?? []) {
    if (g.source === "enwiki" && banned.has(g.title.toLowerCase())) {
      errors.push(`${it.id}: gold "${g.title}" is in the bundled corpus / in-app eval set`);
    }
  }
}

if (online) {
  const bySource = new Map();
  for (const it of items) for (const g of it.gold ?? []) {
    if (!bySource.has(g.source)) bySource.set(g.source, new Set());
    bySource.get(g.source).add(g.title);
  }
  for (const [source, titleSet] of bySource) {
    const host = source === "enwikivoyage" ? "en.wikivoyage.org" : "en.wikipedia.org";
    const titles = [...titleSet];
    for (let i = 0; i < titles.length; i += 50) {
      const batch = titles.slice(i, i + 50);
      const url = `https://${host}/w/api.php?action=query&format=json&formatversion=2&redirects=1&prop=pageprops&ppprop=disambiguation&titles=${encodeURIComponent(batch.join("|"))}`;
      const res = await fetch(url, { headers: { "User-Agent": "boar-eval/1.0 (dataset validation)" } });
      const data = await res.json();
      const redirects = new Map((data.query.redirects ?? []).map((r) => [r.from, r.to]));
      const normalized = new Map((data.query.normalized ?? []).map((r) => [r.from, r.to]));
      const pages = new Map(data.query.pages.map((p) => [p.title, p]));
      for (const t of batch) {
        const norm = normalized.get(t) ?? t;
        const target = redirects.get(norm) ?? norm;
        const page = pages.get(target);
        if (!page || page.missing) errors.push(`${source}: "${t}" does not exist`);
        else if (page.pageprops && "disambiguation" in page.pageprops) errors.push(`${source}: "${t}" is a disambiguation page`);
        else if (target !== t) console.log(`note ${source}: "${t}" -> "${target}" (redirect/normalized; resolver accepts it)`);
      }
    }
  }
}

const counts = {};
for (const it of items) counts[it.category] = (counts[it.category] ?? 0) + 1;
console.log(`${items.length} items`, counts);
if (errors.length) {
  console.error(errors.join("\n"));
  process.exit(1);
}
console.log(online ? "OK (schema, contamination, gold titles online)" : "OK (schema, contamination)");
