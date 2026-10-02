import type { AnswerEvent } from "./answerEvents";

/**
 * How long streamed tokens wait before the chat renders them (audit #6). On-device models write 10-30 tokens
 * a second (33-100 ms apart), so the old 50 ms window rendered about once per token; ~110 ms groups 1-3
 * tokens per render (about 9 renders a second, still reads as continuous).
 */
export const STREAM_FLUSH_MS = 110;

/** Tokens wait for the batch; anything else (stage, sources, done, warnings) shows at once. */
export function flushDelay(event: AnswerEvent): number {
  return event.type === "token" ? STREAM_FLUSH_MS : 0;
}

/**
 * Whether a flushed batch gets the DS layout animation (the snippet folding to its preview when an answer is
 * done, Prism F2-2). Not with a warning, nor for a declined answer: there the text leaves through its Reveal,
 * and a native layout animation on the same view made it vanish in one frame (iPhone v9).
 */
export function batchAnimatesLayout(events: readonly AnswerEvent[], declined: boolean): boolean {
  if (declined) return false;
  if (events.some((e) => e.type === "warning")) return false;
  return events.some((e) => e.type === "done");
}
