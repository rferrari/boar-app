/**
 * The one place the UI names a catalog item (chat included: Quill uses it too).
 * - Knowledge entries: from i18n by id (Prism I18N-3).
 * - The two answer models: by what they are for, Fast (compact) and More accurate
 *   (default), the same names as Settings › Assistant (r4to, decision 5C).
 * - The search model: Search.
 * - Anything else (7B, Phi, Hugging Face files): the short display name; these
 *   only show in the advanced part of Models.
 * The technical name (display name + label) is for detail only: Settings ›
 * Assistant › Details and About.
 */
import type { TFunction } from "i18next";
import { displayNameOf, MODEL_CATALOG, type CatalogModel } from "../../models/manifest";

export const TRANSLATED_LABEL_IDS = ["corpus-standard", "corpus-full", "wiki-vital5"] as const;
const TRANSLATED = new Set<string>(TRANSLATED_LABEL_IDS);

type Named = { id: string } & Pick<CatalogModel, "label" | "displayName"> & Partial<Pick<CatalogModel, "kind" | "answerTier">>;

/** A model outside the two answer tiers and the search model: shown only in Models › Advanced. */
export function isAdvancedModel(m: Named): boolean {
  return m.kind === "llm" && !m.answerTier;
}

export function catalogLabel(item: Named, t: TFunction, opts?: { technical?: boolean }): string {
  if (TRANSLATED.has(item.id)) return t(`flows.catalog.label.${item.id}`);
  if (opts?.technical) return item.label;
  if (item.kind === "llm" && item.answerTier) return t(`flows.onboarding.answerTier.${item.answerTier}`);
  if (item.kind === "embedding") return t("flows.assistant.search");
  return displayNameOf(item);
}

export const friendlyModelName = catalogLabel;

/** The friendly name for a model the engine names by id (chat receipts); the label when it is not in the catalog. */
export function friendlyModelNameById(id: string | undefined, label: string, t: TFunction): string {
  const m = id ? MODEL_CATALOG.find((x) => x.id === id) : undefined;
  return m ? catalogLabel(m, t) : label;
}

/** "Qwen3 4B · Qwen3-4B-Instruct-2507 (Q4_K_M)": the detail line, never a title. */
export function technicalModelName(m: Named): string {
  const short = displayNameOf(m);
  return short === m.label ? m.label : `${short} · ${m.label}`;
}
