import type { TFunction } from "i18next";
import { friendlyModelName, friendlyModelNameById } from "../flows/catalogLabel";
import type { CatalogModel } from "../../models/manifest";

/** The chat's pure helpers take a plain translate function; Loom's helpers take i18next's. */
type T = (key: string, opts?: Record<string, unknown>) => string;

/**
 * The name the chat shows for a model: its tier ("Fast" / "More accurate", r4to via Boar), from
 * Loom's single helper (src/ui/flows/catalogLabel.ts). The technical name stays in Settings ›
 * Assistant › Details. A model outside the catalog (Hugging Face) keeps the label it came with.
 */
/** Any catalog-like model: the fields Loom's helper reads (kind/answerTier absent = not an answer tier). */
export type NameableModel = Pick<CatalogModel, "id" | "label" | "displayName"> & Partial<Pick<CatalogModel, "kind" | "answerTier">>;

export function chatModelName(model: NameableModel, t: T): string {
  return friendlyModelName(model, t as unknown as TFunction);
}

/** Same, for a model the engine names by id + technical label (receipt, effective model, downgrade). */
export function chatModelNameById(id: string | undefined, label: string, t: T): string {
  return friendlyModelNameById(id, label, t as unknown as TFunction);
}
