import { describe, expect, it } from "vitest";
import type { MemoryFit } from "../../inference/memoryFit";
import { catalogFit, expertFractionHint, fitForSnapshot, wontFitHere } from "./fit";
import { contextSizeForRam } from "../../inference/memoryFit";

const GB = 1024 ** 3;

describe("expertFractionHint", () => {
  it("recognizes mixture-of-experts ids by their active-parameter suffix", () => {
    expect(expertFractionHint({ id: "lfm2.5-8b-a1b-q4km" })).toBe(0.9);
    expect(expertFractionHint({ id: "qwen3.6-35b-a3b-q2" })).toBe(0.9);
    expect(expertFractionHint({ id: "qwen2.5-7b-instruct-q4km" })).toBe(0);
  });
});

describe("catalogFit", () => {
  const ram = { totalBytes: 8 * GB, availableBytes: 4 * GB };

  it("only estimates language models, and only with a RAM reading", () => {
    expect(catalogFit({ id: "bge", kind: "embedding", sizeBytes: GB }, ram, 4096)).toBeUndefined();
    expect(catalogFit({ id: "q", kind: "llm", sizeBytes: GB }, { totalBytes: 0, availableBytes: 0 }, 4096)).toBeUndefined();
  });

  it("keeps a small dense model resident", () => {
    expect(catalogFit({ id: "q15", kind: "llm", sizeBytes: GB }, ram, 4096)?.verdict).toBe("resident");
  });

  it("lets a large MoE stream from storage but a large dense model thrash", () => {
    // 12 GB file, 6 GB free: the MoE hot set (~1.9 GB, doubled for slack) plus buffers fits; the dense file does not.
    const roomy = { totalBytes: 8 * GB, availableBytes: 6 * GB };
    expect(catalogFit({ id: "big-a3b", kind: "llm", sizeBytes: 12 * GB }, roomy, 4096)?.verdict).toBe("streaming");
    expect(catalogFit({ id: "big-dense", kind: "llm", sizeBytes: 12 * GB }, roomy, 4096)?.verdict).toBe("thrashing");
  });

  it("reports insufficient when buffers alone exceed free RAM", () => {
    expect(catalogFit({ id: "huge", kind: "llm", sizeBytes: 60 * GB }, { totalBytes: 8 * GB, availableBytes: GB }, 4096)?.verdict).toBe("insufficient");
  });
});

describe("wontFitHere (CR-1: 7B/8B offered for download on a 3.8 GB phone)", () => {
  const fitOf = (o: Partial<MemoryFit>): MemoryFit => ({
    verdict: "thrashing",
    fileBytes: 0,
    kvCacheBytes: 0,
    computeBytes: 0,
    anonBytes: 0,
    hotWeightBytes: 0,
    expertFraction: 0,
    availableBytes: 0,
    totalBytes: 0,
    fromMetadata: false,
    ...o,
  });

  it("the limit: dense weights plus working memory against total RAM, on both sides", () => {
    const total = 4_000_000_000;
    expect(wontFitHere({}, fitOf({ fileBytes: 3_500_000_000, anonBytes: 500_000_000, totalBytes: total }))).toBe(false);
    expect(wontFitHere({}, fitOf({ fileBytes: 3_500_000_001, anonBytes: 500_000_000, totalBytes: total }))).toBe(true);
  });

  it("insufficient always counts; a mixture-of-experts file streams, so size alone does not", () => {
    expect(wontFitHere({}, fitOf({ verdict: "insufficient", totalBytes: 1e12 }))).toBe(true);
    expect(wontFitHere({}, fitOf({ fileBytes: 12e9, anonBytes: 1e9, totalBytes: 4e9, expertFraction: 0.9, verdict: "streaming" }))).toBe(false);
  });

  it("never the catalog's answer tiers (the 4B goes through the confirmation), and not without an estimate", () => {
    expect(wontFitHere({ answerTier: "default" }, fitOf({ verdict: "insufficient" }))).toBe(false);
    expect(wontFitHere({}, undefined)).toBe(false);
  });

  it("on a 3.8 GB phone: a 4.7 GB 7B can't open; a 2.4 GB model not in a tier still can", () => {
    const phone = { totalBytes: 3.8 * 1024 ** 3, availableBytes: 2.2 * 1024 ** 3 };
    const seven = { id: "some-7b", kind: "llm" as const, sizeBytes: 4.7e9 };
    const small = { id: "some-3b", kind: "llm" as const, sizeBytes: 2.4e9 };
    expect(wontFitHere({}, catalogFit(seven, phone, 4096))).toBe(true);
    expect(wontFitHere({}, catalogFit(small, phone, 4096))).toBe(false);
  });
});

describe("fitForSnapshot (one RAM read per render, perf audit #3)", () => {
  const llm = { id: "qwen3-4b", kind: "llm" as const, sizeBytes: 2.5e9 };
  const ram = { totalBytes: 6e9, availableBytes: 3e9 };

  it("is catalogFit with the engine's context size for that RAM", () => {
    expect(fitForSnapshot(llm, ram)).toEqual(catalogFit(llm, ram, contextSizeForRam(ram.totalBytes)));
  });

  it("no snapshot (the native read failed): no estimate", () => {
    expect(fitForSnapshot(llm, undefined)).toBeUndefined();
  });
});
