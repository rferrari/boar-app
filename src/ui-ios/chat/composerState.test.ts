import { describe, it, expect } from "vitest";
import { composerNotice, composerPlaceholderKey, modelStatus } from "./composerState";

describe("modelStatus", () => {
  it("puts a load error ahead of the loading flag (iOS shot e927016: both showed at once)", () => {
    expect(modelStatus(false, "model file not found")).toBe("error");
    expect(modelStatus(true, "stale error")).toBe("error");
  });

  it("is loading until the model is ready", () => {
    expect(modelStatus(false, null)).toBe("loading");
    expect(modelStatus(false, "")).toBe("loading");
    expect(modelStatus(true, undefined)).toBe("ready");
  });
});

describe("composerNotice", () => {
  it("on error shows no line under the card, but keeps the reason for screen readers", () => {
    expect(composerNotice("error")).toEqual({ line: null, hint: "chat.composer.modelError" });
  });

  it("never says loading when the load failed", () => {
    const { line, hint } = composerNotice("error");
    expect([line, hint]).not.toContain("chat.composer.notReady");
  });

  it("while loading, no second line (the top strip names the model), only the hint; nothing when ready", () => {
    expect(composerNotice("loading")).toEqual({ line: null, hint: "chat.composer.notReady" });
    expect(composerNotice("ready")).toEqual({ line: null, hint: null });
  });
});

describe("composerPlaceholderKey (Prism LD-1)", () => {
  it("tells that typing works while the model loads", () => {
    expect(composerPlaceholderKey("loading")).toBe("chat.composer.placeholderLoading");
    expect(composerPlaceholderKey("ready")).toBe("chat.composer.placeholder");
    expect(composerPlaceholderKey("error")).toBe("chat.composer.placeholder");
  });
});

describe("indexing (Prism IX-1)", () => {
  it("is its own state while the library is indexed, with its own placeholder and hint, and sending held", () => {
    expect(modelStatus(false, null, true)).toBe("indexing");
    expect(modelStatus(true, null, true)).toBe("ready");
    expect(modelStatus(false, "boom", true)).toBe("error");
    expect(composerPlaceholderKey("indexing")).toBe("chat.composer.placeholderIndexing");
    expect(composerNotice("indexing")).toEqual({ line: null, hint: "chat.composer.indexing" });
  });
});
