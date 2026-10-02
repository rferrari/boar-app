/**
 * Capacity packages offered by the setup wizard. Every number on a package
 * card is computed here from the manifest and what is already on disk;
 * nothing is typed in by hand.
 *
 * The manifest keeps its four tiers for now. "minimum" and "standard" only
 * differ from "full" by ~3 MB of JSON packs, so the wizard shows "full" as
 * the essential package and hides the other two.
 */
import type { CatalogModel, SetupTier, TierDefinition } from "../../models/manifest";

export type PackageId = "essential" | "encyclopedia";

export const PACKAGES: { id: PackageId; tier: SetupTier }[] = [
  { id: "essential", tier: "full" },
  { id: "encyclopedia", tier: "encyclopedia" },
];

/** The answer model a package installs: "default" (bigger, better answers) or "compact" (low-RAM phones). */
export type AnswerTier = "default" | "compact";

/**
 * The answer models the setup can offer. Uses the manifest's answerTier when
 * present; before that field exists, the required language model is the
 * default and there is no compact option.
 */
export function answerModelChoices(catalog: CatalogModel[]): Partial<Record<AnswerTier, CatalogModel>> {
  const llms = catalog.filter((m) => m.kind === "llm");
  const byTier = (tier: AnswerTier) => llms.find((m) => m.answerTier === tier);
  return {
    default: byTier("default") ?? llms.find((m) => m.required),
    compact: byTier("compact"),
  };
}

/**
 * Everything a package installs: the required non-language assets (the
 * search model), the chosen answer model, and the tier's knowledge packs.
 */
export function packageAssets(tier: TierDefinition, catalog: CatalogModel[], answerModel?: CatalogModel): CatalogModel[] {
  const llm = answerModel ?? answerModelChoices(catalog).default;
  return [
    ...catalog.filter((m) => m.required && m.kind !== "llm"),
    ...(llm ? [llm] : []),
    ...catalog.filter((m) => m.kind === "corpus" && tier.corpusPackIds.includes(m.id)),
  ];
}

export interface PackagePlan {
  assets: CatalogModel[];
  /** Assets still to download or import. */
  pending: CatalogModel[];
  downloadBytes: number;
  /** Disk the package takes once installed, including what is already there. */
  installedBytes: number;
  /** The biggest language model, whose memory fit decides whether the package fits. */
  largestLlm: CatalogModel | undefined;
}

export function planPackage(assets: CatalogModel[], present: Record<string, boolean>): PackagePlan {
  const pending = assets.filter((a) => !present[a.id]);
  const llms = assets.filter((a) => a.kind === "llm");
  return {
    assets,
    pending,
    downloadBytes: pending.reduce((sum, a) => sum + a.sizeBytes, 0),
    installedBytes: assets.reduce((sum, a) => sum + a.sizeBytes, 0),
    largestLlm: llms.reduce<CatalogModel | undefined>((big, m) => (!big || m.sizeBytes > big.sizeBytes ? m : big), undefined),
  };
}

/**
 * The speed a package card assumes for its time estimate, shown next to the
 * estimate: 5 MB/s, roughly a 40 Mbit/s connection.
 */
export const REFERENCE_BYTES_PER_SEC = 5_000_000;

/**
 * Seconds to move `bytes` at `bytesPerSec`. The card shows the speed it
 * assumed next to the result, so the estimate is a declared formula rather
 * than a promise. Undefined when the speed is unknown.
 */
export function transferSeconds(bytes: number, bytesPerSec: number | undefined): number | undefined {
  if (!bytesPerSec || bytesPerSec <= 0) return undefined;
  return bytes / bytesPerSec;
}

/** Bytes missing on the device for this download, or 0 when it fits. Unknown free space never blocks. */
export function storageShortfall(downloadBytes: number, freeBytes: number): number {
  if (freeBytes <= 0) return 0;
  return Math.max(0, downloadBytes - freeBytes);
}

/**
 * The package setup recommends and pre-selects: the richest one whose
 * download fits the free space measured on the phone (PACKAGES is ordered
 * smallest first). Unknown free space never blocks; if nothing fits, the
 * smallest package.
 */
export function recommendPackage(plans: { id: PackageId; shortfall: number }[]): PackageId {
  const fitting = plans.filter((p) => p.shortfall === 0);
  return (fitting[fitting.length - 1] ?? plans[0]).id;
}

/**
 * The package that carries the RECOMMENDED seal, or none yet. Before the catalog loads the free
 * space reads 0 ("unknown, never blocks"), so the richest package would get the seal while the
 * pre-selection still waits: the seal waits too, and then lands where the selection does (FL-22).
 */
export function shownRecommendation(plans: { id: PackageId; shortfall: number }[], loaded: boolean): PackageId | undefined {
  return loaded ? recommendPackage(plans) : undefined;
}
