// Gate item "pt-topic" (Boar, fix/pt1-lexicon-names 9192a41): "Por que existem as estações do ano?" with and without
// accents must show only on-topic sources (Season, Axial tilt, solstice/equinox) or refuse honestly with no source;
// never the Walipini greenhouse or a hurricane-season article (the PT guard matched the lexicon name loosely).
const ON_TOPIC = /^season$|^seasons$|\bseason\b(?!.*hurricane)|axial tilt|axial precession|solstice|equinox|esta[çc][õo]es do ano|earth's orbit|obliquity/i;
const NEVER = /walipini|hurricane|furac/i;
const REFUSAL = /(don't|do not) support this answer|n[ãa]o sustentam esta resposta|did(n't| not) find|(no|don't have a) (reliable |good )?(offline )?source|won't answer from memory|n[ãa]o encontrei|n[ãa]o tenho (uma )?fonte|n[ãa]o est[áa] no acervo|fonte offline confi[áa]vel/i;

/** @returns {{ pass: boolean, failures: string[], warnings: string[] } | null} */
export function checkPtTopic(row) {
  if (!row.queryId?.startsWith("ptt-")) return null;
  // What the user sees: the cited sources (the card shows only those since a8ee6bf); older runs fall back to all sources.
  const shown = [...new Set(row.citedTitles ?? row.retrievedTitles ?? [])];
  const inPrompt = [...new Set(row.retrievedTitles ?? [])];
  const failures = [], warnings = [];
  const never = [...new Set([...shown, ...inPrompt])].filter((t) => NEVER.test(t));
  if (never.length) failures.push(`forbidden source shown or in the prompt: ${never.map((t) => `"${t}"`).join(", ")}`);
  const off = shown.filter((t) => !ON_TOPIC.test(t) && !NEVER.test(t));
  if (off.length) failures.push(`off-topic source cited: ${off.map((t) => `"${t}"`).join(", ")}`);
  if (!shown.length && !REFUSAL.test(row.answer ?? "") && !/not from an offline source|n[ãa]o (vem|[ée]) de (uma )?fonte offline/i.test(row.answer ?? "")) {
    const junk = inPrompt.filter((t) => !ON_TOPIC.test(t));
    failures.push(`no source cited and no honest refusal: answered from memory without saying so${junk.length ? ` (off-topic sources in the prompt: ${junk.slice(0, 3).map((t) => `"${t}"`).join(", ")})` : ""}`);
  }
  if (!shown.length && !failures.length) warnings.push("honest refusal or declared memory answer");
  return { pass: failures.length === 0, failures, warnings };
}
