import type { AnswerState } from "./answerReducer";
import type { GeneratingStep } from "./presentation";

/**
 * Memo comparisons for the pieces of a streaming answer. answerReducer copies the answer on every token
 * but keeps the fields a token doesn't touch (sources, instant, places, location, the other tier), so a
 * piece compares only the fields it reads and skips the re-render while the text streams next to it.
 */

/** The same fields, by reference: a piece that reads only these renders the same. */
export function sameAnswerFields(a: AnswerState, b: AnswerState, keys: readonly (keyof AnswerState)[]): boolean {
  return a === b || keys.every((k) => a[k] === b[k]);
}

/** Same numbers in the same order (cited / related indexes, rebuilt on every render). */
export function sameNumbers(a: readonly number[] | undefined, b: readonly number[] | undefined): boolean {
  if (a === b) return true;
  if (!a || !b || a.length !== b.length) return false;
  return a.every((n, i) => n === b[i]);
}

/** The step card shows the same thing (generatingSteps builds a new list on every render). */
export function sameSteps(a: readonly GeneratingStep[], b: readonly GeneratingStep[]): boolean {
  if (a === b) return true;
  if (a.length !== b.length) return false;
  return a.every((s, i) => {
    const o = b[i];
    return s.key === o.key && s.label === o.label && s.short === o.short && s.icon === o.icon && s.status === o.status;
  });
}
