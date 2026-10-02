/** Pure helpers for `MetaLine` (no RN imports, so vitest can load them). */
export const META_SEPARATOR = " · ";

/** Keeps only the non-empty facts. */
export function metaItems(items: (string | false | null | undefined)[]): string[] {
  return items.filter((i): i is string => typeof i === "string" && i.trim().length > 0);
}
