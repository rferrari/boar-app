#!/usr/bin/env node
// PT questions about a named EIP/ERC (Boar, 2026-09-27, knowledge 85773fc): the answer must cite the page of that
// proposal ("Ethereum EIPs/ERCs: EIP-1559: ..."), not only the generic "Ethereum" article. Reads the gate's PT runs
// (results/gates/<label>/pt/v2-pt.jsonl, 4B with packs). Blocking set: the 7 EIPs Boar listed; ERCs reported.
// Usage (from eval/): node scripts/eip-pt-check.mjs <control-label> <candidate-label>
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const EVAL_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const labels = process.argv.slice(2);
const BLOCKING = ["4844", "1559", "155", "7702", "7251", "4895", "3675"];
const questions = readFileSync(join(EVAL_DIR, "dataset/questions.v2-pt.jsonl"), "utf8").trim().split("\n").map((l) => JSON.parse(l))
  .map((q) => ({ id: q.id, query: q.query, ids: [...q.query.matchAll(/(EIP|ERC)-?(\d+)/g)].map((m) => [m[1], m[2]]) })).filter((q) => q.ids.length);
const page = (kind, n) => new RegExp(`\\b${kind}-${n}:`);
const res = {};
for (const label of labels) {
  const rows = Object.fromEntries(readFileSync(join(EVAL_DIR, "results/gates", label, "pt/v2-pt.jsonl"), "utf8").trim().split("\n").map((l) => JSON.parse(l)).map((r) => [r.queryId, r]));
  res[label] = Object.fromEntries(questions.map((q) => {
    const r = rows[q.id] ?? {};
    const cited = r.citedTitles ?? [];
    const top3 = (r.rawRetrievedTitles ?? []).slice(0, 3);
    return [q.id, { cited: q.ids.every(([k, n]) => cited.some((t) => page(k, n).test(t))), top3: q.ids.every(([k, n]) => top3.some((t) => page(k, n).test(t))), citedTitles: [...new Set(cited)] }];
  }));
}
const cand = labels.at(-1);
const blockingQs = questions.filter((q) => q.ids.some(([k, n]) => k === "EIP" && BLOCKING.includes(n)));
const passed = blockingQs.filter((q) => res[cand][q.id].cited).length;
const L = [`# PT questions on a named EIP/ERC: ${labels.join(" vs ")}`, "",
  `TL;DR: ${cand}: the right proposal page is cited on **${passed}/${blockingQs.length}** of Boar's 7 EIPs (${BLOCKING.map((n) => `EIP-${n}`).join(", ")}). Regenerate with \`node eval/scripts/eip-pt-check.mjs ${labels.join(" ")}\`.`, "",
  `| Question | Proposal | ${labels.map((l) => `${l}: cited / in top-3`).join(" | ")} | Cited titles (${cand}) |`, `|---|---|${labels.map(() => "---").join("|")}|---|`];
for (const q of questions) L.push(`| ${q.id}${blockingQs.includes(q) ? " (blocking)" : ""} | ${q.ids.map((x) => x.join("-")).join(", ")} | ${labels.map((l) => `${res[l][q.id].cited ? "yes" : "**no**"} / ${res[l][q.id].top3 ? "yes" : "no"}`).join(" | ")} | ${res[cand][q.id].citedTitles.map((t) => t.replace("Ethereum EIPs/ERCs: ", "").slice(0, 40)).join("; ") || "–"} |`);
writeFileSync(join(EVAL_DIR, "reports", `eip-pt-${labels.join("-vs-")}.md`), L.join("\n") + "\n");
console.log(L.join("\n"));
process.exit(passed === blockingQs.length ? 0 : 1);
