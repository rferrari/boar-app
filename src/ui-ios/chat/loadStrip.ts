/**
 * The model/library status strip on top of the chat (Prism L3-1). Pure, so the rules are tested.
 */

/** Shown while the model loads or the library indexes, above a conversation or the empty state. */
export function loadStripShown(s: { loadError: boolean; ready: boolean; itemCount: number; modelsRequested: boolean }): boolean {
  return !s.loadError && !s.ready && (s.itemCount > 0 || s.modelsRequested);
}

/**
 * What the strip's text crossfades on: the kind of status, not its numbers. "Loading the model…" →
 * "Preparing the model…" swaps; "Indexing 15 / 300" → "Indexing 16 / 300" just updates in place.
 */
export function loadStripKey(label: string): string {
  return label.replace(/\d+([.,]\d+)?/g, "#");
}
