import { describe, it, expect, vi } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { singularsPt } from "../../rag/ptLexicon";
import { shouldWarmPtLexicon, warmPtLexicon, WARMUP_WORD } from "./lexiconWarmup";

describe("PT lexicon warm-up (audit #5)", () => {
  it("runs only for a Portuguese app", () => {
    expect(shouldWarmPtLexicon("pt-BR")).toBe(true);
    expect(shouldWarmPtLexicon("pt")).toBe(true);
    expect(shouldWarmPtLexicon("en-US")).toBe(false);
    expect(shouldWarmPtLexicon(undefined)).toBe(false);
  });

  it("the warm-up word misses the lexicon, so the English-titles map gets built", () => {
    const lexicon = JSON.parse(readFileSync(join(__dirname, "../../../assets/lexicon/pt-en.json"), "utf8")) as Record<string, string>;
    expect(WARMUP_WORD.length).toBeGreaterThanOrEqual(7);
    for (const key of [WARMUP_WORD, ...singularsPt(WARMUP_WORD)]) expect(lexicon[key]).toBeUndefined();
    // The suggested "fahrenheit" is a key: it would hit and skip the titles map.
    expect(lexicon.fahrenheit).toBeDefined();
  });

  it("reads every title of the lexicon once, and only once per process", () => {
    const values = vi.fn(() => ["Nuclear fission", "Fahrenheit"]);
    const lexicon = new Proxy({} as Record<string, string>, {
      ownKeys: () => (values(), ["a", "b"]),
      getOwnPropertyDescriptor: (_t, k) => ({ enumerable: true, configurable: true, value: k === "a" ? "Nuclear fission" : "Fahrenheit" }),
      get: (_t, k) => (k === "a" ? "Nuclear fission" : k === "b" ? "Fahrenheit" : undefined),
    });
    const load = vi.fn(() => lexicon);
    warmPtLexicon(load);
    warmPtLexicon(load);
    expect(load).toHaveBeenCalledTimes(1);
    expect(values).toHaveBeenCalled();
  });
});
