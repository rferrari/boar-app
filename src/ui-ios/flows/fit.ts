/**
 * Memory fit for catalog rows, from Tusk's estimateMemoryFit. A model not
 * downloaded yet has no GGUF header to read, so the estimate runs on the
 * file size plus an expert-fraction hint for mixture-of-experts files.
 */
import type { CatalogModel } from "../../models/manifest";
import { contextSizeForRam, estimateMemoryFit, MemoryFit } from "../../inference/memoryFit";

/** The phone's RAM at one moment: read once per render, shared by every row (perf audit #3). */
export interface RamSnapshot {
  totalBytes: number;
  availableBytes: number;
}

/** catalogFit against a snapshot, with the context size the engine would use on this phone. Pure. */
export function fitForSnapshot(model: Pick<CatalogModel, "id" | "kind" | "sizeBytes">, ram: RamSnapshot | undefined): MemoryFit | undefined {
  return ram ? catalogFit(model, ram, contextSizeForRam(ram.totalBytes)) : undefined;
}

/** "-a1b", "-a3b": the active-parameter suffix mixture-of-experts files carry. */
const MOE_ID = /-a\d+(\.\d+)?b\b/i;

/** Tusk's guidance: ~0.9-0.95 of the weights are routed experts in A-suffixed MoE models. */
export function expertFractionHint(model: Pick<CatalogModel, "id">): number {
  return MOE_ID.test(model.id) ? 0.9 : 0;
}

/**
 * This model can't open on this phone, so it is not offered for download
 * (Prism CR-1: 7B/8B on 3.8 GB). From the same estimate as the rows: its
 * working memory exceeds the free RAM ("insufficient"), or it is dense and
 * its weights plus working memory exceed the phone's total RAM, so it can't
 * stay in memory at all (a mixture-of-experts file streams its experts).
 * The catalog's answer tiers never count: the default one goes through the
 * low-RAM confirmation instead.
 */
export function wontFitHere(model: Pick<CatalogModel, "answerTier">, fit: MemoryFit | undefined): boolean {
  if (!fit || model.answerTier) return false;
  if (fit.verdict === "insufficient") return true;
  return fit.expertFraction === 0 && fit.fileBytes + fit.anonBytes > fit.totalBytes;
}

export function catalogFit(
  model: Pick<CatalogModel, "id" | "kind" | "sizeBytes">,
  ram: { totalBytes: number; availableBytes: number },
  nCtx: number
): MemoryFit | undefined {
  if (model.kind !== "llm" || ram.totalBytes <= 0 || ram.availableBytes <= 0) return undefined;
  return estimateMemoryFit({
    fileBytes: model.sizeBytes,
    nCtx,
    shape: null,
    expertFractionHint: expertFractionHint(model),
    totalRamBytes: ram.totalBytes,
    availableRamBytes: ram.availableBytes,
  });
}
