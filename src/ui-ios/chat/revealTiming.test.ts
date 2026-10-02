import { describe, expect, it } from "vitest";
import { motionSpec } from "../theme/motionSpec";
import { REST, UNBOUNDED, boundsAfter, hideSteps, boundsAtStart, frameHeight, revealDeadline, revealMove, revealOnLayout, revealTiming, type RevealBounds, type RevealMove } from "./revealTiming";

describe("revealTiming (SEND-MOTION D2, DS §6 roles)", () => {
  it("shows as a layout change plus an enter fade", () => {
    const r = revealTiming(true, false);
    expect(r.height).toEqual({ duration: motionSpec("layout", false).duration, curve: "standard" });
    expect(r.opacity).toEqual({ duration: motionSpec("enter", false).duration, curve: "enter" });
  });
  it("hides as a layout change plus an exit fade", () => {
    const r = revealTiming(false, false);
    expect(r.height.duration).toBe(motionSpec("layout", false).duration);
    expect(r.opacity).toEqual({ duration: motionSpec("exit", false).duration, curve: "exit" });
    expect(r.opacity.duration).toBeLessThan(r.height.duration);
  });
  it("under reduce motion: instant height, only the short fade", () => {
    for (const shown of [true, false]) {
      const r = revealTiming(shown, true);
      expect(r.height.duration).toBe(0);
      expect(r.opacity.duration).toBe(motionSpec(shown ? "enter" : "exit", true).duration);
      expect(r.opacity.duration).toBeGreaterThan(0);
    }
  });
});

describe("revealOnLayout", () => {
  it("grows a block asked in this run from 0, records a restored one in place", () => {
    expect(revealOnLayout(null, 110, true)).toBe("grow");
    expect(revealOnLayout(null, 110, false)).toBe("record");
  });
  it("animates later changes of the content's height, ignores sub-pixel noise", () => {
    expect(revealOnLayout(110, 60, false)).toBe("resize");
    expect(revealOnLayout(60, 180, true)).toBe("resize");
    expect(revealOnLayout(110, 110.2, true)).toBe("none");
  });
  it("waits for content past the gap before its first measure (Prism F2-7)", () => {
    // A 12 pt gap, content not laid out yet: not a measure; the real layout then grows.
    expect(revealOnLayout(null, 12, true, 12)).toBe("wait");
    expect(revealOnLayout(null, 0, true, 0)).toBe("wait");
    expect(revealOnLayout(null, 58, true, 12)).toBe("grow");
    expect(revealOnLayout(null, 58, false, 12)).toBe("record");
    // Once measured, a later empty layout is a real resize.
    expect(revealOnLayout(58, 12, true, 12)).toBe("resize");
  });
});

// BUG-reveal-empty (iPhone 13, f7e8eb2): a streamed answer grew in to its first measure (almost only its
// gap), the frame kept that height, and the rest of the text stayed cut: an empty card. The model below
// replays a Reveal's frame (content height clamped by its bounds) through the moves the component makes.
describe("Reveal never hides shown content (BUG-reveal-empty)", () => {
  /** The bounds after a move is over (what stays applied, even if Fabric keeps the last animated value). */
  const over = (move: RevealMove | null, before: RevealBounds = REST) => (move ? boundsAfter(move) : before);

  it("content that grows after the reveal is shown whole", () => {
    // First token: the body measures only its gap and grows in to it.
    let bounds = over(revealMove("grow", null, 12));
    // The rest of the answer streams in after the move is over.
    expect(frameHeight(12, bounds)).toBe(12);
    expect(frameHeight(24, bounds)).toBe(24);
    expect(frameHeight(640, bounds)).toBe(640);
    expect(bounds).toEqual(REST);
    // Even a value frozen mid-move is harmless once the move ends: the bounds end at REST, not at a height.
    bounds = over(revealMove("resize", 12, 640));
    expect(frameHeight(2000, bounds)).toBe(2000);
  });

  it("a shrink lowers the floor and ends at REST too", () => {
    const move = revealMove("resize", 300, 120)!;
    expect(move.bound).toBe("minHeight");
    expect(frameHeight(120, boundsAtStart(move))).toBe(300);
    expect(frameHeight(120, boundsAfter(move))).toBe(120);
    expect(frameHeight(500, boundsAfter(move))).toBe(500);
  });

  it("while a move runs, the frame goes from the old height to the new one", () => {
    const grow = revealMove("grow", null, 110)!;
    expect(frameHeight(110, boundsAtStart(grow))).toBe(0);
    expect(frameHeight(110, { ...boundsAtStart(grow), maxHeight: 55 })).toBe(55);
  });

  it("only a hidden block ends closed", () => {
    const hide = revealMove("hide", null, 110)!;
    expect(frameHeight(110, boundsAfter(hide))).toBe(0);
    expect(boundsAfter(revealMove("show", 0, 110)!)).toEqual(REST);
  });

  it("the safety deadline covers both halves of a move, reduced or not", () => {
    for (const reduce of [false, true])
      for (const shown of [true, false]) {
        const t = revealTiming(shown, reduce);
        expect(revealDeadline(t)).toBeGreaterThanOrEqual(t.height.duration);
        expect(revealDeadline(t)).toBeGreaterThanOrEqual(t.opacity.duration);
        expect(revealDeadline(t)).toBeGreaterThan(0);
      }
  });

  it("REST clamps nothing", () => {
    expect(REST.minHeight).toBe(0);
    expect(REST.maxHeight).toBe(UNBOUNDED);
    expect(frameHeight(UNBOUNDED - 1, REST)).toBe(UNBOUNDED - 1);
  });
});

describe("hideSteps (plan B, iPhone F2-2: nothing folds, it fades then its space closes)", () => {
  it("fades over the DS exit role and never folds the height", () => {
    const h = hideSteps(false);
    expect(h.foldsHeight).toBe(false);
    expect(h.fadeMs).toBe(motionSpec("exit", false).duration);
    expect(h.curve).toBe("exit");
    // Content keeps its full height while it fades: bounds stay at REST.
    expect(frameHeight(320, REST)).toBe(320);
  });
  it("the deadline comes after the fade", () => {
    for (const reduce of [false, true]) {
      const h = hideSteps(reduce);
      expect(h.deadlineMs).toBeGreaterThanOrEqual(h.fadeMs);
    }
  });
  it("under reduce motion, the short fade still runs before the space closes (never 0: the text must not vanish)", () => {
    expect(hideSteps(true).fadeMs).toBe(motionSpec("exit", true).duration);
    expect(hideSteps(true).fadeMs).toBeGreaterThan(0);
    // Folding instead would be instant there (the DS layout role is 0 under reduce motion): the probe's case.
    expect(revealTiming(false, true).height.duration).toBe(0);
    expect(hideSteps(true).foldsHeight).toBe(false);
  });
});
