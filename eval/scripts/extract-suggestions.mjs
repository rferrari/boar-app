#!/usr/bin/env node
// Writes the empty-chat suggestions of a source tree as a gate dataset, with each one's declared source
// (SUGGESTION_SOURCES in src/ui/chat/suggestions.ts: corpus ids + expected title words), and a run plan grouping
// the ids by the corpus they must be asked with. Ids come from the English text (lib/suggestion-check.mjs).
// Usage: node scripts/extract-suggestions.mjs <tree-root> <out.jsonl> <plan.tsv>
//   plan.tsv lines: <pack ids, comma-separated or "-"><TAB><question ids, comma-separated>   ("builtin" is always there)
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { suggestionId } from "./lib/suggestion-check.mjs";

const [root, out, planOut] = process.argv.slice(2);
const load = (l) => JSON.parse(readFileSync(join(root, "src/i18n/locales", `${l}.json`), "utf8")).chat?.suggestions ?? {};
const en = load("en"), pt = load("pt");

// SUGGESTION_SOURCES entries, fields in any order: { key: "q1", corpus: [...], expect: [...], langs: ["en"] }
const srcFile = join(root, "src/ui/chat/suggestions.ts");
const sources = {};
// SUGGESTION_VALIDATION.byModel: "<model id>": { en: [...], pt: [...] } = the keys each model is offered per language.
const byModel = {};
if (existsSync(srcFile)) {
  const text = readFileSync(srcFile, "utf8");
  const list = (obj, field) => { const m = obj.match(new RegExp(`${field}:\\s*\\[([^\\]]*)\\]`)); return m ? [...m[1].matchAll(/"([^"]+)"/g)].map((y) => y[1]) : null; };
  for (const m of text.matchAll(/\{\s*key:\s*"(\w+)"[^{}]*\}/g)) {
    sources[m[1]] = { corpus: list(m[0], "corpus") ?? [], expect: list(m[0], "expect") ?? [], langs: list(m[0], "langs") };
  }
  for (const m of text.matchAll(/"([\w.-]+)":\s*\{\s*en:\s*\[([^\]]*)\],\s*pt:\s*\[([^\]]*)\]\s*\}/g)) {
    const keys = (s) => [...s.matchAll(/"(\w+)"/g)].map((y) => y[1]);
    byModel[m[1]] = { en: keys(m[2]), pt: keys(m[3]) };
  }
  // A declaration block we cannot read must stop the gate, not silently fall back to the builtin corpus.
  if (/SUGGESTION_SOURCES/.test(text) && !Object.keys(sources).length) throw new Error(`SUGGESTION_SOURCES found in ${srcFile} but no entry could be parsed`);
}

// The app offers a (question, language) pair to a model when `langs` includes the language (or is undeclared) and the
// model's byModel list for that language has the key (suggestionsFor). offeredTo = those models (null: no byModel).
const langOk = (k, lang) => !sources[k]?.langs || sources[k].langs.includes(lang);
const offeredTo = (k, lang) => (Object.keys(byModel).length ? Object.keys(byModel).filter((m) => langOk(k, lang) && byModel[m][lang].includes(k)) : null);
const offered = (k, lang) => langOk(k, lang) && (offeredTo(k, lang)?.length ?? 1) > 0;
// Every (question, language) pair is written; `offered` says whether the app shows it (CIT-1 measures all of them,
// the gate blocks only on the offered ones).
const rows = Object.keys(en).flatMap((k) => {
  const suggestion = (lang) => ({ key: k, ...(sources[k] ?? { corpus: [], expect: [] }), offered: offered(k, lang), offeredTo: offeredTo(k, lang) });
  const base = { category: "suggestion", gold: [], license: "original" };
  return [
    { ...base, suggestion: suggestion("en"), id: suggestionId(en[k], "en"), query: en[k], lang: "en", source_url: "src/i18n/locales/en.json" },
    ...(pt[k] ? [{ ...base, suggestion: suggestion("pt"), id: suggestionId(en[k], "pt"), query: pt[k], lang: "pt-BR", source_url: "src/i18n/locales/pt.json" }] : []),
  ];
});
writeFileSync(out, rows.map((r) => JSON.stringify(r)).join("\n") + "\n");

// One run per declared corpus combination (the first listed corpus; "builtin" = no pack).
const plan = {};
for (const r of rows) {
  const c = r.suggestion.corpus[0] ?? "builtin";
  (plan[c === "builtin" ? "-" : c] ??= []).push(r.id);
}
writeFileSync(planOut, Object.entries(plan).map(([p, ids]) => `${p}\t${ids.join(",")}`).join("\n") + "\n");
console.log(`${rows.length} suggestions (${rows.filter((r) => r.suggestion.offered).length} offered, ${Object.keys(sources).length} with a declared source) -> ${out}; plan: ${Object.keys(plan).join(" ")}`);
