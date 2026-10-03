// PT answers written in English (Tusk/Boar 2026-09-27): per model, the MODEL answers to the 41 PT v2 items (4B:
// results/gates/<label>/pt/v2-pt.jsonl, 1.5B: pt-compact/v2-pt.jsonl) whose language is English, by the tree's own
// passageLanguage (src/routing/context.ts). Not counted: no model call (extractive, grounding-guard, calculator,
// places, health excerpts), refusals/declines. The fixed "not from an offline source" preface is removed first.
// Usage (from eval/): npx tsx scripts/pt-language.mts <tree-root> <control-label> <candidate-label>
//   exit 1 = a model has more English answers than its control (blocking, Boar 2026-09-27)
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const [tree, control, candidate] = process.argv.slice(2);
const { passageLanguage } = await import(join(tree, "src/routing/context.ts"));
const EVAL = new URL("..", import.meta.url).pathname;
const REFUSAL = /(don't|do not) support this answer|n[ãa]o sustentam esta resposta|did(n't| not) find|n[ãa]o encontrei|no (reliable |good )?(offline )?source|n[ãa]o tenho uma fonte/i;
const PREFACE = /^\s*(this answer is not from an offline source\.?|esta resposta n[ãa]o vem de uma fonte offline\.?)\s*/i;
function measure(label: string, file: string) {
  const p = join(EVAL, "results/gates", label, file);
  if (!existsSync(p)) return null;
  const rows = readFileSync(p, "utf8").trim().split("\n").map((l) => JSON.parse(l));
  const model = rows.filter((r) => r.modelCalled !== false && !r.declined && !REFUSAL.test(r.answer ?? "") && !(r.reasonCodes ?? []).some((c: string) => /^grounding:health-(extractive|no-source)$|^answer:/.test(c)));
  const en = model.filter((r) => passageLanguage(String(r.answer ?? "").replace(PREFACE, "")) === "en").map((r) => r.queryId);
  return { n: model.length, en };
}
const L = [`# PT answers written in English: ${candidate} vs ${control}`, ""];
const rows: string[] = [];
let summary: string[] = [];
let fail = false;
for (const [name, file] of [["4B", "pt/v2-pt.jsonl"], ["1.5B", "pt-compact/v2-pt.jsonl"]]) {
  const c = measure(control, file), k = measure(candidate, file);
  if (!k) continue;
  // Blocking (Boar, after 4169899): per model, the candidate may not have more English answers than the control.
  if (c && k.en.length > c.en.length) fail = true;
  summary.push(`${name} ${k.en.length}/${k.n}${c ? ` (control ${c.en.length}/${c.n})` : ""}`);
  rows.push(`| ${name} | ${c ? `${c.en.length}/${c.n}` : "–"} | **${k.en.length}/${k.n}** | ${k.en.join(", ") || "none"} | ${c ? c.en.filter((q) => !k.en.includes(q)).join(", ") || "none" : "–"} | ${c ? k.en.filter((q) => !c.en.includes(q)).join(", ") || "none" : "–"} |`);
}
L.splice(2, 0, `TL;DR: **${fail ? "FAIL" : "PASS"}**: model answers in English to PT questions: ${summary.join(" · ")}. Blocks when a model has more than its control. Language by the candidate tree's passageLanguage.`, "");
L.push("| Model | Control | Candidate | Candidate ids in English | Fixed (EN → PT) | New in English |", "|---|---|---|---|---|---|", ...rows, "");
writeFileSync(join(EVAL, "reports", `pt-language-${control}-vs-${candidate}.md`), L.join("\n"));
console.log(L.join("\n"));
process.exit(fail ? 1 : 0);
