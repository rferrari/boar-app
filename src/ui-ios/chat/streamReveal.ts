import { motionSpec } from "../theme/motionSpec";

/**
 * SEND-MOTION v2 P1: streamed text shows at a steady pace instead of in 110 ms jumps. Tokens still arrive in
 * batches (streamBatch); between them and the screen a clock reveals characters at the rate they come in,
 * speeding up so the text never trails the model by more than about a second, and draining quickly when
 * the answer is done. The newest characters fade in through the text tones (disabled → secondary → primary).
 * Pure: the hook (useSmoothText) only runs the clock.
 */

/** The reveal clock's step: ~30 Hz, steady enough to read as flowing, half the renders of 60 Hz. */
export const REVEAL_FRAME_MS = 33;
/** The text may trail what the model wrote by about this much before the reveal speeds up. */
export const MAX_LAG_MS = 1000;
/** Once the answer is done, what's left shows within about this much. */
export const DRAIN_MS = 300;
/** Floor and ceiling of the pace, in characters per second (a slow model still flows; a burst still reads). */
export const MIN_CPS = 24;
export const MAX_CPS = 600;

/**
 * Characters per second to reveal at: the rate text comes in (clamped), or faster when behind (so the lag
 * stays under MAX_LAG_MS), or faster still when draining.
 */
export function revealRate(incomingCps: number, lagChars: number, draining: boolean): number {
  const base = Math.min(MAX_CPS, Math.max(MIN_CPS, incomingCps || 0));
  const catchUp = (lagChars * 1000) / (draining ? DRAIN_MS : MAX_LAG_MS);
  return Math.max(base, catchUp);
}

/** How far the reveal gets after `dtMs` (fractional; at least one character per step while behind). */
export function nextShown(shown: number, targetLength: number, dtMs: number, rate: number): number {
  if (shown >= targetLength) return targetLength;
  return Math.min(targetLength, shown + Math.max(1, (rate * dtMs) / 1000));
}

/** Smoothed incoming rate (characters per second) from the text's growth between two batches. */
export function incomingRate(previousCps: number, grewChars: number, overMs: number): number {
  if (grewChars <= 0 || overMs <= 0) return previousCps;
  const sample = (grewChars * 1000) / overMs;
  return previousCps > 0 ? previousCps * 0.7 + sample * 0.3 : sample;
}

/**
 * Where to cut the revealed text so no half-written marker shows for a frame: not inside a citation
 * ("[1" before "[12]"), not right after an opening "*"/"**" or "`" (the literal asterisks would flash
 * before the bold), not in the middle of a surrogate pair.
 */
