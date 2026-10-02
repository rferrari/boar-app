/**
 * What the composer says about sending, from the model's state. Typing is
 * always allowed; sending waits for a loaded model. A failed load is never
 * reported as "loading": the error card above says what to do.
 */
/** "indexing": the model is loaded, the offline library is still being indexed (first run); sending waits for it. */
export type ModelStatus = "ready" | "loading" | "indexing" | "error";

export function modelStatus(ready: boolean, loadError: string | null | undefined, indexing = false): ModelStatus {
  if (loadError) return "error";
  if (ready) return "ready";
  return indexing ? "indexing" : "loading";
}

/**
 * Why sending is off, as i18n keys: `line` is shown above the field, `hint`
 * is the send button's accessibility hint. One message per state: while loading,
 * the strip at the top already names the model (Prism LD-1), so the field only
 * changes its placeholder; on error, the error card says what to do.
 */
export function composerNotice(status: ModelStatus): { line: string | null; hint: string | null } {
  switch (status) {
    case "loading":
      return { line: null, hint: "chat.composer.notReady" };
    case "indexing":
      // Held until the library is indexed, so the first answer isn't missing its sources (Prism IX-1).
      return { line: null, hint: "chat.composer.indexing" };
    case "error":
      return { line: null, hint: "chat.composer.modelError" };
    default:
      return { line: null, hint: null };
  }
}

/** The field's placeholder: while the model loads it says typing already works. */
export function composerPlaceholderKey(status: ModelStatus): string {
  if (status === "loading") return "chat.composer.placeholderLoading";
  if (status === "indexing") return "chat.composer.placeholderIndexing";
  return "chat.composer.placeholder";
}
