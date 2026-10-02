/**
 * The install list of setup step 3 as the mockup draws it: one row per
 * category (answer model, search model, knowledge, places) with an
 * aggregated status. Honest by construction (Prism): a failure anywhere in a
 * category makes the row a failure; it never reads as downloading or ready.
 * No native imports.
 */
import type { RowState } from "./modelRowState";

export type InstallCategory = "answer" | "search" | "knowledge" | "places";
export type CategoryStatus = "failed" | "moving" | "ready" | "queued" | "import";

export interface InstallItem {
  id: string;
  kind: string;
  sizeBytes: number;
  state: RowState;
  /** Comes in as a file (offline build, or no published URL yet). */
  importOnly: boolean;
}

export interface CategoryRow {
  category: InstallCategory;
  status: CategoryStatus;
  /** Files of this category on the phone, and in total. */
  done: number;
  total: number;
  /** 0..1 of the category's bytes on the phone. */
  fraction: number;
  items: InstallItem[];
}

export const CATEGORY_ORDER: InstallCategory[] = ["answer", "search", "knowledge", "places"];

export function categoryOf(item: Pick<InstallItem, "id" | "kind">): InstallCategory {
  if (item.kind === "llm") return "answer";
  if (item.kind === "embedding") return "search";
  return item.id.startsWith("poi-") ? "places" : "knowledge";
}

const isDone = (s: RowState) => s.kind === "installed" || s.kind === "in-use";
const isMoving = (s: RowState) => (s.kind === "downloading" && s.progress > 0) || s.kind === "verifying";

function bytesOnPhone(i: InstallItem): number {
  if (isDone(i.state) || i.state.kind === "verifying") return i.sizeBytes;
  if (i.state.kind === "downloading") return i.sizeBytes * i.state.progress;
  return 0;
}

export function installCategories(items: InstallItem[]): CategoryRow[] {
  return CATEGORY_ORDER.map((category) => {
    const mine = items.filter((i) => categoryOf(i) === category);
    const total = mine.reduce((sum, i) => sum + i.sizeBytes, 0);
    const status: CategoryStatus = mine.some((i) => i.state.kind === "failed")
      ? "failed"
      : mine.every((i) => isDone(i.state))
        ? "ready"
        : mine.some((i) => isMoving(i.state))
          ? "moving"
          : mine.some((i) => !isDone(i.state) && i.importOnly)
            ? "import"
            : "queued";
    return {
      category,
      status,
      done: mine.filter((i) => isDone(i.state)).length,
      total: mine.length,
      fraction: total > 0 ? mine.reduce((sum, i) => sum + bytesOnPhone(i), 0) / total : 0,
      items: mine,
    };
  }).filter((c) => c.total > 0);
}
