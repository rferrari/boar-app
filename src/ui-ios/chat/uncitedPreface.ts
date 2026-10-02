import { uncitedPreface } from "../../routing/context";

const PREFACES = [uncitedPreface(true), uncitedPreface(false)];

/**
 * The answer text as shown, without the engine's opening "not from an offline source" line (Tusk 4375d76).
 * Prism CX-5: shown, it was the same warning twice and in two languages (the line in the answer's language,
 * the weak-source note in the app's). One warning stays, the note, in the app's language like the rest of the
 * app's own words, read once by screen readers. The line stays in what is copied or shared, where no note
 * travels with it (the engine's reason for it). While it streams in, a partial line shows nothing (no flash).
 */
export function withoutUncitedPreface(text: string): string {
  const start = text.trimStart();
  if (!start) return text;
  for (const p of PREFACES) {
    if (start.startsWith(p)) return start.slice(p.length).trimStart();
    if (p.startsWith(start)) return "";
  }
  return text;
}
