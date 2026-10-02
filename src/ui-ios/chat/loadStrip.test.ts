import { describe, expect, it } from "vitest";
import { loadStripKey, loadStripShown } from "./loadStrip";

describe("loadStripShown (Prism L3-1)", () => {
  const base = { loadError: false, ready: false, itemCount: 0, modelsRequested: true };
  it("shows while the model is not ready, over a conversation or once models are requested", () => {
    expect(loadStripShown(base)).toBe(true);
    expect(loadStripShown({ ...base, modelsRequested: false, itemCount: 2 })).toBe(true);
  });
  it("hides when ready, on an error, or before anything was requested on an empty chat", () => {
    expect(loadStripShown({ ...base, ready: true })).toBe(false);
    expect(loadStripShown({ ...base, loadError: true })).toBe(false);
    expect(loadStripShown({ ...base, modelsRequested: false })).toBe(false);
  });
});

describe("loadStripKey (the text crossfades on the kind of status, not its numbers)", () => {
  it("a new status swaps", () => {
    expect(loadStripKey("Loading the model…")).not.toBe(loadStripKey("Preparing the model…"));
  });
  it("a counter or a percentage updates in place", () => {
    expect(loadStripKey("Indexing knowledge 15 / 300")).toBe(loadStripKey("Indexing knowledge 16 / 300"));
    expect(loadStripKey("Loading the model… 42%")).toBe(loadStripKey("Loading the model… 43%"));
    expect(loadStripKey("Carregando 1,5 GB")).toBe(loadStripKey("Carregando 2,25 GB"));
  });
});
