// Gate item "knowledge-topic" (Boar, 2026-09-27): every source a knowledge answer CITES (done.cited, the only ones the
// card shows since integration a8ee6bf) must be on the question's topic; the retrieved ones are reported too.
// A registered TARGET: reported, but not blocking until the engine fix (the result carries `target: true`).
const TOPIC = {
  "kt-001": /1906|san francisco/i,
  "kt-002": /rayleigh|scatter|sky|diffuse (sky|radiation)|atmospher|colou?r of the sky/i,
  "kt-003": /1970 (fifa )?world cup|fifa world cup|brazil national football|pel[ée]\b|world cup final/i,
  "kt-004": /dark matter|wimp|weakly interacting|axion|primordial black hole|modified newtonian|\bmond\b|lambda-cdm|galaxy rotation/i,
  "kt-005": /vaccin|immun|antibod|antigen|adaptive immune/i,
};

/** @returns {{ pass: boolean, failures: string[], warnings: string[], target: true } | null} */
export function checkKnowledgeTopic(row) {
  const re = TOPIC[row.queryId];
  if (!re) return null;
  const failures = [], warnings = [];
  const cited = row.citedTitles ?? [];
  const offCited = cited.filter((t) => !re.test(t));
  if (offCited.length) failures.push(`cited source off topic: ${[...new Set(offCited)].map((t) => `"${t}"`).join(", ")}`);
  const offShown = (row.retrievedTitles ?? []).filter((t) => !re.test(t));
  if (offShown.length) warnings.push(`shown/retrieved off topic: ${[...new Set(offShown)].slice(0, 4).map((t) => `"${t}"`).join(", ")}`);
  if (row.citedTitles === undefined) warnings.push("no done.cited in this run (runner or engine older than c884d7a)");
  return { pass: failures.length === 0, failures, warnings, target: true };
}
