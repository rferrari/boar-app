// Gate item "suggestions" (Boar RT-1, 2026-09-26): for every question the empty chat suggests, the app's search
// top-3 must contain a source on the question's topic, without packs and with the catalog's packs.
// Rules are written per question (keyed by the slug of the English text, so an edited suggestion needs a new rule).
// Deterministic, no model calls.

export const slug = (t) => t.toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 48);

/**
 * slug(EN question) -> onTopic: title pattern; retrieval "optional": math, where the search may find nothing on topic
 * (then the answer must contain `answer`, and any source it shows must still be on topic).
 */
export const SUGGESTION_TOPICS = {
  [slug("Why do we have seasons on Earth?")]: { onTopic: /season|axial tilt|solstice|equinox|obliquity|earth's orbit|esta[çc][õo]es/i },
  [slug("What is the difference between a pandemic and an epidemic?")]: { onTopic: /pandemic|epidemic|epidemiolog|outbreak|endemic|pandemia|epidemia/i },
  // RF-1 (engine-routing 176dd71): exact °C/°F by the calculator, without the model.
  [slug("What is 30 °C in Fahrenheit?")]: { onTopic: /fahrenheit|celsius|temperature|conversion/i, retrieval: "optional", answer: /\b86\b/, calculator: true },
  [slug("How do I stop a nosebleed?")]: { onTopic: /nosebleed|epistaxis|nasal|bleeding|sangramento|nariz/i },
};

// RF-1 (answered with a cited source on every model) is a registered target until the engine fix ("plate" root and
// [n] attribution by support, Tusk); flip to true when it lands to make it blocking (Boar, 2026-09-27, after 21bab42).
export const RF1_BLOCKING = true; // engine fix delivered in fix/pt1-lexicon-names deedc8d (gate ea21e82)

/** Row id for a suggestion: sug-<slug of the English text>-<lang>. */
export const suggestionId = (en, lang) => `sug-${slug(en)}-${lang}`;

/**
 * @param {{ queryId: string, rawRetrievedTitles?: string[], retrievedTitles?: string[] }} row
 * @returns {{ pass: boolean, failures: string[], warnings: string[] } | null}
 */
export function checkSuggestion(row) {
  if (!row.queryId?.startsWith("sug-")) return null;
  const key = row.queryId.replace(/^sug-/, "").replace(/-(en|pt)$/, "");
  // The tree's own declaration (SUGGESTION_SOURCES, Quill RT-1) wins: expected title words of an on-topic source.
  const declared = row.suggestion?.expect?.length
    ? new RegExp(row.suggestion.expect.map((w) => `\\b${w.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`).join("|"), "i")
    : null;
  const hand = SUGGESTION_TOPICS[key];
  const rule = declared ? { ...hand, onTopic: declared } : hand;
  if (!rule) return { pass: false, failures: [`no topic rule for this suggestion: add "${key}" to scripts/lib/suggestion-check.mjs`], warnings: [] };
  const failures = [], warnings = [];
  const top3 = (row.rawRetrievedTitles ?? row.retrievedTitles ?? []).slice(0, 3);
  if (rule.retrieval !== "optional") {
    if (!top3.length) failures.push("search returned nothing");
    else if (!top3.some((t) => rule.onTopic.test(t))) failures.push(`no on-topic source in the search top-3: ${top3.map((t) => `"${t}"`).join(", ")}`);
  }
  // Same rule as health answers (accepted 2026-09-26): a source the answer shows must be on topic. The card shows
  // only the cited sources (done.cited), so judge those when the row has them (as pt-topic-check does).
  const off = (row.citedTitles ?? row.retrievedTitles ?? []).filter((t) => !rule.onTopic.test(t));
  if (off.length) failures.push(`off-topic source shown: ${[...new Set(off)].map((t) => `"${t}"`).join(", ")}`);
  if (rule.answer && !rule.answer.test(row.answer ?? "")) failures.push(`answer lacks the expected result (${rule.answer.source}): "${(row.answer ?? "").slice(0, 100)}"`);
  // RF-1 (Prism, 35ffb87): a suggestion the app offers must be ANSWERED with a cited source on every model — never
  // declined by the Compacto nor answered from memory with the preface. Math (retrieval optional) is exempt from citing.
  const answer = row.answer ?? "";
  const searchFailures = failures.length;
  if (rule.calculator && row.modelCalled === true) failures.push("used the model (expected the exact conversion without it)");
  if (row.declined || /did(n't| not) find this in this phone|n[ãa]o encontrei isso no acervo/i.test(answer)) failures.push("declined (the app offers this suggestion, so it must answer it)");
  else if (/not from an offline source|n[ãa]o (vem|[ée]) de (uma )?fonte offline/i.test(answer)) failures.push("answered from memory with the no-source preface");
  else if (rule.retrieval !== "optional" && row.citedTitles !== undefined && !row.citedTitles.length) failures.push("answered without citing any source");
  // Only the RF-1 answer checks failed: a TARGET miss (reported, not blocking) until RF1_BLOCKING.
  const onlyAnswer = failures.length > 0 && searchFailures === 0;
  // A pair the app does not offer (langs) is measured for CIT-1 but never blocks.
  // Per model: the app offers the pair only to the models in SUGGESTION_VALIDATION.byModel (offeredTo).
  const notOffered = row.suggestion?.offered === false || (Array.isArray(row.suggestion?.offeredTo) && !row.suggestion.offeredTo.includes(row.modelId));
  if (notOffered) warnings.push("not offered by the app in this language (measured only)");
  return { pass: failures.length === 0, failures, warnings, ...((onlyAnswer && !RF1_BLOCKING) || notOffered ? { target: true } : {}) };
}
