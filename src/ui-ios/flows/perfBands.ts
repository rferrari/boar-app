/**
 * Speed bands for the Performance screen, shared with the eval reports so
 * both label a run the same way. Provisional until device baselines exist:
 * bump PERF_BANDS_VERSION whenever a threshold changes.
 */
import { tokensPerSecond } from "../../eval/evalHarness.pure";
import type { ExecutionTelemetryRecord } from "../../services/executionTelemetry.pure";

export const PERF_BANDS_VERSION = 1;
export const PERF_BANDS_PROVISIONAL = true;

export type PerfBand = "fast" | "ok" | "slow";

/** Human reading is ~6-7 tok/s: at 10+ the text stays ahead of the reader. */
export const TOK_PER_SEC_FAST = 10;
export const TOK_PER_SEC_OK = 5;

/** Send → first token, retrieval and prefill included. */
export const TTFT_FAST_MS = 2000;
export const TTFT_OK_MS = 5000;

export function tokPerSecBand(tokPerSec: number): PerfBand {
  if (tokPerSec >= TOK_PER_SEC_FAST) return "fast";
  if (tokPerSec >= TOK_PER_SEC_OK) return "ok";
  return "slow";
}

export function ttftBand(ttftMs: number): PerfBand {
  if (ttftMs <= TTFT_FAST_MS) return "fast";
  if (ttftMs <= TTFT_OK_MS) return "ok";
  return "slow";
}

export function median(values: number[]): number | undefined {
  if (values.length === 0) return undefined;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

/** Same formula as the eval harness, so the screen matches the reports. */
export function recordTokPerSec(r: ExecutionTelemetryRecord): number | undefined {
  return tokensPerSecond(r.tokensGenerated ?? 0, r.generationLatencyMs);
}

export interface PerfSummary {
  /** Successful answers the medians are computed over. */
  sampleSize: number;
  ttftMs?: number;
  tokPerSec?: number;
  totalLatencyMs?: number;
}

/** Medians over the most recent `limit` successful answers (records newest first). */
export function summarizeRecent(records: ExecutionTelemetryRecord[], limit = 20): PerfSummary {
  const recent = records.filter((r) => r.outcome === "success").slice(0, limit);
  const defined = (xs: (number | undefined)[]) => xs.filter((x): x is number => x != null);
  return {
    sampleSize: recent.length,
    ttftMs: median(defined(recent.map((r) => r.ttftMs))),
    tokPerSec: median(defined(recent.map(recordTokPerSec))),
    totalLatencyMs: median(defined(recent.map((r) => r.totalLatencyMs))),
  };
}