export function safeCut(text: string, n: number): number {
  let cut = Math.max(0, Math.min(text.length, Math.floor(n)));
  if (cut >= text.length) return text.length;
  const head = text.slice(0, cut);
  const partial = /\[\d*$|\*{1,2}$|`$/.exec(head);
  if (partial) cut = partial.index;
  const code = text.charCodeAt(cut - 1);
  if (cut > 0 && code >= 0xd800 && code <= 0xdbff) cut -= 1;
  return cut;
}

/** A word longer than this (a URL, a compound) shows by characters rather than waiting whole. */
export const MAX_WORD = 24;

/**
 * Where to cut by whole words (cost option 1): at the end of the last complete word before `n`, so the
 * screen changes at the model's word rate (~15-20 a second) instead of on every clock step (~30), and the
 * fade runs per word, as in the chat apps. A word longer than MAX_WORD shows by characters; the whole
 * text shows at once when reached. Markers and surrogates as in safeCut.
 */
export function wordCut(text: string, n: number): number {
  const cut = safeCut(text, n);
  if (cut >= text.length || cut === 0) return cut;
  if (/\s/.test(text[cut])) return cut;
  let space = cut - 1;
  while (space >= 0 && !/\s/.test(text[space])) space--;
  if (cut - (space + 1) > MAX_WORD) return cut;
  return safeCut(text, Math.max(0, space));
}

/** One step of the reveal: the text length it reached and when. */
export interface RevealMark {
  end: number;
  at: number;
}

/**
 * The fade window: the DS `enter` role (something appears). With `change` (150 ms) and the DS's tertiary
 * being secondary's colour, the fade had two quick steps only and read weak in the dark theme.
 */
export const FADE_MS = motionSpec("enter", false).duration;

/**
 * How many of the last revealed characters are still fading in, per tone: the newest third of the
 * window in tertiary, the next third in secondary, the rest already primary.
 */
export function fadeTail(marks: readonly RevealMark[], shownLength: number, now: number, windowMs = FADE_MS): { tertiary: number; secondary: number } {
  let tertiaryFrom = shownLength;
  let secondaryFrom = shownLength;
  // The first mark younger than each threshold starts that tone (each mark's `end` is where its step
  // ended; the step's characters are those after the previous mark).
  for (let i = marks.length - 1; i >= 0; i--) {
    const age = now - marks[i].at;
    const startOfStep = i > 0 ? marks[i - 1].end : 0;
    if (age < windowMs / 3) tertiaryFrom = Math.min(tertiaryFrom, startOfStep);
    if (age < (windowMs * 2) / 3) secondaryFrom = Math.min(secondaryFrom, startOfStep);
    else break;
  }
  const tertiary = Math.max(0, shownLength - tertiaryFrom);
  const secondary = Math.max(0, tertiaryFrom - secondaryFrom);
  return { tertiary, secondary };
}

/** Drops marks older than the fade window (the clock keeps the list short). */
export function pruneMarks(marks: RevealMark[], now: number, windowMs = FADE_MS): RevealMark[] {
  const firstYoung = marks.findIndex((m) => now - m.at < windowMs);
  // Keep one older mark: it's where the youngest step starts.
  return firstYoung <= 0 ? (firstYoung === 0 ? marks : marks.slice(-1)) : marks.slice(firstYoung - 1);
}

/**
 * "tertiary" is drawn in text.disabled: the DS's tertiary is secondary's colour, and a fade needs a third,
 * fainter step. Decorative and gone within a third of the window, it carries no information (DS rule).
 */
export type TailTone = "tertiary" | "secondary";

/**
 * The last block's inline parts with the fading tail marked: the newest `tail.tertiary` characters in
 * tertiary, the `tail.secondary` before them in secondary, the rest untouched (null). Text parts split
 * where a tone starts; a citation is one piece. Counts are of the source text, so a Markdown marker in
 * the tail shifts it by a character or two (not visible at this size).
 */
export function toneTail<T extends { type: string; text?: string }>(
  inlines: readonly T[],
  tail: { tertiary: number; secondary: number }
): { part: T; tone: TailTone | null }[] {
  if (tail.tertiary <= 0 && tail.secondary <= 0) return inlines.map((part) => ({ part, tone: null }));
  // Budget per tone, spent from the end of the text backwards: tertiary first, then secondary.
  const budget: { tone: TailTone; left: number }[] = [
    { tone: "tertiary", left: tail.tertiary },
    { tone: "secondary", left: tail.secondary },
  ];
  const currentTone = () => budget.find((b) => b.left > 0) ?? null;
  const reversed: { part: T; tone: TailTone | null }[] = [];
  for (let i = inlines.length - 1; i >= 0; i--) {
    const part = inlines[i];
    if (typeof part.text !== "string") {
      // A citation: one piece, toned by where the tail is ("[n]" counts as 3).
      const b = currentTone();
      if (b) b.left = Math.max(0, b.left - 3);
      reversed.push({ part, tone: b?.tone ?? null });
      continue;
    }
    let text = part.text;
    let b = currentTone();
    while (text && b) {
      const n = Math.min(b.left, text.length);
      reversed.push({ part: { ...part, text: text.slice(text.length - n) }, tone: b.tone });
      text = text.slice(0, text.length - n);
      b.left -= n;
      b = currentTone();
    }
    if (text) reversed.push({ part: { ...part, text }, tone: null });
  }
  return reversed.reverse();
}

/**
 * Prism CX-12: the receipt and the send button came 0.3-0.5 s before the text finished showing (the steady
 * reveal drains after the model is done). A text block is still showing its end while the stream is over
 * and the reveal hasn't settled; an answer counts as running until none of its blocks is.
 */
export function isDraining(streaming: boolean, settled: boolean): boolean {
  return !streaming && !settled;
}

export function answerStillShowing(running: boolean, draining: Readonly<Record<string, boolean>>): boolean {
  return running || Object.values(draining).some(Boolean);
}
