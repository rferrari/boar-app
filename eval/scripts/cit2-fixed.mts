// CIT-2 fixed case (Prism, review/ui-qa/cases/CIT-2.md): the 1.5B puts [1] after the period ("… Zone. [1]") and the
// support attribution added a second [1] ("… [1]. [1]"). Deterministic: the raw model texts from the AVD runs go
// through the tree's own post-processing (checkCitations, then attributeCitations, as src/routing/answer.ts does)
// with the real sources, and the result must carry [1] exactly once. No model, no sampling.
// Usage (from eval/): npx tsx scripts/cit2-fixed.mts <tree-root>   (exit 1 = the duplicate is back)
import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const tree = resolve(process.argv[2] ?? "..");
const citations: any = await import(join(tree, "src/routing/citations.ts"));
const bodies: Record<string, string> = JSON.parse(readFileSync(new URL("../dataset/cit2-sources.json", import.meta.url), "utf8"));
const chunk = (title: string) => ({ chunkId: title, docId: title, title, body: bodies[title], score: 1, source: "corpus" }) as any;
const CASES = [
  { q: "What causes the monsoon?", source: "Monsoon", raw: "The monsoon is caused by seasonal reversing wind and changes in atmospheric circulation and precipitation associated with the annual latitudinal oscillation of the Intertropical Convergence Zone. [1]" },
  { q: "What causes the greenhouse effect?", source: "Greenhouse effect", raw: "The greenhouse effect is caused by heat-trapping gases in a planet's atmosphere that prevent the planet from losing heat to space, raising its surface temperature. [1]" },
  { q: "What causes the monsoon?", source: "Monsoon", raw: "The monsoon is a seasonal reversing wind that follows the Intertropical Convergence Zone between its northern and southern limits. [1]" },
];
let fail = 0;
for (const c of CASES) {
  const sources = [chunk(c.source)];
  let text = c.raw;
  if (citations.checkCitations) text = citations.checkCitations(text, sources).text ?? text;
  const out = citations.attributeCitations(text, sources).text;
  const n = (out.match(/\[1\]/g) ?? []).length;
  const ok = n === 1 && !/\[(\d+)\][ \t]*[.!?]?[ \t]*\[\1\]/.test(out);
  if (!ok) fail++;
  console.log(`${ok ? "PASS" : "FAIL"} ${c.q} -> …${out.slice(-40)}  ([1] x${n})`);
}
console.log(fail ? `CIT-2 FAIL: ${fail}/${CASES.length}` : `CIT-2 PASS: ${CASES.length}/${CASES.length}`);
process.exit(fail ? 1 : 0);
