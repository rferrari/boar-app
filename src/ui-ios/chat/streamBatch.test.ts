import { describe, it, expect } from "vitest";
import { batchAnimatesLayout, flushDelay, STREAM_FLUSH_MS } from "./streamBatch";

describe("flushDelay (audit #6)", () => {
  it("batches tokens over a window longer than the on-device gap between tokens", () => {
    expect(flushDelay({ answerId: "a", type: "token", tier: "fast", text: "x" })).toBe(STREAM_FLUSH_MS);
    // 30 tok/s is 33 ms apart: a window must hold more than one of them.
    expect(STREAM_FLUSH_MS).toBeGreaterThan(2 * 33);
    expect(STREAM_FLUSH_MS).toBeLessThanOrEqual(120);
  });
  it("shows stage changes, sources and done at once", () => {
    expect(flushDelay({ answerId: "a", type: "stage", stage: "generating", tier: "fast", at: 1 })).toBe(0);
    expect(flushDelay({ answerId: "a", type: "sources", tier: "fast", sources: [] })).toBe(0);
    expect(flushDelay({ answerId: "a", type: "warning", code: "weak_sources" } as never)).toBe(0);
  });
});

describe("batchAnimatesLayout (iPhone v9: no native layout animation where a Reveal leaves)", () => {
  const done = { type: "done", answerId: "a", tier: "fast", outcome: "success" } as never;
  const warning = { type: "warning", answerId: "a", code: "weak_sources", declined: true } as never;
  const token = { type: "token", answerId: "a", tier: "fast", text: "x" } as never;
  it("animates the end of a normal answer (the snippet folding)", () => {
    expect(batchAnimatesLayout([token, done], false)).toBe(true);
  });
  it("not for a decline, nor a batch with a warning, nor plain tokens", () => {
    expect(batchAnimatesLayout([done], true)).toBe(false);
    expect(batchAnimatesLayout([warning, done], false)).toBe(false);
    expect(batchAnimatesLayout([token], false)).toBe(false);
  });
});
