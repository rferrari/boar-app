import { describe, expect, it } from "vitest";
import { median, summarizeRecent, tokPerSecBand, ttftBand } from "./perfBands";
import type { ExecutionTelemetryRecord } from "../../services/executionTelemetry.pure";

function record(overrides: Partial<ExecutionTelemetryRecord>): ExecutionTelemetryRecord {
  return { id: "r", createdAt: 0, adaptiveRoutingUsed: false, outcome: "success", ...overrides };
}

describe("tokPerSecBand", () => {
  it("puts the thresholds in the faster band", () => {
    expect(tokPerSecBand(10)).toBe("fast");
    expect(tokPerSecBand(9.9)).toBe("ok");
    expect(tokPerSecBand(5)).toBe("ok");
    expect(tokPerSecBand(4.9)).toBe("slow");
  });
});

describe("ttftBand", () => {
  it("puts the thresholds in the faster band", () => {
    expect(ttftBand(2000)).toBe("fast");
    expect(ttftBand(2001)).toBe("ok");
    expect(ttftBand(5000)).toBe("ok");
    expect(ttftBand(5001)).toBe("slow");
  });
});

describe("median", () => {
  it("handles empty, odd and even inputs", () => {
    expect(median([])).toBeUndefined();
    expect(median([3, 1, 2])).toBe(2);
    expect(median([4, 1, 3, 2])).toBe(2.5);
  });
});

describe("summarizeRecent", () => {
  it("uses only successful answers and the harness tok/s formula", () => {
    const records = [
      record({ ttftMs: 1000, tokensGenerated: 100, generationLatencyMs: 10_000, totalLatencyMs: 11_000 }),
      record({ outcome: "failure", ttftMs: 99_000 }),
      record({ ttftMs: 3000, tokensGenerated: 60, generationLatencyMs: 10_000, totalLatencyMs: 13_000 }),
    ];
    expect(summarizeRecent(records)).toEqual({
      sampleSize: 2,
      ttftMs: 2000,
      tokPerSec: 8,
      totalLatencyMs: 12_000,
    });
  });

  it("keeps only the newest `limit` answers", () => {
    const records = [record({ ttftMs: 1 }), record({ ttftMs: 2 }), record({ ttftMs: 100 })];
    expect(summarizeRecent(records, 2).ttftMs).toBe(1.5);
  });

  it("leaves metrics absent when nothing measured them", () => {
    expect(summarizeRecent([record({})])).toEqual({ sampleSize: 1 });
  });
});
