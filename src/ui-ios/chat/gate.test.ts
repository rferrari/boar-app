import { describe, it, expect } from "vitest";
import { makeGate } from "./gate";

describe("makeGate (library ready, Prism HX-1)", () => {
  it("holds a question until opened, then lets it through once", async () => {
    const g = makeGate(false);
    let passed = false;
    const waiting = g.promise.then(() => (passed = true));
    await Promise.resolve();
    expect(passed).toBe(false);
    expect(g.done).toBe(false);
    g.open();
    g.open();
    await waiting;
    expect(passed).toBe(true);
    expect(g.done).toBe(true);
  });

  it("starts open when there is nothing to wait for", async () => {
    const g = makeGate(true);
    expect(g.done).toBe(true);
    await expect(g.promise).resolves.toBeUndefined();
  });
});
