// Blocking, new fixed case (Boar 2026-09-27): the instant passage the chat shows for a suggestion must be on its topic,
// with the suggestions' criterion (SUGGESTION_SOURCES.expect). The title is not enough: PT q7 showed a passage of
// "Greenhouse effect" about "Surface heating ... a host star", so the passage text must name the topic: the head word
// of one of the expected titles ("Greenhouse effect" -> greenhouse, "Plate tectonics" -> plate/tectonic).

const heads = (expect) => [...new Set(expect.flatMap((t) => t.toLowerCase().split(/\s+/).filter((w) => w.length >= 5 || expect.length === 1).slice(0, 2)))];

/** @returns {null | { pass: boolean, title?: string, passage?: string, words?: string[] }} null = nothing to check */
export function checkSnippetTopic(row) {
  const snip = row.screen?.snippet;
  const expect = row.suggestion?.expect;
  if (!snip || !expect?.length) return null;
  const words = heads(expect);
  const body = snip.text.replace(/^[^\n]*\((em ingl[êe]s|in English)\):\s*/i, "");
  const re = new RegExp(`\\b(${words.map((w) => w.replace(/s$/, "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|")})`, "i");
  return re.test(body) ? { pass: true } : { pass: false, title: snip.title, passage: body.slice(0, 160), words };
}
