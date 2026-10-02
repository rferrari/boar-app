/**
 * A conversation's title is the start of its first question (Prism TT-1: model-made titles read
 * "Stay" or "Monsoon causes vary.", a claim the answer never made). No model, set when the session
 * is created, so a failed answer doesn't leave "New chat". Cut at a word, with an ellipsis.
 */
export const TITLE_MAX_CHARS = 42;

export function titleFromQuestion(question: string, max = TITLE_MAX_CHARS): string {
  const text = question.replace(/\s+/g, " ").trim();
  if (text.length <= max) return text;
  const cut = text.slice(0, max);
  const atWord = cut.lastIndexOf(" ");
  return `${(atWord >= max / 2 ? cut.slice(0, atWord) : cut).replace(/[\s,;:.–-]+$/, "")}…`;
}
