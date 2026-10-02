/**
 * Motion numbers and roles, with no React Native import so tests can read them.
 * tokens.motion and theme/motion.ts build on these; nothing else may write a
 * duration (motionGuard.test.ts). Rules in docs/DESIGN_SYSTEM.md §6.
 */

export const DURATION = { instant: 90, fast: 150, base: 220, slow: 320 } as const;

/** Cubic-bezier control points: Material 3 "standard", "emphasized decelerate" and "emphasized accelerate". */
export const CURVE = {
  standard: [0.2, 0, 0, 1],
  enter: [0.05, 0.7, 0.1, 1],
  exit: [0.3, 0, 0.8, 0.15],
} as const satisfies Record<string, readonly [number, number, number, number]>;

export type Curve = keyof typeof CURVE;

/** Periods of the ambient loops: spinner turn, indeterminate sweep, skeleton pulse (there and back). */
export const LOOP = { spin: 900, sweep: 1200, pulse: 1400 } as const;

/** A loading placeholder shows only when the wait is longer than this (no skeleton blink on a fast load). */
export const DELAY = { skeleton: 150 } as const;

/** How far an entering or leaving block travels, in pt. */
export const TRAVEL = 8;

/**
 * A "pop" enter (a new item joining a live list, e.g. an article found while BOAR researches): it
 * starts at this scale and grows to 1 with the enter curve. Dropped under reduce motion (a fade only).
 */
export const POP_SCALE = 0.9;

/** An enter's starting scale: POP_SCALE for a pop, 1 otherwise or under reduce motion. */
export function enterScale(pop: boolean, reduceMotion: boolean): number {
  return pop && !reduceMotion ? POP_SCALE : 1;
}

/**
 * - enter: something appears (sheet, toast, content after a skeleton, a new step, an expanded item).
 * - exit: something leaves. In a content swap it is the short half of the crossfade.
 * - change: a state changes in place (selection border/fill, check, radio dot, stepper segment).
 * - layout: a size or position changes (expand, "show more", a card that shrinks).
 */
export type MotionRole = "enter" | "exit" | "change" | "layout";

export interface MotionSpec {
  duration: number;
  curve: Curve;
  /** Distance the block may travel; 0 = fade only. */
  travel: number;
}

/**
 * The one table of motion. Reduce motion keeps a short fade for enter/exit (a hard cut reads as a
 * jump), drops every travel, keeps `change` (colour only, nothing moves) and makes `layout` instant.
 */
export function motionSpec(role: MotionRole, reduceMotion: boolean, opts: { swap?: boolean } = {}): MotionSpec {
  switch (role) {
    case "enter":
      return reduceMotion
        ? { duration: DURATION.instant, curve: "enter", travel: 0 }
        : { duration: DURATION.base, curve: "enter", travel: TRAVEL };
    case "exit":
      return {
        duration: reduceMotion || opts.swap ? DURATION.instant : DURATION.fast,
        curve: "exit",
        travel: reduceMotion ? 0 : TRAVEL,
      };
    case "change":
      return { duration: DURATION.fast, curve: "standard", travel: 0 };
    case "layout":
      return { duration: reduceMotion ? 0 : DURATION.base, curve: "standard", travel: 0 };
  }
}

/**
 * A content swap (setup step, conversation, skeleton → content): the old block leaves fast, the new
 * one enters after it. Sequential and short, never two long animations in a row.
 */
export function crossfadeSpec(reduceMotion: boolean): { out: MotionSpec; in: MotionSpec & { delay: number } } {
  const out = motionSpec("exit", reduceMotion, { swap: true });
  return { out, in: { ...motionSpec("enter", reduceMotion), delay: out.duration } };
}
