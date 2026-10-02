import { motionSpec, type Curve } from "../theme/motionSpec";

export interface RevealTiming {
  height: { duration: number; curve: Curve };
  opacity: { duration: number; curve: Curve };
}

/**
 * How a Reveal moves (SEND-MOTION D2), from the DS roles only (motionSpec, DS §6): its height as a
 * `layout` change, its fade as `enter` (showing) or `exit` (hiding). Reduce motion comes from the same
 * table: the height is instant and the fade stays short, nothing travels.
 */
export function revealTiming(shown: boolean, reduceMotion: boolean): RevealTiming {
  const layout = motionSpec("layout", reduceMotion);
  const fade = motionSpec(shown ? "enter" : "exit", reduceMotion);
  return {
    height: { duration: layout.duration, curve: layout.curve },
    opacity: { duration: fade.duration, curve: fade.curve },
  };
}

/**
 * What a Reveal does when its content measures `next` (it was `previous`, null before the first layout):
 * - "record": first layout of a block shown in place (restored history): nothing moves;
 * - "grow": first layout of a block that appears (asked in this run): 0 → its height;
 * - "resize": the content changed height (a preview collapsing, a count becoming a list);
 * - "none": same height;
 * - "wait": a first layout with no content past `floor` (the block's gap): not a measure yet.
 */
export function revealOnLayout(
  previous: number | null,
  next: number,
  appear: boolean,
  floor = 0,
  epsilon = 0.5
): "record" | "grow" | "resize" | "none" | "wait" {
  // A first layout with nothing but the block's own gap (content not laid out yet, e.g. behind a Swap):
  // wait for the real one. Growing to the empty height would settle at rest, and the content would then
  // come in at once (Prism F2-7: "Found 1 passage" pushing everything 42-46 dp in one frame).
  if (previous == null && next <= floor + epsilon) return "wait";
  if (previous == null) return appear ? "grow" : "record";
  return Math.abs(next - previous) < epsilon ? "none" : "resize";
}

/**
 * When a move must be over, at the latest: both halves back to back. Past it the Reveal forces its rest
 * (content fully visible) whatever happened to the animation or the measurement (BUG-reveal-empty).
 */
export function revealDeadline(timing: RevealTiming): number {
  return timing.height.duration + timing.opacity.duration;
}

/** Larger than any block: a maxHeight that never clamps. */
export const UNBOUNDED = 1_000_000;

/** The height bounds of a Reveal's frame. The frame is its content's height clamped to them. */
export interface RevealBounds {
  minHeight: number;
  maxHeight: number;
}

/**
 * At rest the bounds clamp nothing: the block is exactly its content, however that content grows later
 * (streamed text, a relayout). BUG-reveal-empty (iPhone 13, f7e8eb2): the frame's animated `height` was
 * "released" by dropping it from the animated style, but on Fabric the last animated value stays applied,
 * so a streamed answer stayed cut at its first measure. Bounds instead of a height: every move ends here,
 * and a value left applied at rest is harmless.
 */
export const REST: RevealBounds = { minHeight: 0, maxHeight: UNBOUNDED };

/**
 * One move of the frame: which bound animates, from where to where, and whether it ends at REST.
 * Growing animates maxHeight up (the content is already taller: the cap opens); shrinking animates
 * minHeight down (the content is already shorter: the floor lowers); hiding animates maxHeight to 0 and
 * stays there until the content unmounts.
 */
export interface RevealMove {
  bound: keyof RevealBounds;
  from: number;
  to: number;
  /** Ends at REST (shown); false = stays closed (hidden). */
  release: boolean;
}

export function revealMove(kind: "grow" | "resize" | "hide" | "show", previous: number | null, next: number): RevealMove | null {
  switch (kind) {
    case "grow":
    case "show":
      return { bound: "maxHeight", from: previous ?? 0, to: next, release: true };
    case "hide":
      return { bound: "maxHeight", from: next, to: 0, release: false };
    case "resize":
      if (previous == null || previous === next) return null;
      return next > previous
        ? { bound: "maxHeight", from: previous, to: next, release: true }
        : { bound: "minHeight", from: previous, to: next, release: true };
  }
}

/** The bounds when a move starts: the other bound at rest, the moving one at `from`. */
export function boundsAtStart(move: RevealMove): RevealBounds {
  return move.bound === "maxHeight" ? { minHeight: 0, maxHeight: move.from } : { minHeight: move.from, maxHeight: UNBOUNDED };
}

/** The bounds once a move is over. */
export function boundsAfter(move: RevealMove): RevealBounds {
  return move.release ? REST : { minHeight: 0, maxHeight: 0 };
}

/** The frame's height for a content height under some bounds (what the user sees). */
export function frameHeight(content: number, b: RevealBounds): number {
  return Math.min(Math.max(content, b.minHeight), b.maxHeight);
}

/**
 * How a block leaves (plan B, iPhone F2-2: folding its height, the declined text and its sources vanished in
 * one frame, twice, on the device). No height moves: its opacity fades over the DS `exit` role, the frame
 * keeps its height (nothing cut), then, invisible, it unmounts under the DS layout animation and only its
 * neighbours slide. The deadline ends it even if the fade's end never reports.
 */
export function hideSteps(reduceMotion: boolean): { fadeMs: number; curve: Curve; foldsHeight: false; deadlineMs: number } {
  const t = revealTiming(false, reduceMotion);
  return { fadeMs: t.opacity.duration, curve: t.opacity.curve, foldsHeight: false, deadlineMs: revealDeadline(t) };
}
