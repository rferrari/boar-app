/**
 * Whether a layout of the chat list should pin it to the bottom again (audit #8/#10): only when its height
 * really changed (the keyboard moving), not on every layout pass that leaves it the same.
 */
export function heightChanged(previous: number | null, height: number, epsilon = 1): boolean {
  return previous == null || Math.abs(height - previous) >= epsilon;
}

/**
 * How long a glide (a native animated scrollToEnd) owns the list: no snap may cut it meanwhile. Not a motion
 * duration of ours: a guard a bit longer than the platforms' own smooth scroll (~250-300 ms).
 */
export const GLIDE_GUARD_MS = 350;

type Schedule = (fn: () => void, ms: number) => () => void;

const defaultSchedule: Schedule = (fn, ms) => {
  const id = setTimeout(fn, ms);
  return () => clearTimeout(id);
};

/**
 * Keeps the chat list anchored at its end without jumps (SEND-MOTION D4, v5). Two moves:
 * - `glide()`: one native animated scroll to the end. Content growing while following the end glides
 *   (a question sent, a block entering, the text's new line: Prism F2-5, where each line snapped 26 dp);
 * - a snap, for the list's own layout (the keyboard, in the same frame) and under reduce motion.
 * While a glide runs, nothing cuts it: changes are held, and one more glide follows it if any came in,
 * so growth that keeps coming (streamed words) is a chain of glides, never a restart (audit #13).
 * Before, sending scrolled before the question was even in the list, and every layout of the closing
 * keyboard and of the new content snapped over that animation (the "things jump" of the send).
 */
export function createBottomPin(deps: {
  /** `sameFrame`: a layout of the list itself (the keyboard), where the content didn't change. */
  scrollToEnd: (animated: boolean, sameFrame?: boolean) => void;
  now?: () => number;
  schedule?: Schedule;
}) {
  const now = deps.now ?? Date.now;
  const schedule = deps.schedule ?? defaultSchedule;
  let glideUntil = 0;
  let held = false;
  let armed = false;
  let cancelTrailing: (() => void) | null = null;

  const gliding = () => now() < glideUntil;

  function glide(animated = true) {
    armed = false;
    // Reduce motion: the same place, reached without the movement.
    if (!animated) {
      deps.scrollToEnd(false);
      return;
    }
    glideUntil = now() + GLIDE_GUARD_MS;
    held = false;
    deps.scrollToEnd(true);
    cancelTrailing?.();
    cancelTrailing = schedule(() => {
      cancelTrailing = null;
      glideUntil = 0;
      if (held) glide(true);
    }, GLIDE_GUARD_MS);
  }

  return {
    gliding,
    glide,
    /** The next content change glides instead of snapping: the new rows are measured by then. */
    armGlide() {
      armed = true;
    },
    /**
     * A change while following the end. `animated`: false under reduce motion. Content glides (held while
     * a glide runs); a "layout" change (the list's own height, the keyboard) snaps in the same frame and
     * leaves an armed glide for the content.
     */
    pin(animated = true, from: "content" | "layout" = "content") {
      if (armed && from === "content") return glide(animated);
      if (gliding()) {
        held = true;
        return;
      }
      if (from === "content" && animated) return glide(true);
      deps.scrollToEnd(false, from === "layout");
    },
    /** The user took the list (a drag): nothing of ours moves it any more. */
    cancel() {
      glideUntil = 0;
      held = false;
      armed = false;
      cancelTrailing?.();
      cancelTrailing = null;
    },
  };
}

/** Within this distance of the end the list still follows it (a new line may push it past the edge). */
export const FOLLOW_SLACK = 120;

/**
 * Whether "Jump to latest" shows (Prism CX-6): more than a screen from the end, answer or not (a long
 * conversation read back up had no way down once the answer was done); while an answer writes, as soon as
 * the list stops following it (the new text lands out of view). It goes once the end is reached.
 */
export function jumpToLatestShown(p: { distance: number; viewport: number; generating: boolean }): boolean {
  return p.generating ? p.distance >= FOLLOW_SLACK : p.distance > p.viewport;
}

/**
 * Whether a change of the list's own height (the keyboard, the composer growing a line) keeps its end in
 * view: when following the end, and always on the empty state (iPhone v8: with a 3-line composer the
 * last suggestion was cut at the list's bottom edge; the empty state is taller than a keyboard-high list,
 * so it counted as "not following"). There the hero goes up under the header's veil and the suggestions
 * stay whole. Only when the list gets shorter: opening the chat, the empty state starts at its top.
 */
export function keepEndOnResize(p: { following: boolean; empty: boolean; shrank: boolean }): boolean {
  return p.following || (p.empty && p.shrank);
}
