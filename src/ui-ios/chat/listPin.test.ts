import { describe, it, expect } from "vitest";
import { createBottomPin, GLIDE_GUARD_MS, heightChanged, jumpToLatestShown, keepEndOnResize } from "./listPin";

describe("heightChanged (audit #8/#10)", () => {
  it("pins on the first layout and while the keyboard moves the list", () => {
    expect(heightChanged(null, 600)).toBe(true);
    expect(heightChanged(600, 580)).toBe(true);
    expect(heightChanged(580, 600)).toBe(true);
  });
  it("ignores layouts that leave the height as it was", () => {
    expect(heightChanged(600, 600)).toBe(false);
    expect(heightChanged(600, 600.4)).toBe(false);
  });
});

function harness() {
  let clock = 0;
  const calls: boolean[] = [];
  let pending: { fn: () => void; at: number } | null = null;
  const pin = createBottomPin({
    scrollToEnd: (animated) => calls.push(animated),
    now: () => clock,
    schedule: (fn, ms) => {
      const task = { fn, at: clock + ms };
      pending = task;
      return () => {
        if (pending === task) pending = null;
      };
    },
  });
  const advance = (ms: number) => {
    clock += ms;
    if (pending && pending.at <= clock) {
      const { fn } = pending;
      pending = null;
      fn();
    }
  };
  return { pin, calls, advance };
}

describe("createBottomPin (SEND-MOTION D4)", () => {
  it("content growing while following the end glides, never snaps (v5, Prism F2-5)", () => {
    const h = harness();
    h.pin.pin(); // a new line of the answer
    expect(h.calls).toEqual([true]);
    h.pin.pin(); // more words while it glides: held, not a restart
    h.pin.pin();
    expect(h.calls).toEqual([true]);
    h.advance(GLIDE_GUARD_MS); // one more glide for what came in
    expect(h.calls).toEqual([true, true]);
    expect(h.calls.every((animated) => animated)).toBe(true);
  });

  it("streamed words keep coming: a chain of glides, each started once its guard is over", () => {
    const h = harness();
    for (let i = 0; i < 12; i++) {
      h.pin.pin();
      h.advance(GLIDE_GUARD_MS / 3);
    }
    // 12 changes over 4 guards: about one glide per guard, none cut short.
    expect(h.calls.length).toBeLessThanOrEqual(5);
    expect(h.calls.every((animated) => animated)).toBe(true);
  });

  it("the list's own layout (the keyboard) still snaps in the same frame", () => {
    const h = harness();
    h.pin.pin(true, "layout");
    expect(h.calls).toEqual([false]);
  });

  it("glides once on the content change after a send, not before it", () => {
    const h = harness();
    h.pin.armGlide();
    expect(h.calls).toEqual([]);
    h.pin.pin();
    expect(h.calls).toEqual([true]);
    expect(h.pin.gliding()).toBe(true);
  });

  it("holds snaps during a glide and follows with one more glide", () => {
    const h = harness();
    h.pin.glide();
    h.pin.pin(); // the keyboard closing
    h.pin.pin(); // the steps card measured
    expect(h.calls).toEqual([true]);
    h.advance(GLIDE_GUARD_MS);
    expect(h.calls).toEqual([true, true]);
    h.advance(GLIDE_GUARD_MS);
    expect(h.pin.gliding()).toBe(false);
    h.pin.pin(true, "layout");
    expect(h.calls).toEqual([true, true, false]);
  });

  it("ends a glide quietly when nothing came in", () => {
    const h = harness();
    h.pin.glide();
    h.advance(GLIDE_GUARD_MS);
    expect(h.calls).toEqual([true]);
    expect(h.pin.gliding()).toBe(false);
  });

  it("keeps an armed glide for the content: the keyboard's layouts snap meanwhile, in the same frame", () => {
    const h = harness();
    h.pin.armGlide();
    h.pin.pin(true, "layout");
    expect(h.calls).toEqual([false]);
    h.pin.pin();
    expect(h.calls).toEqual([false, true]);
    h.pin.pin(true, "layout");
    expect(h.calls).toEqual([false, true]);
  });

  it("under reduce motion reaches the end without animating", () => {
    const h = harness();
    h.pin.armGlide();
    h.pin.pin(false);
    expect(h.calls).toEqual([false]);
    expect(h.pin.gliding()).toBe(false);
  });

  it("lets a drag take the list: no trailing glide, no armed glide", () => {
    const h = harness();
    h.pin.glide();
    h.pin.pin();
    h.pin.cancel();
    h.advance(GLIDE_GUARD_MS);
    expect(h.calls).toEqual([true]);
    // An armed glide is dropped too: the keyboard's layout then snaps as usual.
    h.pin.armGlide();
    h.pin.cancel();
    h.pin.pin(true, "layout");
    expect(h.calls).toEqual([true, false]);
  });
});

describe("jumpToLatestShown (Prism CX-6)", () => {
  it("without an answer writing: only more than a screen from the end", () => {
    expect(jumpToLatestShown({ distance: 900, viewport: 700, generating: false })).toBe(true);
    expect(jumpToLatestShown({ distance: 500, viewport: 700, generating: false })).toBe(false);
    expect(jumpToLatestShown({ distance: 0, viewport: 700, generating: false })).toBe(false);
  });
  it("while an answer writes: as soon as the list stops following it", () => {
    expect(jumpToLatestShown({ distance: 200, viewport: 700, generating: true })).toBe(true);
    expect(jumpToLatestShown({ distance: 60, viewport: 700, generating: true })).toBe(false);
  });
});

describe("keepEndOnResize (iPhone v8: suggestion cut by a 3-line composer)", () => {
  it("keeps the end in view when following it", () => {
    expect(keepEndOnResize({ following: true, empty: false, shrank: false })).toBe(true);
  });
  it("on the empty state whenever the list gets shorter, even scrolled (it is taller than a keyboard-high list)", () => {
    expect(keepEndOnResize({ following: false, empty: true, shrank: true })).toBe(true);
  });
  it("the empty state opens at its top: no pin on its first layout or when the list grows", () => {
    expect(keepEndOnResize({ following: false, empty: true, shrank: false })).toBe(false);
  });
  it("not in a conversation read further up", () => {
    expect(keepEndOnResize({ following: false, empty: false, shrank: true })).toBe(false);
  });
});
